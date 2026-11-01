# Prayer Home

A lightweight home screen (launcher) for Google TV / Android TV, built around the prayer day:

- clock, English date and the Hijri date, in Arabic or English
- the day's prayer times, with a countdown to the next one that turns red when the chime is due
- a chime 5–30 minutes (default 10) before each prayer, even while you're watching another app
- your football teams' matches with club badges, live minute and score, plus silent kick-off and
  goal banners over other apps
- highlights for a finished match: its mute button turns into a video button, and the longest
  YouTube highlights from the competitions', clubs' and broadcasters' channels play in the YouTube app
- the standings of any match's competition: the league table, the cup's knockout tree, or both, with
  the teams currently on the pitch marked by a red LIVE tag and the panel refreshing itself every 30
  seconds while a match is on or about to start
- who scored, and when, in the match you opened: the scorer's minute, name and badge beside the table
- a Quran button that opens the companion **[Quran Reader](https://github.com/theyousssef/quran-reader-tv)** app (a separate app; Prayer Home only opens it)
- three pinned apps on Home, everything else on an Apps page
- keeps the TV's screensaver from covering the prayer screen
- ten themes, switchable from Settings

Made for and tested on a TCL Google TV (Android 14). It should work on other Google TV and
Android TV devices running Android 9 or newer, but only that TV has been tested.

## Install

You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools)
and the TV on the same network.

1. On the TV, turn on developer options: Settings → System → About → click **Android TV OS build**
   7 times. Then Settings → System → Developer options → turn on **Wireless debugging**
   and note the IP address and port it shows. The first time, choose **Pair device with pairing
   code** and run `adb pair <ip:pairing-port> <code>` on your computer.
2. Download `prayer-home.apk` from the [latest release](../../releases/latest).
3. Run:

   ```sh
   ./install-to-tv.sh <tv-ip:port> prayer-home.apk
   ```

   or, without the script:

   ```sh
   TV=<tv-ip:port>
   adb connect $TV
   adb -s $TV install -r prayer-home.apk
   adb -s $TV shell appops set com.prayerhome.tv SYSTEM_ALERT_WINDOW allow
   adb -s $TV shell appops set com.prayerhome.tv AUTO_START allow    # TCL TVs only
   adb -s $TV shell appops set com.prayerhome.tv ASSOC_START allow   # TCL TVs only
   adb -s $TV shell pm grant com.prayerhome.tv android.permission.WRITE_SECURE_SETTINGS
   adb -s $TV shell appops set com.prayerhome.tv REQUEST_INSTALL_PACKAGES allow
   # turn on Prayer Home's home-screen service, keeping any others that are on
   adb -s $TV shell settings get secure enabled_accessibility_services   # → <current>
   adb -s $TV shell settings put secure enabled_accessibility_services "<current>:com.prayerhome.tv/com.prayerhome.tv.core.HomeGuardService"
   adb -s $TV shell settings put secure accessibility_enabled 1
   ```

Google TV ignores other home apps while its own launcher is enabled. Instead of switching Google's
launcher off, Prayer Home turns on a small accessibility service that opens Prayer Home whenever the
Google TV home screen comes up: after a restart, on Home, or on Back from an app. You may see the
Google TV screen for a moment first.

Earlier versions switched the Google TV launcher (`launcherx`) and its setup app (`setupwraith`) off.
Don't do that: on a Chromecast with Google TV it leaves a black screen at every restart, and without
`setupwraith` the device drops out of the Google Home app. If you did, turn them back on with the
first two lines under *Undo*.

`SYSTEM_ALERT_WINDOW` lets the chime and match banners show over other apps. `WRITE_SECURE_SETTINGS`
lets Prayer Home switch the screensaver off (Settings → Screensaver); without it that setting does
nothing. On TCL TVs, `AUTO_START` and `ASSOC_START` let the chime and match alarms start Prayer Home
when Android has closed it in the background; without them TCL's app guard drops those alarms
silently, and match banners stop until Home is opened again. `REQUEST_INSTALL_PACKAGES` (*Install unknown apps*)
lets Prayer Home install its own updates; without it the TV asks before each update. If it's
off, Home explains this and offers to open the switch for you.

