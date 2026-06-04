from enum import Enum, auto
from dataclasses import dataclass

class ColorRole(Enum):
    PRIMARY = auto()
    ON_PRIMARY = auto()
    PRIMARY_CONTAINER = auto()
    ON_PRIMARY_CONTAINER = auto()
    SECONDARY = auto()
    ON_SECONDARY = auto()
    SECONDARY_CONTAINER = auto()
    ON_SECONDARY_CONTAINER = auto()
    TERTIARY = auto()
    ON_TERTIARY = auto()
    TERTIARY_CONTAINER = auto()
    ON_TERTIARY_CONTAINER = auto()
    ERROR = auto()
    ON_ERROR = auto()
    ERROR_CONTAINER = auto()
    ON_ERROR_CONTAINER = auto()
    BACKGROUND = auto()
    ON_BACKGROUND = auto()
    SURFACE = auto()
    ON_SURFACE = auto()
    SURFACE_VARIANT = auto()
    ON_SURFACE_VARIANT = auto()
    OUTLINE = auto()
    OUTLINE_VARIANT = auto()
    SHADOW = auto()
    SCRIM = auto()
    INVERSE_SURFACE = auto()
    INVERSE_ON_SURFACE = auto()
    INVERSE_PRIMARY = auto()
    SURFACE_DIM = auto()
    SURFACE_BRIGHT = auto()
    SURFACE_CONTAINER_LOWEST = auto()
    SURFACE_CONTAINER_LOW = auto()
    SURFACE_CONTAINER = auto()
    SURFACE_CONTAINER_HIGH = auto()
    SURFACE_CONTAINER_HIGHEST = auto()

@dataclass(frozen=True)
class ColorScheme:
    primary: str
    on_primary: str
    primary_container: str
    on_primary_container: str
    secondary: str
    on_secondary: str
    secondary_container: str
    on_secondary_container: str
    tertiary: str
    on_tertiary: str
    tertiary_container: str
    on_tertiary_container: str
    error: str
    on_error: str
    error_container: str
    on_error_container: str
    background: str
    on_background: str
    surface: str
    on_surface: str
    surface_variant: str
    on_surface_variant: str
    outline: str
    outline_variant: str
    shadow: str
    scrim: str
    inverse_surface: str
    inverse_on_surface: str
    inverse_primary: str
    surface_dim: str
    surface_bright: str
    surface_container_lowest: str
    surface_container_low: str
    surface_container: str
    surface_container_high: str
    surface_container_highest: str

class ColorSchemes:
    @staticmethod
    def light_baseline() -> ColorScheme:
        return ColorScheme(
            primary="#6750A4",
            on_primary="#FFFFFF",
            primary_container="#EADDFF",
            on_primary_container="#21005D",
            secondary="#625B71",
            on_secondary="#FFFFFF",
            secondary_container="#E8DEF8",
            on_secondary_container="#1D192B",
            tertiary="#7D5260",
            on_tertiary="#FFFFFF",
            tertiary_container="#FFD8E4",
            on_tertiary_container="#31111D",
            error="#B3261E",
            on_error="#FFFFFF",
            error_container="#F9DEDC",
            on_error_container="#410E0B",
            background="#FFFBFE",
            on_background="#1C1B1F",
            surface="#FFFBFE",
            on_surface="#1C1B1F",
            surface_variant="#E7E0EC",
            on_surface_variant="#49454F",
            outline="#79747E",
            outline_variant="#CAC4D0",
            shadow="#000000",
            scrim="#000000",
            inverse_surface="#313033",
            inverse_on_surface="#F4EFF4",
            inverse_primary="#D0BCFF",
            surface_dim="#DED8E1",
            surface_bright="#FEF7FF",
            surface_container_lowest="#FFFFFF",
            surface_container_low="#F7F2FA",
            surface_container="#F3EDF7",
            surface_container_high="#ECE6F0",
            surface_container_highest="#E6E0E9",
        )

    @staticmethod
    def dark_baseline() -> ColorScheme:
        return ColorScheme(
            primary="#D0BCFF",
            on_primary="#381E72",
            primary_container="#4F378B",
            on_primary_container="#EADDFF",
            secondary="#CCC2DC",
            on_secondary="#332D41",
            secondary_container="#4A4458",
            on_secondary_container="#E8DEF8",
            tertiary="#EFB8C8",
            on_tertiary="#492532",
            tertiary_container="#633B48",
            on_tertiary_container="#FFD8E4",
            error="#F2B8B5",
            on_error="#601410",
            error_container="#8C1D18",
            on_error_container="#F9DEDC",
            background="#1C1B1F",
            on_background="#E6E1E5",
            surface="#1C1B1F",
            on_surface="#E6E1E5",
            surface_variant="#49454F",
            on_surface_variant="#CAC4D0",
            outline="#938F99",
            outline_variant="#49454F",
            shadow="#000000",
            scrim="#000000",
            inverse_surface="#E6E1E5",
            inverse_on_surface="#313033",
            inverse_primary="#6750A4",
            surface_dim="#141218",
            surface_bright="#3B383E",
            surface_container_lowest="#0F0D13",
            surface_container_low="#1D1B20",
            surface_container="#211F26",
            surface_container_high="#2B2930",
            surface_container_highest="#36343B",
        )
