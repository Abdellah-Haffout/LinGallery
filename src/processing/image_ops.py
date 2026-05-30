"""
LinGallery — Image Processing Operations.
All operations use Pillow and are safe to call from background threads.
"""
from __future__ import annotations
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
    HAS_EXIFREAD = True
except ImportError:
    HAS_EXIFREAD = False


def _save_in_place(img: Image.Image, path: str) -> None:
    """Save a PIL image back to its original path, preserving format."""
    suffix = Path(path).suffix.lower()
    fmt_map = {
        ".jpg": "JPEG", ".jpeg": "JPEG",
        ".png": "PNG", ".webp": "WEBP",
        ".bmp": "BMP", ".tiff": "TIFF", ".tif": "TIFF",
    }
    fmt = fmt_map.get(suffix, "PNG")
    img.save(path, fmt, quality=95 if fmt == "JPEG" else None)


def rotate(path: str, degrees: int) -> bool:
    """Rotate image by degrees (counter-clockwise). expand=True preserves full image."""
    try:
        img = Image.open(path)
        img = img.rotate(degrees, expand=True)
        _save_in_place(img, path)
        return True
    except Exception:
        return False


def flip_horizontal(path: str) -> bool:
    """Mirror the image horizontally (left-right)."""
    try:
        img = Image.open(path)
        img = ImageOps.mirror(img)
        _save_in_place(img, path)
        return True
    except Exception:
        return False


def flip_vertical(path: str) -> bool:
    """Flip the image vertically (top-bottom)."""
    try:
        img = Image.open(path)
        img = ImageOps.flip(img)
        _save_in_place(img, path)
        return True
    except Exception:
        return False


def crop(path: str, x: int, y: int, width: int, height: int) -> bool:
    """Crop the image to the given pixel rectangle."""
    try:
        img = Image.open(path)
        box = (x, y, x + width, y + height)
        img = img.crop(box)
        _save_in_place(img, path)
        return True
    except Exception:
        return False


def read_exif(path: str) -> dict:
    """Return a flat dict of human-readable EXIF tags."""
    result = {}
    suffix = Path(path).suffix.lower()
    if suffix == ".svg":
        return result

    if HAS_EXIFREAD:
        try:
            with open(path, "rb") as f:
                tags = exifread.process_file(f, stop_tag="UNDEF", details=False)
            for key, val in tags.items():
                clean_key = key.split(" ")[-1]  # "EXIF DateTimeOriginal" → "DateTimeOriginal"
                result[clean_key] = str(val)
        except Exception:
            pass

    # Always add basic file metadata
    try:
        stat = os.stat(path)
        result["File Size"] = f"{stat.st_size / 1024:.1f} KB"
        img = Image.open(path)
        result["Dimensions"] = f"{img.width} × {img.height} px"
        result["Format"] = img.format or Path(path).suffix.upper().lstrip(".")
    except Exception:
        pass

    return result


def get_image_dimensions(path: str) -> Optional[tuple[int, int]]:
    """Fast dimension check without fully decoding the image."""
    try:
        img = Image.open(path)
        return img.size  # (width, height)
    except Exception:
        return None
