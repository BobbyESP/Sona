/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.data

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import com.bobbyesp.sona.messaging.domain.CameraCaptures
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The camera's pictures for messages, in the cache: sending copies one like any picked file. Those
 * older than a day are removed when a new one is made.
 */
class AndroidCameraCaptures(private val context: Context, private val io: CoroutineDispatcher) :
    CameraCaptures {

    override suspend fun newCapture(): String? =
        withContext(io) {
            try {
                val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
                val now = System.currentTimeMillis()
                folder
                    .listFiles()
                    ?.filter { now - it.lastModified() > DAY }
                    ?.forEach { it.delete() }
                val file = File(folder, "IMG_$now.jpg").apply { createNewFile() }
                FileProvider.getUriForFile(
                        context,
                        AndroidAttachmentFiles.authority(context),
                        file,
                    )
                    .toString()
            } catch (e: IOException) {
                Log.w(TAG, "No place for a capture", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "No place for a capture", e)
                null
            }
        }

    private companion object {
        const val TAG = "CameraCaptures"
        const val FOLDER = "message-captures"
        const val DAY = 24L * 60 * 60 * 1000
    }
}
