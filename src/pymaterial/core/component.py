from typing import Any, Dict, List, Optional
from abc import ABC, abstractmethod

from .context import RenderContext
from ..state.state import State
from .slots import SlotRegistry

class UIComponent(ABC):
    def __init__(self, component_id: str, accessibility_label: Optional[str] = None):
        self.component_id = component_id
        self.accessibility_label = accessibility_label
        self.children: List["UIComponent"] = []

    def on_mount(self) -> None:
        pass

    def on_unmount(self) -> None:
        pass

    def on_update(self) -> None:
        pass

    @abstractmethod
    def render(self, context: RenderContext) -> Any:
        pass

class StatelessComponent(UIComponent):
    pass

class StatefulComponent(UIComponent):
    def __init__(self, component_id: str, accessibility_label: Optional[str] = None):
        super().__init__(component_id, accessibility_label)
        self._states: Dict[str, State[Any]] = {}

    def register_state(self, name: str, state: State[Any]) -> None:
        self._states[name] = state

class CompositeComponent(UIComponent):
    def __init__(self, component_id: str, children: List[UIComponent], accessibility_label: Optional[str] = None):
        super().__init__(component_id, accessibility_label)
        self.children = children

    def render(self, context: RenderContext) -> Any:
        return {
            "type": self.__class__.__name__,
            "id": self.component_id,
            "children": [child.render(context) for child in self.children]
        }

class SlotComponent(UIComponent):
    def __init__(self, component_id: str, slots: SlotRegistry, accessibility_label: Optional[str] = None):
        super().__init__(component_id, accessibility_label)
        self.slots = slots
