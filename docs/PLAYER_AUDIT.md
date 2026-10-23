# Player and dependency audit — 2026-10-12

Both engines now pass native playback checks on the connected Vivo V2515 (Android
16/API 36). Hardware decoding and HDR10 output were observed, rather than inferred
from configuration flags. This is a bounded compatibility check, not a claim of
optimal performance for every file/device or complete public-release certification.

## Fixed issues

- VLC could autoplay after a lifecycle pause issued before its surface existed.
  Play/pause intent now survives loading; duplicate pause stays paused.
- A fixed 300 ms resume timer could discard a seek before the input became seekable.
  Pending seeks now apply on native readiness/seekability, including paused seeking.
- VLC sidecars were copied synchronously and shared one cache filename. Preparation
  now runs on IO, uses a unique owned file, and cleans up cancellation/stale results.
  Descriptor lifetime extends through native playback and ends after stop/release.
- The native test exposed a second sidecar defect: the MediaPlayer String overload
  treated a `file://` URI as a filesystem path, and the slave was attached before
  an input existed. Register an explicit `Media` subtitle slave before playback.
- Fontconfig setup could race startup prewarming and player creation. Configuration
  and native singleton construction now share one lock; initialization is marked
  successful only after environment setup. Keep the single-font cache workaround.
- Removed global prohibition of late-frame dropping/skipping; VLC can use its normal
  catch-up behavior when decoding falls behind. Preserved audio backend compatibility,
  hardware-with-software-fallback selection and other documented codec/font options.
- Capability probing previously cached by MIME/resolution and ignored codec profiles,
  frame rate and HDR color information. It now uses Media3's renderer-compatible
  capability checks and includes those properties in its cache identity.
- Late native track discovery could miss language defaults. Apply audio/subtitle
  defaults independently when tracks become available; later track changes preserve
  manual selections, including Subtitles Off. Exo track labels retain both language
  and title so full-dialogue subtitles can be distinguished from signs/songs.
- Anime movies use the persisted Anime genre marker to prefer Japanese audio while
  retaining MOVIE identity, history keys and the two-folder model.
- Screen-awake ownership now covers both engines and is removed when playback stops.
  Exo view detachment and repeated release are explicit and safe.

The existing session mutex, progress writer, completion rules, next/previous and
auto-next, audio focus/noisy/lifecycle handling, manual track menus and external
player routes remain. Native/view commands stay on the main thread; blocking SAF
preparation is a suspend operation on IO. No new settings or playback features.

## Stable dependency updates

Checked official Google Maven/Maven Central metadata and upstream documentation.
The machine-readable inventory is [DEPENDENCY_AUDIT.json](DEPENDENCY_AUDIT.json).

| Dependency | Before | After |
| --- | --- | --- |
| Media3 | 1.10.1 | 1.11.1, including Inspector |
| libVLC Android | 3.7.5 | 3.7.7 |
| Coil | 2.7.0 | 3.6.3 |
| Kotlin compiler/Compose/serialization plugins | 2.2.10 | 2.4.21 |
| KSP | 2.3.6 | 2.3.12 |
| Compose BOM | 2026.06.01 | 2026.09.00 |
| AndroidX Core | 1.19.0 | 1.19.1 |
| Navigation Compose | 2.9.8 | 2.10.2 |
| Room | 2.8.4 | 2.8.5 |
| OkHttp | 5.4.0 | 5.5.0 |
| Retrofit serialization converter | Archived JakeWharton 1.0.0 | Official Square 3.0.0 |
| Gradle wrapper | 9.6.1 | 9.8.1, regenerated scripts/JAR and pinned checksum |
| JVM JUnit BOM | 5.13.4 | 6.1.3 (Jupiter/Vintage, JDK 21) |
| ktfmt | 0.61 | 0.64 |

The other declared libraries already matched stable repository versions. Foojay
1.0.0 is current according to the [Gradle plugin portal](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention).
Test/tool dependencies now use the catalog. Remove the unused Kotlin Android
plugin declaration; AGP's built-in Kotlin remains enabled.

Media3's old ExoPlayer MetadataRetriever was removed. Use Inspector's AutoCloseable
retriever and close it after probing, including cancellation/timeout. Coil 3's
singleton factory and local-image APIs replace Coil 2; explicit artwork cache keys
and fallback behavior remain. No network image module is needed for durable local
artwork. Room schema remains 13; no customer-data migration/reset was introduced.
Bundled libVLC notices name the new version. Normalize the 22 existing Kotlin files
that failed the updated repository-wide formatter; this final pass changes formatting
only and its source backup/list are retained under `build/`.

