# AniLife Glass UI — Implementation and acceptance checklist

**Source of truth:** The six-panel user-approved Glass UI mockup (Home, Details, Browse, cinematic player, Settings, brand/system card) and the separate YouTube-style mobile ambient-player reference. **Target:** mobile Android. Preserve Android TV behaviour.

**Status terminology:** [x] implemented in repository code; [ ] not yet verified or incomplete. Passing Gradle/CI does not imply a visual match. Do not check a design acceptance item until screenshots from a real device verify it.

## 0. Guardrails and release gates
- [x] Develop on `feature/unified-glass-ui`, keep stable/main unchanged during implementation
- [x] Maintain one universal mobile APK preview workflow and direct release asset
- [ ] Verify all of the below visually against reference at phone sizes 360dp, 393dp and tablet
- [ ] Confirm light, dark and every user-selected theme/accent; glass panes remain neutral and translucent, accent glow adapts
- [ ] Preserve user-configured backgrounds, disabled blur setting, and low-end/low-RAM performance
- [ ] Verify accessibility contrast, dynamic text scale, touch targets, focus/keyboard/remote and screen reader labels
- [ ] Verify Android CI, unit tests, lint and APK build for final commit
- [ ] Verify playback, source changing/fallback, casting, subtitles, downloaded/offline episodes, resume, notifications, list/account sync
- [ ] Verify TV/Fire TV layouts and remote navigation were not modified/regressed
- [ ] Verify release SHA = successful latest commit; asset post-dates build; publish **one** `anilife-glass-universal.apk`, no ZIP
- [ ] Record real-device screenshots of Home, Details, Browse, Settings, My List, Downloads, Schedule, portrait Watch, fullscreen landscape Player

## 1. Universal Glass foundation
- [x] Reuse theme-driven Material colour scheme, not hard-coded purple glass
- [x] Reusable `glassCard`, `glassPanelColor`, `glassRimColor`
- [x] Theme-coloured soft atmospheric wash behind primary phone destinations (`glassAtmosphere`)
- [x] Shared rounded poster cards, selected navigation accents and transparent panel primitives
- [ ] Transparent/blurred/frosted treatment consistently applied to **all** dialog/sheet/popover and settings subpages
- [ ] Add consistent luminous inner rims, shadow depth, optional Haze blur fallback for Android below 12
- [ ] Standardize paddings, icon sizes, heading/body text, small compact chips, button height, loading/empty/error states
- [ ] Verify gradient effects do not add flicker, high overdraw, frame drops, or wash out text

## 2. Home
- [x] Inset rounded hero and reactive backdrop with logo or text fallback
- [x] Hero Watch now and Details actions
- [ ] Match screenshot: compact hero, crisp title/meta, integrated inner gradient, tinted glass CTAs and progress dots
- [ ] Continue Watching **horizontal thumbnails** with resume overlay, actual progress strip and remaining time
- [ ] Trending Now and New Episodes rails with small poster cards and consistent section actions
- [ ] Ensure meaningful content visible above fold on average phones (avoid giant hero)
- [ ] Ensure all home personalization, reordering, long-press, loading and TV navigation work

## 3. Anime Details
- [x] Existing cover + logo header, studio and series stats, watch/list actions
- [ ] Compact two-column poster/title composition with legible blurred backdrop
- [ ] Purple/accent only for CTAs/selected Overview/Episodes/Related pill, not glass base
- [ ] Small equidistant status/format/year cards and frosted countdown with icon
- [ ] Compact genre pills, description, episodic progress and expandable Overview
- [ ] Episode browser/cards and season selection; long-running title virtualization
- [ ] Verify source/studio links, list actions, Watched toggle and Watch/Continue logic

## 4. Browse
- [x] Search/filter/category chips, 3-column media grid, shared rounded posters
- [ ] Real frosted compact search and filter buttons with consistent background
- [ ] One unified selected-chip treatment and clean scrolling/collapsing header
- [ ] Condense poster title/meta typography to match screenshot without ellipsis overload
- [ ] Verify filtering, categories, pagination, query keyboard, TV focus and season browsing

