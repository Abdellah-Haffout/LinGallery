"""
LinGallery — Full Image Viewer.
Fully wired: zoom, pan, prev/next, keyboard nav, fullscreen, slideshow,
rotate, flip, crop, EXIF info panel, delete.
"""
from __future__ import annotations
from pathlib import Path
from typing import List, Optional
import os

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout,
    QGraphicsView, QGraphicsScene, QGraphicsPixmapItem,
    QLabel, QMessageBox, QDialog, QFormLayout, QScrollArea,
    QFrame, QFileDialog
)
from PySide6.QtCore import Qt, Signal, QTimer, QRectF, QPointF
from PySide6.QtGui import QPixmap, QWheelEvent, QPainter, QImage, QKeyEvent, QColor

from core.constants import AppConst, DarkPalette
from ui.components.icon_button import IconButton
from ui.viewer.slideshow_controller import SlideshowController
from ui.viewer.edit_toolbar import EditToolbar
import processing.image_ops as image_ops


class ImageViewer(QWidget):
    """
    Full-featured image viewer.
    Receives image_list + index via load(); supports all viewer actions.
    """
    back_requested    = Signal()
    image_deleted     = Signal(str)   # path of deleted file

    def __init__(self, parent=None):
        super().__init__(parent)
        self._image_list: List[str] = []
        self._current_index: int = 0
        self._current_path: str = ""
        self._is_fullscreen: bool = False
        self._slideshow = SlideshowController(self)
        self._slideshow.next_requested.connect(self.next_image)
        self._setup_ui()
        self.setFocusPolicy(Qt.StrongFocus)

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
            f"background-color: {DarkPalette.SURFACE};"
            f"border-bottom: 1px solid {DarkPalette.DIVIDER};"
        )
        top = QHBoxLayout(self._top_bar)
        top.setContentsMargins(8, 8, 8, 8)
        top.setSpacing(4)

        self._back_btn = IconButton("arrow_back", tooltip="Back to Gallery (Esc)")
        self._back_btn.clicked.connect(self._go_back)

        self._title_label = QLabel("")
        self._title_label.setStyleSheet(
            f"color: {DarkPalette.ON_SURFACE}; font-size: 14px; font-weight: 600;"
        )
        self._counter_label = QLabel("")
        self._counter_label.setStyleSheet(
            f"color: {DarkPalette.ON_SURFACE_VARIANT}; font-size: 12px;"
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
        self._scene.addItem(self._pixmap_item)

        center_lay.addWidget(self._prev_btn)
        center_lay.addWidget(self._view, stretch=1)
        center_lay.addWidget(self._next_btn)
        root_layout.addWidget(center, stretch=1)

        # ── Bottom Edit Toolbar ───────────────────────────────────────
        self._edit_toolbar = EditToolbar()
        self._edit_toolbar.rotate_left_requested.connect(self._rotate_left)
        self._edit_toolbar.rotate_right_requested.connect(self._rotate_right)
        self._edit_toolbar.flip_h_requested.connect(self._flip_h)
        self._edit_toolbar.flip_v_requested.connect(lambda: self._flip_v())
        self._edit_toolbar.crop_requested.connect(self._start_crop)
        self._edit_toolbar.info_requested.connect(self._show_info)
        self._edit_toolbar.delete_requested.connect(self._delete_image)
        root_layout.addWidget(self._edit_toolbar)

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
        self._current_index = (self._current_index + 1) % len(self._image_list)
        self._display_current()

    def prev_image(self):
        if not self._image_list:
            return
        self._current_index = (self._current_index - 1) % len(self._image_list)
        self._display_current()

    def _display_current(self):
        if not self._image_list:
            return
        path = self._image_list[self._current_index]
        self._current_path = path
        pixmap = QPixmap(path)
        self._pixmap_item.setPixmap(pixmap)
        self._scene.setSceneRect(QRectF(pixmap.rect()))
        self.fit_to_screen()
        self._title_label.setText(Path(path).name)
        total = len(self._image_list)
        self._counter_label.setText(f"{self._current_index + 1} / {total}")
        # Show/hide nav buttons
        has_nav = total > 1
        self._prev_btn.setVisible(has_nav)
        self._next_btn.setVisible(has_nav)

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
            pixmap = QPixmap(self._current_path)
            self._pixmap_item.setPixmap(pixmap)
            self._scene.setSceneRect(QRectF(pixmap.rect()))
            self.fit_to_screen()

    def _rotate_left(self):
        if self._current_path and self._current_path.lower().endswith(
            tuple(AppConst.SUPPORTED_FORMATS_PILLOW)
        ):
            image_ops.rotate(self._current_path, 90)
            self._reload_current()

    def _rotate_right(self):
        if self._current_path and self._current_path.lower().endswith(
            tuple(AppConst.SUPPORTED_FORMATS_PILLOW)
        ):
            image_ops.rotate(self._current_path, -90)
            self._reload_current()

    def _flip_h(self):
        if self._current_path:
            image_ops.flip_horizontal(self._current_path)
            self._reload_current()

    def _flip_v(self):
        if self._current_path:
            image_ops.flip_vertical(self._current_path)
            self._reload_current()

    def _start_crop(self):
        self._view.start_crop_mode()

    def _show_info(self):
        if not self._current_path:
            return
        exif = image_ops.read_exif(self._current_path)
        dlg = _ExifDialog(self._current_path, exif, self)
        dlg.exec()

    def _delete_image(self):
        if not self._current_path:
            return
        reply = QMessageBox.question(
            self, "Delete Image",
            f"Permanently delete:\n{Path(self._current_path).name}?",
            QMessageBox.Yes | QMessageBox.No, QMessageBox.No
        )
        if reply == QMessageBox.Yes:
            path = self._current_path
            self._image_list.pop(self._current_index)
            if self._image_list:
                self._current_index = min(self._current_index, len(self._image_list) - 1)
                self._display_current()
            else:
                self._go_back()
            try:
                os.remove(path)
            except Exception:
                pass
            self.image_deleted.emit(path)

    def _go_back(self):
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
            if self._is_fullscreen:
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
        elif k == Qt.Key_Delete:
            self._delete_image()
        else:
            super().keyPressEvent(event)


# ─────────────────────────────────────────────────────────────────────
# Custom Graphics View — Zoom + Pan + Crop overlay
# ─────────────────────────────────────────────────────────────────────
class _ZoomPanView(QGraphicsView):
    crop_rect_selected = Signal(int, int, int, int)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._crop_mode = False
        self._crop_origin = QPointF()
        self._crop_rect_item = None

        self.setDragMode(QGraphicsView.ScrollHandDrag)
        self.setTransformationAnchor(QGraphicsView.AnchorUnderMouse)
        self.setResizeAnchor(QGraphicsView.AnchorUnderMouse)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.setStyleSheet(
            f"background-color: {DarkPalette.BACKGROUND}; border: none;"
        )
        self.setFrameShape(QGraphicsView.NoFrame)
        self.setRenderHints(
            self.renderHints() |
            QPainter.SmoothPixmapTransform |
            QPainter.Antialiasing
        )

    def wheelEvent(self, event: QWheelEvent):
        factor = AppConst.ZOOM_STEP if event.angleDelta().y() > 0 else 1 / AppConst.ZOOM_STEP
        self.scale(factor, factor)

    def start_crop_mode(self):
        self._crop_mode = True
        self.setDragMode(QGraphicsView.NoDrag)
        self.setCursor(Qt.CrossCursor)

    def _end_crop_mode(self):
        self._crop_mode = False
        self.setDragMode(QGraphicsView.ScrollHandDrag)
        self.setCursor(Qt.ArrowCursor)
        if self._crop_rect_item:
            self.scene().removeItem(self._crop_rect_item)
            self._crop_rect_item = None

    def mousePressEvent(self, event):
        if self._crop_mode and event.button() == Qt.LeftButton:
            self._crop_origin = self.mapToScene(event.pos())
        else:
            super().mousePressEvent(event)

    def mouseMoveEvent(self, event):
        if self._crop_mode:
            current = self.mapToScene(event.pos())
            rect = QRectF(self._crop_origin, current).normalized()
            if self._crop_rect_item:
                self.scene().removeItem(self._crop_rect_item)
            self._crop_rect_item = self.scene().addRect(
                rect,
                pen=self._crop_pen(),
                brush=QColor(79, 195, 195, 40)
            )
        else:
            super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event):
        if self._crop_mode and event.button() == Qt.LeftButton:
            current = self.mapToScene(event.pos())
            rect = QRectF(self._crop_origin, current).normalized()
            x, y, w, h = int(rect.x()), int(rect.y()), int(rect.width()), int(rect.height())
            self._end_crop_mode()
            if w > 10 and h > 10:
                self.crop_rect_selected.emit(x, y, w, h)
        else:
            super().mouseReleaseEvent(event)

    def _crop_pen(self):
        from PySide6.QtGui import QPen
        p = QPen(QColor(DarkPalette.PRIMARY))
        p.setWidth(2)
        p.setStyle(Qt.DashLine)
        return p

    def keyPressEvent(self, event: QKeyEvent):
        if self._crop_mode and event.key() == Qt.Key_Escape:
            self._end_crop_mode()
        else:
            # Forward to parent ImageViewer for keyboard nav
            if self.parent():
                self.parent().keyPressEvent(event)
            else:
                super().keyPressEvent(event)


