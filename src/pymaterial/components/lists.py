from dataclasses import dataclass
from typing import Optional, Any, List

from ..core.component import SlotComponent, CompositeComponent, UIComponent
from ..core.context import RenderContext
from ..core.slots import SlotRegistry

@dataclass(frozen=True)
class ListItemProps:
    headline: str
    supporting_text: Optional[str] = None
    clickable: bool = True
    show_divider: bool = False

class ListItem(SlotComponent):
    def __init__(
        self,
        component_id: str,
        props: ListItemProps,
        slots: Optional[SlotRegistry] = None
    ):
        super().__init__(component_id, slots or SlotRegistry())
        self.props = props

    def render(self, context: RenderContext) -> Any:
        return {
            "type": self.__class__.__name__,
            "id": self.component_id,
            "props": self.props,
            "slots": {name: slot.content.render(context) if slot.content else None for name, slot in self.slots._slots.items()},
            "resolved_tokens": {
                "container_color": context.theme.color_scheme.surface,
                "headline_color": context.theme.color_scheme.on_surface,
                "supporting_text_color": context.theme.color_scheme.on_surface_variant,
                "headline_style": context.theme.type_scale.body_large,
                "supporting_text_style": context.theme.type_scale.body_medium,
            }
        }

class ListComponent(CompositeComponent):
    def __init__(self, component_id: str, children: List[ListItem]):
        # We only accept ListItem children
        super().__init__(component_id, children) # type: ignore

    def render(self, context: RenderContext) -> Any:
        return {
            "type": self.__class__.__name__,
            "id": self.component_id,
            "children": [child.render(context) for child in self.children],
            "resolved_tokens": {
                # Surface colors
                "container_color": context.theme.color_scheme.surface,
                # Item content colors (normal, hover, selected)
                "content_color": context.theme.color_scheme.on_surface_variant,
                "hover_color": context.theme.color_scheme.surface_variant,
                "hover_content_color": context.theme.color_scheme.on_surface,
                "selected_color": context.theme.color_scheme.primary_container,
                "selected_content_color": context.theme.color_scheme.on_primary_container,
                # Structural
                "outline": context.theme.color_scheme.outline,
            }
        }
