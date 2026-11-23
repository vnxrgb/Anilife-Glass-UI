# Anilumos — Anime, illuminated

<p align="center"><img src="docs/assets/anilumos-mark.svg" width="168" alt="Anilumos A-and-candle logo"></p>

**Anilumos** is a new visual identity and development fork built on the AniLife Android anime client. Its theme-responsive Liquid Glass interface, candle-flame identity, native player, ambient lighting, sources, profiles, lists, and downloads share the underlying AniLife codebase.

## Identity and compatibility

- Display name: **Anilumos**
- Brand: a luminous A with a warm candle flame; the launch background is always pure black
- Android application ID: `io.github.anilifecommunity.anilife` (unchanged to preserve update and data compatibility)
- Internal source namespace: `com.anilili` (retained to avoid breaking source integration)
- The `anilife://` deep-link protocol stays supported for backwards compatibility
- The existing GitHub repository path remains unchanged while the code is rebranded

## Mobile preview APK

The feature-branch preview workflow builds and verifies a single `anilumos-universal.apk` for phone/tablet testing. The direct download appears under the [Anilumos preview release](https://github.com/vnxrgb/Anilife-Glass-UI/releases/tag/anilumos-preview) only after tests, lint, APK assembly and signature verification pass.

This is a debug preview, **not** the stable signed release. To update an existing installation, both APKs need matching application IDs and compatible signing certificates. Debug builds have a separate `.debug` application ID suffix, so they install alongside release builds.

## Build from source

Requires JDK 17, Android SDK 36, Build Tools 35.0.1 and the bundled Gradle 8.13 wrapper:

```sh
./gradlew :app:testMobileDebugUnitTest :app:lintMobileDebug :app:assembleMobileDebug --max-workers=2 --no-configuration-cache
```

Use `gradlew.bat` on Windows. The existing internals and Gradle-generated file names may retain legacy project names until the stable release scripts are migrated.

## Credits and license notices

**Built upon AniLife.** Anilumos is a rebranded derivative of the original [AniLife project](https://github.com/anilifecommunity/android-and-tv). Credit belongs to its maintainers and contributors for the original application. Rebranding does not remove original copyright, notice or license obligations.

**Morphe:** This app uses code from Morphe. To learn more, visit https://morphe.software

Retain [LICENSE](LICENSE), existing copyright notices, `app/src/main/assets/MORPHE_NOTICE.txt`, and other third-party licenses. The repository's current LICENSE contains GPL-3.0 text. Source or assets introduced under earlier terms may have additional restrictions: check provenance and rights before redistributing a derivative APK. The newly drawn Anilumos A-and-flame vector identity is independent of the archived original AniLife artwork.

## Privacy, support and content

Anilumos does not claim rights to anime, third-party sources, artwork or subtitles. The underlying app's behavior, supported devices, privacy features and permissions are unchanged except where explicitly modified and tested. See [PRIVACY.md](PRIVACY.md), [SECURITY.md](SECURITY.md), [SUPPORT.md](SUPPORT.md), and [asset provenance](docs/ASSET-LICENCES.md).
