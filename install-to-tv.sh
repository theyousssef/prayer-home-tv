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

# lets Prayer Home switch the TV's screensaver off (Settings → Screensaver)
adb -s "$TV" shell pm grant com.prayerhome.tv android.permission.WRITE_SECURE_SETTINGS

# Google TV ignores third-party home apps while its own launcher (and the setup
# "recovery" home) are enabled, so they are switched off. Undo: see README.md.
echo
echo "Switching off the Google TV launcher. Note: a Wi-Fi network saved during the TV's first setup"
echo "may be forgotten when the setup app is switched off; if the TV drops off Wi-Fi, reconnect it"
echo "in Settings → Network & Internet (see README.md)."
adb -s "$TV" shell pm disable-user --user 0 com.google.android.apps.tv.launcherx >/dev/null || true
adb -s "$TV" shell pm disable-user --user 0 com.google.android.tungsten.setupwraith >/dev/null || true
adb -s "$TV" shell cmd package set-home-activity com.prayerhome.tv/.MainActivity

adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Installed. Home now resolves to:"
adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
