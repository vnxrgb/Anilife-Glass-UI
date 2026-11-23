# Anilumos — Anime, illuminated

<p align="center"><img src="docs/assets/anilumos-mark.svg" width="168" alt="Anilumos A-and-candle logo"></p>

**Anilumos** is a new visual identity and development fork built on the AniLife Android anime client. Its theme-responsive Liquid Glass interface, candle-flame identity, native player, ambient lighting, sources, profiles, lists, and downloads share the underlying AniLife codebase.

## Identity and compatibility

- Display name: **Anilumos**
- Brand: a luminous A with a warm candle flame; the launch background is always pure black
- Android application ID: `com.anilumos.app` (new, independent from AniLife; existing app data is not migrated automatically)
- Internal source namespace: `com.anilili` (retained to avoid breaking source integration)
- The legacy `anilife://` deep-link protocol remains supported for link compatibility, not as app branding
- GitHub repository: `https://github.com/vnxrgb/Anilumos`

## Stable Android APK

The first stable release uses the independent application ID `com.anilumos.app` and publishes a signed **universal mobile** APK as `Anilumos.apk` under [GitHub Releases](https://github.com/vnxrgb/Anilumos/releases). The debug preview (`com.anilumos.app.debug`) is a separate installation; its data does not transfer automatically to the signed release.

## Mobile preview APK

The feature-branch preview workflow builds and verifies a single `anilumos-universal.apk` for phone/tablet testing. The direct download appears under the [Anilumos preview release](https://github.com/vnxrgb/Anilumos/releases/tag/anilumos-preview) only after tests, lint, APK assembly and signature verification pass.

This is a debug preview, **not** the stable signed release. Anilumos has a different application ID from AniLife, so both can be installed together and have separate data. Debug builds additionally use a `.debug` suffix and install alongside Anilumos release builds.

## Build from source

Requires JDK 17, Android SDK 36, Build Tools 35.0.1 and the bundled Gradle 8.13 wrapper:

```sh
./gradlew :app:testMobileDebugUnitTest :app:lintMobileDebug :app:assembleMobileDebug --max-workers=2 --no-configuration-cache
```

Use `gradlew.bat` on Windows. The existing internals and Gradle-generated file names may retain legacy project names until the stable release scripts are migrated.

## Credits and license notices

**Built upon AniLife.** Anilumos is a rebranded derivative of the original [AniLife project](https://github.com/anilifecommunity/android-and-tv). Credit belongs to its maintainers and contributors for the original application. Rebranding does not remove original copyright, notice or license obligations.

**Morphe:** This app uses code from Morphe. To learn more, visit https://morphe.software

Retain [LICENSE](LICENSE), existing copyright notices, `app/src/main/assets/MORPHE_NOTICE.txt`, and other third-party licenses. The original AniLife source-available license is retained in LICENSE. Independent redistribution requires authorization from its maintainers; the project owner has stated that authorization was obtained. This statement does not relicense original AniLife work. Third-party code and assets remain subject to their separate terms. The newly drawn Anilumos A-and-flame vector identity is independent of the archived original AniLife artwork.

## Privacy, support and content

Anilumos does not claim rights to anime, third-party sources, artwork or subtitles. The underlying app's behavior, supported devices, privacy features and permissions are unchanged except where explicitly modified and tested. See [PRIVACY.md](PRIVACY.md), [SECURITY.md](SECURITY.md), [SUPPORT.md](SUPPORT.md), and [asset provenance](docs/ASSET-LICENCES.md).
