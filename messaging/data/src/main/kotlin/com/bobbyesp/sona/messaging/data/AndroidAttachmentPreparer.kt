/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.bobbyesp.sona.messaging.data.AndroidAttachmentFiles.Companion.toHex
import com.bobbyesp.sona.messaging.domain.AttachmentPreparer
import com.bobbyesp.sona.messaging.domain.PreparedAttachment
import com.bobbyesp.sona.messaging.domain.envelope.FileNames
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Makes a file the user picked ready to send: reads its name and type from the content resolver,
 * copies it into `files/messages/sent/` (so it can still be sent, and sent again, after the
 * picker's grant is gone) and works out its SHA-256 on the way. A picture is turned upright,
 * resized to [MAX_SIDE] px on its longest side and saved as JPEG at [QUALITY], unless the original
 * is asked for: at about 500 bytes of file per `MESSAGE`, every kilobyte saved is two fewer
 * messages.
 *
 * Streams throughout: only a picture being resized is ever whole in memory, and only at the size it
 * is decoded to.
 */
class AndroidAttachmentPreparer(
    private val context: Context,
    private val io: CoroutineDispatcher,
) : AttachmentPreparer {

    private val root = AndroidAttachmentFiles.messagesRoot(context)
    private val sent = File(root, AndroidAttachmentFiles.SENT)

    override suspend fun prepare(
        source: String,
        originalQuality: Boolean,
        limit: Long,
    ): PreparedAttachment =
        withContext(io) {
            val uri = Uri.parse(source)
            val folder = File(sent, UUID.randomUUID().toString())
            try {
                val (name, declared) = describe(uri)
                val mime = typeOf(uri, name)
                val picture = mime in Resizable && !originalQuality
                if (!picture && declared != null && declared > limit) {
                    return@withContext PreparedAttachment.TooLarge(declared, limit)
                }
                folder.mkdirs()
                val prepared =
                    if (picture)
                        resized(uri, name, folder) ?: copied(uri, name, mime, folder, limit)
                    else copied(uri, name, mime, folder, limit)
                if (prepared !is PreparedAttachment.Ready) folder.deleteRecursively()
                if (prepared is PreparedAttachment.Ready && prepared.size > limit) {
                    folder.deleteRecursively()
                    return@withContext PreparedAttachment.TooLarge(prepared.size, limit)
                }
                prepared
            } catch (e: Exception) {
                folder.deleteRecursively()
                PreparedAttachment.Unreadable("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            }
        }

    /** The name the picker gives, made safe, and the size it declares. */
    private fun describe(uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        context.contentResolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameAt = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeAt = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameAt >= 0 && !cursor.isNull(nameAt)) name = cursor.getString(nameAt)
                    if (sizeAt >= 0 && !cursor.isNull(sizeAt)) size = cursor.getLong(sizeAt)
                }
            }
        return FileNames.safe(name ?: uri.lastPathSegment ?: "file") to size?.takeIf { it >= 0 }
    }

    private fun typeOf(uri: Uri, name: String): String =
        context.contentResolver.getType(uri)?.lowercase()
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /** The file as it is, copied and hashed; refused past [limit] bytes, whatever was declared. */
    private fun copied(
        uri: Uri,
        name: String,
        mime: String,
        folder: File,
        limit: Long,
    ): PreparedAttachment {
        val target = File(folder, name)
        val digest = MessageDigest.getInstance("SHA-256")
        val input =
            context.contentResolver.openInputStream(uri)
                ?: return PreparedAttachment.Unreadable("Nothing to read at $uri")
        var size = 0L
        input.use { from ->
            DigestOutputStream(target.outputStream(), digest).use { to ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = from.read(buffer)
                    if (read < 0) break
                    size += read
                    if (size > limit) return PreparedAttachment.TooLarge(size, limit)
                    to.write(buffer, 0, read)
                }
            }
        }
        val bounds = if (mime.startsWith("image/")) bounds(uri) else null
        return PreparedAttachment.Ready(
            file = target.relativeTo(root).path,
            name = name,
            mime = mime,
            size = size,
            sha256 = digest.digest().toHex(),
            width = bounds?.first,
            height = bounds?.second,
        )
    }

    /** The picture upright, at most [MAX_SIDE] px, as JPEG; `null` when it cannot be decoded. */
    private fun resized(uri: Uri, name: String, folder: File): PreparedAttachment? {
        val (width, height) = bounds(uri) ?: return null
        val options =
            BitmapFactory.Options().apply { inSampleSize = sampleSize(max(width, height)) }
        val decoded = open(uri) { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val upright = upright(decoded, orientation(uri))
        val longest = max(upright.width, upright.height)
        val scaled =
            if (longest <= MAX_SIDE) upright
            else {
                val scale = MAX_SIDE.toFloat() / longest
                Bitmap.createScaledBitmap(
                    upright,
                    (upright.width * scale).roundToInt().coerceAtLeast(1),
                    (upright.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
            }
        val target = File(folder, name.substringBeforeLast('.').ifBlank { "picture" } + ".jpg")
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(target.outputStream(), digest).use { out: OutputStream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }
        val result =
            PreparedAttachment.Ready(
                file = target.relativeTo(root).path,
                name = target.name,
                mime = "image/jpeg",
                size = target.length(),
                sha256 = digest.digest().toHex(),
                width = scaled.width,
                height = scaled.height,
            )
        if (scaled !== upright) scaled.recycle()
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        return result
    }

    private fun bounds(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri) { BitmapFactory.decodeStream(it, null, options) }
        return (options.outWidth to options.outHeight).takeIf { it.first > 0 && it.second > 0 }
    }

    private fun orientation(uri: Uri): Int =
        open(uri) {
            ExifInterface(it)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun <T> open(uri: Uri, read: (InputStream) -> T): T? = runCatching {
        context.contentResolver.openInputStream(uri)?.use(read)
    }
        .getOrNull()

    private companion object {
        const val MAX_SIDE = 2048
        const val QUALITY = 85

        /** Types decoded and recompressed; a GIF would lose its motion, so it goes as it is. */
        val Resizable = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

        /** The largest power of two that keeps the picture at least [MAX_SIDE] px. */
        fun sampleSize(longest: Int): Int {
            var sample = 1
            while (longest / (sample * 2) >= MAX_SIDE) sample *= 2
            return sample
        }
    }
}
