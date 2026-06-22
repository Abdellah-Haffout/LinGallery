package com.soufianodev.lingallery.data

import java.io.File

/**
 * Loads the native Rust scanner library directly from the build output path.
 * The path is passed via the JVM system property `-Dlingallery.native.lib=...`
 * set automatically by the Gradle build.
 *
 * Thread-safe and idempotent.
 */
object NativeLibLoader {
    private var loaded = false
    var isAvailable = false
        private set

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true

            val libPath = System.getProperty("lingallery.native.lib")
            if (libPath.isNullOrBlank()) {
                System.err.println("[LinGallery] System property 'lingallery.native.lib' not set. Native scanner disabled.")
                return
            }

            val libFile = File(libPath)
            if (!libFile.exists()) {
                System.err.println("[LinGallery] Native library not found at: $libPath")
                return
            }

            try {
                System.load(libFile.absolutePath)
                isAvailable = true
                println("[LinGallery] Native scanner loaded: ${libFile.absolutePath}")
            } catch (e: UnsatisfiedLinkError) {
                System.err.println("[LinGallery] Failed to load native library: ${e.message}")
            }
        }
    }
}
