"""
LinGallery — Async Image Manager.
Decodes images in background QRunnable tasks, serves results via signals,
and maintains a two-tier LRU cache (memory + disk thumbnails).
"""
from __future__ import annotations
import os
from typing import Optional

from PySide6.QtCore import QObject, QRunnable, QThreadPool, Signal, Qt, QSize
from PySide6.QtGui import QImage, QImageReader, QPixmap
from PIL import Image as PilImage

from storage.cache_manager import CacheManager
from core.constants import AppConst


# ─────────────────────────────────────────────────────────────────────
# Worker signals (must live in a QObject, not QRunnable)
# ─────────────────────────────────────────────────────────────────────
class _LoadSignals(QObject):
    loaded = Signal(str, QImage, int)   # (path, image, generation)
    error  = Signal(str, str, int)      # (path, message, generation)
    
    def __del__(self):
        """Prevent crashes when tasks are deleted during shutdown."""
        try:
            self.loaded.disconnect()
        except Exception:
            pass
        try:
            self.error.disconnect()
        except Exception:
            pass


class _ThumbTask(QRunnable):
    """
    Decodes a thumbnail for the gallery grid.
    Checks disk cache first; generates + saves if missing.
    """
    def __init__(self, path: str, size: tuple[int, int], cache: CacheManager, generation: int):
        super().__init__()
        self.setAutoDelete(True)
        self.path = path
        self.size = size
        self.cache = cache
        self.generation = generation
        self.signals = _LoadSignals()

    def run(self):
        if not os.path.exists(self.path):
            try:
                self.signals.error.emit(self.path, "File not found", self.generation)
            except RuntimeError:
                pass
            return

        # 1 — Try disk cache
        disk_img = self.cache.load_disk_thumb(self.path, self.size)
        if disk_img is not None:
            qimg = _pil_to_qimage(disk_img)
            if qimg and not qimg.isNull():
                try:
                    self.signals.loaded.emit(self.path, qimg, self.generation)
                except RuntimeError:
                    pass
                return

        # 2 — Decode via QImageReader (handles all Qt-supported formats)
        try:
            reader = QImageReader(self.path)
            reader.setAutoTransform(True)
            native = reader.size()
            if native.isValid() and native.width() > 0:
                target = QSize(*self.size)
                scaled = native.scaled(target, Qt.KeepAspectRatio)
                reader.setScaledSize(scaled)
            img = reader.read()
            if img.isNull():
                try:
                    self.signals.error.emit(self.path, reader.errorString(), self.generation)
                except RuntimeError:
                    pass
            else:
                # Save to disk cache via PIL for next run
                try:
                    pil = _qimage_to_pil(img)
                    if pil:
                        self.cache.save_disk_thumb(self.path, self.size, pil)
                except Exception:
                    pass
                try:
                    self.signals.loaded.emit(self.path, img, self.generation)
                except RuntimeError:
                    pass
        except Exception as e:
            try:
                self.signals.error.emit(self.path, str(e), self.generation)
            except RuntimeError:
                pass


class _FullImageTask(QRunnable):
    """Decodes an image for the viewer, downscaled to the display's
    maximum dimension to avoid storing multi-megapixel buffers that will
    never be shown at native resolution."""
    def __init__(self, path: str, max_dimension: int = 0, generation: int = 0):
        super().__init__()
        self.setAutoDelete(True)
        self.path = path
        self.max_dimension = max_dimension
        self.generation = generation
        self.signals = _LoadSignals()

    def run(self):
        if not os.path.exists(self.path):
            try:
                self.signals.error.emit(self.path, "File not found", self.generation)
            except RuntimeError:
                pass
            return
        try:
            reader = QImageReader(self.path)
            reader.setAutoTransform(True)
            if self.max_dimension > 0:
                native = reader.size()
                if native.isValid() and native.width() > 0:
                    target = QSize(self.max_dimension, self.max_dimension)
                    scaled = native.scaled(target, Qt.KeepAspectRatio)
                    reader.setScaledSize(scaled)
            img = reader.read()
            if img.isNull():
                try:
                    self.signals.error.emit(self.path, reader.errorString(), self.generation)
                except RuntimeError:
                    pass
            else:
                try:
                    self.signals.loaded.emit(self.path, img, self.generation)
                except RuntimeError:
                    pass
        except Exception as e:
            try:
                self.signals.error.emit(self.path, str(e), self.generation)
            except RuntimeError:
                pass


# ─────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────
def _pil_to_qimage(pil_img: PilImage.Image) -> Optional[QImage]:
    try:
        pil_img = pil_img.convert("RGBA")
        data = pil_img.tobytes("raw", "RGBA")
        return QImage(data, pil_img.width, pil_img.height, QImage.Format_RGBA8888).copy()
    except Exception:
        return None


