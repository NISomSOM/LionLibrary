# LionLibrary validation history

Dated validation records moved out of `References/AGENT.md` on 2026-11-16 so the guide
describes the current project. Content is unchanged (verbatim). Section numbers refer to the
guide as it was before the move; counts, hashes and device observations are historical evidence
for their revision, not current results. Current results are in AGENT.md section 19 and
`References/IMPROVEMENT_PROGRESS.md` (local).

## From section 14 (Build and verification)

### Prior release-improvement validation on 2026-10-10 (historical)

These are completed local results for the implementation, not a new test run whenever
this document is read:

- Formatting/check formatting, debug unit tests/lint, connected Android tests and
  debug/minified resource-shrunk release assembly passed.
- **32 unit tests** and **14 Android integration/UI tests** passed on API 35 x86_64.
- Populated schemas **6–12 → 13** passed Android Room migration tests; host legacy
  ALTER checks also passed. The 1,000-title/10,000-episode query smoke is a host
  synthetic check, not an Android-device performance benchmark.
- Lint reported **0 errors, 76 warnings**. Remaining warnings include dependency
  update notices, direct test dependency declarations, legacy assets/font certificates,
  KTX/style suggestions and intentional ARM-only release scope. No blanket baseline.
- Release inspection found 10 native libraries across ARM32/ARM64. ARM64 ELF LOAD
  segments and native ZIP offsets passed 16 KB alignment; SDK zipalign also passed.
- Actual setup/library emulator screens were reviewed, including font scale 2.0.
  Navigation contrast and independent filter/results scrolling were corrected.

Evidence: `build/completed-validation.log`, `app/build/reports/`,
`build/release-apk-verification.json`, and `build/reviewshots/` (notably
`library-large-text-final.png` and `setup-large-text.png`). PowerShell redirection
created the validation log as UTF-16; source/documents are UTF-8.
The isolated audit emulator was stopped after validation.

GitHub Actions is configured for JDK 21/SDK 37, formatting/unit/lint/build checks,
Python guards, API 35 x86_64 connected tests and report/mapping upload even on failure.
Its remote execution has not been observed; local success does not establish CI success.
setup-android@v3 must be given explicit `packages` (platform-tools, platforms;android-37.0,
build-tools;37.0.0): its default includes the removed legacy `tools` package and fails
(observed 2026-10-24, run 40070021699).


## From section 15 (Release artifacts) - artifact recorded on 2026-10-18

The recorded unsigned artifact is
`app/build/outputs/apk/release/app-release-unsigned.apk` (approximately 95.7 MiB).
Its SHA-256, verified after the 2026-10-18 missing-file/deletion update, is:

```text
f8743415a93b2577c2790cc8e67a8df1d0396b7faa90fc573578aab4dbda3ebd
```

The R8 mapping is `app/build/outputs/mapping/release/mapping.txt`. Rebuilding or
signing changes the artifact/hash; archive the exact delivered build and its evidence.


## Former sections 17-25

## 17. Latest regression revision (2026-10-10)

- User-approved scope: exactly Movies and Shows roots; an anime film inside Movies
  appears in both Movies and Anime. No third root or new Settings options. **Approved
  exception (user, 2026-10-23):** the single Playback switch "Online skip times" (section 25).
- Removed Setup/Health UI, routes and DI; kept metadata internals/history contracts.
- Corrected original-title/alias matching and release-year ties; retained parsed years.
- Added automatic missing/corrupt artwork recovery and visible local-file fallbacks.
- Restored the damaged JumpBackInCard source, including next-up indication and overflow.
- Bundled Inter and its OFL notice; independent Nuvio-inspired UI with orange accents.
- Validation details and device observations: [bug-fix record](../docs/BUGFIX_VALIDATION.md).
  Historical test/APK figures elsewhere in this guide describe the earlier implementation;
  use the bug-fix record for this revision’s evidence and remaining limits.

