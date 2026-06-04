from ..core.component import UIComponent, StatelessComponent
from ..core.context import RenderContext
from ..core.slots import SlotRegistry

from .buttons import FilledButton, ButtonProps
from .cards import ElevatedCard, CardProps
from .dialogs import AlertDialog, DialogProps
from .chips import FilterChip, ChipProps

class SimpleText(StatelessComponent):
    def __init__(self, text: str):
        super().__init__("text")
        self.text = text
        
    def render(self, context: RenderContext) -> dict:
        return {"type": "Text", "text": self.text}

class IconPlaceholder(StatelessComponent):
    def render(self, context: RenderContext) -> dict:
        return {"type": "Icon"}

def build_example_tree() -> UIComponent:
    """
    Builds a complex, composed Material Design 3 UI tree without any backend rendering logic.
    """
    
    # 1. Create a Filter Bar (Chips)
    chip1 = FilterChip("chip_recent", ChipProps(label="Recent", selected=True))
    chip2 = FilterChip("chip_favorites", ChipProps(label="Favorites", selected=False))
    
    # 2. Create an Action Button for the Card
    action_slots = SlotRegistry()
    action_slots.fill("icon", IconPlaceholder("icon1"))
    
    btn = FilledButton(
        "btn_save", 
        ButtonProps(label="Save Profile", on_click=None),
        slots=action_slots
    )
    
    # 3. Create a Card with Content and Action
    card_slots = SlotRegistry()
    card_slots.fill("content", SimpleText("User Profile Information"))
    card_slots.fill("footer", btn)
    
    card = ElevatedCard("profile_card", CardProps(), slots=card_slots)
    
    # 4. Wrap everything in a Dialog
    dialog_slots = SlotRegistry()
    dialog_slots.fill("title", SimpleText("Profile Settings"))
    dialog_slots.fill("content", card)
    
    # Normally we'd use a generic Layout component here, but this is a pure tree example
    # We'll just pass the card as the main content slot of the dialog.
    dialog = AlertDialog(
        "settings_dialog",
        DialogProps(dismissible=True),
        slots=dialog_slots
    )
    
    return dialog
