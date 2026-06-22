package com.soufianodev.lingallery.phone

import com.soufianodev.lingallery.model.Album
import com.soufianodev.lingallery.model.ImageFile
import com.soufianodev.lingallery.model.PhoneScanEvent
import com.soufianodev.lingallery.model.PhoneScanProgress
import com.soufianodev.lingallery.phone.data.GvfsPhoneDetector
import com.soufianodev.lingallery.phone.data.PhoneAlbumManager
import com.soufianodev.lingallery.phone.data.PhoneEvent
import com.soufianodev.lingallery.phone.data.PhoneThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

private class PhoneActiveJobs(
    val scanJob: Job,
    var enrichmentJob: Job? = null,
    var thumbnailJob: Job? = null
) {
    fun cancelAll() {
        scanJob.cancel()
        enrichmentJob?.cancel()
        thumbnailJob?.cancel()
    }
}

class PhoneManager(
    private val scope: CoroutineScope,
    private val onPhoneAlbumAdded: (Album) -> Unit,
    private val onPhoneImagesFound: (mountPath: Path, images: List<ImageFile>) -> Unit,
    private val onPhoneAlbumRemoved: (phoneId: String, mountPath: Path) -> Unit,
    private val onScanProgress: (String, PhoneScanProgress) -> Unit,
    private val onMetadataBatch: (mountPath: Path, phoneId: String, batch: List<ImageFile>) -> Unit = { _, _, _ -> },
    private val onThumbnailUpdated: (mountPath: Path, imagePath: Path, thumbPath: Path) -> Unit = { _, _, _ -> }
) {
    private val thumbnailCache = PhoneThumbnailCache()
    private val albumManager = PhoneAlbumManager(thumbnailCache)
    private val detector = GvfsPhoneDetector()
    private val activePhoneJobs = java.util.concurrent.ConcurrentHashMap<String, PhoneActiveJobs>()

    fun start() {
        scope.launch {
            withContext(Dispatchers.IO) { thumbnailCache.cleanupOnStartup() }
        }
        scope.launch {
            delay(200)
            try {
                detector.observe().collect { event ->
                    when (event) {
                        is PhoneEvent.Connected -> {
                            val phone = event.phone
                            println("[PhoneManager] Phone connected: ${phone.name} at ${phone.mountPath}")
                            activePhoneJobs.remove(phone.id)?.cancelAll()

                            val scanJob = scope.launch {
                                try {
                                    albumManager.onPhoneConnected(phone, scope).collect { scanEvent ->
                                        println("[PhoneManager] Received scanEvent: $scanEvent")
                                        when (scanEvent) {
                                            is PhoneScanEvent.AlbumCreated -> {
                                                println("[PhoneManager] Adding album: ${scanEvent.phoneName} at ${scanEvent.mountPath}")
                                                onPhoneAlbumAdded(Album(
                                                    path = scanEvent.mountPath,
                                                    name = scanEvent.phoneName,
                                                    images = emptyList(),
                                                    previewPath = null,
                                                    isPhoneAlbum = true
                                                ))
                                            }
                                            is PhoneScanEvent.Progress -> {
                                                println("[PhoneManager] Progress: discovered ${scanEvent.imagesDiscovered} in ${scanEvent.scannedDirs} dirs")
                                                onScanProgress(phone.id, PhoneScanProgress(
                                                    phoneName = phone.name,
                                                    imagesDiscovered = scanEvent.imagesDiscovered,
                                                    isScanning = true
                                                ))
                                            }
                                            is PhoneScanEvent.ImagesFound -> {
                                                println("[PhoneManager] ImagesFound: ${scanEvent.images.size} images")
                                                onPhoneImagesFound(phone.mountPath, scanEvent.images)
                                            }
                                            is PhoneScanEvent.Complete -> {
                                                println("[PhoneManager] Complete: total ${scanEvent.totalImages} images")
                                                onScanProgress(phone.id, PhoneScanProgress(
                                                    phoneName = phone.name,
                                                    imagesDiscovered = scanEvent.totalImages,
                                                    isScanning = false
                                                ))
                                                val snapshot = albumManager.getImageSnapshot(phone.id)
                                                if (snapshot != null) {
                                                    val enrichmentJob = albumManager.enrichMetadataAsync(snapshot, scope) { batch ->
                                                        onMetadataBatch(phone.mountPath, phone.id, batch)
                                                    }
                                                    enrichmentJob.invokeOnCompletion {
                                                        onMetadataBatch(phone.mountPath, phone.id, emptyList())
                                                    }
                                                    val thumbnailJob = thumbnailCache.generateThumbnails(
                                                        phone.id, snapshot, scope
                                                    ) { updatedImage ->
                                                        val thumbPath = updatedImage.thumbnailPath
                                                        if (thumbPath != null) {
                                                            onThumbnailUpdated(phone.mountPath, updatedImage.path, thumbPath)
                                                        }
                                                    }
                                                    activePhoneJobs[phone.id]?.let { jobs ->
                                                        jobs.enrichmentJob = enrichmentJob
                                                        jobs.thumbnailJob = thumbnailJob
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("[Phone] Scan failed for ${phone.name}: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                            activePhoneJobs[phone.id] = PhoneActiveJobs(scanJob)
                        }
                        is PhoneEvent.Disconnected -> {
                            activePhoneJobs.remove(event.phoneId)?.cancelAll()
                            val mountPath = albumManager.onPhoneDisconnected(event.phoneId)
                            if (mountPath != null) {
                                onPhoneAlbumRemoved(event.phoneId, mountPath)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("[Phone] Detection failed: ${e.message}")
            }
        }
    }
}
