package com.sambhavdwivedi.videoplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.sambhavdwivedi.videoplayer.model.Video

class VideoRepository(private val context: Context) {

    fun getAllVideos(): List<Video> {
        val videos = mutableListOf<Video>()
        try {
            queryMediaStore(videos)
        } catch (_: SecurityException) {
        }
        return videos
    }

    private fun queryMediaStore(videos: MutableList<Video>) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection, projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown"
                val duration = cursor.getLong(durationCol)
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateAddedCol)
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)

                val videoUri = ContentUris.withAppendedId(collection, id)
                val thumbUri = ContentUris.withAppendedId(collection, id)

                videos.add(
                    Video(
                        id = id,
                        title = title,
                        durationMs = duration,
                        sizeBytes = size,
                        dateAdded = dateAdded,
                        width = width,
                        height = height,
                        uri = videoUri,
                        thumbnailUri = thumbUri
                    )
                )
            }
        }
    }
}
