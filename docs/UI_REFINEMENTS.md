# UI interaction fixes

Later feedback changes the fade timing, compact bar size and poster/hero details. See [current fixes](UI_FEEDBACK_FIXES.md); this report records the preceding build.


Completed **2026-10-11**, following the first [UI polish](UI_POLISH.md). This record
supersedes that pass's four-tab navigation, resume overlay controls, static hero
paging and immediate Details toolbar rules.

## Requested behavior and implementation

| Request | Implemented behavior |
| --- | --- |
| Initial show playback label | Play until at least one episode is completed, including a later episode or season. Resume takes precedence for an unfinished target; Replay remains for a completed target. Movie behavior is preserved. |
| Floating navigation | Home, Library and Settings overlay the page in one translucent rounded capsule. Content draws beneath and around it; scroll content has enough trailing space to expose the last item. There is no reserved full-width background strip. |
| Jump Back In cards | Landscape artwork, lower gradient, episode code above the title, episode title below, top-right time/Next up badge and inset orange progress. No overlaid play or three-dot buttons. Tap resumes; long press and an accessibility action retain the existing options. |
| Search tab | Removed its route, screen, ViewModel, binding and unused copy. Library retains its query field, filters, sorting, watchlist and restored input. Repository search contracts remain available internally. |
| Category shortcut navigation | Movies/TV/Anime headers still apply their Library filter. Home navigation returns directly to the Home entry instead of restoring a saved Library stack under the Home route. |
| Media transition | Details uses a short 120 ms enter/pop-enter fade and 100 ms exit/pop-exit fade. Other navigation retains its existing restrained fades. |
| Carousel motion | Crossfading artwork and captions have different, restrained horizontal translations; the backdrop also moves slightly during vertical scrolling. Indicators use the live page offset in draw, so they follow the swipe without a second delayed animation. |
| Carousel action | View Details retains its white appearance while paging. The action waits for the settled title, preserving the correct media identity. |
| Home status bar | Hero artwork extends under the status bar. Horizontal display-cutout insets and the floating navigation's system gesture inset are retained. |
| Details toolbar | The logo and opaque surface appear after the bottom of the primary playback action passes the toolbar boundary, including direct scroll jumps. Back remains accessible from the start. |

The cards follow the inspected Nuvio Card layout: 247 dp width, 16:9 artwork,
12 dp corners, 10 dp caption inset, 2 dp text gaps, 7 dp badge inset and 3 dp progress
with 9/4 dp horizontal/vertical insets. Title text uses Inter 14 sp semibold;
episode/badge text uses 10 sp, matching the compact reference. LionLibrary retains
system text scaling and moves the caption below artwork above 140% scale.

The reference was [Nuvio's pinned Home components](https://github.com/NuvioMedia/NuvioMobile/tree/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components).
The supplied [liquid-glass navigation project](https://github.com/om-prakash-yadav/liquid-glass-navigation)
is a Flutter package; its floating treatment informed an independent Material 3
Compose implementation. No external navigation dependency, blur pipeline, Nuvio
source, artwork or font files were imported.

## Engineering and preservation

- A pure playback-label selector expresses the Play/Resume/Next Episode/Replay rules.
- Resume badges use captured position/duration from existing watch-progress columns.
  The Room projection and mapper expose those values; no table/schema migration is
  needed. Next-up items clear the previous episode's timing. Unknown durations use
  watched percentage rather than invented minutes; invalid/bounded values are handled.
- Scroll content receives the measured floating navigation inset through a
  presentation composition local. Scaffold padding is still applied and consumed;
  its explicit zero content insets allow the overlay layout without a lint suppression.
- Hero offsets are read in graphics layers and the indicator Canvas. Only current
  and neighboring artwork layers participate. Lifecycle/touch-exploration guards
  remain, and system-disabled animation suppresses autoplay and parallax transforms.
- Episode list/season/action source was compared with the pre-change snapshot and
  is identical apart from formatting. Scanner, root selection, identities,
  subtitles and playback engines are preserved.

Fresh physical before/after snapshots have identical complete rows and identities:
**13 media, 187 episodes, 12 seasons and 5 progress records**. These counts are from
this task's baseline, after the user had watched more content; the prior pass's
three history records are historical. Artwork remains **208 files**, with **zero
missing references**; all titles remain identified.

## Validation and device review

Final results:

- **51 JVM tests passed**, including 11 new playback-label and status-boundary cases.
- **27 Android tests passed** on the isolated API 35 emulator. The Room check also
  verifies duration/position projection and that next-up timing does not leak.
- All **16 affected UI/navigation tests passed again at 200% system text scale**;
  this is a subset of the full suite, not 16 additional distinct tests.
- Debug/test APKs and the minified/resource-shrunk release built successfully.
- Android lint: **0 errors, 68 warnings**. Changed-file ktfmt, architecture guards
  and whitespace checks pass. No blanket lint baseline or inset suppression.
- Release inspection verifies 10 ARM native libraries, ARM64 ELF/native ZIP 16 KB
  alignment and packaged notices.

The final debug APK is installed on the Vivo V2515/API 36 with replacement
installation and no data reset. Phone font scale remains 1.15. Reviewed both Movies
and TV header shortcuts followed by Home navigation, untouched Frieren's Play label,
Details before/after the toolbar threshold, resume timing and long-press options,
Home edge-to-edge drawing and the carousel mid-swipe. No playback was started and
no app crash was observed during this review. The emulator's text scale was restored
after the accessibility run.

The bounded physical Home flow used seven horizontal swipes (one captured midway)
and six vertical swipes. **2,300 frames**: median 8 ms, p90 10 ms, p95 12 ms,
p99 28 ms; one missed deadline (0.04%), zero missed-vsync/slow-bitmap-upload events.
Legacy jank classification was 8.70%. This debug-device sample uses a different flow
from the earlier pass; it does not prove a comparative speedup or all-device
performance. TalkBack and broader playback/device coverage remain separate checks.

## Local evidence and release artifact

The [17-image review gallery](../build/refinement-review/index.html) contains ten
physical captures and seven enlarged-text emulator fixtures. Keep this private
local library/device evidence local.

- [Complete-row preservation audit](../build/refinement-review/preservation-audit.json)
- [Frame statistics](../build/refinement-review/motion-sample.json)
- [Final build](../build/refinement-final-build.log)
- [Full Android results](../build/refinement-final-android-tests.log)
- [Final enlarged-text results](../build/refinement-final-large-text-tests.log)
- [Formatting check](../build/refinement-format-check.log)
- [Release verification](../build/refinement-release-verification.log)

Unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`,
**100,828,365 bytes (96.2 MiB)**. SHA-256:

```text
c00b566d98bee39d39cfa3da22235af86926c75254033b6409f5c12951f666f7
```

The existing [public-release checklist](RELEASE_CHECKLIST.md) still applies.
