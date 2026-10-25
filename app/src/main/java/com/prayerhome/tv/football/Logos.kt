package com.prayerhome.tv.football

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Club badges: downloaded once, kept on disk forever and in a small memory cache. */
class Logos(context: Context) {
    private val dir = File(context.filesDir, "logos").apply { mkdirs() }
    private val mem = LruCache<String, ImageBitmap>(64)

    fun cached(img: String): ImageBitmap? = mem.get(img)

    /** The badge's file on disk, if it was downloaded already. */
    fun file(side: Side): File? = if (side.img.isEmpty()) null else File(dir, side.img.replace('/', '_')).takeIf { it.exists() }

    suspend fun load(side: Side): ImageBitmap? {
        if (side.img.isEmpty()) return null
        mem.get(side.img)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = File(dir, side.img.replace('/', '_'))
            if (!f.exists()) {
                if (!download(side.logoUrl, f)) download(side.logoUrlOld, f)
            }
            val bmp = runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
            bmp?.also { mem.put(side.img, it) }
        }
    }

    private fun download(url: String, f: File): Boolean = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            if (c.responseCode != 200) return@runCatching false
            val tmp = File(dir, f.name + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(f)
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)
}
