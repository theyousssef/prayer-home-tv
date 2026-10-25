package com.prayerhome.tv.quran

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The link to the separate Quran Reader app (com.prayerhome.quran).
 *
 * Prayer Home tells it when Home is shown or hidden; when the reader is left while reciting it asks
 * back with [ACTION_ASK], and Home offers "Keep reciting on the Home screen?" with Keep playing / Pause.
 */
object Quran {
    const val PKG = "com.prayerhome.quran"
    const val HOME_SHOWN = "$PKG.action.HOME_SHOWN"
    const val HOME_HIDDEN = "$PKG.action.HOME_HIDDEN"
    const val PAUSE = "$PKG.action.PAUSE"
    const val KEEP_PLAYING = "$PKG.action.KEEP_PLAYING"
    const val ACTION_ASK = "com.prayerhome.tv.action.ASK_KEEP_PLAYING"

    /** An ask that arrives up to this long before Home becomes visible is still shown. */
    const val ASK_WINDOW_MS = 3_000L

    class Ask(val title: String?, val at: Long)

    /** The latest unanswered ask; the activity takes it when (or once) Home is visible. */
    val ask = MutableStateFlow<Ask?>(null)

    fun send(context: Context, action: String) {
        runCatching { context.sendBroadcast(Intent(action).setPackage(PKG)) }
    }

    fun launchIntent(context: Context): Intent? {
        val pm = context.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(PKG) ?: pm.getLaunchIntentForPackage(PKG))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}

/** Receives the reader's "ask whether to keep playing" broadcast. */
class QuranReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Quran.ACTION_ASK) return
        Quran.ask.value = Quran.Ask(intent.getStringExtra("title"), SystemClock.elapsedRealtime())
    }
}
