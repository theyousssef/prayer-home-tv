package com.prayerhome.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.App
import com.prayerhome.tv.core.DateText
import com.prayerhome.tv.football.BracketRound
import com.prayerhome.tv.football.Competition
import com.prayerhome.tv.football.Goal
import com.prayerhome.tv.football.LiveNow
import com.prayerhome.tv.football.Match
import com.prayerhome.tv.football.MatchState
import com.prayerhome.tv.football.Side
import com.prayerhome.tv.football.Tie
import com.prayerhome.tv.ui.themes.TeamLogoSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.floor

private enum class StandingsMode { TABLE, BRACKET }

/** The live feed poll and table refresh interval while a match is on or about to start. */
private const val LIVE_EVERY_MS = 30_000L

/** "Around kick-off": a match that starts or has started within this window makes the page refresh. */
private val NEAR_KICKOFF: Duration = Duration.ofMinutes(5)

/** On opening, a match that finished this recently still means the table changed. */
private val RECENT_RESULT: Duration = Duration.ofHours(4)

private fun backTo(onBack: () -> Unit) = Nav(apps = {}, matches = {}, settings = {}, home = onBack, launch = {})

/**
 * A competition's standings: its league table (leagues, the UCL's league phase, cup groups) and/or
 * its knockout tree (cups).
 *
 * The saved copy opens instantly and stays for a week. The table is only downloaded again while a
 * match of this competition is live or around kick-off (±5 minutes), and then every 30 seconds; the
 * rest of the time the saved numbers stand, so opening the panel costs nothing. The small live feed
 * is used to keep the live rows red and the minutes ticking.
 */
