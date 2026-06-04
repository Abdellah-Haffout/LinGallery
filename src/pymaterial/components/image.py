from dataclasses import dataclass
from typing import Any, Optional

from ..core.component import UIComponent
from ..core.context import RenderContext

@dataclass(frozen=True)
class ImageProps:
    path: str
    smooth: bool = True

class Image(UIComponent):
    def __init__(
        self,
        component_id: str,
        props: ImageProps,
        accessibility_label: Optional[str] = None
    ):
        super().__init__(component_id, accessibility_label)
        self.props = props

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "Image",
            "id": self.component_id,
            "props": self.props,
            "resolved_tokens": {}
        }
