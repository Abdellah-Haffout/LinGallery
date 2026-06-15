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
import androidx.compose.ui.input.pointer.PointerIcon
import com.soufianodev.lingallery.ui.components.stablePointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import com.soufianodev.lingallery.i18n.Strings
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
    val surfaceContainer = if (isDark) DarkPalette.SURFACE_CONTAINER else LightPalette.SURFACE_CONTAINER
    val onSurfaceVariant = if (isDark) DarkPalette.ON_SURFACE_VARIANT else LightPalette.ON_SURFACE_VARIANT

    val scrollbarStyle = ScrollbarStyle(
        minimalHeight = 24.dp,
        thickness = 6.dp,
        shape = RoundedCornerShape(3.dp),
        hoverDurationMillis = 100,
        unhoverColor = onSurfaceVariant.copy(alpha = 0.2f),
        hoverColor = onSurfaceVariant.copy(alpha = 0.5f)
    )

    val gridState = rememberLazyGridState()

    if (images.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (hasAlbums) Strings.Gallery.empty
                       else Strings.Gallery.noAlbums,
                fontSize = 15.sp,
                color = onSurfaceVariant
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
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(surfaceContainer.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .stablePointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clickable { onImageClicked(index) }
                        ) {
                            AsyncImage(
                                request = ComposableImageRequest(image.path.toUri().toString() + "?t=${image.lastModified}") {
                                    crossfade()
                                },
                                contentDescription = image.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
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
