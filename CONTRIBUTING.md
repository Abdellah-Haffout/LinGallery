# Contributing to LinGallery

## Requirements

- Linux (primary target; Windows/macOS builds possible but untested)
- Java 21 (JDK) — install via your package manager, e.g. `sudo apt install openjdk-21-jdk`
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
  app/                        -- Entry point, dependency wiring, i18n
    Main.kt                   -- Application entry point, window setup
    AppModule.kt              -- Service locator, wires gallery/viewer/phone
    App.kt                    -- Root composable, screen routing, snackbar
    AppWindow.kt              -- Window state (fullscreen, size, position)
    AppConst.kt               -- App-wide constants
    Strings.kt                -- i18n via ResourceBundle
  model/                      -- Domain models
    Album.kt                  -- Album data class
    ImageFile.kt              -- Image file data class
    PhoneInfo.kt              -- Detected phone metadata
    PhoneScanEvent.kt         -- Phone scan progress events
  gallery/                    -- Gallery feature module
    GalleryScreen.kt          -- Gallery root composable
    GalleryGrid.kt            -- Thumbnail grid
    AlbumSidebar.kt           -- Album list sidebar
    GalleryStateHolder.kt     -- Gallery state management
    GalleryRepository.kt      -- Data operations for gallery
    GalleryUiState.kt         -- UI state types
    data/
      FileIndexer.kt          -- Progressive directory scanner
      FileWatcher.kt          -- Real-time file system watcher
      GalleryIndex.kt         -- SQLite cache for gallery state
  viewer/                     -- Image viewer feature module
    ViewerScreen.kt           -- Viewer root composable
    ViewerStateHolder.kt      -- Viewer state management
    ImageDisplay.kt           -- Image rendering with zoom/pan
    CropOverlay.kt            -- Interactive crop overlay
    EditToolbar.kt            -- Editing controls
    FloatingZoomControl.kt    -- On-image zoom slider
  phone/                      -- MTP phone detection feature
    PhoneManager.kt           -- Phone lifecycle management
    data/
      GvfsPhoneDetector.kt    -- GVfs-based MTP phone detection
      PhoneAlbumManager.kt    -- Phone album creation
      PhoneThumbnailCache.kt  -- Phone image thumbnail caching
  shared/                     -- Cross-cutting utilities
    imaging/
      ImageEditor.kt          -- Crop, rotate, flip, EXIF reading
    filesystem/
      PathExt.kt              -- Path extension functions
      SafeFileOps.kt          -- Safe file I/O operations
    desktop/
      WindowUtil.kt           -- Window bounds utilities
      Clipboard.kt            -- System clipboard integration
  ui/                         -- Reusable UI components and theme
    component/
      LinGallerySnackbar.kt   -- Custom snackbar with error/action styles
      Tooltip.kt              -- Tooltip composable
      CursorGuard.kt          -- Cursor visibility guard
    theme/
      Color.kt                -- Dark and light color palettes
      Theme.kt                -- Material3 theme setup
      Icons.kt                -- Custom icon definitions
```

## Stack

- Kotlin 2.4.0 / JVM 21
- Jetpack Compose Multiplatform 1.11.1 (JetBrains)
- Material3 design system
- Sketch 4.4.0-beta02 (image loading, SVG, GIF)
- Scrimage 4.3.5 (image processing, WebP, extra formats)
- metadata-extractor 2.18.0 (EXIF)
- TwelveMonkeys ImageIO (JPEG, BMP, TIFF, WebP)
- FileKit 0.14.2 (native OS file picker dialogs)
- SQLite via JDBC 3.49.1.0 (persistent cache)

## Dependencies

Dependencies are managed through the Gradle version catalog in
`gradle/libs.versions.toml`. Add new entries there rather than hardcoding
versions in `build.gradle.kts`.

## Coding conventions

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `kotlin.code.style=official` (set in `gradle.properties`)
- No semicolons
- No `!!` unless absolutely necessary — prefer null-safe operators

## Pull request process

1. Open an issue describing the change before starting work
2. Fork the repository and create a feature branch
3. Make your changes
4. Ensure the project builds with `./gradlew build`
5. Submit a pull request referencing the issue

## Code of conduct

Be respectful and constructive. Disagreements are fine; personal attacks are not.
