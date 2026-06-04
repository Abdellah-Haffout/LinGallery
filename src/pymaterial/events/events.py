from enum import Enum, auto
from dataclasses import dataclass
from abc import ABC

class EventPhase(Enum):
    CAPTURE = auto()
    TARGET = auto()
    BUBBLE = auto()

@dataclass
class UIEvent(ABC):
    source_component_id: str
    timestamp_ms: int
    phase: EventPhase = EventPhase.BUBBLE
    _propagation_stopped: bool = False

    def stop_propagation(self) -> None:
        self._propagation_stopped = True

    @property
    def propagation_stopped(self) -> bool:
        return self._propagation_stopped

@dataclass
class ClickEvent(UIEvent):
    x: float
    y: float
    button: int

@dataclass
class HoverEvent(UIEvent):
    x: float
    y: float
    entering: bool

@dataclass
class FocusEvent(UIEvent):
    focused: bool

@dataclass
class InputEvent(UIEvent):
    value: str
    selection_start: int
    selection_end: int

@dataclass
class KeyEvent(UIEvent):
    key_code: int
    modifiers: int

@dataclass
class DragEvent(UIEvent):
    start_x: float
    start_y: float
    current_x: float
    current_y: float
    delta_x: float
    delta_y: float
