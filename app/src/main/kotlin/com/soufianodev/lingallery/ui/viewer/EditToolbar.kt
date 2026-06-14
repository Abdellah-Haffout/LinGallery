package com.soufianodev.lingallery.ui.viewer

import androidx.compose.foundation.BorderStroke
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
    var showCopyMenu by remember { mutableStateOf(false) }
    var showTransferMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color(0xD9141D1E),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier
            .wrapContentSize()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TooltipIconButton(icon = AppIcons.RotateLeft, tooltip = "Rotate Left (L)", onClick = onRotateLeft, tint = Color(0xFFE2E8F0), enabled = isEditableFormat)
            TooltipIconButton(icon = AppIcons.RotateRight, tooltip = "Rotate Right (R)", onClick = onRotateRight, tint = Color(0xFFE2E8F0), enabled = isEditableFormat)
            TooltipIconButton(icon = AppIcons.Flip, tooltip = "Flip Horizontal", onClick = onFlipH, tint = Color(0xFFE2E8F0), enabled = isEditableFormat)

            VerticalDivider(
                modifier = Modifier.height(20.dp).padding(horizontal = 4.dp),
                color = Color.White.copy(alpha = 0.12f)
            )

            TooltipIconButton(icon = AppIcons.Crop, tooltip = "Crop (C)", onClick = onCrop, tint = Color(0xFFE2E8F0), enabled = isEditableFormat)

            VerticalDivider(
                modifier = Modifier.height(20.dp).padding(horizontal = 4.dp),
                color = Color.White.copy(alpha = 0.12f)
            )

            // Copy button with dropdown
            Box {
                TooltipIconButton(icon = AppIcons.ContentCopy, tooltip = "Copy image", onClick = { showCopyMenu = !showCopyMenu }, tint = Color(0xFFE2E8F0))
                DropdownMenu(
                    expanded = showCopyMenu,
                    onDismissRequest = { showCopyMenu = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(text = { Text("Copy image to clipboard", style = MaterialTheme.typography.bodyMedium) }, onClick = { showCopyMenu = false; onCopyClipboard() })
                    DropdownMenuItem(text = { Text("Copy name to clipboard", style = MaterialTheme.typography.bodyMedium) }, onClick = { showCopyMenu = false; onCopyName() })
                    DropdownMenuItem(text = { Text("Copy path to clipboard", style = MaterialTheme.typography.bodyMedium) }, onClick = { showCopyMenu = false; onCopyPath() })
                }
            }

            // Transfer button with dropdown
            Box {
                TooltipIconButton(icon = AppIcons.Transfer, tooltip = "Transfer image", onClick = { showTransferMenu = !showTransferMenu }, tint = Color(0xFFE2E8F0))
                DropdownMenu(
                    expanded = showTransferMenu,
                    onDismissRequest = { showTransferMenu = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(text = { Text("Move image to another folder", style = MaterialTheme.typography.bodyMedium) }, onClick = { showTransferMenu = false; onMove() })
                    DropdownMenuItem(text = { Text("Copy image to another folder", style = MaterialTheme.typography.bodyMedium) }, onClick = { showTransferMenu = false; onCopyFile() })
                }
            }

            TooltipIconButton(icon = AppIcons.Edit, tooltip = "Rename", onClick = onRename, tint = Color(0xFFE2E8F0))

            VerticalDivider(
                modifier = Modifier.height(20.dp).padding(horizontal = 4.dp),
                color = Color.White.copy(alpha = 0.12f)
            )

            TooltipIconButton(icon = AppIcons.Info, tooltip = "Image Info (I)", onClick = onInfo, tint = Color(0xFFE2E8F0))
            TooltipIconButton(icon = AppIcons.Delete, tooltip = "Delete (Del)", onClick = onDelete, tint = Color(0xFFE2E8F0))
        }
    }
}