When a file moves outside/between roots, URI identity still takes priority. Do not merge
or delete catalog rows solely because filenames or TMDB IDs happen to match. Files outside
the two granted roots cannot be rediscovered/opened until relocated or re-granted. No
source video, subtitle, or customer database is deleted/reset by this revision.

## 18. Physical-device follow-up (2026-10-10)

See [physical phone review](../docs/PHONE_REVIEW.md) for that revision’s evidence and
private screenshot gallery. The Vivo API 36 debug update was installed with `-r -t`.
All 190 videos in the two selected roots were catalogued. Michael now identifies as
TMDB 936075 on its existing local ID; all 13 current titles are identified. The final
cache audit decoded 208 images, with no corrupt files or missing artwork references.
IDs, episode rows, added dates, watchlist, sidecar paths and per-file engines were
compared and preserved. The current review baseline differs from an older backup;
do not use the older counts to claim an upgrade-preservation result.

Additional presentation contracts:

- Render a card title once; Library uses the title already inside MediaCard.
- Metadata/rating groups wrap as complete items. Letterboxd remains a clickable,
  single-line label at 200% font size, with the full label visible.
- At that revision, Details used an opaque toolbar immediately on scroll.
  Section 20 supersedes this with the primary-action threshold.
- Phone orientation policy uses smallest-screen width (600 dp tablet threshold),
  independent of a forced landscape player window. Navigation bar/rail selection
  still follows current window width. Back from playback restores phone portrait.
- Audio/subtitle sheets open expanded and retain Material window insets so available
  choices remain reachable in landscape. Do not force a zero-inset partial sheet.
- Let bundled libVLC select a device-compatible audio backend; avoid a blanket
  OpenSL ES override. Preserve singleton prewarming, fontconfig and descriptor rules.
  Startup format/codec warnings remain in the physical logs even though video and
  AudioTrack start. Listening, long playback and wider codec/device checks remain
  release gates; do not claim exhaustive playback compatibility.

Verification: 40 JVM tests and the full 19 Android checks passed. All eight affected
UI/navigation checks passed again after the final track-sheet change. Architecture
checks pass; Android lint has zero errors and 69 existing warnings. No schema change,
new folder root, onboarding or manual repair UI was introduced.

The final phone-review debug APK is installed on the Vivo. The minified unsigned
release was rebuilt and its native alignment/notices verified. SHA-256 at that revision:
`450355e5a526d5efd5378ca5572c112ad053c502cb129007a83e3c81bce697bf` (96.2 MiB). See the physical phone review for the
artifacts from that revision; production signing and the remaining distribution gates still apply.

## 19. Polished browsing UI (2026-10-11)

The completed adaptation covers Home, Library, Search, Settings and the Details
header, with original Compose components and LionLibrary's orange identity.
See [active design decisions](../DESIGN.md) and [validation report](../docs/UI_POLISH.md).
The reference frontmatter in DESIGN.md describes Nuvio; it is not the active theme.
Bundled licensed Inter remains in use; no Nuvio source, font or artwork was imported.

This pass preserves the two roots, scanning/matching/artwork contracts, playback
engines and episode list logic. Before/after phone snapshots have identical complete
rows for 13 media, 187 episodes, 12 seasons and three progress records. Artwork
remains 208 files with no missing references. The final debug update is installed
on the Vivo API 36 phone without a data reset.

Recorded checks: 40 JVM tests, the full 25-test Android suite before the final
large-text navigation adjustment, and all 14 affected UI/navigation tests again
after that adjustment with system text scale 2.0. Final debug/test/minified release
assembly, changed-file formatting, architecture guards and release packaging passed.
Lint reports zero errors and 69 warnings. These results do not imply exhaustive
skill compliance, TalkBack coverage, playback compatibility or public-release approval.

