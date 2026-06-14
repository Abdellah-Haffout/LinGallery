package com.soufianodev.lingallery.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soufianodev.lingallery.ui.components.stablePointerHoverIcon
import com.soufianodev.lingallery.data.Album
import com.soufianodev.lingallery.theme.AppConst

@Composable
fun PenguinLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Colors
        val bodyColor = Color(0xFF1E293B) // Slate 800
        val bellyColor = Color.White
        val beakColor = Color(0xFFFBBF24) // Yellow 400
        val eyeColor = Color(0xFF0F172A) // Slate 900

        // Draw feet (orange ovals at the bottom)
        drawOval(
            color = beakColor,
            topLeft = Offset(w * 0.22f, h * 0.8f),
            size = Size(w * 0.24f, h * 0.12f)
        )
        drawOval(
            color = beakColor,
            topLeft = Offset(w * 0.54f, h * 0.8f),
            size = Size(w * 0.24f, h * 0.12f)
        )

        // Draw arms/flippers on sides
        drawOval(
            color = bodyColor,
            topLeft = Offset(w * 0.06f, h * 0.35f),
            size = Size(w * 0.16f, h * 0.35f)
        )
        drawOval(
            color = bodyColor,
            topLeft = Offset(w * 0.78f, h * 0.35f),
            size = Size(w * 0.16f, h * 0.35f)
        )

        // Main body oval
        drawOval(
            color = bodyColor,
            topLeft = Offset(w * 0.18f, h * 0.12f),
            size = Size(w * 0.64f, h * 0.72f)
        )

        // White belly/face area
        drawOval(
            color = bellyColor,
            topLeft = Offset(w * 0.26f, h * 0.28f),
            size = Size(w * 0.48f, h * 0.52f)
        )
        // Upper eye mask whites
        drawOval(
            color = bellyColor,
            topLeft = Offset(w * 0.28f, h * 0.22f),
            size = Size(w * 0.24f, h * 0.24f)
        )
        drawOval(
            color = bellyColor,
            topLeft = Offset(w * 0.48f, h * 0.22f),
            size = Size(w * 0.24f, h * 0.24f)
        )

        // Eyes (pupils)
        drawOval(
            color = eyeColor,
            topLeft = Offset(w * 0.38f, h * 0.3f),
            size = Size(w * 0.08f, h * 0.08f)
        )
        drawOval(
            color = eyeColor,
            topLeft = Offset(w * 0.54f, h * 0.3f),
            size = Size(w * 0.08f, h * 0.08f)
        )
        
        // Small white eye highlights
        drawOval(
            color = Color.White,
            topLeft = Offset(w * 0.41f, h * 0.31f),
            size = Size(w * 0.03f, h * 0.03f)
        )
        drawOval(
            color = Color.White,
            topLeft = Offset(w * 0.57f, h * 0.31f),
            size = Size(w * 0.03f, h * 0.03f)
        )

        // Beak (pointing downwards)
        val beakPath = Path().apply {
            moveTo(w * 0.5f, h * 0.36f)
            lineTo(w * 0.43f, h * 0.46f)
            lineTo(w * 0.57f, h * 0.46f)
            close()
        }
        drawPath(
            path = beakPath,
            color = beakColor
        )
    }
}

