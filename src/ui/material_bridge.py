from PySide6.QtGui import QColor, QFont
from PySide6.QtWidgets import QFrame

from pymaterial.theme.dark_theme import DarkTheme
from pymaterial.tokens.typography import TextStyle
from pymaterial.core.context import RenderContext
from pymaterial.renderer.interface import RendererInterface
from pymaterial.tokens.elevation import ElevationLevel
from pymaterial.theme.theme import ThemeOverride
import dataclasses
from core.constants import DarkPalette

class DummyRenderer(RendererInterface):
    """Stub renderer required to construct a RenderContext,
    as we are bridging using standard Qt widgets, not painting."""
    def draw_rect(self, x: float, y: float, width: float, height: float, color: str) -> None: pass
    def draw_rounded_rect(self, x: float, y: float, width: float, height: float, radius: float, color: str) -> None: pass
    def draw_circle(self, cx: float, cy: float, radius: float, color: str) -> None: pass
    def draw_line(self, x1: float, y1: float, x2: float, y2: float, color: str, thickness: float) -> None: pass
    def draw_text(self, text: str, x: float, y: float, style: TextStyle, color: str) -> None: pass
    def measure_text(self, text: str, style: TextStyle) -> tuple[float, float]: return (0.0, 0.0)
    def draw_surface(self, x: float, y: float, width: float, height: float, radius: float, elevation: ElevationLevel, base_color: str) -> None: pass
    def save_state(self) -> None: pass
    def restore_state(self) -> None: pass

class MaterialQtBridge:
    def __init__(self):
        base_theme = DarkTheme.get()
        custom_color_scheme = dataclasses.replace(
            base_theme.color_scheme,
            primary=DarkPalette.PRIMARY,
            on_primary=DarkPalette.ON_PRIMARY,
            primary_container=DarkPalette.PRIMARY_CONTAINER,
            on_primary_container=DarkPalette.ON_PRIMARY_CONTAINER,
            secondary=DarkPalette.SECONDARY,
            on_secondary=DarkPalette.ON_SECONDARY,
            secondary_container=DarkPalette.SECONDARY_CONTAINER,
            on_secondary_container=DarkPalette.ON_SECONDARY_CONTAINER,
            error=DarkPalette.ERROR,
            on_error=DarkPalette.ON_ERROR,
            error_container=DarkPalette.ERROR_CONTAINER,
            on_error_container=DarkPalette.ON_ERROR_CONTAINER,
            background=DarkPalette.BACKGROUND,
            on_background=DarkPalette.ON_SURFACE,
            surface=DarkPalette.SURFACE,
            on_surface=DarkPalette.ON_SURFACE,
            surface_variant=DarkPalette.SURFACE_VARIANT,
            on_surface_variant=DarkPalette.ON_SURFACE_VARIANT,
            outline=DarkPalette.OUTLINE,
            outline_variant=DarkPalette.OUTLINE_VARIANT,
            shadow=DarkPalette.SHADOW,
            scrim=DarkPalette.SCRIM,
            surface_container=DarkPalette.SURFACE_CONTAINER,
            surface_container_high=DarkPalette.SURFACE_CONTAINER,
        )
        self.theme = ThemeOverride(color_scheme=custom_color_scheme).apply(base_theme)
        self.renderer = DummyRenderer()
        self.context = RenderContext(theme=self.theme, renderer=self.renderer)

    def to_qcolor(self, hex_str: str) -> QColor:
        return QColor(hex_str)

    def to_qfont(self, text_style: TextStyle) -> QFont:
        font = QFont(text_style.font_family, int(text_style.size_sp))
        weight_map = {
            100: QFont.Weight.Thin,
            200: QFont.Weight.ExtraLight,
            300: QFont.Weight.Light,
            400: QFont.Weight.Normal,
            500: QFont.Weight.Medium,
            600: QFont.Weight.DemiBold,
            700: QFont.Weight.Bold,
            800: QFont.Weight.ExtraBold,
            900: QFont.Weight.Black,
        }
        font.setWeight(weight_map.get(text_style.weight, QFont.Weight.Normal))
        
        if text_style.letter_spacing_sp != 0.0:
            font.setLetterSpacing(QFont.AbsoluteSpacing, text_style.letter_spacing_sp)
        return font

    def apply_dialog_style(self, frame: QFrame, resolved_tokens: dict):
        container_color = resolved_tokens.get("container_color", "#1E1E1E")
        shape = resolved_tokens.get("shape", None)
        radius = 8
        if shape and hasattr(shape, "top_start_dp"):
            radius = int(shape.top_start_dp)
            
        obj_name = frame.objectName()
        selector = f"QFrame#{obj_name}" if obj_name else "QFrame"

        child_bg = resolved_tokens.get("child_background", "transparent")

        frame.setStyleSheet(f"""
            {selector} {{
                background-color: {container_color};
                border: 1px solid #444444;
                border-radius: {radius}px;
            }}
            {selector} QLabel {{
                background-color: {child_bg};
                border: none;
            }}
        """)

