package com.soufianodev.lingallery.ui.viewer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.soufianodev.lingallery.theme.AppConst
import com.soufianodev.lingallery.theme.AppIcons
import com.soufianodev.lingallery.ui.components.TooltipIconButton
import com.soufianodev.lingallery.theme.DarkPalette
import com.soufianodev.lingallery.theme.LightPalette

@Composable
fun EditToolbar(
    isEditableFormat: Boolean,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipH: () -> Unit,
    onCrop: () -> Unit,
    onCopyClipboard: () -> Unit,
    onCopyName: () -> Unit,
    onCopyPath: () -> Unit,
    onMove: () -> Unit,
    onCopyFile: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean
) {
    val onSurface = if (isDark) DarkPalette.ON_SURFACE else LightPalette.ON_SURFACE
    val surface = if (isDark) DarkPalette.SURFACE else LightPalette.SURFACE
    val outlineVariant = if (isDark) DarkPalette.OUTLINE_VARIANT else LightPalette.OUTLINE_VARIANT
    var showCopyMenu by remember { mutableStateOf(false) }
    var showTransferMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().height(AppConst.BOTTOM_BAR_HEIGHT.dp),
        color = surface
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))

            TooltipIconButton(icon = AppIcons.RotateLeft, tooltip = "Rotate Left (L)", onClick = onRotateLeft, tint = onSurface, enabled = isEditableFormat)
            TooltipIconButton(icon = AppIcons.RotateRight, tooltip = "Rotate Right (R)", onClick = onRotateRight, tint = onSurface, enabled = isEditableFormat)
            TooltipIconButton(icon = AppIcons.Flip, tooltip = "Flip Horizontal", onClick = onFlipH, tint = onSurface, enabled = isEditableFormat)

            Spacer(Modifier.width(16.dp))

            TooltipIconButton(icon = AppIcons.Crop, tooltip = "Crop (C)", onClick = onCrop, tint = onSurface, enabled = isEditableFormat)

            Spacer(Modifier.width(24.dp))

            // Copy button with dropdown
            Box {
                TooltipIconButton(icon = AppIcons.ContentCopy, tooltip = "Copy image", onClick = { showCopyMenu = !showCopyMenu }, tint = onSurface)
                DropdownMenu(
                    expanded = showCopyMenu,
                    onDismissRequest = { showCopyMenu = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(text = { Text("Copy image to clipboard", fontSize = 13.sp) }, onClick = { showCopyMenu = false; onCopyClipboard() })
                    DropdownMenuItem(text = { Text("Copy image name to clipboard", fontSize = 13.sp) }, onClick = { showCopyMenu = false; onCopyName() })
                    DropdownMenuItem(text = { Text("Copy image path to clipboard", fontSize = 13.sp) }, onClick = { showCopyMenu = false; onCopyPath() })
                }
            }

            // Transfer button with dropdown
            Box {
                TooltipIconButton(icon = AppIcons.Transfer, tooltip = "Transfer image", onClick = { showTransferMenu = !showTransferMenu }, tint = onSurface)
                DropdownMenu(
                    expanded = showTransferMenu,
                    onDismissRequest = { showTransferMenu = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(text = { Text("Move image to another folder", fontSize = 13.sp) }, onClick = { showTransferMenu = false; onMove() })
                    DropdownMenuItem(text = { Text("Copy image to another folder", fontSize = 13.sp) }, onClick = { showTransferMenu = false; onCopyFile() })
                }
            }

            TooltipIconButton(icon = AppIcons.Edit, tooltip = "Rename", onClick = onRename, tint = onSurface)

            Spacer(Modifier.width(24.dp))

            TooltipIconButton(icon = AppIcons.Info, tooltip = "Image Info (I)", onClick = onInfo, tint = onSurface)
            TooltipIconButton(icon = AppIcons.Delete, tooltip = "Delete (Del)", onClick = onDelete, tint = onSurface)

            Spacer(Modifier.width(24.dp))

            Spacer(Modifier.weight(1f))
        }
    }
}


