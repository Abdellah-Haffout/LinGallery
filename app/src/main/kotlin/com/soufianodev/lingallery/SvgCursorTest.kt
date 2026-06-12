package com.soufianodev.lingallery

import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import java.awt.Point
import java.awt.Toolkit
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.InputStream
import org.jetbrains.skia.Surface
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

object SvgCursorTest {
    fun testLoad(svgStream: InputStream) {
        val bytes = svgStream.readAllBytes()
        val data = Data.makeFromBytes(bytes)
        val dom = SVGDOM(data)
        
        // render to surface
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(32, 32))
        dom.render(surface.canvas)
        val skiaImage = surface.makeImageSnapshot()
        
        println("Loaded SVG, width: \${skiaImage.width}")
    }
}
