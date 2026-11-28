/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.bobbyesp.sona.sip.api.VideoSize

/** Where the floating preview rests: one of the four corners of the video area. */
internal enum class Corner {
    TopStart,
    TopEnd,
    BottomStart,
    BottomEnd;

    val isTop: Boolean
        get() = this == TopStart || this == TopEnd

    val isStart: Boolean
        get() = this == TopStart || this == BottomStart
}

/**
 * The top-left offset of a [tile] resting in [corner] of [area], [margin] from its edges. With
 * [rtl], the start is on the right.
 */
internal fun cornerOffset(
    corner: Corner,
    tile: Size,
    area: Size,
    margin: Float,
    rtl: Boolean = false,
): Offset {
    val left = corner.isStart != rtl
    val x = if (left) margin else area.width - tile.width - margin
    val y = if (corner.isTop) margin else area.height - tile.height - margin
    return Offset(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
}

/**
 * The corner nearest the centre of a [tile] let go at [offset], its top-left. A fling's [velocity]
 * (px/s) first projects the centre ahead.
 */
internal fun nearestCorner(
    offset: Offset,
    tile: Size,
    area: Size,
    velocity: Offset = Offset.Zero,
    rtl: Boolean = false,
): Corner {
    val centre = offset + Offset(tile.width / 2f, tile.height / 2f) + velocity * FLING_PROJECTION_S
    val left = centre.x < area.width / 2f
    val top = centre.y < area.height / 2f
    val start = left != rtl
    return when {
        top && start -> Corner.TopStart
        top -> Corner.TopEnd
        start -> Corner.BottomStart
        else -> Corner.BottomEnd
    }
}

private const val FLING_PROJECTION_S = 0.15f

/**
 * The size of [video]'s picture in [area]: letterboxed, or cropped to cover it when [fill]. An
 * unknown size takes the area.
 */
internal fun videoFrame(area: Size, video: VideoSize?, fill: Boolean): Size {
    if (video == null || video.width <= 0 || video.height <= 0) return area
    if (area.width <= 0f || area.height <= 0f) return area
    val aspect = video.width.toFloat() / video.height
    val wide = area.width / area.height > aspect
    // Fit: the side that runs out first sets the size. Fill: the other one.
    return if (wide != fill) Size(area.height * aspect, area.height)
    else Size(area.width, area.width / aspect)
}
