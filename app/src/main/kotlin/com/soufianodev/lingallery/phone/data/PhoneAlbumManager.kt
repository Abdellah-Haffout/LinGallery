package com.soufianodev.lingallery.phone.data

import com.soufianodev.lingallery.app.AppConst
import com.soufianodev.lingallery.model.ImageFile
import com.soufianodev.lingallery.model.PhoneInfo
import com.soufianodev.lingallery.model.PhoneScanEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.util.logging.Logger
import java.nio.file.Path
import java.util.Calendar
import java.util.LinkedList

class PhoneAlbumManager(
    private val thumbnailCache: PhoneThumbnailCache,
    private val supportedFormats: Set<String> = AppConst.SUPPORTED_FORMATS
) {
    private val activePhones = mutableMapOf<String, PhoneInfo>()
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

        val batchBuffer = mutableListOf<ImageFile>()
        var lastEmitTime = System.currentTimeMillis()
        val batchLimit = 100
        val emitIntervalMs = 500L

        val timedOut = withTimeoutOrNull(120_000) {
            while (dirQueue.isNotEmpty() && currentCoroutineContext().isActive) {
                val dir = dirQueue.removeFirst()
                if (!visited.add(dir)) continue
                // Use runInterruptible so that if the coroutine is cancelled the
                // underlying blocking thread is actually interrupted (GVFS/MTP
                // calls can hang indefinitely; plain withTimeout only throws
                // TimeoutCancellationException but leaves the thread blocked).
                val dirExists = try {
                    withTimeout(5000) {
                        runInterruptible { Files.isDirectory(dir) }
                    }
                } catch (_: TimeoutCancellationException) { false }
                  catch (_: IOException) { false }
                  catch (_: SecurityException) { false }
                if (!dirExists) continue
                val name = dir.fileName.toString()
                if (name.startsWith(".") || name in SKIP_DIRS) continue

                val batch = mutableListOf<ImageFile>()
                val subdirs = mutableListOf<Path>()

                try {
                    withTimeout(10_000) {
                        runInterruptible {
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
                    batchBuffer.addAll(sorted)
                }

                // Chunk and emit if we exceed batchLimit
                while (batchBuffer.size >= batchLimit) {
                    val toEmit = batchBuffer.subList(0, batchLimit).toList()
                    repeat(batchLimit) { batchBuffer.removeFirst() }
                    emit(PhoneScanEvent.ImagesFound(toEmit))
                    lastEmitTime = System.currentTimeMillis()
                }

                // Or if some time has passed and we have any accumulated images
                val now = System.currentTimeMillis()
                if (batchBuffer.isNotEmpty() && now - lastEmitTime >= emitIntervalMs) {
                    val toEmit = batchBuffer.toList()
                    batchBuffer.clear()
                    emit(PhoneScanEvent.ImagesFound(toEmit))
                    lastEmitTime = now
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
        } == null

        // Emit any remaining buffered items
        if (batchBuffer.isNotEmpty()) {
            emit(PhoneScanEvent.ImagesFound(batchBuffer.toList()))
            batchBuffer.clear()
        }

        val snapshot = allImages.toList()
        scannedImages[info.id] = snapshot
        emit(PhoneScanEvent.Complete(snapshot.size))

        if (timedOut) {
            logger.warning("BFS scan timed out, partial results: ${allImages.size} images")
        }
    }.flowOn(Dispatchers.IO)

    fun onPhoneDisconnected(phoneId: String): Path? {
        val info = activePhones.remove(phoneId)
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
                ensureActive()
                val enriched = batch.map { image ->
                    try {
                        val size = try {
                            withTimeout(5000) { runInterruptible { Files.size(image.path) } }
                        } catch (_: TimeoutCancellationException) { 0L }
                          catch (_: IOException) { 0L }
                        val mtime = try {
                            withTimeout(5000) { runInterruptible { Files.getLastModifiedTime(image.path) } }
                        } catch (_: TimeoutCancellationException) { java.nio.file.attribute.FileTime.fromMillis(0L) }
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
                    val year = g[1].toInt()
                    val month = g[2].toInt()
                    val day = g[3].toInt()
                    val hour = g[4].toInt()
                    val minute = g[5].toInt()
                    val second = g[6].toInt()
                    if (month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59 && second in 0..59) {
                        Calendar.getInstance().apply {
                            set(year, month - 1, day, hour, minute, second)
                        }.timeInMillis
                    } else 0L
                } else {
                    val year = g[7].toInt()
                    val month = g[8].toInt()
                    val day = g[9].toInt()
                    val hour = g[10].toInt()
                    val minute = g[11].toInt()
                    val second = g[12].toInt()
                    if (month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59 && second in 0..59) {
                        Calendar.getInstance().apply {
                            set(year, month - 1, day, hour, minute, second)
                        }.timeInMillis
                    } else 0L
                }
            } catch (_: Exception) { 0L }
        }
    }
}
