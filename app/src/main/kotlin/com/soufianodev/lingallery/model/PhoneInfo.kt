package com.soufianodev.lingallery.model

import java.nio.file.Path

data class PhoneInfo(
    val id: String,
    val name: String,
    val mountPath: Path
)
