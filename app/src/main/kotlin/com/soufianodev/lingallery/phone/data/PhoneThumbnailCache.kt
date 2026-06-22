package com.soufianodev.lingallery.phone.data

import com.soufianodev.lingallery.app.AppConst
import com.soufianodev.lingallery.model.ImageFile
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class PhoneThumbnailCache(
    private val cacheRoot: Path = AppConst.PHONE_THUMBNAIL_DIR,
    private val gracePeriodMs: Long = 45_000L,
    private val thumbMaxDim: Int = 400
) {
    private val graceTimers = mutableMapOf<String, Job>()

    fun get(phoneId: String, imagePath: Path): Path? {
        val cached = cacheRoot.resolve(phoneId).resolve(hashPath(imagePath) + ".jpg")
        return if (Files.exists(cached)) cached else null
    }

    fun hasCache(phoneId: String): Boolean {
        val dir = cacheRoot.resolve(phoneId)
        if (!Files.isDirectory(dir)) return false
        return try {
            Files.list(dir).use { stream ->
                stream.anyMatch { it.fileName.toString().endsWith(".jpg") }
            }
        } catch (_: Exception) { false }
    }

    fun generateThumbnails(
        phoneId: String,
        snapshot: List<ImageFile>,
        scope: CoroutineScope,
        onProgress: (ImageFile) -> Unit
    ): Job {
        return scope.launch(Dispatchers.IO) {
            val dir = cacheRoot.resolve(phoneId)
            Files.createDirectories(dir)
            for (image in snapshot) {
                if (!isActive) break
                val hash = hashPath(image.path)
                val thumbPath = dir.resolve("$hash.jpg")
                if (Files.exists(thumbPath)) {
                    onProgress(image.copy(thumbnailPath = thumbPath))
                    continue
                }
                try {
                    val img = ImmutableImage.loader().fromFile(image.path.toFile())
                    val thumb = img.cover(thumbMaxDim, thumbMaxDim)
                    thumb.output(JpegWriter(), thumbPath.toFile())
                    onProgress(image.copy(thumbnailPath = thumbPath))
                } catch (_: Exception) {
                }
            }
        }
    }

    fun startGracePeriod(phoneId: String) {
        try {
            val dir = cacheRoot.resolve(phoneId)
            Files.createDirectories(dir)
            Files.writeString(dir.resolve(".disconnected"), System.currentTimeMillis().toString())
        } catch (_: Exception) {}

        graceTimers[phoneId]?.cancel()
        graceTimers[phoneId] = backgroundScope.launch {
            delay(gracePeriodMs)
            purge(phoneId)
        }
    }

    fun cancelGracePeriod(phoneId: String) {
        graceTimers[phoneId]?.cancel()
        graceTimers.remove(phoneId)
        try {
            Files.deleteIfExists(cacheRoot.resolve(phoneId).resolve(".disconnected"))
        } catch (_: Exception) {}
    }

    fun cleanupOnStartup() {
        if (!Files.isDirectory(cacheRoot)) return
        val now = System.currentTimeMillis()
        try {
            Files.list(cacheRoot).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach { dir ->
                    val marker = dir.resolve(".disconnected")
                    if (Files.exists(marker)) {
                        val timestamp = try {
                            Files.readString(marker).trim().toLongOrNull()
                        } catch (_: Exception) { null }
                        if (timestamp != null && now - timestamp >= gracePeriodMs) {
                            dir.toFile().deleteRecursively()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun purge(phoneId: String) {
        graceTimers.remove(phoneId)?.cancel()
        try {
            val dir = cacheRoot.resolve(phoneId)
            if (Files.isDirectory(dir)) {
                dir.toFile().deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private val backgroundScope = CoroutineScope(Dispatchers.IO)

        private fun hashPath(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(path.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
