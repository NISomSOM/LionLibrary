# Android pattern audit

Later authorized scope changes and regression evidence are recorded in
[BUGFIX_VALIDATION.md](BUGFIX_VALIDATION.md). The audit below describes the original
release-improvement baseline; the user subsequently removed Setup/Health UI.

Reviewed before implementation against the eight Android skills installed from `References/skills` on 2026-10-10. The existing debug build passes. This is a source audit, not a claim that device testing has passed.

## Existing strengths

- Kotlin, Compose Material 3, Koin constructor injection, Room, and reactive flows are already in use.
- Most screens separate Root (DI/platform effects) from Screen (state and actions).
- DTOs, Room entities, and domain models are separate; files and artwork remain local.
- Folder identity, specials/extras, VLC descriptor lifetime, and fontconfig have documented regression-sensitive reasons to exist.

## Gaps to correct

| Skill | Existing gap | Required treatment |
| --- | --- | --- |
| Presentation MVI | PlayerScreen receives a ViewModel; transient domain state lives in composables; form/search state is not restored | Hoist state, expose actions, isolate the playback surface, persist essential input with SavedStateHandle |
| Data layer | PlayerViewModel depends on DAOs/entities; replacement writes can change identity; destructive migration enabled | Domain playback interface, stable updates, explicit tested migrations |
| Error handling | Raw exception messages and catch-all recovery; cancellation swallowed | Typed expected failures, localized UiText, cancellation propagation |
| Navigation | String substitution routes and redundant navigation events | Serializable routes, direct navigation callbacks for simple navigation |
| Compose components | Hardcoded text/colors, small icon targets, incomplete previews | Resource strings, semantic theme roles, default Material targets, distinct-state previews |
| Koin | Generally compliant; engine creation remains hardwired | Inject engine factory and new domain contracts through existing DI modules |
| Unit testing | Limited parser/rating tests and an instrumented package-name example | JUnit 5, AssertK, Turbine, coroutine test dispatchers, fakes and documented break vectors |
| Module structure | Existing project intentionally has one module | Keep one module; enforce layer boundaries with packages and checks |

## Deliberate project adaptations

The approved plan and AGENT.md preserve a single Android module, Retrofit/OkHttp, Koin, KSP, MVI, and both engines. KMP/multi-module/Ktor examples in the skills are patterns, not a reason to replace working infrastructure. The application intentionally has a dark-only brand; previews exercise this actual theme, compact/expanded widths, and large fonts rather than inventing a light theme.

Only `ScannedFile` and `ParsedFile` are approved legacy removals: AGENT.md section 19 and a reference search both confirm they are unused. Other documented behaviors must remain.

## Test attack surface

- Matching: wrong first result, aliases, equal-confidence ties, empty results, non-Latin/emoji titles, locale-dependent case conversion.
- Episode parsing: combined files, invalid/reversed ranges, enormous numbers, wrappers, special-only shows, and extras.
- Progress: unknown/negative duration, repeated completion, delayed writes after episode change, pause/exit, rapid transitions, and cancellation.
- Library: literal SQL wildcard text, anime movies, sort ties, missing ratings, empty watchlist, saved filters, and collaborator failure.
- Scans: fatal authentication/network errors, cancellation, concurrent requests, interrupted runs, unidentified retries, and permission loss.
- Persistence: all supported schema upgrades retain IDs/history; repair does not cascade-delete episodes.

Room, networking, and device UI checks are integration/device tests, not in-memory unit tests. Physical device and signed release validation remain explicit release gates.
