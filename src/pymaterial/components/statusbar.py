from dataclasses import dataclass
from typing import Any, Optional

from ..core.component import UIComponent
from ..core.context import RenderContext

@dataclass(frozen=True)
class StatusBarProps:
    message: str

class StatusBar(UIComponent):
    def __init__(
        self,
        component_id: str,
        props: StatusBarProps,
        accessibility_label: Optional[str] = None
    ):
        super().__init__(component_id, accessibility_label)
        self.props = props

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "StatusBar",
            "id": self.component_id,
            "props": self.props,
            "resolved_tokens": {
                "container_color": context.theme.color_scheme.surface,
                "text_color": context.theme.color_scheme.on_surface_variant,
                "text_typography": context.theme.type_scale.label_medium,
                "divider_color": context.theme.color_scheme.outline_variant
            }
        }
