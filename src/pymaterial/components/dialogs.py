from dataclasses import dataclass
from typing import List, Optional, Any

from ..core.component import SlotComponent, UIComponent
from ..core.context import RenderContext
from ..core.slots import SlotRegistry
from ..tokens.elevation import ElevationLevel
from ..tokens.spacing import Spacing

@dataclass(frozen=True)
class DialogProps:
    dismissible: bool = True
    scrim_dismissible: bool = True
    focus_trap: bool = True
    escape_closes: bool = True

class Dialog(SlotComponent):
    def __init__(
        self,
        component_id: str,
        props: DialogProps,
        slots: Optional[SlotRegistry] = None
    ):
        super().__init__(component_id, slots or SlotRegistry())
        self.props = props

    def render(self, context: RenderContext) -> Any:
        return {
            "type": self.__class__.__name__,
            "id": self.component_id,
            "props": self.props,
            "slots": {name: slot.content.render(context) if slot.content else None for name, slot in self.slots._slots.items()},
            "resolved_tokens": {
                "container_color": self._get_container_color(context),
                "child_background": self._get_child_background(),
                "elevation": self._get_elevation(),
                "shape": self._get_shape(context),
                "title_color": self._get_title_color(context),
                "supporting_text_color": self._get_supporting_text_color(context),
                "title_typography": self._get_title_typography(context),
                "supporting_text_typography": self._get_supporting_text_typography(context),
                "padding": Spacing.SPACING_24.value,
                "spacing": Spacing.SPACING_24.value,
                "button_spacing": Spacing.SPACING_8.value
            }
        }

    def _get_title_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.on_surface

    def _get_supporting_text_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.on_surface_variant

    def _get_title_typography(self, context: RenderContext) -> Any:
        return context.theme.type_scale.headline_small

    def _get_supporting_text_typography(self, context: RenderContext) -> Any:
        return context.theme.type_scale.body_medium

    def _get_container_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.surface_container_high

    def _get_child_background(self) -> str:
        """MD3 spec: all direct content children (labels, text) inside a dialog
        surface must be transparent — they inherit the container surface color.
        Returning 'transparent' here lets consumer bridges (e.g. Qt) apply this
        rule correctly without hard-coding it outside the library."""
        return "transparent"

    def _get_elevation(self) -> float:
        return ElevationLevel.LEVEL_3.value

    def _get_shape(self, context: RenderContext) -> Any:
        return context.theme.shape_scale.extra_large

class AlertDialog(Dialog):
    pass

class ConfirmationDialog(Dialog):
    pass

class CustomDialog(Dialog):
    pass

class FullScreenDialog(Dialog):
    def _get_shape(self, context: RenderContext) -> Any:
        return context.theme.shape_scale.none # Full screen dialogs have no rounded corners
