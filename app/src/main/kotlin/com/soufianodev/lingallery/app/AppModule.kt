package com.soufianodev.lingallery.app

import com.soufianodev.lingallery.gallery.GalleryRepository
import com.soufianodev.lingallery.gallery.GalleryStateHolder
import com.soufianodev.lingallery.phone.PhoneManager

import com.soufianodev.lingallery.shared.desktop.WindowBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.soufianodev.lingallery.viewer.ViewerStateHolder
import kotlinx.coroutines.CoroutineScope
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.nio.file.Path
import java.nio.file.Paths

class AppModule(
    private val scope: CoroutineScope,
    val awtWindow: java.awt.Window
) {
    private val scanRoots: List<Path> = AppConst.DEFAULT_SCAN_ROOTS.map {
        Paths.get(it.replace("~", System.getProperty("user.home")))
    }

    val galleryRepository = GalleryRepository(scope, scanRoots = scanRoots)
    val galleryStateHolder = GalleryStateHolder(repository = galleryRepository, scope = scope)
    val viewerStateHolder = ViewerStateHolder(
        galleryRepository = galleryRepository,
        scope = scope,
        onImageRemovedFromAlbum = { albumPath, imagePath ->
            galleryStateHolder.updateState { it.removeImage(albumPath, imagePath) }
        },
        onImageAddedToAlbum = { albumPath, _ ->
            galleryStateHolder.updateState { it.syncAlbum(albumPath) }
        }
    )

    private var savedWindowBounds: WindowBounds? = null

    private val phoneNames = mutableMapOf<String, String>()

    val phoneManager = PhoneManager(
        scope = scope,
        onPhoneAlbumAdded = { album ->
            phoneNames[album.path.toString()] = album.name
            galleryStateHolder.updateState { it.addAlbum(album).markPendingPhoneSort(album.path) }
        },
        onPhoneImagesFound = { mountPath, images ->
            galleryStateHolder.updateState { it.appendPhoneImages(mountPath, images) }
        },
        onPhoneAlbumRemoved = { phoneId, path ->
            val wasCurrent = galleryStateHolder.uiState.value.currentAlbum?.path == path
            galleryStateHolder.updateState {
                val state = it.removeAlbum(path).removePhoneScanProgress(phoneId)
                if (wasCurrent) state.copy(currentAlbumIndex = 0)
                else state
            }
            val friendlyName = phoneNames.remove(path.toString()) ?: path.fileName.toString()
            galleryStateHolder.setStatus(Strings.Status.phoneDisconnected(friendlyName))
        },
        onScanProgress = { phoneId, progress ->
            galleryStateHolder.updateState { it.updatePhoneScanProgress(phoneId, progress) }
        },
        onMetadataBatch = { mountPath, phoneId, batch ->
            if (batch.isNotEmpty()) {
                galleryStateHolder.updateState { it.updatePhoneImageMetadata(mountPath, batch) }
            } else {
                val friendlyName = phoneNames[mountPath.toString()] ?: mountPath.fileName.toString()
                val imageCount = galleryStateHolder.uiState.value.albums
                    .firstOrNull { it.path == mountPath }?.images?.size ?: 0
                galleryStateHolder.setStatus(Strings.Status.phoneConnected(friendlyName, imageCount))
                galleryStateHolder.updateState { it.removePhoneScanProgress(phoneId) }
                val currentPath = galleryStateHolder.uiState.value.currentAlbum?.path
                if (currentPath == mountPath) {
                    galleryStateHolder.updateState { it.markPendingPhoneSort(mountPath) }
                } else {
                    galleryStateHolder.updateState { it.sortPhoneByRecent(mountPath) }
                }
            }
        },
        onThumbnailUpdated = { mountPath, imagePath, thumbPath ->
            galleryStateHolder.updateState { it.updatePhoneImageThumbnail(mountPath, imagePath, thumbPath) }
        }
    )

    fun init() {
        galleryStateHolder.init(scope)
        phoneManager.start()
    }

    fun toggleFullscreen(isFullscreen: Boolean) {
        val frame = awtWindow as? Frame ?: return
        val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        if (!isFullscreen) {
            savedWindowBounds = WindowBounds(
                x = frame.bounds.x, y = frame.bounds.y,
                width = frame.bounds.width, height = frame.bounds.height,
                extendedState = frame.extendedState
            )
            device.fullScreenWindow = frame
        } else {
            device.fullScreenWindow = null
            savedWindowBounds?.let { bounds ->
                frame.extendedState = bounds.extendedState
                if (bounds.extendedState and Frame.MAXIMIZED_BOTH == 0) {
                    frame.bounds = Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
                }
            }
            savedWindowBounds = null
        }
    }

    fun undoDelete(onResult: (Boolean, String) -> Unit) {
        val ctx = viewerStateHolder.consumeTrashContext() ?: run {
            onResult(false, Strings.Snackbar.undoFailed)
            return
        }
        scope.launch {
            val restoreOk = withContext(Dispatchers.IO) {
                galleryRepository.restoreFromTrash(ctx.trashPath, ctx.originalPath).isSuccess
            }
            if (!restoreOk) {
                onResult(false, Strings.Snackbar.undoFailed)
                return@launch
            }
            galleryStateHolder.updateState { it.syncAlbum(ctx.originalPath.parent) }
            if (ctx.wasCurrent) {
                val images = galleryStateHolder.uiState.value.currentAlbumImages
                val idx = images.indexOfFirst { it.path == ctx.originalPath }
                if (idx >= 0) {
                    viewerStateHolder.enter(images, idx)
                }
            }
            onResult(true, Strings.Snackbar.restored(ctx.originalPath.fileName.toString()))
        }
    }

    fun cleanup() {
        galleryRepository.stopWatcher()
        galleryRepository.close()
    }
}