The warmed physical browsing sample recorded zero missed frame deadlines and a
95th-percentile frame time of 9 ms versus 13 ms before. The first pass after installation
had two slow frames; cache/session conditions differ, so this is a practical sample,
not a controlled benchmark. Local screenshots, preservation results, frame statistics
and artifact hash are linked from the validation report. Prior sections' dated test
counts and APK hashes remain historical evidence.

## 20. Prior UI interaction fixes (2026-10-11)

The user's follow-up supersedes the initial polish's four-tab navigation, resume
controls, immediate toolbar and static paging choices. See
[current design decisions](../DESIGN.md) and [validation](../docs/UI_REFINEMENTS.md).

- Search is removed as a destination, screen, ViewModel and DI binding. Keep query,
  category/filter/sort/watchlist workflows in Library; repository search remains.
- Home tab navigation pops directly to the Home entry. Do not restore a stack
  saved against Home that can reopen filtered Library instead. Category headers
  still navigate to parameterized Library and apply the requested filter.
- Jump Back In matches the Nuvio Card visual arrangement through original code:
  artwork, lower episode/title caption, top-right timing badge and orange progress.
  No play/three-dot overlays. Tap resumes; long press/accessibility opens options.
  Captured duration/position come from existing progress columns via the projection
  and mapper. Next-up items clear prior-episode timing; no schema migration.
- Home uses restrained image/caption parallax and crossfade, drawn from live pager
  offsets. Indicators share that offset; View Details stays white while paging
  and opens only the settled title. Lifecycle/accessibility/reduced-motion guards
  remain. No imported source/assets/fonts, Flutter dependency or blur pipeline.
- Floating navigation has no reserved full-width background. Home is edge-to-edge
  under the status bar. Details delays its logo surface until the Play action
  scrolls above the toolbar boundary, retaining Back and handling scroll jumps.

Final checks: 51 JVM tests and 27 Android tests passed; all 16 affected UI/navigation
checks passed again at 200% system text. Debug/test/minified release assembly,
changed-file formatting, architecture guards and native packaging checks passed.
Lint: zero errors, 68 warnings. Complete physical rows/IDs remain identical for
13 titles, 187 episodes, 12 seasons and five progress entries; artwork remains
208 files with no missing references. The final debug APK is installed on the Vivo
without resetting data. These are the current baseline counts, not the initial
polish's earlier three progress records.

The physical Home gesture sample recorded 2,300 frames, p95 12 ms and one missed
frame deadline (0.04%). Its flow differs from earlier samples; do not claim a
comparative performance gain or exhaustive device/playback coverage. The current
report links the private screenshot gallery and exact artifact/test evidence.

## 21. Prior feedback fixes (2026-10-11)

See [implementation and evidence](../docs/UI_FEEDBACK_FIXES.md). The six follow-up
requests supersede the prior Details fade timing and full-width compact bar:
loaded media fades visibly, tab reselection does not navigate, navigation is
smaller, year captions are unclipped, bookmark glyphs are smaller, and hero
metadata omits whole genres to fit one line. Material controls, Inter and orange
identity remain. No new library dependency, folder model, schema or episode-list
change was needed.

Final checks: 57 JVM tests and all 30 Android tests pass; 19 affected UI/navigation
tests passed at 200% system text across the suite and one screenshot-timeout
rerun. Lint reports zero errors and 68 existing warnings. Changed-file formatting,
architecture guards, debug/test/release assembly and native packaging passed.
The debug update is installed on the Vivo API 36 phone without resetting data.
Complete before/after rows are identical for 13 media, 187 episodes, 12 seasons
and five progress records; 208 artwork files have no missing references.

The short physical sample, including screen recording, media entry/back and Home
reselection, rendered 676 frames with four missed frame deadlines (0.59%),
p95 15 ms. This is a different workload from previous samples, not evidence of a
controlled performance improvement or exhaustive compatibility. The latest
artifact hash is recorded in section 15; prior reports keep historical hashes.

## 22. Prior player visual polish (2026-10-11)

