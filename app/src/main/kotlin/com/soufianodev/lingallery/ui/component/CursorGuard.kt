package com.soufianodev.lingallery.ui.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

fun Modifier.stablePointerHoverIcon(icon: PointerIcon): Modifier = composed {
    val lastIcon = remember { mutableStateOf<PointerIcon?>(null) }
    if (icon != lastIcon.value) { lastIcon.value = icon }
    this.then(Modifier.pointerHoverIcon(icon))
}