## 5. Portrait Watch and ambient mode
- [x] Sample native video frames and transition between subdued colours over watch page
- [x] Video remains clear, ambient wash extends underneath synopsis and episode controls
- [ ] Tune to YouTube mobile example: richer **blurred** frame-reactive blooms, no flat muddy fill or abrupt colour shifts
- [x] Provide persistent on/off and three-level ambient strength controls for live sampling and watch-page wash
- [ ] Frost synopsis, source/category, action buttons and active episode without obscuring readability
- [ ] Verify embedded players, protected surfaces, black frames and low-RAM fallback

## 6. Fullscreen cinematic player
- [x] Existing playback, subtitles, seek, skip, cast, source, fullscreen and settings interactions
- [x] Neutral translucent time/transport buttons
- [ ] Match reference: artwork unobscured, slim glowing seekbar, compact transport and glass bottom toolbar
- [ ] Group Episodes, Audio & Subtitles, Speed, Quality in a rounded frosted dock (existing features must remain)
- [ ] Style overlays for settings/source selection/quality and handle small & ultrawide layouts
- [ ] Verify subtitle readability, gesture seek/lock and fullscreen rotation, accessibility and low-end FPS

## 7. Floating navigation and safe areas
- [x] Five fixed-width Home/My List/Browse/Downloads/Profile items
- [x] Stable selected indicator with accent-colour light and neutral adaptive bar
- [x] Implement compact height/padding and animated soft indicator/glow in navigation code (visual match still unverified)
- [ ] No page content hidden behind capsule or floating companions (scroll to final row)
- [ ] Verify hidden states for login, player fullscreen, sheets, TV and keyboard

## 8. Settings
- [x] Category grid with rounded frosted tiles, icon emphasis and caption spacing
- [ ] Reduce tile size/spacing toward the screenshot and add shared background atmosphere
- [ ] Style every subpage: Player, Subtitles, Downloads, Servers, Accounts, Notifications, Content, Appearance, Data, App, About
- [ ] Validate all toggles, pickers, focus, settings search, theme preview, back navigation

## 9. My List, Profile, Downloads
- [x] My List filters, watchlist, Continue Watching, account/sync and sign-in paths exist
- [x] Neutral glass account and download cards
- [ ] Match reference spacing and compact progress thumbnails for Continue Watching
- [ ] Frost search, filter, lists, account sheet and empty/error states consistently
- [ ] Verify list creation, edit/move/remove, sync and downloads deletion/playback/export

## 10. Schedule and secondary screens
- [x] Neutral date selection chips + day stepper fixed in a reserved footer (no overlay)
- [ ] Match spacing, timeline, card styles and ensure footer never covers items
- [ ] Apply glass system to notifications, calendar dialogs, list dialogs, update/splash/other entry points
- [ ] Verify reminders, timezone updates, accessibility and navigation

## Release criteria
An APK build is only **technical readiness**. A final release requires all core screens to match the approved mockup at realistic phone sizes AND player ambient mode to behave like the supplied YouTube reference, with all regression and accessibility gates checked. Leave this file honest: do not mark items done based solely on source changes or a successful GitHub Action.

## Implementation log (2026-11-22)
- `7efa3ae`: Shared frosted search/filter components, account card, offline empty state, and near-black live ambient frame suppression. Code only; build/device verification outstanding.
- Follow-up: Cinematic outline and translucency in player settings/source modal and side panel, consistent glass treatment on library/download artwork. Code only; build/device verification outstanding.
- Next priorities remain the unchecked mockup/real-device acceptance items above. Do not mark them complete on the basis of commits alone.

- `SECONDARY UI PASS`: Notifications screen and cards now use the mobile glass atmosphere; calendar day cells retain neutral theme-adaptive frosted backgrounds and accented selection; next-episode countdown uses a theme-responsive rim/gradient glass panel. Code implementation, not device-tested.

- `SHARED SETTINGS PASS`: Settings rows and selection rows now use neutral translucent theme-colour panels and subtle hairline borders on mobile. TV keeps the established D-pad focus rendering. Code only; functionality and visual verification pending.

- `DIALOG GLASS PASS`: Shared phone modal/sheet backgrounds now use the current Material theme's translucent surface instead of a fixed dark panel. TV dialogs retain their existing colours; device build/visual verification pending.

