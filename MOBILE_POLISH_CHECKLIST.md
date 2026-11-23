# Mobile polish tracking

Branch: `feature/mobile-polish`. Original base: `main` at `43cf518ed6811e57428b159754d58b65944370c0`.
Last audited 2026-11-23: `main` at `36d3254327659ec821e0b0a61d8b7d5b9c7dc3a6`; branch 5 commits ahead and 2 behind. Do not merge old redesign branches, modify main, or publish APKs/releases automatically.

## Implemented (source changes; device impact unverified)
- [x] Home hero pager: memoize media IDs used for TV page/focus identity, avoiding allocations on unrelated recompositions (`4e89a10`).
- [x] Adaptive layout: phone rail poster sizing and Browse minimum card width; tablet Home poster columns account for available width. Dedicated adaptive-layout unit tests added on this branch. Preserve approved visuals and TV behavior pending device review.

## Verification still required
- [ ] Compile mobile variant and run focused unit tests and lint for current branch HEAD; record GitHub Actions URLs and results. No passing result established as of this audit.
- [ ] Profile Home/Browse/My List scroll frame times, allocations and recomposition counts before/after on a physical phone.
- [ ] Check Home hero auto-advance, navigation, banner clicks, tablet split-screen, reduced motion and accessibility.
- [ ] Confirm phone rail/card sizing across common display-size settings and small screens.
- [ ] Verify streaming, downloads, history, accounts, mini-player and ambient video lighting unchanged.

## Next safe priorities
- [ ] Establish a passing compile/test/lint milestone for existing changes before further performance claims.
- [ ] Identify a measured scroll-jank hotspot in Home/Browse/My List; avoid speculative rewrites or extra blur.
- [ ] Audit user-visible legacy branding separately from required AniLife/Morphe author, copyright and license credits; preserve internal identifiers and migration keys.
- [ ] Review lifecycle, loading/error states, image caching and touch targets with concrete evidence.

No smoothness, compatibility or visual-quality improvement is declared device-verified.