@Composable
fun StandingsScreen(s: PageStyle, m: HomeModel, match: Match, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = App.of(ctx).standings
    val goalsRepo = App.of(ctx).goals
    val compId = match.comp
    // The Matches page keeps polling this match while it is live; follow that fresh copy so the
    // side card's score and final whistle don't stick at the values from the moment it was opened.
    val shown = m.allMatches.firstOrNull { it.id == match.id } ?: match

    // A memory copy can carry the last visit's live marks; the live feed below repaints them.
    var data by remember(compId) { mutableStateOf(repo.cached(compId)?.copy(live = LiveNow())) }
    var failed by remember(compId) { mutableStateOf(false) }
    var refreshing by remember(compId) { mutableStateOf(false) }
    var fromCache by remember(compId) { mutableStateOf(data != null) }
    var retry by remember(compId) { mutableIntStateOf(0) }
    var updatedAt by remember(compId) { mutableStateOf<Instant?>(null) }
    // The user's choice when a competition has both a table and a draw (e.g. the UCL in spring).
    var chosen by remember(compId) { mutableStateOf<StandingsMode?>(null) }
    var stageIdx by remember(compId) { mutableIntStateOf(-1) }
    var drawIdx by remember(compId) { mutableIntStateOf(0) }
    // Who scored. The result, the minute and the state are not read here: they are the fresh Match
    // from the list below, the same values as the matches list, the home screen and the banners. The
    // score is part of the key, so a goal restarts this loop and its scorer is read right away; the
    // stored copy opens the card with the goals already on screen.
    var goals by remember(match.id) { mutableStateOf(goalsRepo.cached(shown)) }
    var goalsMissing by remember(match.id) { mutableStateOf(false) }

    LaunchedEffect(match.id, shown.state, shown.homeScore, shown.awayScore) {
        while (true) {
            val current = m.allMatches.firstOrNull { it.id == match.id } ?: shown
            if (App.homeVisible) {
                val fresh = runCatching { goalsRepo.load(current) }.getOrNull()
                if (fresh != null) {
                    goals = fresh
                    goalsMissing = false
                } else if (goals == null) {
                    goalsMissing = true
                }
            }
            delay(goalsRepo.interval(current))
        }
    }

    LaunchedEffect(compId, retry) {
        if (compId.isEmpty()) return@LaunchedEffect
        // 1. The saved copy (memory, then the week-long file) for an instant first frame. Reading it
        //    also restarts its week, even when it was already in memory. Live marks from an earlier
        //    visit are dropped: the live feed below paints the ones that are true now.
        val saved = runCatching { repo.loadCached(compId) }.getOrNull()
        if (saved != null && data == null) {
            data = saved.copy(live = LiveNow())
            fromCache = true
        }
        var lastFull = Instant.EPOCH
        var first = true
        while (true) {
            if (!App.homeVisible) { delay(3_000); continue }
            // The page's own clock (the shared ticker), so the header time and the kick-off test agree.
            val now = m.now
            // 2. The small live feed: the red rows, the ticking minutes, and the signal that a tie
            //    has just ended (which means the table itself needs one more download).
            val liveNow = runCatching { repo.live(compId) }.getOrNull()
            val before = data
            if (liveNow != null && before != null) {
                val (next, ended) = before.applyLive(liveNow)
                if (next != before) data = next
                if (ended) lastFull = Instant.EPOCH
            }
            // 3. Is anything around kick-off? Only then is the table downloaded again.
            val fixtures = fixturesOf(compId, shown, m.allMatches, data)
            val onPitch = liveNow?.teams?.isNotEmpty() == true || fixtures.any { it.state == MatchState.LIVE }
            val nearKickoff = fixtures.any { kotlin.math.abs(Duration.between(now, it.kickoff).seconds) <= NEAR_KICKOFF.seconds }
            val justFinished = first && fixtures.any { it.state == MatchState.FINISHED && it.kickoff.isAfter(now.minus(RECENT_RESULT)) }
            val mustLoad = data == null
            val due = first || Duration.between(lastFull, now).seconds >= LIVE_EVERY_MS / 1000
            if ((onPitch || nearKickoff || justFinished || mustLoad) && due) {
                refreshing = true
                val fresh = runCatching { repo.refresh(compId) }.getOrNull()
                refreshing = false
                lastFull = now
                if (fresh != null) {
                    data = fresh
                    failed = false
                    fromCache = false
                    updatedAt = Instant.now()
                } else if (data == null) {
                    failed = true
                }
            }
            first = false
            // 4. Sleep: 30 s while something is around kick-off, otherwise until the next kick-off
            //    minus five minutes (or half an hour of quiet), with no table downloads in between.
            delay(
                when {
                    onPitch || nearKickoff -> LIVE_EVERY_MS
                    mustLoad -> 10_000L
                    else -> sleepUntil(fixtures, now)
                },
            )
        }
    }

    val hasTable = data?.hasTable == true
    val hasBracket = data?.hasBracket == true
    val mode = when {
        hasTable && hasBracket -> chosen ?: StandingsMode.TABLE
        hasBracket -> StandingsMode.BRACKET
        else -> StandingsMode.TABLE
    }
    val liveCount = data?.live?.teams?.size?.div(2) ?: 0

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(
            Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(s, m, "Standings", backTo(onBack), backLabel = "Matches") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    T(shown.league, s.body, 24, s.muted)
                    if (liveCount > 0) {
                        Row(
                            Modifier.background(s.redBg, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Dot(s.red, 8)
                            Spacer(Modifier.width(7.dp))
                            T("$liveCount LIVE", s.body, 19, s.red, weight = 600, spacing = 0.05f)
                        }
                    }
                }
            }

            // Mode switch and the stage / draw selector. A competition with both a table and a draw
            // gets the switch on its own line, so Up from the table reaches the selector first.
            val d = data
            val both = hasTable && hasBracket
            val stageNeeded = d != null && mode == StandingsMode.TABLE && d.tables.size > 1
            val drawNeeded = d != null && mode == StandingsMode.BRACKET && d.bracket?.draws.orEmpty().size > 1
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (both) {
                    TabPill(s, "Table", mode == StandingsMode.TABLE) { chosen = StandingsMode.TABLE }
                    TabPill(s, "Bracket", mode == StandingsMode.BRACKET) { chosen = StandingsMode.BRACKET }
                } else if (d != null && stageNeeded) {
                    StagePicker(s, d, match.league, match.stage, stageIdx) { stageIdx = it }
                } else if (d != null && drawNeeded) {
                    DrawPicker(s, d, drawIdx) { drawIdx = it }
                }
                if (mode == StandingsMode.BRACKET && d?.bracket != null && !drawHasMatch(d, match)) {
                    T("Your match isn't in this draw yet", s.body, 16, s.muted)
                }
                Spacer(Modifier.weight(1f))
                val at = updatedAt
                T(
                    when {
                        compId.isEmpty() -> ""
                        refreshing -> if (data == null) "Loading…" else "Updating…"
                        fromCache -> "Saved copy"
                        at != null -> "Updated ${DateText.time(at, m.day.zone)}"
                        else -> ""
                    },
                    s.body, 18, s.muted,
                )
            }
            if (both && d != null && (stageNeeded || drawNeeded)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mode == StandingsMode.TABLE && stageNeeded) StagePicker(s, d, match.league, match.stage, stageIdx) { stageIdx = it }
                    if (mode == StandingsMode.BRACKET && drawNeeded) DrawPicker(s, d, drawIdx) { drawIdx = it }
                }
            }

            when {
                // A few of LiveScore's small feeds (Egypt's League Cup, for one) carry no competition id.
                compId.isEmpty() -> EmptyState(s, "No standings for this competition yet", Modifier.weight(1f))
                d == null && !failed -> LoadingState(s, match.league, Modifier.weight(1f))
                d == null -> ErrorState(s, Modifier.weight(1f)) { failed = false; retry++ }
                mode == StandingsMode.TABLE -> TableView(
                    s, m, d,
                    stageIdx.takeIf { it in d.tables.indices } ?: stageFor(d.tables, match.league, match.stage),
                    shown,
                    goals,
                    goalsMissing,
                    onBack,
                    Modifier.weight(1f),
                )
                else -> BracketView(s, m, d, drawIdx, shown, onBack, Modifier.weight(1f))
            }
        }
    }
}

/** The competition's kick-off times we already know about: the opened match, the team list, the draw. */
private class Fixture(val kickoff: Instant, val state: MatchState)

private fun fixturesOf(compId: String, shown: Match, all: List<Match>, c: Competition?): List<Fixture> {
    val out = ArrayList<Fixture>()
    if (compId.isNotEmpty()) out += Fixture(shown.kickoff, shown.state)
    all.forEach { if (it.comp == compId && it.id != shown.id) out += Fixture(it.kickoff, it.state) }
    c?.bracket?.draws?.forEach { draw ->
        draw.rounds.forEach { round ->
            round.ties.forEach { tie -> if (tie.kickoff != Instant.EPOCH) out += Fixture(tie.kickoff, tie.state) }
        }
    }
    return out
}

