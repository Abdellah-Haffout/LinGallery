from typing import Generic, TypeVar, Protocol, List

T = TypeVar('T')

class Observer(Protocol[T]):
    def on_change(self, old_value: T, new_value: T) -> None:
        ...

class State(Generic[T]):
    def __init__(self, initial_value: T):
        self._value = initial_value
        self._observers: List[Observer[T]] = []

    def get(self) -> T:
        return self._value

    def set(self, new_value: T) -> None:
        if self._value != new_value:
            old_value = self._value
            self._value = new_value
            self._notify_observers(old_value, new_value)

    def subscribe(self, observer: Observer[T]) -> None:
        if observer not in self._observers:
            self._observers.append(observer)

    def unsubscribe(self, observer: Observer[T]) -> None:
        if observer in self._observers:
            self._observers.remove(observer)

    def _notify_observers(self, old_value: T, new_value: T) -> None:
        for observer in self._observers:
            observer.on_change(old_value, new_value)
