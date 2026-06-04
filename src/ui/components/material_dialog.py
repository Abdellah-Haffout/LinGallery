from PySide6.QtWidgets import QFrame, QVBoxLayout, QHBoxLayout, QLabel, QWidget, QLineEdit, QPushButton, QSizePolicy
from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QMouseEvent, QKeyEvent, QFont

from ui.material_bridge import MaterialQtBridge
from pymaterial.components.dialogs import AlertDialog, DialogProps, DialogTitle, DialogSupportingText
from pymaterial.components.text import TextProps
from pymaterial.components.buttons import FilledButton, TextButton, ButtonProps
from pymaterial.components.textfields import OutlinedTextField, TextFieldProps

def _apply_font(widget, text_style):
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

class MD3Button(QPushButton):
    def __init__(self, bridge: MaterialQtBridge, text: str, is_filled: bool = False, parent=None):
        super().__init__(text, parent)
        self.setCursor(Qt.PointingHandCursor)
        self.setFixedHeight(40)
        
        props = ButtonProps(label=text, on_click=None)
        if is_filled:
            btn_comp = FilledButton(f"btn_{text}", props=props)
        else:
            btn_comp = TextButton(f"btn_{text}", props=props)
            
        resolved = btn_comp.render(bridge.builder.context)["resolved_tokens"]
        
        bg_color = resolved["container_color"]
        fg_color = resolved["label_color"]
        
        radius = 20
        _apply_font(self, resolved["text_style"])
        
        theme = bridge.theme
        if is_filled:
            hover_bg = theme.color_scheme.primary_container
        else:
            hover_bg = theme.color_scheme.surface_variant
            
        border = ""
        if resolved.get("has_outline"):
            border = f"border: 1px solid {theme.color_scheme.outline};"
        else:
            border = "border: none;"
            
        self.setStyleSheet(f"""
            QPushButton {{
                background-color: {bg_color};
                color: {fg_color};
                border-radius: {radius}px;
                padding: 0 24px;
                {border}
            }}
            QPushButton:hover {{
                background-color: {hover_bg};
            }}
        """)

class MD3TextField(QLineEdit):
    def __init__(self, bridge: MaterialQtBridge, label: str, value: str = "", parent=None):
        super().__init__(value, parent)
        self.setFixedHeight(56)
        
        props = TextFieldProps(label=label, value=value)
        tf_comp = OutlinedTextField("tf", props=props)
        resolved = tf_comp.render(bridge.builder.context)["resolved_tokens"]
        
        bg_color = resolved["container_color"]
        outline_color = resolved["outline_color"]
        
        theme = bridge.theme
        indicator_color = resolved.get("indicator_color", theme.color_scheme.primary)
        
        shape = resolved.get("shape", 4.0)
        if hasattr(shape, "top_start_dp"):
            radius = int(shape.top_start_dp)
        else:
            radius = int(shape)
            
        _apply_font(self, resolved["text_style"])
        
        self.setPlaceholderText(label)
        self.setStyleSheet(f"""
            QLineEdit {{
                background-color: {bg_color};
                color: {theme.color_scheme.on_surface};
                border: 1px solid {outline_color};
                border-radius: {radius}px;
                padding: 0 16px;
                selection-background-color: {theme.color_scheme.primary_container};
            }}
            QLineEdit:focus {{
                border: 2px solid {indicator_color};
                padding: 0 15px;
            }}
        """)

class MaterialDialog(QFrame):
    def __init__(self, parent: QWidget, title: str, content_text: str = "", content_widget: QWidget = None):
        super().__init__(parent)
        self.setObjectName("materialDialogOverlay")
        self.bridge = MaterialQtBridge.get()
        theme = self.bridge.theme
        
        dialog_comp = AlertDialog("md3_dialog", props=DialogProps())
        self.resolved = dialog_comp.render(self.bridge.builder.context)["resolved_tokens"]
        
        self.setStyleSheet(f"""
            QFrame#materialDialogOverlay {{
                background-color: {theme.color_scheme.scrim}80;
            }}
        """)
        
        self.overlay_layout = QVBoxLayout(self)
        self.overlay_layout.setContentsMargins(0, 0, 0, 0)
        self.overlay_layout.setAlignment(Qt.AlignCenter)
        
        self.dialog_box = QFrame(self)
        self.dialog_box.setObjectName("dialogBox")
        
        bg_color = self.resolved.get("container_color", theme.color_scheme.surface)
        radius = int(self.resolved.get("shape", 28).top_start_dp) if hasattr(self.resolved.get("shape"), "top_start_dp") else 28
        
        self.dialog_box.setStyleSheet(f"""
            QFrame#dialogBox {{
                background-color: {bg_color};
                border: 1px solid {theme.color_scheme.outline};
                border-radius: {radius}px;
            }}
        """)
        
        padding = int(self.resolved.get("padding", 24))
        spacing = int(self.resolved.get("spacing", 24))
        button_spacing = int(self.resolved.get("button_spacing", 8))
        
        self.dialog_box.setMinimumWidth(320)
        self.dialog_box.setMaximumWidth(560)
        self.dialog_box.setSizePolicy(QSizePolicy.MinimumExpanding, QSizePolicy.Minimum)
        
        box_layout = QVBoxLayout(self.dialog_box)
        box_layout.setContentsMargins(padding, padding, padding, padding)
        box_layout.setSpacing(spacing)
        
        if title:
            title_comp = DialogTitle("dialog_title", TextProps(title))
            self.title_label = self.bridge.builder.build(title_comp, self.dialog_box)
            box_layout.addWidget(self.title_label)
            
        if content_text:
            content_comp = DialogSupportingText("dialog_content", TextProps(content_text))
            self.content_label = self.bridge.builder.build(content_comp, self.dialog_box)
            box_layout.addWidget(self.content_label)
            
        if content_widget:
            box_layout.addWidget(content_widget)
            
        self.actions_layout = QHBoxLayout()
        self.actions_layout.setContentsMargins(0, 0, 0, 0)
        self.actions_layout.setSpacing(button_spacing)
        self.actions_layout.addStretch()
        box_layout.addLayout(self.actions_layout)
        
        self.overlay_layout.addWidget(self.dialog_box)
        self.hide()
        
    def add_action(self, text: str, callback, is_primary: bool = False) -> MD3Button:
        btn = MD3Button(self.bridge, text, is_primary, self.dialog_box)
        if callback:
            btn.clicked.connect(callback)
        self.actions_layout.addWidget(btn)
        return btn
        
    def open(self):
        self.resize(self.parent().size())
        self.show()
        self.raise_()
        
    def close_dialog(self):
        self.hide()
        self.deleteLater()

    def resizeEvent(self, event):
        super().resizeEvent(event)
        if self.parent():
            self.resize(self.parent().size())
            
    def mousePressEvent(self, event: QMouseEvent):
        if not self.dialog_box.geometry().contains(event.pos()):
            self.close_dialog()
        else:
            super().mousePressEvent(event)

    def keyPressEvent(self, event: QKeyEvent):
        if event.key() == Qt.Key_Escape:
            self.close_dialog()
        else:
            super().keyPressEvent(event)
