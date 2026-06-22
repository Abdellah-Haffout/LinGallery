package com.soufianodev.lingallery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

class AppWindowState {
    var isFullscreen by mutableStateOf(false)
    var savedWidth by mutableStateOf(1400.dp)
    var savedHeight by mutableStateOf(900.dp)
    var positionX by mutableStateOf(0)
    var positionY by mutableStateOf(0)

    val size: DpSize
        get() = DpSize(savedWidth, savedHeight)
}
