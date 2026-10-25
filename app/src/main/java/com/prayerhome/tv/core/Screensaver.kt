package com.prayerhome.tv.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Keeps the TV's screensaver (Google's Backdrop, or TCL's gallery, both Android "dreams") from
 * starting over the prayer screen. Writing these needs WRITE_SECURE_SETTINGS, granted once over adb:
 * `pm grant com.prayerhome.tv android.permission.WRITE_SECURE_SETTINGS`.
 *
 * With dreams off, Android turns the screen off when the TV's idle timer runs out instead
 * of starting the screensaver, so the TV still sleeps normally. The TV's own values are saved the
 * first time and put back when the setting is switched to "On".
 */
object Screensaver {
    private const val TAG = "PrayerScreensaver"
    private val KEYS = listOf("screensaver_enabled", "screensaver_activate_on_sleep", "screensaver_activate_on_dock")

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Applies [Prefs.screensaverOff] ([off]). Cheap: only writes values that differ. */
    fun apply(context: Context, off: Boolean) {
        if (!canWrite(context)) return
        val cr = context.contentResolver
        val saved = context.getSharedPreferences("screensaver", Context.MODE_PRIVATE)
        runCatching {
            if (off) {
                if (!saved.contains(KEYS[0])) {
                    // remember the TV's own values once, so "On" can restore them
                    saved.edit().apply { KEYS.forEach { putInt(it, Settings.Secure.getInt(cr, it, 1)) } }.apply()
                }
                KEYS.forEach { if (Settings.Secure.getInt(cr, it, 1) != 0) Settings.Secure.putInt(cr, it, 0) }
            } else if (saved.contains(KEYS[0])) {
                KEYS.forEach { Settings.Secure.putInt(cr, it, saved.getInt(it, 1)) }
                saved.edit().clear().apply()
            }
        }.onFailure { Log.w(TAG, "cannot change screensaver", it) }
    }
}