/** How long to stay quiet: up to the next kick-off minus five minutes, capped at half an hour. */
private fun sleepUntil(fixtures: List<Fixture>, now: Instant): Long {
    val next = fixtures.map { it.kickoff }.filter { it.isAfter(now) }.minOrNull()
    if (next == null) return 30 * 60_000L
    val ms = Duration.between(now, next).toMillis() - NEAR_KICKOFF.toMillis()
    return ms.coerceIn(5_000L, 30 * 60_000L)
}

// ------------------------------------------------------------------ loading / empty states

@Composable
private fun LoadingState(s: PageStyle, competition: String, modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Ico.PODIUM, s.accent, 56)
        Spacer(Modifier.height(24.dp))
        T("Loading $competition", s.body, 36, s.text)
        Spacer(Modifier.height(10.dp))
        T("It stays saved for a week of instant opens", s.body, 24, s.muted)
    }
}

@Composable
private fun ErrorState(s: PageStyle, modifier: Modifier, onRetry: () -> Unit) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { retry.requestFocus() } }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        T("Couldn't load the standings", s.body, 38, s.red)
        Spacer(Modifier.height(12.dp))
        T("Check the TV's connection, then try again.", s.body, 24, s.muted)
        Spacer(Modifier.height(28.dp))
        Pill(s, focusRequester = retry, onClick = onRetry) { focused ->
            Icon(Ico.REFRESH, if (focused) s.accent else s.muted, 22)
            Spacer(Modifier.width(10.dp))
            T("Try again", s.body, 24, if (focused) s.text else s.muted)
        }
    }
}

@Composable
private fun EmptyState(s: PageStyle, text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        T(text, s.body, 30, s.muted)
    }
}

// ------------------------------------------------------------------ table

// Compact enough that the numbers sit next to the names instead of drifting to the far edge.
private const val COL_RANK = 48
private const val COL_BADGE = 36
private const val COL_NAME = 400
private const val COL_STAT = 62
private const val COL_GD = 72
private const val COL_PTS = 82
private val TABLE_W = 980.dp

/** Row height plus the list's spacing: one D-pad press scrolls exactly one row. */
private const val ROW_STEP = 71

/** The UCL / Europa league phase: 1–8 straight to the round of 16, 9–24 into the play-offs. */
private val LEAGUE_PHASE_CUTS = setOf(8, 24)

/**
 * The table as one plain scroll surface: the D-pad scrolls the list, nothing is selected. The two
 * teams of the match the panel was opened from are already marked, so there is nothing to focus.
 */
@Composable
private fun TableView(
    s: PageStyle,
    m: HomeModel,
    c: Competition,
    stageIndex: Int,
    focusMatch: Match,
    goals: List<Goal>?,
    goalsMissing: Boolean,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    if (c.tables.isEmpty()) {
        EmptyState(s, "No table for this competition yet", modifier)
        return
    }
    val stage = c.tables[stageIndex.coerceIn(0, c.tables.lastIndex)]
    val opened = remember(focusMatch.id) { setOf(focusMatch.home.id, focusMatch.away.id) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusReq = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val rowPx = with(LocalDensity.current) { ROW_STEP.dp.toPx() }
    val showGroups = stage.groups.size > 1
    // Focus moves into the table once; picking another stage keeps focus on that picker.
    var tookFocus by remember { mutableStateOf(false) }

    // Where your two teams sit in the flattened list (section labels and cut lines count as items),
    // so the list can open on them without putting a focus box around them.
    val openedIndex = remember(stage, opened) {
        var idx = 0
        var found = -1
        stage.groups.forEach { group ->
            if (showGroups) idx++
            val cuts = if (group.rows.size == 36) LEAGUE_PHASE_CUTS else emptySet()
            group.rows.forEachIndexed { i, row ->
                if (found < 0 && row.teamId in opened) found = idx
                idx++
                if (i + 1 in cuts) idx++
            }
        }
        found
    }

    LaunchedEffect(stage.id) {
        delay(120)
        // The table opens at #1. It only scrolls when the higher of your two teams is below the
        // first screenful, and then just enough to bring it into view.
        if (openedIndex > 0) {
            val visible = list.layoutInfo.visibleItemsInfo
            val onScreen = visible.isNotEmpty() && openedIndex in visible.first().index..visible.last().index
            // If the list hasn't been laid out yet, leave it at the top: never jump needlessly.
            if (visible.isNotEmpty() && !onScreen) runCatching { list.scrollToItem((openedIndex - 2).coerceAtLeast(0)) }
        }
        if (!tookFocus) {
            tookFocus = true
            runCatching { focusReq.requestFocus() }
        }
    }

    Row(modifier.fillMaxWidth()) {
        Column(Modifier.width(TABLE_W).fillMaxHeight()) {
            TableHeaderRow(s)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().weight(1f).clipToBounds()
                    .focusRequester(focusReq)
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { e -> tableKeys(e, list, scope, rowPx, onBack) }
                    .focusable(),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = list,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(bottom = 8.dp, start = 2.dp, end = 2.dp),
                ) {
                    stage.groups.forEachIndexed { gi, group ->
                        val cuts = if (group.rows.size == 36) LEAGUE_PHASE_CUTS else emptySet()
                        if (showGroups) {
                            item(key = "g$gi") {
                                SectionLabel(s, group.name.uppercase(), Modifier.padding(top = 8.dp, bottom = 2.dp))
                            }
                        }
                        group.rows.forEachIndexed { i, row ->
                            item(key = "r$gi-$i-${row.teamId}") {
                                TeamLine(s, row, c.live.teams[row.teamId], row.teamId in opened)
                            }
                            if (i + 1 in cuts) {
                                item(key = "cut$gi-$i") {
                                    CutLine(s, if (i + 1 == 8) "TOP 8 · ROUND OF 16" else "25–36 · OUT")
                                }
                            }
                        }
                    }
                }
                // A faint frame: it says "the D-pad scrolls here" without marking any team.
                if (focused) {
                    Box(Modifier.matchParentSize().border(1.dp, s.accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp)))
                }
            }
        }
        Spacer(Modifier.width(40.dp))
        MatchSideCard(s, m, focusMatch, goals, goalsMissing, stage, focusReq, Modifier.width(500.dp).fillMaxHeight())
    }
}

