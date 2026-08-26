# Repository Guidelines

## Project Structure & Module Organization

Markbook has two mobile clients sharing one Vault contract. The priority client
is an Android APK for HarmonyOS 4 Mate 60 devices; its Kotlin/Gradle sources
live under `android/`. The existing `entry/` module is the
HarmonyOS 5/6 Stage HAP. App resources and metadata live in `AppScope/`, and
ArkTS code lives under
`entry/src/main/ets/`: UI pages are in `pages/`, data objects in `model/`, and
filesystem or sync logic in `services/`. Resources are grouped under
`entry/src/main/resources/base/`. Hypium tests live in
`entry/src/ohosTest/ets/test/`.

Keep Markdown files and image attachments as the source of truth. Do not add a
database dependency for data that can be derived from the notebook directory.
Both clients must preserve identical Vault paths and relative Markdown links.
`docs/contracts/vault-contract.md` is the sole persisted-data contract for file
layout, attachment paths, recovery, trash, and conflicts.

All project documentation lives under `docs/`: `constitution.md` and `product/`
hold the governing rules and roadmap, `contracts/` holds cross-client data
contracts, `design/` holds the active experience baseline, and `specs/` holds
numbered work. Specs `001`–`003` are platform-neutral capability domains, `004`
tracks cross-client interoperability, `005`–`006` track Android and HAP platform
delivery, `007` is the completed design-knowledge-base setup record, and `008`
tracks Android implementation hardening. Update the affected `spec.md`,
`plan.md`, and `tasks.md` before changing scope.

Keep task ownership singular. Capability specs do not track platform UI, build,
or API completion. Android implementation tasks belong in `005`, HAP tasks in
`006`, shared fixtures and migration checks in `004`, and traceable Android
defects in `008`. A capability may require both clients to pass final acceptance
without copying their implementation tasks into multiple specs.

## Design Knowledge Base

Before implementing or reviewing any user-visible behavior, start with
`docs/design/README.md` and read the relevant design file. `design_principles.md`
and `components.md` apply to all
screens; `note_editor.md` applies to Markdown editing; `capture_flow.md` applies
to camera, image processing, attachment storage, and insertion. Use
`review_checklist.md` before handing off a UI or interaction change. When a
change alters a documented flow or component behavior, update the design
document first, then the affected numbered `spec.md`, `plan.md`, and `tasks.md`.
`docs/contracts/vault-contract.md` remains authoritative for persisted data
behavior.

## Version and Release Management

`docs/RELEASE_NOTES.md` records version scope, validation status, and iteration
history. Before producing a candidate build, update the release notes, affected
platform spec and acceptance record, Android `versionName`, and monotonically
increasing `versionCode`. Do not mark a development baseline as released before
its required Mate real-device checks are recorded.

## Build, Test, and Development Commands

Prioritize the Android APK defined by spec `005`; use the existing Gradle Wrapper
under `android/` with Java 11 or newer. Use DevEco Studio with a matching
installed SDK for the HarmonyOS 5/6 HAP.

- `ohpm install` installs project dependencies.
- `./scripts/build-hap.sh` builds the entry HAP with the DevEco-provided Node,
  Hvigor, and SDK paths.
- Build and run the Hypium test HAP from DevEco Studio on a configured device;
  do not claim device coverage from compilation alone.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` from `android/` runs the
  Android unit tests and builds the debug APK.
- `./scripts/install-apk.sh` installs the generated APK using an available
  `adb` or `hdc` connection after the device authorizes the debugging key.

DevEco Studio supplies the Node and Hvigor runtimes used by `build-hap.sh`. Do
not commit `.hvigor/`, `oh_modules/`, signed HAPs, or local SDK paths.
For CLI builds, `DEVECO_SDK_HOME` must point to an SDK root containing a
version directory such as `HarmonyOS-6.0.1`; `.../sdk/default` alone is not
the expected root. Use `--no-daemon` if the local Hvigor cache has a stale lock.
Use `docs/specs/005-harmonyos4-android-apk/quickstart.md` for Android build and
Mate 60 acceptance, and `docs/specs/006-harmonyos5-6-native-hap/quickstart.md`
for HAP acceptance.

## Coding Style & Naming Conventions

Use two-space indentation in ArkTS and JSON5. Name components and classes in
`PascalCase`, methods and fields in `camelCase`, and tests as
`Behavior.test.ets`. Prefer explicit ArkTS types at API and persistence
boundaries. UI code belongs in pages; direct file or network access belongs in
services. Keep user-facing strings in resources when they are reused.

## Testing Guidelines

Use JUnit for Android logic and Hypium (`describe`, `it`, and `expect`) for
ArkTS logic. Run shared fixtures against both clients. Camera changes require
real-device checks on HarmonyOS 4 APK and HarmonyOS 5/6 HAP: capture a photo,
restart, and confirm the image and relative Markdown link remain valid.

## Commit & Pull Request Guidelines

History uses short English subjects such as `Logic implement` and
`Init the spec-kit structure along with doc folder`. Prefer a clear imperative
subject such as `Add local attachment storage`, and keep commits focused. Pull
requests must describe user-visible behavior, list build/test results, link the
relevant issue, and include Mate phone screenshots for UI changes.

## Security & Configuration

Never commit Google Drive tokens, NAS credentials, personal notebooks, signing
keys, or `local.properties`. Use the platform secure store where available;
otherwise keep credentials only in process memory. Request only required
permissions, and redact remote URLs and secrets from logs.
