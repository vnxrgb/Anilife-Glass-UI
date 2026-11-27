# Interface and cinematic playback

Part of the [AniLumos technical documentation](TECHNICAL_GUIDE.md).

**Source baseline:** [`6ebaead`](https://github.com/vnxrgb/Anilumos/tree/6ebaead4093a31b76a40bef0718827ca8b48167b) on `main`. This describes inspected source, not a new device-test result. Original additions are licensed under [MIT](../LICENSE-ANILUMOS) only to the extent owned by vnxrgb; inherited and third-party components retain their terms. See [COPYRIGHT.md](../COPYRIGHT.md).

## Optical Glass UI

The UI separates translucent neutral surfaces from live optical effects. GlassSurface.glassCard provides rounded translucent Material surfaces with a fine rim; OpticalGlass samples a supplied LayerBackdrop only for floating chrome, avoiding a scrolling surface sampling itself. Its implementation uses Kyant Backdrop blur, optional lens refraction and highlight. On Android below API 31, disabled effects or absent backdrop, it falls back to a clipped solid surface and border. Lens refraction is conditional on API 33 and later. Navigation backing colors and opacity protect contrast against variable artwork.

GlassMaterialControls exposes Liquid, Frosted and Smoked preferences, opacity, blur and border controls through SettingsStore. Its own description explicitly limits experimental optical glass to floating navigation and notes that other controls retain neutral surfaces. Do not claim that every preset or slider produces identical effects on every screen. GlassSection supports per-area glass preferences including Navigation, Home, Details, Browse, Popups and Refresh. Rendering cost and device support matter.

**Code:** [OpticalGlass.kt](../app/src/main/java/com/anilili/ui/components/OpticalGlass.kt) · [GlassSurface.kt](../app/src/main/java/com/anilili/ui/components/GlassSurface.kt) · [FloatingNavigationBar.kt](../app/src/main/java/com/anilili/ui/components/FloatingNavigationBar.kt) · [NavigationGlassContrast.kt](../app/src/main/java/com/anilili/ui/components/NavigationGlassContrast.kt) · [GlassMaterialControls.kt](../app/src/main/java/com/anilili/ui/settings/GlassMaterialControls.kt) · [GlassSection.kt](../app/src/main/java/com/anilili/data/settings/GlassSection.kt) · [SettingsStore.kt](../app/src/main/java/com/anilili/data/settings/SettingsStore.kt).

**Dependencies:** Compose/Material3 and Kyant Backdrop; retain library and upstream attribution.

## Artwork ambient effects

HomeAmbient and DetailAmbient are distinct from video ambient lighting. They load a small 64×64 non-hardware artwork bitmap through Coil, derive colors for the relevant background, and use transparent/fallback state when artwork cannot be loaded. Home uses its list state for its presentation; Details adapts to selected artwork. These effects do not sample the playback video.

**Code:** [HomeAmbient.kt](../app/src/main/java/com/anilili/ui/home/HomeAmbient.kt) · [DetailAmbient.kt](../app/src/main/java/com/anilili/ui/detail/DetailAmbient.kt) · [DetailScreen.kt](../app/src/main/java/com/anilili/ui/detail/DetailScreen.kt) · [HomeScreen.kt](../app/src/main/java/com/anilili/ui/home/HomeScreen.kt).

## Ambient Cinema

The player-side implementation adapts Morphe's GPLv3 spatial ambient-light math rather than claiming the renderer as an original algorithm. NativeVideoAmbientSampler uses Android PixelCopy to obtain a small 96×54 frame from the playback surface, performs color processing via MorpheAmbientRenderer on a worker dispatcher, and exposes a palette for VideoAmbientWash and fullscreen side ambient rendering. The palette is scoped to episode/stream identity in WatchScreen. PlayerAmbientSampling provides a separate small-frame tint helper and expects failure on secure video surfaces. Failed sampling must keep the dark/fallback appearance, not disable secure playback. The renderer runs independently of captions and playback timing.

**Code:** [VideoAmbientWash.kt](../app/src/main/java/com/anilili/ui/watch/VideoAmbientWash.kt) · [MorpheAmbientRenderer.kt](../app/src/main/java/com/anilili/ui/watch/MorpheAmbientRenderer.kt) · [AmbientLightMath.java](../app/src/main/java/com/anilili/ui/watch/AmbientLightMath.java) · [PlayerAmbientSampling.kt](../app/src/main/java/com/anilili/ui/watch/PlayerAmbientSampling.kt) · [WatchScreen.kt](../app/src/main/java/com/anilili/ui/watch/WatchScreen.kt).

**Provenance:** [Morphe integration notes](MORPHE_AMBIENT_PORT.md) and [required notice](../licenses/morphe/NOTICE). Original integration work is distinct from Morphe's renderer; GPLv3 obligations apply to covered combined work.

## Player controls, skip timing and Up Next

- TimedSkipPopup animates a countdown only while in a supplied time window and playing; leaving the window resets its state. Timing metadata is obtained by existing and additional segment integrations. ManualSkipButtonPolicy provides a fixed-length fallback where position and duration are valid, only drawn with the controls by the player. Unknown duration is not a usable skip timeline.
- PlayerFillerSkip represents a target and callback for a positively identified filler episode with an available next non-filler target. WatchViewModel evaluates next indexes and season advancement according to skip preferences. Unknown or mixed classifications are not automatically treated as filler.
- NextEpisodeCard presents the next episode, play-now and cancel actions. Its trigger logic is intended for the end of the file, not the beginning of an outro window, so post-credit scenes can play. Autoplay and cancellation behavior are coordinated with existing player state. Afterglow is the separate terminal-series experience.
- PlayerLiquidGlass and the player layout components provide the presentation layer; Media3, source selection and stream playback remain inherited foundations.

**Code:** [TimedSkipPopup.kt](../app/src/main/java/com/anilili/ui/watch/TimedSkipPopup.kt) · [ManualSkipButtonPolicy.kt](../app/src/main/java/com/anilili/ui/watch/ManualSkipButtonPolicy.kt) · [AutoSkipCountdown.kt](../app/src/main/java/com/anilili/ui/watch/AutoSkipCountdown.kt) · [PlayerFillerSkip.kt](../app/src/main/java/com/anilili/ui/watch/PlayerFillerSkip.kt) · [NextEpisodeCard.kt](../app/src/main/java/com/anilili/ui/watch/NextEpisodeCard.kt) · [NextEpisodeCountdown.kt](../app/src/main/java/com/anilili/ui/components/NextEpisodeCountdown.kt) · [PlayerLiquidGlass.kt](../app/src/main/java/com/anilili/ui/watch/PlayerLiquidGlass.kt) · [WatchViewModel.kt](../app/src/main/java/com/anilili/ui/watch/WatchViewModel.kt).

**Tests:** [manual skip](../app/src/test/java/com/anilili/ui/watch/ManualSkipButtonPolicyTest.kt), [filler skip](../app/src/test/java/com/anilili/ui/watch/FillerSkipTest.kt), [next episode trigger](../app/src/test/java/com/anilili/ui/watch/NextEpisodeTriggerTest.kt), [ambient tint](../app/src/test/java/com/anilili/ui/watch/PlayerAmbientTintTest.kt).

## Responsive layout and accessibility

AppDeviceProfile derives phone/tablet/TV layout from window width, device signals and preferences. The inspected profile uses navigation rail at 600 dp or TV and expanded layout at 840 dp or TV, with different padding and low-memory effect policies. The stable mobile release targets phones and tablets; code paths mentioning TV do not imply a released stable TV application. Use shared device profile and chrome components rather than fixed phone-sized layouts. Test portrait, landscape, resized tablet windows, large UI scales, light/dark/AMOLED and system-bar contrast.

**Code:** [AppDeviceProfile.kt](../app/src/main/java/com/anilili/ui/adaptive/AppDeviceProfile.kt) · [UiScale.kt](../app/src/main/java/com/anilili/ui/adaptive/UiScale.kt) · [FloatingNavigationBar.kt](../app/src/main/java/com/anilili/ui/components/FloatingNavigationBar.kt) · [NavigationGlassContrast.kt](../app/src/main/java/com/anilili/ui/components/NavigationGlassContrast.kt) · [Theme.kt](../app/src/main/java/com/anilili/ui/theme/Theme.kt) · [MainActivity.kt](../app/src/main/java/com/anilili/MainActivity.kt).
