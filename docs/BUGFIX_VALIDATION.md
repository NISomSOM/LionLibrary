# Recognition, artwork and UI regression revision

Date: 2026-10-10. Development version: 2.1.0 (3), schema 13.

The user requested exactly two roots, Movies and Shows. Anime films live in Movies,
retain MOVIE storage/playback identity, and appear in both Movies and Anime. Anime
series live in Shows. This revision introduces no additional folder or Settings option.

## Changes and reasons

- Home previously excluded anime-tagged films from Movies. Movies now includes all
  movies; Anime additionally includes tagged films. Hero and genre collections retain
  deduplication by local ID, and Library's existing overlapping category query is tested.
- Exact titles from different years could both score 1.0, losing the parsed year's
  evidence and rejecting Michael (2026) as ambiguous. Year agreement now breaks that
  tie; true equal-evidence matches still remain unidentified. Search considers original
  titles and a bounded API-relevance/text-similarity shortlist, including aliases for
  already-confident candidates. Unmatched movie rows retain their parsed year.
- Known titles skipped matching on every scan and never recovered lost local images.
  The coordinator now waits for missing/corrupt artwork recovery before publishing
  completion. Artwork-only DAO updates preserve identity, descriptions, numbering,
  watchlist, subtitles, engines and history. Good cached files are reused; missing
  remote seasons/stills do not cause unrelated episode metadata to be substituted.
- Image publication rejects empty/non-image/corrupt responses and signals a domain
  artwork revision. Shared LocalArtwork tries alternate local files; logos retain a
  readable title on failure. Requests detect same-path replacements while unchanged
  files retain their cache keys. Episode thumbnails can fall back to show artwork.
- Unmatched rows with file paths are visible in Library/Search as Local file. Details
  and playback treat legacy raw TV/anime rows as single files; watch-state filtering
  uses their episode-zero progress correctly. Successful retries preserve stable IDs,
  watchlist/discovery time, preferred engine, subtitles and history.
- Removed onboarding and Library Health UI/routes/DI and Details repair actions, at
  the user's explicit request. Settings returns to sectioned key/folders/playback/scan
  controls, with active cancellation, history confirmation and local About/notices.
  The app launches directly into Home. Metadata identification internals remain for
  scanning and existing history-preservation regressions.
- Independent Nuvio-inspired typography, dark surfaces, portrait/landscape cards and
  bounded hero layout retain LionLibrary orange. Inter is bundled as four static
  weights for offline use across all typography roles; its OFL notice is included.
  No Nuvio GPL implementation or uncertainly licensed Nuvio font was copied.
- Restored the damaged JumpBackInCard source, preserving next-up, progress, playback
  and overflow actions. Removed unused strings from retired UI after a reference/lint
  check. Existing unrelated workspace changes were retained.

## Verification

- Debug application and Android-test APK builds; 40 JVM tests, zero failures/skips.
- 19 Android integration/UI tests passed on the isolated API 35 emulator: populated
  Room upgrades, browse/watch-state/raw playback, identification/history preservation,
  missing/corrupt image recovery with intercepted HTTP, recovery concurrency, player
  sessions, direct Home launch, simplified Settings, large text, watchlist and same-path
  artwork reload. Tests used fixtures, not the customer's phone database.
- Android lint: zero errors, 69 warnings (earlier release-improvement report: 76).
  Existing warnings remain; this is not a claim of universal skill compliance.
- Domain/ViewModel/Screen architecture guardrails and populated upgrades 6 through 12
  to 13 pass. No schema change or destructive migration was introduced.
- Minified ARM release build and APK verification: native ELF/ZIP 16 KB alignment and
  local notices are checked. Build is unsigned; signing/licensing/beta release gates
  remain in RELEASE_CHECKLIST.md. Final unsigned release: 96.2 MiB. SHA-256:
  `450355e5a526d5efd5378ca5572c112ad053c502cb129007a83e3c81bce697bf`.
  Artifact details: build/release-apk-verification.json.
- Settings was visually reviewed; its standalone background/title contrast was
  corrected. Debug validation artifacts and screenshots are under build/reviewshots.

Evidence files (build outputs are ignored by Git): bugfix-verified-build.log,
bugfix-device-tests-final.log, bugfix-apk-validation.json, Gradle test XML and lint reports.

## Phone observations and remaining limits

ADB read access worked on the authorized Vivo V2515, API 36. The selected roots were
Movies/Movies and Movies/Shows, and the moved Romaji anime film was confirmed inside
Movies/Movies. The earlier cache audit found 79 missing artwork references, including
season posters. A read-only backup captured 19 media rows, 310 episodes and 16 progress
rows before any attempted update. Credentials were not exported into this report.

The phone subsequently reconnected and the debug update was installed with `install
-r -t`, preserving app data. The live review reproduced and fixed a further saturated
same-year matching tie, transparent-logo fallback overlap, repeated Library captions,
ratings reflow, scrolled toolbar contrast and phone orientation after playback.
Michael (2026) now matches TMDB 936075 without changing its local ID. All 190 current
video files in the selected roots are catalogued; all 13 current titles are identified.
Media IDs, episode rows, added dates, watchlist, subtitles and engine preferences were
compared before/after and preserved. This review's starting catalog differs from the
older backup above; see [physical phone review](PHONE_REVIEW.md) for the exact baseline,
screenshots, checks and playback limitations.

Remote metadata/artwork can be absent or unavailable. Unknown files remain local;
show artwork covers missing episode stills without renumbering files. Old URIs outside
the two granted roots cannot be rediscovered/opened just because their filenames match.
This revision does not merge/delete those records by filename or reset customer data.

References: [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile),
[Inter license](https://github.com/rsms/inter/blob/master/LICENSE.txt).

Final phone follow-up: the current APK is installed on the Vivo. All 208 cached images decoded, zero catalog files are unmatched, and the eight affected UI/navigation checks passed again after the final track-picker layout change. See PHONE_REVIEW.md for the complete evidence and private screenshot gallery.
