@file:OptIn(ExperimentalMaterial3Api::class)

package com.soufianodev.lingallery

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
        title = "LinGallery",
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
fun Breadcrumbs(
    path: Path?,
    onSegmentClick: (Path) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                val homePath = Paths.get(System.getProperty("user.home"))
                onSegmentClick(homePath)
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = "Home",
                tint = if (isDark) Color(0xFF2DD4BF) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (path != null) {
            val absPath = path.toAbsolutePath().normalize()
            val parts = mutableListOf<Path>()
            var current: Path? = absPath
            while (current != null) {
                parts.add(0, current)
                current = current.parent
            }

            parts.forEachIndexed { index, part ->
                val segmentName = if (part.fileName == null) {
                    part.toString().trimEnd('/', '\\')
                } else {
                    part.fileName.toString()
                }

                if (segmentName.isNotEmpty()) {
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val isLast = index == parts.size - 1
                    Text(
                        text = segmentName,
                        style = if (isLast) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                        color = if (isLast) {
                            if (isDark) Color(0xFF2DD4BF) else MaterialTheme.colorScheme.primary
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSegmentClick(part) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
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
    var statusMessage by remember { mutableStateOf("Scanning for images\u2026") }
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
    var isDark by remember { mutableStateOf(true) }
    var isDynamicColor by remember { mutableStateOf(false) }
    var userSeedColor by remember { mutableStateOf(Color(0xFF4FC3C3)) }
    var dynamicSeedColor by remember { mutableStateOf<Color?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentImage, state.currentAlbumIndex, isDynamicColor) {
        if (isDynamicColor) {
            val path = state.currentImage?.path ?: state.currentAlbum?.previewPath
            if (path != null) {
                withContext(Dispatchers.IO) {
                    val color = extractDominantColor(path.toFile())
                    withContext(Dispatchers.Main) {
                        dynamicSeedColor = color
                    }
                }
            } else {
                dynamicSeedColor = null
            }
        } else {
            dynamicSeedColor = null
        }
    }

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
                        showSnackbar("Saved copy")
                    } else {
                        state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                        showSnackbar("Saved copy")
                    }
                } else {
                    state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                    refreshCurrentImageFile()
                    showSnackbar("Image cropped")
                }
            } else {
                state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                showErrorSnackbar("Crop failed")
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
                showSnackbar("Image renamed")
            } else {
                showErrorSnackbar("Rename failed")
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
                showErrorSnackbar("Rotate failed")
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
                showErrorSnackbar("Rotate failed")
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
                showErrorSnackbar("Flip failed")
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
                showErrorSnackbar("Undo failed")
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
                    showErrorSnackbar("Album already exists")
                    return@launch
                }
                val newAlbums = state.albums.toMutableList()
                newAlbums.add(albumIdx, album)
                state = state.copy(albums = newAlbums)
            }

            val mutableImages = album.images.toMutableList()
            val insertIdx = record.imageIndex.coerceAtMost(mutableImages.size)
            if (mutableImages.any { it.path == record.imageFile.path }) {
                showErrorSnackbar("Image already restored")
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
            showSnackbar("Restored ${record.imageFile.name}")
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
            showSnackbar("Permanently deleted ${image.name}")
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
                showErrorSnackbar("Delete failed")
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
                message = "Deleted ${image.name}",
                actionLabel = "Undo",
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
            showSnackbar("Name copied to clipboard")
        } catch (_: Exception) {
            showErrorSnackbar("Failed to copy name to clipboard")
        }
    }

    fun copyToClipboard() {
        val image = state.currentImage ?: return
        try {
            val selection = java.awt.datatransfer.StringSelection(image.path.toString())
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            showSnackbar("Path copied to clipboard")
        } catch (_: Exception) {
            showErrorSnackbar("Failed to copy to clipboard")
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
            showSnackbar("Image copied to clipboard")
        } catch (_: Exception) {
            showErrorSnackbar("Copy to clipboard failed")
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
            statusMessage = "Loaded ${state.albums.size} albums from cache"
        } else {
            state = state.copy(isScanning = true)
            statusMessage = "Scanning for images\u2026"
        }

        val indexer = FileIndexer(AppConst.DEFAULT_SCAN_ROOTS)
        val picturesRoot = initialScanRoots.firstOrNull { it.fileName.toString().contains("Pictures", ignoreCase = true) }
        val priorityPath = state.currentAlbum?.path ?: picturesRoot

        indexer.progressiveScan(initialScanRoots, priorityPath).collect { event ->
            when (event) {
                is ScanEvent.AlbumFound -> {
                    state = state.addAlbum(event.album)
                    statusMessage = "Discovered: ${event.album.name}"
                }
                is ScanEvent.ProgressUpdate -> {
                    statusMessage = "Scanning\u2026 ${event.scannedDirs} folders checked \u2014 ${event.totalImages} images found"
                }
                is ScanEvent.ScanComplete -> {
                    state = state.sortAlbums().pruneEmptyAlbums().copy(isScanning = false)
                    statusMessage = "${state.albums.size} albums, ${event.totalImages} images"
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
                        statusMessage = "New album: ${album.name}"
                    }
                }
                is FileEvent.AlbumDeleted -> {
                    state = state.removeAlbum(event.path)
                    statusMessage = "Album removed"
                }
                is FileEvent.AlbumRenamed -> {
                    state = state.renameAlbum(event.oldPath, event.newPath)
                    statusMessage = "Album renamed"
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

    val activeSeedColor = if (isDynamicColor && dynamicSeedColor != null) {
        dynamicSeedColor!!
    } else {
        userSeedColor
    }

    LinGalleryTheme(darkTheme = isDark, seedColor = activeSeedColor) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                                onAddAlbumClicked = {
                                    pendingFileOp = "add_album"
                                    showFilePicker = true
                                },
                                onMockItemClicked = { category ->
                                    showSnackbar("$category is currently empty")
                                },
                                isDark = isDark
                            )

                            Scaffold(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                                topBar = {
                                    TopAppBar(
                                        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                                        title = {
                                            Breadcrumbs(
                                                path = state.currentAlbum?.path,
                                                onSegmentClick = { part ->
                                                    val idx = state.albums.indexOfFirst { it.path.toAbsolutePath().normalize() == part.toAbsolutePath().normalize() }
                                                    if (idx >= 0) {
                                                        state = state.copy(currentAlbumIndex = idx)
                                                    } else {
                                                        scope.launch {
                                                            val album = withContext(Dispatchers.IO) { scanSingleDir(part) }
                                                            if (album != null) {
                                                                state = state.addAlbum(album)
                                                                val newIdx = state.albums.indexOfFirst { it.path.toAbsolutePath().normalize() == part.toAbsolutePath().normalize() }
                                                                if (newIdx >= 0) {
                                                                    state = state.copy(currentAlbumIndex = newIdx)
                                                                }
                                                            } else {
                                                                showSnackbar("No images in folder: ${part.fileName}")
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                        actions = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                var searchQueryText by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }
                                                TextField(
                                                    value = searchQueryText,
                                                    onValueChange = {
                                                        searchQueryText = it
                                                        state = state.copy(searchQuery = it)
                                                    },
                                                    placeholder = { Text("Search", style = MaterialTheme.typography.bodyMedium) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Search,
                                                            contentDescription = "Search",
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    singleLine = true,
                                                    colors = TextFieldDefaults.colors(
                                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        disabledIndicatorColor = Color.Transparent,
                                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    ),
                                                    shape = RoundedCornerShape(100.dp),
                                                    modifier = Modifier
                                                        .width(200.dp)
                                                        .height(38.dp),
                                                    textStyle = MaterialTheme.typography.bodyMedium
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .padding(horizontal = 12.dp)
                                                        .width(1.dp)
                                                        .height(24.dp)
                                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        showSettingsDialog = true
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Settings,
                                                        contentDescription = "Settings",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.background
                            ) { paddingValues ->
                                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    AnimatedVisibility(visible = state.isScanning, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }

                                    state.currentAlbum?.let { album ->
                                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                            Text(
                                                text = album.name,
                                                style = MaterialTheme.typography.headlineLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "Sorted by Date Added • Descending",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    val filteredImages = remember(state.currentAlbumImages, state.searchQuery) {
                                        if (state.searchQuery.isEmpty()) {
                                            state.currentAlbumImages
                                        } else {
                                            state.currentAlbumImages.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
                                        }
                                    }

                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        if (state.isScanning) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Scanning\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        } else {
                                            GalleryView(
                                                images = filteredImages,
                                                onImageClicked = { index ->
                                                    val image = filteredImages.getOrNull(index)
                                                    if (image != null) {
                                                        val fullIndex = state.currentAlbumImages.indexOf(image)
                                                        if (fullIndex >= 0) loadViewerImage(fullIndex)
                                                    }
                                                },
                                                onImageDoubleClicked = { index ->
                                                    val image = filteredImages.getOrNull(index)
                                                    if (image != null) {
                                                        val fullIndex = state.currentAlbumImages.indexOf(image)
                                                        if (fullIndex >= 0) loadViewerImage(fullIndex)
                                                    }
                                                },
                                                isDark = isDark,
                                                hasAlbums = state.albums.isNotEmpty()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = statusMessage,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is Screen.Viewer -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF1B2A2C), // Center: Elegant dark teal
                                        Color(0xFF090E0F)  // Edges: Near pitch black
                                    )
                                )
                            )
                    ) {
                        // 1. ImageViewer content area — fills the entire screen
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

                        // 2. NavColumnOverlay (floating side navigation buttons)
                        NavColumnOverlay(
                            visible = !state.viewerState.isCropping,
                            prevEnabled = state.viewerState.currentIndex > 0,
                            nextEnabled = state.viewerState.currentIndex < state.currentAlbumImages.size - 1,
                            onPrev = { navigateImage(-1) },
                            onNext = { navigateImage(1) }
                        )

                        // 3. Top bar as overlay at the top (with a vertical scrim)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(if (isFullscreen) 0.dp else AppConst.TOP_BAR_HEIGHT.dp)
                                .graphicsLayer { alpha = if (isFullscreen) 0f else 1f }
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
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
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Crop,
                                            contentDescription = "Crop Mode",
                                            tint = Color(0xFF2DD4BF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Crop Mode",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2DD4BF)
                                        )
                                        Spacer(Modifier.weight(1f))
                                        OutlinedButton(
                                            onClick = {
                                                cropModeTransitioning = false
                                                state = state.copy(viewerState = state.viewerState.copy(isCropping = false, cropRect = null))
                                            },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            shape = RoundedCornerShape(20.dp)
                                        ) { Text("Cancel") }
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = ::doApplyCrop,
                                            enabled = state.viewerState.cropRect != null,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2DD4BF),
                                                contentColor = Color(0xFF003737),
                                                disabledContainerColor = Color(0xFF1E2D30),
                                                disabledContentColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        ) { Text("Apply") }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Breadcrumbs(
                                            path = state.currentImage?.path,
                                            onSegmentClick = { part ->
                                                if (Files.isDirectory(part)) {
                                                    val idx = state.albums.indexOfFirst { it.path.toAbsolutePath().normalize() == part.toAbsolutePath().normalize() }
                                                    if (idx >= 0) {
                                                        state = state.copy(currentAlbumIndex = idx, screen = Screen.Gallery)
                                                    } else {
                                                        scope.launch {
                                                            val album = withContext(Dispatchers.IO) { scanSingleDir(part) }
                                                            if (album != null) {
                                                                state = state.addAlbum(album)
                                                                val newIdx = state.albums.indexOfFirst { it.path.toAbsolutePath().normalize() == part.toAbsolutePath().normalize() }
                                                                if (newIdx >= 0) {
                                                                    state = state.copy(currentAlbumIndex = newIdx, screen = Screen.Gallery)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            isDark = true
                                        )

                                        Spacer(Modifier.weight(1f))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Zoom control capsule
                                            Surface(
                                                shape = RoundedCornerShape(100.dp),
                                                color = Color(0x99141D1E),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                ) {
                                                    IconButton(onClick = ::zoomOut, modifier = Modifier.size(28.dp)) {
                                                        Icon(
                                                            imageVector = AppIcons.ZoomOut,
                                                            contentDescription = "Zoom out",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = Color(0xFFE2E8F0)
                                                        )
                                                    }
                                                    val zoomPercentage = ((state.viewerState.scale * 100).toInt()).coerceIn(1, 9999)
                                                    Text(
                                                        text = "$zoomPercentage%",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFE2E8F0),
                                                        modifier = Modifier
                                                            .padding(horizontal = 8.dp)
                                                            .clickable {
                                                                state = state.copy(viewerState = state.viewerState.copy(scale = 1f, panX = 0f, panY = 0f))
                                                            }
                                                    )
                                                    IconButton(onClick = ::zoomIn, modifier = Modifier.size(28.dp)) {
                                                        Icon(
                                                            imageVector = AppIcons.ZoomIn,
                                                            contentDescription = "Zoom in",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = Color(0xFFE2E8F0)
                                                        )
                                                    }
                                                }
                                            }

                                            // Slideshow control
                                            IconButton(onClick = ::toggleSlideshow) {
                                                Icon(
                                                    imageVector = if (slideshowActive) AppIcons.Pause else AppIcons.PlayArrow,
                                                    contentDescription = "Slideshow",
                                                    tint = if (slideshowActive) Color(0xFF2DD4BF) else Color(0xFFE2E8F0)
                                                )
                                            }

                                            // Fullscreen control
                                            IconButton(onClick = ::toggleFullscreen) {
                                                Icon(
                                                    imageVector = if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                                                    contentDescription = "Fullscreen",
                                                    tint = Color(0xFFE2E8F0)
                                                )
                                            }

                                            // Settings / Mode control
                                            IconButton(
                                                onClick = {
                                                    showSettingsDialog = true
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Settings,
                                                    contentDescription = "Settings",
                                                    tint = Color(0xFFE2E8F0)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. CropZoomControl overlay
                        CropZoomControl(
                            visible = state.viewerState.isCropping,
                            scale = state.viewerState.scale,
                            onZoomIn = ::zoomIn,
                            onZoomOut = ::zoomOut,
                            onReset = {
                                state = state.copy(viewerState = state.viewerState.copy(scale = 1f))
                            }
                        )

                        // 5. Bottom edit toolbar overlay
                        AnimatedVisibility(
                            visible = !state.viewerState.isCropping && !isFullscreen,
                            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                                    slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it },
                            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)) +
                                   slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
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

                        // Esc fullscreen Box
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
                                    contentDescription = "Exit Fullscreen (Esc)",
                                    tint = Color.White
                                )
                            }
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
                        if (op == "add_album") {
                            scope.launch {
                                val album = withContext(Dispatchers.IO) { scanSingleDir(targetFolder) }
                                if (album != null) {
                                    state = state.addAlbum(album)
                                    showSnackbar("Added album: ${album.name}")
                                } else {
                                    showErrorSnackbar("No images found in selected folder")
                                }
                            }
                        } else if (op == "move" || op == "copy") {
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
                                    val title = if (op == "move") "Image moved" else "Image copied"
                                    val details = "$srcName ⟶ $dstName"
                                    showStructuredSnackbar(title, details)
                                } else {
                                    showErrorSnackbar("Operation failed")
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
                    title = { Text("Details", fontWeight = FontWeight.ExtraBold) },
                    text = {
                        Column {
                            Text("Filename: ${state.currentImage?.name ?: ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            exifData!!.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(key, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                                    Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.6f))
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } }
                )
            }

            // Rename dialog
            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Rename Image", fontWeight = FontWeight.ExtraBold) },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("File Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = ::executeRename,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("Rename") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // Crop choice dialog
            if (showCropDialog) {
                AlertDialog(
                    onDismissRequest = { showCropDialog = false },
                    title = { Text("Save Crop", fontWeight = FontWeight.ExtraBold) },
                    text = { Text("How do you want to save the cropped image?") },
                    confirmButton = {
                        Button(
                            onClick = { executeCrop("copy") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("Save as Copy") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { showCropDialog = false }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { executeCrop("overwrite") }) { Text("Overwrite") }
                        }
                    }
                )
            }

            // Delete confirmation
            if (showDeleteConfirm) {
                val image = state.currentImage ?: return@LinGalleryTheme
                AlertDialog(
                    onDismissRequest = {
                        permanentDeleteChecked = false
                        showDeleteConfirm = false
                    },
                    title = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(16.dp))
                            Text("Delete Image", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    text = {
                        Column {
                            Text("Are you sure you want to delete ${image.name}?", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { permanentDeleteChecked = !permanentDeleteChecked }) {
                                Checkbox(checked = permanentDeleteChecked, onCheckedChange = { permanentDeleteChecked = it })
                                Spacer(Modifier.width(8.dp))
                                Text("Permanently delete", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = ::confirmDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            // Permanent delete warning
            if (showPermanentDeleteWarning) {
                val image = state.currentImage ?: return@LinGalleryTheme
                AlertDialog(
                    onDismissRequest = { showPermanentDeleteWarning = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AppIcons.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(16.dp))
                            Text("Permanently Delete", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    text = {
                        Text("This action cannot be undone. Are you absolutely sure you want to permanently delete ${image.name}?", style = MaterialTheme.typography.bodyMedium)
                    },
                    confirmButton = {
                        Button(onClick = ::confirmPermanentDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Delete Permanently")
                        }
                    },
                    dismissButton = { TextButton(onClick = { showPermanentDeleteWarning = false }) { Text("Cancel") } }
                )
            }

            // Settings Dialog
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Settings / الإعدادات", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // 1. Dark Mode switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDark = !isDark }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Dark Theme / المظهر الداكن", style = MaterialTheme.typography.bodyLarge)
                                    Text("Enable dark mode for the entire application / تفعيل الوضع الداكن للتطبيق بالكامل", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = isDark,
                                    onCheckedChange = { isDark = it }
                                )
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // 2. Dynamic Color switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDynamicColor = !isDynamicColor }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Dynamic Color / الألوان الحيوية (المطابقة)", style = MaterialTheme.typography.bodyLarge)
                                    Text("Match theme accent color with the active image or album cover / مطابقة لون التطبيق تلقائياً مع ألوان الصورة أو الألبوم الحالي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = isDynamicColor,
                                    onCheckedChange = { isDynamicColor = it }
                                )
                            }

                            if (!isDynamicColor) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                // 3. Preset color chooser
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Accent Color / اللون الأساسي المفضل", style = MaterialTheme.typography.bodyLarge)
                                    Text("Select a custom theme accent / اختر لونك المفضل للتطبيق", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    
                                    val presets = listOf(
                                        Pair("Cyan", Color(0xFF4FC3C3)),
                                        Pair("Light Blue", Color(0xFF03A9F4)),
                                        Pair("Blue", Color(0xFF2196F3)),
                                        Pair("Dark Blue", Color(0xFF1A237E)),
                                        Pair("Indigo", Color(0xFF3F51B5)),
                                        Pair("Purple", Color(0xFF9C27B0)),
                                        Pair("Green", Color(0xFF4CAF50)),
                                        Pair("Orange", Color(0xFFFF9800)),
                                        Pair("Red", Color(0xFFE91E63))
                                    )
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        presets.forEach { (name, color) ->
                                            val isSelected = userSeedColor == color
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(100.dp))
                                                    .background(color)
                                                    .border(
                                                        width = if (isSelected) 3.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(100.dp)
                                                    )
                                                    .clickable { userSeedColor = color },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showSettingsDialog = false }) {
                            Text("Done / تم")
                        }
                    }
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
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible && prevEnabled,
            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it },
            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)) +
                   slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Surface(
                onClick = onPrev,
                shape = RoundedCornerShape(50.dp),
                color = Color.Black.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.NavigateBefore,
                        contentDescription = "Previous (←)",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = visible && nextEnabled,
            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)) +
                   slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            Surface(
                onClick = onNext,
                shape = RoundedCornerShape(50.dp),
                color = Color.Black.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.NavigateNext,
                        contentDescription = "Next (→)",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
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

fun extractDominantColor(file: java.io.File): Color {
    try {
        if (!file.exists() || !file.isFile) return Color(0xFF4FC3C3)
        val image = javax.imageio.ImageIO.read(file) ?: return Color(0xFF4FC3C3)
        val w = image.width
        val h = image.height
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        val stepX = maxOf(1, w / 15)
        val stepY = maxOf(1, h / 15)
        for (x in 0 until w step stepX) {
            for (y in 0 until h step stepY) {
                val rgb = image.getRGB(x, y)
                r += (rgb shr 16) and 0xFF
                g += (rgb shr 8) and 0xFF
                b += rgb and 0xFF
                count++
            }
        }
        if (count == 0) return Color(0xFF4FC3C3)
        return Color((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    } catch (_: Exception) {
        return Color(0xFF4FC3C3)
    }
}