References: [Media3 release notes](https://developer.android.com/jetpack/androidx/releases/media3),
[Coil 3 migration](https://coil-kt.github.io/coil/upgrading_to_coil3/),
[archived converter's migration notice](https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter),
[AGP built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin).

## Device evidence and practical limits

The six native-only instrumentation tests never query or write the customer library
or watch history. They use pinned AndroidX fixtures packaged only into the test APK;
the private fixture provider exists only in the debug variant.

| Physical-phone check | Result |
| --- | --- |
| AVC SDR, both engines | `c2.mtk.avc.decoder`; playback, early resume, repeated pause, paused seek and sidecar availability pass |
| HEVC Main10 HDR10, both engines | `c2.mtk.hevc.decoder`; decoded and displayed frames observed |
| HDR surface output, both engines | App SurfaceView buffers have dataspace `0x11c60000`: BT.2020, ST2084/PQ, limited range |
| HDR display capability | Android reports HDR10, HLG and HDR10+; no Dolby Vision display capability reported |
| HLG | Transfer metadata survives Inspector extraction; full HLG playback/display was not measured |
| Missing SAF input | Localized file-access failure; no playback starts |
| Library/artwork/history preservation | Exact before/after equality for every row: 14 media, 192 episodes, 14 seasons, 1 watch-progress row; 218 artwork files, zero missing references |

Short samples are insufficient to quantify long-session battery, thermal throttling,
4K/high-frame-rate throughput, decoder behavior after prolonged seeking or all
subtitle codecs. HDR10+/Dolby Vision playback was not validated. HDR10 decoding and
PQ surface output do not certify optical color accuracy or peak luminance. Retain
these as beta/device-matrix checks rather than forcing decoding/tone-mapping flags.
[Android documents the platform/device dependency of HDR](https://developer.android.com/media/media3/exoplayer/supported-formats).
Both renderers retain SurfaceView for direct output, consistent with
[Android's power/timing/HDR guidance](https://developer.android.com/media/media3/ui/surface).

Example diagnostic counters: Exo SDR rendered 38/dropped 0; Exo HDR10 rendered
32/dropped 1; VLC HDR10 displayed 32/lost 0. These snapshots include startup and
seeking and are **not** steady-state drop-rate or before/after benchmarks.

## Final validation and artifacts

- 62 JVM tests pass; no failures/errors/skips.
- API 35 x86_64 full Android suite: 38 discovered, 37 pass, 1 assumption skip.
  The skipped Exo HDR10 fixture exceeds the emulator decoder's advertised profile
  support. That same test passes on the physical phone. VLC's software fallback
  renders it on the emulator. All 6 native tests pass on the phone.
- Whole-repository Kotlin formatting, architecture guards and populated host schema
  6–12 → 13 checks pass. Android Room migration checks are included in the suite.
- Final debug/test and R8/resource-shrunk release assembly pass. Lint: 0 errors,
  46 warnings; no warning baseline was added. Existing Gradle deprecations remain
  relevant before a future Gradle 10 upgrade.
- Final ARM32/ARM64 unsigned APK: 100,366,541 bytes (95.7 MiB), 10 native libraries;
  ARM64 ELF load alignment and all native ZIP offsets meet 16 KB requirements;
  required notices are bundled. Test fixtures/provider are absent from release.
- SHA-256: `5f230e0845c5446b73ada71778a86995614cbdd3f994b6289175a756bdd327a7`.

The complete Android/native runs preceded the final formatting-only normalization;
the final builds, unit tests, formatting and lint checks ran afterward. The final
debug build is installed without clearing data and the phone is left in LionLibrary.
The isolated emulator is stopped. Remote CI and signed-release installation were
not run. Existing signing, native-source/license distribution and beta gates remain.

Local evidence: `build/player-audit-final-verification2.log`,
`build/player-audit-all-emulator.log`, `build/player-audit-phone/` (native results,
decoder logs, output surfaces/screenshots), `build/phone-review/player-audit-*`,
`build/release-apk-verification.json`, and the usual Gradle unit/lint reports.
