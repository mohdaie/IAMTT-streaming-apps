#!/bin/sh
# Preserve device evidence even when native code terminates instrumentation.
gradle -p torrent-downloader :app:connectedDebugAndroidTest --no-daemon --stacktrace
iamtt_test_result=$?
mkdir -p torrent-downloader/app/build/reports/device
adb logcat -b all -d > torrent-downloader/app/build/reports/device/logcat.txt
adb shell dumpsys activity exit-info com.iamtt.downloader > torrent-downloader/app/build/reports/device/exits.txt
exit "$iamtt_test_result"
