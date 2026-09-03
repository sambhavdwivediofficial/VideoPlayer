package com.sambhavdwivedi.videoplayer.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest

object VideoDeleteUtil {

    fun requestDelete(
        context: Context,
        uris: List<Uri>,
        launchIntentSender: (IntentSenderRequest) -> Unit,
        onImmediateSuccess: () -> Unit
    ) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(resolver, uris)
            launchIntentSender(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } else {
            try {
                uris.forEach { resolver.delete(it, null, null) }
                onImmediateSuccess()
            } catch (e: SecurityException) {
                val recoverable = e as? RecoverableSecurityException
                val intentSender = recoverable?.userAction?.actionIntent?.intentSender
                if (intentSender != null) {
                    launchIntentSender(IntentSenderRequest.Builder(intentSender).build())
                }
            }
        }
    }
}