- `AMBIENT COMPOSITION PASS` (`764a282`): Replaced the watch-page solid video-colour slab with three feathered radial colour blooms and a subtle shared gradient. The video remains unchanged; colours continue to come from sampled frames. Code implementation; live visual result still requires testing.

- NAVIGATION REFINEMENT: Selected destination now uses a neutral glass gradient with a restrained theme-accent outline instead of a permanently accent-filled background. Explicit accessibility labels retained for all five tabs. Code only; device screenshot comparison remains pending.

- `GLASS VISUAL COHESION PASS`: Shared glassCard gradient now has top highlight and better depth without allocating live blur; Home hero page dots sit in a frosted capsule and both hero CTAs include icons; Details tabs use a theme-responsive translucent sticky bar and selected pill. Implemented in code, not yet device-verified.

- `TOP CHROME & TOUCH TARGET PASS`: Theme-adaptive hairline glass highlights now live in the shared mobile PageTitleBar without sacrificing its pinned opaque scroll backing, and Continue Watching artwork corner actions have larger 40dp hit targets and a legible rim. Code change; touch accessibility acceptance still requires 48dp minimum audit.

- `HERO PLAY WIRING PASS`: Hero Watch now resumes the saved episode directly via existing Home callback when history exists, and safely opens Details for first-time titles (source/episode choice); Details still independently opens anime detail. Continue Watching artwork corner buttons use 48dp touch targets. Code; playback regression verification outstanding.

- `AMBIENT CONTROL PASS`: Added a persistent "Ambient video glow" setting under Player on handheld devices, indexed in Settings search, disabled both PixelCopy callback and wash when off; stored in DataStore and profile backup. No intensity slider yet. Code complete; on-device verification pending.

- `AMBIENT STRENGTH PASS`: Player settings expose an on/off switch and three controlled intensity levels (Subtle/Balanced/Vivid) via the existing stepper, persisted as a DataStore float in backups. Live video gradients scale without changing playback or subtitles. Build/device verification outstanding.

- `HOME & LOW-RAM PASS`: Home's Continue Watching shelf now uses wide 16:9 glass media thumbnails instead of square cards, retaining resume/progress and detail/remove controls; ambient PixelCopy sampling is disabled on Android low-RAM devices to avoid unnecessary per-frame bitmap allocations. Functional/visual validation pending.

- `BROWSE & PLAYER DETAIL PASS`: Browse empty search state is a compact neutral glass card on handheld devices, preserving all context-sensitive filter advice and adult-content controls; player's seek progress now has a soft accent/white underglow with existing chapter markers and buffer track retained. Code implemented; real-device acceptance pending.

- `SETTINGS DENSITY PASS`: Phone category tiles now use tighter 7dp gutters, 32dp icon badges, more compact padding, and flexible 1-2 line labels to match the approved three-column Settings reference. Verify readable labels at enlarged font settings and TV grid behaviour.

- `HERO COMPOSITION PASS`: Mobile Home featured area height, genre and CTA vertical spacing reduced to expose more feed; Anime Details poster/title composition tightened, outlined cover and secondary metadata neutralized so accent highlights remain for interactions. Visual/device acceptance remains unverified.

- `PLAYER DOCK & SETTINGS FINDABILITY PASS`: Noncompact/fullscreen player bottom icons now share one frosted rounded action dock, retaining control group scaling and every existing onClick handler. Ambient strength row stays indexed/visible even while ambient is off and disables its stepper buttons; stored strength is remembered. Real-device acceptance and accessibility tests pending.

- `SHARED EMPTY/ERROR PASS`: Full-screen loading now retains the shared phone atmosphere while TV uses its original canvas; shared full-screen error content is frosted on mobile without changing retry, technical details or reporting. Code only; device verification outstanding.

- `ACCESSIBLE PLAYER + BROWSE POLISH PASS`: Phone player timeline now exposes a progress value and accessibility seek action, uses a minimum 48dp touch target, and avoids division by zero on transient zero-width gestures. Browse no-results glass panel now caps its width on tablets and centers guidance. Implementation only; build, screen reader and device testing remain outstanding.

