package com.soufianodev.lingallery.shared.filesystem

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun safeListDir(dir: Path, timeoutMs: Long = 5000): List<Path> =
    withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                Files.list(dir).use { stream -> stream.toList() }
            }
        } catch (_: TimeoutException) { emptyList() }
        catch (_: IOException) { emptyList() }
        catch (_: SecurityException) { emptyList() }
    }

suspend fun safeIsDirectory(path: Path, timeoutMs: Long = 3000): Boolean =
    withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) { Files.isDirectory(path) }
        } catch (_: TimeoutException) { false }
        catch (_: IOException) { false }
        catch (_: SecurityException) { false }
    }
