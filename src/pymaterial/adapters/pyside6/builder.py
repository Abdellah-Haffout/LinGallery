from PySide6.QtWidgets import (
    QWidget, QLabel, QPushButton, QFrame, QVBoxLayout, QHBoxLayout,
    QScrollArea, QListView, QStatusBar, QListWidget, QListWidgetItem, QFormLayout
)
from PySide6.QtGui import QFont, QCursor, QPixmap
from PySide6.QtCore import Qt, QSize

from ...core.component import UIComponent
from ...core.context import RenderContext
from ...theme.dark_theme import DarkTheme

from ...theme.theme import Theme

class PySide6MaterialBuilder:
    def __init__(self, theme: Theme | None = None):
        self.theme = theme or DarkTheme.get()
        # PySide6 handles its own rendering loop
        self.context = RenderContext(theme=self.theme, renderer=None) # type: ignore

    def build(self, component: UIComponent, parent: QWidget | None = None) -> QWidget:
        node = component.render(self.context)
        return self._build_node(node, parent)

    def _build_node(self, node: dict | None, parent: QWidget | None = None) -> QWidget:
        if not node:
            return QWidget(parent)
            
        type_name = node["type"]
        if type_name == "Text":
            return self._build_text(node, parent)
        elif type_name.endswith("Button"):
            return self._build_button(node, parent)
        elif type_name in ("Row", "Column"):
            return self._build_layout(node, parent)
        elif type_name.endswith("Dialog"):
            return self._build_dialog(node, parent)
        elif type_name == "TopAppBar":
            return self._build_top_app_bar(node, parent)
        elif type_name == "ScrollArea":
            return self._build_scroll_area(node, parent)
        elif type_name == "GridView":
            return self._build_grid_view(node, parent)
        elif type_name == "Image":
            return self._build_image(node, parent)
        elif type_name == "Divider":
            return self._build_divider(node, parent)
        elif type_name == "StatusBar":
            return self._build_status_bar(node, parent)
        elif type_name == "Icon":
            return self._build_icon(node, parent)
        elif type_name == "ListComponent":
            return self._build_list(node, parent)
        elif type_name == "FormLayout":
            return self._build_form_layout(node, parent)
        else:
            w = QWidget(parent)
            w.setObjectName(node["id"])
            return w

    def _apply_font(self, widget: QWidget, text_style):
        font = QFont(text_style.font_family, int(text_style.size_sp))
        weight_map = {
            100: QFont.Weight.Thin, 200: QFont.Weight.ExtraLight, 300: QFont.Weight.Light,
            400: QFont.Weight.Normal, 500: QFont.Weight.Medium, 600: QFont.Weight.DemiBold,
            700: QFont.Weight.Bold, 800: QFont.Weight.ExtraBold, 900: QFont.Weight.Black,
        }
        font.setWeight(weight_map.get(text_style.weight, QFont.Weight.Normal))
        if text_style.letter_spacing_sp != 0.0:
            font.setLetterSpacing(QFont.AbsoluteSpacing, text_style.letter_spacing_sp)
        widget.setFont(font)

    def _build_text(self, node: dict, parent: QWidget | None) -> QLabel:
        props = node["props"]
        tokens = node["resolved_tokens"]
        label = QLabel(props.text, parent)
        label.setObjectName(node["id"])
        self._apply_font(label, tokens["text_style"])
        if tokens["text_style"].size_sp < 16:
            label.setWordWrap(True)
        label.setStyleSheet(f"color: {tokens['color']};")
        return label

    def _build_button(self, node: dict, parent: QWidget | None) -> QPushButton:
        props = node["props"]
        tokens = node["resolved_tokens"]
        btn = QPushButton(props.label, parent)
        btn.setObjectName(node["id"])
        if props.accessibility_label:
            btn.setToolTip(props.accessibility_label)
        btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        
        if props.on_click:
            btn.clicked.connect(lambda _checked=False: props.on_click())
            
        self._apply_font(btn, tokens["text_style"])
        
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

    def _build_layout(self, node: dict, parent: QWidget | None) -> QWidget:
        w = QWidget(parent)
        w.setObjectName(node["id"])
        layout = QHBoxLayout(w) if node["type"] == "Row" else QVBoxLayout(w)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        for child in node.get("children", []):
            if child:
                layout.addWidget(self._build_node(child, w))
        return w

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
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(16)
        if slots.get("title"):
            layout.addWidget(self._build_node(slots["title"], frame))
        if slots.get("content"):
            layout.addWidget(self._build_node(slots["content"], frame), stretch=1)
        if slots.get("actions"):
            actions_layout = QHBoxLayout()
            actions_layout.addStretch()
            actions_layout.addWidget(self._build_node(slots["actions"], frame))
            layout.addLayout(actions_layout)
        return frame

    def _build_top_app_bar(self, node: dict, parent: QWidget | None) -> QWidget:
        w = QWidget(parent)
        w.setObjectName(node["id"])
        w.setFixedHeight(56) # MD3 standard
        tokens = node["resolved_tokens"]
        w.setStyleSheet(f"background-color: {tokens['container_color']};")
        
        layout = QHBoxLayout(w)
        layout.setContentsMargins(16, 0, 16, 0)
        layout.setSpacing(16)
        
        if node.get("navigation_icon"):
            layout.addWidget(self._build_node(node["navigation_icon"], w))
            
        title = QLabel(node["props"].title)
        self._apply_font(title, tokens["title_typography"])
        title.setStyleSheet(f"color: {tokens['title_color']};")
        layout.addWidget(title)
        layout.addStretch()
        
        for action in node.get("actions", []):
            if action:
                layout.addWidget(self._build_node(action, w))
                
        return w

    def _build_scroll_area(self, node: dict, parent: QWidget | None) -> QScrollArea:
        scroll = QScrollArea(parent)
        scroll.setObjectName(node["id"])
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.NoFrame)
        scroll.setStyleSheet(f"background-color: {node['resolved_tokens']['container_color']}; border: none;")
        if node.get("content"):
            scroll.setWidget(self._build_node(node["content"], scroll))
        return scroll

    def _build_grid_view(self, node: dict, parent: QWidget | None) -> QWidget:
        # For GridView we will rely on GalleryView's custom implementation
        # but we inject the Material tokens into it by returning a styled QWidget wrapper.
        # This acts as a placeholder if called directly, but in practice LinGallery
        # uses the custom PySide6 QListView for performance.
        w = QWidget(parent)
        return w

    def _build_image(self, node: dict, parent: QWidget | None) -> QLabel:
        lbl = QLabel(parent)
        lbl.setObjectName(node["id"])
        path = node["props"].path
        if path:
            pixmap = QPixmap(path)
            lbl.setPixmap(pixmap)
        return lbl

    def _build_divider(self, node: dict, parent: QWidget | None) -> QFrame:
        props = node["props"]
        tokens = node["resolved_tokens"]
        frame = QFrame(parent)
        frame.setObjectName(node["id"])
        if props.vertical:
            frame.setFixedWidth(1)
        else:
            frame.setFixedHeight(1)
        frame.setStyleSheet(f"background-color: {tokens['color']};")
        return frame

    def _build_status_bar(self, node: dict, parent: QWidget | None) -> QStatusBar:
        sb = QStatusBar(parent)
        sb.setObjectName(node["id"])
        tokens = node["resolved_tokens"]
        sb.showMessage(node["props"].message)
        sb.setStyleSheet(f"""
            QStatusBar {{
                background-color: {tokens['container_color']};
                color: {tokens['text_color']};
                border-top: 1px solid {tokens['divider_color']};
            }}
        """)
        return sb

    def _build_icon(self, node: dict, parent: QWidget | None) -> QLabel:
        props = node["props"]
        tokens = node["resolved_tokens"]
        lbl = QLabel(props.icon_name, parent)
        lbl.setObjectName(node["id"])
        # Assuming Material Symbols font is loaded in the app
        font = QFont("Material Symbols Outlined", props.size)
        lbl.setFont(font)
        lbl.setStyleSheet(f"color: {tokens['color']};")
        return lbl

    def _build_list(self, node: dict, parent: QWidget | None) -> QListWidget:
        lw = QListWidget(parent)
        lw.setObjectName(node["id"])
        tokens = node["resolved_tokens"]
        
        # Pull tokens for states from theme
        theme = self.theme
        surface = theme.color_scheme.surface
        surface_variant = theme.color_scheme.surface_variant
        primary_container = theme.color_scheme.primary_container
        on_primary_container = theme.color_scheme.on_primary_container
        on_surface = theme.color_scheme.on_surface
        on_surface_variant = theme.color_scheme.on_surface_variant
        outline = theme.color_scheme.outline

        lw.setStyleSheet(f"""
            QListWidget {{
                background-color: {surface};
                border: none;
                outline: none;
                padding: 8px 0;
            }}
            QListWidget::item {{
                padding: 10px 16px;
                border-radius: 0;
                color: {on_surface_variant};
            }}
            QListWidget::item:hover {{
                background-color: {surface_variant};
                color: {on_surface};
            }}
            QListWidget::item:selected {{
                background-color: {primary_container};
                color: {on_primary_container};
            }}
            QScrollBar:vertical {{
                background: {surface};
                width: 4px;
                border-radius: 2px;
            }}
            QScrollBar::handle:vertical {{
                background: {outline};
                border-radius: 2px;
            }}
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
                height: 0;
            }}
        """)
        
        for child in node.get("children", []):
            if child and child["type"] == "ListItem":
                props = child["props"]
                item = QListWidgetItem(props.headline)
                if props.supporting_text:
                    item.setText(f"{props.headline}  ({props.supporting_text})")
                lw.addItem(item)
                
        return lw

    def _build_form_layout(self, node: dict, parent: QWidget | None) -> QWidget:
        w = QWidget(parent)
        w.setObjectName(node["id"])
        layout = QFormLayout(w)
        layout.setContentsMargins(0, 0, 0, 0)
        tokens = node["resolved_tokens"]
        layout.setSpacing(tokens["spacing"])
        
        for child in node.get("children", []):
            if child and child["type"] == "FormField":
                props = child["props"]
                
                lbl = QLabel(props.label)
                self._apply_font(lbl, tokens["label_typography"])
                lbl.setStyleSheet(f"color: {tokens['label_color']};")
                
                val = QLabel(props.value)
                val.setWordWrap(True)
                val.setTextInteractionFlags(Qt.TextSelectableByMouse)
                self._apply_font(val, tokens["value_typography"])
                val.setStyleSheet(f"color: {tokens['value_color']};")
                
                layout.addRow(lbl, val)
                
        return w
