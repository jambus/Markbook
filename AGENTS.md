# Repository Guidelines

## Project Structure & Module Organization

Markbook has two planned mobile clients sharing one Vault contract. The
priority client is an Android APK for HarmonyOS 4 Mate 60 devices; place its
Kotlin/Gradle sources under `android/`. The existing `entry/` module is the
HarmonyOS 5/6 Stage HAP. App resources and metadata live in `AppScope/`, and
ArkTS code lives under
`entry/src/main/ets/`: UI pages are in `pages/`, data objects in `model/`, and
filesystem or sync logic in `services/`. Resources are grouped under
`entry/src/main/resources/base/`. Hypium tests live in
`entry/src/ohosTest/ets/test/`.

Keep Markdown files and image attachments as the source of truth. Do not add a
database dependency for data that can be derived from the notebook directory.
Both clients must preserve identical Vault paths and relative Markdown links.
Numbered specs live in `doc/specs/`; update `spec.md`, `plan.md`, and `tasks.md`
before changing scope.

## Build, Test, and Development Commands

Prioritize the Android APK defined by spec `005`; use its Gradle Wrapper after
the `android/` project is created. Use DevEco Studio with a matching installed
SDK for the HarmonyOS 5/6 HAP.

- `ohpm install` installs project dependencies.
- `hvigorw assembleHap` builds the entry HAP.
- `hvigorw assembleHap -p buildMode=test -p module=entry@ohosTest` builds the
  Hypium test HAP; install and run it on a configured device.
- `./gradlew :app:assembleDebug` from `android/` will build the Android debug
  APK after that project is scaffolded.

The wrapper is supplied by DevEco Studio when the project is synchronized. Do
not commit `.hvigor/`, `oh_modules/`, signed HAPs, or local SDK paths.
For CLI builds, `DEVECO_SDK_HOME` must point to an SDK root containing a
version directory such as `HarmonyOS-6.0.1`; `.../sdk/default` alone is not
the expected root. Use `--no-daemon` if the local Hvigor cache has a stale lock.

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
