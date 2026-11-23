# AniLumos mobile polish — v1.0.2 clean branch

Base: main at c8edaa9503519042a3f5f6313c2d0f0b5ed7cc65 (2026-11-23).
This branch deliberately excludes the divergent feature/mobile-polish branch's authentication, pairing, manifest, release workflow, and build configuration edits.

## Completed
- [x] Start a clean branch from v1.0.2 main; preserve update security and account fixes.
- [x] Port reviewed Home hero-list memoization and adaptive poster/grid sizing from the old polish branch.
- [x] Port corresponding adaptive layout unit tests.
- [x] Preserve player, ambient lighting, sources, IDs, attribution, and all license files without modification.

## Verification still required
- [ ] Run fresh mobile compile, unit tests, lint and Android CI on this exact branch.
- [ ] Review the three source/test file diffs against main and inspect any CI warnings.
- [ ] Profile Home, Browse and My List on physical devices (baseline vs branch frame times and allocations).
- [ ] Regression-test startup, navigation, account sign-in, updates, search, downloads, history, player gestures, subtitles, sources, mini-player and ambient lighting.
- [ ] Check reduced-motion, TalkBack and minimum touch targets.
- [ ] Audit user-visible branding separately, preserving upstream AniLife and Morphe legal credits.

Do not merge or publish until verified. Do not assume improved smoothness from successful compilation alone.
