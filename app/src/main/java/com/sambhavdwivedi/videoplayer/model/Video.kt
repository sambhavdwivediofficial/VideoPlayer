package com.sambhavdwivedi.videoplayer.model

import android.net.Uri

data class Video(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val uri: Uri,
    val thumbnailUri: Uri,
    val folderName: String
)
