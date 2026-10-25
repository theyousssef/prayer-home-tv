package com.prayerhome.tv.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.football.Match
import com.prayerhome.tv.football.MatchState
import com.prayerhome.tv.ui.themes.TeamLogoSafe
import java.time.Duration
import java.time.Instant

/** Your teams' matches from the last 24 hours and the next 24 hours, plus the three shown on Home. */
@Composable
fun MatchesPage(s: PageStyle, m: HomeModel, nav: Nav) {
    val first = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    val all = m.windowMatches
    val now = Instant.now()
    val live = all.filter { it.isLive }
    val earlier = all.filter { !it.isLive && it.kickoff <= now }.sortedByDescending { it.kickoff }
    val soon = all.filter { !it.isLive && it.kickoff > now && it.kickoff <= now.plus(Duration.ofHours(24)) }
    val later = all.filter { !it.isLive && it.kickoff > now.plus(Duration.ofHours(24)) }
    val sections = listOf("LIVE NOW" to live, "NEXT 24 HOURS" to soon, "LAST 24 HOURS" to earlier, "COMING UP" to later).filter { it.second.isNotEmpty() }
    val firstId = sections.firstOrNull()?.second?.firstOrNull()?.id

    LaunchedEffect(firstId == null) { runCatching { if (firstId != null) first.requestFocus() else back.requestFocus() } }

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            PageHeader(s, m, "Matches", nav, backFocus = back) {
                T("Last 24 h · next 24 h", s.body, 22, s.muted)
            }
            if (sections.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    T(if (m.scoresStatus.isNotEmpty()) "${m.scoresStatus} — retrying every minute" else "None of your teams play within 24 hours", s.body, 30, s.muted)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp)) {
                    for ((title, list) in sections) {
                        item(key = "h$title") { SectionLabel(s, title, Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                        items(list, key = { it.id }) { match ->
                            MatchRow(s, m, match, if (match.id == firstId) first else null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRow(s: PageStyle, m: HomeModel, match: Match, fr: FocusRequester?) {
    Focusable(Modifier.fillMaxWidth().height(112.dp), focusRequester = fr) { focused ->
        val live = match.isLive
        val border = if (focused) s.accent else if (live) s.red else s.border
        Row(
            Modifier.matchParentSize()
                .box(if (focused) s.surfaceFocus else s.surface, border, if (focused) s.focusBorder.dp else 1.dp, RoundedCornerShape(s.radius.dp))
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Box(Modifier.width(220.dp), contentAlignment = Alignment.CenterEnd) {
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
        }
    }
}
