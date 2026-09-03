package com.sambhavdwivedi.videoplayer.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class SeekFeedback(val forward: Boolean, val stamp: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingVideoScreen(viewModel: VideoViewModel, onBack: () -> Unit) {
    val video by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val hasNext by viewModel.hasNext.collectAsState()
    val hasPrevious by viewModel.hasPrevious.collectAsState()
    val allVideos by viewModel.videos.collectAsState()

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var isLocked by rememberSaveable { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var resizeMode by rememberSaveable { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }

    var showVolumeHud by remember { mutableStateOf(false) }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }

    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val coroutineScope = rememberCoroutineScope()
    val swipeOffsetX = remember { Animatable(0f) }

    val previewImageLoader = remember { ThumbnailImageLoader.get(context) }

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pausePlayback()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPlayback()
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isLocked) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(650)
            seekFeedback = null
        }
    }

    BackHandler { onBack() }

    val currentVideo = video ?: return
    val currentIndex = allVideos.indexOfFirst { it.id == currentVideo.id }
    val nextVideo = allVideos.getOrNull(currentIndex + 1)
    val previousVideo = allVideos.getOrNull(currentIndex - 1)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = constraints.maxWidth.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isLocked) Modifier.pointerInput(currentVideo.id) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> {
                                        viewModel.seekBy(-10_000)
                                        seekFeedback = SeekFeedback(false, System.currentTimeMillis())
                                    }
                                    offset.x > third * 2 -> {
                                        viewModel.seekBy(10_000)
                                        seekFeedback = SeekFeedback(true, System.currentTimeMillis())
                                    }
                                    else -> viewModel.togglePlayPause()
                                }
                            }
                        )
                    } else Modifier
                )
                .then(
                    if (!isLocked) Modifier.pointerInput(currentVideo.id) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                brightnessLevel = (activity?.window?.attributes?.screenBrightness ?: 0.5f)
                                    .let { if (it < 0f) 0.5f else it }
                            },
                            onDragEnd = { showVolumeHud = false; showBrightnessHud = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val isRightSide = change.position.x > size.width / 2
                                val delta = -dragAmount / size.height.toFloat()
                                if (isRightSide) {
                                    showBrightnessHud = false
                                    showVolumeHud = true
                                    volumeLevel = (volumeLevel + delta * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeLevel.toInt(), 0)
                                } else {
                                    showVolumeHud = false
                                    showBrightnessHud = true
                                    brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                                    activity?.window?.attributes = activity?.window?.attributes?.apply {
                                        screenBrightness = brightnessLevel
                                    }
                                }
                            }
                        )
                    } else Modifier
                )
                .then(
                    if (!isLocked) Modifier.pointerInput(currentVideo.id, hasNext, hasPrevious, widthPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val threshold = widthPx * 0.22f
                                val current = swipeOffsetX.value
                                coroutineScope.launch {
                                    when {
                                        current <= -threshold && hasNext -> {
                                            swipeOffsetX.animateTo(-widthPx, tween(200))
                                            viewModel.playNext()
                                            swipeOffsetX.snapTo(0f)
                                        }
                                        current >= threshold && hasPrevious -> {
                                            swipeOffsetX.animateTo(widthPx, tween(200))
                                            viewModel.playPrevious()
                                            swipeOffsetX.snapTo(0f)
                                        }
                                        else -> swipeOffsetX.animateTo(0f, tween(220))
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch { swipeOffsetX.animateTo(0f, tween(220)) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                var newValue = swipeOffsetX.value + dragAmount
                                if (newValue < 0f && !hasNext) newValue = 0f
                                if (newValue > 0f && !hasPrevious) newValue = 0f
                                coroutineScope.launch { swipeOffsetX.snapTo(newValue) }
                            }
                        )
                    } else Modifier
                )
        ) {
            if (previousVideo != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset((swipeOffsetX.value - widthPx).roundToInt(), 0) }
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = previousVideo.thumbnailUri,
                        imageLoader = previewImageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
            ) {
                AndroidView(
                    factory = { PlayerView(it).apply { player = viewModel.player; useController = false } },
                    update = { it.resizeMode = resizeMode },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (nextVideo != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset((swipeOffsetX.value + widthPx).roundToInt(), 0) }
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = nextVideo.thumbnailUri,
                        imageLoader = previewImageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.Center).size(42.dp)
                )
            }

            if (showVolumeHud) {
                GestureHud(icon = Icons.Filled.VolumeUp, percent = volumeLevel / maxVolume.toFloat(), modifier = Modifier.align(Alignment.Center))
            }
            if (showBrightnessHud) {
                GestureHud(icon = Icons.Filled.BrightnessMedium, percent = brightnessLevel, modifier = Modifier.align(Alignment.Center))
            }

            seekFeedback?.let { fb ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f),
                    modifier = Modifier
                        .align(if (fb.forward) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 40.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC000000))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (fb.forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Text("10s", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            IconButton(
                onClick = { isLocked = !isLocked },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = "Lock",
                    tint = Color.White.copy(alpha = if (isLocked) 1f else 0.5f)
                )
            }

            if (controlsVisible && !isLocked) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            text = currentVideo.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp).weight(1f)
                        )
                        IconButton(onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }) {
                            Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect ratio", tint = Color.White)
                        }
                        IconButton(onClick = { showSpeedSheet = true }) {
                            Icon(Icons.Filled.Speed, contentDescription = "Speed", tint = Color.White)
                        }
                        IconButton(onClick = {
                            activity?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
                        }) {
                            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.playPrevious() }, enabled = hasPrevious) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = if (hasPrevious) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = {
                            viewModel.seekBy(-10_000)
                            seekFeedback = SeekFeedback(false, System.currentTimeMillis())
                        }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.padding(horizontal = 10.dp))
                        IconButton(onClick = { viewModel.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(Modifier.padding(horizontal = 10.dp))
                        IconButton(onClick = {
                            viewModel.seekBy(10_000)
                            seekFeedback = SeekFeedback(true, System.currentTimeMillis())
                        }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = { viewModel.playNext() }, enabled = hasNext) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = if (hasNext) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(30.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val sliderValue = if (isDragging) dragPosition
                    else if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()) else 0f

                    Slider(
                        value = sliderValue.coerceIn(0f, 1f),
                        onValueChange = { isDragging = true; dragPosition = it },
                        onValueChangeFinished = {
                            viewModel.seekTo((dragPosition * durationMs).toLong())
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF3A3A3C)
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = formatMs(if (isDragging) (dragPosition * durationMs).toLong() else positionMs), color = Color.White, fontSize = 12.sp)
                        Text(text = formatMs(durationMs), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showSpeedSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF0A0A0C),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier.padding(bottom = 28.dp, top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Playback speed",
                    color = Color(0xFFB0B0B0),
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "${"%.1f".format(playbackSpeed)}x",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF8A8A8A),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.height(10.dp))

                SpeedRuler(
                    speed = playbackSpeed,
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) }
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0.5f, 0.8f, 1.0f, 1.5f, 2.0f).forEach { preset ->
                        val isSelected = kotlin.math.abs(playbackSpeed - preset) < 0.02f
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF3A3A3C),
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setPlaybackSpeed(preset) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "%.1f".format(preset),
                                color = if (isSelected) Color.White else Color(0xFFB0B0B0),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedRuler(speed: Float, onSpeedChange: (Float) -> Unit) {
    val density = LocalDensity.current
    val pxPerUnit = with(density) { 200.dp.toPx() }

    val currentSpeed by rememberUpdatedState(speed)
    val currentOnSpeedChange by rememberUpdatedState(onSpeedChange)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val deltaSpeed = -dragAmount / pxPerUnit
                        val newSpeed = (currentSpeed + deltaSpeed).coerceIn(0.25f, 2.5f)
                        val rounded = (kotlin.math.round(newSpeed / 0.05f) * 0.05f)
                        currentOnSpeedChange(rounded)
                    }
                )
            }
    ) {
        val centerX = size.width / 2f
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(160, 255, 255, 255)
            textSize = with(density) { 13.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
        }

        for (i in 5..60) {
            val v = i * 0.05f
            val x = centerX + (v - speed) * pxPerUnit
            if (x in -50f..size.width + 50f) {
                val isMajor = i % 10 == 0
                val isMid = i % 2 == 0

                val topStart = size.height * 0.1f
                val lineHeight = when {
                    isMajor -> size.height * 0.55f
                    isMid -> size.height * 0.35f
                    else -> size.height * 0.2f
                }
                drawLine(
                    color = if (isMajor) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.3f),
                    start = Offset(x, topStart),
                    end = Offset(x, topStart + lineHeight),
                    strokeWidth = if (isMajor) 3f else 1.5f
                )
                if (isMajor) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.1f".format(v),
                        x,
                        topStart + lineHeight + 26f,
                        textPaint
                    )
                }
            }
        }
        drawLine(
            color = Color.White,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height * 0.62f),
            strokeWidth = 4f
        )
    }
}

@Composable
private fun GestureHud(icon: androidx.compose.ui.graphics.vector.ImageVector, percent: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(text = "${(percent * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
