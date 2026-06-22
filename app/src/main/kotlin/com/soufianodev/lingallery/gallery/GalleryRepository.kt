package com.soufianodev.lingallery.gallery

import com.soufianodev.lingallery.app.AppConst
import com.soufianodev.lingallery.gallery.data.FileIndexer
import com.soufianodev.lingallery.gallery.data.FileWatcher
import com.soufianodev.lingallery.gallery.data.GalleryIndex
import com.soufianodev.lingallery.gallery.data.FileEvent
import com.soufianodev.lingallery.gallery.data.ScanEvent
import com.soufianodev.lingallery.model.Album
import com.soufianodev.lingallery.model.ImageFile
import com.soufianodev.lingallery.shared.imaging.ImageEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension

class GalleryRepository(
    scope: CoroutineScope,
    private val galleryIndex: GalleryIndex = GalleryIndex(),
    scanRoots: List<Path> = emptyList()
) {
    private val fileIndexer = FileIndexer()
    private val fileWatcher = FileWatcher(roots = scanRoots)

    val events: Flow<FileEvent> = fileWatcher.events

    fun progressiveScan(roots: List<Path>, priorityPath: Path? = null): Flow<ScanEvent> =
        fileIndexer.progressiveScan(roots, priorityPath)

    fun startWatcher(scope: CoroutineScope) {
        fileWatcher.start(scope)
    }

    fun stopWatcher() {
        fileWatcher.stop()
    }

    fun loadCachedState(): GalleryUiState? =
        galleryIndex.loadSnapshot()

    fun saveState(state: GalleryUiState) {
        galleryIndex.saveState(state)
    }

    fun close() {
        galleryIndex.close()
    }

    fun scanSingleDir(dir: Path): Album? {
        if (!Files.isDirectory(dir)) return null
        val images = mutableListOf<ImageFile>()
        try {
            Files.list(dir).forEach { path ->
                if (!Files.isDirectory(path)) {
                    val name = path.fileName.toString()
                    val dot = name.lastIndexOf('.')
                    val ext = if (dot >= 0) name.substring(dot).lowercase() else ""
                    if (ext in AppConst.SUPPORTED_FORMATS) {
                        try {
                            val size = Files.size(path)
                            val mtime = Files.getLastModifiedTime(path)
                            images.add(
                                ImageFile(
                                    path = path,
                                    name = name,
                                    extension = ext,
                                    size = size,
                                    lastModified = mtime.toMillis()
                                )
                            )
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) { return null }
        val sortedImages = images.distinctBy { it.path }.sortedByDescending { it.lastModified }
        if (sortedImages.isEmpty()) return null
        return Album(
            path = dir,
            name = dir.fileName.toString(),
            images = sortedImages,
            previewPath = sortedImages.first().path
        )
    }

    fun readImageFileInfo(path: Path): ImageFile? {
        return try {
            val name = path.fileName.toString()
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
            val ext = path.extension
            ImageFile(
                path = path,
                name = name,
                extension = if (ext.startsWith(".")) ext else ".$ext",
                size = attrs.size(),
                lastModified = attrs.lastModifiedTime().toMillis()
            )
        } catch (_: Exception) { null }
    }

    fun readExifCached(path: Path): Map<String, String> {
        val cached = galleryIndex.loadMetadata(path)
        if (cached != null) {
            val map = linkedMapOf<String, String>()
            val pairs = cached.removeSurrounding("{", "}")
                .split(", ")
                .mapNotNull { pair ->
                    val eq = pair.indexOfFirst { it == '=' }
                    if (eq > 0) pair.take(eq) to pair.drop(eq + 1) else null
                }
            for ((k, v) in pairs) map[k] = v
            return map
        }
        val result = ImageEditor.readExif(path)
        if (result.isNotEmpty()) {
            galleryIndex.saveMetadata(path, result.entries.joinToString(", ") { "${it.key}=${it.value}" })
        }
        return result
    }

    fun moveToTrash(file: Path): Result<Path> = runCatching {
        val home = Paths.get(System.getProperty("user.home"))
        val trashDir = home.resolve(".local/share/Trash/files")
        val infoDir = home.resolve(".local/share/Trash/info")
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        Files.createDirectories(trashDir)
        Files.createDirectories(infoDir)
        val realFile = file.toRealPath()
        val fileName = realFile.fileName.toString()
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot >= 0) fileName.substring(0, dot) else fileName
        val ext = if (dot >= 0) fileName.substring(dot) else ""
        var trashFileName = fileName
        var counter = 1
        while (trashDir.resolve(trashFileName).toFile().exists() && counter < 10000) {
            trashFileName = "$stem ($counter)$ext"
            counter++
        }
        val trashPath = trashDir.resolve(trashFileName)
        val infoPath = infoDir.resolve("$trashFileName.trashinfo")
        Files.move(realFile, trashPath, StandardCopyOption.ATOMIC_MOVE)
        val encoded = URLEncoder.encode(realFile.toString(), Charsets.UTF_8)
        val date = LocalDateTime.now().format(dateFormatter)
        Files.writeString(infoPath, "[Trash Info]\nPath=$encoded\nDeletionDate=$date\n")
        trashPath
    }

    fun restoreFromTrash(trashPath: Path, originalPath: Path): Result<Unit> = runCatching {
        val home = Paths.get(System.getProperty("user.home"))
        val infoDir = home.resolve(".local/share/Trash/info")
        val realOriginal = originalPath.toAbsolutePath().normalize()
        val infoPath = infoDir.resolve("${trashPath.fileName}.trashinfo")
        Files.createDirectories(realOriginal.parent)
        try {
            Files.move(trashPath, realOriginal, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.copy(trashPath, realOriginal, StandardCopyOption.REPLACE_EXISTING)
            Files.deleteIfExists(trashPath)
        }
        Files.deleteIfExists(infoPath)
    }
}
