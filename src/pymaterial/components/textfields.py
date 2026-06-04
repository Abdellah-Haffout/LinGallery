from enum import Enum, auto
from dataclasses import dataclass
from typing import Optional, Any

from ..core.component import SlotComponent
from ..core.context import RenderContext
from ..core.slots import SlotRegistry

class TextFieldState(Enum):
    ENABLED = auto()
    DISABLED = auto()
    HOVERED = auto()
    FOCUSED = auto()
    ERROR = auto()

@dataclass(frozen=True)
class ValidationResult:
    valid: bool
    message: Optional[str] = None

@dataclass(frozen=True)
class TextFieldProps:
    label: str
    value: str
    placeholder: str = ""
    helper_text: str = ""
    error_message: str = ""
    state: TextFieldState = TextFieldState.ENABLED
    counter_max: Optional[int] = None

class TextField(SlotComponent):
    def __init__(
        self,
        component_id: str,
        props: TextFieldProps,
        slots: Optional[SlotRegistry] = None
    ):
        super().__init__(component_id, slots or SlotRegistry())
        self.props = props

    def validate(self, value: str) -> ValidationResult:
        # Abstract contract for validation hook
        return ValidationResult(valid=True)

    def render(self, context: RenderContext) -> Any:
        return {
            "type": self.__class__.__name__,
            "id": self.component_id,
            "props": self.props,
            "slots": {name: slot.content.render(context) if slot.content else None for name, slot in self.slots._slots.items()},
            "resolved_tokens": {
                "container_color": self._get_container_color(context),
                "indicator_color": self._get_indicator_color(context),
                "outline_color": self._get_outline_color(context),
                "text_style": context.theme.type_scale.body_large,
                "label_style": context.theme.type_scale.body_small,
                "shape": self._get_shape(context)
            }
        }

    def _get_container_color(self, context: RenderContext) -> str:
        return context.theme.color_scheme.surface_variant

    def _get_outline_color(self, context: RenderContext) -> str:
        if self.props.state == TextFieldState.ERROR:
            return context.theme.color_scheme.error
        if self.props.state == TextFieldState.FOCUSED:
            return context.theme.color_scheme.primary
        return context.theme.color_scheme.outline

    def _get_indicator_color(self, context: RenderContext) -> str:
        if self.props.state == TextFieldState.ERROR:
            return context.theme.color_scheme.error
        if self.props.state == TextFieldState.FOCUSED:
            return context.theme.color_scheme.primary
        return context.theme.color_scheme.on_surface_variant

    def _get_shape(self, context: RenderContext) -> Any:
        return context.theme.shape_scale.extra_small # Top corners only typically

class FilledTextField(TextField):
    pass

class OutlinedTextField(TextField):
    def _get_container_color(self, context: RenderContext) -> str:
        return "transparent"

    def _get_shape(self, context: RenderContext) -> Any:
        return context.theme.shape_scale.extra_small # All corners
