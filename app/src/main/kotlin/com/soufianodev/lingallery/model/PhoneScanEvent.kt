package com.soufianodev.lingallery.model

import java.nio.file.Path

data class PhoneScanProgress(
    val phoneName: String,
    val imagesDiscovered: Int,
    val isScanning: Boolean
)

sealed interface PhoneScanEvent {
    data class AlbumCreated(val phoneName: String, val mountPath: Path) : PhoneScanEvent
    data class Progress(val scannedDirs: Int, val imagesDiscovered: Int) : PhoneScanEvent
    data class ImagesFound(val images: List<ImageFile>) : PhoneScanEvent
    data class Complete(val totalImages: Int) : PhoneScanEvent
}
