/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.di

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.view.Surface
import android.view.TextureView
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.sip.api.CallVideo
import com.bobbyesp.sona.sip.api.CameraFacing
import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.api.VideoSurfaces
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The demo flavor's video: test patterns painted on the call screen's views, so the video screen
 * runs without a camera or a server. One background thread paints about 30 times a second, and only
 * while a view is attached.
 */
internal class DemoVideoSurfaces(private val engine: SipEngine, scope: CoroutineScope) :
    VideoSurfaces {

    private data class Attached(
        val call: CallId,
        val remote: TextureView?,
        val preview: TextureView?,
    )

    private val attached = MutableStateFlow<Attached?>(null)

    init {
        val painter =
            Executors.newSingleThreadExecutor {
                    Thread(it, "sona-demo-video").apply { isDaemon = true }
                }
                .asCoroutineDispatcher()
        scope.launch(painter) { attached.collectLatest { now -> if (now != null) paint(now) } }
    }

    override fun attach(call: CallId, remote: Any?, preview: Any?) {
        val remoteView = remote as? TextureView
        val previewView = preview as? TextureView
        attached.value =
            if (remoteView == null && previewView == null) null
            else Attached(call, remoteView, previewView)
    }

    private suspend fun paint(target: Attached) {
        val remote = Target(target.remote)
        val preview = Target(target.preview)
        try {
            var frame = 0L
            while (currentCoroutineContext().isActive) {
                val call = engine.calls.value.firstOrNull { it.id == target.call }
                val video = call?.video
                remote.draw(if (video?.showsRemote == true) ::bars else null, frame)
                // As the Linphone engine: the preview runs while a view shows it, sent or not.
                preview.draw(if (call != null) ::waves else null, frame, video)
                frame++
                delay(FRAME_MS)
            }
        } finally {
            remote.release()
            preview.release()
        }
    }

    private fun bars(canvas: Canvas, frame: Long, @Suppress("UNUSED_PARAMETER") video: CallVideo?) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val bar = width / BARS.size
        BARS.forEachIndexed { index, color ->
            paint.color = color
            canvas.drawRect(index * bar, 0f, (index + 1) * bar, height * 0.75f, paint)
        }
        paint.color = Color.rgb(16, 16, 24)
        canvas.drawRect(0f, height * 0.75f, width, height, paint)

        val sweep = (frame * 6 % width.toLong().coerceAtLeast(1)).toFloat()
        paint.color = Color.WHITE
        canvas.drawRect(sweep, 0f, sweep + width / 80f, height * 0.75f, paint)

        val radius = minOf(width, height) / 12f
        val span = (width - 2 * radius).coerceAtLeast(1f)
        val travelled = frame * 5f % (2 * span)
        val x = radius + span - abs(travelled - span)
        val y = height * 0.375f + sin(frame / 9.0).toFloat() * height * 0.2f
        paint.color = Color.rgb(250, 250, 250)
        canvas.drawCircle(x, y, radius, paint)

        val seconds = frame / FPS
        text.textSize = height / 24f
        canvas.drawText(
            String.format(Locale.ROOT, "%02d:%02d.%02d", seconds / 60, seconds % 60, frame % FPS),
            width / 2f,
            height * 0.8f,
            text,
        )
    }

    /** Tinted by which camera is "on". */
    private fun waves(canvas: Canvas, frame: Long, video: CallVideo?) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val back = video?.camera == CameraFacing.Back
        canvas.drawColor(if (back) Color.rgb(40, 24, 64) else Color.rgb(0, 60, 62))
        val stripe = maxOf(width, height) / 10f
        val shift = frame * 3 % (2 * stripe)
        paint.color = if (back) Color.rgb(120, 80, 200) else Color.rgb(0, 150, 140)
        paint.strokeWidth = stripe / 2f
        var start = -height - 2 * stripe + shift
        while (start < width + height) {
            canvas.drawLine(start, height, start + height, 0f, paint)
            start += 2 * stripe
        }
        val pulse = 0.6f + 0.4f * sin(frame / 6.0).toFloat()
        paint.color = Color.argb(200, 255, 255, 255)
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 5f * pulse, paint)
    }

    /** The view's texture comes and goes with it: each frame takes the current one, or skips. */
    private class Target(private val view: TextureView?) {
        private var texture: SurfaceTexture? = null
        private var surface: Surface? = null
        private var blank = false

        fun draw(
            pattern: ((Canvas, Long, CallVideo?) -> Unit)?,
            frame: Long,
            video: CallVideo? = null,
        ) {
            // Black once is enough: it stays until something else is drawn.
            if (pattern == null && blank) return
            val current = view?.surfaceTexture
            if (current == null) {
                release()
                return
            }
            if (current !== texture) {
                release()
                texture = current
                surface = Surface(current)
            }
            val canvas =
                try {
                    surface?.lockCanvas(null)
                } catch (_: RuntimeException) {
                    // Released under us, or out of buffers: the next frame tries again.
                    release()
                    null
                } ?: return
            try {
                if (pattern == null) canvas.drawColor(Color.BLACK)
                else pattern(canvas, frame, video)
            } finally {
                runCatching { surface?.unlockCanvasAndPost(canvas) }
            }
            blank = pattern == null
        }

        fun release() {
            surface?.release()
            surface = null
            texture = null
            blank = false
        }
    }

    private companion object {
        const val FPS = 30L
        const val FRAME_MS = 1000L / FPS

        /** SMPTE's bars at 75%: white, yellow, cyan, green, magenta, red, blue. */
        val BARS =
            listOf(
                Color.rgb(191, 191, 191),
                Color.rgb(191, 191, 0),
                Color.rgb(0, 191, 191),
                Color.rgb(0, 191, 0),
                Color.rgb(191, 0, 191),
                Color.rgb(191, 0, 0),
                Color.rgb(0, 0, 191),
            )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.MONOSPACE
            }
    }
}
