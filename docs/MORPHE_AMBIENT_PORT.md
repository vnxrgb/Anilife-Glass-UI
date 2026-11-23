# Enhanced ambient lighting (Morphe GPLv3 port)

This branch integrates Morphe's actual `AmbientLightMath.java` spatial renderer with AniLife's Android Media3 player.

- Upstream: https://github.com/MorpheApp/morphe-patches
- Original file: `extensions/youtube/src/main/java/app/morphe/extension/youtube/ambientlight/AmbientLightMath.java` (copyright 2026 Morphe)
- License: GNU GPL version 3, plus the project's GPLv3 Section 7 terms in `licenses/morphe/NOTICE`.
- Modification: package name and class accessibility changed; `MorpheAmbientRenderer.kt` adapts the spatial math to AniLife's player. AniLife is not affiliated with Morphe.
- Android integration: `VideoAmbientWash.kt` reads Media3's existing video `SurfaceView` via Android PixelCopy, computes the near and far linear-light grids on a worker dispatcher and draws the ambient layer beneath watch-page details. It does not alter subtitles, decoder configuration or video playback.
- Safety: returns no light for secure/invalid frames, prevents some unnecessary processing on still scenes and low-memory devices, and samples only while playback is active.
- This is **not** Morphe's YouTube-specific `AmbientLightController.java` or `LightView.java`. Their YouTube view identifiers and injected hooks are inapplicable to AniLife. The pixel-processing core is directly ported, while surface capture, screen geometry, visibility and settings integration are AniLife-specific.
- The source is made available here on the feature branch. The project license is GPLv3 on this branch as confirmed authorized by the project representative; the former AniLife license is retained for provenance in `licenses/anilife/PRIOR-LICENSE.txt`.
- Notices and license copies are also packaged at `app/src/main/assets/MORPHE_NOTICE.txt` and `app/src/main/assets/GPL-3.0.txt` for APK recipients.

## Validation

Passing CI assembly and unit tests is required before a preview APK is distributed. Device testing must still validate illumination geometry, subtitle legibility, performance, light/dark themes, encrypted content fallback and player/mini-player transitions. Passing CI alone does not prove visual equivalence with Morphe's enhanced YouTube patch.
