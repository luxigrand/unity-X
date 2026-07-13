# Nexus Neuro Android

Kotlin + Jetpack Compose APK for the Nexus Neuro Phase 1 MVP (mock EEG / pulse, REM detection, local stim stub).

## Build APK

```bash
cd android
.\gradlew.bat assembleRelease
```

Debug APK (easier sideload, debug-signed):

```bash
.\gradlew.bat assembleDebug
```

## Output paths

| Build | APK |
|-------|-----|
| Release | `android/app/build/outputs/apk/release/app-release.apk` (debug-keystore signed for sideload) |
| Debug | `android/app/build/outputs/apk/debug/app-debug.apk` |

## Install on phone

1. Copy the APK to the device.
2. Enable **Install unknown apps** for your file manager.
3. Open the APK and install.
4. Launch **Nexus Neuro**.

## Login (same as desktop)

| Role | Kimlik | Şifre |
|------|--------|-------|
| Administrator (Manual) | `57019027696` | `15041212.k` |
| Personel (Auto / Co-Pilot) | `5433307329` | `1599511324` |

## Notes

- USB Arduino serial and TTS are stubs in v1 (stim state is local).
- `local.properties` points at the machine Android SDK; regenerate if you move machines.
