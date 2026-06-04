from dataclasses import dataclass
from typing import Optional, Any

from ..core.component import SlotComponent
from ..core.context import RenderContext
from ..core.slots import SlotRegistry

@dataclass(frozen=True)
class ChipProps:
    label: str
    selected: bool = False
    enabled: bool = True

class Chip(SlotComponent):
    def __init__(
        self,
        component_id: str,
        props: ChipProps,
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
                "label_color": self._get_label_color(context),
                "shape": context.theme.shape_scale.small,
                "text_style": context.theme.type_scale.label_large
            }
        }

    def _get_container_color(self, context: RenderContext) -> str:
        return "transparent" # Base chips are outlined usually

    def _get_label_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.on_surface_variant

class AssistChip(Chip):
    pass

class FilterChip(Chip):
    def _get_container_color(self, context: RenderContext) -> str:
        if self.props.selected:
            return context.theme.color_scheme.secondary_container
        return "transparent"

    def _get_label_color(self, context: RenderContext) -> str:
        if self.props.selected:
            return context.theme.color_scheme.on_secondary_container
        return context.theme.color_scheme.on_surface_variant

class InputChip(Chip):
    pass

class SuggestionChip(Chip):
    pass
