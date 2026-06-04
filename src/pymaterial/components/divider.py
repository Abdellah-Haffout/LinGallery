from dataclasses import dataclass
from typing import Any, Optional

from ..core.component import UIComponent
from ..core.context import RenderContext

@dataclass(frozen=True)
class DividerProps:
    vertical: bool = False

class Divider(UIComponent):
    def __init__(
        self,
        component_id: str,
        props: Optional[DividerProps] = None,
        accessibility_label: Optional[str] = None
    ):
        super().__init__(component_id, accessibility_label)
        self.props = props or DividerProps()

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "Divider",
            "id": self.component_id,
            "props": self.props,
            "resolved_tokens": {
                "color": context.theme.color_scheme.outline_variant
            }
        }
