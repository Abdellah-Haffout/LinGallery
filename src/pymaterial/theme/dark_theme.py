from .theme import Theme
from ..tokens.color import ColorSchemes
from ..tokens.typography import TypeScales
from ..tokens.shape import ShapeScales
from ..tokens.motion import MotionSpecs

class DarkTheme:
    @staticmethod
    def get() -> Theme:
        return Theme(
            color_scheme=ColorSchemes.dark_baseline(),
            type_scale=TypeScales.baseline(),
            shape_scale=ShapeScales.baseline(),
            motion=MotionSpecs.baseline(),
            is_dark=True,
        )
