# Android TV parity tracker (source audit: 2026-11-25)

Reference mobile main: `ebe374eea7df5a77909f3a6b903753166f188c7e`
TV branch before this tracker: `50930bec8e8e0f711efee8f5a905f2ff200b737a`

**Do not treat a shared Kotlin source file as proof of TV usability.** TV is 223 commits behind main and 91 ahead. Review individual changes; no wholesale merge. Status legend: PRESENT = code exists, VALIDATE = remote/hardware behavior unverified, MISSING = no verified implementation.

| Area | TV status | Next verification |
| --- | --- | --- |
| Launcher, branding, banner | PRESENT / VALIDATE | TV and Fire TV launch, package ID, 10-foot artwork |
| Home, Browse, Search, Details | PRESENT / VALIDATE | D-pad focus, back stack, overscan, poster memory |
| Player, subtitles, source choice | PRESENT / VALIDATE | remote seek, intro/outro, playback recovery |
| Ambient Cinema Mode | VALIDATE | real low-power hardware, light/dark contrast |
| Profile, library, collections, Wrapped | PRESENT / VALIDATE | TV navigation and export alternatives |
| Downloads, calendar, notes, queue | VALIDATE | storage permissions, keyboard-free navigation |
| AniList, MAL, Simkl, Discord | VALIDATE | device pairing and supported account flows |
| QR TV pairing | PRESENT / VALIDATE | end-to-end mobile-to-TV sign-in |
| Provider compatibility and credits | VALIDATE | provider results and required AniLife/Morphe text |
| Github direct updates for TV | MISSING | use signed, TV-only official preview assets; reject mobile APKs |
| Release-signed TV alpha | MISSING | preview workflow signs, tests, lint, checks cert and publishes standalone APK |

## Current verified blockers

- `UpdateManager.fetchLatest` only consults signed Nostr manifests; no GitHub release fallback. TV update checks must never accept a mobile APK or unverified signing identity.
- `feature/tv-polish` CI previously passed at run 45429849473, but this does not establish parity with today's main.
- Current source-repo `.github/workflows/tv-preview.yml` produces a **debug-signed** TV APK for temporary CI artifacts; this must not be presented as a release-signed preview.
- The preview repository's inspected recent releases contain `Anilumos.apk` but no verified TV-specific signed APK. Publishing requires the dedicated preview workflow and verification of the resulting asset.

## Safe next batch

1. Reconcile main's update code, release-selection tests, and TV UI against branch changes without overwriting TV-specific focus behavior.
2. Implement an official GitHub TV-only update source with trusted release metadata and signer/package verification, then test rejecting mobile-only assets and downgrades.
3. Verify remote-only playback, TV pairing and focus regressions; run TV unit tests and lint before a signed preview build.

## TV GitHub update integration batch
TV flavor selects only official signed-TV-named GitHub prereleases with a matching TV-UPDATE.json metadata asset, checksum, package and version. Older releases without metadata are rejected. Existing installer checks enforce installed signer and package. Preview publisher must produce metadata before offers become available; tests and build unverified.

## Update asset URL hardening
TV update selection now requires both metadata and APK asset URLs to match the selected release tag exactly, with no query parameters or cross-release asset substitution; APK size is capped at 512 MiB. Added regression tests. Build and hardware validation remain pending. GitHub metadata is not a cryptographic signature; the installer must continue to enforce package and installed signing identity, and the downloaded SHA-256 must match before installation.
