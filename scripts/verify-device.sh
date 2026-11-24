#!/usr/bin/env bash
set -euo pipefail
python scripts/generate-test-audio.py
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
API_LEVEL="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [ "$API_LEVEL" -ge 33 ]; then
  adb shell pm grant dev.lad.player android.permission.READ_MEDIA_AUDIO
else
  adb shell pm grant dev.lad.player android.permission.READ_EXTERNAL_STORAGE
fi
adb shell mkdir -p /sdcard/Music/LadTest
for audio_fixture in test-audio/*; do
  adb push "$audio_fixture" /sdcard/Music/LadTest/
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Music/LadTest/$(basename "$audio_fixture")"
done
adb shell am start -n dev.lad.player/dev.lad.MainActivity
adb shell mkdir -p /sdcard/Music/Allowed/Hidden /sdcard/Music/Allowed2 /sdcard/Music/Elsewhere
adb shell cp /sdcard/Music/LadTest/01-first.mp3 /sdcard/Music/Allowed/allowed.mp3
adb shell cp /sdcard/Music/LadTest/02-wave.flac /sdcard/Music/Allowed/Hidden/hidden.flac
adb shell cp /sdcard/Music/LadTest/03-wind.wav /sdcard/Music/Allowed2/sibling.wav
adb shell cp /sdcard/Music/LadTest/04-night.ogg /sdcard/Music/Elsewhere/elsewhere.ogg
for audio_fixture in Allowed/allowed.mp3 Allowed/Hidden/hidden.flac Allowed2/sibling.wav Elsewhere/elsewhere.ogg; do
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Music/$audio_fixture"
done
adb shell am instrument -w -e notClass dev.lad.UpgradeSeedTest,dev.lad.UpgradeVerifyTest dev.lad.player.test/androidx.test.runner.AndroidJUnitRunner
