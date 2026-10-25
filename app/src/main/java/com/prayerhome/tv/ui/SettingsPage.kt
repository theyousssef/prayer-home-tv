package com.prayerhome.tv.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.App
import com.prayerhome.tv.alarm.ChimePlayer
import com.prayerhome.tv.alarm.ChimeScheduler
import com.prayerhome.tv.core.CalcMethod
import com.prayerhome.tv.core.Cities
import com.prayerhome.tv.core.Prayer
import com.prayerhome.tv.core.Prefs
import com.prayerhome.tv.core.Screensaver
import com.prayerhome.tv.core.TeamRef
import com.prayerhome.tv.football.LiveScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Row_ {
    val key: String
}
private class Header(val title: String) : Row_ { override val key = "h:$title" }
private class Choice(override val key: String, val label: String, val value: String, val step: (Int) -> Unit) : Row_
private class Action(override val key: String, val label: String, val value: String, val run: () -> Unit) : Row_

@Composable
fun SettingsPage(s: PageStyle, m: HomeModel, nav: Nav) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    val p = app.prefs
    var v by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun changed(rescheduleChime: Boolean = false) {
        v++
        if (rescheduleChime) ChimeScheduler.schedule(ctx)
    }

    fun <T> cycle(list: List<T>, cur: T, d: Int): T = list[(list.indexOf(cur).coerceAtLeast(0) + d + list.size) % list.size]

    if (searching) {
        TeamSearch(s, m, onDone = { searching = false; v++ })
        return
    }
    if (picking) {
        ThemePicker(s, m, p.themeIndex.coerceIn(0, THEMES.size - 1),
            onPick = { i -> p.themeIndex = i; picking = false; v++ },
            onCancel = { picking = false })
        return
    }

    val rows = buildList<Row_> {
        v // re-read prefs after every change
        add(Action("tvsettings", "Android TV settings", "Open") {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
        add(Header("APPEARANCE"))
        val themes = THEMES
        add(Choice("theme", "Theme", "${themes[p.themeIndex.coerceIn(0, themes.size - 1)].name}   ${p.themeIndex + 1} / ${themes.size}") { d ->
            p.themeIndex = (p.themeIndex + d + themes.size) % themes.size; changed()
        })
        add(Choice("lang", "Prayer names & Hijri date", if (p.english) "English" else "Arabic") { _ -> p.english = !p.english; changed() })

        add(Header("PRAYER TIMES"))
        add(Choice("loc", "Location", p.location.name) { d ->
            val cur = Cities.all.indexOfFirst { it.name == p.location.name }
            p.location = Cities.all[((if (cur < 0) 0 else cur) + d + Cities.all.size) % Cities.all.size]; changed(true)
        })
        add(Choice("method", "Calculation method", CalcMethod.byId(p.methodId).label) { d ->
            p.methodId = cycle(CalcMethod.entries, CalcMethod.byId(p.methodId), d).id; changed(true)
        })
        add(Choice("asr", "Asr", if (p.hanafiAsr) "Hanafi (later)" else "Standard — Shafi'i, Maliki, Hanbali") { _ ->
            p.hanafiAsr = !p.hanafiAsr; changed(true)
        })
        for (pr in Prayer.entries) {
            val off = p.offsets[pr.ordinal]
            add(Choice("off${pr.ordinal}", "${pr.english} adjustment", if (off == 0) "0 min" else "%+d min".format(off)) { d ->
                val o = p.offsets; o[pr.ordinal] = (o[pr.ordinal] + d).coerceIn(-30, 30); p.offsets = o; changed(true)
            })
        }
        add(Choice("hijri", "Hijri date adjustment", if (p.hijriOffset == 0) "0 days" else "%+d day".format(p.hijriOffset)) { d ->
            p.hijriOffset = (p.hijriOffset + d).coerceIn(-2, 2); changed()
        })
        add(Action("tz", "Timezone & daylight saving", "${m.day.zone.id} · follows the TV's tz database") { })

        add(Header("CHIME · ${p.chimeMinutes} MINUTES BEFORE EACH PRAYER"))
        add(Choice("chime", "Chime", if (p.chimeEnabled) "On" else "Off") { _ -> p.chimeEnabled = !p.chimeEnabled; changed(true) })
        add(Choice("lead", "Chime time · also when the countdown turns red", "${p.chimeMinutes} min before each prayer") { d ->
            p.chimeMinutes = cycle(Prefs.CHIME_CHOICES, p.chimeMinutes, d); changed(true)
        })
        add(Choice("vol", "Chime volume · % of TV volume", "${p.chimeVolume}%") { d -> p.chimeVolume = (p.chimeVolume + d * 10).coerceIn(20, 100); changed() })
        add(Choice("over", "Also while watching other apps", if (p.chimeOverApps) "Yes" else "Only on Home") { _ -> p.chimeOverApps = !p.chimeOverApps; changed() })
        add(Action("test", "Play the chime now", "OK") { ChimePlayer.play(ctx, p.chimeVolume) })
        val overlay = Settings.canDrawOverlays(ctx)
        add(Action("overlay", "Banner over other apps", if (overlay) "Allowed" else "Not allowed — OK to open settings") {
            if (!overlay) runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })

        add(Header("MATCH NOTIFICATIONS"))
        add(Choice("alerts", "Kick-off & goal banners for my teams", if (p.matchAlerts) "On" else "Off") { _ ->
            setMatchAlerts(ctx, !p.matchAlerts); changed()
        })

        add(Header("MY TEAMS · OK TO REMOVE"))
        for (t in p.teams) {
            add(Action("team${t.id}", t.name, "Remove") {
                p.teams = p.teams.filterNot { it.id == t.id }; changed()
                scope.launch { app.matches.refreshAll() }
            })
        }
        add(Action("addteam", "Add a team…", "Search") { searching = true })

        add(Header("SYSTEM"))
        val canWrite = Screensaver.canWrite(ctx)
        add(Choice("saver", "Screensaver", (if (p.screensaverOff) "Off" else "On (TV default)") + if (canWrite) "" else " · needs the adb grant in README") { _ ->
            p.screensaverOff = !p.screensaverOff; Screensaver.apply(ctx, p.screensaverOff); changed()
        })
        add(Action("refresh", "Refresh match data", m.scoresStatus.ifEmpty { "OK" }) { scope.launch { app.matches.refreshAll() } })
    }

    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            PageHeader(s, m, "Settings", nav) { T("◀ ▶ to change · OK to select", s.body, 22, s.muted) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
                items(rows, key = { it.key }) { r ->
                    when (r) {
                        is Header -> SectionLabel(s, r.title, Modifier.padding(top = 18.dp, bottom = 4.dp))
                        // OK on Theme opens the thumbnail picker; ◀ ▶ still step through the themes in place.
                        is Choice -> SettingRow(s, r.label, r.value, if (r.key == "theme") first else null,
                            onClick = if (r.key == "theme") ({ picking = true }) else ({ r.step(1) }), onStep = r.step)
                        is Action -> SettingRow(s, r.label, r.value, null, onClick = r.run, onStep = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(s: PageStyle, label: String, value: String, fr: FocusRequester?, onClick: () -> Unit, onStep: ((Int) -> Unit)?) {
    Focusable(
        Modifier.fillMaxWidth().height(76.dp).onPreviewKeyEvent { e ->
            if (onStep == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key.nativeKeyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { onStep(-1); true }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { onStep(1); true }
                else -> false
            }
        },
        onClick = onClick,
        focusRequester = fr,
    ) { focused ->
        Row(
            Modifier.matchParentSize()
                .box(if (focused) s.surfaceFocus else s.surface, if (focused) s.accent else s.border, if (focused) 2.dp else 1.dp, RoundedCornerShape((s.radius * 0.8f).toInt().dp))
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(label, s.body, 26, s.text)
            Spacer(Modifier.weight(1f))
            if (onStep != null && focused) { Icon(Ico.BACK, s.accent, 22); Spacer(Modifier.width(12.dp)) }
            T(value, s.body, 24, if (focused) s.accent else s.muted)
            if (onStep != null && focused) { Spacer(Modifier.width(12.dp)); Icon(Ico.NEXT, s.accent, 22) }
        }
    }
}

@Composable
private fun TeamSearch(s: PageStyle, m: HomeModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    var query by remember { mutableStateOf("") }
    var index by remember { mutableStateOf<List<LiveScore.TeamInfo>>(emptyList()) }
    val field = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    BackHandler { onDone() }
    LaunchedEffect(Unit) {
        index = withContext(Dispatchers.IO) { app.matches.loadTeamIndex() }
        runCatching { field.requestFocus() }
    }
    val have = app.prefs.teams.map { it.id }.toSet()
    val q = query.trim().lowercase()
    val results = if (q.length < 2) emptyList() else index.asSequence()
        .filter { it.id !in have && it.name.lowercase().contains(q) }
        .sortedWith(compareBy({ youthOrWomen(it.name) && !youthOrWomen(q) }, { !it.name.lowercase().startsWith(q) }, { it.name.length }))
        .take(40).toList()

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PageHeader(s, m, "Add a team", Nav(apps = {}, matches = {}, settings = {}, home = onDone, launch = {})) {
                T("${index.size} teams from this week's fixtures", s.body, 22, s.muted)
            }
            Box(
                Modifier.fillMaxWidth().height(86.dp).box(s.surface, s.accent, 2.dp, RoundedCornerShape(s.radius.dp)).padding(horizontal = 28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = style(s.body, 34, s.text, 400, 0f, 0f, androidx.compose.ui.text.style.TextAlign.Start, false),
                    cursorBrush = SolidColor(s.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                    decorationBox = { inner ->
                        if (query.isEmpty()) T("Type a club name, e.g. Ahly, Juventus…", s.body, 34, s.muted)
                        inner()
                    },
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
                items(results, key = { it.id }) { t ->
                    Focusable(Modifier.fillMaxWidth().height(84.dp), onClick = {
                        app.prefs.teams = app.prefs.teams + TeamRef(t.id, t.name, t.img)
                        scope.launch { app.matches.refreshAll() }
                        onDone()
                    }) { focused ->
                        Row(
                            Modifier.matchParentSize().box(if (focused) s.surfaceFocus else s.surface, if (focused) s.accent else s.border, if (focused) 2.dp else 1.dp, RoundedCornerShape(s.radius.dp)).padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (t.img.isNotEmpty()) TeamLogo(com.prayerhome.tv.football.Side(t.id, t.name, "", t.img), 52)
                            else Box(Modifier.size(52.dp).box(s.surfaceFocus, s.border, 1.dp, CircleShape), contentAlignment = Alignment.Center) {
                                // LiveScore has no badge for some (mostly amateur) clubs: show their initial instead.
                                T(t.name.take(1).uppercase(), s.body, 24, s.muted)
                            }
                            Spacer(Modifier.width(20.dp))
                            T(t.name, s.body, 28, s.text)
                            Spacer(Modifier.width(16.dp))
                            T(t.country, s.body, 22, s.muted)
                            Spacer(Modifier.weight(1f))
                            if (focused) T("OK to add", s.body, 22, s.accent)
                        }
                    }
                }
            }
        }
    }
}

private fun youthOrWomen(n: String) = Regex("""\b(U\d\d|W|II|B|Reserves|Youth)\b""", RegexOption.IGNORE_CASE).containsMatchIn(n)

/**
 * Turns kick-off / goal banners on or off for every match without its own setting. Turning them on
 * without the "display over other apps" permission opens that settings screen, like the chime's row.
 */
fun setMatchAlerts(ctx: android.content.Context, on: Boolean) {
    App.prefs(ctx).matchAlerts = on
    com.prayerhome.tv.football.MatchWatch.schedule(ctx)
    if (on && !Settings.canDrawOverlays(ctx)) runCatching {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
