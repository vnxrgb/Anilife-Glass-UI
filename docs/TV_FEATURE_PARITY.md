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
