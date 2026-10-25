# Prayer Home

A lightweight home screen (launcher) for Google TV / Android TV, built around the prayer day:

- clock, English date and the Hijri date in Arabic
- the day's prayer times, with a countdown to the next one that turns red in the last 10 minutes
- a chime 10 minutes before each prayer, even while you're watching another app
- your football teams' matches with club badges, live minute and score
- three pinned apps on Home, everything else on an Apps page
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
   adb -s $TV shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
   adb -s $TV shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
   adb -s $TV shell cmd package set-home-activity com.prayerhome.tv/.MainActivity
   ```

Google TV ignores other home apps while its own launcher, and its setup app's fallback home
screen, are enabled, so both are switched off. Undo below.

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
| Home | Arrows move, OK opens. The matches panel opens the Matches page. The small **Settings** button under *All apps* opens Settings. |
| All apps | OK opens an app. **Hold OK** pins or unpins it (Home shows 3). |
| Settings | ◀ ▶ change a value, OK selects. Theme (OK opens a picker with previews), location, calculation method, Asr, per-prayer minute offsets, Hijri ±days, chime on/off/volume, teams. |
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
- **Chime.** One exact alarm fires 10 minutes before the next prayer. It plays over other apps,
  lowering their sound, and shows a small banner. It stays silent while the TV is in standby.
- **Football.** Data comes from LiveScore's public JSON feed: free, no key, and unofficial, so it
  could change. Fixtures from yesterday to a week ahead refresh every 2 hours. While Home is
  visible and one of your teams is playing, the live score is checked every 15 seconds. Nothing
  is checked while you're in another app.
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

`screen` can be `home`, `apps`, `matches` or `settings`. Debug builds also accept
`--el shift <seconds>` to preview another time of day, for example the red last-10-minutes state.

## Credits

The fonts in `app/src/main/res/font` are Google Fonts families under the SIL Open Font License
1.1. They are cut down to the weights and Latin and Arabic characters the app uses. Each family's
license is in [`licenses/fonts`](licenses/fonts).
