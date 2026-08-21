package com.sambhavdwivedi.videoplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sambhavdwivedi.videoplayer.ui.NowPlayingVideoScreen
import com.sambhavdwivedi.videoplayer.ui.VideoGridScreen
import com.sambhavdwivedi.videoplayer.ui.VideoViewModel
import com.sambhavdwivedi.videoplayer.ui.theme.VideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(8, 8, 8)

        setContent {
            VideoPlayerTheme {
                VideoApp()
            }
        }
    }
}

@Composable
fun VideoApp() {
    val viewModel: VideoViewModel = viewModel()
    var hasPermission by remember { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        viewModel.loadVideos()
    }

    LaunchedEffect(Unit) {
        viewModel.loadVideos()
        launcher.launch(permission)
    }

    BackHandler(enabled = showPlayer) {
        showPlayer = false
    }

    if (showPlayer) {
        NowPlayingVideoScreen(viewModel = viewModel, onBack = { showPlayer = false })
    } else {
        Scaffold(containerColor = Color(0xFF080808)) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                VideoGridScreen(viewModel = viewModel, onVideoOpen = { showPlayer = true })
            }
        }
    }
}
