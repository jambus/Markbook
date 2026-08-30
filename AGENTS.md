# Repository Guidelines

## Daily Vibe Coding & Model Routing

Use **Terra with xhigh reasoning effort** by default. Terra Main owns each task
end to end: establish context, make the smallest safe change, validate it, and
report the result. Do not delegate routine work merely to parallelize it.

Route work by risk and reasoning depth:

- **Routine request → Terra Main:** localized UI or content changes,
  straightforward implementation, focused tests, documentation, and small
  refactors with clear acceptance criteria.
- **Complex architecture → Sol:** cross-module/client design, Vault-contract
  implications, migrations, compatibility, security, or multiple plausible
  approaches. Provide files, constraints, alternatives, and the decision needed.
- **Hard bug → Sol:** an unresolved reproducible, concurrency, lifecycle,
  persistence, flaky, or cross-platform failure. Include repro, expected vs.
  observed behavior, attempted fixes, logs, and relevant code.
- **Completed feature → Sol review:** review correctness, regression risk,
  contract compliance, test coverage, and user-visible behavior; resolve or
  explicitly defer findings.
- **Mechanical work → Luna:** repetitive, low-risk, specified work such as
  discovery, formatting, boilerplate, test data, renames, and inventories. Give
  exact scope and acceptance checks; Terra verifies the result.

Escalation does not transfer accountability: Terra integrates and validates the
outcome. Do not delegate secret handling, destructive operations, or ambiguous
scope decisions without explicit user authorization. Sol review supplements the
required design documentation and checklist for user-visible changes.

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

Documentation lives under `docs/`: `constitution.md` and `product/` govern,
`contracts/` defines cross-client data, `design/` is the experience baseline, and
`specs/` tracks work. Update affected `spec.md`, `plan.md`, and `tasks.md` before
changing scope. Keep ownership singular: use `004` for shared fixtures and
migration checks, `005` for Android delivery, `006` for HAP delivery, and `008`
for traceable Android defects; do not duplicate platform implementation work in
capability specs.

## Design Knowledge Base

Before implementing or reviewing user-visible behavior, read
`docs/design/README.md` and the relevant design file: `design_principles.md` and
`components.md` apply to all screens; `note_editor.md` covers Markdown editing;
`capture_flow.md` covers camera and attachments. Use `review_checklist.md`
before handing off UI or interaction changes. Update a changed design document
before its affected numbered `spec.md`, `plan.md`, and `tasks.md`.

## Version and Release Management

`docs/RELEASE_NOTES.md` records version scope, validation status, and iteration
history. Before producing a candidate build, update the release notes, affected
platform spec and acceptance record, Android `versionName`, and monotonically
increasing `versionCode`. Do not mark a development baseline as released before
its required Mate real-device checks are recorded.

## Multi-Agent Development Workflow

Use sub-agents for non-trivial implementation work. The primary agent acts as
the coordinator, controls handoffs between roles, and owns the final result.

### Architecture and Review Agent

- Model: `gpt-5.6-sol`.
- Reasoning effort: `high` unless the task clearly requires a different level.
- Inspect the relevant design documents, Vault contract, specs, tests, and
  existing implementation before proposing changes.
- Define architectural boundaries, compatibility risks, acceptance criteria,
  implementation constraints, and required tests.
- Review completed changes for correctness, regressions, data safety,
  concurrency issues, and consistency with the shared Vault contract.
- Remain read-only unless the user explicitly asks this role to edit files.
- Cite concrete files and code locations in architecture and review findings.
- Reuse the same Sol agent for the final review when possible so that the
  original architectural context is preserved.

### Development Agent

- Model: `gpt-5.6-terra`.
- Reasoning effort: `high` unless the task clearly requires a different level.
- Implement the approved in-scope changes and own production-file edits during
  the implementation phase.
- Update required design, contract, spec, plan, task, acceptance, and release
  documentation in the order defined by this repository guide.
- Add or update automated tests together with the implementation.
- Report changed files, implementation decisions, and known limitations before
  handing the work to the test and review roles.

### Test Agent

