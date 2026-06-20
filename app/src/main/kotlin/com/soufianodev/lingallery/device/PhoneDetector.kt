package com.soufianodev.lingallery.device

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

interface PhoneDetector {
    fun observe(): Flow<PhoneEvent>
}

sealed interface PhoneEvent {
    data class Connected(val phone: PhoneInfo) : PhoneEvent
    data class Disconnected(val phoneId: String) : PhoneEvent
}

data class PhoneInfo(
    val id: String,
    val name: String,
    val mountPath: Path
)

class GvfsPhoneDetector(
    private val gvfsPath: Path? = resolveGvfsPath(),
    private val pollIntervalMs: Long = 1000L
) : PhoneDetector {

    override fun observe(): Flow<PhoneEvent> = channelFlow {
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

        fun detectCurrentPhones(): Map<String, PhoneInfo> {
            val result = mutableMapOf<String, PhoneInfo>()
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                Files.list(gvfsDir).use { stream ->
                    stream.filter { Files.isDirectory(it) }.forEach { dir ->
                        val name = dir.fileName.toString()
                        if (name.startsWith("mtp:")) {
                            val future = executor.submit(java.util.concurrent.Callable {
                                try {
                                    Files.list(dir).use { it.findFirst().isPresent }
                                    true
                                } catch (_: Exception) { false }
                            })
                            val isAlive = try {
                                future.get(2, java.util.concurrent.TimeUnit.SECONDS)
                            } catch (_: java.util.concurrent.TimeoutException) { false }
                            catch (_: Exception) { false }
                            if (isAlive) {
                                val info = resolvePhoneInfo(dir)
                                result[info.id] = info
                            }
                        }
                    }
                }
            } catch (e: IOException) {
            } catch (e: SecurityException) {
            } finally {
                executor.shutdownNow()
            }
            return result
        }

        val initial = detectCurrentPhones()
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
                                        if (Files.isDirectory(fullPath) && filename.toString().startsWith("mtp:")) {
                                            val info = resolvePhoneInfo(fullPath)
                                            if (!knownPhones.containsKey(info.id)) {
                                                knownPhones[info.id] = info
                                                trySend(PhoneEvent.Connected(info))
                                            }
                                        }
                                    }
                                    StandardWatchEventKinds.ENTRY_DELETE -> {
                                        val deletedName = filename.toString()
                                        val removed = knownPhones.filter { (_, info) ->
                                            info.mountPath.fileName.toString() == deletedName
                                        }
                                        for ((id, _) in removed) {
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

        launch {
            while (isActive) {
                delay(pollIntervalMs)
                val current = detectCurrentPhones()
                for ((id, info) in current) {
                    if (!knownPhones.containsKey(id)) {
                        knownPhones[id] = info
                        trySend(PhoneEvent.Connected(info))
                    }
                }
                val removed = knownPhones.keys - current.keys
                for (id in removed) {
                    knownPhones.remove(id)
                    trySend(PhoneEvent.Disconnected(id))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun resolvePhoneInfo(mountPath: Path): PhoneInfo {
        val (name, serial) = try {
            val process = ProcessBuilder("gio", "info", mountPath.toString())
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val displayName = Regex("standard::display-name:\\s(.+)").find(output)
                ?.groupValues?.get(1)?.trim() ?: sanitizeMountName(mountPath)
            val serialNumber = Regex("standard::serial-number:\\s(.+)").find(output)
                ?.groupValues?.get(1)?.trim()
            displayName to serialNumber
        } catch (_: Exception) {
            sanitizeMountName(mountPath) to null
        }

        val raw = if (serial != null) "$name::$serial" else name
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
