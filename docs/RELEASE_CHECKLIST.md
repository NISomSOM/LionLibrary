# Public release gates for 2.1.0 (version code 3)

## Automated verification

Use JDK 21 and an Android SDK with platform 37. Run:

```
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
python scripts/verify_schema_upgrades.py
python scripts/check_android_patterns.py
python scripts/verify_release_apk.py
```

The release variant is minified/resource-shrunk and includes ARM32 and ARM64 in one APK.
The debug variant keeps x86_64 so emulator checks can run. Check R8 mapping output,
APK ZIP and ELF alignment (16 KB), and a clean install/upgrade of the signed release.
Host SQLite validation supplements, and does not replace, Android Room migration tests.

## Signing

Keep the production keystore outside this repository. Preserve the existing signing
identity for upgrades. Set LIONLIBRARY_KEYSTORE, LIONLIBRARY_STORE_PASSWORD,
LIONLIBRARY_KEY_ALIAS and LIONLIBRARY_KEY_PASSWORD in your local environment or
CI secret store, then assembleRelease. All four are required to enable release signing.
Without them Gradle produces an **unsigned validation APK**, never a customer build.
Do not generate a replacement production key automatically. Archive signed APK,
SHA-256, mapping.txt, source revision, dependency inventory and test evidence.

## Device/beta gate

- Android 7/API 24 ARM32 and modern ARM64 phone/tablet, including a 16 KB device.
- Compact/expanded widths, font scale 2.0, TalkBack, keyboard/IME, cutouts and system Back.
- TMDB valid/invalid/offline key, provider revoked grants and SD-card removal/reconnection.
- Migration from each shipped schema; watchlist, completed history, subtitle and engine preservation.
- Folder fixtures: nested movie collections, merged seasons, wrapper folders, special-only shows,
  extras/NCOP/NCED, absolute anime numbering, combined episode files and duplicate movie encodes.
- Exo/VLC next/previous, manual seeks, pause/background/exit, audio focus/noisy output,
  unsupported tracks, unavailable files, external chooser success and cancellation/failure.
- Interrupted/cancelled scans, automatic missing/corrupt artwork recovery, local-file fallback and sort/search.
- External file/folder deletion followed by rescan; revoked, offline and partially loaded
  roots must retain entries. Confirmed movie/show/episode deletion must preserve siblings
  and refuse shared folders, unindexed videos, changed previews and either library root.
  Exercise provider partial failures and combined-episode warnings using disposable fixtures.
- Anime films in Movies must appear in both Movies and Anime; test Romaji aliases and remake years.
- First launch opens Home; Settings exposes only the two roots and original core controls.
- Browse/search/playback with 1,000 titles and 10,000 episodes, offline after a scan.
- Small private beta before publishing the signed APK. Record failures and rerun affected gates.

## Credits and distribution

Ship docs/PRIVACY.md and the APK's local THIRD_PARTY_NOTICES.md/license assets.
Complete the exact-source/relinking obligations for the VLC AAR and transitive codecs;
retain dependency notices. TMDB attribution and the repository’s existing logo are displayed in Settings.
Review that asset against TMDB’s current official attribution kit before distribution.

Online skip times (TheIntroDB/SkipDB) are a release gate of their own:

- **Confirm TheIntroDB's terms of use (and any attribution or app-registration requirement)
  for keyless reads in a distributed app before public release.** Not yet confirmed.
- Confirm that SkipDB's ODbL 1.0 + Service Provider Reciprocity terms are met by in-memory,
  per-session display with the attribution in Settings > About and NOTICE.md.
- Re-check both providers' rate limits (TheIntroDB `/media` 30 per 10 s; SkipDB reads 120 per
  minute when last checked on 2026-10-24) and that the `LionLibrary/<version> Android`
  User-Agent is acceptable to them.
- On a device: chapter buttons on Exo and VLC, an online match, buttons with controls hidden,
  pause kept after a skip, terminal credits opening the next episode exactly once, and the
  switch turning online buttons off. Ship the updated docs/PRIVACY.md.

Sources: [Android backup rules](https://developer.android.com/identity/data/autobackup),
[TMDB key validation](https://developer.themoviedb.org/reference/authentication-validate-key),
[TMDB attribution](https://developer.themoviedb.org/docs/faq),
[VideoLAN libVLC license](https://www.videolan.org/press/lgpl-libvlc.html).
