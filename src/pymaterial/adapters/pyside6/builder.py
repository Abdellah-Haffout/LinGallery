from PySide6.QtWidgets import QWidget, QLabel, QPushButton, QFrame, QVBoxLayout, QHBoxLayout
from PySide6.QtGui import QFont, QCursor
from PySide6.QtCore import Qt

from ...core.component import UIComponent
from ...core.context import RenderContext
from ...theme.dark_theme import DarkTheme

class PySide6MaterialBuilder:
    def __init__(self):
        self.theme = DarkTheme.get()
        # PySide6 handles its own rendering loop, so we don't pass a drawing RendererInterface here.
        self.context = RenderContext(theme=self.theme, renderer=None) # type: ignore

    def build(self, component: UIComponent, parent: QWidget | None = None) -> QWidget:
        node = component.render(self.context)
        return self._build_node(node, parent)

    def _build_node(self, node: dict, parent: QWidget | None = None) -> QWidget:
        type_name = node["type"]
        if type_name == "Text":
            return self._build_text(node, parent)
        elif type_name.endswith("Button"):
            return self._build_button(node, parent)
        elif type_name in ("Row", "Column"):
            return self._build_layout(node, parent)
        elif type_name.endswith("Dialog"):
            return self._build_dialog(node, parent)
        else:
            # Fallback wrapper
            w = QWidget(parent)
            w.setObjectName(node["id"])
            return w

    def _build_text(self, node: dict, parent: QWidget | None) -> QLabel:
        props = node["props"]
        tokens = node["resolved_tokens"]
        label = QLabel(props.text, parent)
        label.setObjectName(node["id"])
        
        # Apply tokens
        ts = tokens["text_style"]
        font = QFont(ts.font_family, int(ts.size_sp), ts.weight)
        label.setFont(font)
        
        # Optional: convert text roles to word wrap
        if ts.size_sp < 16:  # heuristic for body/support text
            label.setWordWrap(True)
            
        label.setStyleSheet(f"color: {tokens['color']};")
        return label

    def _build_button(self, node: dict, parent: QWidget | None) -> QPushButton:
        props = node["props"]
        tokens = node["resolved_tokens"]
        btn = QPushButton(props.label, parent)
        btn.setObjectName(node["id"])
        btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        
        # Callbacks
        if props.on_click:
            btn.clicked.connect(lambda _checked=False: props.on_click())
            
        # Styling
        ts = tokens["text_style"]
        font = QFont(ts.font_family, int(ts.size_sp), ts.weight)
        btn.setFont(font)
        
        bg_color = tokens["container_color"]
        fg_color = tokens["label_color"]
        radius = tokens["shape"].top_start_dp
        border = f"1px solid {self.theme.color_scheme.outline}" if tokens.get("has_outline") else "none"
        
        hover_bg = self.theme.color_scheme.surface_variant if bg_color == "transparent" else self.theme.color_scheme.primary_container

        btn.setStyleSheet(f"""
            QPushButton {{
                background-color: {bg_color};
                color: {fg_color};
                border: {border};
                border-radius: {radius}px;
                padding: 0 16px;
                min-height: 36px;
                min-width: 92px;
            }}
            QPushButton:hover {{
                background-color: {hover_bg};
            }}
        """)
        return btn

    def _build_dialog(self, node: dict, parent: QWidget | None) -> QFrame:
        tokens = node["resolved_tokens"]
        slots = node.get("slots", {})
        
        frame = QFrame(parent)
        frame.setObjectName(node["id"])
        
        bg_color = tokens["container_color"]
        radius = tokens["shape"].top_start_dp
        
        frame.setStyleSheet(f"""
            QFrame#{node["id"]} {{
                background-color: {bg_color};
                border: 1px solid {self.theme.color_scheme.outline};
                border-radius: {radius}px;
            }}
        """)
        
        layout = QVBoxLayout(frame)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(12)
        
        if slots.get("title"):
            layout.addWidget(self._build_node(slots["title"], frame))
            
        if slots.get("content"):
            layout.addWidget(self._build_node(slots["content"], frame), stretch=1)
            
        if slots.get("actions"):
            actions_layout = QHBoxLayout()
            actions_layout.addStretch()
            acts = slots["actions"]
            actions_layout.addWidget(self._build_node(acts, frame))
            layout.addLayout(actions_layout)
            
        return frame

    def _build_layout(self, node: dict, parent: QWidget | None) -> QWidget:
        w = QWidget(parent)
        w.setObjectName(node["id"])
        if node["type"] == "Row":
            layout = QHBoxLayout(w)
        else:
            layout = QVBoxLayout(w)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(10)
        for child in node.get("children", []):
            layout.addWidget(self._build_node(child, w))
        return w
