package com.prayerhome.tv.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.prayerhome.tv.App
import com.prayerhome.tv.R
import com.prayerhome.tv.core.Prayer
import com.prayerhome.tv.core.PrayerSchedule
import java.time.Instant

private const val TAG = "PrayerChime"
private const val ACTION_CHIME = "com.prayerhome.tv.CHIME"

object ChimeScheduler {
    /** (Re)arms a single exact alarm for the next "N minutes before prayer" moment ([com.prayerhome.tv.core.Prefs.chimeMinutes]). */
    fun schedule(context: Context) {
        val prefs = App.prefs(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, null)
        am.cancel(pi)
        if (!prefs.chimeEnabled) return

        val (at, slot) = PrayerSchedule.nextChime(prefs, Instant.now())
        val fire = pendingIntent(context, slot.prayer)
        val ms = at.toEpochMilli()
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, fire)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, ms, 30_000, fire)
        }
        Log.i(TAG, "next chime $at for ${slot.prayer}")
    }

    private fun pendingIntent(context: Context, prayer: Prayer?): PendingIntent {
        val i = Intent(context, ChimeReceiver::class.java).setAction(ACTION_CHIME)
        if (prayer != null) i.putExtra("prayer", prayer.name)
        return PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Fires the chime, and re-arms the alarm after boot / clock / timezone changes (incl. DST switches). */
class ChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CHIME) {
            val prefs = App.prefs(context)
            val prayer = intent.getStringExtra("prayer")?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
            val interactive = context.getSystemService(PowerManager::class.java).isInteractive
            val homeVisible = App.homeVisible
            // don't ring while the TV is in standby; respect "only on the home screen" when chosen
            if (prefs.chimeEnabled && interactive && (homeVisible || prefs.chimeOverApps)) {
                val pending = goAsync()
                ChimePlayer.play(context.applicationContext, prefs.chimeVolume) { pending.finish() }
                if (!homeVisible && prayer != null) ChimeBanner.show(context.applicationContext, prayer)
            }
        }
        ChimeScheduler.schedule(context)
    }
}

object ChimePlayer {
    /**
     * Plays the chime at [volume] percent (20..100) of the stream's current volume: MediaPlayer's
     * volume is a gain applied on top of the TV volume the user has set.
     */
    fun play(context: Context, volume: Int, onDone: () -> Unit = {}) {
        val am = context.getSystemService(AudioManager::class.java)
        // Android 15+ mutes "media" from apps in the background, so use the alarm stream there;
        // on Android 14 (this TV) the media stream follows the TV's own volume, which feels right.
        val usage = if (Build.VERSION.SDK_INT >= 35) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val attrs = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // transient + may-duck: whatever is playing (Netflix, YouTube...) dips under the chime, then resumes
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(focus)
        val mp = runCatching { MediaPlayer.create(context, R.raw.chime, attrs, am.generateAudioSessionId()) }.getOrNull()
        if (mp == null) {
            am.abandonAudioFocusRequest(focus)
            onDone()
            return
        }
        val v = (volume.coerceIn(20, 100) / 100f)
        mp.setVolume(v, v)
        mp.setOnCompletionListener {
            it.release()
            am.abandonAudioFocusRequest(focus)
            onDone()
        }
        mp.start()
    }
}

/** The prayer banner: "العصر   in 10 minutes" (or "Asr in 10 minutes") over whatever app is on screen. */
object ChimeBanner {
    fun show(context: Context, prayer: Prayer) {
        val prefs = App.prefs(context)
        val name = prayer.label(prefs.english)
        val label = SpannableString("$name   in ${prefs.chimeMinutes} minutes")
        val arabic = if (prefs.english) null else runCatching { context.resources.getFont(R.font.amiri_700) }.getOrNull()
        if (arabic != null) {
            label.setSpan(TypefaceSpan(arabic), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            label.setSpan(RelativeSizeSpan(1.3f), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Banner.show(context, Banner.RED) { Banner.text(it, label) }
    }
}

/**
 * Small pill-shaped banners in the top-right corner, over whatever app is on screen, removed after
 * 12 seconds. Needs the "display over other apps" permission. Banners arriving together stack downwards.
 */
object Banner {
    val RED = Color.parseColor("#FF5A5F")
    val GOLD = Color.parseColor("#D9B26F")
    val INK = Color.parseColor("#F3EEE3")
    val MUTED = Color.parseColor("#A3ABBD")
    private const val SLOT_PX = 120
    private val slots = arrayOfNulls<View>(4)

    /** A banner line in the chime's type: Jost, ink on midnight. */
    fun text(context: Context, label: CharSequence, size: Float = 15f, color: Int = INK) = TextView(context).apply {
        text = label
        textSize = size
        setTextColor(color)
        // a short label like "57'" at the row's end was wrapping onto two lines
        isSingleLine = true
        typeface = runCatching { context.resources.getFont(R.font.jost_500) }.getOrNull() ?: Typeface.DEFAULT
    }

    /** Shows the view [build] makes (on the main thread) with a [stroke]-coloured outline. Safe from any thread. */
    fun show(context: Context, stroke: Int, build: (Context) -> View) {
        if (!Settings.canDrawOverlays(context)) return
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post { runCatching { add(app, build(app), stroke) }.onFailure { Log.w(TAG, "banner failed", it) } }
    }

    private fun add(context: Context, content: View, stroke: Int) {
        val wm = context.getSystemService(WindowManager::class.java)
        content.setPadding(48, 22, 48, 22)
        content.background = GradientDrawable().apply {
            cornerRadius = 100f
            setColor(Color.parseColor("#E60B1020"))
            setStroke(3, stroke)
        }
        val slot = slots.indexOfFirst { it == null }.coerceAtLeast(0)
        slots[slot]?.let { old -> runCatching { wm.removeView(old) } }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 40; y = 40 + slot * SLOT_PX
        }
        runCatching {
            wm.addView(content, lp)
            slots[slot] = content
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { wm.removeView(content) }
                if (slots[slot] === content) slots[slot] = null
            }, 12_000)
        }.onFailure { Log.w(TAG, "banner failed", it) }
    }
}
