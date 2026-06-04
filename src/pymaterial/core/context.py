from dataclasses import dataclass
from typing import TYPE_CHECKING

from ..theme.theme import Theme

if TYPE_CHECKING:
    from ..renderer.interface import RendererInterface

@dataclass(frozen=True)
class RenderContext:
    theme: Theme
    renderer: "RendererInterface"
