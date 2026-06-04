from dataclasses import dataclass
from typing import Any

from ..core.component import StatelessComponent
from ..core.context import RenderContext

@dataclass(frozen=True)
class TextProps:
    text: str

class Text(StatelessComponent):
    def __init__(self, component_id: str, props: TextProps):
        super().__init__(component_id)
        self.props = props

    def render(self, context: RenderContext) -> Any:
        return {
            "type": "Text",
            "id": self.component_id,
            "props": self.props,
            "resolved_tokens": {
                "text_style": context.theme.type_scale.body_medium,
                "color": context.theme.color_scheme.on_surface
            }
        }

class TitleText(Text):
    def render(self, context: RenderContext) -> Any:
        return {
            "type": "Text",
            "id": self.component_id,
            "props": self.props,
            "resolved_tokens": {
                "text_style": context.theme.type_scale.title_large,
                "color": context.theme.color_scheme.on_surface
            }
        }
