
## Coordinator note
This device-only run reused the a3545bc build; a03a113 changes only `ci/local-test.sh`.
- The offline suite reached 64 tests: 62 passed. The 2 failures are the tests the owner waived (`PipDismissAndroidTest`, `FullscreenSheetAndroidTest`).
- The emulator then crashed with a software-renderer error ("invalid color buffer") on `WatchStartSecondsAndroidTest`. It crashed on that test again in a resume run (`device-resume/`).
- The owner chose to release with that class excluded (tested manually on a phone).
- The remaining classes run in `device-resume2/`.
- device-resume2 (10 classes without WatchStartSeconds): 19/21 passed. Two NEW failures, both in
  `DownloadOptionsAndroidTest`: `retryAndEveryQualityAudioAndSubtitleSubmitWithoutReload` and
  `audioQualityKeepsTrackAndCatalog`. Neither finds the "Japanese" audio-track radio button. They passed in the
  5a79ac5 run, so this looks like a regression from a later merge (fix/flick or fix/locale). Under investigation.
  pip-audio OK, sponsorblock-r8 OK.
