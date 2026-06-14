package com.soufianodev.lingallery.ui.gallery

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.PointerIcon
import com.soufianodev.lingallery.ui.components.stablePointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import com.soufianodev.lingallery.data.ImageFile
import com.soufianodev.lingallery.theme.DarkPalette
import com.soufianodev.lingallery.theme.LightPalette

@Composable
fun GalleryView(
    images: List<ImageFile>,
    onImageClicked: (Int) -> Unit,
    onImageDoubleClicked: (Int) -> Unit,
    isDark: Boolean,
    hasAlbums: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scrollbarStyle = ScrollbarStyle(
        minimalHeight = 24.dp,
        thickness = 6.dp,
        shape = RoundedCornerShape(3.dp),
        hoverDurationMillis = 100,
        unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
        hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )

    val gridState = rememberLazyGridState()

    if (images.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (hasAlbums) "Open a folder or select an album to browse images."
                       else "No albums found. Add a source folder to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    images,
                    key = { _, img -> "${img.path}_${img.lastModified}" },
                    contentType = { _, _ -> "thumbnail" }
                ) { index, image ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    val scale by animateFloatAsState(targetValue = if (isHovered) 1.05f else 1f, label = "hover_scale")
                    val elevation by animateDpAsState(targetValue = if (isHovered) 8.dp else 2.dp, label = "hover_elevation")

                    ElevatedCard(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .hoverable(interactionSource)
                            .clickable { onImageClicked(index) }
                            .stablePointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                request = ComposableImageRequest(image.path.toUri().toString() + "?t=${image.lastModified}") {
                                    crossfade()
                                },
                                contentDescription = image.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isHovered,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                            )
                                        )
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = image.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(gridState),
                style = scrollbarStyle
            )
        }
    }
}