- `REFERENCE HOME THUMBNAIL PASS`: Continue Watching returned to compact near-square (~124dp) mobile thumbnails so about three appear per row, retaining a progress strip and central play glyph. Two 48dp corner controls were consolidated into one accessible overflow menu containing Details, Remove and list management to avoid covering the artwork. TV retains 16:9 thumbnail geometry. Code only; verify reference screenshots and touch on real devices.

- `COORDINATED CHROME PASS`: Refined five-tab navigation capsule height, inner spacing and neutral layered tint; softened shared mobile search-field rim; reduced the delayed pause-overlay scrim so more video artwork remains visible while keeping title billing. Code only, no APK build or visual acceptance claimed.

- `FIVE-SCREEN DENSITY PASS`: Home hero now reserves more above-the-fold room for content on phone/tablet; Details cover shrunk slightly to balance title/actions; Settings category tiles have tighter icon and vertical spacing; Downloads empty state is width-capped for tablets; Schedule day strip is more compact. Code only; full redesign and device verification are not complete.

- `TEN-CHANGE MOBILE POLISH BATCH` (`738d468` plus follow-up): Two Home hero breakpoints compacted; Details episode controls tightened; Settings category icon backing reduced; Downloads filtered empty state frosted; Schedule date heading condensed; floating navigation bottom clearance reduced; shared search input given 56dp minimum height; paused-player logo capped at 80dp; Browse empty-state outer padding reduced. Implementation only, build and device verification pending.

- `20-CHANGE COORDINATED PASS` (`e17b504` plus follow-up): Fifteen edits across Home (four), Details (three), Settings (two), Downloads (three), Schedule (three), plus navigation icon contrast and shadow, search focus rim, player pause overlay width and Browse empty-state recovery copy. These are layout/polish adjustments, not completion of the full redesign. APK build and device validation remain deferred.

- `SHARED DIALOG READABILITY PASS`: Mobile full-synopsis overlay now uses a theme-neutral glass reading surface with compact phone insets, while TV retains its full-screen dark D-pad reading experience. Custom-list naming field uses the same neutral glass fill, responsive rim and accent focus border; account writes and input handling unchanged. Code only; real-device and accessibility acceptance pending.

- `NAVIGATION ANIMATION SECTION`: Mobile selected-tab glass wash and theme-accent glow now animate independently across selection changes without changing five-tab widths or touch targets. The compact capsule and existing root safe-area contract are preserved. Visual/device acceptance and takeover/IME regression checks remain pending.

- `FULLSCREEN CINEMATIC PLAYER CODE PASS`: Reduced the bottom video scrim to expose more artwork; replaced the bottom control dock's flat dark fill with layered neutral frosted gradient and brighter rim; increased labeled action readability; widened seekbar's restrained glow; made settings bottom-sheet glass more translucent with neutral rim. Existing transport/seek/settings handlers, TV controls, subtitle and cast logic untouched. This is **partial code implementation**, not a verified completed player section: action grouping, device layouts, fullscreen regression and accessibility checks remain open.

- `FULLSCREEN PLAYER GROUPED DOCK PASS`: Fullscreen touch player now separates four primary glass-dock actions (Episodes, Audio & Subtitles, Speed, Quality) from a secondary utility strip (sources, cast, fullscreen and settings). Speed and Quality currently open the existing player Settings panel containing those controls; they are not dedicated subpanels. Inline phone and TV controls retain their existing paths. This is code implementation only; compilation, responsive widths, rotation, subtitles and accessibility verification remain open.

- `SHARED FILTER/SEARCH ACCESSIBILITY PASS`: Shared phone/TV filter pills and sort menus now expose button roles, filter labels and neutral theme-responsive rounded dropdown surfaces; search field exposes its placeholder to accessibility services. Shared glass highlights and shadow depth are refined without live per-card blur. Code-only change; verify screen-reader announcements, Material3 dropdown layout and theme contrast on device.

- `SECONDARY ERROR/DOWNLOAD GLASS PASS`: Shared copyable error-code chip now uses a theme-neutral rounded warning panel and explicit accessibility description; inline error details use adaptive readable foreground; batch download progress is a compact frosted panel with polite status announcements and neutral action rim. Shared glass rim gets a subtle inner hairline. This is source implementation only; compile, TV focus, screen-reader and real-device visual checks remain open.
