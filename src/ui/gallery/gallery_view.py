"""
LinGallery — Gallery View (async lazy-loading thumbnail grid).
"""
from __future__ import annotations
from pathlib import Path
from typing import List

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QListWidget, QListWidgetItem,
    QListView, QAbstractItemView, QLabel
)
from PySide6.QtCore import Qt, QSize, Signal
from PySide6.QtGui import QIcon, QPixmap, QImage

from core.constants import AppConst, DarkPalette
from logic.image_manager import ImageManager


class GalleryView(QWidget):
    """
    Async thumbnail grid. Thumbnails load in background threads.
    Emits image_selected(path, image_list, index) to the viewer.
    """
    image_selected = Signal(str, list, int)  # path, all paths, index

    def __init__(self, image_manager: ImageManager, parent=None):
        super().__init__(parent)
        self._manager = image_manager
        self._image_paths: List[str] = []
        self._path_to_item: dict[str, QListWidgetItem] = {}
        self._current_folder = ""
        self._setup_ui()

        # Connect async delivery
        self._manager.thumbnail_ready.connect(self._on_thumbnail_ready)

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(0)

        # Empty state label
        self._empty_label = QLabel("Open a folder or select an album to browse images")
        self._empty_label.setAlignment(Qt.AlignCenter)
        self._empty_label.setStyleSheet(
            f"color: {DarkPalette.ON_SURFACE_VARIANT}; font-size: 15px;"
        )
        layout.addWidget(self._empty_label)

        thumb_w, thumb_h = AppConst.THUMB_SIZE
        self._list = QListWidget()
        self._list.setViewMode(QListView.IconMode)
        self._list.setIconSize(QSize(thumb_w, thumb_h))
        self._list.setResizeMode(QListView.Adjust)
        self._list.setSpacing(AppConst.THUMB_SPACING)
        self._list.setMovement(QListView.Static)
        self._list.setSelectionMode(QAbstractItemView.SingleSelection)
        self._list.setUniformItemSizes(True)
        self._list.setLayoutMode(QListView.Batched)
        self._list.setBatchSize(30)
        self._list.setStyleSheet(f"""
            QListWidget {{
                background-color: transparent;
                border: none;
                outline: none;
            }}
            QListWidget::item {{
                background-color: {DarkPalette.SURFACE_CONTAINER};
                border-radius: {AppConst.CORNER_RADIUS}px;
                padding: 4px;
                color: {DarkPalette.ON_SURFACE_VARIANT};
                font-size: 11px;
            }}
            QListWidget::item:selected {{
                background-color: {DarkPalette.PRIMARY_CONTAINER};
                color: {DarkPalette.ON_PRIMARY_CONTAINER};
            }}
            QListWidget::item:hover {{
                background-color: {DarkPalette.SURFACE_VARIANT};
            }}
            QScrollBar:vertical {{
                background: {DarkPalette.BACKGROUND};
                width: 6px;
                border-radius: 3px;
            }}
            QScrollBar::handle:vertical {{
                background: {DarkPalette.OUTLINE};
                border-radius: 3px;
            }}
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
                height: 0;
            }}
        """)
        self._list.hide()
        self._list.itemDoubleClicked.connect(self._on_double_clicked)
        layout.addWidget(self._list)

    # ── Public API ────────────────────────────────────────────────────
    def load_folder(self, folder_path: str):
        if folder_path == self._current_folder:
            return
        self._current_folder = folder_path
        self._image_paths.clear()
        self._path_to_item.clear()
        self._list.clear()

        path = Path(folder_path)
        images = sorted(
            str(f) for f in path.iterdir()
            if f.suffix.lower() in AppConst.SUPPORTED_FORMATS
        )

        if not images:
            self._list.hide()
            self._empty_label.show()
            return

        self._image_paths = images
        self._empty_label.hide()
        self._list.show()

        # Create placeholder items immediately (non-blocking)
        placeholder = _placeholder_pixmap(*AppConst.THUMB_SIZE)
        for img_path in images:
            item = QListWidgetItem(QIcon(placeholder), Path(img_path).name)
            item.setData(Qt.UserRole, img_path)
            item.setTextAlignment(Qt.AlignCenter)
            item.setSizeHint(QSize(
                AppConst.THUMB_SIZE[0] + 16,
                AppConst.THUMB_SIZE[1] + 32
            ))
            self._list.addItem(item)
            self._path_to_item[img_path] = item

        # Kick off background thumbnail loading
        for img_path in images:
            self._manager.request_thumbnail(img_path)

    def get_image_list(self) -> List[str]:
        return list(self._image_paths)

    # ── Slots ─────────────────────────────────────────────────────────
    def _on_thumbnail_ready(self, path: str, qimage: QImage):
        item = self._path_to_item.get(path)
        if item is None:
            return
        pixmap = QPixmap.fromImage(qimage)
        item.setIcon(QIcon(pixmap))

    def _on_double_clicked(self, item: QListWidgetItem):
        path = item.data(Qt.UserRole)
        if path and path in self._image_paths:
            idx = self._image_paths.index(path)
            self.image_selected.emit(path, list(self._image_paths), idx)


# ── Helpers ───────────────────────────────────────────────────────────
def _placeholder_pixmap(w: int, h: int) -> QPixmap:
    """A subtle dark rectangle as a thumbnail placeholder."""
    pm = QPixmap(w, h)
    pm.fill(Qt.transparent)
    from PySide6.QtGui import QPainter, QColor, QPen
    p = QPainter(pm)
    p.setRenderHint(QPainter.Antialiasing)
    p.setBrush(QColor(DarkPalette.SURFACE_VARIANT))
    p.setPen(Qt.NoPen)
    p.drawRoundedRect(0, 0, w, h, AppConst.CORNER_RADIUS, AppConst.CORNER_RADIUS)
    p.end()
    return pm
