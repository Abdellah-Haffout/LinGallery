"""
LinGallery — Material Design 3 Color System & Application Constants.
Palette: Neutral Teal / Slate — No purple. Tuned for reduced eye strain.
"""
from PySide6.QtGui import QColor


# ──────────────────────────────────────────────
# Material Design 3 Tonal Palette — Dark Theme (Teal / Slate)
# ──────────────────────────────────────────────
class DarkPalette:
    # Backgrounds
    BACKGROUND        = "#0F1117"
    SURFACE           = "#181D24"
    SURFACE_VARIANT   = "#222831"
    SURFACE_CONTAINER = "#1E2430"

    # Primary (teal)
    PRIMARY           = "#4FC3C3"
    ON_PRIMARY        = "#003737"
    PRIMARY_CONTAINER = "#005252"
    ON_PRIMARY_CONTAINER = "#A6EFEF"

    # Secondary (blue-gray)
    SECONDARY         = "#90A4AE"
    ON_SECONDARY      = "#1A2730"
    SECONDARY_CONTAINER = "#2B3A42"
    ON_SECONDARY_CONTAINER = "#C4D8E2"

    # Error
    ERROR             = "#FF7676"
    ON_ERROR          = "#4D0000"
    ERROR_CONTAINER   = "#7A1A1A"
    ON_ERROR_CONTAINER = "#FFBABA"

    # Outlines
    OUTLINE           = "#3D5160"
    OUTLINE_VARIANT   = "#1E2D38"

    # Text
    ON_SURFACE        = "#D9E4EC"
    ON_SURFACE_VARIANT = "#8FAAB8"

    # Utility
    SCRIM             = "#000000"
    SHADOW            = "#000000"
    DIVIDER           = "#1E2D38"

    # Accent overlay for hover states
    HOVER_OVERLAY     = "rgba(79, 195, 195, 0.08)"
    PRESSED_OVERLAY   = "rgba(79, 195, 195, 0.16)"


# ──────────────────────────────────────────────
# Material Design 3 Tonal Palette — Light Theme
# ──────────────────────────────────────────────
class LightPalette:
    BACKGROUND        = "#F5F8FA"
    SURFACE           = "#FFFFFF"
    SURFACE_VARIANT   = "#E1ECEF"
    SURFACE_CONTAINER = "#EDF3F5"

    PRIMARY           = "#006A6A"
    ON_PRIMARY        = "#FFFFFF"
    PRIMARY_CONTAINER = "#A6EFEF"
    ON_PRIMARY_CONTAINER = "#003737"

    SECONDARY         = "#4A6572"
    ON_SECONDARY      = "#FFFFFF"
    SECONDARY_CONTAINER = "#C4D8E2"
    ON_SECONDARY_CONTAINER = "#1A2730"

    ERROR             = "#C62828"
    ON_ERROR          = "#FFFFFF"
    ERROR_CONTAINER   = "#FFCDD2"
    ON_ERROR_CONTAINER = "#4D0000"

    OUTLINE           = "#5C7D8A"
    OUTLINE_VARIANT   = "#B8D0DA"

    ON_SURFACE        = "#101D23"
    ON_SURFACE_VARIANT = "#3D5A68"

    SCRIM             = "#000000"
    SHADOW            = "#000000"
    DIVIDER           = "#B8D0DA"
    HOVER_OVERLAY     = "rgba(0, 106, 106, 0.08)"
    PRESSED_OVERLAY   = "rgba(0, 106, 106, 0.16)"


# ──────────────────────────────────────────────
# Application-wide Constants
# ──────────────────────────────────────────────
class AppConst:
    APP_NAME          = "LinGallery"
    APP_VERSION       = "1.0.0"
    CACHE_DIR         = "~/.cache/lingallery"
    THUMB_DIR         = "~/.cache/lingallery/thumbnails"
    DB_PATH           = "~/.cache/lingallery/meta.db"

    # Default scan roots (~/Pictures, ~/Downloads)
    DEFAULT_SCAN_ROOTS = ["~/Pictures", "~/Downloads"]

    SUPPORTED_FORMATS = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tiff", ".tif", ".svg"}
    SUPPORTED_FORMATS_PILLOW = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tiff", ".tif"}

    THUMB_SIZE        = (220, 220)
    MAX_CACHE_MB      = 64
    PRELOAD_AHEAD     = 3
    SLIDESHOW_DEFAULT_INTERVAL_MS = 4000

    # UI metrics
    TOP_BAR_HEIGHT    = 64
    SIDEBAR_WIDTH     = 260
    BOTTOM_BAR_HEIGHT = 56
    THUMB_SPACING     = 10
    CORNER_RADIUS     = 12
    ICON_SIZE         = 24

    # Zoom limits
    ZOOM_STEP         = 1.15
    ZOOM_MIN          = 0.05
    ZOOM_MAX          = 32.0

    # Theme
    THEME_DARK        = "dark"
    THEME_LIGHT       = "light"
