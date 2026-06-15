package com.soufianodev.lingallery

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import androidx.compose.ui.zIndex
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.SingletonSketch
import com.github.panpf.sketch.Sketch
import com.soufianodev.lingallery.data.*
import com.soufianodev.lingallery.data.FileIndexer
import com.soufianodev.lingallery.data.GalleryIndex
import com.soufianodev.lingallery.data.ScanEvent
import com.soufianodev.lingallery.processing.ImageEditor
import com.soufianodev.lingallery.i18n.Strings
import com.soufianodev.lingallery.theme.AppConst
import com.soufianodev.lingallery.theme.AppIcons
import com.soufianodev.lingallery.ui.components.CloseIconStyle
import com.soufianodev.lingallery.ui.components.LinGallerySnackbar
import com.soufianodev.lingallery.ui.components.SnackbarStyle
import com.soufianodev.lingallery.ui.components.TooltipIconButton
import com.soufianodev.lingallery.ui.components.stablePointerHoverIcon
import com.soufianodev.lingallery.theme.DarkPalette
import com.soufianodev.lingallery.theme.LightPalette
import com.soufianodev.lingallery.theme.LinGalleryTheme
import com.soufianodev.lingallery.ui.components.FilePickerDialog
import com.soufianodev.lingallery.ui.gallery.AlbumPanel
import com.soufianodev.lingallery.ui.gallery.GalleryView
import com.soufianodev.lingallery.ui.viewer.EditToolbar
import com.soufianodev.lingallery.ui.viewer.FloatingZoomControl
import com.soufianodev.lingallery.ui.viewer.ImageViewer
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

fun main() = application {
    System.setProperty("jdk.nio.file.WatchService.maxEventsPerPoll", "16384")

    SingletonSketch.setSafe {
        Sketch.Builder(PlatformContext.INSTANCE).apply {
            logger(level = com.github.panpf.sketch.util.Logger.Level.Warn)
        }.build()
    }

    val initialScanRoots = AppConst.DEFAULT_SCAN_ROOTS.map {
        Paths.get(it.replace("~", System.getProperty("user.home")))
    }
    val fileWatcher = FileWatcher(roots = initialScanRoots)
    val galleryIndex = GalleryIndex()

    val windowState = rememberWindowState(width = 1400.dp, height = 900.dp)

    Window(
        onCloseRequest = {
            fileWatcher.stop()
            galleryIndex.close()
            exitApplication()
            exitProcess(0)
        },
        title = Strings.App.name,
        state = windowState
    ) {
        LinGalleryApp(
            awtWindow = window,
            windowState = windowState,
            fileWatcher = fileWatcher,
            galleryIndex = galleryIndex,
            initialScanRoots = initialScanRoots
        )
    }
}

