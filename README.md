# LinGallery

LinGallery is a modern, high-performance image viewer and gallery application built entirely in Python for the Linux desktop. It leverages PySide6 (Qt for Python) to deliver a native, hardware-accelerated experience across both Wayland and Xorg environments. 

The application is engineered to handle massive image libraries efficiently, utilizing asynchronous loading, two-tier caching, and non-blocking background indexing. The UI adheres strictly to Material Design 3 principles, offering a clean, professional, and eye-friendly interface.

## Core Features

*   **Asynchronous Library Discovery:** Automatically indexes specified root directories (e.g., `~/Pictures`) in the background, presenting discovered folders dynamically without freezing the UI.
*   **High-Performance Rendering:** Employs `QGraphicsView` for hardware-accelerated image scaling, panning, and zooming.
*   **Two-Tier Caching System:**
    *   **Memory Cache (L1):** A strictly managed LRU cache to keep memory consumption under specified limits (e.g., 512MB).
    *   **Disk Cache (L2):** Persists generated thumbnails to disk, drastically reducing load times for large RAW/JPEG files on subsequent runs.
*   **Non-Destructive Image Processing:** Integrates Pillow for fast, in-place image modifications including rotation, mirroring, flipping, and custom crop selections.
*   **Material Design 3 Architecture:** Built with a custom, dynamic styling engine that implements a professional Teal/Slate palette, specifically designed to reduce eye strain. All iconography is rendered dynamically via SVG paths, ensuring perfect clarity at any scale.
*   **Robust Navigation:** Full support for keyboard navigation, slideshow mode, and seamless switching between the gallery grid and the full-screen viewer.
*   **Metadata Support:** Parses and displays EXIF metadata directly within the viewer.

## System Requirements

*   Linux Environment (Xorg or Wayland)
*   Python 3.10 or higher

## Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/yourusername/lingallery.git
    cd lingallery
    ```

2.  **Create and activate a virtual environment:**
    ```bash
    python3 -m venv venv
    source venv/bin/activate
    ```

3.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

## Usage

Start the application using the main entry point:

```bash
python3 src/main.py
```

### Keyboard Shortcuts

*   **Left / A:** Previous Image
*   **Right / D:** Next Image
*   **F:** Toggle Fullscreen
*   **Space:** Toggle Slideshow
*   **Plus (+):** Zoom In
*   **Minus (-):** Zoom Out
*   **F2:** Fit to Screen
*   **L:** Rotate Left
*   **R:** Rotate Right
*   **I:** View Image Information (EXIF)
*   **Delete:** Delete Image
*   **Escape:** Exit Fullscreen or Return to Gallery

## Architecture Overview

LinGallery follows a strict separation of concerns, divided into distinct layers:

*   **UI Layer (`src/ui`):** Contains pure PySide6 widget implementations, including the main application shell, the async gallery grid, and the hardware-accelerated image viewer.
*   **Logic Layer (`src/logic`):** Houses the `ImageManager` and `LibraryIndexer`, which orchestrate background thread pools to ensure the main UI thread remains unblocked during heavy I/O operations.
*   **Storage Layer (`src/storage`):** Manages the dual-tier `CacheManager`, handling both in-memory LRU eviction and persistent disk storage for thumbnails.
*   **Processing Layer (`src/processing`):** Abstracts all direct image manipulation (cropping, rotation, metadata extraction) using Pillow.

## License

This project is licensed under the MIT License.
