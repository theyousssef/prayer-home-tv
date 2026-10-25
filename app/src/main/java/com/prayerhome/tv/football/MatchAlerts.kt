package com.prayerhome.tv.football

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.PowerManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.prayerhome.tv.App
import com.prayerhome.tv.alarm.Banner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

private const val TAG = "MatchAlerts"
private const val ACTION_TICK = "com.prayerhome.tv.MATCH_TICK"

/**
 * Kick-off and goal banners for your teams, over whatever app is on screen (silent).
 *
 * Every time match data arrives (Home's own 15 s loop, or the background alarm below) each match's
 * state and score are compared with the last ones seen, which are kept on disk. A banner fires only
 * when that last sighting is recent, so the first load, or catching up after standby, stays quiet.
 */
object MatchAlerts {
    /** Older sightings count as stale: catching up on them would announce old goals. */
    private const val FRESH_MS = 3 * 60_000L
    private const val KEEP_MS = 2 * 86_400_000L

    enum class Kind { KICKOFF, GOAL }

    private class Seen(val state: MatchState, val home: Int, val away: Int, val at: Long) {
        fun encode() = "${state.name}|$home|$away|$at"
    }

    private fun parse(s: String?): Seen? = runCatching {
        val p = s!!.split('|')
        Seen(MatchState.valueOf(p[0]), p[1].toInt(), p[2].toInt(), p[3].toLong())
    }.getOrNull()

    private fun seen(m: Match, now: Long) = Seen(m.state, m.homeScore.toIntOrNull() ?: 0, m.awayScore.toIntOrNull() ?: 0, now)

    /** Last sightings, mirrored on disk so a restarted process still knows the score. */
    private var memory: HashMap<String, Seen>? = null
    private var savedAt = 0L

    /** Records [matches] as just seen, firing banners for kick-offs and goals since the previous sighting. */
    @Synchronized
    fun observe(context: Context, matches: List<Match>) {
        val now = System.currentTimeMillis()
        val sp = context.getSharedPreferences("match-alerts", Context.MODE_PRIVATE)
        val mem = memory ?: HashMap<String, Seen>().also { map ->
            for ((k, v) in sp.all) parse(v as? String)?.let { map[k] = it }
            memory = map
        }
        var changed = false
        for (m in matches) {
            val prev = mem[m.id]
            if (prev != null && now - prev.at <= FRESH_MS) check(context, prev, m)
            // Keep the furthest state and score seen: the day feed can lag the live one by a poll,
            // and a score that flickers 2 → 1 → 2 must not announce the same goal twice.
            val cur = seen(m, now).let { c ->
                if (prev == null) return@let c
                val back = prev.state != MatchState.OFF && c.state != MatchState.OFF && prev.state.ordinal > c.state.ordinal
                Seen(if (back) prev.state else c.state, maxOf(prev.home, c.home), maxOf(prev.away, c.away), now)
            }
            if (prev == null || prev.state != cur.state || prev.home != cur.home || prev.away != cur.away) changed = true
            mem[m.id] = cur
        }
        mem.entries.removeAll { it.value.at < now - KEEP_MS }
        // The disk copy only needs to stay within FRESH_MS of the truth, so unchanged scores are written once a minute.
        if (changed || now - savedAt > 60_000L) {
            savedAt = now
            sp.edit().clear().apply { mem.forEach { (k, v) -> putString(k, v.encode()) } }.apply()
        }
    }

    private fun check(context: Context, prev: Seen, m: Match) {
        val cur = seen(m, 0)
        val kind = when {
            cur.home + cur.away > prev.home + prev.away && (cur.state == MatchState.LIVE || cur.state == MatchState.FINISHED) -> Kind.GOAL
            prev.state == MatchState.UPCOMING && cur.state == MatchState.LIVE -> Kind.KICKOFF
            else -> return
        }
        // which side scored, for the highlight (both when the feed jumped two goals at once)
        val scorer = when {
            kind != Kind.GOAL -> 0
            cur.home > prev.home && cur.away <= prev.away -> 1
            cur.away > prev.away && cur.home <= prev.home -> 2
            else -> 0
        }
        fire(context, m, kind, scorer)
    }

    private fun fire(context: Context, m: Match, kind: Kind, scorer: Int) {
        val prefs = App.prefs(context)
        val interactive = context.getSystemService(PowerManager::class.java).isInteractive
        Log.i(TAG, "$kind ${m.home.name} ${m.homeScore}-${m.awayScore} ${m.away.name} (alerts ${prefs.matchAlertsFor(m.id)}, screen $interactive)")
        if (!interactive || !prefs.matchAlertsFor(m.id)) return
        val logos = App.of(context).logos
        Banner.show(context, if (kind == Kind.GOAL) Banner.GOLD else Banner.MUTED) { ctx -> view(ctx, m, kind, scorer, logos) }
    }

