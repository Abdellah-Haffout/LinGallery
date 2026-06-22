package com.soufianodev.lingallery.shared.filesystem

import java.nio.file.Path
import kotlin.io.path.extension

val Path.extensionLower: String
    get() {
        val ext = extension
        return if (ext.startsWith(".")) ext.lowercase() else ".${ext.lowercase()}"
    }

fun normalizePath(path: Path): Path =
    try { path.toRealPath() } catch (_: Exception) { path.toAbsolutePath().normalize() }

fun resolveCollision(dir: Path, fileName: String): String {
    if (!dir.resolve(fileName).toFile().exists()) return fileName
    val dot = fileName.lastIndexOf('.')
    val stem = if (dot >= 0) fileName.substring(0, dot) else fileName
    val ext = if (dot >= 0) fileName.substring(dot) else ""
    var counter = 1
    while (counter < 10000) {
        val candidate = "$stem ($counter)$ext"
        if (!dir.resolve(candidate).toFile().exists()) return candidate
        counter++
    }
    return "$stem (${System.currentTimeMillis()})$ext"
}

fun uniqueDestination(path: Path): Path {
    val parent = path.parent
    val name = path.fileName.toString()
    val dot = name.lastIndexOf('.')
    val stem = if (dot >= 0) name.substring(0, dot) else name
    val ext = if (dot >= 0) name.substring(dot) else ""
    var counter = 1
    while (counter < 10000) {
        val candidate = parent.resolve("$stem ($counter)$ext")
        if (!candidate.toFile().exists()) return candidate
        counter++
    }
    return parent.resolve("$stem (${System.currentTimeMillis()})$ext")
}
