package com.prayerhome.tv.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.prayerhome.tv.App
import com.prayerhome.tv.football.MatchWatch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.football.Match
import com.prayerhome.tv.football.MatchState
import com.prayerhome.tv.ui.themes.TeamLogoSafe
import java.time.Duration
import java.time.Instant

/** What the Matches page shows on top of the match list. Back steps out of these one at a time. */
enum class MatchesPanel { NONE, TEAMS, SEARCH }

/**
 * Your teams' matches from the last 48 hours and the next 24 hours, plus the three shown on Home.
 * Every football setting lives in the header: the bell turns kick-off / goal banners on or off for all
 * matches, Teams opens the team list (add / remove), Refresh reloads the fixtures. OK on a match mutes or
 * unmutes just that one, or — once the match has ended — opens its YouTube highlights.
 */
@Composable
fun MatchesPage(s: PageStyle, m: HomeModel, nav: Nav, startPanel: MatchesPanel = MatchesPanel.NONE) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    val prefs = app.prefs
    var panel by remember { mutableStateOf(startPanel) }
    // The finished match whose highlights are open; Back returns to the match list.
    var watching by remember { mutableStateOf<Match?>(null) }
    // The match whose competition standings are open; Back returns to the match list.
    var standings by remember { mutableStateOf<Match?>(null) }
    var restoreId by remember { mutableStateOf<String?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    // Above the standings / highlights early returns, so the list keeps its place when they close.
    val listState = rememberLazyListState()
    if (watching != null) {
        // Registered after the activity's "Back goes Home" handler, so Back closes the highlights first.
        BackHandler { watching = null; focusTick++ }
        HighlightsScreen(s, m, watching!!, onBack = { watching = null; focusTick++ })
        return
    }
    if (standings != null) {
        // Same as the highlights: Back closes the panel before it reaches the activity.
        BackHandler { standings = null; focusTick++ }
        StandingsScreen(s, m, standings!!, onBack = { standings = null; focusTick++ })
        return
    }
    // Composed only while a panel is open, so it is registered after (and wins over) the activity's
    // "Back goes Home" handler even when the page was the first thing shown.
    if (panel != MatchesPanel.NONE) BackHandler {
        panel = if (panel == MatchesPanel.SEARCH) MatchesPanel.TEAMS else MatchesPanel.NONE
    }
    when (panel) {
        MatchesPanel.SEARCH -> { TeamSearch(s, m, onDone = { panel = MatchesPanel.TEAMS }); return }
        MatchesPanel.TEAMS -> { TeamsScreen(s, m, onBack = { panel = MatchesPanel.NONE }, onAdd = { panel = MatchesPanel.SEARCH }); return }
        MatchesPanel.NONE -> {}
    }

    // bumped on every toggle so the rows re-read their setting
    var v by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val teamsPill = remember { FocusRequester() }
    val addFirst = remember { FocusRequester() }
    // one requester per row, so closing the highlights or standings brings focus back to the match
    val rowReqs = remember { HashMap<String, FocusRequester>() }
    fun rowReq(id: String) = rowReqs.getOrPut(id) { FocusRequester() }
    val teams = prefs.teams
    val all = m.windowMatches
    val now = Instant.now()
    val live = all.filter { it.isLive }
    val earlier = all.filter { !it.isLive && it.kickoff <= now }.sortedByDescending { it.kickoff }
    val soon = all.filter { !it.isLive && it.kickoff > now && it.kickoff <= now.plus(Duration.ofHours(24)) }
    val later = all.filter { !it.isLive && it.kickoff > now.plus(Duration.ofHours(24)) }
    val sections = if (teams.isEmpty()) emptyList() else
        listOf("LIVE NOW" to live, "NEXT 24 HOURS" to soon, "LAST 48 HOURS" to earlier, "COMING UP" to later).filter { it.second.isNotEmpty() }
    val firstId = sections.firstOrNull()?.second?.firstOrNull()?.id

    // Position of a match in the list (each section has a header item).
    fun indexOfMatch(id: String): Int {
        var i = 0
        for ((_, list) in sections) {
            i++
            val at = list.indexOfFirst { it.id == id }
            if (at >= 0) return i + at
            i += list.size
        }
        return -1
    }

    // Focus the first match; with no matches the Teams button (or, with no teams at all, "Add a team").
    // After the highlights or standings close, focus goes back to the row that was opened — scrolling
    // to it first when it is out of view, because a row that is not composed cannot take focus.
    LaunchedEffect(focusTick, firstId == null, teams.isEmpty()) {
        val back = restoreId
        if (back != null) {
            if (runCatching { rowReq(back).requestFocus() }.isFailure) {
                val at = indexOfMatch(back)
                if (at >= 0) {
                    runCatching { listState.scrollToItem(at) }
                    delay(120)
                    runCatching { rowReq(back).requestFocus() }
                }
            }
            restoreId = null
            return@LaunchedEffect
        }
        runCatching {
            when {
                firstId != null -> rowReq(firstId).requestFocus()
                teams.isEmpty() -> addFirst.requestFocus()
                else -> teamsPill.requestFocus()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            PageHeader(s, m, "Matches", nav) {
                v
                val on = prefs.matchAlerts
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    // the one global switch for kick-off / goal banners (per-match mutes override it)
                    Pill(s, onClick = { setMatchAlerts(ctx, !on); v++ }) { focused ->
                        Icon(if (on) Ico.BELL else Ico.BELL_OFF, if (focused || on) s.accent else s.muted, 22)
                        Spacer(Modifier.width(10.dp))
                        T(if (on) "Notifications on" else "Notifications off", s.body, 22, if (focused) s.text else s.muted)
                    }
                    Pill(s, focusRequester = teamsPill, onClick = { panel = MatchesPanel.TEAMS }) { focused ->
                        Icon(Ico.BALL, if (focused) s.accent else s.muted, 22)
                        Spacer(Modifier.width(10.dp))
                        T("Teams · ${teams.size}", s.body, 22, if (focused) s.text else s.muted)
                    }
                    Pill(s, onClick = {
                        if (!refreshing) {
                            refreshing = true
                            scope.launch {
                                app.matches.refreshAll()
                                refreshing = false
                                m.toast = app.matches.status.value.ifEmpty { "Match data updated" }
                            }
                        }
                    }) { focused ->
                        Icon(Ico.REFRESH, if (focused) s.accent else s.muted, 22)
                        Spacer(Modifier.width(10.dp))
                        T(if (refreshing) "Refreshing…" else "Refresh", s.body, 22, if (focused) s.text else s.muted)
                    }
                }
            }
            if (sections.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val msg = when {
                        teams.isEmpty() -> "You haven't picked any teams yet"
                        m.scoresStatus.isNotEmpty() -> "${m.scoresStatus} — retrying every minute"
                        else -> "None of your teams have matches in the last 48 hours or the next 24 hours"
                    }
                    T(msg, s.body, 30, s.muted)
                    if (teams.isEmpty()) {
                        Spacer(Modifier.height(30.dp))
                        Pill(s, focusRequester = addFirst, onClick = { panel = MatchesPanel.SEARCH }) { focused ->
                            Icon(Ico.PLUS, s.accent, 24)
                            Spacer(Modifier.width(10.dp))
                            T("Add a team", s.body, 26, if (focused) s.text else s.muted)
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
                ) {
                    for ((title, list) in sections) {
                        item(key = "h$title") { SectionLabel(s, title, Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                        items(list, key = { it.id }) { match ->
                            v
                            val override = prefs.matchAlertOverride(match.id)
                            val global = prefs.matchAlerts
                            val finished = match.state == MatchState.FINISHED
                            MatchRow(
                                s, m, match, rowReq(match.id),
                                on = override ?: global,
                                custom = !finished && override != null && override != global,
                                onStandings = { restoreId = match.id; standings = match },
                            ) {
                                if (finished) {
                                    restoreId = match.id
                                    watching = match
                                } else {
                                    // two visible states; the override is dropped again when it matches the global setting
                                    val next = !(override ?: global)
                                    prefs.setMatchAlertOverride(match.id, match.kickoff.epochSecond, if (next == global) null else next)
                                    MatchWatch.schedule(ctx)
                                    v++
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Turns kick-off / goal banners on or off for every match without its own setting. Turning them on
 * without the "display over other apps" permission opens that settings screen.
 */
fun setMatchAlerts(ctx: android.content.Context, on: Boolean) {
    App.prefs(ctx).matchAlerts = on
    MatchWatch.schedule(ctx)
    if (on && !Settings.canDrawOverlays(ctx)) runCatching {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun MatchRow(
    s: PageStyle,
    m: HomeModel,
    match: Match,
    fr: FocusRequester?,
    on: Boolean,
    custom: Boolean,
    onStandings: () -> Unit,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(112.dp), verticalAlignment = Alignment.CenterVertically) {
        Focusable(
            Modifier.weight(1f).fillMaxHeight(),
            onClick = onClick,
            focusRequester = fr,
            // Right opens this competition's standings straight from the row, without a second stop.
            onKey = { e ->
                if (e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown) { onStandings(); true } else false
            },
        ) { focused ->
            val live = match.isLive
            val border = if (focused) s.accent else if (live) s.red else s.border
            Row(
                Modifier.matchParentSize()
                    .box(if (focused) s.surfaceFocus else s.surface, border, if (focused) s.focusBorder.dp else 1.dp, RoundedCornerShape(s.radius.dp))
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(310.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(match.league, s.body, 20, s.muted)
                    T(m.matchTime(match), s.body, 22, if (match.state == MatchState.UPCOMING) s.accent else s.muted)
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        T(match.home.name, s.body, 30, s.text, align = TextAlign.End)
                        Spacer(Modifier.width(16.dp))
                        TeamLogoSafe(match.home, 56)
                    }
                    Box(Modifier.width(200.dp), contentAlignment = Alignment.Center) {
                        if (match.hasScore) T("${match.homeScore} – ${match.awayScore}", s.numbers, 48, if (live) s.red else s.accent, weight = s.numbersWeight)
                        else T(m.matchTimeOnly(match), s.numbers, 34, s.text, weight = s.numbersWeight)
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        TeamLogoSafe(match.away, 56)
                        Spacer(Modifier.width(16.dp))
                        T(match.away.name, s.body, 30, s.text)
                    }
                }
                Box(Modifier.width(200.dp), contentAlignment = Alignment.CenterEnd) {
                    when (match.state) {
                        MatchState.LIVE -> Row(
                            Modifier.background(s.redBg, RoundedCornerShape(999.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Dot(s.red, 10); Spacer(Modifier.width(8.dp))
                            T("LIVE ${match.statusLabel}", s.body, 22, s.red, weight = 600, spacing = 0.06f)
                        }
                        MatchState.UPCOMING -> T(m.matchDay(match), s.body, 22, s.muted)
                        else -> T(match.statusLabel, s.body, 22, s.muted, weight = 600)
                    }
                }
                Spacer(Modifier.width(24.dp))
                RowAction(s, match.state == MatchState.FINISHED, on, custom, focused)
            }
        }
        Spacer(Modifier.width(12.dp))
        // The competition's standings, to the right of the mute / highlights button. Right on the row
        // opens it too, so this stays a pointer target rather than an extra D-pad stop.
        Box(
            Modifier.width(78.dp).fillMaxHeight()
                .box(s.surface, s.border, 1.dp, RoundedCornerShape(s.radius.dp))
                .pointerInput(Unit) { detectTapGestures { onStandings() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ico.PODIUM, s.muted, 28)
        }
    }
}

/**
 * The row's action: a bell (or struck-through bell) muting just this match, or — once it has
 * finished — the video sign that opens the match's YouTube highlights.
 */
@Composable
private fun RowAction(s: PageStyle, finished: Boolean, on: Boolean, custom: Boolean, focused: Boolean) {
    Box(Modifier.size(width = if (finished) 196.dp else 150.dp, height = 60.dp)) {
        Row(
            Modifier.matchParentSize().box(
                if (focused) s.accent else androidx.compose.ui.graphics.Color.Transparent,
                if (focused) s.accent else s.border, 1.dp, RoundedCornerShape(999.dp),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val c = if (focused) s.bg else if (finished || on) s.accent else s.muted
            when {
                finished -> {
                    Icon(Ico.VIDEO, c, 22)
                    Spacer(Modifier.width(8.dp))
                    T("Highlights", s.body, 20, c, weight = 600)
                }
                else -> {
                    Icon(if (on) Ico.BELL else Ico.BELL_OFF, c, 22)
                    Spacer(Modifier.width(8.dp))
                    T(if (on) "On" else "Muted", s.body, 20, c, weight = 600)
                }
            }
        }
        if (custom) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 6.dp).size(12.dp).background(s.red, CircleShape))
    }
}
