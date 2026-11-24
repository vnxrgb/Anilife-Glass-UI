# AniLumos optical glass redesign

Reference: https://github.com/kushagrasinghx/BitChord (visual inspiration only). Its integration code is GPL-3.0; do not copy it into the differently licensed AniLumos project. Evaluate the independently licensed Apache-2.0 Kyant0/backdrop engine directly and retain its notices if adopted. Verify current upstream licensing before integration.

## Reset and replacement
- [x] Isolate from mobile polish and stable branches, retaining feature code.
- [x] Remove decorative translucent card fills, gradient rims and artificial interface blooms at the shared surface entry points.
- [ ] Remove remaining legacy glass rendering in navigation, player controls, headers, popups, dialogs and theme previews.
- [ ] Build a shared recorded backdrop and optical renderer for floating controls, with refraction on supported hardware, frost fallback and solid accessibility fallback.
- [ ] Redesign settings, search, filters, navigation, cards, downloads, details, Wrapped, discovery and guided tour using a coherent component system; content surfaces remain legible rather than refracting every poster.
- [ ] Adapt tablet and TV layouts, preserve D-pad focus and low-end performance; integrate TV-only feature changes without changing existing preview branches.
- [ ] Migrate appearance preferences safely, preserving themes, account data and backups.
- [ ] Preserve VideoAmbientWash, Morphe renderer, playback, downloads, sign-in and pairing behavior.
- [ ] Compile both flavors, run unit tests and lint, and validate real device layouts and performance before any preview publication. Do not publish or merge automatically. Confirm distribution rights before release.