The user's scope is small visual changes, without new controls/features. The
footer is now a tighter outlined surface with smaller labels/icons. The elapsed
and total times move beneath the full-width seek bar. `PlayerBottomControls`
takes values and callbacks, preserving the existing Player actions and formatting.
Library/Home bookmarks share a 16 dp glyph in a 24 dp badge; Details uses the
same glyph with a 48 dp outlined target.

Eight affected UI tests passed; the footer check also passed on the final build
in landscape, exercising normal and 200% font sizes, time placement, seeking,
scrub completion and all three existing options. Debug/test/minified release
assembly, formatting, architecture guards and native packaging passed. Lint has
zero errors and the same 68 existing warnings. Prior complete unit/Android suite
results remain dated in preceding reports.

The final debug build is installed on the connected Vivo without a data reset.
Player controls and both track sheets were inspected on the phone. Complete media,
episode and season rows remain identical (13/187/12); all eight existing progress
keys remain. Only Michael's position/progress/timestamp advanced during the brief
resume/pause check. Artwork remains 208 files with zero missing references. The
latest unsigned release hash is in section 15.

Evidence and exact checks: [player visual polish](../docs/PLAYER_UI_POLISH.md).


## 23. Player and dependency audit on 2026-10-12 (prior pass)

Stable catalog/toolchain upgrades and the official Retrofit converter/Coil 3 migration
are recorded in [PLAYER_AUDIT.md](../docs/PLAYER_AUDIT.md). No schema/root/UI feature
changes were introduced. Updated playback contracts are incorporated in section 11.

Final checks: 62 JVM tests pass; 38 Android tests discovered on API 35, with 37 passes
and one capability-based Exo HDR10 skip. All six native-only tests pass on the Vivo
API 36 phone, including both HDR10 paths. Hardware AVC/HEVC decoders and BT.2020/PQ
SurfaceView buffers were observed for both engines. This does not certify all HDR
formats, devices, optical color accuracy, battery life or sustained 4K throughput.

Whole-repository formatting, architecture/schema guards, unit/lint checks and final
debug/test/minified release builds pass. Lint has 0 errors and 46 warnings. The release
APK has 10 ARM native libraries and passes 16 KB ARM64 ELF/native ZIP alignment.
The final formatting-only pass normalized 22 existing files; Android/native checks
preceded it, and final builds/unit/lint/format checks ran afterward.

Final debug is installed on the phone without clearing data. Every library/history
row matches the before-audit snapshot: 14 media, 192 episodes, 14 seasons, 1 progress
row; 218 artwork files, zero missing references. This supersedes older device counts
because the user changed the library/history between tasks. The isolated emulator is
stopped. The current unsigned artifact/hash are in section 15; release gates remain.


## 24. Missing-file cleanup and explicit device deletion (2026-10-18, current)

The scanner previously only added/upserted files. `LibraryReconciler` now inventories
configured roots and removes confirmed missing file rows and their progress in Room
transactions, plus affected empty seasons/shows. It runs before metadata-key validation
inside the scanner. Invalid, revoked, offline, loading, null or incomplete inventories
retain rows; unconfigured-root rows are not pruned. A missing listing entry is checked
against the exact document before cleanup. Reconciliation never deletes source files
or artwork. Existing independent availability/repair APIs retain their old contracts.

`MediaDeletionRepository` is the domain contract; `SafMediaDeletionRepository` owns
preview/confirmation tokens, current-root and ownership validation, one exact native
delete and post-operation reconciliation. `SafLibraryDocuments` uses strict provider
queries and opaque document identities. `DeletionSafety` is pure ancestry/ownership
policy. Root IDs/document names must not be treated as filesystem path prefixes.

Home poster shelves, hero and Jump Back In long presses expose Delete from device.
Episode long presses append the same action to the existing menu. A movie or episode
deletes one exact video file and leaves sibling files/sidecars. A show deletes its
validated direct child folder of Shows and all its contents. A combined-episode file
is indivisible and the confirmation warns about its full episode range. Shared/read-only
folders, unindexed videos, multiple source folders and either configured root (including
a root nested inside the proposed folder) are refused rather than guessed.

