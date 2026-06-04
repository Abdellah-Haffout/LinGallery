from enum import Enum, auto
from dataclasses import dataclass
from typing import Optional, Any

from ..core.component import SlotComponent
from ..core.context import RenderContext
from ..core.slots import SlotRegistry

class InteractionState(Enum):
    ENABLED = auto()
    DISABLED = auto()
    HOVERED = auto()
    FOCUSED = auto()
    PRESSED = auto()
    DRAGGED = auto()

@dataclass(frozen=True)
class ButtonProps:
    label: str
    on_click: Any  # Reference to a callback function
    state: InteractionState = InteractionState.ENABLED
    loading: bool = False
    accessibility_label: Optional[str] = None

class Button(SlotComponent):
    def __init__(
        self,
        component_id: str,
        props: ButtonProps,
        slots: Optional[SlotRegistry] = None
    ):
        super().__init__(component_id, slots or SlotRegistry(), props.accessibility_label)
        self.props = props

    def render(self, context: RenderContext) -> Any:
        # Resolve common tokens
        text_style = context.theme.type_scale.label_large
        shape = context.theme.shape_scale.full
        
        # Specific button variants will override this abstract base render logic
        # by supplying their specific tokens (container_color, label_color, etc).
        return {
            "type": self.__class__.__name__,
            "id": self.component_id,
            "props": self.props,
            "slots": {name: slot.content.render(context) if slot.content else None for name, slot in self.slots._slots.items()},
            "resolved_tokens": {
                "text_style": text_style,
                "shape": shape,
                "container_color": self._get_container_color(context),
                "label_color": self._get_label_color(context),
                "elevation": self._get_elevation(),
                "has_outline": self._has_outline()
            }
        }

    def _get_container_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.surface # Fallback

    def _get_label_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.primary # Fallback

    def _get_elevation(self) -> float:
        return 0.0

    def _has_outline(self) -> bool:
        return False

class FilledButton(Button):
    def _get_container_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            # MD3 disabled state is typically onSurface with 12% opacity
            return context.theme.color_scheme.on_surface # Note: Should apply opacity in renderer
        return context.theme.color_scheme.primary

    def _get_label_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            return context.theme.color_scheme.on_surface # Note: 38% opacity
        return context.theme.color_scheme.on_primary

class OutlinedButton(Button):
    def _get_container_color(self, context: RenderContext) -> str:
        return "transparent" # Or None/equivalent in the rendering system

    def _get_label_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            return context.theme.color_scheme.on_surface
        return context.theme.color_scheme.primary

    def _has_outline(self) -> bool:
        return True

class TextButton(Button):
    def _get_container_color(self, context: RenderContext) -> str:
        return "transparent"

    def _get_label_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            return context.theme.color_scheme.on_surface
        return context.theme.color_scheme.primary

class TonalButton(Button):
    def _get_container_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            return context.theme.color_scheme.on_surface
        return context.theme.color_scheme.secondary_container

    def _get_label_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            return context.theme.color_scheme.on_surface
        return context.theme.color_scheme.on_secondary_container

class ElevatedButton(Button):
    def _get_container_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            return context.theme.color_scheme.on_surface
        return context.theme.color_scheme.surface_container_low

    def _get_label_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            return context.theme.color_scheme.on_surface
        return context.theme.color_scheme.primary

    def _get_elevation(self) -> float:
        if self.props.state == InteractionState.DISABLED:
            return 0.0
        if self.props.state == InteractionState.HOVERED:
            return 3.0
        if self.props.state == InteractionState.PRESSED:
            return 1.0
        return 1.0

class IconButton(Button):
    def _get_container_color(self, context: RenderContext) -> str:
        return "transparent"

    def _get_label_color(self, context: RenderContext) -> str:
        if self.props.state == InteractionState.DISABLED:
            return context.theme.color_scheme.on_surface
        return context.theme.color_scheme.on_surface_variant
