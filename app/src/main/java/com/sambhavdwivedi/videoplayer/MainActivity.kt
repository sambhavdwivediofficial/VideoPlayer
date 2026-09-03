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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sambhavdwivedi.videoplayer.data.VideoDeleteUtil
import com.sambhavdwivedi.videoplayer.model.Video
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
    val context = LocalContext.current
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadVideos()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.removeDeletedVideos(pendingDeleteIds)
        }
        pendingDeleteIds = emptySet()
    }

    LaunchedEffect(Unit) {
        viewModel.loadVideos()
        permissionLauncher.launch(requiredPermissions)
    }

    BackHandler(enabled = showPlayer) {
        showPlayer = false
    }

    if (showPlayer) {
        NowPlayingVideoScreen(viewModel = viewModel, onBack = { showPlayer = false })
    } else {
        Scaffold(containerColor = Color(0xFF080808)) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                VideoGridScreen(
                    viewModel = viewModel,
                    onVideoOpen = { showPlayer = true },
                    onDeleteVideos = { toDelete ->
                        pendingDeleteIds = toDelete.map { it.id }.toSet()
                        VideoDeleteUtil.requestDelete(
                            context = context,
                            uris = toDelete.map { it.uri },
                            launchIntentSender = { request -> deleteLauncher.launch(request) },
                            onImmediateSuccess = {
                                viewModel.removeDeletedVideos(pendingDeleteIds)
                                pendingDeleteIds = emptySet()
                            }
                        )
                    }
                )
            }
        }
    }
}
