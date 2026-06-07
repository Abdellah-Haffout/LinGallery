"""
LinGallery — responsive, delegate-painted thumbnail grid.
"""
from __future__ import annotations

from collections import OrderedDict
from pathlib import Path
from typing import List

from PySide6.QtCore import (
    QAbstractListModel,
    QModelIndex,
    QRect,
    QSize,
    Qt,
    QTimer,
    Signal,
)
from PySide6.QtGui import QColor, QImage, QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QAbstractItemView,
    QLabel,
    QListView,
    QStyledItemDelegate,
    QStyle,
    QVBoxLayout,
    QWidget,
)

from core.constants import AppConst
from logic.image_manager import ImageManager
from ui.material_bridge import MaterialQtBridge


class _ImageGridModel(QAbstractListModel):
    PathRole = Qt.UserRole + 1
    PixmapRole = Qt.UserRole + 2

    # Maximum number of thumbnails held in memory at any time.
    # Once exceeded, the least-recently-used entries are evicted.
    MAX_THUMBNAILS = 200

    thumbnails_evicted = Signal(list)  # emitted with list of evicted paths

    def __init__(self, parent=None):
        super().__init__(parent)
        self._paths: list[str] = []
        self._pixmaps: OrderedDict[str, QPixmap] = OrderedDict()

    def rowCount(self, parent=QModelIndex()) -> int:
        return 0 if parent.isValid() else len(self._paths)

    def data(self, index: QModelIndex, role: int = Qt.DisplayRole):
        if not index.isValid() or index.row() >= len(self._paths):
            return None
        path = self._paths[index.row()]
        if role == self.PathRole:
            return path
        if role == self.PixmapRole:
            # Move to end (most-recently-used) on access
            if path in self._pixmaps:
                self._pixmaps.move_to_end(path)
                return self._pixmaps[path]
            return None
        if role == Qt.ToolTipRole:
            return Path(path).name
        return None

    def set_paths(self, paths: list[str]):
        self.beginResetModel()
        self._paths = paths
        self._pixmaps.clear()
        self.endResetModel()

    def paths(self) -> list[str]:
        return list(self._paths)

    def set_thumbnail(self, path: str, pixmap: QPixmap):
        row = self.row_for_path(path)
        if row < 0:
            return
        # Replacing the dict entry releases the old QPixmap's
        # GPU/server-side resource when its refcount drops to zero.
        self._pixmaps[path] = pixmap
        self._pixmaps.move_to_end(path)
        evicted: list[str] = []
        while len(self._pixmaps) > self.MAX_THUMBNAILS:
            old_path, old_pm = self._pixmaps.popitem(last=False)
            evicted.append(old_path)
            # Pixmap resource released when old_pm goes out of scope.
        if evicted:
            self.thumbnails_evicted.emit(evicted)
        idx = self.index(row, 0)
        self.dataChanged.emit(idx, idx, [self.PixmapRole])

    def remove_path(self, path: str):
        row = self.row_for_path(path)
        if row < 0:
            return
        self.beginRemoveRows(QModelIndex(), row, row)
        self._paths.pop(row)
        self._pixmaps.pop(path, None)
        self.endRemoveRows()

    def row_for_path(self, path: str) -> int:
        try:
            return self._paths.index(path)
        except ValueError:
            return -1


