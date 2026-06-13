package com.soufianodev.lingallery.processing

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.angles.Degrees
import com.sksamuel.scrimage.nio.BmpWriter
import com.sksamuel.scrimage.nio.ImageWriter
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.nio.TiffWriter
import com.sksamuel.scrimage.webp.WebpWriter
import com.soufianodev.lingallery.data.GalleryIndex
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO

object ImageEditor {

    fun crop(
        path: Path,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        destPath: Path? = null
    ): Boolean {
        return try {
            val img = ImmutableImage.loader().fromFile(path.toFile()).applyExifOrientation(path)

            if (img.width <= 0 || img.height <= 0 ||
                width <= 0f || height <= 0f) return false

            var left = kotlin.math.floor(x + 0.5f).toInt()
            var top = kotlin.math.floor(y + 0.5f).toInt()
            var right = kotlin.math.floor((x + width) + 0.5f).toInt()
            var bottom = kotlin.math.floor((y + height) + 0.5f).toInt()

            left = maxOf(0, minOf(left, img.width))
            top = maxOf(0, minOf(top, img.height))
            right = maxOf(left + 1, minOf(right, img.width))
            bottom = maxOf(top + 1, minOf(bottom, img.height))

            val cropped = img.subimage(left, top, right - left, bottom - top)
            val dest = destPath ?: path
            val writer = writerForExtension(dest) ?: return false
            cropped.output(writer, dest.toFile())
            true
        } catch (_: Exception) { false }
    }

    fun rotate(path: Path, degrees: Int): Boolean {
        return try {
            val img = ImmutableImage.loader().fromFile(path.toFile())
                .applyExifOrientation(path)
                .rotate(Degrees(degrees))
            val writer = writerForExtension(path) ?: return false
            img.output(writer, path.toFile())
            true
        } catch (_: Exception) { false }
    }

    fun flipHorizontal(path: Path): Boolean {
        return try {
            val img = ImmutableImage.loader().fromFile(path.toFile())
                .applyExifOrientation(path)
                .flipX()
            val writer = writerForExtension(path) ?: return false
            img.output(writer, path.toFile())
            true
        } catch (_: Exception) { false }
    }

    fun readExif(path: Path): Map<String, String> {
        val result = linkedMapOf<String, String>()
        try {
            val name = path.fileName.toString()
            val ext = name.substringAfterLast('.', "").lowercase()

            try {
                val attrs = Files.readAttributes(path, "*")
                val size = attrs["size"] as? Long ?: 0L
                val sizeKb = size / 1024.0
                result["File Size"] = if (sizeKb >= 1024) {
                    "%.2f MB".format(sizeKb / 1024.0)
                } else {
                    "%.1f KB".format(sizeKb)
                }
                val mtime = attrs["lastModifiedTime"] as? FileTime
                if (mtime != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US)
                    result["Modified"] = sdf.format(Date(mtime.toMillis()))
                }
            } catch (_: Exception) { }

            if (ext != "svg") {
                try {
                    val readers = ImageIO.getImageReadersBySuffix(ext)
                    if (readers.hasNext()) {
                        val r = readers.next()
                        val stream = ImageIO.createImageInputStream(path.toFile())
                        r.input = stream
                        val w = r.getWidth(0)
                        val h = r.getHeight(0)
                        if (w > 0 && h > 0) {
                            result["Dimensions"] = "${w} \u00d7 ${h} px"
                        }
                        result["Format"] = r.formatName
                        stream.close()
                    }
                } catch (_: Exception) { }
            } else {
                result["Format"] = "SVG"
            }

            if (ext !in setOf("png", "bmp", "svg", "webp")) {
                try {
                    val metadata = ImageMetadataReader.readMetadata(path.toFile())
                    val exifSub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
                    if (exifSub != null) {
                        val date = exifSub.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                        if (date != null) result["Date Taken"] = date
                        val iso = exifSub.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
                        if (iso != null) result["ISO"] = iso.toString()
                        val aperture = exifSub.getString(ExifSubIFDDirectory.TAG_APERTURE)
                        if (aperture != null) result["Aperture"] = aperture
                        val shutter = exifSub.getString(ExifSubIFDDirectory.TAG_SHUTTER_SPEED)
                        if (shutter != null) result["Shutter Speed"] = shutter
                        val focal = exifSub.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)
                        if (focal != null) result["Focal Length"] = focal
                    }
                    val exifIfd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                    if (exifIfd0 != null) {
                        val make = exifIfd0.getString(ExifIFD0Directory.TAG_MAKE)
                        if (make != null) result["Camera Make"] = make
                        val model = exifIfd0.getString(ExifIFD0Directory.TAG_MODEL)
                        if (model != null) result["Camera Model"] = model
                    }
                    val gps = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
                    if (gps != null) {
                        val geo = gps.geoLocation
                        if (geo != null) {
                            result["GPS"] = "${geo.latitude}, ${geo.longitude}"
                        }
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return result
    }

    fun readExifCached(path: Path, galleryIndex: GalleryIndex): Map<String, String> {
        val cached = galleryIndex.loadMetadata(path)
        if (cached != null) {
            val map = linkedMapOf<String, String>()
            val pairs = cached.removeSurrounding("{", "}")
                .split(", ")
                .mapNotNull { pair ->
                    val eq = pair.indexOfFirst { it == '=' }
                    if (eq > 0) pair.take(eq) to pair.drop(eq + 1) else null
                }
            for ((k, v) in pairs) map[k] = v
            return map
        }
        val result = readExif(path)
        if (result.isNotEmpty()) {
            galleryIndex.saveMetadata(path, result.entries.joinToString(", ") { "${it.key}=${it.value}" })
        }
        return result
    }

    private fun writerForExtension(path: Path): ImageWriter? {
        val ext = path.fileName.toString().substringAfterLast('.', "png").lowercase()
        return when (ext) {
            "png" -> PngWriter()
            "jpg", "jpeg" -> JpegWriter()
            "webp" -> WebpWriter.DEFAULT
            "bmp" -> BmpWriter()
            "tiff", "tif" -> TiffWriter()
            else -> null
        }
    }

    /**
     * Reads the EXIF orientation from the file and bakes it into the pixel data.
     * Scrimage writers already produce clean output without EXIF orientation tags,
     * so no post-processing is needed to prevent double-application.
     */
    private fun ImmutableImage.applyExifOrientation(path: Path): ImmutableImage {
        return try {
            val metadata = ImageMetadataReader.readMetadata(path.toFile())
            val dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            val orientation = dir?.getInteger(ExifIFD0Directory.TAG_ORIENTATION) ?: 1
            when (orientation) {
                1 -> this
                2 -> this.flipX()
                3 -> this.rotate(Degrees(180))
                4 -> this.flipY()
                5 -> this.rotate(Degrees(90)).flipX()
                6 -> this.rotate(Degrees(90))
                7 -> this.rotate(Degrees(-90)).flipX()
                8 -> this.rotate(Degrees(-90))
                else -> this
            }
        } catch (_: Exception) { this }
    }


}
