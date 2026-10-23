# LionLibrary UI polish

Historical first pass. The subsequent [interaction fixes](UI_REFINEMENTS.md)
supersede navigation, resume-card controls, hero motion and Details toolbar timing.

Implemented and reviewed on **2026-10-11**. Home, Library, Search, Settings and the
Details header now share a clearer hierarchy, consistent spacing and restrained
motion. The orange identity, local-library workflows and existing episode list
structure remain in place. [DESIGN.md](../DESIGN.md) separates the observed Nuvio
reference from the active LionLibrary design.

## Implemented design

| Area | Result |
| --- | --- |
| Typography | Bundled licensed Inter, static 400/500/600/700 weights; page headings 38/42 sp, shelf headings 18/24 sp, reading text 15/22 sp, small labels at least 12 sp |
| Color and spacing | Orange #FF5722, background #0B0D10, surface #171A20, warm selection #36221B; page gutters 16 dp, Details 18 dp, card gaps 12 dp, shelf separation 24 dp |
| Home | Shorter bounded hero with artwork contained within each page, a clear View details action, quiet indicators and consistent shelf headings |
| Posters and resume | One poster caption below each image; independent watchlist/overflow controls; readable resume progress and large-text captions |
| Library and Search | Shared search fields and category chips, adaptive grids, preserved filters/sorting, clearer empty-state copy |
| Settings | Quieter section labels, compact folder rows and consistent surfaces; the existing key, two folders, playback overrides, scan, history and About controls |
| Details | White primary playback action, independent watchlist control, wrapping metadata, expandable three-line synopsis and an opaque toolbar when scrolling |
| Navigation and accessibility | Labelled orange selection; whole destination labels in two rows above 140% text scale; grids widen above 130%; bottom insets consumed once and navigation yields to the keyboard |

The episode list, season selection and episode option logic retain their existing
structure. Shared typography and source formatting affect this area. No Nuvio
source, artwork or fonts were imported. Existing licensed Inter remains bundled.
No onboarding, additional folder roots or new Settings options were introduced.

## Engineering and motion

Shared layout/motion tokens live in `ui/theme/DesignTokens.kt`; reusable headings,
search fields and chips live in `presentation/components/BrowseComponents.kt`.
Components use Material 3 behavior, theme roles, resource strings, independent
touch targets and state-specific previews. Screen state/actions and typed routes
retain the existing MVI architecture; this pass adds no database migration or data
layer dependency to the browsing UI.

Screen transitions use 220 ms fades with 150 ms exits. Hero movement uses the same
standard duration and easing `(0.2, 0, 0, 1)`. Indicator width and opacity are read in
layout/draw rather than driving composition on every animation frame. Hero pages
are clipped without parallax or scale effects. No blur, video preview or additional
image-fetch pipeline was added.

Decorative hero rotation waits eight seconds and uses the settled page, preventing
the timer from cancelling its own animation midway. Rotation stops after dragging
or invoking its action, while backgrounded, with touch exploration, or when system
animation is disabled. The details action opens the settled item and is disabled
during a swipe.

## Validation

| Check | Recorded result |
| --- | --- |
| JVM unit suites | 40 tests passed |
| Full Android suite, API 35 | 25 tests passed before the final large-text navigation adjustment |
| Final affected UI/navigation checks | All 14 passed with system font scale 2.0 after that adjustment |
| Final build | Debug, Android test APK and minified/resource-shrunk unsigned release built successfully |
| Android lint | 0 errors, 69 warnings; no blanket baseline added |
| Formatting and boundaries | Changed-file ktfmt check, Android architecture guard and `git diff --check` passed |
| Reference document | Existing design validator passed: YAML, six required sections, 23 pinned links and preview focus states |
| Release packaging | All 10 ARM native libraries, ARM64 ELF/native ZIP 16 KB alignment and packaged notices verified |

Six new device UI tests cover distinct risks: settled hero selection during a
swipe, large-text resume actions, Unicode query clearing without category changes,
full category-heading navigation, external-playback override precedence, and
complete destination labels at 200% text scale. Existing integration checks remain
in the suite. The final affected run is a subset, not 14 additional distinct tests.

The final debug update was installed on the connected **Vivo V2515, API 36**, with
replacement installation and no data reset. Physical review covered Home,
Library/filter menus, Search/keyboard, Settings, movie/show Details, expanded
synopsis, episodes and the same anime film in both Movies and Anime. Phone font
scale remained **1.15**. The isolated emulator used 2.0 for the accessibility run;
its font setting was restored afterwards. These checks do not establish exhaustive
TalkBack, device or codec coverage.

### Data preservation

Read-only before/after database snapshots contained identical IDs and complete
rows: **13 media, 187 episodes, 12 seasons and 3 watch-progress records**. This
includes metadata, watchlist, added dates, subtitle paths, file URIs, engine choices
and history. The artwork cache retained **208 files** with **zero missing artwork
references**. All 13 titles remain identified. Anime films remain stored as movies
and appear in both categories. The Movies/Shows root model is unchanged.

### Browsing performance sample

The same phone/debug-build flow used six Home swipes followed by Library, Search,
Settings and Home navigation. Frame statistics were reset for each sample.

| Android frame statistic | Before | First pass after installation | Warm repeat |
| --- | ---: | ---: | ---: |
| Frames | 1,856 | 1,778 | 1,884 |
| Missed frame deadlines | 0 (0%) | 2 (0.11%) | 0 (0%) |
| Median CPU frame time | 7 ms | 7 ms | 6 ms |
| 95th percentile | 13 ms | 14 ms | 9 ms |
| 99th percentile | 30 ms | 53 ms | 32 ms |
| Legacy jank classification | 10.45% | 9.45% | 4.88% |

The warmed sample supports smooth browsing, while the first post-install sample
had two slow frames. The baseline and repeat differ in cache/session warmth; this
is a practical device check, not a controlled benchmark or guarantee across all
screens/devices. No root cause for the two outliers was established.

## Review evidence and artifact

Open the [local screenshot gallery](../build/polish-review/index.html) to compare
13 physical screenshots and seven emulator captures. The large-text captures are
isolated UI fixtures, not every production screen. These ignored local artifacts
contain private library context and are not published.

- [Screenshot manifest](../build/polish-review/manifest.json)
- [Complete-row preservation audit](../build/polish-review/preservation-audit.json)
- [Frame comparison](../build/polish-review/frame-comparison.json)
- [Full Android results](../build/polish-final-android-tests.log)
- [Final large-text results](../build/polish-accessible-ui-tests.log)
- [Final build](../build/polish-accessible-final-build.log)
- [Changed-file formatting](../build/polish-format-check.log)
- [Release verification](../build/polish-release-verification.log)

The unsigned release APK is `app/build/outputs/apk/release/app-release-unsigned.apk`,
**100,829,629 bytes (96.2 MiB)**. Verified SHA-256:

```text
482ec751bbebc8d0a193e9f600e4b356c4f0dcc0364544263a56a79422aa4736
```

Production signing and the remaining [release checklist](RELEASE_CHECKLIST.md)
still apply. Historical implementation/phone-review records describe earlier
revisions; this report records the completed UI pass.
