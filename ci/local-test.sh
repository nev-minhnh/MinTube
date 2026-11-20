#!/usr/bin/env bash
# Local test pipeline for MinTube. GitHub Actions only builds and releases; every test runs here and the
# results are committed under test-results/<timestamp>-<commit>/ so they travel with the code.
#
# Usage (from the repo root, Git Bash on Windows or any bash):
#   ci/local-test.sh [--avd <name>] [--no-device]
# Needs: JDK 21 + Android SDK on PATH/ANDROID_HOME, an AVD with a google_apis x86_64 image (API 35) for the
# device suite. Exit code is non-zero when any stage fails; the summary is written either way.
set -uo pipefail

AVD="mintube_phone"
DEVICE=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --avd) AVD="$2"; shift 2 ;;
    --no-device) DEVICE=0; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

commit=$(git rev-parse --short HEAD)
dirty=$(git status --porcelain --untracked-files=no | grep -v '^.. test-results/' | wc -l | tr -d ' ')
stamp=$(date +%Y%m%d-%H%M)
OUT="test-results/$stamp-$commit"
mkdir -p "$OUT/unit" "$OUT/lint" "$OUT/device"
status=0
declare -A stage

run_stage() {  # name, gradle args...
  local name="$1"; shift
  echo "=== $name"
  if ./gradlew "$@" --console=plain > "$OUT/$name.log" 2>&1; then stage[$name]=PASS; else stage[$name]=FAIL; status=1; fi
  echo "$name: ${stage[$name]}"
}

run_stage unit-and-lint :app:testDebugUnitTest :app:lintDebug --continue
run_stage builds :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
run_stage r8-builds -PandroidTestBuildType=minifiedDebug :app:assembleMinifiedDebug :app:assembleMinifiedDebugAndroidTest

cp app/build/test-results/testDebugUnitTest/*.xml "$OUT/unit/" 2>/dev/null || true
cp app/build/reports/lint-results-debug.txt app/build/reports/lint-results-debug.xml "$OUT/lint/" 2>/dev/null || true

unit_line=$(python - "$OUT/unit" <<'PY'
import glob, sys, xml.etree.ElementTree as E
t = f = e = s = 0
failed = []
for p in glob.glob(sys.argv[1] + '/*.xml'):
    r = E.parse(p).getroot()
    t += int(r.get('tests')); f += int(r.get('failures')); e += int(r.get('errors')); s += int(r.get('skipped'))
    for c in r.iter('testcase'):
        if c.find('failure') is not None or c.find('error') is not None:
            failed.append(r.get('name') + '.' + c.get('name'))
print(f"{t} run, {f} failures, {e} errors, {s} skipped" + ("; failed: " + ", ".join(failed) if failed else ""))
PY
)
lint_line=$(grep -oE "[0-9]+ errors?, [0-9]+ warnings?" "$OUT/lint/lint-results-debug.txt" 2>/dev/null || echo "no lint report")

device_line="skipped (--no-device)"
if [[ $DEVICE == 1 && ${stage[builds]} == PASS && ${stage[r8-builds]} == PASS ]]; then
  echo "=== device suite on $AVD"
  emulator -avd "$AVD" -no-window -no-snapshot -gpu swiftshader_indirect -noaudio -no-boot-anim > "$OUT/device/emulator.log" 2>&1 &
  emu=$!
  adb wait-for-device
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do sleep 2; done
  if bash ci/device-suite.sh "$OUT/device" > "$OUT/device/suite.log" 2>&1; then device_line=PASS; else device_line=FAIL; status=1; fi
  adb emu kill > /dev/null 2>&1 || kill "$emu" 2>/dev/null
  wait "$emu" 2>/dev/null
  rm -f "$OUT/device/logcat.txt"  # large and machine-specific; the per-suite outputs stay
elif [[ $DEVICE == 1 ]]; then
  device_line="not run (builds failed)"; status=1
fi

{
  echo "# Local test run $stamp"
  echo
  echo "- Commit: \`$commit\`$( [[ $dirty != 0 ]] && echo " (working tree had $dirty uncommitted file(s): results are NOT for this commit alone)")"
  echo "- Host: $(uname -s), $(java -version 2>&1 | head -n 1)"
  echo "- Unit tests: $unit_line"
  echo "- Lint (debug): $lint_line"
  echo "- Stages: unit-and-lint ${stage[unit-and-lint]}, builds ${stage[builds]}, r8-builds ${stage[r8-builds]}"
  echo "- Offline device suite ($AVD): $device_line"
  echo "- Overall: $( [[ $status == 0 ]] && echo PASS || echo FAIL )"
} > "$OUT/SUMMARY.md"
gzip -f "$OUT"/*.log 2>/dev/null
cat "$OUT/SUMMARY.md"
exit $status
