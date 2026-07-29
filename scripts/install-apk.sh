#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk="${1:-$repo_root/android/app/build/outputs/apk/debug/app-debug.apk}"
hdc="${HDC_BIN:-/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc}"

[[ -f "$apk" ]] || { printf 'APK not found: %s\n' "$apk" >&2; exit 2; }

print_hdc_authorization_help() {
  printf '%s\n' 'HarmonyOS device detected, but it is waiting for debug-key authorization.' >&2
  printf '%s\n' 'Unlock the Mate 60 and accept the USB debugging / debugging-key prompt.' >&2
  printf '%s\n' "Then rerun: $0" >&2
}

if command -v adb >/dev/null 2>&1; then
  adb_devices="$(adb devices 2>/dev/null || true)"
  if printf '%s\n' "$adb_devices" | awk '$2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'; then
    adb install -r "$apk"
    exit 0
  fi
fi

if [[ -x "$hdc" ]]; then
  probe_file="$(mktemp -t markbook-hdc.XXXXXX)"
  "$hdc" list targets >"$probe_file" 2>&1 &
  hdc_pid=$!
  for _ in {1..10}; do
    kill -0 "$hdc_pid" 2>/dev/null || break
    sleep 0.1
  done
  if kill -0 "$hdc_pid" 2>/dev/null; then
    kill "$hdc_pid" 2>/dev/null || true
    wait "$hdc_pid" 2>/dev/null || true
    hdc_targets=""
  else
    wait "$hdc_pid" 2>/dev/null || true
    hdc_targets="$(<"$probe_file")"
  fi
  rm -f "$probe_file"
  if [[ "$hdc_targets" == *"connect-key"* ]]; then
    print_hdc_authorization_help
    exit 4
  fi
  if [[ -n "$hdc_targets" ]]; then
    install_file="$(mktemp -t markbook-hdc-install.XXXXXX)"
    if "$hdc" install "$apk" >"$install_file" 2>&1; then
      cat "$install_file"
      rm -f "$install_file"
      exit 0
    fi
    install_output="$(<"$install_file")"
    cat "$install_file" >&2
    rm -f "$install_file"
    if [[ "$install_output" == *"connect-key"* ]]; then
      print_hdc_authorization_help
      exit 4
    fi
    exit 1
  fi
fi

printf 'No Android or HarmonyOS device is available. Connect Mate 60 and enable USB debugging.\n' >&2
exit 3
