#!/usr/bin/env bash
# Build Prayer Home and install it on the TV as the Home app.
# Usage: ./install-to-tv.sh <tv-ip:port>      (Settings → System → Developer options → Wireless debugging)
#        ./install-to-tv.sh <tv-ip:port> path/to/prayer-home.apk    (install a downloaded APK instead of building)
set -e
TV=${1:?usage: ./install-to-tv.sh <tv-ip:port> [apk]}
APK=${2:+$(cd "$(dirname "$2")" && pwd)/$(basename "$2")}
cd "$(dirname "$0")"

if [[ -z $APK ]]; then
  ./gradlew --console=plain -q assembleRelease
  APK=app/build/outputs/apk/release/app-release.apk
fi

adb connect "$TV" >/dev/null
adb -s "$TV" install -r "$APK"

# compile ahead-of-time so the launcher opens instantly
adb -s "$TV" shell cmd package compile -m speed -f com.prayerhome.tv >/dev/null

# banners over other apps (the chime, and match kick-offs and goals)
adb -s "$TV" shell appops set com.prayerhome.tv SYSTEM_ALERT_WINDOW allow

# TCL's app-start guard (TclAppBoot) drops broadcasts to an app whose process isn't running, alarms
# included, unless it may auto-start. Without this a killed launcher misses its chime and match
# alarms, and the 30 s match-check chain stops for good. No-op on TVs without these ops.
adb -s "$TV" shell appops set com.prayerhome.tv AUTO_START allow 2>/dev/null || true
adb -s "$TV" shell appops set com.prayerhome.tv ASSOC_START allow 2>/dev/null || true

# lets Prayer Home install its own updates without asking (Settings → Apps → Install unknown apps)
adb -s "$TV" shell appops set com.prayerhome.tv REQUEST_INSTALL_PACKAGES allow

# lets Prayer Home switch the TV's screensaver off (Settings → Screensaver)
adb -s "$TV" shell pm grant com.prayerhome.tv android.permission.WRITE_SECURE_SETTINGS

# Google TV ignores third-party home apps while its own launcher (launcherx) is enabled. Switching it
# and its setup app (setupwraith) off breaks other things (a Chromecast boots to a black screen and
# drops out of Google Home), so they stay on and Prayer Home's accessibility service (HomeGuardService)
# opens Prayer Home whenever the Google TV home screen comes up. Other services that are on stay on.
GUARD=com.prayerhome.tv/com.prayerhome.tv.core.HomeGuardService
current=$(adb -s "$TV" shell settings get secure enabled_accessibility_services | tr -d '\r')
if [[ $current != *"$GUARD"* ]]; then
  if [[ -z $current || $current == null ]]; then current=$GUARD; else current="$current:$GUARD"; fi
  adb -s "$TV" shell settings put secure enabled_accessibility_services "$current"
fi
adb -s "$TV" shell settings put secure accessibility_enabled 1

adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Installed. In front now:"
adb -s "$TV" shell dumpsys activity activities | grep -m1 topResumedActivity || true
