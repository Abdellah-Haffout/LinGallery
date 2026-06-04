from dataclasses import dataclass
from typing import Optional, Dict

@dataclass
class Slot:
    name: str
    content: Optional["UIComponent"] = None  # Type hint as string to avoid circular import

class SlotRegistry:
    def __init__(self):
        self._slots: Dict[str, Slot] = {}

    def fill(self, name: str, component: "UIComponent") -> None:
        if name not in self._slots:
            self._slots[name] = Slot(name=name)
        self._slots[name].content = component

    def get(self, name: str) -> Optional["UIComponent"]:
        slot = self._slots.get(name)
        return slot.content if slot else None
