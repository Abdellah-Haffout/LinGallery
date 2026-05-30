"""
LinGallery — Two-tier LRU cache (memory + disk) for thumbnails.
"""
from __future__ import annotations
import hashlib
import os
from collections import OrderedDict
from pathlib import Path
from typing import Optional, Any


def _thumb_cache_dir() -> Path:
    d = Path("~/.cache/lingallery/thumbnails").expanduser()
    d.mkdir(parents=True, exist_ok=True)
    return d


def _path_hash(path: str) -> str:
    return hashlib.md5(path.encode()).hexdigest()


class CacheManager:
    """
    Two-tier LRU cache:
      L1 — in-memory dict of {path: QImage} with MB-based eviction.
      L2 — disk cache of pre-rendered thumbnails as PNG files.
    Thread-safe reads; writes should be done from a single thread (main thread).
    """

    def __init__(self, capacity_mb: int = 512):
        self._mem: OrderedDict[str, dict] = OrderedDict()
        self.capacity_mb = capacity_mb
        self._current_mb = 0.0
        self._disk_dir: Path = _thumb_cache_dir()

    # ─── Memory Cache ────────────────────────────────────────────────
    def get(self, key: str) -> Optional[Any]:
        if key not in self._mem:
            return None
        self._mem.move_to_end(key)
        return self._mem[key]["value"]

    def put(self, key: str, value: Any, size_mb: float) -> None:
        if key in self._mem:
            self._current_mb -= self._mem[key]["size"]
        self._mem[key] = {"value": value, "size": size_mb}
        self._mem.move_to_end(key)
        self._current_mb += size_mb
        self._evict()

    def _evict(self) -> None:
        while self._current_mb > self.capacity_mb and self._mem:
            _, evicted = self._mem.popitem(last=False)
            self._current_mb -= evicted["size"]

    def clear(self) -> None:
        self._mem.clear()
        self._current_mb = 0.0

    # ─── Disk Thumbnail Cache ─────────────────────────────────────────
    def disk_thumb_path(self, image_path: str, size: tuple[int, int]) -> Path:
        h = _path_hash(f"{image_path}_{size[0]}x{size[1]}")
        return self._disk_dir / f"{h}.png"

    def has_disk_thumb(self, image_path: str, size: tuple[int, int]) -> bool:
        return self.disk_thumb_path(image_path, size).exists()

    def save_disk_thumb(self, image_path: str, size: tuple[int, int], pil_image) -> None:
        try:
            dest = self.disk_thumb_path(image_path, size)
            pil_image.save(str(dest), "PNG")
        except Exception:
            pass

    def load_disk_thumb(self, image_path: str, size: tuple[int, int]):
        """Returns a PIL Image or None."""
        try:
            from PIL import Image as PilImage
            p = self.disk_thumb_path(image_path, size)
            if p.exists():
                return PilImage.open(str(p)).copy()
        except Exception:
            pass
        return None

    def clear_disk_cache(self) -> None:
        for f in self._disk_dir.glob("*.png"):
            try:
                f.unlink()
            except Exception:
                pass