Confirmations display title, exact relative storage path, folder file count and permanent
delete warning. Tokens are in-memory, single-use and checked against a fresh inventory,
root settings, library ownership, flags, sizes and modification metadata. Changes require
new review; there is never a parent/root deletion fallback. `LibraryMutationGate` serializes
scanner writes/reconciliation with deletion; the shared `DeletionPresenter` guards preview,
cancel and duplicate confirm states in Home/Details. Once native deletion starts, coroutine
cancellation cannot interrupt its cleanup; provider failure/partial deletion still runs
reconciliation and reports failure. This is not an atomic storage-plus-Room transaction or
a guarantee across process death/provider failure. A later rescan recovers partial results.

`RoomPlaybackRepository` checks parent/episode existence transactionally before a queued
progress write, preventing deleted history from being recreated by a late player save.
No schema migration/reset, extra folder type or new setting was introduced (schema 13).

Validation: 76 JVM tests pass; the full API 35 Android run discovers 60 tests: 59 pass
and one existing Exo HDR10 capability assumption is skipped. Deletion tests use only
synthetic private-cache documents and in-memory Room databases, including an actual
SAF DocumentsProvider, scanner-entry regression, stale confirmations, sibling preservation,
partial failures, cancellation and menus. The final URI KTX adapter and enlarged-dialog
fixture receive focused follow-up checks. No customer media is deleted for testing.

The fixture DocumentsProvider is debug-only, guarded by MANAGE_DOCUMENTS signature
permission and confined to its private-cache fixture directory; it is absent from release.
Final formatting, architecture/schema guards, debug/test and minified release builds
pass. Lint reports zero errors and 47 warnings. The release APK has 10 ARM native
libraries with verified ARM64 ELF/native ZIP 16 KB alignment and packaged notices.
The phone disconnected before current-device validation/installation, so do not repeat
older phone counts or claim this update is installed. See the safety record for final
artifact/hash, lint/build evidence and the optional system-font-check limitation.

## 25. Intro/credits skip buttons (2026-10-24, current)

