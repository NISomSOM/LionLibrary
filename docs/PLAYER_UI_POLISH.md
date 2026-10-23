# Player visual polish — 2026-10-11

This pass implements the requested small visual changes and adds no player options.

- The +90s/Subs/Audio bar is a compact outlined translucent pill. Labels use Inter
  12/16 sp medium and icons are 16 dp. Button targets remain at least 48 dp; options
  can wrap at narrow widths or enlarged font sizes.
- Elapsed time and total duration are small badges beneath the seek bar, aligned
  to its left and right edges. The slider now spans the available width.
- Bookmarks use a shared 16 dp glyph. Cards have a 24 dp circular contrast badge
  instead of shading the entire 48 dp target; Details retains a 48 dp outlined
  control. Watchlist actions stay independent of opening/playing media.

`PlayerBottomControls` takes position/duration and action callbacks. The existing
scrub, scrub-end, +90-second seek, track refresh and sheet-opening actions are
preserved. Time formatting was moved unchanged. Playback engines, ViewModel,
gestures, system-bar handling, track sheets and episode-list logic were not changed.

Validation completed:

- Eight affected Android UI tests pass on the isolated API 35 emulator, including
  the new footer check at normal and 200% text. The footer check also passes in
  landscape on the final build. It verifies time placement, hours-long duration,
  seek state/commit, all existing options and 48 dp button targets.
- Debug/test/minified release builds, changed-file formatting, architecture guards
  and whitespace checks pass. Lint has **0 errors and 68 existing warnings**.
- Native packaging verification passes for 10 ARM libraries, ARM64 ELF/native ZIP
  16 KB alignment and required notices.
- The final debug build is installed on the Vivo phone without resetting data.
  The paused player, Library/Details bookmarks and existing subtitle/audio sheets
  were inspected. No tracks were changed. The movie was briefly resumed and paused.
- Complete media/episode/season rows remain identical: **13/187/12**. All **eight
  existing progress keys** remain; only Michael's progress/position/timestamp
  advanced during the playback check. **208 artwork files** remain with **zero
  missing references**.

Local evidence:

- [Nine-image review gallery](../build/player-polish-review/index.html)
- [Phone preservation audit](../build/player-polish-review/preservation-audit.json)
- [Final build/lint](../build/player-polish-verified-build.log)
- [Affected Android tests](../build/player-polish-android-tests.log)
- [Final landscape footer check](../build/player-polish-landscape-tests.log)
- [Release verification](../build/player-polish-final-release-verification.log)

The unsigned release APK is 100,828,365 bytes (96.2 MiB). SHA-256:

```text
c27785c6fdf83a02d9c7f0cc186f391a649698e1e75506a13f440854861b1fac
```

Previous full-suite results and release hashes remain historical in their dated
reports. This pass used focused UI checks for its visual scope.