def _qimage_to_pil(qimg: QImage) -> Optional[PilImage.Image]:
    try:
        qimg = qimg.convertToFormat(QImage.Format_RGBA8888)
        ptr = qimg.bits()
        size = qimg.width() * qimg.height() * 4
        return PilImage.frombytes("RGBA", (qimg.width(), qimg.height()), bytes(ptr)[:size])
    except Exception:
        return None


# ─────────────────────────────────────────────────────────────────────
# Public Manager
# ─────────────────────────────────────────────────────────────────────
class ImageManager(QObject):
    """
    Central image loading controller.
    All heavy work runs in QThreadPool workers.
    Results are delivered on the main thread via Qt signals.
    """
    thumbnail_ready    = Signal(str, QImage)   # gallery thumbnails
    full_image_ready   = Signal(str, QImage)   # viewer full-res images
    load_error         = Signal(str, str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._pool = QThreadPool.globalInstance()
        self._pool.setMaxThreadCount(max(4, os.cpu_count() or 4))
        self._cache = CacheManager(capacity_mb=AppConst.MAX_CACHE_MB)
        self._pending_thumbs: set[str] = set()
        self._pending_full: set[str] = set()
        self._generation = 0

    def flush_state(self):
        """Clear all cached images and reset runtime state for a refresh."""
        self._cache.clear()
        self._pending_thumbs.clear()
        self._pending_full.clear()
        self._generation += 1
        print(f"[ImageManager] State flushed. Advanced to generation {self._generation}.")

    # ── Thumbnails ────────────────────────────────────────────────────
    def request_thumbnail(self, path: str, size: tuple[int, int] = None, priority: int = 0, force: bool = False) -> None:
        """Request a thumbnail.  When *force* is True the pending-set
        guard is bypassed so a fresh task is always submitted (used
        after invalidation to override stale in-flight results)."""
        size = size or AppConst.THUMB_SIZE

        # L1: memory  (skip when forcing a fresh decode)
        if not force:
            cached = self._cache.get(f"thumb:{path}")
            if cached is not None:
                self.thumbnail_ready.emit(path, cached)
                return

            if path in self._pending_thumbs:
                return

        self._pending_thumbs.add(path)

        task = _ThumbTask(path, size, self._cache, self._generation)
        task.signals.loaded.connect(self._on_thumb_loaded)
        task.signals.error.connect(self._on_error)
        self._pool.start(task, priority)

    def _on_thumb_loaded(self, path: str, img: QImage, generation: int) -> None:
        if generation != self._generation:
            print(f"[ImageManager] Discarding stale thumb task for {path} (gen {generation} vs {self._generation})")
            return
        self._pending_thumbs.discard(path)
        size_mb = img.sizeInBytes() / (1024 * 1024)
        self._cache.put(f"thumb:{path}", img, size_mb)
        self.thumbnail_ready.emit(path, img)

    # ── Full Images ───────────────────────────────────────────────────
    def request_full_image(self, path: str, max_dimension: int = 0) -> None:
        cached = self._cache.get(f"full:{path}")
        if cached is not None:
            self.full_image_ready.emit(path, cached)
            return

        if path in self._pending_full:
            return
        self._pending_full.add(path)

        task = _FullImageTask(path, max_dimension=max_dimension, generation=self._generation)
        task.signals.loaded.connect(self._on_full_loaded)
        task.signals.error.connect(self._on_error)
        self._pool.start(task)

    def _on_full_loaded(self, path: str, img: QImage, generation: int) -> None:
        if generation != self._generation:
            print(f"[ImageManager] Discarding stale full task for {path} (gen {generation} vs {self._generation})")
            return
        self._pending_full.discard(path)
        size_mb = img.sizeInBytes() / (1024 * 1024)
        self._cache.put(f"full:{path}", img, size_mb)
        self.full_image_ready.emit(path, img)

    def _on_error(self, path: str, msg: str, generation: int) -> None:
        if generation != self._generation:
            return
        self._pending_thumbs.discard(path)
        self._pending_full.discard(path)
        self.load_error.emit(path, msg)

    def invalidate(self, path: str) -> None:
        """Call after editing an image to force a fresh decode.
        Clears memory cache, disk cache, AND cancels any in-flight
        decode tasks so stale results are never stored."""
        self._cache.remove(f"thumb:{path}")
        self._cache.remove(f"full:{path}")
        self._cache.invalidate_disk_thumbs(path)
        # Cancel in-flight tasks — their results will be discarded
        # by the late-arrival guards in _on_thumb_loaded / _on_full_loaded.
        self._pending_thumbs.discard(path)
        self._pending_full.discard(path)
