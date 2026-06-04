from dataclasses import dataclass
from typing import Any, Optional, Callable, List

from ..core.component import UIComponent
from ..core.context import RenderContext

@dataclass(frozen=True)
class GridViewProps:
    items: List[Any]
    on_item_click: Optional[Callable] = None
    on_item_double_click: Optional[Callable] = None

class GridView(UIComponent):
    def __init__(
        self,
        component_id: str,
        props: GridViewProps,
        accessibility_label: Optional[str] = None
    ):
        super().__init__(component_id, accessibility_label)
        self.props = props

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "GridView",
            "id": self.component_id,
            "props": self.props,
            "resolved_tokens": {
                "background_color": "transparent",
                "item_surface_container": context.theme.color_scheme.surface_container,
                "item_surface_variant": context.theme.color_scheme.surface_variant,
                "item_primary_container": context.theme.color_scheme.primary_container,
                "item_primary": context.theme.color_scheme.primary,
                "outline": context.theme.color_scheme.outline,
                "shape": context.theme.shape_scale.medium
            }
        }
