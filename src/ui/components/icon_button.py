"""
LinGallery — Material Design 3 Icon Button.
Renders inline SVG via QSvgRenderer onto a QPixmap for pixel-perfect icon display.
"""
from __future__ import annotations

from PySide6.QtWidgets import QPushButton
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtGui import QIcon, QPixmap, QPainter, QColor
from PySide6.QtCore import Qt, QSize, QByteArray, QRectF

from core.icons import get_icon_svg
from core.constants import DarkPalette, AppConst


def _render_svg_to_pixmap(svg_data: str, color: str, icon_px: int) -> QPixmap:
    """Render an SVG string into a QPixmap using QSvgRenderer."""
    renderer = QSvgRenderer()
    renderer.load(QByteArray(svg_data.encode("utf-8")))

    pixmap = QPixmap(icon_px, icon_px)
    pixmap.fill(Qt.transparent)

    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.SmoothPixmapTransform)
    renderer.render(painter, QRectF(0, 0, icon_px, icon_px))
    painter.end()

    return pixmap


class IconButton(QPushButton):
    """
    A circular Material Design 3 icon button backed by a Material SVG icon.
    """
    def __init__(
        self,
        icon_name: str,
        parent=None,
        size: int = 40,
        icon_size: int = None,
        color: str = None,
        tooltip: str = "",
    ):
        super().__init__(parent)
        self._icon_name = icon_name
        self._btn_size = size
        self._icon_size = icon_size or AppConst.ICON_SIZE
        self._color = color or DarkPalette.ON_SURFACE

        self.setFixedSize(size, size)
        self.setCursor(Qt.PointingHandCursor)
        if tooltip:
            self.setToolTip(tooltip)

        self._apply_icon()
        self._apply_style()

    def _apply_icon(self):
        svg = get_icon_svg(self._icon_name, color=self._color, size=self._icon_size)
        if svg:
            pixmap = _render_svg_to_pixmap(svg, self._color, self._icon_size)
            self.setIcon(QIcon(pixmap))
            self.setIconSize(QSize(self._icon_size, self._icon_size))

    def _apply_style(self):
        r = self._btn_size // 2
        self.setStyleSheet(f"""
            QPushButton {{
                background-color: transparent;
                border-radius: {r}px;
                border: none;
            }}
            QPushButton:hover {{
                background-color: {DarkPalette.SURFACE_VARIANT};
            }}
            QPushButton:pressed {{
                background-color: {DarkPalette.PRIMARY_CONTAINER};
            }}
        """)

    def set_icon_name(self, icon_name: str):
        self._icon_name = icon_name
        self._apply_icon()

    def set_color(self, color: str):
        self._color = color
        self._apply_icon()
