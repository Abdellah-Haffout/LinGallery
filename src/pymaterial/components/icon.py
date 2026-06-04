from dataclasses import dataclass
from typing import Any, Optional

from ..core.component import UIComponent
from ..core.context import RenderContext

@dataclass(frozen=True)
class IconProps:
    icon_name: str
    size: int = 24

class Icon(UIComponent):
    def __init__(
        self,
        component_id: str,
        props: IconProps,
        accessibility_label: Optional[str] = None
    ):
        super().__init__(component_id, accessibility_label)
        self.props = props

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "Icon",
            "id": self.component_id,
            "props": self.props,
            "resolved_tokens": {
                "color": context.theme.color_scheme.on_surface
            }
        }
