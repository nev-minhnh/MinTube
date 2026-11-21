
## Coordinator note
This device-only run reused the a3545bc build; a03a113 changes only `ci/local-test.sh`.
- The offline suite reached 64 tests: 62 passed. The 2 failures are the tests the owner waived (`PipDismissAndroidTest`, `FullscreenSheetAndroidTest`).
- The emulator then crashed with a software-renderer error ("invalid color buffer") on `WatchStartSecondsAndroidTest`. It crashed on that test again in a resume run (`device-resume/`).
- The owner chose to release with that class excluded (tested manually on a phone).
- The remaining classes run in `device-resume2/`.
