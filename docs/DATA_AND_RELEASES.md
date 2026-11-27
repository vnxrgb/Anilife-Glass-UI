# Data, integrations and releases

Part of the [AniLumos technical documentation](TECHNICAL_GUIDE.md).

**Source baseline:** [`6ebaead`](https://github.com/vnxrgb/Anilumos/tree/6ebaead4093a31b76a40bef0718827ca8b48167b) on `main`. This describes inspected source, not a new device-test result. Original additions are licensed under [MIT](../LICENSE-ANILUMOS) only to the extent owned by vnxrgb; inherited and third-party components retain their terms. See [COPYRIGHT.md](../COPYRIGHT.md).

## Data layer

AppGraph initializes the shared HTTP client, serialization, metadata cache and repository dependencies. MiruroRepository orchestrates metadata and existing playback sources. The bounded AppCache uses memory plus disk files for re-derivable public metadata, freshness/expiry checks, optional stale handling and batch writes. This is distinct from persistent viewing history and user-authored data. EpisodeMetadata supports merging primary and fallback rows and alignment to seasons; source responses cannot be assumed to map directly to a particular season.

**Code:** [AppGraph.kt](../app/src/main/java/com/anilili/data/AppGraph.kt) · [MiruroRepository.kt](../app/src/main/java/com/anilili/data/MiruroRepository.kt) · [AppCache.kt](../app/src/main/java/com/anilili/data/cache/AppCache.kt) · [EpisodeMetadata.kt](../app/src/main/java/com/anilili/data/model/EpisodeMetadata.kt).

## Community metadata integrations

| Integration | Role and limits |
| --- | --- |
| AniList / AniLife metadata infrastructure | Catalogue identity, details and existing recommendation/relationship foundations. |
| AniSkip | Existing intro/outro timing foundation; uses MAL mapping. |
| TheIntroDB | Additional timing lookups; diagnostic flow requires verified TVDB episode mapping. |
| IntroDB | Additional segment timestamps, subject to mapping and coverage. |
| Tenrai | Community recommendations and episode flags; parser maps explicit recap/filler flags only; false flags do not establish canon or mixed status. Recommendation entries use MAL IDs, exclude the seed, rank by votes and deduplicate, up to 24 IDs in the inspected parser. |
| FillerLists / AniFillerPedia | Explicit episode classifications; data terms and attribution remain applicable. |
| Curated watch-order data | Community routes and optional steps; see [feature guide](FEATURE_IMPLEMENTATIONS.md#franchise-watch-order). |

**Code:** [AniSkipClient.kt](../app/src/main/java/com/anilili/data/remote/AniSkipClient.kt) · [TenraiParsing.kt](../app/src/main/java/com/anilili/data/remote/TenraiParsing.kt) · [MetadataDiagnostics.kt](../app/src/main/java/com/anilili/data/MetadataDiagnostics.kt) · [EpisodeMetadata.kt](../app/src/main/java/com/anilili/data/model/EpisodeMetadata.kt).

Diagnostics records playback context and runs independent read-only metadata probes with HTTP disk cache disabled, differentiating missing mapping, unavailable data and service errors. MetadataRefresh offers a separate refresh workflow with progress, network cache bypass, file validation and metadata cache clearing. It is not a reset of a user's journal. Respect provider terms, request limits and explicit classifications. See the README's source links and [diagnostics documentation](DIAGNOSTICS.md).

## Inherited application foundations

The fork retains the upstream account integrations, library, playback, subtitles, episode downloading, search, schedule, notification and profile foundations. The following is a navigation map, not a claim that every line has been audited or originally authored here.

| Subsystem | Start reading |
| --- | --- |
| Accounts and profile | [ProfileViewModel.kt](../app/src/main/java/com/anilili/ui/profile/ProfileViewModel.kt) · [AccountSheet.kt](../app/src/main/java/com/anilili/ui/profile/AccountSheet.kt) · [Profiles.kt](../app/src/main/java/com/anilili/data/profiles/Profiles.kt) |
| History and library | [LibraryStore.kt](../app/src/main/java/com/anilili/data/library/LibraryStore.kt) · [ContinueWatchingVisibility.kt](../app/src/main/java/com/anilili/data/library/ContinueWatchingVisibility.kt) |
| Playback | [WatchViewModel.kt](../app/src/main/java/com/anilili/ui/watch/WatchViewModel.kt) · [WatchScreen.kt](../app/src/main/java/com/anilili/ui/watch/WatchScreen.kt) |
| Downloads/offline | [EpisodeDownloads.kt](../app/src/main/java/com/anilili/playback/EpisodeDownloads.kt) · [EpisodeDownloadService.kt](../app/src/main/java/com/anilili/playback/EpisodeDownloadService.kt) · [DownloadsScreen.kt](../app/src/main/java/com/anilili/ui/downloads/DownloadsScreen.kt) · [DownloadedEpisodeScreen.kt](../app/src/main/java/com/anilili/ui/watch/DownloadedEpisodeScreen.kt) |
| Schedule | [ScheduleViewModel.kt](../app/src/main/java/com/anilili/ui/schedule/ScheduleViewModel.kt) · [ScheduleScreen.kt](../app/src/main/java/com/anilili/ui/schedule/ScheduleScreen.kt) |
| Pairing | [PairingHandshake.kt](../app/src/main/java/com/anilili/data/pairing/PairingHandshake.kt) · [PairingTransport.kt](../app/src/main/java/com/anilili/data/pairing/PairingTransport.kt) · [PhonePairingViewModel.kt](../app/src/main/java/com/anilili/ui/pairing/PhonePairingViewModel.kt) and [protocol](TV_PAIRING_PROTOCOL.md) |

Provider architecture and upstream review process are documented in [UPSTREAM_PROVIDER_SYNC.md](UPSTREAM_PROVIDER_SYNC.md). AniLumos does not claim ownership of third-party media or an unrestricted right to access or redistribute it.

## In-app updates and release identity

The AniLumos-specific GithubUpdateSource points at this repository's releases API. It filters drafts and incompatible prereleases, parses version tags and selects the expected mobile or TV APK asset name. UpdateManager downloads into cache, checks the published SHA-256 and candidate package/version/signing certificate, and uses Android's install permission/installer flow. It does not silently replace the installed application. Mobile package identity is com.anilumos.app.

**Code:** [GithubUpdateSource.kt](../app/src/main/java/com/anilili/data/update/GithubUpdateSource.kt) · [UpdateManager.kt](../app/src/main/java/com/anilili/data/update/UpdateManager.kt) · [UpdateDialog.kt](../app/src/main/java/com/anilili/ui/settings/UpdateDialog.kt).

See [update source tests](../app/src/test/java/com/anilili/data/update/GithubUpdateSourceTest.kt), [update manager tests](../app/src/test/java/com/anilili/data/update/UpdateManagerTest.kt), [release runbook](release/runbook.md) and [configuration](CONFIGURATION.md). Signing secrets belong in protected release configuration, never documentation.

## Website and distribution

The public site is maintained on the [website branch](https://github.com/vnxrgb/Anilumos/tree/website), with a [static HTML/CSS/JavaScript landing page](https://github.com/vnxrgb/Anilumos/blob/website/website/index.html), responsive layout, sticky navigation, screenshots, feature sections and reduced-motion treatment. The site's deployment and content are distinct from the Android binary. The README documents the official Render URL, GitHub release links and Orion Store listing. Review the website branch separately before asserting that a site change is deployed.

## Build and validation

For the main mobile baseline, use JDK 17, Android SDK 36 and the repository's Gradle wrapper:

```bash
./gradlew :app:testMobileDebugUnitTest :app:lintMobileDebug :app:assembleMobileDebug
```

For signed releases follow the runbook and protected CI configuration. Unit tests and lint validate code behavior and static constraints, not runtime provider availability or physical device performance. Validate changed features on both phone and tablet. Document test results with the exact revision.

This documentation does not merge experimental features into main, publish APKs or certify external service availability.
