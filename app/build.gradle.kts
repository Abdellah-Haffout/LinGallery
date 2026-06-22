plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(libs.compose.uiToolingPreview)

    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.sketch.compose)
    implementation(libs.sketch.svg)
    implementation(libs.sketch.gif)

    implementation(libs.metadata.extractor)

    implementation(libs.scrimage.core)
    implementation(libs.scrimage.webp)
    implementation(libs.scrimage.extra)

    runtimeOnly("com.twelvemonkeys.imageio:imageio-jpeg:3.13.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-bmp:3.13.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-tiff:3.13.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.13.1")
    runtimeOnly("com.github.usefulness:webp-imageio:0.10.0")

    implementation(libs.sqlite.jdbc)

    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs)
}

compose.desktop {
    application {
        mainClass = "com.soufianodev.lingallery.app.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "com.soufianodev.lingallery"
            packageVersion = "1.0.0"
            linux {
                modules("jdk.security.auth")
            }
        }

        jvmArgs += listOf(
            "-Dskiko.renderApi=OPENGL",
            "-Dskiko.gpu.resourceCacheLimit=128M",
            "-Dsun.awt.enableExtraMouseButtons=false",
            "-Xss512k"
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        // jvm-default behavior is default in Kotlin 2.x
    }
}
