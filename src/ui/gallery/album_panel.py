"""
LinGallery — Album Panel (left sidebar).
Shows discovered folders as clickable album items with image count badges.
"""
from __future__ import annotations
from pathlib import Path

from PySide6.QtWidgets import QWidget, QVBoxLayout, QListWidget, QListWidgetItem
from PySide6.QtCore import Qt, Signal, QSize

from pymaterial.components import Column, Text, TextProps, Divider, DividerProps, ListComponent, ListItem, ListItemProps
from ui.material_bridge import MaterialQtBridge
from core.constants import AppConst

class AlbumPanel(QWidget):
    """
    Left sidebar listing albums (directories) discovered by LibraryIndexer.
    Emits album_selected(folder_path) when an album is clicked.
    """
    album_selected = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedWidth(AppConst.SIDEBAR_WIDTH)
        self._bridge = MaterialQtBridge.get()
        self._album_paths: dict[str, int] = {}  # path → row index
        self._setup_ui()

    def _setup_ui(self):
        # Define the UI using PyMaterialKit
        tree = Column(
            component_id="album_panel_root",
            children=[
                Column(
                    component_id="album_panel_header_container",
                    children=[
                        Text(
                            component_id="album_panel_title",
                            props=TextProps(text="Albums")
                        )
                    ]
                ),
                Divider(
                    component_id="album_panel_divider",
                    props=DividerProps()
                ),
                ListComponent(
                    component_id="album_list",
                    children=[] # Items added dynamically
                )
            ]
        )

        root_widget = self._bridge.builder.build(tree, self)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        layout.addWidget(root_widget)

        # Apply specific height and layout to the header container
        header_container = self.findChild(QWidget, "album_panel_header_container")
        if header_container:
            header_container.setFixedHeight(AppConst.TOP_BAR_HEIGHT)
            h_layout = header_container.layout()
            if h_layout:
                h_layout.setContentsMargins(16, 0, 16, 0)
                h_layout.setAlignment(Qt.AlignVCenter | Qt.AlignLeft)

        # Style the title text correctly using tokens via the bridge if needed, 
        # but the builder handles it. We just need to adjust sizing/margins.
        
        # Extract the dynamically built QListWidget
        self.list_widget = self.findChild(QListWidget, "album_list")
        if self.list_widget:
            self.list_widget.itemClicked.connect(self._on_item_clicked)
            self.list_widget.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
            self.list_widget.setFocusPolicy(Qt.NoFocus)

    def add_album(self, folder_path: str, image_count: int, _preview: str = ""):
        """Add or update an album entry."""
        if not self.list_widget: return
        
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
        if self.list_widget:
            self.list_widget.clear()
        self._album_paths.clear()

    def select_first(self):
        if self.list_widget and self.list_widget.count() > 0:
            self.list_widget.setCurrentRow(0)
            item = self.list_widget.item(0)
            if item:
                self._on_item_clicked(item)

    def select_path(self, folder_path: str) -> bool:
        if not self.list_widget: return False
        row = self._album_paths.get(folder_path)
        if row is None:
            return False
        self.list_widget.setCurrentRow(row)
        item = self.list_widget.item(row)
        if item:
            self._on_item_clicked(item)
            return True
        return False
