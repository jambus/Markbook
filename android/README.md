# Markbook Android APK

This module is the priority client for Mate 60 devices running HarmonyOS 4.
It uses Kotlin, the Android Storage Access Framework, the system camera intent,
and a native WebView editor. The current experimental Google Drive account flow
uses Google Play Services; local Vault, editing, and camera features do not.
Google Drive must not be considered available on the target device until the
Mate 60 HarmonyOS 4 environment passes the documented authorization test.

Use Java 11 or newer, then build and test a debug APK with:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. Install it on a
connected device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`
after confirming the Mate 60 appears in `adb devices`.

The first real-device pass must verify Vault URI persistence, daily-note
creation, automatic/manual saving, camera insertion, rectangle crop, four-point
perspective correction, forced-stop recovery, and relative Markdown image links.
Follow `docs/specs/005-harmonyos4-android-apk/quickstart.md` from the repository
root and record the APK version, device, system version, and exceptions.

Run the platform-neutral fixture check from the repository root with
`./scripts/verify-vault-contract.sh`. The same fixture is used when validating
the native HarmonyOS client and desktop Obsidian interoperability.
