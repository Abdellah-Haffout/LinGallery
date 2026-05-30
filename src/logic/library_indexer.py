"""
LinGallery — Library Indexer.
Scans one or more root directories in a background QThread and emits progress signals.
"""
from __future__ import annotations
import os
from pathlib import Path
from typing import List

from PySide6.QtCore import QThread, Signal

from core.constants import AppConst


class LibraryIndexer(QThread):
    """
    Background thread that recursively scans root directories.

    Signals:
        album_found(folder_path, image_count, first_image_path)
            — Emitted for each directory that contains at least one image.
        scan_progress(scanned_dirs, total_images_so_far)
            — Emitted periodically during the scan.
        scan_complete(total_albums, total_images)
            — Emitted once the full scan is finished.
    """
    album_found   = Signal(str, int, str)   # path, count, preview_path
    scan_progress = Signal(int, int)        # scanned_dirs, total_images
    scan_complete = Signal(int, int)        # total_albums, total_images

    def __init__(self, roots: List[str] = None, parent=None):
        super().__init__(parent)
        self._roots = [
            Path(r).expanduser()
            for r in (roots or AppConst.DEFAULT_SCAN_ROOTS)
        ]
        self._stop_requested = False

    def stop(self):
        self._stop_requested = True
        self.wait()

    def run(self):
        total_albums = 0
        total_images = 0
        scanned_dirs = 0

        for root in self._roots:
            if not root.exists():
                continue
            # Walk the entire subtree
            for dirpath, dirnames, filenames in os.walk(root):
                if self._stop_requested:
                    return

                # Filter hidden directories in-place (skip .thumbnails, .cache, etc.)
                dirnames[:] = [
                    d for d in dirnames
                    if not d.startswith(".") and d not in {"lost+found"}
                ]

                images = [
                    f for f in filenames
                    if Path(f).suffix.lower() in AppConst.SUPPORTED_FORMATS
                ]

                scanned_dirs += 1

                if images:
                    first = str(Path(dirpath) / images[0])
                    count = len(images)
                    total_images += count
                    total_albums += 1
                    self.album_found.emit(str(dirpath), count, first)

                if scanned_dirs % 10 == 0:
                    self.scan_progress.emit(scanned_dirs, total_images)

        self.scan_complete.emit(total_albums, total_images)
