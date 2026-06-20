package com.soufianodev.lingallery.data

import java.nio.file.Path

data class ImageFile(
    val path: Path,
    val name: String,
    val extension: String,
    val size: Long,
    val lastModified: Long,
    val thumbnailPath: Path? = null
)
