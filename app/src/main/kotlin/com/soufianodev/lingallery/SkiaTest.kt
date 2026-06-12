package com.soufianodev.lingallery

import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toAwtImage

fun test(image: Image) {
    val composeBitmap = image.toComposeImageBitmap()
    composeBitmap.toAwtImage()
}