# ─────────────────────────────────────────────────────────────────────
# Navigation side buttons
# ─────────────────────────────────────────────────────────────────────
class _NavButton(QWidget):
    clicked = Signal()

    def __init__(self, icon_name: str, tooltip: str = "", parent=None):
        super().__init__(parent)
        self.setFixedWidth(48)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(4, 0, 4, 0)
        btn = IconButton(icon_name, size=40, tooltip=tooltip)
        btn.clicked.connect(self.clicked)
        lay.addStretch()
        lay.addWidget(btn)
        lay.addStretch()
        self.setStyleSheet(
            f"background-color: {DarkPalette.BACKGROUND};"
        )


# ─────────────────────────────────────────────────────────────────────
# EXIF Info Dialog
# ─────────────────────────────────────────────────────────────────────
class _ExifDialog(QDialog):
    def __init__(self, path: str, exif: dict, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Image Information")
        self.setMinimumWidth(400)
        self.setStyleSheet(
            f"background-color: {DarkPalette.SURFACE};"
            f"color: {DarkPalette.ON_SURFACE};"
        )

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(16)

        title = QLabel(Path(path).name)
        title.setStyleSheet("font-size: 14px; font-weight: 700;")
        layout.addWidget(title)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.NoFrame)
        scroll.setStyleSheet("background: transparent;")

        inner = QWidget()
        form = QFormLayout(inner)
        form.setLabelAlignment(Qt.AlignRight)
        form.setSpacing(8)

        lbl_style = f"color: {DarkPalette.ON_SURFACE_VARIANT}; font-size: 12px;"
        val_style = f"color: {DarkPalette.ON_SURFACE}; font-size: 12px;"

        for key, val in exif.items():
            lbl = QLabel(key + ":")
            lbl.setStyleSheet(lbl_style)
            v = QLabel(val)
            v.setStyleSheet(val_style)
            v.setWordWrap(True)
            form.addRow(lbl, v)

        if not exif:
            empty = QLabel("No metadata available.")
            empty.setStyleSheet(lbl_style)
            layout.addWidget(empty)

        scroll.setWidget(inner)
        layout.addWidget(scroll)
