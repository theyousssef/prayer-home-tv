package com.prayerhome.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.prayerhome.tv.apps.AppEntry
import com.prayerhome.tv.core.Screensaver
import com.prayerhome.tv.football.MatchAlerts
import com.prayerhome.tv.football.MatchWatch
import com.prayerhome.tv.quran.Quran
import com.prayerhome.tv.ui.ToastBar
import com.prayerhome.tv.ui.AppsPage
import com.prayerhome.tv.ui.HomeModel
import com.prayerhome.tv.ui.MatchesPage
import com.prayerhome.tv.ui.Nav
import com.prayerhome.tv.ui.SettingsPage
import com.prayerhome.tv.ui.THEMES
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

enum class Screen { HOME, APPS, MATCHES, SETTINGS }

class MainActivity : ComponentActivity() {
    private val app get() = App.of(this)
    private lateinit var model: HomeModel
    private val screen = mutableStateOf(Screen.HOME)
    /** Bumped whenever Home is pressed, to put focus back on the first app. */
    private val homeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = HomeModel(app)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        val now = Instant.now()
                        model.now = now.plusSeconds(debugShiftSeconds)
                        delay(1000L - now.toEpochMilli() % 1000L + 5)
                    }
                }
                launch { app.matches.matches.collect { model.allMatches = it } }
                launch { app.matches.status.collect { model.scoresStatus = it } }
                launch { app.apps.apps.collect { model.allApps = it } }
                launch { app.matches.runWhileVisible() }
            }
        }

        val nav = Nav(
            apps = { screen.value = Screen.APPS },
            matches = { screen.value = Screen.MATCHES },
            settings = { screen.value = Screen.SETTINGS },
            home = { screen.value = Screen.HOME; homeTick.intValue++ },
            launch = ::launchApp,
            quran = ::openQuran,
        )
        setContent { Root(nav) }
        handleExtras(intent)
    }

    private var debugShiftSeconds = 0L

    /**
     * adb helpers: `am start -n com.prayerhome.tv/.MainActivity --ei theme 3 --es screen apps --es lang en`.
     * Debug builds also accept `--el shift <seconds>` to preview other times of day, and
     * `--es simulate kickoff|goal [--ei delay <seconds>]` to fire a match banner (after a delay, so
     * another app can be opened first).
     */
    private fun handleExtras(intent: Intent) {
        if (intent.hasExtra("theme")) app.prefs.themeIndex = intent.getIntExtra("theme", 0).coerceIn(0, THEMES.size - 1)
        intent.getStringExtra("lang")?.let { app.prefs.english = it == "en" }
        intent.getStringExtra("screen")?.let { name -> Screen.entries.firstOrNull { it.name.equals(name, true) }?.let { screen.value = it } }
        val debug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debug && intent.hasExtra("shift")) {
            debugShiftSeconds = intent.getLongExtra("shift", 0L)
            model.now = Instant.now().plusSeconds(debugShiftSeconds)
        }
        if (debug) intent.getStringExtra("toast")?.let { model.toast = it }
        val simulate = intent.getStringExtra("simulate")
        if (debug && simulate != null) {
            val delayMs = intent.getIntExtra("delay", 0) * 1000L
            app.scope.launch { delay(delayMs); MatchAlerts.simulate(app, simulate) }
        }
    }

    @Composable
    private fun Root(nav: Nav) {
        // The designs are drawn on a 1920×1080 canvas: make 1dp == 1 design pixel on any screen.
        val widthPx = resources.displayMetrics.widthPixels.toFloat()
        val heightPx = resources.displayMetrics.heightPixels.toFloat()
        val scale = minOf(widthPx / 1920f, heightPx / 1080f)
        val first = remember { FocusRequester() }
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                Box(Modifier.requiredSize(1920.dp, 1080.dp)) {
                    val theme = THEMES[model.themeIndex.coerceIn(0, THEMES.size - 1)]
                    when (screen.value) {
                        Screen.HOME -> theme.home(model, nav, first)
                        Screen.APPS -> AppsPage(theme.style, model, nav)
                        Screen.MATCHES -> MatchesPage(theme.style, model, nav)
                        Screen.SETTINGS -> SettingsPage(theme.style, model, nav)
                    }
                    if (screen.value == Screen.HOME) {
                        LaunchedEffect(homeTick.intValue, model.themeIndex, model.pinned.size) {
                            delay(60)
                            runCatching { first.requestFocus() }
                        }
                    }
                    model.toast?.let { msg ->
                        ToastBar(theme.style, msg, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
                        LaunchedEffect(msg) { delay(3500); model.toast = null }
                    }
                }
            }
        }
        BackHandler(enabled = screen.value != Screen.HOME) { nav.home() }
        BackHandler(enabled = screen.value == Screen.HOME) { /* the launcher has nowhere to go back to */ }
    }

    private fun openQuran() {
        val i = Quran.launchIntent(this)
        if (i == null || runCatching { startActivity(i) }.isFailure) model.toast = "Quran Reader isn't installed"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            screen.value = Screen.SETTINGS
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while we're already in front: go back to the home screen.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            screen.value = Screen.HOME
            homeTick.intValue++
        }
        handleExtras(intent)
    }

    override fun onStart() {
        super.onStart()
        App.homeVisible = true
        // the TV's settings app may have switched the screensaver back on
        Screensaver.apply(this, app.prefs.screensaverOff)
    }

    override fun onStop() {
        super.onStop()
        App.homeVisible = false
        // Leaving Home: (re)arm the background match checks from the latest scores, in case the chain
        // was broken (e.g. an alarm dropped while the process was dead).
        MatchWatch.schedule(this)
    }

    private fun launchApp(a: AppEntry) {
        if (a.pkg == TCL_TV && openTuner()) return
        try {
            startActivity(a.launch)
        } catch (e: ActivityNotFoundException) {
            Log.w("PrayerHome", "cannot launch ${a.pkg}", e)
            packageManager.getLeanbackLaunchIntentForPackage(a.pkg)?.let { runCatching { startActivity(it) } }
        }
    }

    /**
     * TCL's TV app reopens whichever source was used last (often HDMI), so open it on the
     * satellite/antenna tuner instead, at that input's last channel. AirPlay also registers a
     * tuner-type input, listed first, so TCL's own tuner service is preferred.
     */
    private fun openTuner(): Boolean {
        val tvInputs = getSystemService(TvInputManager::class.java) ?: return false
        val tuners = runCatching { tvInputs.tvInputList }.getOrNull().orEmpty().filter { it.type == TvInputInfo.TYPE_TUNER }
        val tuner = tuners.firstOrNull { "TunerInputService" in it.id }
            ?: tuners.firstOrNull { !it.id.contains("airplay", ignoreCase = true) } ?: return false
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(tuner.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    private companion object {
        const val TCL_TV = "com.tcl.tv"
    }
}