class _GalleryDelegate(QStyledItemDelegate):
    def __init__(self, parent=None):
        super().__init__(parent)
        self._radius = 8
        self._bridge = MaterialQtBridge.get()

    def paint(self, painter: QPainter, option, index: QModelIndex):
        painter.save()
        painter.setRenderHint(QPainter.Antialiasing)
        painter.setRenderHint(QPainter.SmoothPixmapTransform)

        rect = option.rect.adjusted(4, 4, -4, -4)
        selected = bool(option.state & QStyle.State_Selected)
        hover = bool(option.state & QStyle.State_MouseOver)

        theme = self._bridge.theme

        bg = QColor(theme.color_scheme.surface_container)
        if selected:
            bg = QColor(theme.color_scheme.primary_container)
        elif hover:
            bg = QColor(theme.color_scheme.surface_variant)

        painter.setPen(Qt.NoPen)
        painter.setBrush(bg)
        painter.drawRoundedRect(rect, self._radius, self._radius)

        pixmap = index.data(_ImageGridModel.PixmapRole)
        if isinstance(pixmap, QPixmap) and not pixmap.isNull():
            target = rect
            painter.setClipRect(target)
            scaled = pixmap.scaled(
                target.size(),
                Qt.KeepAspectRatioByExpanding,
                Qt.SmoothTransformation,
            )
            target_rect = QRect(
                target.center().x() - scaled.width() // 2,
                target.center().y() - scaled.height() // 2,
                scaled.width(),
                scaled.height(),
            )
            source_rect = QRect(0, 0, scaled.width(), scaled.height())
            painter.drawPixmap(target, scaled, source_rect)
            painter.setClipping(False)
        else:
            painter.setPen(QPen(QColor(theme.color_scheme.outline), 1))
            painter.setBrush(QColor(theme.color_scheme.surface_variant))
            painter.drawRoundedRect(rect.adjusted(16, 16, -16, -16), 6, 6)

        if selected:
            painter.setPen(QPen(QColor(theme.color_scheme.primary), 3))
            painter.setBrush(Qt.NoBrush)
            painter.drawRoundedRect(rect.adjusted(2, 2, -2, -2), self._radius, self._radius)

        painter.restore()

    def sizeHint(self, option, index: QModelIndex) -> QSize:
        return option.decorationSize


