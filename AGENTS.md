# Repository Guidelines

## Project Structure & Module Organization

Markbook is a HarmonyOS Stage-model application. App resources and metadata
live in `AppScope/`. The `entry/` HAP contains ArkTS code under
`entry/src/main/ets/`: UI pages are in `pages/`, data objects in `model/`, and
filesystem or sync logic in `services/`. Resources are grouped under
`entry/src/main/resources/base/`. Hypium tests live in
`entry/src/ohosTest/ets/test/`.

Keep Markdown files and image attachments as the source of truth. Do not add a
database dependency for data that can be derived from the notebook directory.
Numbered specs live in `doc/specs/`; update `spec.md`, `plan.md`, and `tasks.md`
before changing scope.

## Build, Test, and Development Commands

Use DevEco Studio 5.0.0 Release or newer with an installed SDK at or above
HarmonyOS 6.0.1/API 21. The app targets HarmonyOS 5/API 12 and keeps
`compatibleSdkVersion` at HarmonyOS 4/API 10.

- `ohpm install` installs project dependencies.
- `hvigorw assembleHap` builds the entry HAP.
- `hvigorw assembleHap -p buildMode=test -p module=entry@ohosTest` builds the
  Hypium test HAP; install and run it on a configured device.

The wrapper is supplied by DevEco Studio when the project is synchronized. Do
not commit `.hvigor/`, `oh_modules/`, signed HAPs, or local SDK paths.

## Coding Style & Naming Conventions

Use two-space indentation in ArkTS and JSON5. Name components and classes in
`PascalCase`, methods and fields in `camelCase`, and tests as
`Behavior.test.ets`. Prefer explicit ArkTS types at API and persistence
boundaries. UI code belongs in pages; direct file or network access belongs in
services. Keep user-facing strings in resources when they are reused.

## Testing Guidelines

Use Hypium (`describe`, `it`, and `expect`) for unit tests. Cover Markdown
serialization, filesystem behavior, attachment paths, and sync conflict rules.
Run tests before each pull request. Camera changes also require a real-device
check: capture a photo, restart the app, and confirm both the image and relative
Markdown link remain valid.

## Commit & Pull Request Guidelines

History uses short English subjects such as `Logic implement` and
`Init the spec-kit structure along with doc folder`. Prefer a clear imperative
subject such as `Add local attachment storage`, and keep commits focused. Pull
requests must describe user-visible behavior, list build/test results, link the
relevant issue, and include phone and tablet screenshots for UI changes.

## Security & Configuration

Never commit OneDrive tokens, S3 credentials, personal notebooks, signing keys,
or `local.properties`. Use HarmonyOS secure storage where the compatible API
allows it; otherwise keep credentials only in process memory. Request only
required permissions, and redact remote URLs and secrets from logs.
