# Mobile polish tracking

Branch: `feature/mobile-polish`. Original base: `main` at `43cf518ed6811e57428b159754d58b65944370c0`.
Last audited 2026-11-23: `main` at `36d3254327659ec821e0b0a61d8b7d5b9c7dc3a6`; branch had 6 commits ahead and 2 behind before the TV lint correction. Do not merge old redesign branches, modify main, or publish APKs/releases automatically.

## Implemented (source changes; device impact unverified)
- [x] Home hero pager: memoize media IDs used for TV page/focus identity, avoiding allocations on unrelated recompositions (`4e89a10`).
- [x] Adaptive layout: phone rail poster sizing and Browse minimum card width; tablet Home poster columns account for available width. Dedicated adaptive-layout unit tests added on this branch. Preserve approved visuals and TV behavior pending device review.

## Verification still required
- [x] At `564643b`, mobile unit tests and mobile lint/assemble passed; TV unit tests passed. Android CI run: https://github.com/vnxrgb/Anilumos/actions/runs/45184322308 . TV lint/assemble failed on `ImpliedTouchscreenHardware`; corrected in `3a5eb6e` by marking touchscreen optional in the existing TV manifest (no TV feature added). **Post-fix CI not yet verified.**
- [ ] Confirm CI on `3a5eb6e` or later, including TV lint/assemble; do not assume a fix passes until the workflow completes.
- [ ] Dependency review remains blocked because repository dependency graph is disabled (https://github.com/vnxrgb/Anilumos/actions/runs/45184322285); not a demonstrated vulnerable dependency. CodeQL passed: https://github.com/vnxrgb/Anilumos/actions/runs/45184322276 .
- [ ] Profile Home/Browse/My List scroll frame times, allocations and recomposition counts before/after on a physical phone.
- [ ] Check Home hero auto-advance, navigation, banner clicks, tablet split-screen, reduced motion and accessibility.
- [ ] Confirm phone rail/card sizing across common display-size settings and small screens.
- [ ] Verify streaming, downloads, history, accounts, mini-player and ambient video lighting unchanged.

## Next safe priorities
- [ ] Establish a fully passing post-fix compile/test/lint milestone before further performance claims; mobile checks passed at `564643b`.
- [ ] Identify a measured scroll-jank hotspot in Home/Browse/My List; avoid speculative rewrites or extra blur.
- [ ] Audit user-visible legacy branding separately from required AniLife/Morphe author, copyright and license credits; preserve internal identifiers and migration keys.
- [ ] Review lifecycle, loading/error states, image caching and touch targets with concrete evidence.

No smoothness, compatibility or visual-quality improvement is declared device-verified.
