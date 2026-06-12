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

configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.skiko:skiko:0.9.4",
            "org.jetbrains.skiko:skiko-awt:0.9.4",
            "org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.9.4",
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
}

compose.desktop {
    application {
        mainClass = "com.soufianodev.lingallery.MainKt"
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
