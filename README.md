<div align="center">
  <img src="docs/assets/anilumos-mark.svg" width="130" alt="Anilumos logo">

  # Anilumos

  My personal fork of [AniLife](https://github.com/anilifecommunity/android-and-tv), with a redesigned interface and video player.

  [Download APK](https://github.com/vnxrgb/Anilumos/releases/tag/v1.0.0) · [Releases](https://github.com/vnxrgb/Anilumos/releases)
</div>

## About

I liked what AniLife could do, but wanted the app to look and feel a little different. So I forked it and started changing the things I use every day: the home screen, navigation, buttons, themes, and especially the video player.

Anilumos isn't a new streaming service or a project built from scratch. The original app does the heavy lifting. This fork is mostly about making the experience match my own preferences.

## What I changed

- **Liquid Glass UI.** Reworked the navigation bar, banners, cards, buttons and settings with a more consistent glass style. I kept the glass neutral so it works with different themes instead of tinting everything grey.
- **Redesigned video player.** New rounded playback controls, better spacing, a cleaner seek bar and updated fullscreen controls. I also reworked the mini-player so it doesn't feel like a completely different interface.
- **Ambient lighting.** The glow around the player reacts to colors in the actual video. This uses ambient-light code adapted from [Morphe](https://github.com/MorpheApp/morphe-patches), integrated with AniLife's existing player.
- **New branding.** The Anilumos logo is an A with a candle flame. The A follows the selected theme color, while the splash screen always stays black.
- **Theme-matched icons.** Launcher icon variations follow supported accent-color presets.
- **Small UI fixes everywhere.** Cleaner home banners, more balanced buttons, less visual clutter and changes to how the player and other screens fit together.

The goal wasn't to cram in more features. I wanted the features already there to feel better to use.

### The video player

This was the part I spent the most time customizing.

The player still uses AniLife's Media3 playback setup, so the existing source selection, subtitles, gestures, episode navigation and external-player support are kept. The work in Anilumos is mainly in the controls and presentation: rounded buttons, less intrusive overlays, a properly aligned seek bar, a cleaner fullscreen layout and a more consistent mini-player.

The ambient effect is separate from the video itself. It samples small frames to produce light around the picture rather than blurring the playing video or its subtitles. Its availability depends on the device and playback surface.

## What comes from AniLife

Most of the underlying functionality is AniLife's work, including anime discovery, streaming-source integrations, watch history, profiles, downloads, library features and AniList/MyAnimeList syncing.

I've also added a [workflow to check upstream streaming-provider changes](docs/UPSTREAM_PROVIDER_SYNC.md). It proposes changes in a draft pull request for review; it doesn't automatically add sources or update installed APKs.

## Installation

Get the signed **Anilumos.apk** from the [v1.0.0 release](https://github.com/vnxrgb/Anilumos/releases/tag/v1.0.0).

- Stable package: `com.anilumos.app`
- [Preview APKs](https://github.com/vnxrgb/Anilumos/releases/tag/anilumos-preview) use `com.anilumos.app.debug` and install separately.
- Anilumos also installs separately from AniLife. Existing app data won't migrate automatically.
- This release is for Android phones/tablets; I haven't published a separately verified TV build.

## Build from source

You'll need JDK 17 and the Android SDK.

```bash
./gradlew :app:testMobileDebugUnitTest :app:lintMobileDebug :app:assembleMobileDebug
```

## Credits and licenses

I didn't make AniLife, and I don't want this fork to look like I did. Thanks to everyone whose work made it possible:

- **[AniLife Community](https://github.com/anilifecommunity/android-and-tv)** and [its contributors](https://github.com/anilifecommunity/android-and-tv/graphs/contributors) — the original Android app, playback, streaming sources and nearly all the underlying features.
- **[Morphe](https://github.com/MorpheApp/morphe-patches)** — ambient-light rendering code adapted for the player. See the [integration notes](docs/MORPHE_AMBIENT_PORT.md) and [required notice](app/src/main/assets/MORPHE_NOTICE.txt).
- **[AndroidX / Jetpack Compose / Media3](https://github.com/androidx/androidx)** — Android UI and playback foundations.
- **[Haze by Chris Banes](https://github.com/chrisbanes/haze)** — blur and glass UI tooling.
- **[Coil](https://github.com/coil-kt/coil)** — image loading.
- **[OkHttp](https://github.com/square/okhttp)** and **[Okio](https://github.com/square/okio)** — networking and I/O.
- **[MaterialKolor](https://github.com/jordond/MaterialKolor)** — theme color utilities.
- **[Nayuki's QR Code generator](https://github.com/nayuki/QR-Code-generator)** — QR-code generation.

Other dependencies and assets retain their respective notices. You can find the [asset credits here](docs/ASSET-LICENCES.md).

**Morphe notice:** This app uses code from Morphe. To learn more, visit https://morphe.software

The original [AniLife Source-Available License 1.0](LICENSE) is preserved. It requires permission for independently distributing modified versions; I have stated that I obtained that permission for this fork. That doesn't change the license of AniLife's original code. Morphe-derived portions have separate [GPLv3](app/src/main/assets/GPL-3.0.txt) requirements and [notices](app/src/main/assets/MORPHE_NOTICE.txt).

Anilumos is an independent personal fork, not an official release or endorsement by AniLife or Morphe.

---

[Privacy](PRIVACY.md) · [Security](SECURITY.md) · [Support](SUPPORT.md)
