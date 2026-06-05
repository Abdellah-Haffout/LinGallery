"""
LinGallery — Full Image Viewer.
Fully wired: zoom, pan, prev/next, keyboard nav, fullscreen, slideshow,
rotate, flip, crop, EXIF info panel, delete.
"""
from __future__ import annotations
from pathlib import Path
from typing import List, Optional
import os
import shutil

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout,
    QGraphicsView, QGraphicsScene, QGraphicsPixmapItem,
    QLabel, QFormLayout, QScrollArea,
    QFrame, QFileDialog, QLineEdit, QPushButton
)
from PySide6.QtCore import Qt, Signal, QTimer, QRectF, QPointF, QRect
from PySide6.QtGui import QPixmap, QWheelEvent, QPainter, QImage, QKeyEvent, QColor, QGuiApplication

from core.constants import AppConst
from ui.material_bridge import MaterialQtBridge
from logic.image_manager import ImageManager
from ui.components.icon_button import IconButton
from ui.viewer.slideshow_controller import SlideshowController
from ui.viewer.edit_toolbar import EditToolbar
from ui.components.material_dialog import MaterialDialog, MD3TextField, MD3Button
import processing.image_ops as image_ops


class ImageViewer(QWidget):
    """
    Full-featured image viewer.
    Receives image_list + index via load(); supports all viewer actions.
    """
    back_requested    = Signal()
    image_deleted     = Signal(str)   # path of deleted file
    image_changed     = Signal(str, str)  # old path, new path

    def __init__(self, image_manager: ImageManager | None = None, parent=None):
        super().__init__(parent)
        self._bridge = MaterialQtBridge.get()
        self._manager = image_manager
        self._image_list: List[str] = []
        self._current_index: int = 0
        self._current_path: str = ""
        self._pending_path: str = ""
        self._pending_crop_rect: tuple[int, int, int, int] | None = None
        self._is_fullscreen: bool = False
        # Compute the maximum pixel dimension this viewer will ever need.
        # Images are downscaled to this during decode, preventing multi-MB
        # full-resolution buffers from accumulating in cache and on the GPU.
        self._max_dimension = self._compute_max_dimension()
        self._slideshow = SlideshowController(self)
        self._slideshow.next_requested.connect(self.next_image)
        self._setup_ui()
        if self._manager:
            self._manager.full_image_ready.connect(self._on_full_image_ready)
            self._manager.load_error.connect(self._on_load_error)
        self.setFocusPolicy(Qt.StrongFocus)

    @staticmethod
    def _compute_max_dimension() -> int:
        """Return the largest pixel dimension the display could require.
        Uses the screen diagonal so images remain crisp even on high-DPI
        panels, while capping at 3840 to avoid decoding oversized buffers
        for ultra-wide or multi-monitor setups."""
        try:
            screen = QGuiApplication.primaryScreen()
            if screen:
                geo = screen.geometry()
                diag = max(geo.width(), geo.height())
                # Account for device pixel ratio (HiDPI)
                dpr = int(screen.devicePixelRatio() + 0.5)
                return min(max(diag * dpr, 1920), 3840)
        except Exception:
            pass
        return 1920  # safe default

    # ─────────────────────────────────────────────────────────────────
    # UI Setup
    # ─────────────────────────────────────────────────────────────────
    def _setup_ui(self):
        root_layout = QVBoxLayout(self)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)

        # ── Top Bar ───────────────────────────────────────────────────
        self._top_bar = QWidget()
        self._top_bar.setFixedHeight(AppConst.TOP_BAR_HEIGHT)
        self._top_bar.setStyleSheet(
            f"background-color: {self._bridge.theme.color_scheme.surface};"
            f"border-bottom: 1px solid {self._bridge.theme.color_scheme.outline_variant};"
        )
        top = QHBoxLayout(self._top_bar)
        top.setContentsMargins(8, 8, 8, 8)
        top.setSpacing(4)

        self._back_btn = IconButton("arrow_back", tooltip="Back to Gallery (Esc)")
        self._back_btn.clicked.connect(self._go_back)

        self._title_label = QLabel("")
        self._title_label.setStyleSheet(
            f"color: {self._bridge.theme.color_scheme.on_surface}; font-size: 14px; font-weight: 600;"
        )
        self._counter_label = QLabel("")
        self._counter_label.setStyleSheet(
            f"color: {self._bridge.theme.color_scheme.on_surface_variant}; font-size: 12px;"
        )

        self._zoom_out_btn    = IconButton("zoom_out", tooltip="Zoom Out (-)")
        self._zoom_in_btn     = IconButton("zoom_in",  tooltip="Zoom In (+)")
        self._fit_btn         = IconButton("fit_screen", tooltip="Fit to Screen (F2)")
        self._fullscreen_btn  = IconButton("fullscreen",  tooltip="Fullscreen (F)")
        self._slideshow_btn   = IconButton("play_arrow",  tooltip="Slideshow (Space)")

        self._zoom_out_btn.clicked.connect(self.zoom_out)
        self._zoom_in_btn.clicked.connect(self.zoom_in)
        self._fit_btn.clicked.connect(self.fit_to_screen)
        self._fullscreen_btn.clicked.connect(self.toggle_fullscreen)
        self._slideshow_btn.clicked.connect(self._toggle_slideshow)

        top.addWidget(self._back_btn)
        top.addSpacing(8)
        top.addWidget(self._title_label)
        top.addSpacing(8)
        top.addWidget(self._counter_label)
        top.addStretch()
        top.addWidget(self._zoom_out_btn)
        top.addWidget(self._zoom_in_btn)
        top.addWidget(self._fit_btn)
        top.addSpacing(8)
        top.addWidget(self._slideshow_btn)
        top.addWidget(self._fullscreen_btn)
        root_layout.addWidget(self._top_bar)

        # ── Central: nav buttons + graphics view ──────────────────────
        center = QWidget()
        center_lay = QHBoxLayout(center)
        center_lay.setContentsMargins(0, 0, 0, 0)
        center_lay.setSpacing(0)

        self._prev_btn = _NavButton("navigate_before", tooltip="Previous (←)")
        self._next_btn = _NavButton("navigate_next",   tooltip="Next (→)")
        self._prev_btn.clicked.connect(self.prev_image)
        self._next_btn.clicked.connect(self.next_image)

        self._view = _ZoomPanView()
        self._scene = QGraphicsScene(self._view)
        self._view.setScene(self._scene)
        self._pixmap_item = QGraphicsPixmapItem()
        # SmoothTransformation tells Qt to use high-quality bicubic/bilinear
        # resampling when the view transform scales the pixmap.  Without this
        # the item falls back to FastTransformation (nearest-neighbour) and the
        # image looks pixelated even though the view has SmoothPixmapTransform.
        self._pixmap_item.setTransformationMode(Qt.SmoothTransformation)
        self._scene.addItem(self._pixmap_item)

        center_lay.addWidget(self._prev_btn)
        center_lay.addWidget(self._view, stretch=1)
        self._info_panel = _InfoPanel()
        self._info_panel.hide()
        center_lay.addWidget(self._info_panel)
        center_lay.addWidget(self._next_btn)
        root_layout.addWidget(center, stretch=1)

        self._crop_bar = _CropActionBar()
        self._crop_bar.cancel_requested.connect(self._cancel_crop)
        self._crop_bar.apply_requested.connect(self._apply_crop)
        self._crop_bar.hide()
        root_layout.addWidget(self._crop_bar)

        # ── Bottom Edit Toolbar ───────────────────────────────────────
        self._edit_toolbar = EditToolbar()
        self._edit_toolbar.rotate_left_requested.connect(self._rotate_left)
        self._edit_toolbar.rotate_right_requested.connect(self._rotate_right)
        self._edit_toolbar.flip_h_requested.connect(self._flip_h)
        self._edit_toolbar.flip_v_requested.connect(lambda: self._flip_v())
        self._edit_toolbar.crop_requested.connect(self._start_crop)
        self._edit_toolbar.copy_requested.connect(self._copy_image)
        self._edit_toolbar.move_requested.connect(self._move_image)
        self._edit_toolbar.rename_requested.connect(self._rename_image)
        self._edit_toolbar.info_requested.connect(self._show_info)
        self._edit_toolbar.delete_requested.connect(self._delete_image)
        root_layout.addWidget(self._edit_toolbar)

        self._view.crop_rect_selected.connect(self._crop_current)

    # ─────────────────────────────────────────────────────────────────
    # Public API
    # ─────────────────────────────────────────────────────────────────
    def load(self, image_list: List[str], index: int = 0):
        """Load a list of images and display the one at index."""
        self._image_list = image_list
        self._current_index = max(0, min(index, len(image_list) - 1))
        self._display_current()

    def load_image(self, path: str):
        """Convenience: load a single image (no prev/next list)."""
        self.load([path], 0)

    # ─────────────────────────────────────────────────────────────────
    # Navigation
    # ─────────────────────────────────────────────────────────────────
    def next_image(self):
        if not self._image_list:
            return
        self._cleanup_old_cache()
        self._current_index = (self._current_index + 1) % len(self._image_list)
        self._display_current()

    def prev_image(self):
        if not self._image_list:
            return
        self._cleanup_old_cache()
        self._current_index = (self._current_index - 1) % len(self._image_list)
        self._display_current()

    def _display_current(self):
        if not self._image_list:
            return
        path = self._image_list[self._current_index]
        self._current_path = path
        self._pending_path = path
        self._pixmap_item.setPixmap(QPixmap())
        self._scene.setSceneRect(QRectF(0, 0, 1, 1))
        self._title_label.setText(Path(path).name)
        self._info_panel.set_image(path)
        total = len(self._image_list)
        self._counter_label.setText(f"{self._current_index + 1} / {total}")
        has_nav = total > 1
        self._prev_btn.setVisible(has_nav)
        self._next_btn.setVisible(has_nav)
        if self._manager:
            self._manager.request_full_image(path, self._max_dimension)
            self._preload_neighbors()
        else:
            self._set_pixmap(QPixmap(path))

    def _set_pixmap(self, pixmap: QPixmap):
        old = self._pixmap_item.pixmap()
        if not old.isNull():
            old.detach()
        self._pixmap_item.setPixmap(pixmap)
        if pixmap.isNull():
            self._scene.setSceneRect(QRectF(0, 0, 1, 1))
        else:
            self._scene.setSceneRect(QRectF(pixmap.rect()))
            self.fit_to_screen()

    def _on_full_image_ready(self, path: str, qimage: QImage):
        if path != self._pending_path:
            return
        self._set_pixmap(QPixmap.fromImage(qimage))

    def _on_load_error(self, path: str, message: str):
        if path != self._pending_path:
            return
        self._pixmap_item.setPixmap(QPixmap())
        d = MaterialDialog(self, "Unable to open image", f"{Path(path).name}\n\n{message}")
        d.add_action("OK", d.close_dialog, is_primary=True)
        d.open()

    def _preload_neighbors(self):
        if not self._manager or len(self._image_list) < 2:
            return
        for offset in range(1, AppConst.PRELOAD_AHEAD + 1):
            idx = (self._current_index + offset) % len(self._image_list)
            self._manager.request_full_image(
                self._image_list[idx], self._max_dimension
            )

    def _cleanup_old_cache(self):
        if not self._manager:
            return
        if len(self._image_list) < 4:
            return
        cutoff = max(0, self._current_index - AppConst.PRELOAD_AHEAD - 2)
        cleanup_paths = self._image_list[:cutoff]
        for path in cleanup_paths:
            self._manager._cache.remove(f"full:{path}")

    # ─────────────────────────────────────────────────────────────────
    # Zoom
    # ─────────────────────────────────────────────────────────────────
    def zoom_in(self):
        self._view.scale(AppConst.ZOOM_STEP, AppConst.ZOOM_STEP)

    def zoom_out(self):
        factor = 1 / AppConst.ZOOM_STEP
        self._view.scale(factor, factor)

    def fit_to_screen(self):
        self._view.resetTransform()
        self._view.fitInView(self._scene.sceneRect(), Qt.KeepAspectRatio)

    # ─────────────────────────────────────────────────────────────────
    # Fullscreen
    # ─────────────────────────────────────────────────────────────────
    def toggle_fullscreen(self):
        win = self.window()
        if win.isFullScreen():
            win.showNormal()
            self._fullscreen_btn.set_icon_name("fullscreen")
            self._is_fullscreen = False
        else:
            win.showFullScreen()
            self._fullscreen_btn.set_icon_name("fullscreen_exit")
            self._is_fullscreen = True

    # ─────────────────────────────────────────────────────────────────
    # Slideshow
    # ─────────────────────────────────────────────────────────────────
    def _toggle_slideshow(self):
        running = self._slideshow.toggle()
        self._slideshow_btn.set_icon_name("pause" if running else "play_arrow")

    # ─────────────────────────────────────────────────────────────────
    # Editing (all non-destructive preview via reload)
    # ─────────────────────────────────────────────────────────────────
    def _reload_current(self):
        """Force reload of the current image from disk after editing."""
        if self._current_path:
            if self._manager:
                self._manager.invalidate(self._current_path)
            self.image_changed.emit(self._current_path, self._current_path)
            self._display_current()

    def _rotate_left(self):
        if self._current_path and self._current_path.lower().endswith(
            tuple(AppConst.SUPPORTED_FORMATS_PILLOW)
        ):
            if image_ops.rotate(self._current_path, 90):
                self._reload_current()
            else:
                self._show_operation_failed("Rotate failed")

    def _rotate_right(self):
        if self._current_path and self._current_path.lower().endswith(
            tuple(AppConst.SUPPORTED_FORMATS_PILLOW)
        ):
            if image_ops.rotate(self._current_path, -90):
                self._reload_current()
            else:
                self._show_operation_failed("Rotate failed")

    def _flip_h(self):
        if self._current_path:
            if image_ops.flip_horizontal(self._current_path):
                self._reload_current()
            else:
                self._show_operation_failed("Flip failed")

    def _flip_v(self):
        if self._current_path:
            if image_ops.flip_vertical(self._current_path):
                self._reload_current()
            else:
                self._show_operation_failed("Flip failed")

    def _start_crop(self):
        if not self._current_path:
            return
        self._view.start_crop_mode()
        self._pending_crop_rect = None
        self._crop_bar.show_message("Drag to choose a crop area")
        self._crop_bar.show()

    def _crop_current(self, x: float, y: float, width: float, height: float):
        if not self._current_path:
            return
        if not self._current_path.lower().endswith(tuple(AppConst.SUPPORTED_FORMATS_PILLOW)):
            self._show_operation_failed("Crop is not supported for this format")
            return
        source_size = image_ops.get_oriented_image_dimensions(self._current_path)
        if not source_size:
            self._show_operation_failed("Could not read image dimensions")
            return
        crop_rect = self._map_display_crop_to_source(
            QRectF(float(x), float(y), float(width), float(height)),
            source_size,
        )
        if not crop_rect:
            self._show_operation_failed("Invalid crop selection")
            return
        self._pending_crop_rect = crop_rect
        self._crop_bar.show_message("Review crop selection")
        self._crop_bar.show()

    def _map_display_crop_to_source(
        self,
        display_rect: QRectF,
        source_size: tuple[int, int],
    ) -> tuple[int, int, int, int] | None:
        pixmap = self._pixmap_item.pixmap()
        scene_rect = self._scene.sceneRect()
        source_width, source_height = source_size
        if (
            pixmap.isNull()
            or source_width <= 0
            or source_height <= 0
            or scene_rect.width() <= 0
            or scene_rect.height() <= 0
        ):
            return None

        rect = display_rect.normalized().intersected(scene_rect)
        if not rect.isValid() or rect.width() <= 0 or rect.height() <= 0:
            return None

        return image_ops.map_display_crop_to_source(
            (
                rect.left() - scene_rect.left(),
                rect.top() - scene_rect.top(),
                rect.width(),
                rect.height(),
            ),
            (scene_rect.width(), scene_rect.height()),
            (source_width, source_height),
        )

    def _apply_crop(self):
        if not self._current_path or not self._pending_crop_rect:
            return
        d = MaterialDialog(self, "Save Crop", "How do you want to save the cropped image?")
        d.add_action("Cancel", d.close_dialog)
        d.add_action("Overwrite", lambda: [d.close_dialog(), self._execute_crop("overwrite")])
        d.add_action("Save as Copy", lambda: [d.close_dialog(), self._execute_crop("copy")], is_primary=True)
        d.open()

    def _execute_crop(self, choice: str):
        if not self._current_path or not self._pending_crop_rect:
            return
        x, y, width, height = self._pending_crop_rect
        
        if choice == "copy":
            dest_path = str(_unique_destination(Path(self._current_path)))
        else:
            dest_path = self._current_path
            
        if image_ops.crop(self._current_path, x, y, width, height, dest_path=dest_path):
            self._pending_crop_rect = None
            self._crop_bar.hide()
            self._view.cancel_crop_mode()
            if choice == "copy":
                self.image_changed.emit("", dest_path)
                self._image_list.insert(self._current_index + 1, dest_path)
                self.next_image()
            else:
                self._reload_current()
        else:
            self._show_operation_failed("Crop failed")

    def _cancel_crop(self):
        self._pending_crop_rect = None
        self._crop_bar.hide()
        self._view.cancel_crop_mode()

    def _show_info(self):
        if not self._current_path:
            return
        self._info_panel.setVisible(not self._info_panel.isVisible())
        self._info_panel.set_image(self._current_path)

    def _delete_image(self):
        if not self._current_path:
            return
        d = MaterialDialog(self, "Delete image", f"Permanently delete {Path(self._current_path).name}?")
        d.add_action("Cancel", d.close_dialog)
        d.add_action("Delete", lambda: [d.close_dialog(), self._perform_delete_current()], is_primary=True)
        d.open()

    def _perform_delete_current(self):
        path = self._current_path
        try:
            os.remove(path)
        except OSError as exc:
            self._show_operation_failed(f"Delete failed:\n{exc}")
            return
        self._image_list.pop(self._current_index)
        if self._image_list:
            self._current_index = min(self._current_index, len(self._image_list) - 1)
            self._display_current()
        else:
            self._go_back()
        self.image_deleted.emit(path)
        self.image_changed.emit(path, "")

    def _copy_image(self):
        if not self._current_path:
            return
        folder = QFileDialog.getExistingDirectory(
            self,
            "Copy Image to Folder",
            "",
            QFileDialog.Option.DontUseNativeDialog,
        )
        if not folder:
            return
        dest = _unique_destination(Path(folder) / Path(self._current_path).name)
        try:
            shutil.copy2(self._current_path, dest)
        except OSError as exc:
            self._show_operation_failed(f"Copy failed:\n{exc}")
            return
        self.image_changed.emit("", str(dest))

    def _move_image(self):
        if not self._current_path:
            return
        folder = QFileDialog.getExistingDirectory(
            self,
            "Move Image to Folder",
            "",
            QFileDialog.Option.DontUseNativeDialog,
        )
        if not folder:
            return
        old_path = self._current_path
        dest = _unique_destination(Path(folder) / Path(old_path).name)
        try:
            shutil.move(old_path, dest)
        except OSError as exc:
            self._show_operation_failed(f"Move failed:\n{exc}")
            return
        self._replace_current_path(str(dest))
        self.image_changed.emit(old_path, str(dest))

    def _rename_image(self):
        if not self._current_path:
            return
        old = Path(self._current_path)
        
        d = MaterialDialog(self, "Rename Image")
        tf = MD3TextField(d.bridge, "File Name", old.name)
        d.dialog_box.layout().insertWidget(1, tf)
        
        def _on_rename_submit():
            new_name = tf.text()
            d.close_dialog()
            if new_name:
                self._commit_rename(new_name)
                
        tf.returnPressed.connect(_on_rename_submit)
        d.add_action("Cancel", d.close_dialog)
        d.add_action("Rename", _on_rename_submit, is_primary=True)
        d.open()
        tf.selectAll()
        tf.setFocus()

    def _commit_rename(self, new_name: str):
        if not self._current_path:
            return
        old = Path(self._current_path)
        new_name = new_name.strip()
        if not new_name or new_name == old.name:
            return
        new_path = old.with_name(new_name)
        if new_path.suffix == "":
            new_path = new_path.with_suffix(old.suffix)
        if new_path.suffix.lower() not in AppConst.SUPPORTED_FORMATS:
            self._show_operation_failed("Rename must keep a supported image extension")
            return
        if new_path.exists():
            self._show_operation_failed("A file with that name already exists")
            return
        try:
            old.rename(new_path)
        except OSError as exc:
            self._show_operation_failed(f"Rename failed:\n{exc}")
            return
        self._replace_current_path(str(new_path))
        self.image_changed.emit(str(old), str(new_path))

    def _replace_current_path(self, new_path: str):
        old_path = self._current_path
        self._image_list[self._current_index] = new_path
        self._current_path = new_path
        self._pending_path = new_path
        if self._manager:
            self._manager.invalidate(old_path)
            self._manager.invalidate(new_path)
        self._display_current()

    def _show_operation_failed(self, message: str):
        d = MaterialDialog(self, "Image operation failed", message)
        d.add_action("OK", d.close_dialog, is_primary=True)
        d.open()

    def _go_back(self):
        # Clear full image cache when leaving viewer
        if self._manager:
            for path in self._image_list:
                self._manager._cache.remove(f"full:{path}")
        if self._slideshow.is_running:
            self._slideshow.stop()
        if self._is_fullscreen:
            self.window().showNormal()
            self._is_fullscreen = False
        self.back_requested.emit()

    # ─────────────────────────────────────────────────────────────────
    # Keyboard
    # ─────────────────────────────────────────────────────────────────
    def keyPressEvent(self, event: QKeyEvent):
        k = event.key()
        if k in (Qt.Key_Right, Qt.Key_D):
            self.next_image()
        elif k in (Qt.Key_Left, Qt.Key_A):
            self.prev_image()
        elif k == Qt.Key_Escape:
            if self._crop_bar.isVisible():
                self._cancel_crop()
            elif self._is_fullscreen:
                self.toggle_fullscreen()
            else:
                self._go_back()
        elif k == Qt.Key_F:
            self.toggle_fullscreen()
        elif k in (Qt.Key_Plus, Qt.Key_Equal):
            self.zoom_in()
        elif k == Qt.Key_Minus:
            self.zoom_out()
        elif k == Qt.Key_F2:
            self.fit_to_screen()
        elif k == Qt.Key_Space:
            self._toggle_slideshow()
        elif k == Qt.Key_L:
            self._rotate_left()
        elif k == Qt.Key_R:
            self._rotate_right()
        elif k == Qt.Key_I:
            self._show_info()
        elif k == Qt.Key_C:
            self._start_crop()
        elif k == Qt.Key_Delete:
            self._delete_image()
        else:
            super().keyPressEvent(event)

    def resizeEvent(self, event):
        super().resizeEvent(event)


