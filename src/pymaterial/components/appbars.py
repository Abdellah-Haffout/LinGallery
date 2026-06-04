from dataclasses import dataclass
from typing import Optional, Any

from ..core.component import UIComponent
from ..core.context import RenderContext

@dataclass(frozen=True)
class TopAppBarProps:
    title: str

class TopAppBar(UIComponent):
    def __init__(
        self,
        component_id: str,
        props: TopAppBarProps,
        navigation_icon: Optional[UIComponent] = None,
        actions: Optional[list[UIComponent]] = None,
        accessibility_label: Optional[str] = None
    ):
        super().__init__(component_id, accessibility_label)
        self.props = props
        self.navigation_icon = navigation_icon
        self.actions = actions or []

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "TopAppBar",
            "id": self.component_id,
            "props": self.props,
            "navigation_icon": self.navigation_icon.render(context) if self.navigation_icon else None,
            "actions": [action.render(context) for action in self.actions],
            "resolved_tokens": {
                "container_color": context.theme.color_scheme.surface,
                "title_color": context.theme.color_scheme.on_surface,
                "title_typography": context.theme.type_scale.title_large,
            }
        }
