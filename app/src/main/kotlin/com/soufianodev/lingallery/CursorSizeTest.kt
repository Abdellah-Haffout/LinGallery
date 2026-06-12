package com.soufianodev.lingallery

import java.awt.Toolkit

fun main() {
    val toolkit = Toolkit.getDefaultToolkit()
    val size = toolkit.getBestCursorSize(32, 32)
    println("Best cursor size for 32x32 is: \${size.width}x\${size.height}")
    val size2 = toolkit.getBestCursorSize(64, 64)
    println("Best cursor size for 64x64 is: \${size2.width}x\${size2.height}")
}
