"""
LinGallery — Two-tier LRU cache (memory + disk) for thumbnails.
"""
from __future__ import annotations
import hashlib
import os
import threading
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
        self._lock = threading.RLock()

    # ─── Memory Cache ────────────────────────────────────────────────
    def get(self, key: str) -> Optional[Any]:
        with self._lock:
            if key not in self._mem:
                return None
            self._mem.move_to_end(key)
            return self._mem[key]["value"]

    def put(self, key: str, value: Any, size_mb: float) -> None:
        with self._lock:
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
            # Explicitly release the QImage pixel buffer so the allocator
            # can reclaim the memory instead of keeping it as fragmentation.
            val = evicted.get("value")
            if val is not None and hasattr(val, "detach"):
                try:
                    val.detach()
                except Exception:
                    pass

    def clear(self) -> None:
        with self._lock:
            for key, entry in list(self._mem.items()):
                val = entry.get("value")
                if val is not None and hasattr(val, "detach"):
                    try:
                        val.detach()
                    except Exception:
                        pass
            self._mem.clear()
            self._current_mb = 0.0

    def remove(self, key: str) -> None:
        with self._lock:
            entry = self._mem.pop(key, None)
            if entry:
                self._current_mb = max(0.0, self._current_mb - entry["size"])
                val = entry.get("value")
                if val is not None and hasattr(val, "detach"):
                    try:
                        val.detach()
                    except Exception:
                        pass

    # ─── Disk Thumbnail Cache ─────────────────────────────────────────
    def disk_thumb_path(self, image_path: str, size: tuple[int, int]) -> Path:
        try:
            stat = os.stat(image_path)
            version = f"{stat.st_mtime_ns}_{stat.st_size}"
        except OSError:
            version = "missing"
        path_id = _path_hash(image_path)
        content_id = _path_hash(f"{image_path}_{version}_{size[0]}x{size[1]}")
        return self._disk_dir / f"{path_id}_{content_id}.png"

    def has_disk_thumb(self, image_path: str, size: tuple[int, int]) -> bool:
        with self._lock:
            return self.disk_thumb_path(image_path, size).exists()

    def save_disk_thumb(self, image_path: str, size: tuple[int, int], pil_image) -> None:
        with self._lock:
            try:
                dest = self.disk_thumb_path(image_path, size)
                pil_image.save(str(dest), "PNG")
            except Exception:
                pass

    def load_disk_thumb(self, image_path: str, size: tuple[int, int]):
        """Returns a PIL Image or None."""
        with self._lock:
            try:
                from PIL import Image as PilImage
                p = self.disk_thumb_path(image_path, size)
                if p.exists():
                    with PilImage.open(str(p)) as img:
                        img.load()
                        return img.copy()
            except Exception:
                pass
        return None

    def clear_disk_cache(self) -> None:
        with self._lock:
            for f in self._disk_dir.glob("*.png"):
                try:
                    f.unlink()
                except Exception:
                    pass

    def invalidate_disk_thumbs(self, image_path: str) -> None:
        with self._lock:
            path_id = _path_hash(image_path)
            for f in self._disk_dir.glob(f"{path_id}_*.png"):
                try:
                    f.unlink()
                except Exception:
                    pass
