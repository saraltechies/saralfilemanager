#!/usr/bin/env bash
# Runs on the emulator for the "App check" workflow. It keeps going after a failure, so one problem can't hide others.
set -u

OUT=check-output
mkdir -p "$OUT"
DEBUG=com.saraltechies.saralfilemanager.debug
RELEASE=com.saraltechies.saralfilemanager
RUNNER=$DEBUG.test/androidx.test.runner.AndroidJUnitRunner

allow() {
  adb shell appops set --uid "$1" MANAGE_EXTERNAL_STORAGE allow || true
  adb shell appops set "$1" GET_USAGE_STATS allow || true
  adb shell pm grant "$1" android.permission.POST_NOTIFICATIONS || true
  adb shell pm grant "$1" android.permission.READ_EXTERNAL_STORAGE || true
  adb shell pm grant "$1" android.permission.WRITE_EXTERNAL_STORAGE || true
}

instrument() { # name, class or class#method
  echo "=== $1"
  adb logcat -c || true
  adb shell am instrument -w -e class "$2" "$RUNNER" 2>&1 | tee "$OUT/test-$1.txt"
  adb logcat -d -b crash > "$OUT/crash-$1.txt" 2>&1 || true
  if grep -q "OK (" "$OUT/test-$1.txt"; then echo "PASS $1" >> "$OUT/summary.txt"; else echo "FAIL $1" >> "$OUT/summary.txt"; fi
}

./gradlew installDebug installDebugAndroidTest --stacktrace
adb logcat -G 16M || true
allow "$DEBUG"

if [ "${TOUGH:-0}" = "1" ]; then
  adb shell settings put system font_scale 1.5
  adb shell cmd uimode night yes
  adb shell wm size 720x1280
  adb shell wm density 320
  sleep 5
fi
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

adb shell mkdir -p /sdcard/DCIM/Camera
adb push check-video.mp4 /sdcard/DCIM/Camera/check-video.mp4

CHECK=com.saraltechies.saralfilemanager.AppCheckTest
instrument gallery-and-bin com.saraltechies.saralfilemanager.GalleryAndBinTest
instrument every-screen "$CHECK#everyMainScreen"
instrument video "$CHECK#videoReallyPlays"
instrument video-odd-name "$CHECK#videoWithOddNamePlays"
instrument drag "$CHECK#dragSelectsInSmallSteps"
instrument awkward-files "$CHECK#awkwardFiles"
instrument rebuilt "$CHECK#selectionAndViewerSurviveBeingRebuilt"
instrument big-library "$CHECK#bigLibraryAndBigFolder"
adb shell dumpsys gfxinfo "$DEBUG" > "$OUT/gfxinfo-debug.txt" 2>&1 || true
adb shell dumpsys meminfo "$DEBUG" > "$OUT/meminfo-debug.txt" 2>&1 || true

# Leaves a varied set of invented files on the phone for the random tapping below, and gets store-style pictures.
instrument varied-files com.saraltechies.saralfilemanager.StoreScreenshotsTest
adb pull "/sdcard/Android/data/$DEBUG/files/check/." "$OUT/check/" || true
adb pull "/sdcard/Android/data/$DEBUG/files/screenshots/." "$OUT/screens/" || true

# The real thing: the shrunk release build, as people get it, signed with the debug key for this emulator only.
./gradlew assembleRelease --stacktrace
TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
keytool -genkeypair -keystore check.jks -storepass android -keypass android -alias check -keyalg RSA -keysize 2048 -validity 2 -dname "CN=App check"
"$TOOLS/apksigner" sign --ks check.jks --ks-pass pass:android --key-pass pass:android \
  --out release-check.apk app/build/outputs/apk/release/app-release-unsigned.apk
ls -la release-check.apk > "$OUT/apk-size.txt" 2>&1
adb uninstall "$DEBUG" || true
adb install -r release-check.apk || echo "FAIL release-install" >> "$OUT/summary.txt"
allow "$RELEASE"

# Cold start, three times.
for i in 1 2 3; do
  adb shell am force-stop "$RELEASE"
  sleep 2
  adb shell am start -W -n "$RELEASE/.MainActivity" | grep -E "TotalTime|Status" >> "$OUT/cold-start.txt"
  sleep 3
done

# Random tapping on the release build. It stops at the first crash or freeze, and says so.
adb logcat -c || true
for seed in 11 42; do
  adb shell am start -n "$RELEASE/.MainActivity" || true
  sleep 2
  adb shell monkey -p "$RELEASE" -s "$seed" --throttle 100 --pct-syskeys 0 --pct-anyevent 0 -v 3000 2>&1 | tail -n 40 > "$OUT/monkey-$seed.txt"
  if grep -qE "CRASH|NOT RESPONDING|aborted" "$OUT/monkey-$seed.txt"; then echo "FAIL monkey-$seed" >> "$OUT/summary.txt"
  elif grep -q "Monkey finished" "$OUT/monkey-$seed.txt"; then echo "PASS monkey-$seed" >> "$OUT/summary.txt"
  else echo "UNKNOWN monkey-$seed" >> "$OUT/summary.txt"; fi
done
adb logcat -d -b crash > "$OUT/crash-release.txt" 2>&1 || true
adb logcat -d > "$OUT/logcat-release.txt" 2>&1 || true
adb shell dumpsys gfxinfo "$RELEASE" > "$OUT/gfxinfo-release.txt" 2>&1 || true
adb shell dumpsys meminfo "$RELEASE" > "$OUT/meminfo-release.txt" 2>&1 || true
adb shell screencap -p /sdcard/after-monkey.png && adb pull /sdcard/after-monkey.png "$OUT/after-monkey.png" || true

cat "$OUT/summary.txt"
exit 0