class _CropActionBar(QWidget):
    apply_requested = Signal()
    cancel_requested = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self._bridge = MaterialQtBridge.get()
        self.setFixedHeight(56)
        self.setStyleSheet(
            f"background-color: {self._bridge.theme.color_scheme.surface};"
            f"border-top: 1px solid {self._bridge.theme.color_scheme.outline_variant};"
        )
        layout = QHBoxLayout(self)
        layout.setContentsMargins(16, 8, 16, 8)
        layout.setSpacing(10)

        self._message = QLabel("")
        self._message.setStyleSheet(
            f"color: {self._bridge.theme.color_scheme.on_surface}; font-size: 13px; font-weight: 600;"
        )
        layout.addWidget(self._message)
        layout.addStretch()
        
        bridge = MaterialQtBridge.get()
        cancel_btn = MD3Button(bridge, "Cancel", False, self)
        cancel_btn.clicked.connect(self.cancel_requested.emit)
        layout.addWidget(cancel_btn)
        
        apply_btn = MD3Button(bridge, "Apply", True, self)
        apply_btn.clicked.connect(self.apply_requested.emit)
        layout.addWidget(apply_btn)

    def show_message(self, text: str):
        self._message.setText(text)


class _InfoPanel(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self._bridge = MaterialQtBridge.get()
        self.setFixedWidth(320)
        self.setStyleSheet(
            f"background-color: {self._bridge.theme.color_scheme.surface};"
            f"border-left: 1px solid {self._bridge.theme.color_scheme.outline_variant};"
        )

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 18)
        layout.setSpacing(14)

        title = QLabel("Details")
        title.setStyleSheet(
            f"color: {self._bridge.theme.color_scheme.on_surface}; font-size: 18px; font-weight: 800;"
        )
        layout.addWidget(title)

        self._scroll = QScrollArea()
        self._scroll.setWidgetResizable(True)
        self._scroll.setFrameShape(QFrame.NoFrame)
        self._scroll.setStyleSheet("background: transparent; border: none;")
        self._content = QWidget()
        self._form = QFormLayout(self._content)
        self._form.setContentsMargins(0, 0, 0, 0)
        self._form.setSpacing(10)
        self._form.setLabelAlignment(Qt.AlignLeft)
        self._scroll.setWidget(self._content)
        layout.addWidget(self._scroll)

    def set_image(self, path: str):
        while self._form.rowCount():
            self._form.removeRow(0)

        exif = image_ops.read_exif(path)
        data = {"Filename": Path(path).name}
        data.update(exif)
        for key, value in data.items():
            self._add_row(str(key), str(value))

    def _add_row(self, key: str, value: str):
        key_label = QLabel(key)
        key_label.setStyleSheet(
            f"color: {self._bridge.theme.color_scheme.on_surface_variant}; font-size: 12px;"
        )
        value_label = QLabel(value)
        value_label.setWordWrap(True)
        value_label.setTextInteractionFlags(Qt.TextSelectableByMouse)
        value_label.setStyleSheet(
            f"color: {self._bridge.theme.color_scheme.on_surface}; font-size: 12px;"
        )
        self._form.addRow(key_label, value_label)



