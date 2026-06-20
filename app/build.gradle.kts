plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val sketchVersion = "4.4.0-beta02"
val skikoVersion = "0.144.6"

configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.skiko:skiko:$skikoVersion",
            "org.jetbrains.skiko:skiko-awt:$skikoVersion",
            "org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skikoVersion",
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("com.drewnoakes:metadata-extractor:2.18.0")

    implementation("io.github.panpf.sketch4:sketch-compose:$sketchVersion")
    implementation("io.github.panpf.sketch4:sketch-svg:$sketchVersion")
    implementation("io.github.panpf.sketch4:sketch-animated-gif:$sketchVersion")

    // TwelveMonkeys ImageIO plugins — auto-register via ServiceLoader
    runtimeOnly("com.twelvemonkeys.imageio:imageio-jpeg:3.13.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-bmp:3.13.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-tiff:3.13.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.13.1")

    // Alternative WebP support via ImageIO
    runtimeOnly("com.github.usefulness:webp-imageio:0.10.0")

    // Scrimage — image loading, processing, and format-aware saving
    implementation("com.sksamuel.scrimage:scrimage-core:4.3.5")
    implementation("com.sksamuel.scrimage:scrimage-webp:4.3.5")
    implementation("com.sksamuel.scrimage:scrimage-formats-extra:4.3.5")

    // SQLite for persistent gallery index cache
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")

    // FileKit — native file/directory picker and cross-platform file operations
    implementation("io.github.vinceglb:filekit-core:0.14.2")
    implementation("io.github.vinceglb:filekit-dialogs-compose:0.14.2")
}

compose.desktop {
    application {
        mainClass = "com.soufianodev.lingallery.MainKt"
        nativeDistributions {
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
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}
