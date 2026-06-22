package com.soufianodev.lingallery.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.SingletonSketch
import com.github.panpf.sketch.Sketch
import com.soufianodev.lingallery.ui.theme.LinGalleryTheme
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.system.exitProcess

fun main() = application {
    FileKit.init(appId = "com.soufianodev.lingallery")
    System.setProperty("jdk.nio.file.WatchService.maxEventsPerPoll", "16384")

    SingletonSketch.setSafe {
        Sketch.Builder(PlatformContext.INSTANCE).apply {
            logger(level = com.github.panpf.sketch.util.Logger.Level.Warn)
        }.build()
    }

    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val windowState = rememberWindowState(width = 1400.dp, height = 900.dp)

    Window(
        onCloseRequest = {
            appScope.cancel()
            exitApplication()
            exitProcess(0)
        },
        title = Strings.App.name,
        state = windowState
    ) {
        val module = remember {
            AppModule(appScope, window).also { it.init() }
        }
        LinGalleryTheme(darkTheme = true) {
            App(module)
        }
        DisposableEffect(module) {
            onDispose { module.cleanup() }
        }
    }
}
