# Local test run 20261121-0026

- Commit: `0ff8d97`
- Host: MINGW64_NT-10.0-26300, openjdk version "21.0.13.1" 2026-11-17 LTS
- Unit tests: 1817 run, 0 failures, 0 errors, 2 skipped
- Lint (debug): 0 errors, 604 warnings
- Stages: unit-and-lint PASS, builds PASS, r8-builds PASS
- Offline device suite (mintube_phone): FAIL
- Overall: FAIL

## Coordinator note
The offline device suite did not finish: 61 passed and 3 failed
(`DiagnosticSaveAndroidTest`: `selectedDestinationContainsTheExistingDiagnosticZip`,
`pickerCancellationKeepsTheScreenUsable`, `deniedDocumentProviderReportsWriteFailure`). After test 65 of 91,
the device went offline: another agent booted a second instance of the same AVD (`mintube_phone`) at 01:07.
Fix: `ci/local-test.sh` now uses a dedicated AVD, `mintube_ci`. Re-run after the next merge.
