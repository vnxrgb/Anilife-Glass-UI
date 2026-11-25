# Mobile polish tracking

Branch: `feature/mobile-polish`. Original base: `main` at `43cf518ed6811e57428b159754d58b65944370c0`.
Last audited 2026-11-23: `main` at `36d3254327659ec821e0b0a61d8b7d5b9c7dc3a6`; branch had 6 commits ahead and 2 behind before the TV lint correction. Do not merge old redesign branches, modify main, or publish APKs/releases automatically.

## Implemented (source changes; device impact unverified)
- [x] Home hero pager: memoize media IDs used for TV page/focus identity, avoiding allocations on unrelated recompositions (`4e89a10`).
- [x] Adaptive layout: phone rail poster sizing and Browse minimum card width; tablet Home poster columns account for available width. Dedicated adaptive-layout unit tests added on this branch. Preserve approved visuals and TV behavior pending device review.

## Verification still required
- [x] Post-fix Android CI on `acb85c8` passed on 2026-11-23: mobile unit tests, mobile lint/assemble, TV unit tests, and TV lint/assemble all successful. https://github.com/vnxrgb/Anilumos/actions/runs/45200411693 . The `ImpliedTouchscreenHardware` fix in `3a5eb6e` is build-verified; physical-device behavior is not.
- [x] Confirmed all four Android CI jobs passed on `acb85c8`; CodeQL also passed: https://github.com/vnxrgb/Anilumos/actions/runs/45200411833 .
- [ ] Dependency review still failed on `acb85c8` (https://github.com/vnxrgb/Anilumos/actions/runs/45200411735); prior failure attributed to disabled repository dependency graph, not a demonstrated vulnerable dependency. Recheck configuration separately.
- [ ] Profile Home/Browse/My List scroll frame times, allocations and recomposition counts before/after on a physical phone.
- [ ] Check Home hero auto-advance, navigation, banner clicks, tablet split-screen, reduced motion and accessibility.
- [ ] Confirm phone rail/card sizing across common display-size settings and small screens.
- [ ] Verify streaming, downloads, history, accounts, mini-player and ambient video lighting unchanged.

## Next safe priorities
- [x] Establish passing post-fix compile/test/lint milestone (`acb85c8`); no performance claim follows from CI alone.
- [ ] Identify a measured scroll-jank hotspot in Home/Browse/My List; avoid speculative rewrites or extra blur.
- [ ] Audit user-visible legacy branding separately from required AniLife/Morphe author, copyright and license credits; preserve internal identifiers and migration keys.
- [ ] Review lifecycle, loading/error states, image caching and touch targets with concrete evidence.

No smoothness, compatibility or visual-quality improvement is declared device-verified.

## Branch reconciliation (2026-11-23)
- Current `main`: `c8edaa9503519042a3f5f6313c2d0f0b5ed7cc65` (v1.0.2 direct in-app updates). GitHub comparison: polish branch 14 commits ahead / 4 behind, merge base `43cf518`.
- Overlap to reconcile before merging: OAuthUnavailableDialog, ProfileScreen, SettingsScreen and TV manifest are modified on both histories; release workflow also differs. Do not blindly merge or overwrite current main's direct-update security checks, versioning, signing verification or account fixes.
- The prior green CI milestone is `acb85c8`, **not** the current polish branch tip. Newer changes need fresh mobile compile/tests/lint after reconciliation; device smoothness and ambient-lighting regressions remain unverified.
- Next action: isolate mobile performance commits against current main (or carefully reconcile overlapping files), then run one meaningful CI milestone; avoid speculative player changes and APK publication.