@Composable
fun LinGalleryApp(
    awtWindow: java.awt.Window,
    windowState: WindowState,
    fileWatcher: FileWatcher,
    galleryIndex: GalleryIndex,
    initialScanRoots: List<Path>
) {
    var state by remember { mutableStateOf(GalleryState()) }
    var showFilePicker by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var exifData by remember { mutableStateOf<Map<String, String>?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPermanentDeleteWarning by remember { mutableStateOf(false) }
    var permanentDeleteChecked by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var slideshowActive by remember { mutableStateOf(false) }
    var pendingFileOp by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf(Strings.Status.scanning) }
    var isFullscreen by remember { mutableStateOf(false) }
    var savedWindowBounds by remember { mutableStateOf<java.awt.Rectangle?>(null) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarIsError by remember { mutableStateOf(false) }
    var snackbarAutoClose by remember { mutableStateOf(true) }
    var snackbarIsDismissible by remember { mutableStateOf(true) }
    var snackbarCloseIconStyle by remember { mutableStateOf(CloseIconStyle.NORMAL) }
    var snackbarShowCloseButton by remember { mutableStateOf(false) }
    var snackbarTitle by remember { mutableStateOf("") }
    var snackbarDetails by remember { mutableStateOf("") }
    var cropModeTransitioning by remember { mutableStateOf(false) }
    val isDark = true

    fun showSnackbar(msg: String) {
        snackbarTitle = ""
        snackbarDetails = ""
        snackbarIsError = false
        snackbarAutoClose = true
        snackbarIsDismissible = true
        snackbarCloseIconStyle = CloseIconStyle.NORMAL
        snackbarShowCloseButton = false
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    fun showStructuredSnackbar(title: String, details: String) {
        snackbarTitle = title
        snackbarDetails = details
        snackbarIsError = false
        snackbarAutoClose = true
        snackbarIsDismissible = true
        snackbarCloseIconStyle = CloseIconStyle.NORMAL
        snackbarShowCloseButton = false
        scope.launch { snackbarHostState.showSnackbar(title) }
    }

    fun showErrorSnackbar(msg: String) {
        snackbarTitle = ""
        snackbarDetails = ""
        snackbarIsError = true
        snackbarAutoClose = true
        snackbarIsDismissible = true
        snackbarCloseIconStyle = CloseIconStyle.NORMAL
        snackbarShowCloseButton = false
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    fun loadViewerImage(index: Int) {
        val images = state.currentAlbumImages
        val image = images.getOrNull(index) ?: return
        state = state.copy(
            screen = Screen.Viewer(index),
            viewerState = ViewerState(currentIndex = index)
        )
    }

    fun navigateImage(delta: Int) {
        val images = state.currentAlbumImages
        if (images.isEmpty()) return
        val newIndex = (state.viewerState.currentIndex + delta).coerceIn(0, images.size - 1)
        state = state.copy(
            viewerState = state.viewerState.copy(currentIndex = newIndex, panX = 0f, panY = 0f)
        )
    }

    var showCropDialog by remember { mutableStateOf(false) }

    fun doApplyCrop() {
        showCropDialog = true
    }

    fun uniqueDestination(path: Path): Path {
        val parent = path.parent
        val name = path.fileName.toString()
        val dot = name.lastIndexOf('.')
        val stem = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        var counter = 1
        while (counter < 10000) {
            val candidate = parent.resolve("$stem ($counter)$ext")
            if (!candidate.toFile().exists()) return candidate
            counter++
        }
        return parent.resolve("$stem (${System.currentTimeMillis()})$ext")
    }

    fun refreshCurrentImageFile() {
        val image = state.currentImage ?: return
        val idx = state.viewerState.currentIndex
        val albumIdx = state.currentAlbumIndex
        try {
            val attrs = Files.readAttributes(image.path, "*")
            val newSize = attrs["size"] as? Long ?: return
            val newMtime = (attrs["lastModifiedTime"] as? FileTime)?.toMillis() ?: return
            val updated = image.copy(size = newSize, lastModified = newMtime)
            val albums = state.albums.toMutableList()
            val albumImages = albums[albumIdx].images.toMutableList()
            albumImages[idx] = updated
            val updatedAlbum = albums[albumIdx].copy(images = albumImages)
            albums[albumIdx] = updatedAlbum
            state = state.copy(albums = albums)
        } catch (_: Exception) { }
    }

    fun executeCrop(choice: String) {
        val rect = state.viewerState.cropRect ?: return
        val image = state.currentImage ?: return
        if (!rect.isValid()) return
        cropModeTransitioning = false
        showCropDialog = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val destPath = if (choice == "copy") {
                    uniqueDestination(image.path)
                } else {
                    image.path
                }
                val ok = ImageEditor.crop(image.path, rect.x, rect.y, rect.width, rect.height, destPath)
                val newFile = if (ok && choice == "copy") {
                    val name = destPath.fileName.toString()
                    val dot = name.lastIndexOf('.')
                    val ext = if (dot >= 0) name.substring(dot).lowercase() else ""
                    ImageFile(
                        path = destPath,
                        name = name,
                        extension = ext,
                        size = Files.size(destPath),
                        lastModified = Files.getLastModifiedTime(destPath).toMillis()
                    )
                } else null
                Triple(ok, destPath, newFile)
            }
            val (ok, _, newFile) = result
            if (ok) {
                if (choice == "copy" && newFile != null) {
                    val album = state.currentAlbum
                    if (album != null) {
                        val newImages = (album.images + newFile)
                            .distinctBy { it.path }
                            .sortedByDescending { it.lastModified }
                        val newIndex = newImages.indexOfFirst { img -> img.path == newFile.path }
                        state = state.copy(
                            albums = state.albums.toMutableList().apply { set(state.currentAlbumIndex, album.copy(images = newImages)) },
                            viewerState = state.viewerState.copy(
                                isCropping = false,
                                cropRect = null,
                                currentIndex = newIndex,
                                scale = 1f,
                                panX = 0f,
                                panY = 0f
                            )
                        )
                        showSnackbar(Strings.Snackbar.savedCopy)
                    } else {
                        state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                        showSnackbar(Strings.Snackbar.savedCopy)
                    }
                } else {
                    state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                    refreshCurrentImageFile()
                    showSnackbar(Strings.Snackbar.imageCropped)
                }
            } else {
                state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                showErrorSnackbar(Strings.Snackbar.cropFailed)
            }
        }
    }

    fun showRenameDialog() {
        val image = state.currentImage ?: return
        val dot = image.name.lastIndexOf('.')
        renameText = if (dot >= 0) image.name.substring(0, dot) else image.name
        showRenameDialog = true
    }

    fun executeRename() {
        val image = state.currentImage ?: return
        val newName = renameText.trim().trimEnd('.')
        if (newName.isEmpty()) return
        showRenameDialog = false

        val ext = image.extension.removePrefix(".")
        val dest = if (ext.isNotEmpty()) {
            image.path.parent.resolve("$newName.$ext")
        } else {
            image.path.parent.resolve(newName)
        }

        if (dest == image.path) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    Files.move(image.path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    true
                } catch (_: Exception) { false }
            }
            if (ok) {
                val newImage = readImageFileInfo(dest)
                if (newImage != null) {
                    val albumPath = image.path.parent
                    state = state.removeImage(albumPath, image.path)
                    state = state.addImage(albumPath, newImage)
                }
                if (state.screen is Screen.Viewer) {
                    loadViewerImage(state.viewerState.currentIndex)
                }
                showSnackbar(Strings.Snackbar.imageRenamed)
            } else {
                showErrorSnackbar(Strings.Snackbar.renameFailed)
            }
        }
    }

    fun zoomIn() {
        val vs = state.viewerState
        state = state.copy(viewerState = vs.copy(scale = (vs.scale * AppConst.ZOOM_STEP.toFloat()).coerceAtMost(AppConst.ZOOM_MAX.toFloat())))
    }

    fun zoomOut() {
        val vs = state.viewerState
        state = state.copy(viewerState = vs.copy(scale = (vs.scale / AppConst.ZOOM_STEP.toFloat()).coerceAtLeast(AppConst.ZOOM_MIN.toFloat())))
    }

    fun rotateLeft() {
        val path = state.currentImage?.path ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ImageEditor.rotate(path, -90) }
            if (ok) {
                refreshCurrentImageFile()
            } else {
                showErrorSnackbar(Strings.Snackbar.rotateFailed)
            }
        }
    }

    fun rotateRight() {
        val path = state.currentImage?.path ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ImageEditor.rotate(path, 90) }
            if (ok) {
                refreshCurrentImageFile()
            } else {
                showErrorSnackbar(Strings.Snackbar.rotateFailed)
            }
        }
    }

    fun flipHorizontal() {
        val path = state.currentImage?.path ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ImageEditor.flipHorizontal(path) }
            if (ok) {
                refreshCurrentImageFile()
            } else {
                showErrorSnackbar(Strings.Snackbar.flipFailed)
            }
        }
    }

    fun toggleCrop() {
        if (cropModeTransitioning) return
        cropModeTransitioning = true
        val vs = state.viewerState
        state = state.copy(
            viewerState = vs.copy(
                isCropping = !vs.isCropping,
                cropRect = null
            )
        )
        scope.launch {
            delay(300)
            cropModeTransitioning = false
        }
    }

    fun deleteCurrentImage() {
        permanentDeleteChecked = false
        showDeleteConfirm = true
    }

    fun performUndo() {
        val record = state.deletionRecord ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                TrashManager.restoreFromTrash(record.trashPath, record.imageFile.path)
            }
            if (ok.isFailure) {
                showErrorSnackbar(Strings.Snackbar.undoFailed)
                return@launch
            }

            val existingIdx = state.albums.indexOfFirst { it.path == record.albumPath }
            val album: Album
            val albumIdx: Int

            if (existingIdx >= 0) {
                album = state.albums[existingIdx]
                albumIdx = existingIdx
            } else {
                val dirAlbum = scanSingleDir(record.albumPath)
                album = dirAlbum ?: Album(
                    path = record.albumPath,
                    name = record.albumPath.fileName.toString(),
                    images = emptyList(),
                    previewPath = null
                )
                albumIdx = record.albumIndex.coerceAtMost(state.albums.size)
                if (state.albums.any { it.path == album.path }) {
                    showErrorSnackbar(Strings.Snackbar.albumExists)
                    return@launch
                }
                val newAlbums = state.albums.toMutableList()
                newAlbums.add(albumIdx, album)
                state = state.copy(albums = newAlbums)
            }

            val mutableImages = album.images.toMutableList()
            val insertIdx = record.imageIndex.coerceAtMost(mutableImages.size)
            if (mutableImages.any { it.path == record.imageFile.path }) {
                showErrorSnackbar(Strings.Snackbar.imageRestored)
                state = state.copy(deletionRecord = null)
                return@launch
            }
            mutableImages.add(insertIdx, record.imageFile)
            val preview = mutableImages.firstOrNull()?.path
            val restoredAlbum = album.copy(images = mutableImages, previewPath = preview)

            val updatedAlbums = state.albums.toMutableList()
            updatedAlbums[albumIdx] = restoredAlbum

            state = state.copy(
                albums = updatedAlbums,
                deletionRecord = null,
                screen = Screen.Viewer(insertIdx),
                viewerState = ViewerState(currentIndex = insertIdx)
            )
            showSnackbar(Strings.Snackbar.restored(record.imageFile.name))
        }
    }

    fun confirmPermanentDelete() {
        val image = state.currentImage ?: return
        scope.launch {
            showPermanentDeleteWarning = false

            withContext(Dispatchers.IO) {
                try { Files.deleteIfExists(image.path) } catch (_: Exception) {}
            }

            state = state.removeImage(image.path.parent, image.path)
            showSnackbar(Strings.Snackbar.permanentlyDeleted(image.name))
        }
    }

    fun confirmDelete() {
        val image = state.currentImage ?: return
        if (permanentDeleteChecked) {
            showDeleteConfirm = false
            showPermanentDeleteWarning = true
            return
        }
        val albumPath = image.path.parent
        val albumIdx = state.currentAlbumIndex
        val imageIdx = state.viewerState.currentIndex
        scope.launch {
            showDeleteConfirm = false
            val trashResult = withContext(Dispatchers.IO) {
                TrashManager.moveToTrash(image.path)
            }
            if (trashResult.isFailure) {
                showErrorSnackbar(Strings.Snackbar.deleteFailed)
                return@launch
            }
            val trashPath = trashResult.getOrThrow()

            state = state.removeImage(albumPath, image.path)

            val newRecord = DeletionRecord(
                trashPath = trashPath,
                imageFile = image,
                albumPath = albumPath,
                albumIndex = albumIdx,
                imageIndex = imageIdx
            )
            state = state.copy(deletionRecord = newRecord)

            val result = snackbarHostState.showSnackbar(
                message = Strings.Snackbar.deleted(image.name),
                actionLabel = Strings.Buttons.undo,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                performUndo()
            }
        }
    }

    fun copyImageNameToClipboard() {
        val image = state.currentImage ?: return
        try {
            val selection = java.awt.datatransfer.StringSelection(image.name)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            showSnackbar(Strings.Snackbar.nameCopied)
        } catch (_: Exception) {
            showErrorSnackbar(Strings.Snackbar.nameCopyFailed)
        }
    }

    fun copyToClipboard() {
        val image = state.currentImage ?: return
        try {
            val selection = java.awt.datatransfer.StringSelection(image.path.toString())
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            showSnackbar(Strings.Snackbar.pathCopied)
        } catch (_: Exception) {
            showErrorSnackbar(Strings.Snackbar.copyFailed)
        }
    }

    fun copyImageToClipboard() {
        val image = state.currentImage ?: return
        try {
            val img = javax.imageio.ImageIO.read(image.path.toFile()) ?: return
            val buf = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(img, "PNG", buf)
            // AWT clipboard for images
            val transferable = object : java.awt.datatransfer.Transferable {
                override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
                override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor?) = flavor == java.awt.datatransfer.DataFlavor.imageFlavor
                override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor?) = img
            }
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
            showSnackbar(Strings.Snackbar.imageCopied)
        } catch (_: Exception) {
            showErrorSnackbar(Strings.Snackbar.copyFailed)
        }
    }

    fun showExifInfo() {
        val image = state.currentImage ?: return
        scope.launch {
            exifData = withContext(Dispatchers.IO) { ImageEditor.readExifCached(image.path, galleryIndex) }
            showInfoDialog = true
        }
    }

    fun toggleSlideshow() {
        slideshowActive = !slideshowActive
        state = state.copy(isSlideshowActive = slideshowActive)
    }

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val frame = awtWindow as? java.awt.Frame ?: return
        val device = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        if (isFullscreen) {
            savedWindowBounds = frame.bounds
            device.fullScreenWindow = frame
        } else {
            device.fullScreenWindow = null
            frame.extendedState = java.awt.Frame.NORMAL
            savedWindowBounds?.let { bounds ->
                frame.bounds = bounds
            }
            savedWindowBounds = null
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (state.screen !is Screen.Viewer) return false
        return when (event.key) {
            Key.Escape -> {
                if (isFullscreen) {
                    toggleFullscreen()
                } else if (state.viewerState.isCropping) {
                    cropModeTransitioning = false
                    state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                } else {
                    state = state.copy(screen = Screen.Gallery)
                    slideshowActive = false
                }
                true
            }
            Key.DirectionLeft, Key.A -> { navigateImage(-1); true }
            Key.DirectionRight, Key.D -> { navigateImage(1); true }
            Key.Spacebar -> { toggleSlideshow(); true }
            Key.F -> { toggleFullscreen(); true }
            Key.Plus, Key.Equals -> { zoomIn(); true }
            Key.Minus -> { zoomOut(); true }
            Key.F2 -> { state = state.copy(viewerState = state.viewerState.copy(scale = 1f, panX = 0f, panY = 0f)); true }
            Key.L -> { rotateLeft(); true }
            Key.R -> { rotateRight(); true }
            Key.I -> { showExifInfo(); true }
            Key.C -> { toggleCrop(); true }
            Key.Delete -> { deleteCurrentImage(); true }
            else -> false
        }
    }

    LaunchedEffect(slideshowActive) {
        if (slideshowActive) {
            while (true) {
                delay(AppConst.SLIDESHOW_DEFAULT_INTERVAL_MS)
                if (!slideshowActive) break
                navigateImage(1)
            }
        }
    }

    LaunchedEffect(Unit) {
        val cachedState = withContext(Dispatchers.IO) { galleryIndex.loadSnapshot() }
        if (cachedState != null) {
            state = cachedState.sortAlbums().pruneEmptyAlbums()
            statusMessage = Strings.Status.loadedCache(state.albums.size)
        } else {
            state = state.copy(isScanning = true)
            statusMessage = Strings.Status.scanning
        }

        val indexer = FileIndexer(AppConst.DEFAULT_SCAN_ROOTS)
        val picturesRoot = initialScanRoots.firstOrNull { it.fileName.toString().contains("Pictures", ignoreCase = true) }
        val priorityPath = state.currentAlbum?.path ?: picturesRoot

        indexer.progressiveScan(initialScanRoots, priorityPath).collect { event ->
            when (event) {
                is ScanEvent.AlbumFound -> {
                    state = state.addAlbum(event.album)
                    statusMessage = Strings.Status.discovered(event.album.name)
                }
                is ScanEvent.ProgressUpdate -> {
                    statusMessage = Strings.Status.progress(event.scannedDirs, event.totalImages)
                }
                is ScanEvent.ScanComplete -> {
                    state = state.sortAlbums().pruneEmptyAlbums().copy(isScanning = false)
                    statusMessage = Strings.Status.summary(state.albums.size, event.totalImages)
                    withContext(Dispatchers.IO) {
                        galleryIndex.saveState(state)
                    }
                }
            }
        }

        fileWatcher.start(this)
    }

    LaunchedEffect(Unit) {
        fileWatcher.events.collect { event ->
            when (event) {
                is FileEvent.AlbumCreated -> {
                    val album = withContext(Dispatchers.IO) { scanSingleDir(event.path) }
                    if (album != null) {
                        state = state.addAlbum(album)
                        statusMessage = Strings.Status.newAlbum(album.name)
                    }
                }
                is FileEvent.AlbumDeleted -> {
                    state = state.removeAlbum(event.path)
                    statusMessage = Strings.Status.albumRemoved
                }
                is FileEvent.AlbumRenamed -> {
                    state = state.renameAlbum(event.oldPath, event.newPath)
                    statusMessage = Strings.Status.albumRenamed
                }
                is FileEvent.ImageCreated -> {
                    state = withContext(Dispatchers.IO) { state.syncAlbum(event.albumPath) }
                }
                is FileEvent.ImageDeleted -> {
                    state = withContext(Dispatchers.IO) { state.syncAlbum(event.albumPath) }
                }
                is FileEvent.AlbumModified -> {
                    state = withContext(Dispatchers.IO) { state.syncAlbum(event.path) }
                }
                is FileEvent.ImageModified -> {
                    state = withContext(Dispatchers.IO) { state.syncAlbum(event.albumPath) }
                }
            }
        }
    }

    LaunchedEffect(state.screen, isFullscreen) {
        if (state.screen is Screen.Viewer) {
            focusRequester.requestFocus()
        }
    }

    val anyDialogOpen = showInfoDialog || showDeleteConfirm || showRenameDialog
            || showCropDialog || showFilePicker || showPermanentDeleteWarning

    LaunchedEffect(anyDialogOpen, isFullscreen) {
        if (state.screen is Screen.Viewer && !anyDialogOpen) {
            focusRequester.requestFocus()
        }
    }

    LinGalleryTheme(darkTheme = isDark) {
        val bg = if (isDark) DarkPalette.BACKGROUND else LightPalette.BACKGROUND
        val surface = if (isDark) DarkPalette.SURFACE else LightPalette.SURFACE
        val onSurface = if (isDark) DarkPalette.ON_SURFACE else LightPalette.ON_SURFACE
        val onSurfaceVariant = if (isDark) DarkPalette.ON_SURFACE_VARIANT else LightPalette.ON_SURFACE_VARIANT
        val primary = if (isDark) DarkPalette.PRIMARY else LightPalette.PRIMARY
        val outlineVariant = if (isDark) DarkPalette.OUTLINE_VARIANT else LightPalette.OUTLINE_VARIANT

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .focusTarget()
                .focusRequester(focusRequester)
                .onKeyEvent(::handleKeyEvent)
        ) {
            when (val screen = state.screen) {
                is Screen.Gallery -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AlbumPanel(
                                albums = state.albums,
                                currentAlbumIndex = state.currentAlbumIndex,
                                onAlbumSelected = { index ->
                                    state = state.copy(currentAlbumIndex = index)
                                },
                                isDark = isDark
                            )

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(outlineVariant)
                            )

                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(AppConst.TOP_BAR_HEIGHT.dp),
                                    color = surface
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = Strings.App.name,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = primary,
                                            letterSpacing = (-0.5).sp
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        state.currentAlbum?.let { album ->
                                            Text(
                                                text = album.path.toString(),
                                                fontSize = 12.sp,
                                                color = onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                    }
                                }

                                HorizontalDivider(color = outlineVariant)

                                AnimatedVisibility(visible = state.isScanning, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    if (state.isScanning) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(Strings.Status.scanningShort, color = onSurfaceVariant, fontSize = 15.sp)
                                        }
                                    } else {
                                        GalleryView(
                                            images = state.currentAlbumImages,
                                            onImageClicked = { index -> loadViewerImage(index) },
                                            onImageDoubleClicked = { index -> loadViewerImage(index) },
                                            isDark = isDark,
                                            hasAlbums = state.albums.isNotEmpty()
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            color = surface.copy(alpha = 0.95f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = statusMessage,
                                    fontSize = 12.sp,
                                    color = onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is Screen.Viewer -> {
            Column(modifier = Modifier.fillMaxSize().background(bg)) {
                // Viewer top bar — stays in tree, hidden via height/alpha during fullscreen
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isFullscreen) 0.dp else AppConst.TOP_BAR_HEIGHT.dp)
                                .graphicsLayer { alpha = if (isFullscreen) 0f else 1f },
                            color = surface
                        ) {
                            AnimatedContent(
                                targetState = state.viewerState.isCropping,
                                transitionSpec = {
                                    if (targetState) {
                                        (slideInVertically(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(280, easing = FastOutSlowInEasing))) togetherWith
                                        (slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(280, easing = FastOutSlowInEasing)))
                                    } else {
                                        (slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(280, easing = FastOutSlowInEasing))) togetherWith
                                        (slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(280, easing = FastOutSlowInEasing)))
                                    }
                                },
                                label = "toolbar_mode"
                            ) { isCropping ->
                                if (isCropping) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TooltipIconButton(
                                            icon = AppIcons.Crop,
                                            tooltip = Strings.Crop.mode,
                                            onClick = {},
                                            tint = primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = Strings.Crop.mode,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primary
                                        )
                                        Spacer(Modifier.weight(1f))
                                        OutlinedButton(
                                            onClick = {
                                                cropModeTransitioning = false
                                                state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                                            },
                                            shape = RoundedCornerShape(20.dp)
                                        ) { Text(Strings.Buttons.cancel) }
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = ::doApplyCrop,
                                            enabled = state.viewerState.cropRect != null,
                                            colors = ButtonDefaults.buttonColors(containerColor = primary),
                                            shape = RoundedCornerShape(20.dp)
                                        ) { Text(Strings.Buttons.apply) }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TooltipIconButton(
                                            icon = AppIcons.ArrowBack,
                                            tooltip = Strings.Tooltips.backGallery,
                                            onClick = {
                                                state = state.copy(screen = Screen.Gallery)
                                                slideshowActive = false
                                            },
                                            tint = onSurface
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = state.currentImage?.name ?: "",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = onSurface
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = Strings.Viewer.counter(state.viewerState.currentIndex + 1, state.currentAlbumImages.size),
                                            fontSize = 12.sp,
                                            color = onSurfaceVariant
                                        )
                                        Spacer(Modifier.weight(1f))
                                        TooltipIconButton(
                                            icon = AppIcons.ZoomOut,
                                            tooltip = Strings.Tooltips.zoomOut,
                                            onClick = ::zoomOut,
                                            tint = onSurface,
                                            preferTooltipAbove = false
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            tonalElevation = 0.dp
                                        ) {
                                            val zoomPercentage = ((state.viewerState.scale * 100).toInt()).coerceIn(1, 9999)
                                            val zoomLabel = if (zoomPercentage >= 1000) "${zoomPercentage / 100}.${(zoomPercentage % 100) / 10}K" else "$zoomPercentage%"
                                            Text(
                                                text = zoomLabel,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .widthIn(min = 46.dp)
                                                    .clickable {
                                                        state = state.copy(viewerState = state.viewerState.copy(scale = 1f, panX = 0f, panY = 0f))
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                        TooltipIconButton(
                                            icon = AppIcons.ZoomIn,
                                            tooltip = Strings.Tooltips.zoomIn,
                                            onClick = ::zoomIn,
                                            tint = onSurface,
                                            preferTooltipAbove = false
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        TooltipIconButton(
                                            icon = if (slideshowActive) AppIcons.Pause else AppIcons.PlayArrow,
                                            tooltip = Strings.Tooltips.slideshow,
                                            onClick = ::toggleSlideshow,
                                            tint = if (slideshowActive) primary else onSurface,
                                            preferTooltipAbove = false
                                        )
                                        TooltipIconButton(
                                            icon = if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                                            tooltip = Strings.Tooltips.fullscreen,
                                            onClick = ::toggleFullscreen,
                                            tint = onSurface,
                                            preferTooltipAbove = false
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            color = outlineVariant,
                            modifier = Modifier
                                .height(if (isFullscreen) 0.dp else 1.dp)
                                .graphicsLayer { alpha = if (isFullscreen) 0f else 1f }
                        )

                        // Image viewer content area — single stable composable, nav columns overlaid
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().graphicsLayer { clip = true }) {
                            ImageViewer(
                                path = state.currentImage?.path,
                                scale = state.viewerState.scale,
                                panX = state.viewerState.panX,
                                panY = state.viewerState.panY,
                                imageLastModified = state.currentImage?.lastModified ?: 0L,
                                isCropping = state.viewerState.isCropping,
                                cropRect = state.viewerState.cropRect?.let {
                                    androidx.compose.ui.geometry.Rect(it.x, it.y, it.x + it.width, it.y + it.height)
                                },
                                onScaleChange = { scale -> state = state.copy(viewerState = state.viewerState.copy(scale = scale)) },
                                onPanChange = { px, py -> state = state.copy(viewerState = state.viewerState.copy(panX = px, panY = py)) },
                                onCropRectChange = { displayCrop ->
                                    state = state.copy(
                                        viewerState = state.viewerState.copy(
                                            cropRect = if (displayCrop != null) CropRect(
                                                x = displayCrop.rect.left, y = displayCrop.rect.top,
                                                width = displayCrop.rect.width, height = displayCrop.rect.height
                                            ) else null
                                        )
                                    )
                                },
                                images = state.currentAlbumImages,
                                currentIndex = state.viewerState.currentIndex,
                                modifier = Modifier.fillMaxSize()
                            )

                            NavColumnOverlay(
                                visible = !state.viewerState.isCropping,
                                bg = bg,
                                onSurface = onSurface,
                                prevEnabled = state.viewerState.currentIndex > 0,
                                nextEnabled = state.viewerState.currentIndex < state.currentAlbumImages.size - 1,
                                onPrev = { navigateImage(-1) },
                                onNext = { navigateImage(1) }
                            )

                            CropZoomControl(
                                visible = state.viewerState.isCropping,
                                scale = state.viewerState.scale,
                                onZoomIn = ::zoomIn,
                                onZoomOut = ::zoomOut,
                                onReset = {
                                    state = state.copy(viewerState = state.viewerState.copy(scale = 1f))
                                }
                            )

                            if (isFullscreen) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(40.dp)
                                        .stablePointerHoverIcon(PointerIcon.Hand)
                                        .clickable { toggleFullscreen() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.FullscreenExit,
                                        contentDescription = Strings.ContentDesc.exitFullscreen,
                                        tint = onSurface
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = outlineVariant,
                            modifier = Modifier
                                .height(if (isFullscreen) 0.dp else 1.dp)
                                .graphicsLayer { alpha = if (isFullscreen) 0f else 1f }
                        )

                        // Bottom edit toolbar — hidden during crop mode
                        AnimatedVisibility(
                            visible = !state.viewerState.isCropping && !isFullscreen,
                            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                                    slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it },
                            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)) +
                                   slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it }
                        ) {
                            EditToolbar(
                                isEditableFormat = state.currentImage?.let {
                                    it.extension in AppConst.EDITABLE_FORMATS
                                } ?: false,
                                onRotateLeft = ::rotateLeft,
                                onRotateRight = ::rotateRight,
                                onFlipH = ::flipHorizontal,
                                onCrop = ::toggleCrop,
                                onCopyClipboard = ::copyImageToClipboard,
                                onCopyName = ::copyImageNameToClipboard,
                                onCopyPath = ::copyToClipboard,
                                onMove = { pendingFileOp = "move"; showFilePicker = true },
                                onCopyFile = { pendingFileOp = "copy"; showFilePicker = true },
                                onRename = ::showRenameDialog,
                                onInfo = ::showExifInfo,
                                onDelete = ::deleteCurrentImage,
                                isDark = isDark
                            )
                        }
                    }
                }
            }

            // File picker dialog
            if (showFilePicker) {
                FilePickerDialog(
                    onDismiss = {
                        showFilePicker = false
                        pendingFileOp = ""
                    },
                    onFolderSelected = { targetFolder ->
                        showFilePicker = false
                        val op = pendingFileOp
                        pendingFileOp = ""
                        if (op == "move" || op == "copy") {
                            val image = state.currentImage ?: return@FilePickerDialog
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        val destPath = uniqueDestination(targetFolder.resolve(image.name))
                                        if (op == "move") Files.move(image.path, destPath)
                                        else Files.copy(image.path, destPath)
                                        val imgInfo = readImageFileInfo(destPath)
                                        destPath to imgInfo
                                    } catch (_: Exception) { null to null }
                                }
                                val (dest, newImage) = result
                                if (dest != null && newImage != null) {
                                    if (op == "move") {
                                        state = state.removeImage(image.path.parent, image.path)
                                        state = state.copy(screen = Screen.Gallery)
                                    }
                                    state = state.addImage(targetFolder, newImage)
                                    val srcName = image.path.parent?.fileName?.toString() ?: "?"
                                    val dstName = targetFolder.fileName?.toString() ?: "?"
                                    val title = if (op == "move") Strings.Snackbar.transferTitleMoved else Strings.Snackbar.transferTitleCopied
                                    val details = Strings.Snackbar.transferDetails(srcName, dstName)
                                    showStructuredSnackbar(title, details)
                                } else {
                                    showErrorSnackbar(Strings.Snackbar.operationFailed)
                                }
                            }
                        }
                    }
                )
            }

            // EXIF info dialog
            if (showInfoDialog && exifData != null) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text(Strings.Dialogs.details, fontWeight = FontWeight.ExtraBold) },
                    text = {
                        Column {
                            Text(Strings.Labels.filenameColon(state.currentImage?.name ?: ""), fontSize = 12.sp, color = onSurface)
                            Spacer(Modifier.height(8.dp))
                            exifData!!.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(key, fontSize = 12.sp, color = onSurfaceVariant, modifier = Modifier.weight(0.4f))
                                    Text(value, fontSize = 12.sp, color = onSurface, modifier = Modifier.weight(0.6f))
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text(Strings.Buttons.close) } }
                )
            }

            // Rename dialog
            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text(Strings.Dialogs.renameImage, fontWeight = FontWeight.ExtraBold) },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text(Strings.Labels.filename) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primary,
                                cursorColor = primary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = ::executeRename,
                            colors = ButtonDefaults.buttonColors(containerColor = primary),
                            shape = RoundedCornerShape(20.dp)
                        ) { Text(Strings.Buttons.rename) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) { Text(Strings.Buttons.cancel) }
                    }
                )
            }

            // Crop choice dialog
            if (showCropDialog) {
                AlertDialog(
                    onDismissRequest = { showCropDialog = false },
                    title = { Text(Strings.Dialogs.saveCrop, fontWeight = FontWeight.ExtraBold) },
                    text = { Text(Strings.Crop.saveQuestion) },
                    confirmButton = {
                        Button(
                            onClick = { executeCrop("copy") },
                            colors = ButtonDefaults.buttonColors(containerColor = primary),
                            shape = RoundedCornerShape(20.dp)
                        ) { Text(Strings.Buttons.saveCopy) }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { showCropDialog = false }) { Text(Strings.Buttons.cancel) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { executeCrop("overwrite") }) { Text(Strings.Buttons.overwrite) }
                        }
                    }
                )
            }

            // Delete confirmation
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = {
                        permanentDeleteChecked = false
                        showDeleteConfirm = false
                    },
                    title = { Text(Strings.Dialogs.deleteImage) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = Strings.ContentDesc.delete,
                                modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
                                tint = DarkPalette.ERROR
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(Strings.Dialogs.deleteConfirm(state.currentImage?.name ?: ""))
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = permanentDeleteChecked,
                                    onCheckedChange = { permanentDeleteChecked = it }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(Strings.Dialogs.permanentDeleteCheck, fontSize = 13.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = ::confirmDelete, colors = ButtonDefaults.buttonColors(containerColor = DarkPalette.ERROR)) {
                            Text(Strings.Buttons.delete)
                        }
                    },
                    dismissButton = { TextButton(onClick = {
                        permanentDeleteChecked = false
                        showDeleteConfirm = false
                    }) { Text(Strings.Buttons.cancel) } }
                )
            }

            // Permanent delete warning
            if (showPermanentDeleteWarning) {
                AlertDialog(
                    onDismissRequest = { showPermanentDeleteWarning = false },
                    title = { Text(Strings.Dialogs.permanentlyDelete) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = AppIcons.Warning,
                                contentDescription = Strings.ContentDesc.warning,
                                modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
                                tint = DarkPalette.ERROR
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(Strings.Dialogs.permanentDeleteWarning(state.currentImage?.name ?: ""))
                        }
                    },
                    confirmButton = {
                        Button(onClick = ::confirmPermanentDelete, colors = ButtonDefaults.buttonColors(containerColor = DarkPalette.ERROR)) {
                            Text(Strings.Buttons.deletePermanently)
                        }
                    },
                    dismissButton = { TextButton(onClick = { showPermanentDeleteWarning = false }) { Text(Strings.Buttons.cancel) } }
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val snackbarData = snackbarHostState.currentSnackbarData
                var lastSnackbarData by remember { mutableStateOf<SnackbarData?>(null) }
                var lastDataIsError by remember { mutableStateOf(false) }
                var lastSnackbarTitle by remember { mutableStateOf("") }
                var lastSnackbarDetails by remember { mutableStateOf("") }
                if (snackbarData != null) {
                    lastSnackbarData = snackbarData
                    lastDataIsError = snackbarIsError
                    lastSnackbarTitle = snackbarTitle
                    lastSnackbarDetails = snackbarDetails
                }

                AnimatedVisibility(
                    visible = snackbarData != null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
                    lastSnackbarData?.let { data ->
                        val hasAction = data.visuals.actionLabel != null
                        val style = when {
                            hasAction && lastDataIsError -> SnackbarStyle.ERROR_ACTION
                            hasAction -> SnackbarStyle.ACTION
                            lastDataIsError -> SnackbarStyle.ERROR
                            else -> SnackbarStyle.SUCCESS
                        }

                        LinGallerySnackbar(
                            message = data.visuals.message,
                            title = lastSnackbarTitle,
                            details = lastSnackbarDetails,
                            style = style,
                            modifier = Modifier.padding(top = (AppConst.TOP_BAR_HEIGHT + 12).dp),
                            actionLabel = data.visuals.actionLabel,
                            onAction = { data.performAction() },
                            onDismiss = { data.dismiss() },
                            autoClose = snackbarAutoClose,
                            isDismissible = snackbarIsDismissible,
                            closeIconStyle = snackbarCloseIconStyle,
                            showCloseButton = snackbarShowCloseButton
                        )
                    }
                }

                LaunchedEffect(snackbarData) {
                    val effectiveAutoClose = if (!snackbarAutoClose && !snackbarIsDismissible) true else snackbarAutoClose
                    if (snackbarData != null && effectiveAutoClose) {
                        val hasAction = snackbarData.visuals.actionLabel != null
                        when (snackbarData.visuals.duration) {
                            SnackbarDuration.Short -> delay(4000L)
                            SnackbarDuration.Long -> delay(10000L)
                            SnackbarDuration.Indefinite -> {
                                if (hasAction) delay(7000L) else return@LaunchedEffect
                            }
                        }
                        snackbarData.dismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun NavColumnOverlay(
    visible: Boolean,
    bg: Color,
    onSurface: Color,
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it },
            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)) +
                   slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it }
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                TooltipIconButton(
                    icon = AppIcons.NavigateBefore,
                    tooltip = Strings.Tooltips.previous,
                    onClick = onPrev,
                    enabled = prevEnabled,
                    tint = onSurface
                )
            }
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)) +
                   slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it }
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                TooltipIconButton(
                    icon = AppIcons.NavigateNext,
                    tooltip = Strings.Tooltips.next,
                    onClick = onNext,
                    enabled = nextEnabled,
                    tint = onSurface
                )
            }
        }
    }
}

@Composable
private fun CropZoomControl(
    visible: Boolean,
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.9f),
        exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) +
               scaleOut(tween(200, easing = FastOutSlowInEasing), targetScale = 0.9f)
    ) {
        Box(Modifier.fillMaxSize()) {
            FloatingZoomControl(
                scale = scale,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onReset = onReset,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            )
        }
    }
}

