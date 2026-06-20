package com.soufianodev.lingallery.device

import com.soufianodev.lingallery.data.ImageFile
import com.soufianodev.lingallery.theme.AppConst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.util.logging.Logger
import java.nio.file.Path
import java.util.Calendar
import java.util.LinkedList

data class PhoneScanProgress(
    val phoneName: String,
    val imagesDiscovered: Int,
    val isScanning: Boolean
)

sealed interface PhoneScanEvent {
    data class AlbumCreated(val phoneName: String, val mountPath: Path) : PhoneScanEvent
    data class Progress(val scannedDirs: Int, val imagesDiscovered: Int) : PhoneScanEvent
    data class ImagesFound(val images: List<ImageFile>) : PhoneScanEvent
    data class Complete(val totalImages: Int) : PhoneScanEvent
}

class PhoneAlbumManager(
    private val thumbnailCache: PhoneThumbnailCache,
    private val supportedFormats: Set<String> = AppConst.SUPPORTED_FORMATS
) {
    private val activePhones = mutableMapOf<String, PhoneInfo>()
    private val scanningJobs = mutableMapOf<String, Job>()
    private val scannedImages = mutableMapOf<String, List<ImageFile>>()

    fun onPhoneConnected(info: PhoneInfo, scope: CoroutineScope): Flow<PhoneScanEvent> = flow {
        activePhones[info.id] = info

        if (thumbnailCache.hasCache(info.id)) {
            thumbnailCache.cancelGracePeriod(info.id)
        }

        emit(PhoneScanEvent.AlbumCreated(info.name, info.mountPath))

        val allImages = mutableListOf<ImageFile>()
        val visited = mutableSetOf<Path>()
        val dirQueue = LinkedList<Path>()
        dirQueue.addLast(info.mountPath)

        var scannedDirs = 0
        var lastDirEmit = 0
        var lastImageEmit = 0

        try {
            withTimeout(30_000) {
            while (dirQueue.isNotEmpty() && currentCoroutineContext().isActive) {
                val dir = dirQueue.removeFirst()
                if (!visited.add(dir)) continue
                val dirExists = try { withTimeout(3000) { Files.isDirectory(dir) } }
                    catch (_: TimeoutCancellationException) { false }
                    catch (_: IOException) { false }
                    catch (_: SecurityException) { false }
                if (!dirExists) continue
                val name = dir.fileName.toString()
                if (name.startsWith(".") || name in SKIP_DIRS) continue

                val batch = mutableListOf<ImageFile>()
                val subdirs = mutableListOf<Path>()

                try {
                    withTimeout(5000) {
                        Files.list(dir).use { stream ->
                            stream.forEach { entry ->
                                try {
                                    if (Files.isDirectory(entry)) {
                                        val en = entry.fileName.toString()
                                        if (!en.startsWith(".") && en !in SKIP_DIRS) {
                                            subdirs.add(entry)
                                        }
                                    } else {
                                        val ext = extensionOf(entry)
                                        if (ext in supportedFormats) {
                                            batch.add(
                                                ImageFile(
                                                    path = entry,
                                                    name = entry.fileName.toString(),
                                                    extension = ext,
                                                    size = 0L,
                                                    lastModified = parseTimestampFromFilename(entry.fileName.toString())
                                                )
                                            )
                                        }
                                    }
                                } catch (e: IOException) { }
                                catch (e: UncheckedIOException) { }
                                catch (e: SecurityException) { }
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                } catch (e: IOException) {
                } catch (e: UncheckedIOException) {
                } catch (e: SecurityException) {
                } catch (e: Exception) {
                    logger.warning("Unexpected error scanning directory $dir: ${e.message}")
                }

                scannedDirs++

                if (batch.isNotEmpty()) {
                    val sorted = batch.sortedByDescending { it.lastModified }
                    allImages.addAll(sorted)
                    emit(PhoneScanEvent.ImagesFound(sorted))
                }

                if (scannedDirs - lastDirEmit >= 5 || allImages.size - lastImageEmit >= 50) {
                    emit(PhoneScanEvent.Progress(scannedDirs, allImages.size))
                    lastDirEmit = scannedDirs
                    lastImageEmit = allImages.size
                }

                for (subdir in subdirs) {
                    if (subdir !in visited) {
                        dirQueue.addLast(subdir)
                    }
                }
            }
            }
        } catch (_: TimeoutCancellationException) {
            logger.warning("BFS scan timed out after 30s")
            scannedImages[info.id] = allImages.toList()
            return@flow
        } catch (e: Exception) {
            logger.warning("BFS scan terminated unexpectedly: ${e.message}")
            scannedImages[info.id] = allImages.toList()
            return@flow
        }

        val snapshot = allImages.toList()
        scannedImages[info.id] = snapshot
        emit(PhoneScanEvent.Complete(snapshot.size))
    }.flowOn(Dispatchers.IO)

    fun onPhoneDisconnected(phoneId: String): Path? {
        val info = activePhones.remove(phoneId)
        scanningJobs.remove(phoneId)?.cancel()
        scannedImages.remove(phoneId)
        if (info != null) {
            thumbnailCache.startGracePeriod(phoneId)
        }
        return info?.mountPath
    }

    fun getImageSnapshot(phoneId: String): List<ImageFile>? = scannedImages[phoneId]

    fun getPhoneName(phoneId: String): String? = activePhones[phoneId]?.name

    fun enrichMetadataAsync(
        snapshot: List<ImageFile>,
        scope: CoroutineScope,
        onBatch: (List<ImageFile>) -> Unit
    ): Job {
        return scope.launch(Dispatchers.IO) {
            snapshot.chunked(20).forEach { batch ->
                val enriched = batch.map { image ->
                    try {
                        val size = try { withTimeout(3000) { Files.size(image.path) } }
                            catch (_: TimeoutCancellationException) { 0L }
                            catch (_: IOException) { 0L }
                        val mtime = try { withTimeout(3000) { Files.getLastModifiedTime(image.path) } }
                            catch (_: TimeoutCancellationException) { java.nio.file.attribute.FileTime.fromMillis(0L) }
                            catch (_: IOException) { java.nio.file.attribute.FileTime.fromMillis(0L) }
                        image.copy(size = size, lastModified = mtime.toMillis())
                    } catch (e: IOException) { image }
                    catch (e: SecurityException) { image }
                }
                onBatch(enriched)
            }
        }
    }

    private fun extensionOf(path: Path): String {
        val name = path.fileName.toString()
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot).lowercase() else ""
    }

    companion object {
        private val logger = Logger.getLogger(PhoneAlbumManager::class.java.name)
        private val SKIP_DIRS = setOf("lost+found", "Android", "System Volume Information", "\$RECYCLE.BIN")

        private val TIMESTAMP_REGEX = Regex(
            """(?:(\d{4})(\d{2})(\d{2})[-_](\d{2})(\d{2})(\d{2})|""" +
            """(\d{4})-(\d{2})-(\d{2})\s+at\s+(\d{2})\.(\d{2})\.(\d{2}))"""
        )

        private fun parseTimestampFromFilename(name: String): Long {
            val match = TIMESTAMP_REGEX.find(name) ?: return 0L
            return try {
                val g = match.groupValues
                if (g[1].isNotEmpty()) {
                    Calendar.getInstance().apply {
                        set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(),
                            g[4].toInt(), g[5].toInt(), g[6].toInt())
                    }.timeInMillis
                } else {
                    Calendar.getInstance().apply {
                        set(g[7].toInt(), g[8].toInt() - 1, g[9].toInt(),
                            g[10].toInt(), g[11].toInt(), g[12].toInt())
                    }.timeInMillis
                }
            } catch (_: Exception) { 0L }
        }
    }
}
