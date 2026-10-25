# Prayer Home

A lightweight home screen (launcher) for Google TV / Android TV, built around the prayer day:

- clock, English date and the Hijri date, in Arabic or English
- the day's prayer times, with a countdown to the next one that turns red when the chime is due
- a chime 5–30 minutes (default 10) before each prayer, even while you're watching another app
- your football teams' matches with club badges, live minute and score, plus silent kick-off and
  goal banners over other apps
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
   adb -s $TV shell pm grant com.prayerhome.tv android.permission.WRITE_SECURE_SETTINGS
   adb -s $TV shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
   adb -s $TV shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
   adb -s $TV shell cmd package set-home-activity com.prayerhome.tv/.MainActivity
   ```

Google TV ignores other home apps while its own launcher, and its setup app's fallback home
screen, are enabled, so both are switched off. Undo below.

`SYSTEM_ALERT_WINDOW` lets the chime and match banners show over other apps. `WRITE_SECURE_SETTINGS`
lets Prayer Home switch the screensaver off (Settings → Screensaver); without it that setting does
nothing.

> **Wi‑Fi warning.** If your Wi‑Fi network was saved during the TV's first-time setup, switching off
> the setup app (`setupwraith`) can make the TV forget it. Your adb connection then drops. If that
> happens, reconnect in Settings → Network & Internet. Once you re-add it there, the network is
> saved by Settings and stays saved.

### Remote buttons

With the Google TV launcher off, the remote's YouTube, Settings and TV buttons may do
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

## Using it

| Where | Remote |
|---|---|
| Home | Arrows move, OK opens. The matches panel opens the Matches page. Under *All apps* are two small buttons: **Quran** opens the Quran Reader app and **Settings** opens Settings. |
| All apps | OK opens an app. **Hold OK** pins or unpins it (Home shows 3). |
| Matches | The **Notifications** button in the header turns kick-off and goal banners on or off. OK on a match mutes or unmutes just that match; a small red dot marks a match that differs from the header setting. |
| Settings | ◀ ▶ change a value, OK selects. Theme (OK opens a picker with previews), Arabic or English prayer names and Hijri date, location, calculation method, Asr, per-prayer minute offsets, Hijri ±days, chime on/off, chime time (5, 10, 15, 20 or 30 minutes before), chime volume (20–100 % of the TV's volume), match notifications, teams, screensaver. |
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
- **Football.** Data comes from LiveScore's public JSON feed: free, no key, and unofficial, so it
  could change. Fixtures from yesterday to a week ahead refresh every 2 hours. While Home is
  visible and one of your teams is playing, the live score is checked every 15 seconds.
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
