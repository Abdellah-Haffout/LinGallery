from dataclasses import dataclass
from typing import Any, Optional, List

from ..core.component import UIComponent, CompositeComponent
from ..core.context import RenderContext
from ..tokens.spacing import Spacing

class FormLayout(CompositeComponent):
    def render(self, context: RenderContext) -> Any:
        return {
            "type": "FormLayout",
            "id": self.component_id,
            "children": [child.render(context) for child in self.children],
            "resolved_tokens": {
                "spacing": int(Spacing.SPACING_8.value),
                "label_color": context.theme.color_scheme.on_surface_variant,
                "value_color": context.theme.color_scheme.on_surface,
                "label_typography": context.theme.type_scale.body_medium,
                "value_typography": context.theme.type_scale.body_medium
            }
        }

@dataclass(frozen=True)
class FormFieldProps:
    label: str
    value: str

class FormField(UIComponent):
    def __init__(
        self,
        component_id: str,
        props: FormFieldProps,
        accessibility_label: Optional[str] = None
    ):
        super().__init__(component_id, accessibility_label)
        self.props = props

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "FormField",
            "id": self.component_id,
            "props": self.props,
            "resolved_tokens": {} # Handled by FormLayout tokens usually, but can be explicit
        }
