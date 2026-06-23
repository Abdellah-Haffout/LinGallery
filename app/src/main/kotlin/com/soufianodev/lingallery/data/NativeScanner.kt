package com.soufianodev.lingallery.data

import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * JNI bridge to the Rust native file scanner.
 * The native method scans multiple root directories in parallel using jwalk,
 * returning a tab-separated string of (path, size, lastModified) per image file.
 */
object NativeScanner {

    private val mtpScope = CoroutineScope(Dispatchers.IO)
    private val thumbnailQueue = Channel<Path>(Channel.UNLIMITED)
    val thumbnailDownloadedFlow = MutableSharedFlow<Path>(
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    init {
        NativeLibLoader.load()
        mtpScope.launch {
            for (path in thumbnailQueue) {
                try {
                    val cacheFile = MtpCacheManager.downloadThumbnailIfNeeded(path)
                    if (cacheFile != null) {
                        thumbnailDownloadedFlow.emit(path)
                    }
                } catch (e: Exception) {
                    System.err.println("[LinGallery] Failed to download thumbnail for $path: ${e.message}")
                }
            }
        }
    }

    val isAvailable: Boolean
        get() = NativeLibLoader.isAvailable

    /**
     * Scans the given root paths natively.
     * @param rootsStr semicolon-separated absolute root paths, e.g. "/home/user/Pictures;/home/user/Downloads"
     * @return tab-separated lines: "path\tsize\tlastModified\n" for each image file found
     */
    @JvmStatic
    external fun nativeScan(rootsStr: String): String

    @JvmStatic
    external fun nativeMtpDetectConnectedDevices(): String

    @JvmStatic
    external fun nativeMtpGetDeviceStorages(serial: String): String

    @JvmStatic
    external fun nativeMtpScanDevice(serial: String, storageId: Int, model: String, callback: MtpScanCallback)

    @JvmStatic
    external fun nativeMtpDownloadThumbnail(serial: String, storageId: Int, handle: Int, cachePath: String): Boolean

    @JvmStatic
    external fun nativeMtpDownloadFile(serial: String, storageId: Int, handle: Int, cachePath: String): Boolean


    /**
     * Parse the native scan result into a map of album directory -> list of ImageFile.
     */
    fun scanToAlbums(roots: List<Path>, currentAlbumPath: Path? = null): List<Album> {
        val rootsStr = roots.joinToString(";") { it.toAbsolutePath().toString() }
        val result = nativeScan(rootsStr)

        val albumImagesMap = linkedMapOf<Path, MutableList<ImageFile>>()

        for (line in result.lineSequence()) {
            if (line.isEmpty()) continue
            val firstTab = line.indexOf('\t')
            if (firstTab < 0) continue
            val secondTab = line.indexOf('\t', firstTab + 1)
            if (secondTab < 0) continue

            val pathStr = line.substring(0, firstTab)
            val sizeStr = line.substring(firstTab + 1, secondTab)
            val mtimeStr = line.substring(secondTab + 1)

            val size = sizeStr.toLongOrNull() ?: continue
            val lastModified = mtimeStr.toLongOrNull() ?: continue
            val path = Path.of(pathStr)
            val parent = path.parent ?: continue
            val name = path.fileName.toString()
            val dot = name.lastIndexOf('.')
            val ext = if (dot >= 0) name.substring(dot).lowercase() else ""

            albumImagesMap.getOrPut(parent) { mutableListOf() }.add(
                ImageFile(
                    path = path,
                    name = name,
                    extension = ext,
                    size = size,
                    lastModified = lastModified
                )
            )
        }

        val albums = albumImagesMap.map { (dir, images) ->
            val sorted = images.distinctBy { it.path }.sortedByDescending { it.lastModified }
            Album(
                path = dir,
                name = dir.fileName.toString(),
                images = sorted,
                previewPath = sorted.first().path
            )
        }

        // Prioritize current album path if specified
        if (currentAlbumPath != null) {
            val normalizedCurrent = if (currentAlbumPath.isMtp()) {
                currentAlbumPath.toAbsolutePath().normalize()
            } else {
                try {
                    currentAlbumPath.toRealPath()
                } catch (_: Exception) {
                    currentAlbumPath.toAbsolutePath().normalize()
                }
            }
            val currentAlbum = albums.find {
                val norm = if (it.path.isMtp()) {
                    it.path.toAbsolutePath().normalize()
                } else {
                    try { it.path.toRealPath() } catch (_: Exception) { it.path.toAbsolutePath().normalize() }
                }
                norm == normalizedCurrent
            }
            if (currentAlbum != null) {
                return listOf(currentAlbum) + (albums - currentAlbum)
            }
        }

        return albums
    }

    fun scanMtpDevices(
        devicesStr: String? = null,
        onProgress: (scannedDirs: Int, totalImages: Int, currentFolderName: String, progress: Float) -> Unit = { _, _, _, _ -> },
        onAlbumFound: (Album) -> Unit = {}
    ): List<Album> {
        val resultList = mutableListOf<Album>()
        if (!isAvailable) return resultList

        val resolvedDevicesStr = if (devicesStr.isNullOrEmpty()) {
            nativeMtpDetectConnectedDevices()
        } else {
            devicesStr
        }
        if (resolvedDevicesStr.isEmpty()) return resultList

        for (deviceLine in resolvedDevicesStr.lineSequence()) {
            if (deviceLine.isEmpty()) continue
            val parts = deviceLine.split("\t")
            if (parts.size < 3) continue
            val serial = parts[0]
            val manufacturer = parts[1]
            val model = parts[2]
            
            val storagesStr = nativeMtpGetDeviceStorages(serial)
            if (storagesStr.isEmpty()) continue

            for (storagePart in storagesStr.split(",")) {
                val sParts = storagePart.split(":")
                if (sParts.size < 2) continue
                val storageId = sParts[0].toIntOrNull() ?: continue
                val storageDesc = sParts[1]

                // Scan this storage progressively using JNI callback
                nativeMtpScanDevice(
                    serial = serial,
                    storageId = storageId,
                    model = model,
                    callback = object : MtpScanCallback {
                        override fun onProgress(
                            scannedDirs: Int,
                            totalImages: Int,
                            currentFolderName: String,
                            topFolderIdx: Int,
                            totalTopFolders: Int
                        ) {
                            val progress = if (totalTopFolders > 0) {
                                topFolderIdx.toFloat() / totalTopFolders.toFloat()
                            } else {
                                0f
                            }
                            onProgress(scannedDirs, totalImages, currentFolderName, progress)
                        }

                        override fun onAlbumFound(
                            albumPath: String,
                            albumName: String,
                            imagesData: String
                        ) {
                            val albumImages = mutableListOf<ImageFile>()
                            for (line in imagesData.lineSequence()) {
                                if (line.isEmpty()) continue
                                val firstTab = line.indexOf('\t')
                                if (firstTab < 0) continue
                                val secondTab = line.indexOf('\t', firstTab + 1)
                                if (secondTab < 0) continue

                                val pathStr = line.substring(0, firstTab)
                                val sizeStr = line.substring(firstTab + 1, secondTab)
                                val mtimeStr = line.substring(secondTab + 1)

                                val size = sizeStr.toLongOrNull() ?: continue
                                val lastModified = mtimeStr.toLongOrNull() ?: continue
                                val path = Path.of(pathStr)
                                val name = path.fileName.toString()
                                val dot = name.lastIndexOf('.')
                                val ext = if (dot >= 0) name.substring(dot).lowercase() else ""

                                albumImages.add(
                                    ImageFile(
                                        path = path,
                                        name = name,
                                        extension = ext,
                                        size = size,
                                        lastModified = lastModified
                                    )
                                )
                                thumbnailQueue.trySend(path)
                            }

                            if (albumImages.isNotEmpty()) {
                                val sorted = albumImages.distinctBy { it.path }.sortedByDescending { it.lastModified }
                                val album = Album(
                                    path = Path.of(albumPath),
                                    name = albumName,
                                    images = sorted,
                                    previewPath = sorted.first().path
                                )
                                resultList.add(album)
                                onAlbumFound(album)
                            }
                        }
                    }
                )
            }
        }
        return resultList
    }
}

interface MtpScanCallback {
    fun onProgress(
        scannedDirs: Int,
        totalImages: Int,
        currentFolderName: String,
        topFolderIdx: Int,
        totalTopFolders: Int
    )

