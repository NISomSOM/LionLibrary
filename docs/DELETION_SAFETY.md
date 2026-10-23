# Missing-file cleanup and explicit deletion

Regression attack surface, recorded before writing tests (2026-10-17):

- Empty healthy root after external deletion versus revoked, offline, loading, null
  or failed provider listings. Only a complete inventory plus an explicit missing
  document result may remove a library row. Unconfigured roots stay untouched.
- Preserve sibling movies/episodes, their IDs, watchlists and history. Removing the
  last episode removes its empty season/show; combined files represent their whole range.
- Opaque document IDs, Unicode names, prefix-colliding siblings, cycles, wrapper
  folders, show seasons split across folders and files directly in Shows.
- Never delete either configured root, a movie's parent folder, a directory that
  contains another root/show/movie, an unindexed video, or a read-only target.
- Added/replaced files, changed roots/permissions/library ownership while confirming;
  forged, replayed or duplicate confirmation tokens. Revalidate before one native call.
- Provider false/throw/partial deletion: no parent fallback; reconcile only proven
  missing rows and report failure. Cancellation after native mutation must still clean up.
- Scan/delete overlap and late progress writes must not resurrect stale rows.
- UI preview, cancel and duplicate confirm behavior, visible exact path, permanent
  deletion warning and combined-episode warning; episode menu retains existing actions.

Pure policy/presenter tests use in-memory values and coroutine test dispatchers.
Room/provider tests are Android integration tests, run only on an isolated emulator
with synthetic documents. Customer video files are never used as deletion fixtures.

## Implemented contracts (2026-10-18)

Scanning starts with `LibraryReconciler` under the shared `LibraryMutationGate`.
A successful complete inventory and an exact missing-document probe are both required
before removing a file's catalog/progress rows. Empty healthy roots are reconciled;
revoked, offline, loading, null or failed inventories and unconfigured roots retain
entries. Missing episodes also prune their affected empty seasons and shows. Surviving
IDs, watchlists and progress stay intact. Scanning never deletes storage or artwork.

Home poster shelves, hero and Jump Back In expose **Delete from device** on long press.
The existing episode menu gains the same action. Movies (including anime movies) and
episodes delete one video file; shows delete their validated direct child folder of Shows.
Movie/episode sidecars stay in place. Show-folder confirmation explicitly includes all
its contents and the file count. Combined-episode files are indivisible and warn about
the displayed episode range. Existing episode actions/layout and playback stay intact.

`SafMediaDeletionRepository` issues an in-memory single-use confirmation token and
displays the exact root-relative path. Confirmation rebuilds the plan and compares
configured roots, ownership, verified ancestry, contents, flags, sizes and modification
metadata. Changed or tampered plans need a new preview. Unknown file metadata,
read-only targets, loose show files, multiple source folders, shared indexed content,
unindexed videos and either configured root (including a nested root) are refused.
Document IDs are opaque: names and URI prefixes never establish ancestry.

One exact `DocumentsContract.deleteDocument` request is made. There is no retry against
a parent or library root. Provider false/throw/partial deletion triggers conservative
reconciliation and a failure message. Once native mutation begins, coroutine cancellation
cannot abandon the subsequent reconciliation. A late player-progress write checks
parent/episode existence in a database transaction to avoid recreating deleted history.
No schema change, reset, new setting or third folder type was introduced (schema 13).

SAF and Room cannot form one atomic transaction. Process death, an unreliable provider
or an external replacement after validation cannot be completely eliminated; a later
rescan recovers proven missing rows. Provider flags and last-modified metadata are
verification inputs, not a filesystem-level atomic versioned-delete guarantee.

The Android adapter follows the official [DocumentsContract API](https://developer.android.com/reference/android/provider/DocumentsContract)
and [Document flags/metadata contract](https://developer.android.com/reference/android/provider/DocumentsContract.Document).
Loading cursors are rejected. A null directory listing is never treated as empty;
the exact-document probe separately handles providers that translate FileNotFound
into a null cursor, confirming absence through opening that same document.

## Verification

- **76 JVM tests pass**, including 10 pure deletion-policy and four presenter tests.
- **60 Android tests discovered: 59 pass, one existing Exo HDR10 capability skip**
  on the isolated API 35 x86_64 emulator. New coverage includes 15 reconciliation/
  repository integration tests, two real ContentResolver/SAF provider tests and five
  Compose interaction tests. The scanner entry regression, selective cleanup,
  cancellation, changed/forged/replayed previews, shared/nested roots, partial failure,
  sibling preservation, combined files and Home/episode menus are covered.
- Focused follow-ups pass after the equivalent URI KTX adapter cleanup and final
  dialog-fixture adjustment. The final dialog test doubles inherited typography within
  the test app: exact path exists, Cancel is visible/clickable and never confirms deletion.
  Its screenshot was inspected; the message area scrolls and actions remain accessible.
- Formatting, architecture/schema guards, populated schema 6–12 to 13 checks,
  debug/test APK builds, lint and minified release build pass. Lint has **0 errors,
  47 warnings**; preexisting warnings remain. Native packaging verifies ten ARM
  libraries, ARM64 ELF/native ZIP 16 KB alignment and included notices.
- The fixture DocumentsProvider is debug-only, signature-permission protected by
  `MANAGE_DOCUMENTS` and confined to private-cache synthetic files. It has no picker
  intent filter. The merged release manifest contains neither fixture provider/authority.

Logs: `build/deletion-all-android.log`, `build/deletion-final-checks.log`,
`build/deletion-final-focused.log`, `build/deletion-enlarged-dialog.log`,
`build/deletion-final-lint.log`, `build/deletion-release.log`.
Screenshots: `build/deletion-review/delete-file-enlarged-typography.png` and
`build/deletion-review/delete-show-busy.png`. Earlier screenshots with large-text
names did not actually enlarge dialog-window text; only the final typography fixture
is evidence of enlarged rendering. It is not a system font-scale certification.

Automatic approval review blocked an optional ADB system-font setting check, classifying
it as a device-settings mutation. No system setting was changed. The app-only enlarged
typography check above supplies the safe alternative; physical-device font-scale and
provider coverage remain release-checklist items. The customer's phone disconnected
before current-device validation/installation. No customer media was deleted for testing.

Unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`,
100,391,025 bytes. SHA-256:

```text
f8743415a93b2577c2790cc8e67a8df1d0396b7faa90fc573578aab4dbda3ebd
```

This artifact is for validation; existing signing/distribution gates still apply.
