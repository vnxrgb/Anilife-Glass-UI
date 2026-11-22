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

- `SECONDARY UPDATE/PROGRESS PASS`: Expanded update notes on phone now use a theme-adaptive neutral frosted reading surface and selected focus rim, preserving TV focus/key scrolling. Account episode progress controls remain on the existing shared dialog primitives; no account or playback logic changed. Source implementation only, pending compile and real-device verification.

- `HOME PERSONALIZATION TOUCH PASS`: Arrange Home drag handle on handhelds is now a 48dp neutral frosted touch surface with theme-adaptive icon contrast. Drag/reorder persistence and TV arrow navigation remain unchanged. Source implementation only; touch/TV/device verification pending.

- `REORDER AND SEARCH ACCESSIBILITY PASS`: Home reorder handle now has a single accessible drag description and 48dp arrow targets; shared phone search clear button and TV dialog clear control have 48dp minimum targets, with an explicit TV search action label. Implementation only; no compilation, screen reader, remote or device acceptance claimed.


- `CALENDAR & ACCESSIBILITY COHESION PASS`: Monthly schedule grid now has a neutral glass panel on phones with preserved TV focus grouping; day cells announce dates, selection, today and episode counts. Shared filter/sort menus announce selected choices; next-episode countdown retains a second-accurate visible clock but throttles spoken announcements to minute-level changes. Source-only implementation; compile, device screen-reader and TV checks remain open.

- `SHARED MOBILE DENSITY & RENDER SAFETY PASS`: Reduced phone poster caption gaps while retaining TV spacing and title baseline consistency; compacted mobile top-level title typography without changing TV headlines; guarded zero-size radial atmosphere shaders and softened background wash over custom wallpapers. Source implementation only; no build or device screenshot acceptance.

- `CINEMATIC PLAYER FOCUSED ACTION PASS` (`422e44c` onward): Dedicated fullscreen playback-speed and video-quality panels now use the existing playback controller, persistent speed setting and shared responsive side/bottom sheet. Labeled pills receive 48dp minimum touch height; scrims are softened. Ultra-short fullscreen layouts no longer omit the primary player actions. The player action dock uses a layered neutral gradient instead of a broad blank backdrop. Ambient frame sampling now increases colour separation slightly and reduces flat gray wash while retaining secure-surface and low-memory fallbacks. **Code implementation only; build completion, actual phone screenshots, rotation, casting, subtitles and Android TV regression verification remain open.**

- `DETAIL / BROWSE / FILTER COHESION PASS`: Phone Details hero and poster/logo proportions tightened without changing TV navigation; studio action now uses neutral glass with a theme-accent interaction rim. Browse quick-sort and suggestions use shared neutral frosted surfaces; active filter chip accents are restrained and removable filters announce their actions. Shared filter sizing and Browse empty-state density are coordinated. These are source changes, **not** visual or accessibility acceptance; no intermediate APK build requested.

- `MAJOR DETAIL/DOWNLOADS/SETTINGS IMPLEMENTATION BATCH`: Detail tabs, season chips, and glass Overview use a shared neutral treatment with a restrained accent rim; facts and season chip density refined. Downloads empty state now provides a clear offline explanation and icon; delete target expanded and filtered empty state tightened. Settings category grid uses consistent rounded neutral icon plates and accessible Open-section labels. Existing data sources, navigation and Android TV content are preserved. **Source implementation only; no intermediate APK build; requires compile, visual, device and TV regression tests.**

- `MY LIST / BROWSE LARGE PASS`: Handheld My List's built-in and custom filter chips now use neutral theme-aware glass while TV retains its original chip palette. Empty-library panels match the common glass material, and playback history indicators show only measured progress (no invented minimum fraction). Browse poster grid gains phone/tablet-specific gutters and tighter vertical density while preserving TV widths, adaptive column counts, pagination, and navigation clearance. **Source only; compile and device verification pending; no APK build requested.**

- `ONE-SIDED AMBIENT FRAME SAFETY PASS` (`2ce528a`): When half of a sampled video frame is black/letterboxed and the other half contains a usable scene colour, the ambient palette now mirrors the valid half rather than mixing it with transparent black. Both-black frames still preserve the prior palette. This is source implementation only; compile, native playback and device appearance remain unverified.

- `SCHEDULE AND NOTIFICATIONS IMPLEMENTATION`: Mobile day strip density, day accessibility labels, open-episode card labels, 48dp reminder touch targets and neutral status tags. Notification cards soften unread outlines and refine mobile poster spacing while retaining TV layouts. **Source only; compile/device verification pending; milestone APK builds remain manual.**

