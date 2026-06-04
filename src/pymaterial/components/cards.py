from dataclasses import dataclass
from typing import Optional, Any

from ..core.component import SlotComponent
from ..core.context import RenderContext
from ..core.slots import SlotRegistry
from ..tokens.elevation import ElevationLevel

@dataclass(frozen=True)
class CardProps:
    clickable: bool = False

class Card(SlotComponent):
    def __init__(
        self,
        component_id: str,
        props: CardProps,
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
                "elevation": self._get_elevation(),
                "shape": context.theme.shape_scale.medium,
                "has_outline": self._has_outline()
            }
        }

    def _get_container_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.surface

    def _get_elevation(self) -> float:
        return ElevationLevel.LEVEL_0.value

    def _has_outline(self) -> bool:
        return False

class ElevatedCard(Card):
    def _get_container_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.surface_container_low

    def _get_elevation(self) -> float:
        return ElevationLevel.LEVEL_1.value

class FilledCard(Card):
    def _get_container_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.surface_container_highest

class OutlinedCard(Card):
    def _get_container_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.surface

    def _has_outline(self) -> bool:
        return True