    fun onAlbumFound(
        albumPath: String,
        albumName: String,
        imagesData: String
    )
}


data class MtpDetails(
    val serial: String,
    val storageId: Int,
    val handle: Int,
    val filename: String
)

fun Path.isMtp(): Boolean = this.toString().startsWith("/mtp/")

fun Path.toMtpDetails(): MtpDetails? {
    val str = this.toString()
    if (!str.startsWith("/mtp/")) return null
    // Path format: /mtp/[serial]/[storage_id]/[folder_path]/[handle]_[filename]
    val parts = str.substring(5).split("/")
    if (parts.size < 3) return null
    val serial = parts[0]
    val storageId = parts[1].toIntOrNull() ?: return null
    
    val lastSegment = parts.last()
    val underscoreIdx = lastSegment.indexOf('_')
    if (underscoreIdx < 0) return null
    val handle = lastSegment.substring(0, underscoreIdx).toIntOrNull() ?: return null
    val filename = lastSegment.substring(underscoreIdx + 1)
    return MtpDetails(serial, storageId, handle, filename)
}

object MtpCacheManager {
    fun getMtpThumbnailCachePath(details: MtpDetails): Path {
        val userHome = System.getProperty("user.home")
        return Path.of(userHome, ".cache", "lingallery", "mtp", "thumbnails", details.serial, "${details.storageId}_${details.handle}_${details.filename}")
    }

    fun getMtpFullFileCachePath(details: MtpDetails): Path {
        val userHome = System.getProperty("user.home")
        return Path.of(userHome, ".cache", "lingallery", "mtp", "full", details.serial, "${details.storageId}_${details.handle}_${details.filename}")
    }

    fun downloadThumbnailIfNeeded(path: Path): Path? {
        val details = path.toMtpDetails() ?: return null
        val cacheFile = getMtpThumbnailCachePath(details)
        if (java.nio.file.Files.exists(cacheFile)) {
            return cacheFile
        }
        val success = NativeScanner.nativeMtpDownloadThumbnail(
            details.serial,
            details.storageId,
            details.handle,
            cacheFile.toAbsolutePath().toString()
        )
        return if (success) cacheFile else null
    }

    fun downloadFileIfNeeded(path: Path): Path? {
        val details = path.toMtpDetails() ?: return null
        val cacheFile = getMtpFullFileCachePath(details)
        if (java.nio.file.Files.exists(cacheFile)) {
            return cacheFile
        }
        val success = NativeScanner.nativeMtpDownloadFile(
            details.serial,
            details.storageId,
            details.handle,
            cacheFile.toAbsolutePath().toString()
        )
        return if (success) cacheFile else null
    }
}

