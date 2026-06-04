import re
from pathlib import Path

path = Path("/home/soufiano/SoufianoDev/PythonProjects/lingallery/src/ui/viewer/image_viewer.py")
content = path.read_text()

# 1. Imports
content = content.replace(
    "from pymaterial.components.dialogs import AlertDialog, DialogProps",
    "from ui.components.material_dialog import MaterialDialog, MD3TextField, MD3Button"
)

# 2. Init
old_init = """        self._view.crop_rect_selected.connect(self._crop_current)
        self._rename_overlay = _RenameOverlay(self)
        self._rename_overlay.rename_requested.connect(self._commit_rename)
        self._rename_overlay.hide()
        self._notice_overlay = _NoticeOverlay(self)
        self._notice_overlay.hide()
        self._crop_save_dialog = _CropSaveDialog(self)
        self._crop_save_dialog.save_requested.connect(self._execute_crop)
        self._crop_save_dialog.hide()

    # ─────────────────────────────────────────────────────────────────"""
new_init = """        self._view.crop_rect_selected.connect(self._crop_current)

    # ─────────────────────────────────────────────────────────────────"""
content = content.replace(old_init, new_init)

# 3. open_image
old_open_image = '            self._notice_overlay.open("Unable to open image", f"{Path(path).name}\\n\\n{message}")'
new_open_image = """            d = MaterialDialog(self, "Unable to open image", f"{Path(path).name}\\n\\n{message}")
            d.add_action("OK", d.close_dialog, is_primary=True)
            d.open()"""
content = content.replace(old_open_image, new_open_image)

# 4. _apply_crop
old_apply_crop = """    def _apply_crop(self):
        if not self._current_path or not self._pending_crop_rect:
            return
        self._crop_save_dialog.open()"""
new_apply_crop = """    def _apply_crop(self):
        if not self._current_path or not self._pending_crop_rect:
            return
        d = MaterialDialog(self, "Save Crop", "How do you want to save the cropped image?")
        d.add_action("Cancel", d.close_dialog)
        d.add_action("Overwrite", lambda: [d.close_dialog(), self._execute_crop("overwrite")])
        d.add_action("Save as Copy", lambda: [d.close_dialog(), self._execute_crop("copy")], is_primary=True)
        d.open()"""
content = content.replace(old_apply_crop, new_apply_crop)

# 5. _delete_image
old_delete_image = """    def _delete_image(self):
        if not self._current_path:
            return
        self._notice_overlay.open(
            "Delete image",
            f"Permanently delete {Path(self._current_path).name}?",
            confirm_text="Delete",
            on_confirm=self._perform_delete_current,
        )"""
new_delete_image = """    def _delete_image(self):
        if not self._current_path:
            return
        d = MaterialDialog(self, "Delete image", f"Permanently delete {Path(self._current_path).name}?")
        d.add_action("Cancel", d.close_dialog)
        d.add_action("Delete", lambda: [d.close_dialog(), self._perform_delete_current()], is_primary=True)
        d.open()"""
content = content.replace(old_delete_image, new_delete_image)

# 6. _rename_image
old_rename_image = """    def _rename_image(self):
        if not self._current_path:
            return
        old = Path(self._current_path)
        self._rename_overlay.open(old.name)"""
new_rename_image = """    def _rename_image(self):
        if not self._current_path:
            return
        old = Path(self._current_path)
        
        d = MaterialDialog(self, "Rename Image")
        tf = MD3TextField(d.bridge, "File Name", old.name)
        d.dialog_box.layout().insertWidget(1, tf)
        
        def _on_rename_submit():
            new_name = tf.text()
            d.close_dialog()
            if new_name:
                self._commit_rename(new_name)
                
        tf.returnPressed.connect(_on_rename_submit)
        d.add_action("Cancel", d.close_dialog)
        d.add_action("Rename", _on_rename_submit, is_primary=True)
        d.open()
        tf.selectAll()
        tf.setFocus()"""
content = content.replace(old_rename_image, new_rename_image)

# 7. _show_operation_failed
old_show_op_failed = """    def _show_operation_failed(self, message: str):
        self._notice_overlay.open("Image operation failed", message)"""
new_show_op_failed = """    def _show_operation_failed(self, message: str):
        d = MaterialDialog(self, "Image operation failed", message)
        d.add_action("OK", d.close_dialog, is_primary=True)
        d.open()"""
content = content.replace(old_show_op_failed, new_show_op_failed)

# 8. keyPressEvent
old_escape = """        elif k == Qt.Key_Escape:
            if self._crop_save_dialog.isVisible():
                self._crop_save_dialog.hide()
            elif self._crop_bar.isVisible():
                self._cancel_crop()
            elif self._notice_overlay.isVisible():
                self._notice_overlay.hide()
            elif self._rename_overlay.isVisible():
                self._rename_overlay.hide()
            elif self._is_fullscreen:
                self.toggle_fullscreen()
            else:
                self._go_back()"""
new_escape = """        elif k == Qt.Key_Escape:
            if self._crop_bar.isVisible():
                self._cancel_crop()
            elif self._is_fullscreen:
                self.toggle_fullscreen()
            else:
                self._go_back()"""
content = content.replace(old_escape, new_escape)

# 9. resizeEvent
old_resize = """    def resizeEvent(self, event):
        super().resizeEvent(event)
        if hasattr(self, "_rename_overlay"):
            self._rename_overlay.reposition()
        if hasattr(self, "_notice_overlay"):
            self._notice_overlay.reposition()
        if hasattr(self, "_crop_save_dialog"):
            self._crop_save_dialog.reposition()"""
new_resize = """    def resizeEvent(self, event):
        super().resizeEvent(event)"""
content = content.replace(old_resize, new_resize)

# 10. _CropActionBar
old_cropbar = """        self._message.setStyleSheet(
            f"color: {DarkPalette.ON_SURFACE}; font-size: 13px; font-weight: 600;"
        )
        layout.addWidget(self._message)
        layout.addStretch()
        layout.addWidget(_TextButton("Cancel", self.cancel_requested))
        layout.addWidget(_TextButton("Apply", self.apply_requested, filled=True))"""
new_cropbar = """        self._message.setStyleSheet(
            f"color: {DarkPalette.ON_SURFACE}; font-size: 13px; font-weight: 600;"
        )
        layout.addWidget(self._message)
        layout.addStretch()
        
        bridge = MaterialQtBridge()
        cancel_btn = MD3Button(bridge, "Cancel", False, self)
        cancel_btn.clicked.connect(self.cancel_requested.emit)
        layout.addWidget(cancel_btn)
        
        apply_btn = MD3Button(bridge, "Apply", True, self)
        apply_btn.clicked.connect(self.apply_requested.emit)
        layout.addWidget(apply_btn)"""
content = content.replace(old_cropbar, new_cropbar)

# 11. Remove old classes
pattern = re.compile(r"class _RenameOverlay\(QFrame\):.*?(?=\n# ─────────────────────────────────────────────────────────────────────\n# Custom Graphics View)", re.DOTALL)
content = pattern.sub("", content)

path.write_text(content)
print("Refactoring complete.")
