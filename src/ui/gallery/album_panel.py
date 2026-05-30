"""
LinGallery — Album Panel (left sidebar).
Shows discovered folders as clickable album items with image count badges.
"""
from __future__ import annotations
from pathlib import Path

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QListWidget, QListWidgetItem,
    QLabel, QHBoxLayout, QAbstractItemView
)
from PySide6.QtCore import Qt, Signal, QSize
from PySide6.QtGui import QFont

from core.constants import DarkPalette, AppConst


class AlbumPanel(QWidget):
    """
    Left sidebar listing albums (directories) discovered by LibraryIndexer.
    Emits album_selected(folder_path) when an album is clicked.
    """
    album_selected = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedWidth(AppConst.SIDEBAR_WIDTH)
        self._setup_ui()
        self._album_paths: dict[str, int] = {}  # path → row index

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        # Header
        header = QWidget()
        header.setFixedHeight(AppConst.TOP_BAR_HEIGHT)
        header.setStyleSheet(f"background-color: {DarkPalette.SURFACE};")
        hlay = QHBoxLayout(header)
        hlay.setContentsMargins(16, 0, 16, 0)
        title = QLabel("Albums")
        title.setStyleSheet(
            f"font-size: 14px; font-weight: 700; "
            f"color: {DarkPalette.ON_SURFACE}; letter-spacing: 0.5px;"
        )
        hlay.addWidget(title)
        layout.addWidget(header)

        # Divider
        div = QWidget()
        div.setFixedHeight(1)
        div.setStyleSheet(f"background-color: {DarkPalette.DIVIDER};")
        layout.addWidget(div)

        # List
        self.list_widget = QListWidget()
        self.list_widget.setSelectionMode(QAbstractItemView.SingleSelection)
        self.list_widget.setFocusPolicy(Qt.NoFocus)
        self.list_widget.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.list_widget.setStyleSheet(f"""
            QListWidget {{
                background-color: {DarkPalette.SURFACE};
                border: none;
                outline: none;
                padding: 8px 0;
            }}
            QListWidget::item {{
                padding: 10px 16px;
                border-radius: 0;
                color: {DarkPalette.ON_SURFACE_VARIANT};
                font-size: 13px;
            }}
            QListWidget::item:hover {{
                background-color: {DarkPalette.SURFACE_VARIANT};
                color: {DarkPalette.ON_SURFACE};
            }}
            QListWidget::item:selected {{
                background-color: {DarkPalette.PRIMARY_CONTAINER};
                color: {DarkPalette.ON_PRIMARY_CONTAINER};
            }}
            QScrollBar:vertical {{
                background: {DarkPalette.SURFACE};
                width: 4px;
                border-radius: 2px;
            }}
            QScrollBar::handle:vertical {{
                background: {DarkPalette.OUTLINE};
                border-radius: 2px;
            }}
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
                height: 0;
            }}
        """)
        self.list_widget.itemClicked.connect(self._on_item_clicked)
        layout.addWidget(self.list_widget)

        self.setStyleSheet(f"background-color: {DarkPalette.SURFACE};")

    def add_album(self, folder_path: str, image_count: int, _preview: str = ""):
        """Add or update an album entry."""
        if folder_path in self._album_paths:
            row = self._album_paths[folder_path]
            item = self.list_widget.item(row)
            item.setData(Qt.UserRole + 1, image_count)
            item.setText(self._format_label(folder_path, image_count))
            return

        item = QListWidgetItem(self._format_label(folder_path, image_count))
        item.setData(Qt.UserRole, folder_path)
        item.setData(Qt.UserRole + 1, image_count)
        item.setSizeHint(QSize(AppConst.SIDEBAR_WIDTH - 8, 48))
        self.list_widget.addItem(item)
        self._album_paths[folder_path] = self.list_widget.count() - 1

    def _format_label(self, path: str, count: int) -> str:
        name = Path(path).name or path
        return f"{name}  ({count})"

    def _on_item_clicked(self, item: QListWidgetItem):
        path = item.data(Qt.UserRole)
        if path:
            self.album_selected.emit(path)

    def clear(self):
        self.list_widget.clear()
        self._album_paths.clear()

    def select_first(self):
        if self.list_widget.count() > 0:
            self.list_widget.setCurrentRow(0)
            item = self.list_widget.item(0)
            if item:
                self._on_item_clicked(item)