- `POPUP GLASS SAFETY/THEME PASS`: Cancelled/dismissed popup PixelCopy callbacks no longer resume dead continuations, failed snapshots are disposed, and low-RAM/API-limited devices fall back to unblurred neutral sheet surfaces. The mobile sheet tint and Appearance preview now follow the active theme's glass palette. **Implementation pending compile, physical device and accessibility acceptance.**

- `SETTINGS SWITCH SEMANTICS / LIGHT THEME CONTRAST PASS` (`456b695`, `45c2754`): Shared Settings switch rows use Compose toggleable semantics to expose checked/unchecked state while preserving the same toggle callback on touch and TV remote. Unfocused handheld Settings row titles and descriptions now derive contrast from Material theme onSurface/onSurfaceVariant, rather than always-light TV palette; TV colour paths remain unchanged. Source implementation pending CI and real-device TalkBack, light/dark and remote validation.

- `REFERENCE SCREENSHOT REFINEMENT`: In Home Continue Watching the overflow now paints a compact 28dp translucent disc inside a 48dp action target rather than covering the 124dp poster with a full-size button. Portrait inline video omits the redundant Episodes toolbar glyph because the watch page immediately below has an episode browser; subtitles/cast/fullscreen/settings remain. No change to fullscreen transport, source handling or TV player. Source only; phone/TV test and subtitle clearance still to verify.

- `COORDINATED SIX-SCREEN REFINEMENT`: 24 source edits across signed-out My List, Home hero/Continue Watching, Details metadata/genres, Settings index, Downloads and Browse. Improves full-length titles, card density, compact layout and consistent neutral glass without altering data or player handlers. Missing patterns: none. No visual acceptance claimed; latest APK remains previously verified build until this batch is compiled.

- `BATCH 1 PLAYER IMPLEMENTATION (in progress, 2026-11-22)`: Compact player action dock now measures its rendered width rather than reserving blank space on narrow surfaces, with zero-width and cramped-width regression tests. The touch seek timeline now maps taps/drags to the inset visible track and guards non-finite gesture coordinates, with endpoint/invalid-input tests. Frame-reactive ambient sampling now excludes the subtitle-heavy bottom band and uses a bounded chroma-weighted colour extraction instead of a plain average; black/grey frames return no new tint, retaining the caller's existing fallback, with unit tests. All edits confined to the feature branch. **Not 50 major changes completed yet; Batch 1 remains in progress. Gradle compile, physical-device screenshots, rotation/subtitles/PiP and Android TV regression tests are unverified; no intermediate APK build triggered.**

- `BATCH 1 COORDINATED PLAYER LAYOUT PASS` (2026-11-22): Fullscreen primary/utility dock uses horizontal scrolling on cramped displays rather than shrinking 48dp action targets; compact inline player emphasizes Play/Pause and leaves skipping to its functioning seekbar/gestures, with vertical clearance from the timeline. Player header/scrim reduced, unknown-duration remaining time says LIVE, tablet labels collapse on short windows, frame-tint sampling defers bitmap recycling until a pending PixelCopy callback completes, and neutral Player settings sheets/drawers use consistent low-accent borders. Timeline visual endpoints now match gesture endpoints across drag animations and unknown-duration seeks are guarded. **Source committed on feature branch, not yet validated on actual phones/Android TV, tests/compile and APK build pending.**

- `BATCH 2 VISUAL PASS (2026-11-22)`: Coordinated neutral-glass refinement of Home spotlight scrims, rimmed hero pills and primary watch CTA, Details hero gradient, Browse filter chip selection, floating navigation neutral selection halo and adaptive width, plus shared glass surface and header frost translucency. Feature branch only; no new APK build triggered. Source is pending compilation and visual acceptance, and Batch 1 release verification remains separate.

- `BATCH 2 RESPONSIVE/BEHAVIOURAL FOLLOW-UP`: Home spotlight indicator shows a bounded seven-dot window instead of overflowing on large featured sets; narrow floating navigation hides only the visible caption while retaining its accessible route name; Details tabs expose actual tab selection via Compose selectable semantics and facts ellipsize within a bounded width; Browse pagination avoids invoking load-more from an empty grid and column calculations tolerate zero/negative available dimensions. New hero indicator regression coverage added. **Code implemented, not visually accepted; no APK build from this follow-up yet.**

