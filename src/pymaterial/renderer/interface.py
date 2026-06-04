from abc import ABC, abstractmethod
from typing import Optional

from ..tokens.color import ColorScheme
from ..tokens.typography import TextStyle
from ..tokens.elevation import ElevationLevel

class RendererInterface(ABC):
    @abstractmethod
    def draw_rect(self, x: float, y: float, width: float, height: float, color: str) -> None:
        pass

    @abstractmethod
    def draw_rounded_rect(self, x: float, y: float, width: float, height: float, radius: float, color: str) -> None:
        pass

    @abstractmethod
    def draw_circle(self, cx: float, cy: float, radius: float, color: str) -> None:
        pass

    @abstractmethod
    def draw_line(self, x1: float, y1: float, x2: float, y2: float, color: str, thickness: float) -> None:
        pass

    @abstractmethod
    def draw_text(self, text: str, x: float, y: float, style: TextStyle, color: str) -> None:
        pass

    @abstractmethod
    def measure_text(self, text: str, style: TextStyle) -> tuple[float, float]:
        """Returns width and height of the text block."""
        pass

    @abstractmethod
    def draw_surface(
        self, 
        x: float, 
        y: float, 
        width: float, 
        height: float, 
        radius: float, 
        elevation: ElevationLevel, 
        base_color: str
    ) -> None:
        """
        Draws a material surface, handling both corner radii and elevation.
        The renderer is responsible for determining how elevation manifests
        (e.g. drop shadow or tonal color adjustment based on base_color).
        """
        pass

    @abstractmethod
    def save_state(self) -> None:
        pass

    @abstractmethod
    def restore_state(self) -> None:
        pass
