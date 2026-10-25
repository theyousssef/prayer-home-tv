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
import android.view.WindowManager
import android.widget.TextView
import com.prayerhome.tv.App
import com.prayerhome.tv.R
import com.prayerhome.tv.core.CHIME_MINUTES
import com.prayerhome.tv.core.Prayer
import com.prayerhome.tv.core.PrayerSchedule
import java.time.Instant

private const val TAG = "PrayerChime"
private const val ACTION_CHIME = "com.prayerhome.tv.CHIME"

object ChimeScheduler {
    /** (Re)arms a single exact alarm for the next "10 minutes before prayer" moment. */
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
        val v = (volume.coerceIn(0, 100) / 100f)
        mp.setVolume(v, v)
        mp.setOnCompletionListener {
            it.release()
            am.abandonAudioFocusRequest(focus)
            onDone()
        }
        mp.start()
    }
}

/** A small banner over whatever app is on screen. Needs the "display over other apps" permission. */
object ChimeBanner {
    fun show(context: Context, prayer: Prayer) {
        if (!Settings.canDrawOverlays(context)) return
        val wm = context.getSystemService(WindowManager::class.java)
        val arabic = runCatching { context.resources.getFont(R.font.amiri_700) }.getOrNull()
        val latin = runCatching { context.resources.getFont(R.font.jost_500) }.getOrNull()
        val label = SpannableString("${prayer.arabic}   in $CHIME_MINUTES minutes")
        if (arabic != null) {
            label.setSpan(TypefaceSpan(arabic), 0, prayer.arabic.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            label.setSpan(RelativeSizeSpan(1.3f), 0, prayer.arabic.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val tv = TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(Color.parseColor("#F3EEE3"))
            typeface = latin ?: Typeface.DEFAULT
            setPadding(48, 22, 48, 22)
            background = GradientDrawable().apply {
                cornerRadius = 100f
                setColor(Color.parseColor("#E60B1020"))
                setStroke(3, Color.parseColor("#FF5A5F"))
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 40; y = 40
        }
        runCatching {
            wm.addView(tv, lp)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { wm.removeView(tv) } }, 12_000)
        }.onFailure { Log.w(TAG, "banner failed", it) }
    }
}