- Model: `gpt-5.6-terra`.
- Reasoning effort: `high` unless the task clearly requires a different level.
- Act independently from the development agent and inspect the actual diff
  before selecting validation coverage.
- Run the relevant Android, HAP, and shared-contract checks and add focused
  regression tests when the assigned scope permits test-file edits.
- Verify acceptance criteria, failure paths, persistence behavior, and version
  metadata; distinguish compilation, automated tests, emulator checks, and real
  device evidence.
- Do not modify production code. Return reproducible failures to the development
  agent and retest after fixes.
- Report exact commands, results, warnings, skipped checks, and remaining device
  validation.

### Required Role Workflow

For feature implementation, persistence or concurrency changes, cross-client
contract changes, or changes spanning multiple production files, the primary
agent MUST use the following workflow:

1. Spawn a `gpt-5.6-sol` architecture and review agent for read-only analysis.
2. Evaluate its proposal and define the accepted implementation scope.
3. Spawn a `gpt-5.6-terra` development agent to implement that scope and add
   implementation-level tests.
4. Spawn a separate `gpt-5.6-terra` test agent to inspect and validate the
   resulting change independently.
5. Send the implementation and test evidence to the original Sol agent for code
   review.
6. Send actionable test or review findings to the development agent for fixes.
7. Ask the test agent to rerun affected checks after fixes, then have the primary
   agent inspect the final diff and evidence before reporting completion.

The primary agent may skip this workflow for simple questions, read-only status
checks, typo fixes, and isolated documentation-only edits. It should state why
the workflow is skipped when the user could reasonably expect implementation
work.

### Multi-Agent Coordination Rules

- Do not let multiple agents edit the same files concurrently.
- Architecture and review may run alongside independent read-only research, but
  editing phases must have explicit file ownership.
- All agents share the same workspace. Inspect the working tree before editing
  and preserve existing user changes.
- Keep task scope and ownership singular. Do not duplicate task records merely
  because multiple agents participated.
- Do not stage, commit, push, install builds, or perform destructive actions
  unless the user requested them.
- A review is not complete until every finding is fixed, rejected with concrete
  evidence, or recorded as an accepted risk.
- The primary agent remains responsible for final validation and must inspect
  commands, outputs, diffs, and unresolved warnings rather than treating an
  agent summary as proof.

## Build, Test, and Development Commands

Prioritize the Android APK defined by spec `005`; use the existing Gradle Wrapper
under `android/` with Java 11 or newer. Use DevEco Studio with a matching
installed SDK for the HarmonyOS 5/6 HAP.

- `ohpm install`; `./scripts/build-hap.sh` builds the entry HAP with
  DevEco-provided Node, Hvigor, and SDK paths.
- Build and run the Hypium test HAP from DevEco Studio on a configured device;
  compilation alone is not device coverage.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` from `android/` runs the
  Android unit tests and builds the debug APK.
- `./scripts/install-apk.sh` installs the generated APK using an available
  `adb` or `hdc` connection after the device authorizes the debugging key.

DevEco Studio supplies Node and Hvigor. Do not commit `.hvigor/`, `oh_modules/`,
signed HAPs, or local SDK paths. For CLI builds, `DEVECO_SDK_HOME` must be an
SDK root containing a version directory (for example `HarmonyOS-6.0.1`), not
only `.../sdk/default`; use `--no-daemon` for a stale Hvigor lock. Follow
`docs/specs/005-harmonyos4-android-apk/quickstart.md` for Android and Mate 60
acceptance, and `docs/specs/006-harmonyos5-6-native-hap/quickstart.md` for HAP.

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

Use short, imperative English commit subjects (for example, `Add local
attachment storage`) and focused commits. Pull requests must describe
user-visible behavior, list build/test results, link the relevant issue, and
include Mate phone screenshots for UI changes.

## Security & Configuration

Never commit Google Drive tokens, NAS credentials, personal notebooks, signing
keys, or `local.properties`. Use the platform secure store where available;
otherwise keep credentials only in process memory. Request only required
permissions, and redact remote URLs and secrets from logs.
