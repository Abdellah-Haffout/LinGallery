package com.soufianodev.lingallery.phone.data

import com.soufianodev.lingallery.model.PhoneInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface PhoneEvent {
    data class Connected(val phone: PhoneInfo) : PhoneEvent
    data class Disconnected(val phoneId: String) : PhoneEvent
}

class GvfsPhoneDetector(
    private val gvfsPath: Path? = resolveGvfsPath(),
    private val pollIntervalMs: Long = 1000L
) {

    fun observe(): Flow<PhoneEvent> = channelFlow {
        val gvfsDir = gvfsPath ?: return@channelFlow
        if (!Files.isDirectory(gvfsDir)) return@channelFlow

        val knownPhones = ConcurrentHashMap<String, PhoneInfo>()

        val watchService: WatchService? = try {
            val ws = FileSystems.getDefault().newWatchService()
            gvfsDir.register(
                ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE
            )
            ws
        } catch (e: IOException) {
            println("[GvfsPhoneDetector] WatchService registration failed: ${e.message}. Falling back to polling-only.")
            null
        }

        suspend fun isPhoneAccessible(mountPath: Path): Boolean {
            if (!Files.exists(mountPath) || !Files.isDirectory(mountPath)) return false
            return try {
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(2000L) {
                        runInterruptible {
                            Files.list(mountPath).use { stream ->
                                stream.anyMatch { Files.exists(it) }
                            }
                        }
                    } == true
                }
            } catch (e: Exception) {
                false
            }
        }

        suspend fun detectCurrentPhones(): Map<String, PhoneInfo> {
            val result = mutableMapOf<String, PhoneInfo>()
            try {
                val dirs = withContext(Dispatchers.IO) {
                    val list = mutableListOf<Path>()
                    Files.list(gvfsDir).use { stream ->
                        stream.filter { Files.isDirectory(it) }.forEach { list.add(it) }
                    }
                    list
                }
                for (dir in dirs) {
                    val name = dir.fileName.toString()
                    if (name.startsWith("mtp:") && Files.exists(dir)) {
                        try {
                            if (isPhoneAccessible(dir)) {
                                val info = resolvePhoneInfo(dir)
                                result[info.id] = info
                            }
                        } catch (e: Exception) {
                            println("[GvfsPhoneDetector] Failed to resolve phone info for $dir: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("[GvfsPhoneDetector] Failed to list GVFS dir: ${e.message}")
            }
            return result
        }

        val initial = detectCurrentPhones()
        println("[GvfsPhoneDetector] Initial scan: found ${initial.size} phone(s): ${initial.values.map { it.name }}")
        for ((id, info) in initial) {
            knownPhones[id] = info
            trySend(PhoneEvent.Connected(info))
        }

        if (watchService != null) {
            launch {
                try {
                    while (isActive) {
                        val key = watchService.poll(1, TimeUnit.SECONDS)
                        if (key != null) {
                            key.pollEvents().forEach { event ->
                                val filename = event.context() as? Path ?: return@forEach
                                val fullPath = gvfsDir.resolve(filename)
                                when (event.kind()) {
                                    StandardWatchEventKinds.ENTRY_CREATE -> {
                                        if (filename.toString().startsWith("mtp:")) {
                                            // GVFS creates the dir entry before the FUSE mount is
                                            // fully ready, so retry isDirectory/accessibility a few times.
                                            launch {
                                                var ready = false
                                                repeat(6) {
                                                    if (!ready) {
                                                        delay(500)
                                                        if (isPhoneAccessible(fullPath)) {
                                                            ready = true
                                                        }
                                                    }
                                                }
                                                if (!ready) {
                                                    println("[GvfsPhoneDetector] WatchService: $filename appeared but is not accessible after retries")
                                                    return@launch
                                                }
                                                val info = resolvePhoneInfo(fullPath)
                                                if (!knownPhones.containsKey(info.id)) {
                                                    println("[GvfsPhoneDetector] WatchService: phone connected: ${info.name} (${info.id})")
                                                    knownPhones[info.id] = info
                                                    trySend(PhoneEvent.Connected(info))
                                                }
                                            }
                                        }
                                    }
                                    StandardWatchEventKinds.ENTRY_DELETE -> {
                                        val deletedName = filename.toString()
                                        val removed = knownPhones.filter { (_, info) ->
                                            info.mountPath.fileName.toString() == deletedName
                                        }
                                        for ((id, _) in removed) {
                                            println("[GvfsPhoneDetector] WatchService: phone disconnected: $deletedName")
                                            knownPhones.remove(id)
                                            trySend(PhoneEvent.Disconnected(id))
                                        }
                                    }
                                }
                            }
                            if (!key.reset()) {
                                for (id in knownPhones.keys.toList()) {
                                    knownPhones.remove(id)
                                    trySend(PhoneEvent.Disconnected(id))
                                }
                                break
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                } catch (_: ClosedWatchServiceException) {
                } catch (e: Exception) {
                    println("[GvfsPhoneDetector] WatchService error: ${e.message}")
                } finally {
                    watchService.close()
                }
            }
        }

        // Polling loop as a fallback / steady-state reconciliation.
        // Use a longer interval than the gio info subprocess duration to avoid overlap.
        launch {
            while (isActive) {
                delay(3000L)
                val current = detectCurrentPhones()
                for ((id, info) in current) {
                    if (!knownPhones.containsKey(id)) {
                        println("[GvfsPhoneDetector] Poll: new phone detected: ${info.name} (${info.id})")
                        knownPhones[id] = info
                        trySend(PhoneEvent.Connected(info))
                    }
                }
                val removed = knownPhones.keys - current.keys
                for (id in removed) {
                    println("[GvfsPhoneDetector] Poll: phone gone: $id")
                    knownPhones.remove(id)
                    trySend(PhoneEvent.Disconnected(id))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun resolvePhoneInfo(mountPath: Path): PhoneInfo {
        val (name, serial) = try {
            val process = ProcessBuilder("gio", "info", mountPath.toString())
                .redirectErrorStream(true)
                .start()
            val output = try {
                process.inputStream.bufferedReader().readText()
            } finally {
                val exited = process.waitFor(5, TimeUnit.SECONDS)
                if (!exited) process.destroyForcibly()
            }
            val displayName = Regex("standard::display-name:\\s+(.+)").find(output)
                ?.groupValues?.get(1)?.trim()
                ?: Regex("^display name:\\s+(.+)", RegexOption.MULTILINE).find(output)
                    ?.groupValues?.get(1)?.trim()
                ?: sanitizeMountName(mountPath)
            // Use id::filesystem as a stable unique identifier for the device.
            // It typically looks like "mtp:host=VENDOR_PRODUCT_SERIAL" and is
            // consistent across reconnects for the same physical device.
            val stableId = Regex("id::filesystem:\\s+(.+)").find(output)
                ?.groupValues?.get(1)?.trim()
            displayName to stableId
        } catch (_: Exception) {
            sanitizeMountName(mountPath) to null
        }

        val raw = if (serial != null) serial else name
        val digest = MessageDigest.getInstance("SHA-256")
        val id = digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        return PhoneInfo(id = id, name = name, mountPath = mountPath)
    }

    private fun sanitizeMountName(mountPath: Path): String {
        val raw = mountPath.fileName.toString()
        return raw
            .removePrefix("mtp:host=")
            .replace("_", " ")
            .replace("+", " ")
            .trim()
            .ifEmpty { "Phone" }
    }

    companion object {
        fun resolveGvfsPath(): Path? {
            val xdg = System.getenv("XDG_RUNTIME_DIR")
            if (xdg != null) {
                val path = Path.of(xdg, "gvfs")
                if (Files.exists(path)) return path
            }
            return try {
                val uid = ProcessBuilder("id", "-u")
                    .start()
                    .inputStream.bufferedReader()
                    .readLine()
                    ?.trim()
                if (uid != null) {
                    val path = Path.of("/run/user", uid, "gvfs")
                    if (Files.exists(path)) path else null
                } else null
            } catch (_: Exception) { null }
        }
    }
}