### Remote buttons

If you switched the Google TV launcher off, the remote's YouTube, Settings and TV buttons may do
nothing, because Android sends them only to that launcher. Install the companion app
**[Custom Mapper](https://github.com/theyousssef/custom-mapper-tv)** to make them work again. You
can also make any button open what you like.

### Undo (bring back the Google TV home screen)

```sh
TV=<tv-ip:port>
adb -s $TV shell pm enable com.google.android.apps.tv.launcherx
adb -s $TV shell pm enable com.google.android.tungsten.setupwraith
adb -s $TV shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
adb -s $TV uninstall com.prayerhome.tv   # optional
```

Without uninstalling, also switch *Prayer Home* off in Settings → System → Accessibility, or it keeps
taking over the Google TV home screen.

## Using it

| Where | Remote |
|---|---|
| Home | Arrows move, OK opens. The matches panel opens the Matches page, even before any team is picked. Under *All apps* are two small buttons: **Quran** opens the Quran Reader app and **Settings** opens Settings. |
| All apps | OK opens an app. **Hold OK** pins or unpins it (Home shows 3). |
| Matches | All the football settings are in the header (▲ from the matches, ◀ ▶ between buttons). **Notifications** turns kick-off and goal banners on or off for every match. **Teams** opens your team list with club badges: *Add a team* searches this week's fixtures, and OK twice on a team removes it. **Refresh** reloads the match data. OK on a match mutes or unmutes just that match; a small red dot marks a match that differs from the Notifications setting. ◀ on the **‹ Home** pill leaves the page without pressing OK. Once a match has ended its bell becomes **Highlights**: OK opens up to three YouTube highlight videos, longest first, from the competitions', clubs' and broadcasters' official channels only (beIN SPORTS, AD Sports, TOD, MBC Action, Sports United, ON Sport, and Kora Plus for Egyptian football), in Arabic and English; OK on a video plays it in the TV's YouTube app. Right of that button, the **standings** button (three podium steps) opens the competition's table or knockout tree; pressing **Right** on the match row opens it too. With no teams yet, the page shows an *Add a team* button. Back closes the highlights, standings, team list or search first. |
| Settings | ◀ ▶ change a value, OK selects. Theme (OK opens a picker with previews), Arabic or English prayer names and Hijri date, location, calculation method, Asr, per-prayer minute offsets, Hijri ±days, chime on/off, chime time (5, 10, 15, 20 or 30 minutes before), chime volume (20–100 % of the TV's volume), banner over other apps, screensaver. A last row opens the Matches page, where the football settings are. |
| Anywhere | Back returns to Home. The Home button always returns to Home. |

The defaults are Giza, Egypt, and the Egyptian General Authority of Survey method. Change the
location (22 cities) and the method in Settings. Opening the TV app from the Apps page tunes it
to the TV's antenna/satellite tuner, not the last HDMI input.

## How it works

- **Prayer times** are calculated on the TV with the PrayTimes method, so no internet is
  needed. For Giza they match Aladhan to the minute, except that Asr is sometimes 1 minute
  earlier because of rounding. Settings has per-prayer offsets.
- **Daylight saving.** Everything is computed in UTC and shown in local time through Android's
  timezone database, so DST changes apply automatically, including a countdown that runs across
  the switch. The alarm re-arms itself after reboots and time or timezone changes.
- **Hijri date** uses Umm al-Qura, with a ±2 day adjustment for local moon sighting.
- **Chime.** One exact alarm fires before the next prayer (10 minutes unless changed in Settings);
  the countdown turns red at the same moment. It plays over other apps, lowering their sound, and
  shows a small banner. Its volume is a percentage of the TV's current volume. It stays silent while
  the TV is in standby.
- **Football.** A fresh install starts with 18 teams — Zamalek, Al Ahly, Pyramids, Real Madrid,
  Barcelona, Atlético Madrid, Liverpool, Manchester City, Manchester United, Arsenal, Chelsea,
  Tottenham, Bayern Munich, Paris Saint-Germain, AC Milan, Inter, Juventus and Trabzonspor — and
  you can add or remove any of them from *Teams*. Data comes from LiveScore's public JSON feed:
  free, no key, and unofficial, so it could change. Fixtures from two days ago to a week ahead refresh every 2 hours. The Matches page
  lists the last 48 hours and the next 24. While Home is visible and one of your teams is playing,
  the live score is checked every 15 seconds.
- **Standings.** The podium button on a match row opens that competition's standings, from the same
  feed: a league table (P, W, D, L, GD, PTS, with a stage picker for split seasons and cup groups),
  a knockout bracket with connecting lines, or both, switched with Table / Bracket above the
  content. The table is compact and dense (large names and numbers) and puts the match you opened
  from beside it; its two teams stay highlighted in the accent colour even when they are not playing,
  as a darker shade of the page (no accent outline), and a live team keeps its normal row with only a
  red `LIVE 67'` tag and its points in the same red. The table always opens at #1 and only scrolls when the higher of your two teams
  is below the first screenful — two teams in the same match (say 4th and 17th) leave it at the top. The UCL league phase is split after 8th and 24th, and a group phase published as
  one stage per group (a World Cup's twelve groups, the CAF Champions League's four) is merged into a
  single table. A competition the feed lists with no competition id at all (Egypt's League Cup, for
  one) is read from its stage instead, and a cup whose groups are one stage each is shown as a single
  table running down from Group A. The bracket is a real
  tree with connecting lines, and it scrolls in both directions: every round and every tie of the
  draw is reachable with the D-pad, your tie's path to the final is lit in the accent colour, and
  only the ties in view are composed, so a 176-tie round stays cheap. The table and the tree are
  plain scroll surfaces — the D-pad scrolls, nothing gets selected, and your two teams are the only
  marks. With nothing further to scroll left (or in the table, which has no horizontal scroll), ◀
  leaves the panel for the matches; ◀ on the *‹ Matches* pill does the same, and ◀ on any page's
  *‹ Home* pill goes home. The panel opens from a saved copy (kept for a week, and that week restarts every time the
  competition is opened). The table is downloaded again only while a match of this competition is
  live, around its kick-off (±5 minutes), or right after a recent result, and then every 30 seconds;
  otherwise nothing is fetched and the saved numbers stand until the next kick-off. The small live
  feed still ticks the minutes and keeps the live tag red, and a tie that finishes while the panel is
  open shows its final whistle with the last score. 36-team league phases get the Top 8 / 24 cut
  lines.

  The card beside the table shows the match's **score, minute and goals**. The first two are the
  live feed's own numbers — the very Match the matches list, the home screen and the banners use —
  so the card always agrees with them, to the second. The goals come from LiveScore's own match page
  (minute, scorer and badge, oldest first) and are read **the moment the score changes**, so a new
  goal shows its scorer within one 15-second poll, then at most every 30 seconds; a finished match
  is read once, again a couple of minutes later for late corrections and own-goal reassignments, and
  its copy is then kept for a week. Nothing is read before kick-off or for a postponed tie. The copy
  on disk opens the card with its goals already on screen. The card scrolls on its own (Right from
  the table, Left to come back), so a seven-goal game or more needs no squeezing.
- **Highlights.** For a finished match the Matches page searches YouTube for its highlights, with
  no API key: a normal results page in English and in Arabic (club names are translated, with a
  built-in list for the common clubs, and cached). A video is kept only when it comes from an
  official channel — a competition or club channel, beIN SPORTS, AD Sports, TOD, MBC Action,
  Sports United, ON Sport, or Kora Plus for Egyptian football — and when the title names both clubs
  (in either language), the score doesn't contradict it, the season matches, and it was uploaded
  after kick-off. That recognises uploads with different naming styles while dropping last season's
  meeting of the same two clubs and fan re-uploads. If the two ordinary searches don't already
  give three videos, the search is repeated inside beIN SPORTS, AD Sports, TOD and Kora Plus, so
  the common case costs two pages. The three longest are shown, cached for 12 hours (15 minutes when
  nothing was found, so late uploads appear), and play directly in the TV's own YouTube app.
- **Match notifications.** While you're in another app, a chain of exact alarms checks the small
  live feed every 30 seconds, from 5 minutes before one of your teams kicks off until the final
  whistle, and only for matches whose notifications are on. A kick-off or a goal shows a silent
  banner in the corner, like the chime's. Nothing is scheduled when no match is near, except a
  fixture refresh every 6 hours, and the alarms never wake a TV in standby. Each match's last seen
  score is kept, so the first load, or catching up after standby, doesn't announce old goals.
- **Screensaver.** With Settings → Screensaver Off (the default), Prayer Home sets Android's
  `screensaver_enabled`, `screensaver_activate_on_sleep` and `screensaver_activate_on_dock` to 0 when
  it starts and whenever Home appears. That covers Google's Backdrop and TCL's own gallery
  screensaver, which are both Android "dreams". Nothing keeps the screen on, so the TV still
  sleeps on its own timers; without a screensaver, Android turns the screen off when the idle time
  that used to start the screensaver runs out (10 minutes on the test TV). Switching back to On
  restores the TV's own values.
- **Quran Reader.** The Quran button opens `com.prayerhome.quran`, or says it isn't installed.
  That is the only link between the two apps: they don't talk to each other. The reader itself asks
  whether to keep reciting when you leave it, and works the same with any launcher. The reader
  stays in the All apps list too, so it can be pinned.
- **Updates.** Once a day Prayer Home asks this repository for its latest release. When the
  release is newer than the installed version, it downloads the APK and installs it over itself
  the next time Home isn't on screen, without asking once *Install unknown apps* is allowed for it
  (the install script does that). Your settings stay. While that permission is off, Home explains
  why it's needed and opens the switch for you (asked again a week later after *Not now*), and the
  TV's install prompt shows the next time Home opens. A release's tag is its version (`v1.6`) and it carries `prayer-home.apk`, signed with the
  same key as the installed app.
- **Footprint.** About a 5 MB APK and about 60 MB of memory.

## Build

JDK 21 is required; Gradle 8.13 fails on JDK 25.

```sh
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleRelease
./install-to-tv.sh <tv-ip:port>          # builds, installs and sets it as Home
```

The release build is signed with your local debug key, which is fine for sideloading.

Preview helpers:

```sh
adb shell am start -n com.prayerhome.tv/.MainActivity --ei theme 3 --es screen apps
```

`screen` can be `home`, `apps`, `matches` or `settings`; `--es lang en` (or `ar`) switches the
language. Debug builds also accept `--el shift <seconds>` to preview another time of day, for
example the red phase before a prayer, `--es simulate kickoff` (or `goal`) with an optional
`--ei delay <seconds>` to fire a match banner after you've opened another app, and `--es toast <text>`.

To try the chime and its banner without waiting for a prayer:

```sh
adb shell am broadcast -n com.prayerhome.tv/.alarm.ChimeReceiver -a com.prayerhome.tv.CHIME --es prayer ASR
```

## Credits

The fonts in `app/src/main/res/font` are Google Fonts families under the SIL Open Font License
1.1. They are cut down to the weights and Latin and Arabic characters the app uses. Each family's
license is in [`licenses/fonts`](licenses/fonts).
