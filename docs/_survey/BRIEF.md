# Survey brief (shared by all read-only survey agents)

Project: `D:/Coding_Project/MinTube/repo` = pristine Litube 4.0.0 (unofficial YouTube Android client, Kotlin, Jetpack Compose + Material3, Koin, Media3, single module `:app`, source root `app/src/main/java/com/hhst/youtubelite/`). We will redesign ONLY the UI (presentation packages, ui/design, ui/theme, ui/components, resources, navigation) into an app called "MinTube"; backend (data, domain, extractor, player engine, downloader, cast, core, diagnostics, di) logic must stay untouched.

## Hard rules for you
- READ-ONLY. Do not modify, create, move or delete anything in the repo, except writing your single report file named in your task (under `docs/_survey/`).
- Do NOT run gradle, git commands that change state, or any build (a baseline build is running concurrently in this checkout).
- Verify from source; cite evidence as repo-relative paths with line numbers (`app/src/main/java/.../Foo.kt:123`). Never guess: if you did not find something, say "not found" and say what you searched for.
- Report in English, in Markdown, dense and factual; no filler.

## What every report must contain (for the packages assigned to you)
1. **Package map**: for each package, its sub-structure (data / domain / presentation or equivalent), key files with 1-line purpose, line counts for big files.
2. **Screens & ViewModels**: every Composable screen / Activity / sheet / dialog, which ViewModel (or state holder) drives it, the UiState type, the events/intents the UI sends, and which domain/repository interfaces the ViewModel calls. This is the ViewModel<->domain contract we must preserve; list it precisely (class names, public functions, StateFlow names).
3. **Data flow**: how data reaches the screen (repository -> VM -> state -> composable), paging, refresh, error/empty/loading handling as currently implemented.
4. **Feature matrix rows**: for each feature in your checklist below, give one of three statuses:
   - `WORKS` = implemented in data layer AND reachable in UI
   - `DATA_ONLY` = data/domain layer supports it but UI is missing or partial (say exactly what is missing)
   - `MISSING` = not present in the data layer at all
   with evidence paths for both the data side and the UI side. Add rows for any extra features you find that are not on the checklist.
5. **UI coupling & redesign notes**: which files are pure presentation (safe to rewrite), which mix UI with logic that must be kept (gestures tied to player engine, identity/revision checks living in composables, test tags, semantics that tests rely on), shared design-system components used (from `ui/design`, `ui/theme`, `ui/components`), hardcoded colors/dimens/strings, accessibility state (contentDescription, touch target sizes), tablet/landscape handling, scroll-state preservation.
6. **Tests touching these packages**: unit tests (`app/src/test`), instrumented tests (`app/src/androidTest`), golden images (`app/src/androidTest/assets/<feature>/goldens`): counts, what they assert, which ones depend on UI structure (test tags, text, golden pixels) and so will need updating after a redesign.
7. **Risks / surprises**: anything a UI rewriter must know.

Keep the report under ~600 lines. Your final chat reply should be at most 15 lines: the report path plus the 5 most important findings.
