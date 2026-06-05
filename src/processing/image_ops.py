"""
LinGallery — Image Processing Operations.
All operations use Pillow and are safe to call from background threads.
"""
from __future__ import annotations
import datetime
import logging
import math
import os
from pathlib import Path
from typing import Optional

from PIL import Image, ImageOps

try:
    import piexif
    HAS_PIEXIF = True
except ImportError:
    HAS_PIEXIF = False

try:
    import exifread
    # Suppress exifread's internal logger so it never prints
    # "PNG file does not have exif data." etc. to the terminal.
    logging.getLogger("exifread").setLevel(logging.CRITICAL)
    HAS_EXIFREAD = True
except ImportError:
    HAS_EXIFREAD = False


def _save_image(img: Image.Image, path: str) -> None:
    """Save a PIL image back to its original path, preserving format."""
    suffix = Path(path).suffix.lower()
    fmt_map = {
        ".jpg": "JPEG", ".jpeg": "JPEG",
        ".png": "PNG", ".webp": "WEBP",
        ".bmp": "BMP", ".tiff": "TIFF", ".tif": "TIFF",
    }
    fmt = fmt_map.get(suffix, "PNG")
    if fmt == "JPEG":
        # subsampling=0 preserves full chroma (4:4:4), quality=95 is near-lossless
        save_args = {"quality": 95, "subsampling": 0}
    elif fmt == "WEBP":
        save_args = {"quality": 95, "method": 6}
    else:
        save_args = {}
    img.save(path, fmt, **save_args)


def rotate(path: str, degrees: int) -> bool:
    """Rotate image by degrees (counter-clockwise). expand=True preserves full image."""
    try:
        with Image.open(path) as img:
            img = ImageOps.exif_transpose(img)
            img = img.rotate(degrees, expand=True)
            _save_image(img, path)
        return True
    except Exception:
        return False


def flip_horizontal(path: str) -> bool:
    """Mirror the image horizontally (left-right)."""
    try:
        with Image.open(path) as img:
            img = ImageOps.exif_transpose(img)
            img = ImageOps.mirror(img)
            _save_image(img, path)
        return True
    except Exception:
        return False


def flip_vertical(path: str) -> bool:
    """Flip the image vertically (top-bottom)."""
    try:
        with Image.open(path) as img:
            img = ImageOps.exif_transpose(img)
            img = ImageOps.flip(img)
            _save_image(img, path)
        return True
    except Exception:
        return False


def crop(path: str, x: int, y: int, width: int, height: int, dest_path: str | None = None) -> bool:
    """Crop the image to the given pixel rectangle."""
    try:
        with Image.open(path) as img:
            img = ImageOps.exif_transpose(img)
            left = max(0, min(int(x), img.width))
            top = max(0, min(int(y), img.height))
            right = max(left + 1, min(int(x + width), img.width))
            bottom = max(top + 1, min(int(y + height), img.height))
            box = (left, top, right, bottom)
            img = img.crop(box)
            _save_image(img, dest_path or path)
        return True
    except Exception:
        return False


def map_display_crop_to_source(
    display_rect: tuple[float, float, float, float],
    display_size: tuple[float, float],
    source_size: tuple[int, int],
) -> Optional[tuple[int, int, int, int]]:
    """Map a crop rectangle from displayed image coordinates to source pixels."""
    display_x, display_y, display_width, display_height = display_rect
    display_image_width, display_image_height = display_size
    source_width, source_height = source_size

    if (
        display_image_width <= 0
        or display_image_height <= 0
        or source_width <= 0
        or source_height <= 0
        or display_width <= 0
        or display_height <= 0
    ):
        return None

    scale_x = source_width / display_image_width
    scale_y = source_height / display_image_height
    left = _round_pixel_boundary(display_x * scale_x)
    top = _round_pixel_boundary(display_y * scale_y)
    right = _round_pixel_boundary((display_x + display_width) * scale_x)
    bottom = _round_pixel_boundary((display_y + display_height) * scale_y)

    left = max(0, min(left, source_width))
    top = max(0, min(top, source_height))
    right = max(left + 1, min(right, source_width))
    bottom = max(top + 1, min(bottom, source_height))
    return left, top, right - left, bottom - top


def _round_pixel_boundary(value: float) -> int:
    return int(math.floor(value + 0.5))


def read_exif(path: str) -> dict:
    """
    Return a flat dict of human-readable image metadata.
    Always includes basic file info (name, size, dimensions, format,
    modification date). Appends EXIF fields when available.
    Never raises — gracefully returns whatever it can extract.
    """
    result = {}
    suffix = Path(path).suffix.lower()

    # ── Basic file metadata (always available) ────────────────────────
    try:
        stat = os.stat(path)
        size_kb = stat.st_size / 1024
        if size_kb >= 1024:
            result["File Size"] = f"{size_kb / 1024:.2f} MB"
        else:
            result["File Size"] = f"{size_kb:.1f} KB"
        mtime = datetime.datetime.fromtimestamp(stat.st_mtime)
        result["Modified"] = mtime.strftime("%Y-%m-%d  %H:%M")
    except Exception:
        pass

    # ── Image dimensions + format (via Pillow) ────────────────────────
    if suffix != ".svg":
        try:
            with Image.open(path) as img:
                result["Dimensions"] = f"{img.width} × {img.height} px"
                fmt = img.format or suffix.upper().lstrip(".")
                result["Format"] = fmt
                # colour mode (RGB, RGBA, L, …)
                result["Color Mode"] = img.mode
        except Exception:
            pass
    else:
        result["Format"] = "SVG"

    # ── EXIF tags via exifread (JPEG / TIFF / some HEIF) ─────────────
    # exifread raises ExifNotFound for PNG/WebP/BMP — that is expected.
    # The logger is already silenced at module load time so nothing
    # leaks to the terminal.
    if HAS_EXIFREAD and suffix not in {".png", ".bmp", ".svg", ".webp"}:
        try:
            with open(path, "rb") as f:
                tags = exifread.process_file(f, stop_tag="UNDEF", details=False)
            for key, val in tags.items():
                # "EXIF DateTimeOriginal" → "Date Taken"; generic: strip namespace
                clean_key = key.split(" ", 1)[-1]
                result[clean_key] = str(val)
        except Exception:
            pass

    return result


def get_image_dimensions(path: str) -> Optional[tuple[int, int]]:
    """Fast dimension check without fully decoding the image."""
    try:
        with Image.open(path) as img:
            return img.size  # (width, height)
    except Exception:
        return None


def get_oriented_image_dimensions(path: str) -> Optional[tuple[int, int]]:
    """Return dimensions after the same EXIF orientation transform used by edits."""
    try:
        with Image.open(path) as img:
            return ImageOps.exif_transpose(img).size
    except Exception:
        return None
