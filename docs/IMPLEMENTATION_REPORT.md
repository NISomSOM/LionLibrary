# Public-release improvements — implementation record

**Later revision:** Onboarding and Library Health/manual repair UI were removed at the
user’s request. Recognition, artwork recovery and Nuvio-inspired styling are recorded
in [BUGFIX_VALIDATION.md](BUGFIX_VALIDATION.md); the original scope below is historical.

Reviewed against References/AGENT.md before changing behavior. Installed all nine
skills from References/skills into the local Codex skills folder; used the eight
Android skills for the audit and implementation. PR quiz is for PR creation and
was not invoked. See ANDROID_PATTERN_AUDIT.md for the existing-code assessment,
project-specific adaptations, preserved behaviors and regression attack surface.

## Customer-facing improvements

- Resumable setup: personal TMDB key verification, offline saved/unverified state,
  folder selection, scan progress, cancellation and retained run summaries.
- Labelled Home, Library, Search and Settings destinations, with a tablet navigation rail.
  Category headers open filtered Library results; essential queries and selections restore.
- Library type, genre, year and watch-state filters; title, recent, year and rating
  sorting; independent watchlist controls on cards and details; useful empty states.
- Library health: raw-file playback, identification retry, metadata search/manual
  matching, grouped show repair, manual episode assignment, availability checks and artwork refresh.
- Visible overflow actions, Material touch targets, safer insets, local font resources,
  reduced-motion/accessibility-aware hero rotation, accurate playback/rating labels and
  localized new copy. Privacy, TMDB attribution/logo and readable license texts in About.

## Reliability and maintainability

- Room schema 13 with populated non-destructive upgrades from schemas 6–12.
  Stable updates/upserts protect episode/history identities and metadata refreshes.
  Explicit final child-table creation avoids Android SQLite legacy rename behavior.
- Movie/show TMDB namespaces remain distinct; different movie files may share metadata.
  Combined-episode files retain their range. Match ranking refuses ambiguous ties.
- App-wide scans serialize requests, cancel with completed writes flushed, retain
  interrupted status/checkpoints, and stop on fatal authentication/network errors.
- Artwork uses private durable storage, unique temporary files and atomic publication;
  orphan cleanup has a grace period. Metadata repair preserves history and engines.
- Player uses an injected domain playback repository, engine factory and audio-focus
  adapter. Progress writes capture identity before suspension and queue in order;
  completion is captured before the UI exposes ENDED. Transitions select each file's
  engine. Recovery is bounded; failed external launches keep the player available.
- Serializable navigation, state/action Screen parameters, typed failures and UiText,
  constructor DI, formatting tasks, layer guards, JUnit 5 logic tests and Android
  integration tests. Test directories are tracked rather than ignored.
- Backup includes library/history and portable playback choices; credentials, SAF
  selections and artwork remain device-local. Universal ARM32/ARM64 release is
  minified and resource-shrunk. Production signing reads external environment values.

## Validation evidence

Final local validation on 2026-10-10 passed:

- JDK 21 Gradle build: format/check format, debug unit tests, debug lint, connected
  Android tests, debug APK and minified/resource-shrunk release APK.
- 32 unit tests and 14 Android integration/UI tests on the isolated API 35 emulator.
- Populated Room upgrades from each schema 6–12, validated against schema 13.
- Host SQLite legacy-ALTER migration checks and 1,000-title/10,000-episode browse smoke.
- Domain/ViewModel/Screen/migration pattern guard script.
- Lint: zero errors, 76 warnings. Remaining warnings include dependency-update
  notices, direct test-dependency declarations, legacy resources/font certificate
  spell checks, KTX/style suggestions and the intentional ARM-only release scope.
- 10 native libraries across ARM32 and ARM64; ARM64 ELF LOAD segments and native
  APK ZIP offsets pass 16 KB alignment. SDK zipalign verification also passed.
- Actual setup/library screens reviewed on the emulator, including font scale 2.0.
  Corrected selected-navigation contrast and made filter/results areas independently scrollable.

Release artifact: app/build/outputs/apk/release/app-release-unsigned.apk.
SHA-256: `8f1b6eb56c858bbcdbdc01a8033457dafe4fe67c2c82baab13fef7120dc7c3d0`.
Detailed command evidence: build/completed-validation.log. Reports remain under
app/build/reports and build/release-apk-verification.json. UI review captures remain
in build/reviewshots, including library-large-text-final.png and setup-large-text.png.
GitHub Actions is configured, but its remote execution has not been observed here.

## Remaining distribution gates

No production keystore was supplied: the generated release APK is an unsigned
validation artifact. Complete signing with the original key, exact VLC/source/relinking
and notice distribution, review the existing TMDB logo against the current brand kit,
and run the physical-device/customer beta checklist before publishing.

Android 7 ARM32, real ARM64 phones/tablets, a 16 KB device, TalkBack, actual SAF
providers/removable storage and real media/codec/audio-focus behavior still need
physical-device validation. The emulator and synthetic repository tests do not
establish those outcomes. Remaining lint warnings are reviewed separately from errors;
no blanket suppression or destructive database fallback was introduced.

TV, accounts/cloud sync, payments, portable export and multiple additional roots
remain deferred as agreed. The single-module architecture and existing Retrofit,
Koin and dual-player infrastructure are retained.