    /** "GOAL  [badge] Liverpool  2 – 0  Brighton [badge]  68'" in the chime banner's type. */
    private fun view(ctx: Context, m: Match, kind: Kind, scorer: Int, logos: Logos): View {
        val dp = ctx.resources.displayMetrics.density
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun gap(w: Int) = View(ctx).also { row.addView(it, LinearLayout.LayoutParams((w * dp).toInt(), 1)) }
        fun badge(side: Side) {
            val f = logos.file(side) ?: return
            val bmp = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() ?: return
            val size = (26 * dp).toInt()
            row.addView(ImageView(ctx).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(size, size))
            gap(8)
        }
        val tag = if (kind == Kind.GOAL) "GOAL" else "KICK-OFF"
        row.addView(Banner.text(ctx, tag, 13f, if (kind == Kind.GOAL) Banner.GOLD else Banner.MUTED).apply { letterSpacing = 0.12f })
        gap(14)
        badge(m.home)
        val score = if (m.hasScore) "${m.homeScore} – ${m.awayScore}" else "v"
        val line = SpannableStringBuilder()
        fun name(s: String, hot: Boolean) {
            val start = line.length
            line.append(s)
            if (hot) line.setSpan(ForegroundColorSpan(Banner.GOLD), start, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        name(m.home.name, scorer == 1)
        line.append("   $score   ")
        name(m.away.name, scorer == 2)
        row.addView(Banner.text(ctx, line))
        gap(8)
        badge(m.away)
        if (m.isLive) {
            gap(6)
            row.addView(Banner.text(ctx, m.statusLabel, 13f, Banner.MUTED))
        }
        return row
    }

    /** Debug builds: run a synthetic kick-off or goal through the same path, using a cached match. */
    fun simulate(context: Context, kind: String) {
        val all = App.of(context).matches.matches.value
        val base = all.firstOrNull { it.isLive } ?: all.firstOrNull() ?: Match(
            "debug", Instant.now(), Side("456", "Zamalek", "ZAM", "enet/80591.png"), Side("0", "Al Ahly", "AHL", ""),
            "", "", "NS", 1, "Egypt Premier League",
        )
        val h = base.homeScore.toIntOrNull() ?: 0
        val a = base.awayScore.toIntOrNull() ?: 0
        val now = System.currentTimeMillis()
        if (kind == "kickoff") {
            check(context, Seen(MatchState.UPCOMING, 0, 0, now), base.copy(homeScore = "0", awayScore = "0", status = "1'", statusCode = 2))
        } else {
            val live = if (base.isLive) base else base.copy(status = "23'", statusCode = 2)
            check(context, Seen(MatchState.LIVE, h, a, now), live.copy(homeScore = "${h + 1}", awayScore = "$a"))
        }
    }
}

/**
 * Background polling while one of your teams is playing: a chain of exact alarms every 30 s, from
 * 5 minutes before kick-off until the final whistle, each doing one small "live" fetch.
 *
 * Alarms rather than a foreground service: nothing stays running between polls, nothing at all is
 * scheduled when no tracked match is near, and Android 14 asks no service type or notification. The
 * alarms are non-wakeup (RTC), so a TV in standby is never woken; the chain resumes when it wakes.
 */
object MatchWatch {
    private const val POLL_MS = 30_000L
    /** With no match coming up, look for new fixtures this often (only while the TV is awake). */
    private val IDLE_CHECK = Duration.ofHours(6)

    fun schedule(context: Context) {
        val app = App.of(context)
        val prefs = app.prefs
        val am = context.getSystemService(AlarmManager::class.java)
        val now = Instant.now()
        val pi = PendingIntent.getBroadcast(
            context, 2, Intent(context, MatchAlertReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val watched = app.matches.matches.value.filter { prefs.matchAlertsFor(it.id) }
        val at: Instant? = when {
            watched.isEmpty() && !prefs.matchAlerts -> null
            watched.any { MatchRepo.inLiveWindow(it, now) } -> now.plusMillis(POLL_MS)
            else -> {
                val nextLead = watched.filter { it.state == MatchState.UPCOMING && it.kickoff > now }.minOfOrNull { it.kickoff.minus(MatchRepo.LEAD) }
                listOfNotNull(nextLead, now.plus(IDLE_CHECK).takeIf { prefs.matchAlerts }).minOrNull()
            }
        }
        if (at == null) {
            am.cancel(pi)
            return
        }
        if (am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, at.toEpochMilli(), pi)
        else am.setWindow(AlarmManager.RTC, at.toEpochMilli(), 10_000, pi)
        Log.d(TAG, "next match check $at")
    }
}

class MatchAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TICK) return
        val app = App.of(context)
        val pending = goAsync()
        app.scope.launch {
            val job: Job = launch { runCatching { app.matches.backgroundTick() }.onFailure { Log.w(TAG, "tick failed", it) } }
            // a receiver gets about 10 s; a slow fetch carries on and is picked up by the next tick
            withTimeoutOrNull(9_000) { job.join() }
            MatchWatch.schedule(context)
            pending.finish()
        }
    }
}
