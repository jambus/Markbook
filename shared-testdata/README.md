# Shared Vault Fixtures

This directory is a small, platform-neutral Vault fixture. The Android APK,
HarmonyOS HAP, and desktop Obsidian checks must preserve its UTF-8 Markdown,
folder names, photo and video relative attachment links (including the angle-bracket
form for paths with spaces), tags, and wikilinks.

The fixture is test input, not application state. Clients must not add a
database, index, or platform-specific metadata file to it.
