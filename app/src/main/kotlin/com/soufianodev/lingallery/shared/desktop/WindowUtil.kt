package com.soufianodev.lingallery.shared.desktop

import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

data class WindowBounds(
    val x: Int, val y: Int,
    val width: Int, val height: Int,
    val extendedState: Int = Frame.NORMAL
)

fun toggleFullscreen(
    frame: Frame,
    isFullscreen: Boolean,
    savedBounds: WindowBounds?
): Pair<Boolean, WindowBounds?> {
    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    return if (!isFullscreen) {
        device.fullScreenWindow = frame
        true to (savedBounds ?: WindowBounds(
            x = frame.bounds.x, y = frame.bounds.y,
            width = frame.bounds.width, height = frame.bounds.height,
            extendedState = frame.extendedState
        ))
    } else {
        device.fullScreenWindow = null
        savedBounds?.let { bounds ->
            frame.extendedState = bounds.extendedState
            if (bounds.extendedState and Frame.MAXIMIZED_BOTH == 0) {
                frame.bounds = Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
            }
        }
        false to null
    }
}
