package com.music.bitchord.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Private, bounded artwork copies survive picker grants and provider refreshes. */
object PlaylistCoverStore {
    private lateinit var context: Context
    private val writeGate = Mutex()
    private val _covers = MutableStateFlow<Map<String, String>>(emptyMap())
    val covers = _covers.asStateFlow()

    fun init(app: Context) {
        context = app.applicationContext
        _covers.value = runCatching {
            Json.decodeFromString<Map<String, String>>(preferences().getString("covers", "{}")!!)
                .filterValues { File(Uri.parse(it).path.orEmpty()).isFile }
        }.getOrDefault(emptyMap())
    }

    private fun preferences() = context.getSharedPreferences("daylight_playlist_covers", Context.MODE_PRIVATE)
    fun key(scope: String?, browseId: String): String {
        val id = browseId.removePrefix("VL")
        return if (id.startsWith("local:playlist:")) "device:$id" else "${scope.orEmpty()}:$id"
    }
    fun cover(scope: String?, browseId: String?): String? = browseId?.let { _covers.value[key(scope, it)] }

    /** Decode on IO, never retain the source photo or its external URI. */
    suspend fun prepare(uri: Uri): String {
        var draftFile: File? = null
        return try { withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "playlist-covers").apply { mkdirs() }
        val input = File(directory, "import-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source)
                input.outputStream().use { sink ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 24L * 1024 * 1024) { "Image too large" }
                        sink.write(buffer, 0, count)
                    }
                }
            }
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(input)) { decoder, info, _ ->
                    val ratio = (1024f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
                    decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(input.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                val decoded = requireNotNull(BitmapFactory.decodeFile(input.path,
                    BitmapFactory.Options().apply { inSampleSize = sample }))
                val orientation = ExifInterface(input.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                val matrix = Matrix().apply {
                    when (orientation) {
                        2 -> postScale(-1f, 1f)
                        3 -> postRotate(180f)
                        4 -> postScale(1f, -1f)
                        5 -> { postRotate(90f); postScale(-1f, 1f) }
                        6 -> postRotate(90f)
                        7 -> { postRotate(270f); postScale(-1f, 1f) }
                        8 -> postRotate(270f)
                    }
                }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }
            val target = File(directory, "draft-${UUID.randomUUID()}.jpg").also { draftFile = it }
            try {
                target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                Uri.fromFile(target).toString()
            } finally { bitmap.recycle() }
        } finally { input.delete() }
        } } catch (failure: Throwable) {
            draftFile?.delete()
            throw failure
        }
    }

    /** Commit once the name write succeeded; failures/cancel leave the prior cover intact. */
    suspend fun save(scope: String?, browseId: String, prepared: String?): Unit = withContext(Dispatchers.IO) {
        writeGate.withLock {
        val key = key(scope, browseId)
        val old = _covers.value[key]
        val saved = prepared?.let {
            val source = File(Uri.parse(it).path!!)
            val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { b -> "%02x".format(b) }
            val target = File(context.filesDir, "playlist-covers/$hash-${UUID.randomUUID()}.jpg")
            require(source.isFile && source.name.startsWith("draft-") &&
                source.parentFile?.canonicalFile == target.parentFile?.canonicalFile)
            source.copyTo(target)
            Uri.fromFile(target).toString()
        }
        val updated = _covers.value.toMutableMap().apply { if (saved == null) remove(key) else put(key, saved) }
        try {
            check(preferences().edit().putString("covers", Json.encodeToString(updated)).commit())
        } catch (failure: Throwable) {
            saved?.let { File(Uri.parse(it).path.orEmpty()).delete() }
            throw failure
        }
        _covers.value = updated
        old?.let { File(Uri.parse(it).path.orEmpty()).delete() }
        }
    }

    fun discard(prepared: String?) {
        prepared?.let { File(Uri.parse(it).path.orEmpty()) }?.takeIf {
            it.name.startsWith("draft-") && it.parentFile?.canonicalFile == File(context.filesDir, "playlist-covers").canonicalFile
        }?.delete()
    }
}
