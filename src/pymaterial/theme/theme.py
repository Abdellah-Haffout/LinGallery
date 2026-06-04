from dataclasses import dataclass, replace
from typing import Optional

from ..tokens.color import ColorScheme
from ..tokens.typography import TypeScale
from ..tokens.shape import ShapeScale
from ..tokens.motion import MotionSpec

@dataclass(frozen=True)
class Theme:
    color_scheme: ColorScheme
    type_scale: TypeScale
    shape_scale: ShapeScale
    motion: MotionSpec
    is_dark: bool

class ThemeOverride:
    def __init__(
        self,
        color_scheme: Optional[ColorScheme] = None,
        type_scale: Optional[TypeScale] = None,
        shape_scale: Optional[ShapeScale] = None,
        motion: Optional[MotionSpec] = None,
        is_dark: Optional[bool] = None,
    ):
        self.color_scheme = color_scheme
        self.type_scale = type_scale
        self.shape_scale = shape_scale
        self.motion = motion
        self.is_dark = is_dark

    def apply(self, base: Theme) -> Theme:
        updates = {}
        if self.color_scheme is not None:
            updates["color_scheme"] = self.color_scheme
        if self.type_scale is not None:
            updates["type_scale"] = self.type_scale
        if self.shape_scale is not None:
            updates["shape_scale"] = self.shape_scale
        if self.motion is not None:
            updates["motion"] = self.motion
        if self.is_dark is not None:
            updates["is_dark"] = self.is_dark
            
        if not updates:
            return base
        return replace(base, **updates)
