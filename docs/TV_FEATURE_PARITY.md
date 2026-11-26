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

## 2026-11-25 TV CI and release checkpoint

- Stable mobile source remains `ebe374eea7df5a77909f3a6b903753166f188c7e`; TV source `04bb868698115df0876afc13d8a953f6843d1dbf` passed TV verification workflow [45659276712](https://github.com/vnxrgb/Anilumos/actions/runs/45659276712) (unit tests, lint, debug APK assembly, package/launcher checks). This is not physical device validation.
- Signed preview release `tv-alpha-signed-ac24c1ebb8fd-run14` exists but uses older source `ac24c1e`. Current TV update fixes still need a newly signed preview and matching metadata.
- Github TV selector hardening now rejects malformed tags, oversized metadata assets, and explicitly non-uploaded APK assets; tests added. CI pending.
- Status: GitHub TV update code PRESENT / VALIDATE; signed latest preview MISSING; TV hardware parity VALIDATE. Existing TV-specific UI remains intact; no wholesale main merge.

## 2026-11-25 latest verification and response-size hardening

- Stable mobile reference remains `ebe374eea7df5a77909f3a6b903753166f188c7e`. TV diverges by 223 commits ahead / 100 behind when comparing TV to main. Reconcile only reviewed individual changes.
- Source TV CI [45679485265](https://github.com/vnxrgb/Anilumos/actions/runs/45679485265) **passed** at `649c73aafbcc3168ef0e0cf4782cc2cdf94c8383`: TV unit tests, lint, debug APK assembly and launcher/package verification. This is not a signed preview or hardware test.
- TV GitHub release metadata fetch now bounds bytes while reading even if HTTP Content-Length is missing or inaccurate. Follow-up commit `75040c3756aaa34288614fcc4be8fa90c6c8bae9` needs CI verification.
- The only verified published TV signed preview still targets `ac24c1e`; the current TV branch has **no verified release-signed preview APK**. Dedicated preview workflow runs every six hours and can be manually dispatched; avoid overlapping signed builds.
- GitHub TV updates: PRESENT / CI validation pending for latest hardening; installer signature checks still required. Full D-pad, Fire TV, account-pairing, playback and feature parity remain VALIDATE; no physical device testing performed.

## 2026-11-25 response origin hardening

- TV CI [45690628564](https://github.com/vnxrgb/Anilumos/actions/runs/45690628564) passed at 75040c3. Main remains ebe374e; TV divergence is 223 behind / 103 ahead, so no wholesale merge.
- TV update metadata fetch now refuses responses redirected away from exact official GitHub release API or selected metadata URL, with unit regression checks. Await new CI for this change.
- Current signed TV preview remains at ac24c1e; physical TV and Fire TV behavior remains unverified.

## 2026-11-25 branch reconciliation checkpoint (TV run 11)

- Actual current stable main head: `74794e7ee98e572a1322b040aac5ecae32233c5f` (upstream attribution notice), newer than the previous tracker reference `ebe374e`. Review attribution text before adapting TV credits; do not cherry-pick blindly.
- Actual TV head: `893e0ded2a3b24cb1dee91db36c795c60eba7101`. Comparison against main: **107 ahead, 224 behind**, merge base `b1ede5586b63166ef9da3dfe06cdef0e7e59d757`. No branch merge was performed.
- Source review confirms the TV update selector requires TV-only release tags, matching APK/metadata assets, package identity, version/build, SHA-256 and bounded sizes. The fetcher now allows approved GitHub CDN redirects for metadata, but rejects redirects for the releases API. The selected asset is not an installable update until the existing package/signature and checksum checks pass.
- Outstanding verification: CI for `893e0de` and later; current preview release signed asset provenance and certificate; end-to-end update installation; actual Android TV/Fire TV remote navigation and playback. Prior passing CI for older commits cannot validate this head.
- Current mobile-source audit is **incomplete**. Before declaring parity, compare latest main source screen-by-screen (especially attribution, v2.0.1 light-mode contrast, integrations, player and settings), then validate every TV-only path with D-pad/Back/remote-only tests. Do not mark shared source as TV-validated.
- No newly signed TV release should be announced without verified unit tests, lint, release assembly, package/launcher, signer and published standalone APK checks.

## 2026-11-25 current mobile delta and release gate

- Compared live branch heads: main `8c44471e39cda281872e342e95cde5c5f4b8542a`; TV `3c20de94c9d81cf2896bdc9851ad2c72a1909f79`; merge base `b1ede5586b63166ef9da3dfe06cdef0e7e59d757`. TV is 110 ahead and 257 behind main. This is a comparison checkpoint, not a merge or parity claim.
- Main contains newer recommendation ranking (`HybridRanking.kt`), metadata diagnostics, episode journal (`JournalStore.kt`), remote AniSkip/AniZip/Jikan changes, and a mobile GitHub update source. Each requires behavior-level TV review; none should be copied blindly over TV-specific focus, update, or playback paths.
- GitHub TV update source exists on the TV branch and filters official preview TV assets with package, version, size and SHA-256 metadata. Its presence supersedes the original MISSING label above; status is **PRESENT / end-to-end VALIDATE**. A matching published signed asset, installer certificate and real TV install are still unverified.
- GitHub connector combined status returned no statuses and its commit workflow lookup returned no runs for TV head `3c20de9`; this is **unknown CI state**, not a failed or passed CI result. Previous passing CI does not validate current main parity.
- Priority implementation queue: (1) validate preview signing/publication without overlapping runs; (2) compare current mobile recommendation and journal behavior with TV flows; (3) verify remote focus/back/overscan and playback; (4) test QR pairing and Fire TV permissions on hardware. No physical-device verification is implied.

## 2026-11-26 verified checkpoint (current heads)

- Mobile main: `6165d5badc28007d65afe415b3dcf9454e660639`; TV: `0e2578e4440e20ce825c6e4d3f77d3db61441847`; compare: **TV 120 ahead / 275 behind**. Preserve TV focus and update changes; do not merge main wholesale.
- Source CI: [run 45787561706](https://github.com/vnxrgb/Anilumos/actions/runs/45787561706) **success** for exact TV head `0e2578e` (workflow result, not hardware verification). Previous `33c2c18` run was cancelled by superseding run; not a verified failure.
- GitHub direct updates: **PRESENT / end-to-end VALIDATE**, not MISSING. TV-only preview release selection, metadata and SHA-256 checks, redirect restrictions and package/signer checks have source implementations and regression tests. Physical install/update remains untested.
- Signed TV preview: **PRESENT for older source / latest-head MISSING**. GitHub releases API lists `tv-alpha-signed-3c20de94c9d8-run17` with standalone `AniLumos-TV-ALPHA-signed.apk` and `TV-UPDATE.json`; latest source `0e2578e` has no verified published matching asset in this checkpoint. Do not claim latest-head APK ready.
- Current mobile delta still requires review of recommendations, journal, metadata diagnostics, AniSkip/AniZip/Jikan, profile/settings, player, light/dark UI, integrations and attribution. Shared code does not prove 10-foot or D-pad usability. Maintain **VALIDATE** for real Android TV and Fire TV, QR pairing, intro/outro, downloads, ambient mode, accessibility, memory/performance and playback.
- Release gate: before next publication check for active preview runs, use the dedicated preview workflow checking out `feature/tv-polish`, then verify unit tests, lint, release assembly, package/launcher, pinned signer, source SHA and published APK plus metadata. Never bypass verification or modify stable branches.
