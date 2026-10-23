# Physical phone review — 2026-10-10

Device: authorized Vivo V2515, Android API 36. Development version 2.1.0 (3), Room schema 13.

ADB updated the debug app with `install -r -t`; no uninstall, app-data clear, database reset, or source-file deletion was issued. The saved key stayed masked in Settings. Diagnostic TMDB requests kept it in memory and did not write or print it.

The live catalog at the start of this review contained 13 media rows, 187 episodes and no progress rows. An earlier-session backup contained 19 media, 310 episodes and 16 progress rows; those older counts cannot establish preservation across the intervening phone state changes. This review compares the captured live catalog before and after the fixes. Brief playback checks intentionally create progress for the tested titles.

## Catalog and artwork audit

- All 190 video files currently under the selected Movies/Movies and Movies/Shows roots have catalog file URIs: 187 episodes and three movie files. No uncatalogued video was found.
- The Romaji anime film was identified as Rascal Does Not Dream of a Knapsack Kid, TMDB 1086591, stored as MOVIE. Both category memberships are retained.
- All 208 cached artwork images in the final audit decoded successfully on the host (205 before Michael's three images were recovered). No referenced artwork file was missing. Null remote episode stills use show artwork; this does not imply TMDB supplies every still.
- Michael (2026) reproduced an identification failure: same-year fuzzy Michael Crunch and exact Michael both capped at 1.0 after the year bonus. Ranking now retains uncapped title similarity as additional tie evidence, while truly equal evidence still remains ambiguous.

## Screens inspected

Home hero, category/recent/genre sections; Library grid, Movies/Anime categories, filters and watchlist empty state; Search input/results; unmatched and identified movie Details; show seasons/episodes and episode options; movie player; Settings key/folders/playback/scan; About and local notices; history confirmation opened and cancelled.

The screenshots exposed title text behind transparent logos following unrelated artwork publications, a duplicate title below Library cards, compressed ratings text, a transparent Details toolbar over scrolled copy, stale Manrope credits, and a phone remaining landscape after leaving the player. The fixes keep image/fallback slots stable, key image state to file changes, remove duplicate card captions, reflow metadata actions, show an opaque toolbar on scroll, correct Inter credits, and derive device orientation policy from smallest width rather than the forced landscape player window.

Michael now identifies as TMDB 936075 on the same local ID 4. All 13 current titles are identified. Comparing live snapshots confirms media IDs, complete episode rows, file URIs, engine preferences, watchlist, added dates and sidecar subtitle paths stayed intact.

A 200% text-size fixture verifies that the entire Letterboxd label stays on one line, its final glyph fits within the layout (allowing one raster pixel for fractional rounding), and its action still fires. Library's title occurs once. Same-path replacement and unrelated artwork-publication tests verify the logo fallback disappears and does not return behind a loaded logo.

Audio and subtitle pickers now open expanded and use Material's default window insets, exposing both choices in the phone's short landscape window. Pause, track sheets, resume and Back were exercised. ADB required explicit display 0 for reliable landscape taps; earlier failed taps were an injection issue. Leaving playback returns the phone to portrait.

The forced OpenSL ES option was removed in favor of the bundled libVLC 3.7.5 device-selection policy, inspected in its dependency source JAR. VLC still emits startup zero-sample-rate/codec warnings, followed by a working 48 kHz AudioTrack and progressing video. This is not proof of audible quality or codec coverage. Both the VLC anime film and Media3 Michael player were opened; no app crash was observed. Remaining startup warnings and listening/long-playback checks stay on the release checklist.

Verification: 40 JVM tests passed; the full 19 Android tests passed, followed by all eight affected UI/navigation checks passing against the final track-picker revision. Android architecture guardrails and diff checks pass. Final lint reports zero errors and 69 existing warnings. Debug and minified release builds succeeded; the release APK verification passed all 10 native libraries, ARM64 ELF/native ZIP 16 KB alignment and bundled notices. The 96.2 MiB release remains unsigned. SHA-256: `450355e5a526d5efd5378ca5572c112ad053c502cb129007a83e3c81bce697bf`. Build evidence: `build/phone-review-complete-build.log` and `build/phone-review-apk-verification.log`.

Review artifacts are local under build/phone-review and contain app screenshots/read-only catalog copies. They are ignored by Git. Some earlier screenshots include system notification overlays; do not publish these private review artifacts.

The local screenshot gallery is `build/phone-review/index.html`. Evidence includes
`file-coverage.json`, `artwork-decode-audit.json`, `preservation-audit.json`, before/after
read-only snapshots, sanitized app logcat, `phone-review-android-tests-passed.log` and
`phone-review-final-screen-tests.log`. Two progress rows belong to the explicitly
tested movie files; no prior progress was cleared.
