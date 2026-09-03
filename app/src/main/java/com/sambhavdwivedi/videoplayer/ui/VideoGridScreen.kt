package com.sambhavdwivedi.videoplayer.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sambhavdwivedi.videoplayer.model.Video
import java.util.Date

@Composable
fun VideoGridScreen(
    viewModel: VideoViewModel,
    onVideoOpen: () -> Unit,
    onDeleteVideos: (List<Video>) -> Unit
) {
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()

    val context = LocalContext.current
    val imageLoader = remember { ThumbnailImageLoader.get(context) }

    var searchActive by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var infoVideo by remember { mutableStateOf<Video?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                            }
                            Text("${selectedIds.size} selected", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        Row {
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Filled.SelectAll, contentDescription = "Select all", tint = Color.White)
                            }
                            if (selectedIds.size == 1) {
                                IconButton(onClick = {
                                    infoVideo = videos.firstOrNull { it.id == selectedIds.first() }
                                }) {
                                    Icon(Icons.Filled.Info, contentDescription = "Info", tint = Color.White)
                                }
                            }
                            IconButton(onClick = {
                                val selected = videos.filter { it.id in selectedIds }
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "video/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selected.map { it.uri }))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share videos"))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                            }
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                            }
                        }
                    }
                } else if (searchActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search", tint = Color.White)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search videos", color = Color(0xFF6E6E70), fontSize = 16.sp)
                                }
                                inner()
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Videos", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                        Row {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
                            }
                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }) {
                                    Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    val options = listOf(
                                        SortMode.DATE_ADDED to "Date added",
                                        SortMode.NAME to "Name",
                                        SortMode.DURATION to "Duration",
                                        SortMode.SIZE to "Size"
                                    )
                                    options.forEach { (mode, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, fontWeight = if (mode == sortMode) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                viewModel.setSortMode(mode)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color.Black
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val columns = if (maxWidth > 600.dp) 4 else 2

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                videos.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No results" else "No videos found",
                            color = Color(0xFF9C9C9E)
                        )
                    }
                }
                else -> {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentPadding = PaddingValues(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalItemSpacing = 6.dp
                    ) {
                        items(videos, key = { it.id }) { video ->
                            VideoThumbnail(
                                video = video,
                                imageLoader = imageLoader,
                                isSelectionMode = isSelectionMode,
                                isSelected = video.id in selectedIds,
                                onTap = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(video.id)
                                    } else {
                                        viewModel.playVideo(video)
                                        onVideoOpen()
                                    }
                                },
                                onLongPress = {
                                    if (!isSelectionMode) viewModel.enterSelectionMode(video.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    infoVideo?.let { v ->
        AlertDialog(
            onDismissRequest = { infoVideo = null },
            containerColor = Color(0xFF141416),
            title = { Text(v.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    InfoRow("Resolution", "${v.width} x ${v.height}")
                    InfoRow("Duration", formatDuration(v.durationMs))
                    InfoRow("Size", formatSize(v.sizeBytes))
                    InfoRow("Folder", v.folderName)
                    InfoRow("Added", formatDate(v.dateAdded))
                }
            },
            confirmButton = {
                Text(
                    "Close",
                    color = Color.White,
                    modifier = Modifier
                        .padding(12.dp)
                        .pointerInput(Unit) { detectTapGestures { infoVideo = null } }
                )
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color(0xFF141416),
            title = { Text("Delete ${selectedIds.size} video(s)?", color = Color.White) },
            text = { Text("This removes them from your device. This can't be undone.", color = Color(0xFFB0B0B0)) },
            confirmButton = {
                Text(
                    "Delete",
                    color = Color(0xFFFF6B6B),
                    modifier = Modifier
                        .padding(12.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                val toDelete = videos.filter { it.id in selectedIds }
                                onDeleteVideos(toDelete)
                                confirmDelete = false
                            }
                        }
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    color = Color.White,
                    modifier = Modifier
                        .padding(12.dp)
                        .pointerInput(Unit) { detectTapGestures { confirmDelete = false } }
                )
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF9C9C9E), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun VideoThumbnail(
    video: Video,
    imageLoader: coil.ImageLoader,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val ratio = if (video.width > 0 && video.height > 0) {
        video.width.toFloat() / video.height.toFloat()
    } else {
        9f / 16f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.4f, 2.5f))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141414))
            .then(
                if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier
            )
            .pointerInput(video.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        AsyncImage(
            model = video.thumbnailUri,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color(0x99000000))
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) Color.Black else Color.White,
                    modifier = Modifier.fillMaxSize().padding(1.dp)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

private fun formatDate(epochSeconds: Long): String {
    return DateFormat.format("d MMM yyyy", Date(epochSeconds * 1000)).toString()
}
