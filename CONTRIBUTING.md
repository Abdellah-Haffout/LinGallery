# Contributing to LinGallery

## Requirements

- Linux
- Java 21 (JDK) -- install via your package manager, e.g. `sudo apt install openjdk-21-jdk`
- Gradle (the project includes the Gradle wrapper)

## Setup

```bash
git clone https://github.com/SoufianoDev/LinGallery.git
cd LinGallery
./gradlew build
```

## Run

```bash
./gradlew run
```

## Project structure

```
app/src/main/kotlin/com/soufianodev/lingallery/
  Main.kt                  -- Entry point, window setup, app state
  data/
    Album.kt               -- Album data class
    ImageFile.kt           -- Image file data class
    GalleryState.kt        -- Central app state, album/image mutations
    GalleryIndex.kt        -- SQLite cache for gallery state and metadata
    FileIndexer.kt         -- Directory scanner (progressive flow)
    FileWatcher.kt         -- Real-time file system watcher
    TrashManager.kt        -- Freedesktop Trash integration
  processing/
    ImageEditor.kt         -- Crop, rotate, flip, EXIF reading
  theme/
    AppConst.kt            -- App-wide constants
    Color.kt               -- Dark and light color palettes
    Theme.kt               -- Material3 theme setup
    Icons.kt               -- Custom icon definitions
  ui/
    components/            -- Reusable UI components
    gallery/               -- Album panel and gallery grid
    viewer/                -- Image viewer, crop overlay, edit toolbar
```

## Stack

- Kotlin 2.1.0 / JVM 21
- Jetpack Compose Desktop (JetBrains)
- Material3 design system
- Sketch (image loading)
- Scrimage (image processing)
- metadata-extractor (EXIF)
- SQLite via JDBC (persistent cache)

## Coding conventions

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `kotlin.code.style=official` (set in `gradle.properties`)
- No semicolons
- No `!!` unless absolutely necessary -- prefer null-safe operators

## Pull request process

1. Open an issue describing the change before starting work
2. Fork the repository and create a feature branch
3. Make your changes
4. Ensure the project builds with `./gradlew build`
5. Submit a pull request referencing the issue

## Code of conduct

Be respectful and constructive. Disagreements are fine; personal attacks are not.
