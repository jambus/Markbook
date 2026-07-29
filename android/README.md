# Markbook Android APK

This module is the priority client for Mate 60 devices running HarmonyOS 4.
It uses Kotlin, the Android Storage Access Framework, the system camera intent,
and a native WebView editor. It does not require Google Play Services.

Build a debug APK with:

```bash
./gradlew :app:assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. Install it on a
connected device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`
after confirming the Mate 60 appears in `adb devices`.

The first real-device pass must verify Vault URI persistence, daily-note
creation, automatic/manual saving, camera insertion, rectangle crop, four-point
perspective correction, forced-stop recovery, and relative Markdown image links.

Run the platform-neutral fixture check from the repository root with
`./scripts/verify-vault-contract.sh`. The same fixture is used when validating
the native HarmonyOS client and desktop Obsidian interoperability.
