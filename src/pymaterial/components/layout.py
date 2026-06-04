from typing import Any, List

from ..core.component import CompositeComponent
from ..core.context import RenderContext

class Row(CompositeComponent):
    def render(self, context: RenderContext) -> Any:
        return {
            "type": "Row",
            "id": self.component_id,
            "children": [child.render(context) for child in self.children],
            "resolved_tokens": {}
        }

class Column(CompositeComponent):
    def render(self, context: RenderContext) -> Any:
        return {
            "type": "Column",
            "id": self.component_id,
            "children": [child.render(context) for child in self.children],
            "resolved_tokens": {}
        }
