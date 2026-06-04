from typing import Any, Optional

from ..core.component import UIComponent
from ..core.context import RenderContext

class ScrollArea(UIComponent):
    def __init__(
        self,
        component_id: str,
        content: UIComponent,
        accessibility_label: Optional[str] = None
    ):
        super().__init__(component_id, accessibility_label)
        self.content = content

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "ScrollArea",
            "id": self.component_id,
            "content": self.content.render(context),
            "resolved_tokens": {
                "container_color": "transparent"
            }
        }
