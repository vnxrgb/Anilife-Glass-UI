# Mobile polish tracking

Base: main at `43cf518ed6811e57428b159754d58b65944370c0` (2026-11-23). Work only on `feature/mobile-polish`; no automatic merge, release or APK publishing.

## Implemented
- [x] Home hero pager: memoize the list of media IDs used to key TV page/focus state. Avoid an allocation on unrelated pager recompositions; behavior remains unchanged. Commit `4e89a10`.

## Verification
- [ ] Compile, unit tests and lint on the current branch commit (CI pending / not verified).
- [ ] Device profiling: Home scroll frame timing and recomposition counts, before vs after.
- [ ] Regression checks: Home hero auto-advance, TV focus, banner navigation and reduced-motion behavior.

## Next priorities
- [ ] Profile Home/Browse/My List scroll jank and fix confirmed hotspots, retaining approved glass visuals.
- [ ] Review player/mini-player only for low-risk evidence-backed performance fixes; preserve ambient lighting and controls.
- [ ] Audit visible legacy branding while preserving required attribution, Morphe notice, IDs and compatibility keys.
- [ ] Check accessibility targets, loading/error states, lifecycle and image cache behavior.

No measured smoothness improvement is claimed until device testing. Existing upstream credits and legal files are untouched.
