#!/usr/bin/env bash
# Offline emulator suite (moved from the GitHub workflow). Needs a running emulator (adb root works on google_apis images).
# Usage: ci/device-suite.sh <results-dir>   APK_ROOT defaults to app/build/outputs/apk
APK_ROOT="${APK_ROOT:-app/build/outputs/apk}"
RESULTS="${1:-device-results}"
set -euo pipefail
mkdir -p "$RESULTS"
collect_results() {
  adb logcat -d > "$RESULTS"/logcat.txt || true
  adb shell dumpsys window > "$RESULTS"/windows.txt || true
  adb pull /data/local/tmp/device-evidence "$RESULTS"/evidence > /dev/null 2>&1 || true
  for package in com.nevminhnh.mintube.debug com.nevminhnh.mintube.minified; do
    adb pull "/data/user/0/$package/files/device-evidence" "$RESULTS/$package" > /dev/null 2>&1 || true
  done
}
trap collect_results EXIT
adb root
adb wait-for-device
for table in iptables ip6tables; do
  adb shell "$table" -I OUTPUT -p tcp -m multiport --dports 80,443 -j REJECT
  adb shell "$table" -I OUTPUT -p udp --dport 443 -j REJECT
done
adb shell svc wifi disable
adb shell svc data disable
adb shell settings put global device_provisioned 1
adb shell settings put secure user_setup_complete 1
adb shell am force-stop com.google.android.googlesdksetup
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell wm dismiss-keyguard
ready=false
started=''
for ((attempt=0; attempt<30; attempt++)); do
  launcher=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -n 1) || launcher=''
  if [[ "$launcher" == */* ]]; then
    if [[ "$launcher" != "$started" ]]; then
      adb shell am force-stop "${launcher%%/*}"
      if adb shell am start -n "$launcher" -a android.intent.action.MAIN -c android.intent.category.HOME > "$RESULTS"/launcher.txt 2>&1; then
        started="$launcher"
      fi
    fi
    adb shell dumpsys window > "$RESULTS"/windows.txt
    if [[ "$launcher" == "$started" ]] && grep -F 'mCurrentFocus=' "$RESULTS"/windows.txt | grep -Fq "${launcher%%/*}/"; then
      ready=true
      break
    fi
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo 'Launcher window is not ready'
  exit 1
fi
install_apks() {
  local variant="$1"
  adb install -r -t -g "$APK_ROOT/$variant/app-$variant.apk"
  adb install -r -t -g "$APK_ROOT/androidTest/$variant/app-$variant-androidTest.apk"
}
run_suite() {
  local package="$1" classes="$2" label="$3" runner="${4:-com.hhst.youtubelite.ContentTestRunner}"
  adb shell am force-stop "$package" || return
  adb shell am instrument -w -r -e class "$classes" "$package.test/$runner" | tee "$RESULTS/$label.txt" || return
  grep -Eq '^OK \([1-9][0-9]* tests?\)' "$RESULTS/$label.txt"
}
classes=(
  com.hhst.youtubelite.browser.TransientUiAndroidTest
  com.hhst.youtubelite.browser.ExternalRouteAndroidTest
  com.hhst.youtubelite.player.engine.PipDismissAndroidTest
  com.hhst.youtubelite.player.engine.PipReturnAndroidTest
  com.hhst.youtubelite.diagnostics.DiagnosticSaveAndroidTest
  com.hhst.youtubelite.player.surface.FullscreenSheetAndroidTest
  com.hhst.youtubelite.player.surface.PlayerControlSizingAndroidTest
  com.hhst.youtubelite.player.datasource.PlaybackLoadErrorAndroidTest
  com.hhst.youtubelite.player.engine.TransportRecoveryAndroidTest
  com.hhst.youtubelite.player.engine.PinnedVideoRecoveryAndroidTest
  com.hhst.youtubelite.player.engine.MediaResponseRecoveryAndroidTest
  com.hhst.youtubelite.player.engine.PlaybackAttemptLifecycleAndroidTest
  com.hhst.youtubelite.player.engine.RemoteResumeAndroidTest
  com.hhst.youtubelite.player.engine.WatchStartSecondsAndroidTest
  com.hhst.youtubelite.player.engine.CastStateIsolationAndroidTest
  com.hhst.youtubelite.core.TrafficVerificationLifecycleAndroidTest
  com.hhst.youtubelite.player.engine.NamedAudioAndroidTest#namedAudioSurvivesQualityChanges
  com.hhst.youtubelite.player.engine.OriginalAudioAndroidTest
  com.hhst.youtubelite.downloader.android.DownloadOptionsAndroidTest
  com.hhst.youtubelite.downloader.android.DownloadIntentAndroidTest
  com.hhst.youtubelite.downloader.io.LocalPublishAndroidTest
  com.hhst.youtubelite.data.media.SearchIdentityAndroidTest
  com.hhst.youtubelite.cast.CastChannelAndroidTest#authenticatedBlockedWriteIsAbortedByLocalClose
  com.hhst.youtubelite.cast.CastChannelAndroidTest#closeDuringAuthenticationCannotRestartTheTransport
)
printf -v class_list '%s,' "${classes[@]}"
status=0
install_apks debug
run_suite com.nevminhnh.mintube.debug "${class_list%,}" offline || status=1
run_suite com.nevminhnh.mintube.debug \
  com.hhst.youtubelite.player.engine.PipAudioOnlyAndroidTest \
  pip-audio || status=1
install_apks minifiedDebug
run_suite com.nevminhnh.mintube.minified \
  com.hhst.youtubelite.player.sponsor.SponsorBlockMinifiedAndroidTest \
  sponsorblock-r8 com.hhst.youtubelite.player.sponsor.R8VerificationRunner || status=1
exit "$status"
