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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.App
import com.prayerhome.tv.core.TeamRef
import com.prayerhome.tv.football.LiveScore
import com.prayerhome.tv.football.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ADD_KEY = "+add"

/** A nav whose only working button is the header's back pill, for the Matches page's sub-screens. */
private fun backOnly(onBack: () -> Unit) = Nav(apps = {}, matches = {}, settings = {}, home = onBack, launch = {})

/** Club badge, or the club's initial in a ring when LiveScore has no badge for it. */
@Composable
private fun Badge(s: PageStyle, id: String, name: String, img: String, size: Int) {
    if (img.isNotEmpty()) TeamLogo(Side(id, name, "", img), size)
    else Box(Modifier.size(size.dp).box(s.surfaceFocus, s.border, 1.dp, CircleShape), contentAlignment = Alignment.Center) {
        T(name.take(1).uppercase(), s.body, (size * 0.46f).toInt(), s.muted)
    }
}

/**
 * The Matches page's team list: "Add a team" first, then one card per team; OK on a team removes it.
 * Back (or the header's Matches pill) returns to the match list.
 */
@Composable
fun TeamsScreen(s: PageStyle, m: HomeModel, onBack: () -> Unit, onAdd: () -> Unit) {
    val app = App.of(LocalContext.current)
    var teams by remember { mutableStateOf(app.prefs.teams) }
    val scope = rememberCoroutineScope()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(key: String) = requesters.getOrPut(key) { FocusRequester() }
    // which card gets focus: the add card at first, the neighbour of a removed team afterwards
    var focusKey by remember { mutableStateOf(ADD_KEY) }
    var focusTick by remember { mutableStateOf(0) }

    // Teams added before badges were stored (the defaults) borrow one from the fixtures or the team index.
    var badges by remember { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(Unit) {
        val fromIndex = withContext(Dispatchers.IO) { app.matches.loadTeamIndex() }.associate { it.id to it.img }
        val fromMatches = m.allMatches.flatMap { listOf(it.home, it.away) }.associate { it.id to it.img }
        badges = (fromIndex + fromMatches).filterValues { it.isNotEmpty() }
    }

    LaunchedEffect(focusTick) {
        delay(40)
        runCatching { req(focusKey).requestFocus() }
    }

    // OK once arms a team ("OK again to remove"), OK again removes it, so a stray press is harmless.
    var armed by remember { mutableStateOf<String?>(null) }

    fun remove(t: TeamRef) {
        armed = null
        val i = teams.indexOfFirst { it.id == t.id }
        teams = teams.filterNot { it.id == t.id }
        app.prefs.teams = teams
        scope.launch { app.matches.refreshAll() }
        m.toast = "Removed ${t.name}"
        focusKey = teams.getOrNull(i)?.id ?: teams.getOrNull(i - 1)?.id ?: ADD_KEY
        focusTick++
    }

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            PageHeader(s, m, "My teams", backOnly(onBack), backLabel = "Matches") {
                T(if (teams.isEmpty()) "Add a team to see its matches" else "OK twice on a team removes it", s.body, 22, s.muted)
            }
            SectionLabel(s, "${teams.size} ${if (teams.size == 1) "TEAM" else "TEAMS"} · KICK-OFF AND GOAL BANNERS FOLLOW THE NOTIFICATIONS BUTTON")
            LazyVerticalGrid(
                GridCells.Fixed(3),
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                item(key = ADD_KEY) {
                    Focusable(Modifier.fillMaxWidth().height(104.dp), onClick = onAdd, focusRequester = req(ADD_KEY)) { focused ->
                        Row(
                            Modifier.matchParentSize()
                                .let { if (focused) it.box(s.surfaceFocus, s.accent, s.focusBorder.dp, RoundedCornerShape(s.radius.dp)) else it.dashed(s.muted, s.radius.dp) }
                                .padding(horizontal = 26.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(60.dp).box(if (focused) s.accent else s.surface, s.accent, 1.dp, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Ico.PLUS, if (focused) s.bg else s.accent, 30)
                            }
                            Spacer(Modifier.width(20.dp))
                            T("Add a team", s.body, 28, if (focused) s.text else s.muted)
                        }
                    }
                }
                items(teams, key = { it.id }) { t ->
                    Focusable(Modifier.fillMaxWidth().height(104.dp), onClick = { if (armed == t.id) remove(t) else armed = t.id }, focusRequester = req(t.id)) { focused ->
                        LaunchedEffect(focused) { if (!focused && armed == t.id) armed = null }
                        Row(
                            Modifier.matchParentSize()
                                .box(if (focused) s.surfaceFocus else s.surface, if (focused) s.accent else s.border, if (focused) s.focusBorder.dp else 1.dp, RoundedCornerShape(s.radius.dp))
                                .padding(horizontal = 26.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Badge(s, t.id, t.name, t.img.ifEmpty { badges[t.id].orEmpty() }, 60)
                            Spacer(Modifier.width(20.dp))
                            T(t.name, s.body, 28, s.text, modifier = Modifier.weight(1f))
                            if (focused) {
                                if (armed == t.id) T("OK again to remove", s.body, 22, s.red, weight = 600)
                                else T("OK to remove", s.body, 22, s.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Search this week's fixtures for a club and add it. [onDone] returns to the team list. */
@Composable
fun TeamSearch(s: PageStyle, m: HomeModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    var query by remember { mutableStateOf("") }
    var index by remember { mutableStateOf<List<LiveScore.TeamInfo>>(emptyList()) }
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    // The text field keeps ▼ for its cursor: send it (and the keyboard's Search key) on to the results.
    fun toResults() {
        keyboard?.hide()
        runCatching { firstResult.requestFocus() }
    }

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
            PageHeader(s, m, "Add a team", backOnly(onDone), backLabel = "Teams") {
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
                    keyboardActions = KeyboardActions(onSearch = { if (results.isNotEmpty()) toResults() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field).onPreviewKeyEvent { e ->
                        if (e.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN && results.isNotEmpty()) {
                            if (e.type == KeyEventType.KeyDown) toResults()
                            true
                        } else false
                    },
                    decorationBox = { inner ->
                        if (query.isEmpty()) T("Type a club name, e.g. Ahly, Juventus…", s.body, 34, s.muted)
                        inner()
                    },
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
                items(results, key = { it.id }) { t ->
                    Focusable(Modifier.fillMaxWidth().height(84.dp), focusRequester = if (t.id == results.first().id) firstResult else null, onClick = {
                        app.prefs.teams = app.prefs.teams + TeamRef(t.id, t.name, t.img)
                        scope.launch { app.matches.refreshAll() }
                        m.toast = "Added ${t.name}"
                        onDone()
                    }) { focused ->
                        Row(
                            Modifier.matchParentSize().box(if (focused) s.surfaceFocus else s.surface, if (focused) s.accent else s.border, if (focused) 2.dp else 1.dp, RoundedCornerShape(s.radius.dp)).padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Badge(s, t.id, t.name, t.img, 52)
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
