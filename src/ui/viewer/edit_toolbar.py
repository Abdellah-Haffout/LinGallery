"""
LinGallery — Edit Toolbar (bottom bar of the viewer).
Rotate, Flip, Crop — all actions call processing.image_ops and reload.
"""
from __future__ import annotations

from PySide6.QtWidgets import QWidget, QHBoxLayout
from PySide6.QtCore import Signal

from core.constants import DarkPalette, AppConst
from ui.components.icon_button import IconButton


class EditToolbar(QWidget):
    """
    Bottom toolbar with non-destructive editing actions.
    Emits action signals; the viewer applies them via image_ops.
    """
    rotate_left_requested  = Signal()
    rotate_right_requested = Signal()
    flip_h_requested       = Signal()
    flip_v_requested       = Signal()
    crop_requested         = Signal()
    copy_requested         = Signal()
    move_requested         = Signal()
    rename_requested       = Signal()
    delete_requested       = Signal()
    info_requested         = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(AppConst.BOTTOM_BAR_HEIGHT)
        self._setup_ui()

    def _setup_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(16, 8, 16, 8)
        layout.setSpacing(8)

        self.setStyleSheet(
            f"background-color: {DarkPalette.SURFACE};"
            f"border-top: 1px solid {DarkPalette.DIVIDER};"
        )

        def btn(icon, tip, signal):
            b = IconButton(icon, tooltip=tip)
            b.clicked.connect(signal)
            return b

        layout.addStretch()
        layout.addWidget(btn("rotate_left",  "Rotate Left (L)",  self.rotate_left_requested))
        layout.addWidget(btn("rotate_right", "Rotate Right (R)", self.rotate_right_requested))
        layout.addWidget(btn("flip",         "Flip Horizontal",  self.flip_h_requested))
        layout.addSpacing(16)
        layout.addWidget(btn("crop",         "Crop (C)",         self.crop_requested))
        layout.addSpacing(24)
        layout.addWidget(btn("content_copy", "Copy to Folder",   self.copy_requested))
        layout.addWidget(btn("drive_file_move", "Move to Folder", self.move_requested))
        layout.addWidget(btn("edit",         "Rename",           self.rename_requested))
        layout.addSpacing(24)
        layout.addWidget(btn("info",         "Image Info (I)",   self.info_requested))
        layout.addWidget(btn("delete",       "Delete (Del)",     self.delete_requested))
        layout.addStretch()
