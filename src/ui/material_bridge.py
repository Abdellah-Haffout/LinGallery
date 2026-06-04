from pymaterial.theme.dark_theme import DarkTheme
from pymaterial.theme.theme import ThemeOverride
from pymaterial.adapters.pyside6.builder import PySide6MaterialBuilder
import dataclasses
from core.constants import DarkPalette

class MaterialQtBridge:
    _instance = None

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
        self.builder = PySide6MaterialBuilder(theme=self.theme)

    @classmethod
    def get(cls) -> "MaterialQtBridge":
        if cls._instance is None:
            cls._instance = MaterialQtBridge()
        return cls._instance