class GalleryView(QWidget):
    """
    Responsive thumbnail grid.
    Emits image_selected(path, image_list, index) to the viewer.
    """
    image_selected = Signal(str, list, int)

    def __init__(self, image_manager: ImageManager, parent=None):
        super().__init__(parent)
        self._manager = image_manager
        self._current_folder = ""
        self._requested_thumbs: set[str] = set()
        self._tile_size = 160
        self._model = _ImageGridModel(self)
        self._bridge = MaterialQtBridge.get()
        self._setup_ui()
        self._manager.thumbnail_ready.connect(self._on_thumbnail_ready)
        self._model.thumbnails_evicted.connect(self._on_thumbnails_evicted)

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 18)
        layout.setSpacing(0)

        self._empty_label = QLabel("Open a folder or select an album to browse images")
        self._empty_label.setAlignment(Qt.AlignCenter)
        self._empty_label.setStyleSheet(
            f"color: {self._bridge.theme.color_scheme.on_surface_variant}; font-size: 15px;"
        )
        layout.addWidget(self._empty_label)

        self._view = QListView()
        self._view.setModel(self._model)
        self._view.setItemDelegate(_GalleryDelegate(self._view))
        self._view.setViewMode(QListView.IconMode)
        self._view.setResizeMode(QListView.Adjust)
        self._view.setMovement(QListView.Static)
        self._view.setSelectionMode(QAbstractItemView.SingleSelection)
        self._view.setUniformItemSizes(True)
        self._view.setLayoutMode(QListView.Batched)
        self._view.setBatchSize(96)
        self._view.setWrapping(True)
        self._view.setWordWrap(False)
        self._view.setMouseTracking(True)
        self._view.setVerticalScrollMode(QAbstractItemView.ScrollPerPixel)
        self._view.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self._view.setStyleSheet(f"""
            QListView {{
                background-color: transparent;
                border: none;
                outline: none;
            }}
            QScrollBar:vertical {{
                background: {self._bridge.theme.color_scheme.background};
                width: 6px;
                border-radius: 3px;
            }}
            QScrollBar::handle:vertical {{
                background: {self._bridge.theme.color_scheme.outline};
                border-radius: 3px;
            }}
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
                height: 0;
            }}
        """)
        self._view.hide()
        self._view.doubleClicked.connect(self._on_double_clicked)
        self._view.verticalScrollBar().valueChanged.connect(self._request_visible_thumbnails)
        layout.addWidget(self._view)

    def load_folder(self, folder_path: str):
        if folder_path == self._current_folder:
            return
        self._current_folder = folder_path
        self._requested_thumbs.clear()

        path = Path(folder_path)
        try:
            # Collect all image files
            image_files = [
                f for f in path.iterdir()
                if f.is_file() and f.suffix.lower() in AppConst.SUPPORTED_FORMATS
            ]
            # Sort by filesystem modification time, newest first
            image_files.sort(key=lambda f: f.stat().st_mtime, reverse=True)
            images = [str(f) for f in image_files]
        except OSError:
            images = []

        self._model.set_paths(images)
        self._update_grid_metrics()

        if not images:
            self._view.hide()
            self._empty_label.show()
            return

        self._empty_label.hide()
        self._view.show()
        QTimer.singleShot(0, self._request_visible_thumbnails)

    def get_image_list(self) -> List[str]:
        return self._model.paths()

    def remove_image(self, path: str):
        self._model.remove_path(path)
        self._requested_thumbs.discard(path)
        if not self._model.paths():
            self._view.hide()
            self._empty_label.show()

    def clear(self):
        """Flush in-memory state during a refresh."""
        self._current_folder = ""
        self._requested_thumbs.clear()
        self._model.set_paths([])
        self._view.hide()
        self._empty_label.show()

    def refresh_current_folder(self):
        folder = self._current_folder
        self._current_folder = ""
        if folder:
            self.load_folder(folder)

    def add_image(self, path: str):
        """
        Insert a newly created image into the model without reloading
        the entire folder.  Keeps the list sorted by filesystem mtime
        (newest first) and requests a thumbnail immediately.

        Uses Qt model row insertion so existing thumbnails are preserved
        — only the new image needs to be decoded.
        """
        if not path or self._model.row_for_path(path) >= 0:
            return  # already present
        folder = str(Path(path).parent)
        if folder != self._current_folder:
            return  # not the active album — nothing to do here

        paths = self._model.paths()
        paths.append(path)
        # Sort by filesystem modification time, newest first
        paths.sort(key=lambda p: Path(p).stat().st_mtime, reverse=True)
        row = paths.index(path)
        self._model.beginInsertRows(QModelIndex(), row, row)
        self._model._paths.insert(row, path)
        self._model.endInsertRows()
        # Request thumbnail for the new image only; existing pixmaps
        # are untouched so no re-decoding is needed.
        self._requested_thumbs.add(path)
        self._manager.request_thumbnail(path, (self._tile_size, self._tile_size), priority=10)
        # Trigger a layout pass so visualRect is correct for the new row,
        # then request any other newly-visible thumbnails.
        QTimer.singleShot(0, self._request_visible_thumbnails)
        self._empty_label.hide()
        self._view.show()

    def _on_thumbnail_ready(self, path: str, qimage: QImage):
        if self._model.row_for_path(path) < 0:
            # The path isn't currently in the model.  This can happen
            # when a stale task from before a model rebuild delivers its
            # result late.  Force a fresh request so the grid is never
            # left with a blank slot for a valid image.
            self._requested_thumbs.discard(path)
            return
        self._model.set_thumbnail(path, QPixmap.fromImage(qimage))

    def _on_thumbnails_evicted(self, evicted_paths: list):
        """Remove evicted paths from the requested set so they are
        re-requested from ImageManager when scrolled back into view."""
        for path in evicted_paths:
            self._requested_thumbs.discard(path)

    def _on_double_clicked(self, index: QModelIndex):
        path = index.data(_ImageGridModel.PathRole)
        paths = self._model.paths()
        if path in paths:
            self.image_selected.emit(path, paths, paths.index(path))

    def _request_visible_thumbnails(self):
        paths = self._model.paths()
        if not paths or not self._view.isVisible():
            return

        viewport = self._view.viewport().rect().adjusted(0, -self._tile_size, 0, self._tile_size)
        for row, path in enumerate(paths):
            if path in self._requested_thumbs:
                continue
            idx = self._model.index(row, 0)
            if self._view.visualRect(idx).intersects(viewport):
                self._requested_thumbs.add(path)
                self._manager.request_thumbnail(path, (self._tile_size, self._tile_size))

    def _update_grid_metrics(self):
        width = max(1, self._view.viewport().width())
        spacing = 8
        min_tile = 132
        max_tile = 220
        columns = max(2, width // (min_tile + spacing))
        tile = (width - spacing * max(0, columns - 1)) // columns
        tile = max(min_tile, min(max_tile, tile))
        self._tile_size = tile
        self._view.setSpacing(spacing)
        self._view.setGridSize(QSize(tile + spacing, tile + spacing))
        self._view.setIconSize(QSize(tile, tile))
        delegate = self._view.itemDelegate()
        if delegate:
            self._view.setItemDelegate(delegate)

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._update_grid_metrics()
        QTimer.singleShot(0, self._request_visible_thumbnails)