User-approved scope (2026-10-23): skip buttons only, one new setting ("Online skip times",
portable store key `online_skip_times`, default on), chapters first, then TheIntroDB v4, then
SkipDB; session-memory cache only; no Room change (schema stays **13**); no scan-time or
next-episode requests; both engines and their contracts kept. Contracts are in section 11.
Provider field names were checked against TheIntroDB's published OpenAPI 4.0.0 and SkipDB's
API docs on 2026-10-24 (SkipDB's documented base is `https://api.skipdb.tv`). Privacy, NOTICE,
in-APK notices and the release checklist were updated; **TheIntroDB terms are an open release
gate**. Test-only dependency: OkHttp `mockwebserver3` 5.5.0 (loopback only).

Chapter spike: Media3 1.11.1 bytecode shows `MatroskaExtractor.updateTrackFormatWithMetadata`
and `Mp4Extractor`/`BoxParser` (QuickTime chapter track and `chpl`) producing `Chapter`
entries; libVLC 3.7.7 exposes `MediaPlayer.getChapters`. On the phone, real MKV chapters
appeared on both engines, so no separate chapter reader was needed.

Checks (JDK 21 JBR): `checkKotlinFormat`, `testDebugUnitTest` (**218** JVM tests, 0 failures;
109 new across policy, session, mappers, repository, HTTP, gate and DI), `lintDebug` (0 errors,
49 warnings; the two new ones are version-update notices), `assembleDebug`,
`compileDebugAndroidTestKotlin`, `assembleRelease`, and all three Python checks passed.
Release SHA-256 at this revision (unsigned): `87f557cc39a388734b947f896c008cd3c87a3f7c0abca85a1235457230981d91`.
Android instrumentation tests were compiled, not run (never on the user's phone).

Vivo API 36 (`adb install -r`, data kept), observed results:

- Exo, Dragon Ball Super: Beerus S1E1 ("Opening" 0:11-1:41): Skip intro shown with controls
  hidden and above the footer when shown; from paused at 1:22 it landed at 1:41 still paused.
  "Credits Start"+"Credits End" merged into one terminal Skip credits: from paused, it opened
  S1E2 (paused) and E1's history became completed (resume 0) through one completion write.
- libVLC, Sakamoto Days 12: no button during its "Recap" chapter; terminal Skip credits while
  playing opened E13, which kept playing. A natural end of E12 also advanced to E13.
- Online: Young Sheldon S1E2 (VLC, no chapters; TheIntroDB answer ineligible: confidence 0.1,
  different release length) showed the SkipDB intro (1:08.2-1:24.2, via TMDB external IDs)
  only with the switch on, and skipping landed at 1:24. With the switch off, no button.
  JoJo Steel Ball Run E3 has both an "Intro" chapter and an eligible TheIntroDB answer; the
  chapter button stayed with the switch off, as designed.
- Settings shows the switch and description; About shows the TheIntroDB/SkipDB (ODbL) line.
- Found and fixed on the device: Koin qualifiers declared after the module were null, so the
  first build crashed at startup; the qualifiers moved above the module and a test was added.
- Data: media/episodes/seasons unchanged (15/217/15). Test playback changed history only:
  Beerus E1 completion timestamp, Sakamoto E12 completed, new partial rows for Sakamoto E13
  and Young Sheldon S1E2; JoJo SBR E3 was briefly unfinished and was marked watched again.

Not verified: an online TheIntroDB-sourced button on a chapter-less file (none in this library
has an eligible answer), MP4 chapters on the device, cancelling a lookup in flight on the
device (unit-tested), TalkBack, and the instrumentation suite on an emulator.

### Paused card hides subtitles and the skip button (2026-10-24)

User request: subtitles and Skip intro must not appear while the paused "You're watching" card shows.
Contract in section 11. The derived state is unit-tested (`PlayerPauseCardTest`). Checks (JDK 21):
`checkKotlinFormat`, `testDebugUnitTest` (**223** JVM tests, 0 failures), `lintDebug` (0 errors,
49 warnings), `assembleDebug` and all three Python checks passed (the release APK check inspected the
existing release build; `assembleRelease` was not rerun).

Vivo API 36 (`adb install -r`, data kept): Exo, Beerus S1E1 in its Opening with English dialogue
subtitles: the card showed neither; resuming brought back both. libVLC, Sakamoto Days E13: Skip intro
in the opening disappeared on the card and returned on resume; with Track 2 and +0.25 s delay the
on-screen line vanished on the card and resumed on the same track and delay. The test then reset the
delay and reselected Forced (rewriting that title's saved subtitle choice with Forced). History: only
resume positions/timestamps of JoJo S6E6, Beerus E1 and Sakamoto E13 changed; counts 15/217/15.

Follow-up (2026-10-24): the card's synopsis now fills the full column width (inside safe-drawing
insets and the 48 dp padding; 2 lines compact, 3 otherwise, then ellipsis) instead of 60-80%, and a
bottom vertical scrim keeps it legible on the right. On the Vivo the Sakamoto E13 synopsis now fits
in two full-width lines (VLC) and Beerus E1's ellipsizes after two (Exo). The faint "Kroi" text seen
behind the card on Sakamoto E13 is the opening's burned-in credit art (pink Japanese typography),
not the Forced track; dialogue subtitles were confirmed hidden on the card. Checks: 223 JVM tests,
lint 0 errors/49 warnings, format and assembleDebug passed. Portrait and 200% font scale were not
checked on the device (they would need system setting changes); the `Paused, large font` preview
covers 2.0. History: only resume points of Sakamoto E13 (2:52 -> 2:50) and Beerus E1 (1:05 -> 1:12).
