"""
LinGallery — Main Window.
Hosts the sidebar album panel + gallery grid + viewer.
Launches LibraryIndexer on startup for auto-scan.
"""
from __future__ import annotations

from PySide6.QtWidgets import (
    QMainWindow, QWidget, QHBoxLayout, QVBoxLayout,
    QStackedWidget, QFileDialog, QPushButton, QLabel,
    QStatusBar
)
from PySide6.QtCore import Qt, QSize
from pathlib import Path

from core.constants import AppConst
from logic.library_indexer import LibraryIndexer
from logic.image_manager import ImageManager
from ui.gallery.album_panel import AlbumPanel
from ui.gallery.gallery_view import GalleryView
from ui.viewer.image_viewer import ImageViewer

from pymaterial.components import Column, Row, Divider, DividerProps, TopAppBar, TopAppBarProps, Text, TextProps, StatusBar, StatusBarProps, InteractionState, ButtonProps, FilledButton
from ui.material_bridge import MaterialQtBridge
from ui.components.icon_button import IconButton

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(AppConst.APP_NAME)
        self.setMinimumSize(1200, 760)
        self.resize(1400, 900)

        self._bridge = MaterialQtBridge.get()
        self.setStyleSheet(f"background-color: {self._bridge.theme.color_scheme.background}; color: {self._bridge.theme.color_scheme.on_surface};")

        # Shared image manager (one pool for whole app)
        self._img_manager = ImageManager(self)
        self._indexer: LibraryIndexer | None = None
        self._scan_roots = list(AppConst.DEFAULT_SCAN_ROOTS)
        self._pending_album_select: str | None = None

        self._build_ui()
        self._start_scan()

    # ─────────────────────────────────────────────────────────────────
    # UI Construction
    # ─────────────────────────────────────────────────────────────────
    def _build_ui(self):
        # ── Gallery shell ─────────────────────────────────────────────
        self._gallery_shell = QWidget()
        gallery_lay = QHBoxLayout(self._gallery_shell)
        gallery_lay.setContentsMargins(0, 0, 0, 0)
        gallery_lay.setSpacing(0)

        # Left sidebar
        self._album_panel = AlbumPanel()
        self._album_panel.album_selected.connect(self._on_album_selected)

        # Divider line
        div_tree = Divider(component_id="main_div", props=DividerProps(vertical=True))
        div = self._bridge.builder.build(div_tree, self._gallery_shell)

        # Right: top-bar + gallery grid
        right = QWidget()
        right_lay = QVBoxLayout(right)
        right_lay.setContentsMargins(0, 0, 0, 0)
        right_lay.setSpacing(0)

        top_bar = self._build_gallery_topbar(right)
        right_lay.addWidget(top_bar)

        div2_tree = Divider(component_id="main_div_2")
        div2 = self._bridge.builder.build(div2_tree, right)
        right_lay.addWidget(div2)

        self._gallery_view = GalleryView(self._img_manager)
        self._gallery_view.image_selected.connect(self._open_viewer)
        right_lay.addWidget(self._gallery_view, stretch=1)

        gallery_lay.addWidget(self._album_panel)
        gallery_lay.addWidget(div)
        gallery_lay.addWidget(right, stretch=1)

        # ── Image Viewer ──────────────────────────────────────────────
        self._image_viewer = ImageViewer(self._img_manager)
        self._image_viewer.back_requested.connect(self._close_viewer)
        self._image_viewer.image_deleted.connect(self._on_image_deleted)
        self._image_viewer.image_changed.connect(self._on_image_changed)

        # ── Stack ─────────────────────────────────────────────────────
        self._stack = QStackedWidget()
        self._stack.addWidget(self._gallery_shell)   # index 0
        self._stack.addWidget(self._image_viewer)    # index 1
        self.setCentralWidget(self._stack)

        # ── Status Bar ────────────────────────────────────────────────
        sb_tree = StatusBar(component_id="main_status_bar", props=StatusBarProps(message="Scanning for images…"))
        self._status = self._bridge.builder.build(sb_tree, self)
        self.setStatusBar(self._status)

    def _build_gallery_topbar(self, parent: QWidget) -> QWidget:
        # Build using PyMaterialKit TopAppBar, injecting custom widgets.
        # TopAppBar takes title and actions.
        # But we need a dynamic folder label in the middle, and custom pyqt buttons.
        # We can construct it via Row.
        tree = Row(
            component_id="gallery_topbar",
            children=[
                Text(
                    component_id="app_logo",
                    props=TextProps(text=AppConst.APP_NAME)
                ),
                Text(
                    component_id="folder_path_label",
                    props=TextProps(text="")
                ),
                FilledButton(
                    component_id="add_source_btn",
                    props=ButtonProps(label="Add Source", on_click=self._add_source)
                )
            ]
        )
        bar = self._bridge.builder.build(tree, parent)
        bar.setFixedHeight(AppConst.TOP_BAR_HEIGHT)
        bar.setStyleSheet(f"background-color: {self._bridge.theme.color_scheme.surface};")
        
        # Override layout to match old style (margins, spacing, stretch)
        lay = bar.layout()
        lay.setContentsMargins(20, 0, 16, 0)
        lay.setSpacing(12)
        
        logo = bar.findChild(QLabel, "app_logo")
        if logo:
            logo.setStyleSheet(
                f"font-size: 20px; font-weight: 800; "
                f"color: {self._bridge.theme.color_scheme.primary}; letter-spacing: -0.5px;"
            )
            
        self._folder_path_label = bar.findChild(QLabel, "folder_path_label")
        if self._folder_path_label:
            self._folder_path_label.setStyleSheet(
                f"color: {self._bridge.theme.color_scheme.on_surface_variant}; font-size: 12px;"
            )
            # Add stretch before folder path or between folder path and buttons
            lay.insertSpacing(1, 16)
            lay.insertStretch(3)
            
        # Manually inject IconButton (hasn't been fully replaced in PyMaterialKit yet for standalone use without Icon component)
        # Actually, let's use the old IconButton for now, but style it via the theme.
        self._rescan_btn = IconButton("sort", tooltip="Rescan library")
        self._rescan_btn.clicked.connect(lambda: self._start_scan())
        lay.insertWidget(4, self._rescan_btn)
        
        return bar

    # ─────────────────────────────────────────────────────────────────
    # Library Scanning
    # ─────────────────────────────────────────────────────────────────
    def _start_scan(self, select_album: str | None = None):
        if self._indexer and self._indexer.isRunning():
            self._indexer.stop()

        self._album_panel.clear()
        self._status.showMessage("Scanning for images…")
        self._pending_album_select = select_album

        self._indexer = LibraryIndexer(roots=self._scan_roots, parent=self)
        self._indexer.album_found.connect(self._on_album_found)
        self._indexer.scan_progress.connect(self._on_scan_progress)
        self._indexer.scan_complete.connect(self._on_scan_complete)
        self._indexer.start()

    def _on_album_found(self, path: str, count: int, preview: str):
        self._album_panel.add_album(path, count, preview)

    def _on_scan_progress(self, dirs_scanned: int, total_images: int):
        self._status.showMessage(
            f"Scanning… {dirs_scanned} folders checked — {total_images} images found"
        )

    def _on_scan_complete(self, total_albums: int, total_images: int):
        self._status.showMessage(
            f"Library ready — {total_albums} albums, {total_images} images"
        )
        if self._pending_album_select and self._album_panel.select_path(self._pending_album_select):
            self._pending_album_select = None
            return
        self._pending_album_select = None
        self._album_panel.select_first()

    def _add_source(self):
        folder = QFileDialog.getExistingDirectory(
            self,
            "Add Image Source Folder",
            "",
            QFileDialog.Option.DontUseNativeDialog,
        )
        if folder:
            if folder not in self._scan_roots:
                self._scan_roots.append(folder)
            self._start_scan(select_album=folder)

    # ─────────────────────────────────────────────────────────────────
    # Gallery ↔ Viewer switching
    # ─────────────────────────────────────────────────────────────────
    def _on_album_selected(self, folder_path: str):
        self._gallery_view.load_folder(folder_path)
        if hasattr(self, "_folder_path_label") and self._folder_path_label:
            self._folder_path_label.setText(folder_path)

    def _open_viewer(self, path: str, image_list: list, index: int):
        self._image_viewer.load(image_list, index)
        self._stack.setCurrentWidget(self._image_viewer)
        self._image_viewer.setFocus()

    def _close_viewer(self):
        self._stack.setCurrentWidget(self._gallery_shell)

    def _on_image_deleted(self, path: str):
        self._img_manager.invalidate(path)
        self._gallery_view.remove_image(path)

    def _on_image_changed(self, old_path: str, new_path: str):
        # Invalidate cache entries for both paths
        if old_path:
            self._img_manager.invalidate(old_path)
        if new_path:
            self._img_manager.invalidate(new_path)
            parent_dir = str(Path(new_path).parent)
            if parent_dir not in self._scan_roots:
                self._scan_roots.append(parent_dir)

        current_album = self._folder_path_label.text() if hasattr(self, "_folder_path_label") and self._folder_path_label else ""

        # ── Case 1: in-place edit (rotate/flip/crop overwrite / rename same dir)
        # old_path == new_path  →  same file touched, just refresh the grid.
        if old_path and old_path == new_path:
            self._gallery_view.refresh_current_folder()
            self._status.showMessage(f"Updated {Path(new_path).name}")
            return

        # ── Case 2: rename within same album
        # old file gone, new file is in the same folder  →  refresh grid.
        if (old_path and new_path
                and str(Path(old_path).parent) == str(Path(new_path).parent)
                and str(Path(new_path).parent) == current_album):
            self._gallery_view.refresh_current_folder()
            self._status.showMessage(f"Renamed to {Path(new_path).name}")
            return

        # ── Case 3: new image added to the current album (e.g. crop → save as copy)
        # old_path is empty, new_path lands in the current album  →  inject directly.
        if not old_path and new_path and str(Path(new_path).parent) == current_album:
            self._gallery_view.add_image(new_path)
            self._status.showMessage(f"Added {Path(new_path).name}")
            return

        # ── Case 4: image deleted from current album (old_path set, new_path empty)
        # The viewer already calls gallery_view.remove_image() via image_deleted signal,
        # so nothing extra is needed here unless the album might now be empty.
        if old_path and not new_path:
            # remove_image was already handled by _on_image_deleted; just update status
            self._status.showMessage(f"Deleted {Path(old_path).name}")
            return

        # ── Case 5: cross-album move or genuinely new folder  →  full rescan.
        target_album = current_album
        if new_path and Path(new_path).parent.exists():
            target_album = str(Path(new_path).parent)
        self._start_scan(select_album=target_album)

    # ─────────────────────────────────────────────────────────────────
    # Window close
    # ─────────────────────────────────────────────────────────────────
    def closeEvent(self, event):
        if self._indexer and self._indexer.isRunning():
            self._indexer.stop()
        event.accept()