/** Up / Down scroll the table; at either end the key is left alone so focus can reach the tabs. */
private fun tableKeys(e: KeyEvent, list: LazyListState, scope: CoroutineScope, rowPx: Float, onBack: () -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val mult = 1 + e.nativeKeyEvent.repeatCount.coerceAtMost(5)
    return when (e.key) {
        // The table has nothing to its left, so Left goes back to the matches.
        Key.DirectionLeft -> { onBack(); true }
        Key.DirectionUp -> if (!list.canScrollBackward) false else {
            scope.launch { list.scrollBy(-rowPx * mult) }
            true
        }
        Key.DirectionDown -> if (!list.canScrollForward) false else {
            scope.launch { list.scrollBy(rowPx * mult) }
            true
        }
        else -> false
    }
}

/**
 * The match the panel was opened from, beside the table: the teams and the score at the top, then
 * every goal with its minute and scorer. The card scrolls on its own (Right from the table) so a
 * seven-goal game fits without squeezing anything.
 */
@Composable
private fun MatchSideCard(
    s: PageStyle,
    m: HomeModel,
    match: Match,
    goals: List<Goal>?,
    goalsMissing: Boolean,
    stage: com.prayerhome.tv.football.TableStage,
    tableFocus: FocusRequester,
    modifier: Modifier,
) {
    // Straight from the live feed: the same score, minute and state the matches list shows.
    val live = match.isLive
    val homeScore = if (match.hasScore) match.homeScore else ""
    val awayScore = if (match.hasScore) match.awayScore else ""
    val label = match.statusLabel
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusReq = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val step = with(LocalDensity.current) { 96.dp.toPx() }
    Box(
        modifier.clipToBounds()
            .focusRequester(focusReq)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> if (!scroll.canScrollBackward) false else {
                        scope.launch { scroll.scrollBy(-step) }
                        true
                    }
                    Key.DirectionDown -> if (!scroll.canScrollForward) false else {
                        scope.launch { scroll.scrollBy(step) }
                        true
                    }
                    Key.DirectionLeft -> { runCatching { tableFocus.requestFocus() }; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Column(
            Modifier.fillMaxSize()
                .box(s.surface, if (focused) s.accent else s.border, if (focused) 2.dp else 1.dp, RoundedCornerShape(s.radius.dp))
                .verticalScroll(scroll)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T("YOUR MATCH", s.body, 17, s.muted, spacing = s.labelSpacing)
            T(match.league, s.body, 20, s.muted)
            MatchTeamLine(s, match.home, homeScore, live)
            MatchTeamLine(s, match.away, awayScore, live)
            if (live) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(s.red, 8)
                    Spacer(Modifier.width(7.dp))
                    T("LIVE $label", s.body, 18, s.red, weight = 600, spacing = 0.05f)
                }
            } else {
                T(
                    if (match.state == MatchState.FINISHED) label else m.matchTime(match),
                    s.body, 18, s.muted,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(s.border))
            when {
                goals == null -> T(
                    if (goalsMissing) "Goals unavailable" else "Looking for the goals…",
                    s.body, 17, s.muted,
                )
                goals.isEmpty() && match.state == MatchState.UPCOMING -> T("Kick-off ${m.matchTimeOnly(match)}", s.body, 17, s.muted)
                goals.isEmpty() -> T("No goals", s.body, 17, s.muted)
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("GOALS", s.body, 20, s.muted, spacing = s.labelSpacing)
                        Spacer(Modifier.weight(1f))
                        T("${goals.size}", s.numbers, 22, s.accent, weight = 600)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        goals.forEach { g -> GoalLine(s, g, if (g.home) match.home else match.away) }
                    }
                }
            }
            if (stage.groups.any { it.rows.size == 36 }) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(s.accent.copy(alpha = 0.5f)))
                T("Top 8 go to the Round of 16 · 25–36 are out", s.body, 16, s.muted)
            }
        }
    }
}

