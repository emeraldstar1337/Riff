# Riff

An open-source Android music player built with Kotlin, Jetpack Compose and Material 3 Expressive. Supports English and Russian.

## Features

Local playback, playlists, favourites, editable queues, timed lyrics, per-device equalizer, listening statistics and seasonal/yearly recaps. Four seek bar styles, artwork colours and customizable player layouts.

Artist grouping combines case variants and explicit collaboration credits such as `feat.`, `ft.`, `&`, `+`, `x`, commas, semicolons and slashes. Each credited artist receives the track's listening time; global totals count the listen once. Original metadata and saved listening records are preserved. Grouping can be disabled in Settings → Library. Text-only tags cannot reliably distinguish every band name from a collaboration; spelling aliases are not guessed.

Audio and statistics stay on the device. Optional lyrics searches send track metadata to LRCLIB. Audio files are never uploaded.

Crossfade, visualizer and moving background decoration are off by default. Compatible playback requests hardware offload. System power saving reduces effects automatically. Power consumption and codec support depend on the device.

## Build

Requires JDK 17, Android SDK 36 and Build Tools 35.0.0.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
```

APK output: `app/build/outputs/apk/debug/`. Version 0.9.1, code 10. Minimum Android 8.0. The application ID remains `dev.lad.player` to support updates without losing data.

For signed release builds, keep the signing key outside the repository and provide these Gradle properties or environment variables:

| Gradle property | Environment variable |
| --- | --- |
| `ladSigningStore` | `LAD_SIGNING_STORE` |
| `ladStorePassword` | `LAD_STORE_PASSWORD` |
| `ladKeyAlias` | `LAD_KEY_ALIAS` |
| `ladKeyPassword` | `LAD_KEY_PASSWORD` |

```sh
./gradlew :app:assembleRelease
```

Without signing configuration, release output is unsigned. Updating an installed APK requires its existing signing key.

## Tests

```sh
./gradlew :app:testDebugUnitTest
./scripts/verify-device.sh
python3 scripts/verify-migration.py
```

Device tests use generated audio fixtures and a test emulator. Separate `UpgradeSeedTest` and `UpgradeVerifyTest` validate installation upgrades.

## License

[MIT](LICENSE). Third-party licenses are listed in [NOTICE.md](NOTICE.md).
