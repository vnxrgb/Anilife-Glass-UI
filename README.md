<div align="center">
  <img src="docs/assets/anilumos-mark.svg" alt="Anilumos logo" width="130">

  # Anilumos

  **An Android anime client with a refined Liquid Glass interface and a redesigned video player.**

  A personal fork of [AniLife](https://github.com/anilifecommunity/android-and-tv).

  [Download v1.0.0](https://github.com/vnxrgb/Anilumos/releases/tag/v1.0.0) · [All releases](https://github.com/vnxrgb/Anilumos/releases) · [Report an issue](https://github.com/vnxrgb/Anilumos/issues)
</div>

## Overview

Anilumos builds on AniLife's existing anime discovery, streaming and library features, with a focus on visual consistency, comfortable navigation and a more immersive playback experience. It retains the original application's core functionality while introducing a custom interface, player refinements and independent branding.

## Features

### Liquid Glass interface

- Neutral translucent surfaces across navigation, buttons, banners, cards and menus.
- A cleaner home screen and more consistent spacing throughout the app.
- Theme-aware styling that works across supported color schemes.
- Custom Anilumos logo, dark launch screen and theme-matched launcher icons.

### Redesigned video player

- Refined fullscreen playback controls with rounded buttons and a cleaner layout.
- Reworked seek bar, player overlays and mini-player presentation.
- Ambient lighting that responds to colors in the video, using an adaptation of [Morphe's](https://github.com/MorpheApp/morphe-patches) ambient-light implementation.
- Existing playback functionality, including subtitles, source selection, gestures and episode controls, remains available.

Ambient lighting availability and performance can vary by device and playback surface.

### Anime and library features

Built on the original AniLife application:

- Anime browsing, search and episode discovery.
- Multiple streaming sources and playback options.
- Watch history, library management and profiles.
- Downloads and external-player support.
- AniList and MyAnimeList integrations.

### Upstream provider update tracking

Anilumos includes a **streaming-provider update workflow** to help keep supported sources aligned with changes from the original AniLife project.

- Tracks upstream changes affecting streaming providers, extractors and resolvers.
- Makes relevant provider updates available for review.
- Keeps provider maintenance separate from Anilumos's custom interface and video player.
- Requires review before updates are included in the app.

Provider updates are not delivered directly to installed APKs; a new app release may be needed. [Provider update details](docs/UPSTREAM_PROVIDER_SYNC.md).

## Download

The current stable release is **[Anilumos v1.0.0](https://github.com/vnxrgb/Anilumos/releases/tag/v1.0.0)**, distributed as a signed universal Android APK.

- **Stable:** `com.anilumos.app`
- **Preview:** `com.anilumos.app.debug` — [preview builds](https://github.com/vnxrgb/Anilumos/releases/tag/anilumos-preview)

Stable, preview and original AniLife installations use separate app data. This release is intended for Android phones and tablets; a separate Anilumos TV release has not been verified.

## Build

Requires JDK 17, Android SDK 36 and the included Gradle wrapper.

```bash
./gradlew :app:testMobileDebugUnitTest :app:lintMobileDebug :app:assembleMobileDebug
```

## Credits and licensing

Anilumos is an independent fork. The original application's core features and substantial portions of its source code were developed by the **[AniLife Community and contributors](https://github.com/anilifecommunity/android-and-tv/graphs/contributors)**. All original attribution and applicable license requirements remain in effect.

Special thanks to:

- **[AniLife](https://github.com/anilifecommunity/android-and-tv)** — original Android application, streaming providers and playback infrastructure.
- **[Morphe](https://github.com/MorpheApp/morphe-patches)** — adapted ambient-light rendering code. [Implementation notes](docs/MORPHE_AMBIENT_PORT.md).
- **[AndroidX / Jetpack Compose / Media3](https://github.com/androidx/androidx)** — Android interface and media playback.
- **[Haze](https://github.com/chrisbanes/haze)** — glass and blur UI components.
- **[Coil](https://github.com/coil-kt/coil)** — image loading.
- **[OkHttp](https://github.com/square/okhttp)** and **[Okio](https://github.com/square/okio)** — networking and I/O.
- **[MaterialKolor](https://github.com/jordond/MaterialKolor)** — theme color utilities.
- **[Nayuki's QR Code generator](https://github.com/nayuki/QR-Code-generator)** — QR-code support.

**Required Morphe notice:**

> This app uses code from Morphe. To learn more, visit https://morphe.software

The [AniLife Source-Available License 1.0](LICENSE) is retained. Independent redistribution of modified AniLife code requires permission; the Anilumos maintainer has stated that permission was obtained. Morphe-derived portions have separate [GPLv3](app/src/main/assets/GPL-3.0.txt) obligations and a [required notice](app/src/main/assets/MORPHE_NOTICE.txt). Additional asset provenance is documented in [ASSET-LICENCES.md](docs/ASSET-LICENCES.md).

Anilumos is not affiliated with or endorsed by AniLife or Morphe. The app does not host anime streams or own third-party content.

---

[Privacy](PRIVACY.md) · [Security](SECURITY.md) · [Support](SUPPORT.md)
