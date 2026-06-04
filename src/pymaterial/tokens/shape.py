from enum import Enum, auto
from dataclasses import dataclass

class ShapeLevel(Enum):
    NONE = auto()
    EXTRA_SMALL = auto()
    SMALL = auto()
    MEDIUM = auto()
    LARGE = auto()
    EXTRA_LARGE = auto()
    FULL = auto()

@dataclass(frozen=True)
class CornerShape:
    top_start_dp: float
    top_end_dp: float
    bottom_start_dp: float
    bottom_end_dp: float

@dataclass(frozen=True)
class ShapeScale:
    none: CornerShape
    extra_small: CornerShape
    small: CornerShape
    medium: CornerShape
    large: CornerShape
    extra_large: CornerShape
    full: CornerShape

class ShapeScales:
    @staticmethod
    def baseline() -> ShapeScale:
        return ShapeScale(
            none=CornerShape(0.0, 0.0, 0.0, 0.0),
            extra_small=CornerShape(4.0, 4.0, 4.0, 4.0),
            small=CornerShape(8.0, 8.0, 8.0, 8.0),
            medium=CornerShape(12.0, 12.0, 12.0, 12.0),
            large=CornerShape(16.0, 16.0, 16.0, 16.0),
            extra_large=CornerShape(28.0, 28.0, 28.0, 28.0),
            full=CornerShape(9999.0, 9999.0, 9999.0, 9999.0),
        )
