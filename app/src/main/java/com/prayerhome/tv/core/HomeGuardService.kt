package com.prayerhome.tv.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.prayerhome.tv.MainActivity

/**
 * Puts Prayer Home in front whenever the Google TV home screen (launcherx) comes up: after boot,
 * on Home, or on Back from an app. Turned on once over adb (install-to-tv.sh).
 *
 * Google TV ignores third-party home apps while launcherx is enabled, but switching launcherx (and
 * its setup app, setupwraith) off has a cost: on a Chromecast with Google TV, setupwraith's
 * recovery home then restarts itself forever at boot (black screen), and without setupwraith the
 * device drops out of the Google Home app. So both stay on and this service steps in instead.
 * The service config only subscribes to launcherx's window changes, so it sees nothing else and
 * does nothing on a TV where launcherx is switched off.
 */
class HomeGuardService : AccessibilityService() {
    override fun onServiceConnected() {
        // bound after boot, maybe once launcherx is already showing
        if (rootInActiveWindow?.packageName == LAUNCHERX) showPrayerHome("launcherx already in front")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.packageName != LAUNCHERX) return
        showPrayerHome("launcherx came up: ${event.className}")
    }

    private fun showPrayerHome(why: String) {
        Log.i(TAG, "$why -> Prayer Home")
        // a bound accessibility service may start activities from the background
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "couldn't open Prayer Home", it) }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "PrayerHomeGuard"
        const val LAUNCHERX = "com.google.android.apps.tv.launcherx"
    }
}