- `BATCH 2 UNIFIED BROWSE SELECTION`: Remaining regular Browse filter chips (active tags, options and filter sheets) now use the shared neutral `glassSelectionColor()` instead of vivid theme-accent fill. Exclusion/error markers preserve their semantic warning colours. Floating nav caption visibility is isolated as a reusable width policy; phone multiwindow zero-width grid behavior has regression coverage. No physical-device visual acceptance claimed.

- `BATCH 2 INTERACTION/A11Y FOLLOW-UP (2026-11-22)`: Phone Home spotlight indicators now provide 28dp interactive targets and accessibility-labelled direct page selection, while TV indicators remain noninteractive; a single-hero carousel no longer shows a meaningless one-dot pager. Details sticky tab pills reserve at least 48dp height. Browse Reset is context-sensitive on mobile (preserved on TV), and the clear-search icon gets a 48dp target. No playback/controller code or TV navigation path changed. **Code-only; physical-device, CI, and screenshot comparison remain pending.**

- `BATCH 2 ACCESSIBILITY + REGRESSION CHECKS`: Overview's expandable description now exposes Expanded/Collapsed state to screen readers; Browse removal chips avoid duplicate spoken Close-icon labels; hero carousel's sliding dot-window has additional boundary and compact-count unit coverage; navigation caption breakpoints cover degenerate widths. All are source-level changes only until CI passes and screenshots are reviewed.

- `APPROVED SIX-SCREEN ALIGNMENT FIX (2026-11-22)`: Home's mobile hero now starts BELOW (not behind) its header and category chips; hero inset isn't counted twice, portrait hero has a stable compact height, centered equal-width Watch/Details CTAs and reserved pager-dot clearance. TV hero rendering and navigation stay unchanged. My List empty watchlist gets a centered useful state; Downloads empty panel is centered above the navigation and carries an offline subtitle; Settings category cards use uniform minimum height; shared title bars remove their distracting full-width rectangle border. No fullscreen or portrait player files changed. Source only; device visual testing outstanding.

- `BATCH 3 START — SECONDARY SCREENS (2026-11-22)`: Schedule empty-day notice is now a centered neutral glass panel, selected day strip gets a readable accent rim and selection semantics, month calendar day has spoken date and selected semantics plus neutral selected episode-count contrast. Notifications reserve floating-capsule clearance and use neutral filter selection. Countdown uses compact typography/padding. No player edits. Build and device testing pending.

- `BATCH 3 SECONDARY SCREEN COMPLETION PASS (2026-11-22)`: Notifications use neutral unread rims, correct Open-title semantics only on actionable cards, and shared frosted date/badge treatments; Schedule date strip is tighter without reducing the 48dp+ hit height, and the empty-day glass card is more compact. Shared centered confirmation/modals use balanced handheld padding and neutral rims. Search/filter dropdowns share 20dp rounded shapes; shared error cards cap the empty/error layout to the available viewport and expand to the full usable phone width. Existing functionality, account operations, remote focus and player code not modified. **Batch 3 secondary-screen implementation pass complete in source only; device acceptance, CI and APK verification remain pending.**

- `BATCH 4 START — SHARED UI POLISH (2026-11-22)`: Unified 1dp luminous rim instead of two stacked strokes on every lazy poster/card; softened floating nav shadows and constrained negative bottom inset for unusual window states; removed the redundant no-op title-bar modifier; card screen-reader detail actions now announce the anime title, and shared search fields announce the current query while retaining the 56dp input and 48dp clear target. No player files changed. This is an opening Batch 4 implementation pass, **not** final screenshot/device acceptance.

- `BATCH 4 RESPONSIVE COHESION PASS (2026-11-22)`: Downloads category selection scrolls horizontally on narrow phones without clipping filters, and its category/episode chips use neutral glass fills; Settings incomplete last category row centers with consistent tile widths without changing TV's grid; Details selected tab rim uses neutral on-surface; My List status/custom-list chips share neutral glass selection. No player work. Source changes awaiting current CI and device/screenshot acceptance.

- `BATCH 4 SCREENSHOT ACCEPTANCE PASS (2026-11-22)`: Reviewed user's latest Home, Browse, Details, Settings, Schedule screenshots. Home spotlight now strengthens the lower gradient over bright art without hiding the subject; row titles truncate safely rather than stealing the right-side action, and grid/rail buttons use compact labels to avoid the floating shortcut disc. Detail cover relies on one shared glass rim rather than a second stroke; related section headings are compact and truncation-safe. The bottom nav shadow and no-wallpaper accent bloom are softer to reduce OLED haze. Existing padding-based scroll clearance is retained so content can scroll above the floating capsule; fullscreen/portrait player intentionally excluded. Actual device acceptance and final CI remain distinct checkpoints.
