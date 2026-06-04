from enum import Enum, auto
from dataclasses import dataclass

class TypeRole(Enum):
    DISPLAY = auto()
    HEADLINE = auto()
    TITLE = auto()
    BODY = auto()
    LABEL = auto()

class TypeSize(Enum):
    LARGE = auto()
    MEDIUM = auto()
    SMALL = auto()

@dataclass(frozen=True)
class TextStyle:
    font_family: str
    size_sp: float
    weight: int
    line_height_sp: float
    letter_spacing_sp: float

@dataclass(frozen=True)
class TypeScale:
    display_large: TextStyle
    display_medium: TextStyle
    display_small: TextStyle
    headline_large: TextStyle
    headline_medium: TextStyle
    headline_small: TextStyle
    title_large: TextStyle
    title_medium: TextStyle
    title_small: TextStyle
    body_large: TextStyle
    body_medium: TextStyle
    body_small: TextStyle
    label_large: TextStyle
    label_medium: TextStyle
    label_small: TextStyle

class TypeScales:
    @staticmethod
    def baseline(font_family: str = "Roboto") -> TypeScale:
        return TypeScale(
            display_large=TextStyle(font_family, 57.0, 400, 64.0, 0.0),
            display_medium=TextStyle(font_family, 45.0, 400, 52.0, 0.0),
            display_small=TextStyle(font_family, 36.0, 400, 44.0, 0.0),
            headline_large=TextStyle(font_family, 32.0, 400, 40.0, 0.0),
            headline_medium=TextStyle(font_family, 28.0, 400, 36.0, 0.0),
            headline_small=TextStyle(font_family, 24.0, 400, 32.0, 0.0),
            title_large=TextStyle(font_family, 22.0, 400, 28.0, 0.0),
            title_medium=TextStyle(font_family, 16.0, 500, 24.0, 0.15),
            title_small=TextStyle(font_family, 14.0, 500, 20.0, 0.1),
            body_large=TextStyle(font_family, 16.0, 400, 24.0, 0.5),
            body_medium=TextStyle(font_family, 14.0, 400, 20.0, 0.25),
            body_small=TextStyle(font_family, 12.0, 400, 16.0, 0.4),
            label_large=TextStyle(font_family, 14.0, 500, 20.0, 0.1),
            label_medium=TextStyle(font_family, 12.0, 500, 16.0, 0.5),
            label_small=TextStyle(font_family, 11.0, 500, 16.0, 0.5),
        )
