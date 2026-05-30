"""
LinGallery — Slideshow Controller.
Drives automatic image advancement at a configurable interval.
"""
from __future__ import annotations

from PySide6.QtCore import QObject, QTimer, Signal

from core.constants import AppConst


class SlideshowController(QObject):
    """
    Timer-based slideshow. Emits next_requested / prev_requested to the viewer.
    """
    next_requested = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self._timer = QTimer(self)
        self._timer.timeout.connect(self.next_requested)
        self._interval_ms = AppConst.SLIDESHOW_DEFAULT_INTERVAL_MS
        self._running = False

    @property
    def is_running(self) -> bool:
        return self._running

    def set_interval(self, ms: int):
        self._interval_ms = max(500, ms)
        if self._running:
            self._timer.setInterval(self._interval_ms)

    def start(self):
        self._timer.start(self._interval_ms)
        self._running = True

    def stop(self):
        self._timer.stop()
        self._running = False

    def toggle(self) -> bool:
        """Start if stopped, stop if running. Returns new state."""
        if self._running:
            self.stop()
        else:
            self.start()
        return self._running