@Composable
fun AlbumPanel(
    albums: List<Album>,
    currentAlbumIndex: Int,
    onAlbumSelected: (Int) -> Unit,
    onAddAlbumClicked: () -> Unit,
    onMockItemClicked: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    PermanentDrawerSheet(
        modifier = modifier.width(AppConst.SIDEBAR_WIDTH.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) {
        // Logo and Title Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PenguinLogo(modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "LinGallery",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00BCD4) // Cyan / primary matching screenshot
                    ),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Pro Media Manager",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
            }
        }

        // "+ Add Album" Button
        Button(
            onClick = onAddAlbumClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF26C6DA), // Cyan matching screenshot
                contentColor = Color(0xFF0F172A) // Dark slate
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp)
                .stablePointerHoverIcon(PointerIcon.Hand)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add Album",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val scrollbarStyle = ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = 6.dp,
            shape = RoundedCornerShape(3.dp),
            hoverDurationMillis = 100,
            unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        val albumListState = rememberLazyListState()

        Box(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                state = albumListState,
                horizontalAlignment = Alignment.Start
            ) {
                // --- LIBRARY SECTION ---
                item {
                    Text(
                        text = "LIBRARY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }

                // Render standard library categories
                // 1. Pictures
                val picturesIdx = albums.indexOfFirst { it.name.equals("pictures", ignoreCase = true) }
                val picturesSelected = currentAlbumIndex == picturesIdx && picturesIdx >= 0
                item {
                    NavigationDrawerItem(
                        label = { Text("Pictures") },
                        selected = picturesSelected,
                        onClick = {
                            if (picturesIdx >= 0) onAlbumSelected(picturesIdx)
                        },
                        icon = {
                            Icon(
                                imageVector = if (picturesSelected) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .stablePointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(100.dp)
                    )
                }

                // 2. Screenshots
                val screenshotsIdx = albums.indexOfFirst { it.name.equals("screenshots", ignoreCase = true) }
                val screenshotsSelected = currentAlbumIndex == screenshotsIdx && screenshotsIdx >= 0
                item {
                    NavigationDrawerItem(
                        label = { Text("Screenshots") },
                        selected = screenshotsSelected,
                        onClick = {
                            if (screenshotsIdx >= 0) onAlbumSelected(screenshotsIdx)
                        },
                        icon = {
                            Icon(
                                imageVector = if (screenshotsSelected) Icons.Filled.Smartphone else Icons.Outlined.Smartphone,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .stablePointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(100.dp)
                    )
                }

                // 3. Downloads
                val downloadsIdx = albums.indexOfFirst { it.name.equals("downloads", ignoreCase = true) }
                val downloadsSelected = currentAlbumIndex == downloadsIdx && downloadsIdx >= 0
                item {
                    NavigationDrawerItem(
                        label = { Text("Downloads") },
                        selected = downloadsSelected,
                        onClick = {
                            if (downloadsIdx >= 0) onAlbumSelected(downloadsIdx)
                        },
                        icon = {
                            Icon(
                                imageVector = if (downloadsSelected) Icons.Filled.Download else Icons.Outlined.Download,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .stablePointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(100.dp)
                    )
                }

                // 4. Favorites (Mock)
                item {
                    NavigationDrawerItem(
                        label = { Text("Favorites") },
                        selected = false,
                        onClick = { onMockItemClicked("Favorites") },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .stablePointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(100.dp)
                    )
                }

                // 5. Recents (Mock)
                item {
                    NavigationDrawerItem(
                        label = { Text("Recents") },
                        selected = false,
                        onClick = { onMockItemClicked("Recents") },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.AccessTime,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .stablePointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(100.dp)
                    )
                }

                // 6. Trash (Mock)
                item {
                    NavigationDrawerItem(
                        label = { Text("Trash") },
                        selected = false,
                        onClick = { onMockItemClicked("Trash") },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .stablePointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(100.dp)
                    )
                }

                // --- COLLECTIONS SECTION ---
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "COLLECTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }

                // Render custom collections
                val libraryNames = listOf("pictures", "downloads", "screenshots")
                itemsIndexed(albums, key = { index, a -> "${index}_${a.path.toUri()}" }) { index, album ->
                    val isLibraryAlbum = album.name.lowercase() in libraryNames
                    if (!isLibraryAlbum) {
                        val isSelected = index == currentAlbumIndex
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = "${album.name}  (${album.imageCount})",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = isSelected,
                            onClick = { onAlbumSelected(index) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.Folder else Icons.Outlined.Folder,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .padding(vertical = 1.dp)
                                .stablePointerHoverIcon(PointerIcon.Hand),
                            shape = RoundedCornerShape(100.dp)
                        )
                    }
                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(albumListState),
                style = scrollbarStyle
            )
        }
    }
}
