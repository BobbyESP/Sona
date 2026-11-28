/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.data

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import com.bobbyesp.sona.messaging.domain.AttachmentFiles
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Message files, under `files/messages/`: `sent/`, `received/` and `incoming/`, written in place
 * chunk by chunk. It never reads a whole file into memory, nor touches anything outside that
 * folder, whatever path is given. Other apps reach them only through [MessageFileProvider].
 */
class AndroidAttachmentFiles(
    private val context: Context,
    private val io: CoroutineDispatcher,
) : AttachmentFiles {

    private val root: File = messagesRoot(context)

    override suspend fun openIncoming(key: String, size: Long): Boolean =
        io<Boolean> {
            val file = incoming(key) ?: return@io false
            if (file.exists() && file.length() == size) return@io true
            file.parentFile?.mkdirs()
            if (file.parentFile!!.usableSpace < size - file.length().coerceAtLeast(0))
                return@io false
            RandomAccessFile(file, "rw").use { it.setLength(size) }
            true
        } ?: false

    override suspend fun write(key: String, offset: Long, bytes: ByteArray): Boolean =
        io<Boolean> {
            val file = incoming(key)?.takeIf { it.exists() } ?: return@io false
            RandomAccessFile(file, "rw").use {
                if (offset + bytes.size > it.length()) return@io false
                it.seek(offset)
                it.write(bytes)
            }
            true
        } ?: false

    override suspend fun sha256(key: String): String? = io {
        incoming(key)?.takeIf { it.exists() }?.let(::sha256Of)
    }

    override suspend fun finish(key: String, name: String): String? = io {
        val from = incoming(key)?.takeIf { it.exists() } ?: return@io null
        val folder =
            File(root, "$RECEIVED/$key").apply {
                deleteRecursively()
                mkdirs()
            }
        val to = File(folder, name)
        if (!inside(to)) return@io null
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
        to.relativeTo(root).path
    }

    override suspend fun discard(key: String) {
        io { incoming(key)?.delete() }
    }

    override suspend fun read(file: String, offset: Long, length: Int): ByteArray? = io {
        val source = stored(file)?.takeIf { it.isFile } ?: return@io null
        RandomAccessFile(source, "r").use {
            if (offset > it.length()) return@io null
            val bytes = ByteArray(minOf(length.toLong(), it.length() - offset).toInt())
            it.seek(offset)
            it.readFully(bytes)
            bytes
        }
    }

    override suspend fun delete(file: String) {
        io {
            val target = stored(file) ?: return@io
            target.delete()
            // Each file has a folder of its own, so that two of the same name never meet.
            target.parentFile?.takeIf { it != root && it.list().isNullOrEmpty() }?.delete()
        }
    }

    override fun uriOf(file: String): String {
        val target = stored(file) ?: return ""
        return FileProvider.getUriForFile(context, authority(context), target).toString()
    }

    private fun incoming(key: String): File? =
        File(root, "$INCOMING/$key.part").takeIf { key.isNotBlank() && inside(it) }

    private fun stored(path: String): File? = File(root, path).takeIf(::inside)

    private fun inside(file: File): Boolean =
        file.canonicalPath.startsWith(root.canonicalPath + File.separator)

    /** Everything here answers a failure (no space, a file gone) with `false` or `null`. */
    private suspend fun <T> io(block: () -> T?): T? =
        withContext(io) {
            try {
                block()
            } catch (e: IOException) {
                Log.w(TAG, "File storage failed", e)
                null
            } catch (e: SecurityException) {
                Log.w(TAG, "File storage refused", e)
                null
            }
        }

    companion object {
        private const val TAG = "AttachmentFiles"
        internal const val SENT = "sent"
        private const val RECEIVED = "received"
        private const val INCOMING = "incoming"

        /** The authority of [MessageFileProvider]: `${applicationId}.messages`. */
        fun authority(context: Context): String = "${context.packageName}.messages"

        internal fun messagesRoot(context: Context) = File(context.filesDir, "messages")

        internal fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().toHex()
        }

        internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}

class MessageFileProvider : FileProvider(R.xml.message_files)