@Composable
private fun MatchTeamLine(s: PageStyle, team: Side, score: String, live: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamLogoSafe(team, 44)
        Spacer(Modifier.width(14.dp))
        T(team.name, s.body, 26, s.text, modifier = Modifier.weight(1f))
        if (score.isNotEmpty()) T(score, s.numbers, 34, s.text, weight = s.numbersWeight)
    }
}

/** One goal: the minute, a side colour, the scorer and a small badge. */
@Composable
private fun GoalLine(s: PageStyle, goal: Goal, team: Side) {
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(68.dp), contentAlignment = Alignment.CenterEnd) {
            T(goal.label, s.numbers, 24, s.muted, weight = s.numbersWeight)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(4.dp).height(26.dp).background(if (goal.home) s.accent else s.muted))
        Spacer(Modifier.width(14.dp))
        T(goal.player, s.body, 27, s.text, modifier = Modifier.weight(1f))
        if (goal.note.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            T(goal.note, s.body, 18, s.muted)
        }
        Spacer(Modifier.width(10.dp))
        TeamLogoSafe(team, 30)
    }
}

@Composable
private fun TableHeaderRow(s: PageStyle) {
    Row(
        Modifier.width(TABLE_W).padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(COL_RANK.dp), contentAlignment = Alignment.CenterStart) {
            T("#", s.body, 19, s.muted, spacing = s.labelSpacing)
        }
        Spacer(Modifier.width(6.dp))
        Spacer(Modifier.width((COL_BADGE + 14).dp))
        Box(Modifier.width(COL_NAME.dp)) {
            T("TEAM", s.body, 19, s.muted, spacing = s.labelSpacing)
        }
        NumHeader(s, "P", COL_STAT)
        NumHeader(s, "W", COL_STAT)
        NumHeader(s, "D", COL_STAT)
        NumHeader(s, "L", COL_STAT)
        NumHeader(s, "GD", COL_GD)
        NumHeader(s, "PTS", COL_PTS)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NumHeader(s: PageStyle, text: String, width: Int) {
    Box(Modifier.width(width.dp), contentAlignment = Alignment.CenterEnd) {
        T(text, s.body, 19, s.muted)
    }
}

@Composable
private fun TeamLine(s: PageStyle, row: com.prayerhome.tv.football.TableRow, live: String?, opened: Boolean) {
    Row(
        Modifier.width(TABLE_W).height(64.dp)
            // Your two teams are recessed into a darker shade of the page, not highlighted.
            .box(if (opened) lerp(s.surface, s.bg, 0.82f) else s.surface, s.border, 1.dp, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(COL_RANK.dp), contentAlignment = Alignment.CenterStart) {
            T("${row.rank}", s.numbers, 24, s.muted, weight = s.numbersWeight)
        }
        Spacer(Modifier.width(6.dp))
        TeamLogoSafe(Side(row.teamId, row.name, row.short, row.img), COL_BADGE)
        Spacer(Modifier.width(14.dp))
        Row(Modifier.width(COL_NAME.dp), verticalAlignment = Alignment.CenterVertically) {
            T(row.name, s.body, 32, s.text, modifier = Modifier.weight(1f, fill = false))
            if (live != null) {
                Spacer(Modifier.width(12.dp))
                Dot(s.red, 8)
                Spacer(Modifier.width(6.dp))
                T("LIVE $live", s.body, 17, s.red, weight = 600, spacing = 0.05f)
            }
        }
        NumCell(s, "${row.played}", COL_STAT, s.muted, 27)
        NumCell(s, "${row.won}", COL_STAT, s.text, 27)
        NumCell(s, "${row.drawn}", COL_STAT, s.text, 27)
        NumCell(s, "${row.lost}", COL_STAT, s.text, 27)
        NumCell(s, signed(row.goalDiff), COL_GD, s.muted, 27)
        Box(Modifier.width(COL_PTS.dp), contentAlignment = Alignment.CenterEnd) {
            // A team that is playing right now keeps its points red, beside the red LIVE tag.
            T("${row.points}", s.numbers, 32, if (live != null) s.red else s.accent, weight = 600)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NumCell(s: PageStyle, text: String, width: Int, color: Color, size: Int) {
    Box(Modifier.width(width.dp), contentAlignment = Alignment.CenterEnd) {
        T(text, s.numbers, size, color, align = TextAlign.End, weight = s.numbersWeight)
    }
}

private fun signed(n: Int) = if (n > 0) "+$n" else "$n"

/** The 8th / 24th place line in a 36-team league phase. */
@Composable
private fun CutLine(s: PageStyle, label: String) {
    Row(
        Modifier.width(TABLE_W).height(30.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(s.accent.copy(alpha = 0.5f)))
        Spacer(Modifier.width(12.dp))
        T(label, s.body, 15, s.muted, spacing = 0.08f)
    }
}

// ------------------------------------------------------------------ bracket tree

private val TREE_CARD_W = 360.dp
private val TREE_CARD_H = 86.dp
private val TREE_GAP = 76.dp
private val TREE_SLOT = 94.dp
private val TREE_LABEL_H = 38.dp

/** One D-pad press in the tree. Holding the key accelerates. */
private val TREE_STEP_X = 320.dp
private val TREE_STEP_Y = 180.dp

/**
 * The whole knockout tree: every round from the first to the final, as one surface that scrolls in
 * both directions. Only the ties in view are composed, so a 176-tie preliminary round costs no more
 * than a round of 16. The tie the panel was opened from is marked in the accent colour (and its path
 * to the final), live ties are red, and nothing else is selected.
 */
@Composable
private fun BracketView(s: PageStyle, m: HomeModel, c: Competition, drawIndex: Int, focusMatch: Match, onBack: () -> Unit, modifier: Modifier) {
    val draws = c.bracket?.draws.orEmpty()
    val draw = draws.getOrNull(drawIndex.coerceIn(0, (draws.size - 1).coerceAtLeast(0)))
    if (draw == null || draw.rounds.isEmpty()) {
        EmptyState(s, "The draw hasn't been made yet", modifier)
        return
    }
    val rounds = draw.rounds
    val scrollX = remember(draw.name, drawIndex) { mutableFloatStateOf(0f) }
    val scrollY = remember(draw.name, drawIndex) { mutableFloatStateOf(0f) }
    val focusReq = remember { FocusRequester() }
    var centred by remember(draw.name, drawIndex) { mutableStateOf(false) }
    // Focus moves into the tree once; changing the draw keeps focus on the draw picker.
    var tookFocus by remember { mutableStateOf(false) }

    val openedRound = rounds.indexOfFirst { r -> r.ties.any { isOpenedTie(it, focusMatch) } }
    val openedIndex = if (openedRound >= 0) rounds[openedRound].ties.indexOfFirst { isOpenedTie(it, focusMatch) } else -1
    val path = remember(draw.name, drawIndex, focusMatch.id) { pathOf(rounds, openedRound, openedIndex) }
    val startRound = if (openedRound >= 0) openedRound else currentRound(rounds, m.now)
    val startIndex = if (openedIndex >= 0) openedIndex else 0

    Column(modifier.fillMaxSize()) {
        // Round names stay pinned on top while the columns scroll underneath them.
        Box(Modifier.fillMaxWidth().height(TREE_LABEL_H).clipToBounds()) {
            Row(Modifier.offset(x = (-scrollX.value).dp).height(TREE_LABEL_H)) {
                rounds.forEachIndexed { r, round ->
                    Box(Modifier.width(TREE_CARD_W).padding(end = TREE_GAP)) {
                        T(roundLabel(round), s.body, 20, if (r == openedRound) s.accent else s.text, weight = 600)
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val vw = maxWidth.value
            val vh = maxHeight.value
            val cardW = TREE_CARD_W.value
            val cardH = TREE_CARD_H.value
            val gap = TREE_GAP.value
            val slot = TREE_SLOT.value
            val colStep = cardW + gap
            val leaves = rounds.first().ties.size
            val contentW = rounds.size * colStep - gap
            val contentH = slot * leaves
            val maxX = (contentW - vw).coerceAtLeast(0f)
            val maxY = (contentH - vh).coerceAtLeast(0f)
            // How many first-round slots one tie of this round covers (1, 2, 4, 8...).
            val spans = remember(rounds) {
                IntArray(rounds.size) { r -> (leaves / rounds[r].ties.size.coerceAtLeast(1)).coerceAtLeast(1) }
            }

            LaunchedEffect(vw, vh, draw.name, drawIndex) {
                if (centred || vw <= 0f || vh <= 0f) return@LaunchedEffect
                val unit = slot * spans[startRound.coerceIn(0, spans.lastIndex)]
                val y = unit * (startIndex + 0.5f) - cardH / 2f
                scrollX.value = (startRound * colStep - 90f).coerceIn(0f, maxX)
                scrollY.value = (y - (vh - cardH) / 2f).coerceIn(0f, maxY)
                centred = true
                delay(80)
                if (!tookFocus) {
                    tookFocus = true
                    runCatching { focusReq.requestFocus() }
                }
            }

            Box(
                Modifier.fillMaxSize().clipToBounds()
                    .focusRequester(focusReq)
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val mult = 1f + e.nativeKeyEvent.repeatCount.coerceAtMost(5)
                        when (e.key) {
                            Key.DirectionUp -> if (scrollY.value <= 0f) false else {
                                scrollY.value = (scrollY.value - TREE_STEP_Y.value * mult).coerceAtLeast(0f); true
                            }
                            Key.DirectionDown -> if (scrollY.value >= maxY) false else {
                                scrollY.value = (scrollY.value + TREE_STEP_Y.value * mult).coerceAtMost(maxY); true
                            }
                            // Nothing further left to scroll: Left leaves for the matches page.
                            Key.DirectionLeft -> if (scrollX.value <= 0f) {
                                onBack(); true
                            } else {
                                scrollX.value = (scrollX.value - TREE_STEP_X.value * mult).coerceAtLeast(0f); true
                            }
                            Key.DirectionRight -> if (scrollX.value >= maxX) false else {
                                scrollX.value = (scrollX.value + TREE_STEP_X.value * mult).coerceAtMost(maxX); true
                            }
                            else -> false
                        }
                    }
                    .focusable(),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    translate(left = -scrollX.value.dp.toPx(), top = -scrollY.value.dp.toPx()) {
                        drawTreeLines(s, rounds, spans, path, scrollX.value, scrollY.value, vw, vh)
                    }
                }
                rounds.forEachIndexed { r, round ->
                    val colX = r * colStep
                    if (colX > scrollX.value + vw + 40f || colX + cardW < scrollX.value - 40f) return@forEachIndexed
                    val n = round.ties.size
                    val unit = slot * spans[r]
                    val from = floor((scrollY.value - 300f - cardH / 2f) / unit - 0.5f).toInt().coerceAtLeast(0)
                    val to = floor((scrollY.value + vh + 300f + cardH / 2f) / unit - 0.5f).toInt().coerceAtMost(n - 1)
                    for (i in from..to) {
                        val tie = round.ties.getOrNull(i) ?: continue
                        Box(
                            Modifier.offset(
                                x = (colX - scrollX.value).dp,
                                y = (unit * (i + 0.5f) - cardH / 2f - scrollY.value).dp,
                            ).width(TREE_CARD_W).height(TREE_CARD_H),
                        ) {
                            TreeTieCard(s, m, tie, isOpenedTie(tie, focusMatch))
                        }
                    }
                }
            }
        }
    }
}

private fun roundLabel(r: BracketRound): String {
    val name = r.name.ifEmpty { "Round ${r.number}" }
    val n = r.ties.size
    return "$name · $n ${if (n == 1) "tie" else "ties"}"
}

/** `round * 100000 + index`, for marking the opened tie's path through the tree. */
private fun tieKey(round: Int, index: Int) = round.toLong() * 100_000L + index

/** The opened tie and every parent it feeds, up to the final. */
private fun pathOf(rounds: List<BracketRound>, round: Int, index: Int): Set<Long> {
    if (round < 0 || index < 0) return emptySet()
    val out = HashSet<Long>()
    var i = index
    var r = round
    out += tieKey(r, i)
    while (r + 1 < rounds.size) {
        i /= 2
        r++
        out += tieKey(r, i)
    }
    return out
}

/** The drawn bracket: pairs of child ties funnel into one parent, with the opened match's path lit. */
private fun DrawScope.drawTreeLines(
    s: PageStyle,
    rounds: List<BracketRound>,
    spans: IntArray,
    path: Set<Long>,
    scrollX: Float,
    scrollY: Float,
    vw: Float,
    vh: Float,
) {
    val cardW = TREE_CARD_W.value
    val cardH = TREE_CARD_H.value
    val gap = TREE_GAP.value
    val slot = TREE_SLOT.value
    val colStep = cardW + gap
    val top = scrollY - 400f
    val bottom = scrollY + vh + 400f

    for (k in 1 until rounds.size) {
        val x1 = (k - 1) * colStep + cardW
        val x2 = k * colStep
        // Only the connectors whose column is in view.
        if (x2 < scrollX - 40f || x1 > scrollX + vw + 40f) continue
        val mid = x1 + gap / 2f
        val children = rounds[k - 1].ties.size
        val parents = rounds[k].ties.size
        val unit = slot * spans[k]
        val childUnit = slot * spans[k - 1]
        val from = floor((top - cardH / 2f) / unit - 0.5f).toInt().coerceAtLeast(0)
        val to = floor((bottom + cardH / 2f) / unit - 0.5f).toInt().coerceAtMost(parents - 1)
        for (j in from..to) {
            val c1 = 2 * j
            val c2 = c1 + 1
            if (c2 >= children) continue
            val y1 = childUnit * (c1 + 0.5f)
            val y2 = childUnit * (c2 + 0.5f)
            val yp = unit * (j + 0.5f)
            if (maxOf(y1, y2, yp) < top || minOf(y1, y2, yp) > bottom) continue
            val onPath = tieKey(k, j) in path
            val color = if (onPath) s.accent else s.border
            val stroke = if (onPath) 3f else 2f
            drawLine(color, Offset(x1.dp.toPx(), y1.dp.toPx()), Offset(mid.dp.toPx(), y1.dp.toPx()), stroke)
            drawLine(color, Offset(x1.dp.toPx(), y2.dp.toPx()), Offset(mid.dp.toPx(), y2.dp.toPx()), stroke)
            drawLine(color, Offset(mid.dp.toPx(), y1.dp.toPx()), Offset(mid.dp.toPx(), y2.dp.toPx()), stroke)
            drawLine(color, Offset(mid.dp.toPx(), yp.dp.toPx()), Offset(x2.dp.toPx(), yp.dp.toPx()), stroke)
        }
    }
}

/** The most relevant round when the opened match is not in the draw: live, then the next kick-off, then the last round. */
private fun currentRound(rounds: List<BracketRound>, now: Instant): Int {
    rounds.forEachIndexed { i, r -> if (r.ties.any { it.isLive }) return i }
    var best = -1
    var bestAt = Instant.MAX
    rounds.forEachIndexed { i, r ->
        r.ties.forEach { t ->
            if (t.kickoff > now && t.kickoff < bestAt && t.state == MatchState.UPCOMING) {
                bestAt = t.kickoff
                best = i
            }
        }
    }
    if (best >= 0) return best
    rounds.forEachIndexed { i, r -> if (r.ties.any { it.kickoff != Instant.EPOCH && it.kickoff <= now }) return i }
    return rounds.lastIndex.coerceAtLeast(0)
}

private fun isOpenedTie(tie: Tie, focus: Match?): Boolean {
    if (focus == null) return false
    if (tie.id.isNotEmpty() && tie.id == focus.id) return true
    val h = tie.home.id
    val a = tie.away.id
    return (h == focus.home.id && a == focus.away.id) || (h == focus.away.id && a == focus.home.id)
}

@Composable
private fun TreeTieCard(s: PageStyle, m: HomeModel, tie: Tie, opened: Boolean) {
    val live = tie.isLive
    Column(
        Modifier.fillMaxSize()
            .box(if (opened) lerp(s.surface, s.bg, 0.82f) else s.surface, s.border, 1.dp, RoundedCornerShape(s.radius.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TreeTeamRow(s, tie, home = true, opened = opened)
        TreeTeamRow(s, tie, home = false, opened = opened)
        Spacer(Modifier.weight(1f))
        // A bye has nothing to say beyond the team line itself.
        if (tie.home.id.isEmpty() || tie.away.id.isEmpty()) return@Column
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Dot(s.red, 7)
                Spacer(Modifier.width(5.dp))
                T("LIVE ${tie.statusLabel}", s.body, 15, s.red, weight = 600, spacing = 0.05f)
            } else {
                T(statusLine(m, tie), s.body, 15, s.muted)
            }
            Spacer(Modifier.weight(1f))
            if (tie.pens.isNotEmpty()) T(tie.pens, s.body, 15, s.muted)
        }
    }
}

@Composable
private fun TreeTeamRow(s: PageStyle, tie: Tie, home: Boolean, opened: Boolean) {
    val side = if (home) tie.home else tie.away
    val other = if (home) tie.away else tie.home
    val won = tie.winner == if (home) 1 else 2
    val score = if (home) tie.homeScore else tie.awayScore
    val name = when {
        side.id.isNotEmpty() -> side.name
        other.id.isNotEmpty() -> "Bye"
        else -> "TBD"
    }
    val color = when {
        won -> s.text
        side.id.isEmpty() -> s.muted
        tie.winner != 0 -> s.muted
        else -> s.text
    }
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (side.img.isNotEmpty()) {
            TeamLogoSafe(side, 18)
            Spacer(Modifier.width(7.dp))
        } else {
            Spacer(Modifier.width(25.dp))
        }
        T(name, s.body, 21, color, modifier = Modifier.weight(1f), weight = if (won) 600 else 400)
        if (score.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            T(score, s.numbers, 22, s.text, weight = 600)
        }
    }
}

private fun statusLine(m: HomeModel, tie: Tie): String {
    val zone = m.day.zone
    return when {
        tie.kickoff == Instant.EPOCH -> tie.statusLabel.ifEmpty { "Date TBC" }
        tie.state == MatchState.FINISHED -> tie.statusLabel + " · " + DateText.dayLabel(tie.kickoff, zone, m.now)
        tie.state == MatchState.UPCOMING -> DateText.dayLabel(tie.kickoff, zone, m.now) + " · " + DateText.time(tie.kickoff, zone)
        else -> tie.statusLabel.ifEmpty { "TBC" }
    }
}

// ------------------------------------------------------------------ tabs

private fun drawHasMatch(c: Competition, match: Match): Boolean =
    c.bracket?.draws.orEmpty().any { d -> d.rounds.any { r -> r.ties.any { isOpenedTie(it, match) } } }

/** The stage the opened match belongs to, so a two-phase season opens on the phase being played. */
private fun stageFor(stages: List<com.prayerhome.tv.football.TableStage>, league: String, stageId: String): Int {
    if (stageId.isNotEmpty()) stages.forEachIndexed { i, st -> if (st.id == stageId) return i }
    val l = league.lowercase()
    stages.forEachIndexed { i, st -> if (l.contains(st.name.lowercase())) return i }
    stages.forEachIndexed { i, st -> if (l.endsWith(st.shortName.lowercase())) return i }
    return 0
}

@Composable
private fun StagePicker(s: PageStyle, d: Competition, league: String, stageId: String, stageIdx: Int, onPick: (Int) -> Unit) {
    val selected = stageIdx.takeIf { it in d.tables.indices } ?: stageFor(d.tables, league, stageId)
    d.tables.forEachIndexed { i, stage -> TabPill(s, stage.shortName, i == selected) { onPick(i) } }
}

@Composable
private fun DrawPicker(s: PageStyle, d: Competition, drawIdx: Int, onPick: (Int) -> Unit) {
    val draws = d.bracket?.draws.orEmpty()
    val selected = drawIdx.coerceIn(0, (draws.size - 1).coerceAtLeast(0))
    draws.forEachIndexed { i, draw -> TabPill(s, draw.name.ifEmpty { "Draw ${i + 1}" }, i == selected) { onPick(i) } }
}

@Composable
private fun TabPill(s: PageStyle, text: String, selected: Boolean, onClick: () -> Unit) {
    Focusable(onClick = onClick) { focused ->
        Row(
            Modifier.box(
                if (focused && !selected) s.surfaceFocus else if (selected) s.accent else s.surface,
                if (focused || selected) s.accent else s.border,
                if (focused) 2.dp else 1.dp,
                RoundedCornerShape(999.dp),
            ).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(text, s.body, 20, if (selected) s.bg else if (focused) s.text else s.muted, weight = if (selected) 600 else 400)
        }
    }
}
