<p align="center">
  <img src="docs/assets/anilumos-mark.svg" alt="Anilumos A-and-flame logo" width="150">
</p>

<h1 align="center">Anilumos</h1>
<p align="center"><strong>Anime, illuminated — a personal take on the anime-watching experience.</strong></p>
<p align="center">
  <a href="https://github.com/vnxrgb/Anilumos/releases">Releases</a> ·
  <a href="#what-makes-anilumos-different">What's different</a> ·
  <a href="#credits--licensing">Credits & licensing</a>
</p>

---

## Made personal, not community-driven

**Anilumos is a personal customization of [AniLife](https://github.com/anilifecommunity/android-and-tv).** It was shaped around one viewer's preferences: a cleaner interface, softer glass surfaces, an immersive player, responsive ambient lighting and a distinctive identity.

It is **not** presented as a new streaming service, an original anime catalogue or an official AniLife release. Much of the application's functional foundation is inherited from AniLife. The focus of this fork is the experience built *around* those capabilities.

## What makes Anilumos different

| Personal additions and refinements | What changed |
| --- | --- |
| **Liquid Glass interface** | A cohesive, neutral glass treatment across mobile navigation, controls, cards, sheets and settings, with soft highlights rather than heavy grey overlays. |
| **Cinematic video player** | Reworked player controls, rounded actions, more deliberate spacing and a more polished seek bar, while retaining the existing Media3-based playback engine. |
| **Video-reactive ambient glow** | Lighting sampled from the *playing video* extends into the player surroundings, including left and right accents in fullscreen. The ambient experience is designed to coexist with subtitles and controls. |
| **Performance-conscious effects** | Native Compose drawing and lightweight glass shading; ambient-frame sampling is bounded and designed to fall back safely on unsupported or low-memory devices. |
| **Anilumos identity** | A custom luminous **A** with a warm candle flame, theme-responsive color accents and a **pure-black splash screen** regardless of light or dark mode. |
| **Theme-matched launcher appearance** | Accent-specific launcher icons where supported; in-app logo and glow follow the selected theme. |
| **Cleaner viewing flow** | Refined banners, buttons, player/mini-player presentation and navigation, with intrusive first-run tour behavior removed. |
| **Personalized About page** | An explanation of the fork without “community-driven platform” claims, plus clickable original-project attribution and persistent developer credits. |
| **Upstream provider reviews** | A GitHub Actions workflow checks for changes to AniLife's streaming-site providers and prepares **draft review PRs**. Provider changes are never silently merged into the app. |

These are **customizations and integrations built on top of AniLife**, not a claim that every underlying capability was written from scratch. Appearance and playback behavior can vary by Android version, device and provider.

## Features retained from AniLife

Anilumos keeps the original application's broad feature set, including:

- Anime discovery, episode listings and multiple streaming-source options.
- Library, watch history, profiles, tracking and AniList/MyAnimeList integration.
- Playback options, subtitles, episode navigation, downloads and external-player handoff.
- The underlying source resolvers, networking, account handling and Media3 playback infrastructure.

Streaming websites are third-party services; availability, domains, playback support and authorization can change independently of this repository. Anilumos does not host anime video files or claim ownership of their content.

## Install and build

**Stable Android release:** [GitHub Releases](https://github.com/vnxrgb/Anilumos/releases) — use the signed `Anilumos.apk` when the stable release is published.

**Preview builds:** [Anilumos preview](https://github.com/vnxrgb/Anilumos/releases/tag/anilumos-preview) — `anilumos-universal.apk` is a **debug** build for testing, not a signed stable release.

- Stable application ID: `com.anilumos.app`
- Debug application ID: `com.anilumos.app.debug`
- The original AniLife package is separate; installing Anilumos does **not** migrate its data automatically.
- The internal Kotlin namespace `com.anilili` and legacy `anilife://` deep links remain for code/link compatibility. They are not the app's user-facing identity.

For local development, use JDK 17, Android SDK 36, Build Tools 35.0.1 and the included Gradle wrapper:

```bash
./gradlew :app:testMobileDebugUnitTest :app:lintMobileDebug :app:assembleMobileDebug --max-workers=2 --no-configuration-cache
```

The stable GitHub Actions workflow separately validates a **signed universal mobile** APK. A successful CI build does not replace physical-device testing. There is no claim of a separately verified Anilumos TV release.

## Keeping streaming providers up to date

Changes to AniLife's streaming-provider implementations can be reviewed and ported without replacing Anilumos's appearance or player customizations.

The [provider-sync workflow](.github/workflows/provider-sync.yml) checks upstream provider-related source files weekly and prepares a **draft pull request** targeting the development branch when relevant changes are found. A maintainer must inspect any new, changed or removed websites, test the integration and explicitly approve a merge. This is **not** a live provider update service, and new provider functionality generally requires an APK update.

See [upstream provider integration notes](docs/UPSTREAM_PROVIDER_SYNC.md).

## Credits & licensing

Anilumos exists because of the work of the original developers and the open-source projects integrated into the app. **The rebrand does not remove their ownership, notices or credits.**

| Project / developers | Contribution and original source |
| --- | --- |
| **[AniLife Community and contributors](https://github.com/anilifecommunity/android-and-tv)** | **Original application and foundation**: catalogue and streaming-source integration, player, accounts, profiles, downloads, discovery and much of the Android codebase. See the [upstream contributor history](https://github.com/anilifecommunity/android-and-tv/graphs/contributors). |
| **[Morphe developers](https://github.com/MorpheApp/morphe-patches)** | Ambient-light rendering mathematics adapted and integrated with Anilumos's player. [Upstream repository](https://github.com/MorpheApp/morphe-patches) · [integration and provenance notes](docs/MORPHE_AMBIENT_PORT.md). |
| **[AndroidX / Jetpack Compose / Media3](https://github.com/androidx/androidx)** | Compose UI, Android infrastructure and the media playback framework. |
| **[Haze — Chris Banes](https://github.com/chrisbanes/haze)** | Glass and blur-related UI tooling used by the project. Anilumos's lightweight native player-glass styling is separately implemented. |
| **[Coil contributors](https://github.com/coil-kt/coil)** | Android image loading. |
| **[Square's OkHttp and Okio teams](https://github.com/square/okhttp)** | Networking and I/O libraries; [Okio repository](https://github.com/square/okio). |
| **[MaterialKolor contributors](https://github.com/jordond/MaterialKolor)** | Material color utilities and theme-color foundations. |
| **[Nayuki](https://github.com/nayuki/QR-Code-generator)** | QR-code generation library used by the underlying application. |

**Morphe user-facing notice (retained in the app):**

> This app uses code from Morphe. To learn more, visit https://morphe.software

**Licensing and provenance**

- The original **AniLife Source-Available License 1.0** is retained in [LICENSE](LICENSE), including the original copyright notices. It restricts independent redistribution without permission; the Anilumos maintainer states that permission for this derivative distribution has been obtained. This does not relicense AniLife's original code.
- Morphe-derived code carries **GPLv3** obligations and additional notices; consult [MORPHE_NOTICE.txt](app/src/main/assets/MORPHE_NOTICE.txt), the [GPLv3 license copy](app/src/main/assets/GPL-3.0.txt) and the [Morphe integration notes](docs/MORPHE_AMBIENT_PORT.md). The different terms must be respected for each component.
- Other dependencies, fonts, media and assets remain governed by their respective licenses. See [asset provenance](docs/ASSET-LICENCES.md), the Gradle dependency declarations and preserved third-party notices.
- The custom Anilumos A-and-flame identity is distinct from AniLife's original artwork; retained legacy assets keep their original provenance.
- Anilumos is an independent personal fork, **not affiliated with or endorsed by AniLife or Morphe**.

For the full history of work, see the [repository commits](https://github.com/vnxrgb/Anilumos/commits/main) and [original AniLife contributors](https://github.com/anilifecommunity/android-and-tv/graphs/contributors). Credit is intentionally given to upstream creators rather than implying the personal customizations created the entire underlying app.

## Additional information

[Privacy](PRIVACY.md) · [Security](SECURITY.md) · [Support](SUPPORT.md) · [Asset licenses](docs/ASSET-LICENCES.md)

**Built on AniLife. Customized to personal taste.**
