# Follow-up UI fixes — 2026-10-11

The six reported issues are fixed in the current development build, installed over
the existing Vivo phone app without clearing data. Orange styling, Inter, the two
folder model, episode-list structure and playback/scanning behavior are preserved.

| Feedback | Current behavior |
| --- | --- |
| Invisible media transition | Details and Player enter with a 240 ms fade after a 70 ms delay; the outgoing browsing page fades over 90 ms. Details also fades real content when loading finishes, preventing a spinner from consuming the entrance animation. Its remembered alpha stays opaque through history updates and scrolling. System animation duration scaling is respected. |
| Home reselect glitch | A current-destination guard returns before any pop/navigate operation. The original Home entry and UI state remain intact. Explicit return to Home from a saved filtered Library stack remains supported. |
| Oversized navigation | The centered floating Material surface caps normal-text width at 280 dp and height at 64 dp, with 22 dp icons. Above 140% font scale, the expanded two-row layout preserves complete labels. Insets remain measured so final content can scroll above it. |
| Clipped years | Only the poster artwork has rounded clipping. The outer clickable Surface is rectangular; its bottom corners can no longer cut caption/year glyphs. |
| Large bookmarks | Glyphs are 18 dp; existing IconButton touch targets and independent watchlist actions remain. |
| Two-line hero metadata | Text is measured using the actual typography, density and available width. The second genre, then the first, is omitted as needed. Year/rating are retained; a final single-line ellipsis handles extremely narrow layouts. Whole Unicode strings remain intact. Measurements are remembered independently of pager animation frames. |

The supplied references were reviewed. The
[bottom navigation library](https://github.com/kareemessam09/liquid-glass-bottom-nav)
describes a compact floating treatment without backdrop blur. The
[Compose liquid-glass reference](https://github.com/kizaki-R/compose-liquid-glass)
uses Haze and AGSL, with its documented fallback starting at API 26. This app
retains its API 24 minimum and existing Material controls; no new dependency or
external implementation was imported. The floating geometry is implemented locally.

Validation:

- **57 JVM tests passed**, including six metadata-fitting cases: exact boundary,
  oversized second/first genre, missing fields, Unicode and tiny available width.
- **30 Android tests passed** on the isolated API 35 emulator. New coverage checks
  navigation dispatch/entry/state on repeated Home taps; complete one-line metadata
  at normal and enlarged fonts; and actual rendered brightness across a delayed
  Details fade, including a later history update.
- **19 affected UI/navigation tests passed at 200% system text across two runs**:
  18 passed in the suite; one screenshot capture reached Compose's two-second redraw
  timeout during release compilation. That one test passed on an isolated rerun
  after compilation, with no source change. Both logs are retained.
- Lint: **0 errors, 68 warnings**. Debug/test/minified release assembly, architecture
  guards, changed-file formatting and whitespace checks passed. Release packaging
  checks cover 10 ARM native libraries, ARM64 ELF/native ZIP 16 KB alignment and notices.
- Phone snapshots have identical complete rows: **13 media, 187 episodes,
  12 seasons and five progress entries**. All **208 artwork files** remain, with
  **zero missing references**. No crash was observed in the checked LionLibrary log.
- The short physical sample includes screen recording, media entry/back and repeated
  Home taps: **676 frames, four missed deadlines (0.59%), median 7 ms, p95 15 ms**.
  Its flow and recording overhead differ from earlier samples; it is not a controlled
  comparison or an exhaustive playback/accessibility/device benchmark.

Evidence remains local and private:

- [Screenshot gallery and phone recording](../build/feedback-review/index.html)
- [Complete-row preservation audit](../build/feedback-review/preservation-audit.json)
- [Final build/unit/lint log](../build/feedback-final-checks.log)
- [Full Android results](../build/feedback-android-tests.log)
- [Enlarged-text suite](../build/feedback-large-text-tests.log) and
  [single screenshot-timeout rerun](../build/feedback-large-text-fade-retest.log)
- [Release build](../build/feedback-release-build.log) and
  [native packaging verification](../build/feedback-release-verification.log)
- [Phone frame statistics](../build/phone-review/feedback-frame-stats.txt)

The release APK is unsigned, 100,828,365 bytes (96.2 MiB), at
`app/build/outputs/apk/release/app-release-unsigned.apk`. Its verified SHA-256 is:

```text
5419bcb19c23216117f84e869963d0f0a1e984a5706d93fea024c106f023382e
```

Public distribution still requires the existing signing and beta gates. The
phone received the debug development build; the release APK was inspected locally.
