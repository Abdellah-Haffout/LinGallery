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


def _save_image(img: Image.Image, path: str) -> None:
    """Save a PIL image back to its original path, preserving format."""
    suffix = Path(path).suffix.lower()
    fmt_map = {
        ".jpg": "JPEG", ".jpeg": "JPEG",
        ".png": "PNG", ".webp": "WEBP",
        ".bmp": "BMP", ".tiff": "TIFF", ".tif": "TIFF",
    }
    fmt = fmt_map.get(suffix, "PNG")
    save_args = {"quality": 95} if fmt == "JPEG" else {}
    img.save(path, fmt, **save_args)


def rotate(path: str, degrees: int) -> bool:
    """Rotate image by degrees (counter-clockwise). expand=True preserves full image."""
    try:
        img = ImageOps.exif_transpose(Image.open(path))
        img = img.rotate(degrees, expand=True)
        _save_image(img, path)
        return True
    except Exception:
        return False


def flip_horizontal(path: str) -> bool:
    """Mirror the image horizontally (left-right)."""
    try:
        img = ImageOps.exif_transpose(Image.open(path))
        img = ImageOps.mirror(img)
        _save_image(img, path)
        return True
    except Exception:
        return False


def flip_vertical(path: str) -> bool:
    """Flip the image vertically (top-bottom)."""
    try:
        img = ImageOps.exif_transpose(Image.open(path))
        img = ImageOps.flip(img)
        _save_image(img, path)
        return True
    except Exception:
        return False


def crop(path: str, x: int, y: int, width: int, height: int, dest_path: str | None = None) -> bool:
    """Crop the image to the given pixel rectangle."""
    try:
        img = ImageOps.exif_transpose(Image.open(path))
        left = max(0, min(x, img.width - 1))
        top = max(0, min(y, img.height - 1))
        right = max(left + 1, min(x + width, img.width))
        bottom = max(top + 1, min(y + height, img.height))
        box = (left, top, right, bottom)
        img = img.crop(box)
        _save_image(img, dest_path or path)
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
