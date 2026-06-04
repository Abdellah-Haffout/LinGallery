from dataclasses import dataclass
from typing import Optional, Any, List

from ..core.component import SlotComponent, CompositeComponent
from ..core.context import RenderContext
from ..core.slots import SlotRegistry

@dataclass(frozen=True)
class MenuItemProps:
    label: str
    selected: bool = False
    disabled: bool = False
    on_select: Any = None

class MenuItem(SlotComponent):
    def __init__(
        self,
        component_id: str,
        props: MenuItemProps,
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
                "label_color": context.theme.color_scheme.on_surface,
                "container_color": context.theme.color_scheme.secondary_container if self.props.selected else "transparent",
                "text_style": context.theme.type_scale.label_large
            }
        }

class Menu(CompositeComponent):
    def __init__(self, component_id: str, children: List[MenuItem]):
        super().__init__(component_id, children) # type: ignore
        
    def open(self) -> None:
        pass # Navigation contract abstract

    def close(self) -> None:
        pass # Navigation contract abstract

    def select(self, item_id: str) -> None:
        pass # Navigation contract abstract

    def render(self, context: RenderContext) -> Any:
        return {
            "type": self.__class__.__name__,
            "id": self.component_id,
            "children": [child.render(context) for child in self.children],
            "resolved_tokens": {
                "container_color": context.theme.color_scheme.surface_container,
                "elevation": 2.0, # MD3 menu elevation
                "shape": context.theme.shape_scale.extra_small
            }
        }