# ─────────────────────────────────────────────────────────────────────
# Custom Graphics View — Zoom + Pan + Crop overlay
# ─────────────────────────────────────────────────────────────────────
class _ZoomPanView(QGraphicsView):
    crop_rect_selected = Signal(float, float, float, float)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._bridge = MaterialQtBridge.get()
        self._crop_active = False
        self._crop_state = "idle"
        self._crop_origin = QPointF()
        self._crop_last_pos = QPointF()
        self._crop_handle = ""
        self._crop_rect_item = None
        self._crop_handle_items = []

        self.setDragMode(QGraphicsView.ScrollHandDrag)
        self.setTransformationAnchor(QGraphicsView.AnchorUnderMouse)
        self.setResizeAnchor(QGraphicsView.AnchorUnderMouse)
        self.setMouseTracking(True)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.setStyleSheet(
            f"background-color: {self._bridge.theme.color_scheme.background}; border: none;"
        )
        self.setFrameShape(QGraphicsView.NoFrame)
        self.setRenderHints(
            self.renderHints()
            | QPainter.SmoothPixmapTransform
            | QPainter.Antialiasing
            | QPainter.LosslessImageRendering
        )

    def wheelEvent(self, event: QWheelEvent):
        factor = AppConst.ZOOM_STEP if event.angleDelta().y() > 0 else 1 / AppConst.ZOOM_STEP
        self.scale(factor, factor)

    def start_crop_mode(self):
        self.cancel_crop_mode()
        self._crop_active = True
        self._crop_state = "idle"
        self.setDragMode(QGraphicsView.NoDrag)
        self.setCursor(Qt.CrossCursor)

    def cancel_crop_mode(self):
        self._crop_active = False
        self._crop_state = "idle"
        self._crop_handle = ""
        self.setDragMode(QGraphicsView.ScrollHandDrag)
        self.setCursor(Qt.ArrowCursor)
        if self._crop_rect_item:
            self.scene().removeItem(self._crop_rect_item)
            self._crop_rect_item = None
        self._clear_crop_handles()

    def mousePressEvent(self, event):
        if not self._crop_active or event.button() != Qt.LeftButton:
            super().mousePressEvent(event)
            return

        scene_pos = self._inside_scene_pos(event.pos())
        if scene_pos is None:
            return

        self._crop_last_pos = scene_pos
        rect = self._current_crop_rect()

        if self._crop_state == "confirmed" and rect.isValid():
            handle = self._hit_handle(scene_pos)
            if handle:
                self._crop_state = "resizing"
                self._crop_handle = handle
                return

            if rect.contains(scene_pos):
                self._crop_state = "moving"
                self._crop_handle = ""
                return

        # If we click outside the current crop rect, or there is no rect,
        # we restart the selection process
        self._crop_state = "selecting"
        self._crop_handle = ""
        self._crop_origin = scene_pos
        
        if self._crop_rect_item:
            self.scene().removeItem(self._crop_rect_item)
            self._crop_rect_item = None
        self._clear_crop_handles()

    def mouseMoveEvent(self, event):
        if not self._crop_active:
            super().mouseMoveEvent(event)
            return

        if self._crop_state in {"selecting", "moving", "resizing"}:
            scene_pos = self._clamped_scene_pos(event.pos())
        else:
            scene_pos = self._inside_scene_pos(event.pos())

        if scene_pos is None:
            self._update_crop_cursor(QPointF())
            return

        if self._crop_state == "selecting":
            rect = QRectF(self._crop_origin, scene_pos).normalized()
            if rect.width() >= 10 and rect.height() >= 10:
                self._set_crop_rect(rect)
        elif self._crop_state == "moving":
            delta = scene_pos - self._crop_last_pos
            self._move_crop_rect(delta)
            self._crop_last_pos = scene_pos
        elif self._crop_state == "resizing":
            self._resize_crop_rect(scene_pos)
            self._crop_last_pos = scene_pos
        else:
            self._update_crop_cursor(scene_pos)

    def mouseReleaseEvent(self, event):
        if not self._crop_active or event.button() != Qt.LeftButton:
            super().mouseReleaseEvent(event)
            return

        if self._crop_state == "idle":
            return

        rect = self._current_crop_rect()
        if not rect.isValid() or rect.width() < 12 or rect.height() < 12:
            if self._crop_rect_item:
                self.scene().removeItem(self._crop_rect_item)
                self._crop_rect_item = None
            self._clear_crop_handles()
            self._crop_state = "idle"
            self.setCursor(Qt.CrossCursor)
            return

        self._crop_state = "confirmed"
        self._emit_crop_rect(rect)
        self._update_crop_cursor(self._inside_scene_pos(event.pos()) or QPointF())

    def _crop_pen(self):
        from PySide6.QtGui import QPen
        p = QPen(QColor(self._bridge.theme.color_scheme.primary))
        p.setWidth(2)
        p.setStyle(Qt.SolidLine)
        return p

    def keyPressEvent(self, event: QKeyEvent):
        if self._crop_active and event.key() == Qt.Key_Escape:
            self.cancel_crop_mode()
        else:
            # Forward to parent ImageViewer for keyboard nav
            parent = self.parent()
            while parent and not hasattr(parent, "next_image"):
                parent = parent.parent()
            if parent:
                parent.keyPressEvent(event)
                return
            super().keyPressEvent(event)

    def _inside_scene_pos(self, viewport_pos) -> QPointF | None:
        pos = self.mapToScene(viewport_pos)
        bounds = self.sceneRect()
        if not bounds.isValid() or bounds.width() <= 1 or bounds.height() <= 1:
            return None
        if not bounds.adjusted(-1, -1, 1, 1).contains(pos):
            return None
        return self._clamped_scene_pos(viewport_pos)

    def _clamped_scene_pos(self, viewport_pos) -> QPointF | None:
        pos = self.mapToScene(viewport_pos)
        bounds = self.sceneRect()
        if not bounds.isValid() or bounds.width() <= 1 or bounds.height() <= 1:
            return None
        return QPointF(
            min(max(pos.x(), bounds.left()), bounds.right()),
            min(max(pos.y(), bounds.top()), bounds.bottom()),
        )

    def _current_crop_rect(self) -> QRectF:
        if not self._crop_rect_item:
            return QRectF()
        return self._crop_rect_item.rect()

    def _set_crop_rect(self, rect: QRectF):
        rect = self._clamp_crop_rect(rect.normalized())
        if self._crop_rect_item is None:
            self._crop_rect_item = self.scene().addRect(
                rect,
                pen=self._crop_pen(),
                brush=QColor(0, 0, 0, 0),
            )
            # Add subtle inner shade fill for Material feel
            brush = QColor(self._bridge.theme.color_scheme.primary)
            brush.setAlpha(35)
            self._crop_rect_item.setBrush(brush)
            self._crop_rect_item.setZValue(10)
        else:
            self._crop_rect_item.setRect(rect)
        self._sync_crop_handles(rect)

    def _clamp_crop_rect(self, rect: QRectF) -> QRectF:
        bounds = self.sceneRect()
        left = min(max(rect.left(), bounds.left()), bounds.right())
        top = min(max(rect.top(), bounds.top()), bounds.bottom())
        right = min(max(rect.right(), bounds.left()), bounds.right())
        bottom = min(max(rect.bottom(), bounds.top()), bounds.bottom())
        return QRectF(QPointF(left, top), QPointF(right, bottom)).normalized()

    def _scene_tolerance(self) -> float:
        p1 = self.mapToScene(0, 0)
        p2 = self.mapToScene(12, 0)
        return max(4.0, abs(p2.x() - p1.x()))

    def _hit_handle(self, pos: QPointF) -> str:
        rect = self._current_crop_rect()
        if not rect.isValid():
            return ""

        tol = self._scene_tolerance()
        near_left = abs(pos.x() - rect.left()) <= tol
        near_right = abs(pos.x() - rect.right()) <= tol
        near_top = abs(pos.y() - rect.top()) <= tol
        near_bottom = abs(pos.y() - rect.bottom()) <= tol
        within_x = rect.left() - tol <= pos.x() <= rect.right() + tol
        within_y = rect.top() - tol <= pos.y() <= rect.bottom() + tol

        if near_left and near_top:
            return "top_left"
        if near_right and near_top:
            return "top_right"
        if near_left and near_bottom:
            return "bottom_left"
        if near_right and near_bottom:
            return "bottom_right"
        if near_top and within_x:
            return "top"
        if near_bottom and within_x:
            return "bottom"
        if near_left and within_y:
            return "left"
        if near_right and within_y:
            return "right"
        return ""

    def _move_crop_rect(self, delta: QPointF):
        rect = self._current_crop_rect()
        if not rect.isValid():
            return
        moved = QRectF(rect)
        moved.translate(delta)
        bounds = self.sceneRect()
        if moved.left() < bounds.left():
            moved.moveLeft(bounds.left())
        if moved.right() > bounds.right():
            moved.moveRight(bounds.right())
        if moved.top() < bounds.top():
            moved.moveTop(bounds.top())
        if moved.bottom() > bounds.bottom():
            moved.moveBottom(bounds.bottom())
        self._set_crop_rect(moved)

    def _resize_crop_rect(self, pos: QPointF):
        rect = QRectF(self._current_crop_rect())
        if not rect.isValid():
            return

        min_size = 12
        if "left" in self._crop_handle:
            rect.setLeft(min(pos.x(), rect.right() - min_size))
        if "right" in self._crop_handle:
            rect.setRight(max(pos.x(), rect.left() + min_size))
        if "top" in self._crop_handle:
            rect.setTop(min(pos.y(), rect.bottom() - min_size))
        if "bottom" in self._crop_handle:
            rect.setBottom(max(pos.y(), rect.top() + min_size))

        self._set_crop_rect(rect)

    def _update_crop_cursor(self, pos: QPointF):
        if not self._crop_active:
            return
        
        if self._crop_state == "idle":
            self.setCursor(Qt.CrossCursor)
            return

        handle = self._hit_handle(pos)
        rect = self._current_crop_rect()
        
        if handle in {"top_left", "bottom_right"}:
            self.setCursor(Qt.SizeFDiagCursor)
        elif handle in {"top_right", "bottom_left"}:
            self.setCursor(Qt.SizeBDiagCursor)
        elif handle in {"left", "right"}:
            self.setCursor(Qt.SizeHorCursor)
        elif handle in {"top", "bottom"}:
            self.setCursor(Qt.SizeVerCursor)
        elif rect.isValid() and rect.contains(pos):
            self.setCursor(Qt.SizeAllCursor)
        else:
            self.setCursor(Qt.CrossCursor)

    def _emit_crop_rect(self, rect: QRectF):
        rect = rect.normalized()
        self.crop_rect_selected.emit(
            rect.left(),
            rect.top(),
            rect.width(),
            rect.height(),
        )

    def _clear_crop_handles(self):
        for item in self._crop_handle_items:
            self.scene().removeItem(item)
        self._crop_handle_items = []

    def _sync_crop_handles(self, rect: QRectF):
        self._clear_crop_handles()
        if not rect.isValid() or rect.width() < 12 or rect.height() < 12:
            return

        size = self._scene_handle_size()
        half = size / 2
        points = [
            QPointF(rect.left(), rect.top()),
            QPointF(rect.center().x(), rect.top()),
            QPointF(rect.right(), rect.top()),
            QPointF(rect.left(), rect.center().y()),
            QPointF(rect.right(), rect.center().y()),
            QPointF(rect.left(), rect.bottom()),
            QPointF(rect.center().x(), rect.bottom()),
            QPointF(rect.right(), rect.bottom()),
        ]
        from PySide6.QtGui import QBrush, QPen
        pen = QPen(QColor(self._bridge.theme.color_scheme.on_primary_container), max(1, int(size / 8)))
        brush = QBrush(QColor(self._bridge.theme.color_scheme.primary))
        for point in points:
            handle = self.scene().addRect(
                point.x() - half,
                point.y() - half,
                size,
                size,
                pen,
                brush,
            )
            handle.setZValue(11)
            self._crop_handle_items.append(handle)

    def _scene_handle_size(self) -> float:
        p1 = self.mapToScene(0, 0)
        p2 = self.mapToScene(14, 0)
        return max(6.0, abs(p2.x() - p1.x()))


def _unique_destination(path: Path) -> Path:
    if not path.exists():
        return path
    stem = path.stem
    suffix = path.suffix
    parent = path.parent
    counter = 1
    while True:
        candidate = parent / f"{stem} ({counter}){suffix}"
        if not candidate.exists():
            return candidate
        counter += 1


# ─────────────────────────────────────────────────────────────────────
# Navigation side buttons
# ─────────────────────────────────────────────────────────────────────
class _NavButton(QWidget):
    clicked = Signal()

    def __init__(self, icon_name: str, tooltip: str = "", parent=None):
        super().__init__(parent)
        self._bridge = MaterialQtBridge.get()
        self.setFixedWidth(48)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(4, 0, 4, 0)
        btn = IconButton(icon_name, size=40, tooltip=tooltip)
        btn.clicked.connect(self.clicked)
        lay.addStretch()
        lay.addWidget(btn)
        lay.addStretch()
        self.setStyleSheet(
            f"background-color: {self._bridge.theme.color_scheme.background};"
        )
