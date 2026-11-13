#!/usr/bin/env bash
# Installs the release APK on a running emulator and fails if the app crashes on launch
# or while its widgets update. Usage: smoke-test.sh path/to/app.apk
set -euo pipefail

apk="$1"
pkg="com.radagva.widgetkit"

adb wait-for-device
adb install -r "$apk"
adb logcat -c
adb logcat -b crash -c || true

echo "Launching the app..."
adb shell am start -W -n "$pkg/.MainActivity"
sleep 10

# APPWIDGET_UPDATE is system-only, so use the widgets' own refresh actions (same code paths;
# the app screen above already rendered every widget layout in its previews).
echo "Triggering widget refreshes..."
adb shell am broadcast -a "$pkg.calendar.REFRESH" -n "$pkg/.calendar.CalendarWidgetProvider" >/dev/null
for receiver in .currency.DollarWidgetProvider .currency.EuroWidgetProvider .currency.DollarEuroWidgetProvider; do
  adb shell am broadcast -a "$pkg.currency.REFRESH" -n "$pkg/$receiver" >/dev/null
done
adb shell am broadcast -a "$pkg.uuid.GENERATE" -n "$pkg/.uuid.UuidWidgetProvider" >/dev/null
adb shell am broadcast -a "$pkg.uuid.COPY" -n "$pkg/.uuid.UuidWidgetProvider" >/dev/null
sleep 10

crashes="$(adb logcat -d -b crash || true)"
if echo "$crashes" | grep -q "Process: $pkg"; then
  echo "::error::The app crashed during the smoke test."
  echo "$crashes"
  exit 1
fi

if ! adb shell pidof "$pkg" >/dev/null; then
  echo "::error::The app is not running after launch."
  adb logcat -d | tail -200
  exit 1
fi

echo "Smoke test passed: the app launched and widgets updated without crashing."
