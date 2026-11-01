package com.prayerhome.tv.football

import android.util.Log
import com.prayerhome.tv.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "Net"

/** A short HTTP answer: the status code (‑1 when the request never got there) and the body. */
internal class Resp(val code: Int, val body: String?) {
    val ok get() = code in 200..299 && body != null
}

/** The one HTTP helper: a plain GET, an honest user agent, short timeouts, and failures logged. */
internal object Http {
    fun get(url: String, json: Boolean = false, label: String = ""): Resp {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "PrayerHome/${BuildConfig.VERSION_NAME} (Android TV)")
            c.setRequestProperty("Accept", if (json) "application/json" else "text/html,application/xhtml+xml")
            c.setRequestProperty("Accept-Language", "en")
            val code = c.responseCode
            val body = if (code in 200..299) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
            if (label.isNotEmpty() && code !in 200..299) Log.w(TAG, "$label HTTP $code")
            Resp(code, body)
        } catch (e: Exception) {
            if (label.isNotEmpty()) Log.w(TAG, "$label ${e.javaClass.simpleName} ${e.message.orEmpty()}")
            Resp(-1, null)
        } finally {
            runCatching { c?.disconnect() }
        }
    }
}
