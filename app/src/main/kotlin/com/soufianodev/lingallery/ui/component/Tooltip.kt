package com.soufianodev.lingallery.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.delay

private class TooltipPositionProvider(
    private val gapPx: Int,
    private val preferAbove: Boolean
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        return if (preferAbove) {
            IntOffset(
                anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
                anchorBounds.top - popupContentSize.height - gapPx
            )
        } else {
            IntOffset(
                anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
                anchorBounds.bottom + gapPx
            )
        }
    }
}

@Composable
fun TooltipIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    tint: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 40.dp,
    showDelayMs: Long = 400L,
    hideDelayMs: Long = 150L,
    preferTooltipAbove: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showTooltip by remember { mutableStateOf(false) }
    var tooltipHovered by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(showDelayMs)
            showTooltip = true
        } else {
            delay(hideDelayMs)
            showTooltip = false
        }
    }

    Box {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = modifier.size(buttonSize)
        ) {
            Icon(imageVector = icon, contentDescription = tooltip, tint = tint)
        }

        if (showTooltip && tooltip.isNotEmpty()) {
            Popup(
                popupPositionProvider = TooltipPositionProvider(
                    gapPx = with(LocalDensity.current) { 4.dp.toPx().toInt() },
                    preferAbove = preferTooltipAbove
                ),
                onDismissRequest = {},
                properties = PopupProperties(focusable = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    tonalElevation = 8.dp,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = tooltip,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
