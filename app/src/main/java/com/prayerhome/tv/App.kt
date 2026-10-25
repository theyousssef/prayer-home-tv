package com.prayerhome.tv

import android.app.Application
import android.content.Context
import com.prayerhome.tv.alarm.ChimeScheduler
import com.prayerhome.tv.apps.AppRepo
import com.prayerhome.tv.core.Prefs
import com.prayerhome.tv.core.Screensaver
import com.prayerhome.tv.football.Logos
import com.prayerhome.tv.football.MatchRepo
import com.prayerhome.tv.football.MatchWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    val prefs by lazy { Prefs(this) }
    val matches by lazy { MatchRepo(this, prefs) }
    val apps by lazy { AppRepo(this) }
    val logos by lazy { Logos(this) }
    /** For short background work started by receivers. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ChimeScheduler.schedule(this)
        MatchWatch.schedule(this)
        Screensaver.apply(this, prefs.screensaverOff)
    }

    companion object {
        /** True while the launcher itself is on screen. */
        @Volatile var homeVisible = false

        fun of(context: Context) = context.applicationContext as App
        fun prefs(context: Context) = of(context).prefs
    }
}
