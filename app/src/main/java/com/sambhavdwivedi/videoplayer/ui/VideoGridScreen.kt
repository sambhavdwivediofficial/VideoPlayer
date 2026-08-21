package com.sambhavdwivedi.videoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.sambhavdwivedi.videoplayer.model.Video

@Composable
fun VideoGridScreen(viewModel: VideoViewModel, onVideoOpen: () -> Unit) {
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(text = "Videos", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
                        Text("No videos found", color = Color(0xFF9C9C9E))
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
                                onClick = {
                                    viewModel.playVideo(video)
                                    onVideoOpen()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: Video, imageLoader: ImageLoader, onClick: () -> Unit) {
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
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = video.thumbnailUri,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
