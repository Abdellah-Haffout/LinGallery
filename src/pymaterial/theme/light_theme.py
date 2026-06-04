from .theme import Theme
from ..tokens.color import ColorSchemes
from ..tokens.typography import TypeScales
from ..tokens.shape import ShapeScales
from ..tokens.motion import MotionSpecs

class LightTheme:
    @staticmethod
    def get() -> Theme:
        return Theme(
            color_scheme=ColorSchemes.light_baseline(),
            type_scale=TypeScales.baseline(),
            shape_scale=ShapeScales.baseline(),
            motion=MotionSpecs.baseline(),
            is_dark=False,
        )
