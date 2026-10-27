package app.lteguard

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    val lines = MutableStateFlow<List<String>>(emptyList())
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val lock = Any()

    fun init(ctx: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(ctx.filesDir, "guard.log")
            file = f
            if (f.exists()) {
                if (f.length() > 300_000) {
                    f.delete()
                } else {
                    lines.value = f.readLines().takeLast(300)
                }
            }
        }
    }

    fun log(msg: String) {
        val line = fmt.format(Date()) + "  " + msg
        synchronized(lock) {
            lines.value = (lines.value + line).takeLast(300)
            try {
                file?.appendText(line + "\n")
            } catch (_: Exception) {
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            lines.value = emptyList()
            try {
                file?.writeText("")
            } catch (_: Exception) {
            }
        }
    }

    fun export(ctx: Context): String {
        val name = "lteguard-log-" + System.currentTimeMillis() + ".txt"
        return try {
            val v = ContentValues()
            v.put(MediaStore.Downloads.DISPLAY_NAME, name)
            v.put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            if (uri == null) {
                "export failed"
            } else {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(lines.value.joinToString("\n").toByteArray())
                }
                "Saved to Downloads/$name"
            }
        } catch (e: Exception) {
            "export failed: $e"
        }
    }
}
