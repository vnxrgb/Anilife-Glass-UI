# Manga v2 — isolated engine research

Base: stable main 4455209715c1442416d612461e0eebea9ec15052. No app dependency, no new app permission, no APK publication. UI and reader remain on backup/manga-ui-reader.

## Reference architecture

Kuroha at 394d2b5719f94a5db3a663fee4702241ea3f82bb declares parser revision 3432a85ac0. The original fork endpoint redirects; canonical Kotatsu-Redo contains exact commit 3432a85ac02ce99824cf841aca40bfd3af78ed3f. JitPack resolution still needs CI validation. Its contract is ParsersApi.VERSION 1, Kotlin 2.2.10, OkHttp 5.1.0, Okio 3.16.0. The standalone module uses API 26 without raising the app minimum or altering anime networking.

## First checkpoint

- Built-in descriptor registry, stable source names, separate broken flag (not live health).
- Source-scoped HTTPS-only session, no automatic redirects or shared credentials; bounded optional HTTP cache and in-memory cookies.
- Android parser host with explicit unsupported-operation failures rather than silent null results; default configuration.
- Provider-native browse/search, catalog raw-offset accounting, details and chapter/page references for reviewed sources. Explicit SAFE rating only; unrated is not verified safe. No bulk live tests.
- Unit tests for compiled contract, source scope, session isolation, empty-page accounting, and synthetic catalog-to-page requests including filtered pagination and out-of-scope image rejection. Dedicated CI compiles this module only.

## Remaining gates

1. Resolve pinned dependency and pass CI. Expand the synthetic fixture tests to actual image delivery; obtain permission for representative sources and test on Android. Differentiate empty/filtered catalogs, network failure, unsupported operation, access restrictions and rating metadata.
2. Implement authorized page delivery, source-aware headers, cache, permitted redirects and normal image handling for approved sources. Assess legitimate browser interaction and persisted per-source settings. Access-control bypass is not part of this project checkpoint.
3. Determine upstream GPLv3 and AniLife distribution compatibility before linking module to app. This module is not yet packaged with AniLumos. Keep notices; Kuroha's own permission does not replace underlying rights.
4. Only then integrate backed-up UI and reader via neutral models. Downloads for permitted content after reader tests.
5. Evaluate separately signed parser bundles: own publisher keys, contract/version matching, validation, rollback, trusted update endpoint and device tests. No remote executable loading at this checkpoint.

Do not report provider count as working count. No signed beta until actual reader validation and licensing gates are satisfied.

## Provider health checkpoint

An explicit reviewed-scope probe checks browse, search, details, chapters and first page bytes. Results expire after 15 minutes; unchecked and failed sources are hidden by the visibility predicate. No UI integration, automatic bulk probes, live provider certification or device image decoding yet. Restricted responses remain blocked. Synthetic tests cover state isolation and expiry.

## Image validation checkpoint

Image delivery now accepts at most three same-origin HTTPS redirects within the reviewed scope, rejects loops and cross-origin redirects, and checks raster signatures against declared media types. AVIF support is conservative (major brand avif/avis). Full device decoding, provider-specific headers, authorized live checks and licensing remain pending.

## Reviewed request defaults

Optional per-host Accept and Accept-Language defaults preserve parser-supplied headers and do not leak across hosts. Credentials, identity spoofing and access bypass are not supported by the configuration. No real provider profiles have been enabled. Android device tests and licensing remain pending.

## Android decoding and foreground refresh

Bitmap decoding uses a sampled raster budget of four million pixels and caller-owned bitmaps. Health readiness now requires Android decoding, not only valid headers. Reviewed profiles can be refreshed serially on demand with a 15-minute minimum interval, including failures. No background scheduling or real profiles are enabled. The local Android instrumentation fixture requires an emulator/device and :manga-engine:connectedDebugAndroidTest. JVM sampling and refresh tests run in existing CI. No app integration or licensing clearance is claimed.

## Device CI gate

The branch-only workflow now includes a separate Android API 30 emulator job after the JVM engine job, running connectedDebugAndroidTest and retaining reports. Local synthetic fixtures only; no live provider access or app packaging. A successful emulator job is required before claiming device decoding validation.

## Opt-in live source validation

Local CI including Android emulator decoding passed in run 45970972628. To test a real provider, manually dispatch Manga v2 engine contract on feature/manga-v2 with live_source (exact parser ID), live_hosts (reviewed exact HTTPS hosts), live_title (exact authorized title), and live_authorized=true. The instrumentation test checks a catalog response, exact safe search match, details, first chapter, first page and Android decode. No arbitrary fallback title, no bulk probe, no download retention, and no credentials. Ordinary push runs skip this test explicitly. Debug engine builds alone request INTERNET for the test; app remains unlinked. A green fixture run is not a live-provider pass. Root AniLife license is source-available and restricts redistribution; written rights and GPL compatibility must be established before packaging this module with the app.

## Atsu one-shot metadata check

Pinned parser ATSUMOE is present. The push commit named `Run one-shot Atsu metadata validation` opts into an Android instrumentation test checking catalog and exact safe Lookism search on the main host only. No chapter, image, or CDN requests are made by this check. Subsequent ordinary commits skip it. Reader validation still requires a specifically authorized title and chapter; local synthetic image tests are separate.

## Atsu one-shot Chico reader check

Operator confirmed permission for automated retrieval of Chico chapter 3. The push commit `Run one-shot Atsu Chico chapter 3 validation` enables only the exact safe Chico title, chapter number 3, and first page decoded on Android using the reviewed Atsu main and CDN hosts. An ambiguous title, missing chapter or access restriction fails without fallback. If several chapter-3 uploads exist, the parser's first matching entry is used. No image retained. Ordinary pushes skip the live check; workflows queue instead of cancelling in-progress checks. This does not establish distribution permission for the app or other works.

## Image compatibility retest

Run 45972277365 passed Atsu metadata; run 45972637735 reached Chico chapter 3 first-page decoding but failed BitmapFactory dimension inspection on API 30. AVIF is a plausible cause, not yet measured. Advertise only supported common image formats on older Android and report detected image format, byte count and API on bounds failure without storing content. The one-shot authorized retest uses API 34 (native AVIF support), ordinary CI remains API 30; minSdk remains unchanged. A modern-device pass does not establish old-device AVIF compatibility. No access restriction handling has changed.

## Legacy AVIF checkpoint

Run 45973630287 verified the authorized Chico chapter 3 first page on Android 14. This checkpoint adds pinned org.aomedia.avif.android:avif:1.3.0.841110fd (upstream BSD-2-Clause, minSdk 21) to the isolated module only. On API <31 use libavif for AVIF, and use it as fallback when the platform decoder fails on newer devices. Serialize native fallback decoding with one native thread, cap input at 16 MiB, original AVIF dimensions at 16384 per axis and 16 million pixels, output at four million pixels; the original-source cap is deliberate because native intermediates may be full-sized. Larger AVIF pages fail rather than allocating without bounds. Native ABI coverage, memory on physical devices, dependency notices and distribution approval still need review. Synthetic blue AVIF fixture is generated locally and tested on Android, alongside a one-shot authorized Chico chapter 3 retest using API 30. No permission bypass or other content access. No app integration. API 26–29 actual device testing remains pending.

## AVIF dependency transport

Run 45974418150 stopped at dependency resolution after three HTTP 429 responses from repo.maven.apache.org. The exact pinned release exists on Maven Central. Route only the libavif module through upstream's documented official repo1.maven.org endpoint. Do not change artifact version or general repository routing. Synthetic 16x16 blue fixture was independently decoded locally (RGB 0,0,253). Retry the API 30 one-shot test; native ABI and runtime behavior still require CI verification.

## Bundle-wide offline compatibility inventory

Run 45975226273 passed live Chico chapter 3 first-page decode and local AVIF fixtures on API 30. The new offline audit enumerates the entire pinned bundle, classifies upstream broken and unsupported categories, and attempts parser initialization and declared search/sort capability inspection using a session that rejects every network request. CI uploads inventory.tsv and summary.txt with the engine test report. No claim of live availability, rights, or support for every architecture is implied. Visibility requires an initialized parser, explicit reviewed ID and fresh READY health. Reviewed profiles and manual live instrumentation now require an exact title and chapter number, and fail on ambiguous titles. No new live provider is enabled by this change; authorized representative sources and fixtures for distinct architectures remain necessary. No automatic bulk probing.

## First bundle inventory and browse-only capability

Actual CI artifact from run 45976000285: 1,386 descriptors, 719 initialized, 430 marked broken upstream, 237 excluded category (classification precedence: broken first), zero initialization failures. Of the initialized parsers, 680 declare search and 39 do not. The health probe now selects the exact approved title via search if supported, or from the first catalogue page for browse-only parsers. An entirely filtered catalogue page need not prevent a separate safe exact-title search. Missing and ambiguous approved titles are distinct outcomes. Shared selection is used by generic manual device tests. No unbounded crawl or title fallback. Synthetic fixtures cover browse-only, missing, ambiguous and filtered cases. Actual live availability of the 39 browse-only parsers is unknown; no new sources or requests enabled. Atsu remains the sole live-tested provider. Browser execution, transformed images, licensing and multi-architecture live validation remain open.

## Provider filtering and reader presentation checkpoint

Add native genre, exclusion, category and publication-state filters using provider-declared options and capabilities, rejecting foreign tags, unsupported combinations and unsupported sorting. Keep SAFE-only postfiltering. Extend offline inventory with combined-search and genre capability flags. Add source-local split-translation preference; retain disabled challenge interception, suspicious content and background update checking. No domain, user agent or server override. Add bounded reader-side crop and rotation of already decoded authorized raster data, tested on a synthetic bitmap. These are not parser image reconstruction; arbitrary browser JavaScript and parser image transformation remain explicitly unsupported pending legitimate use cases and security review. No new provider is enabled and no live checks are scheduled. UI integration, rights clearance and representative live testing remain pending.

## Stage-aware provider diagnostics and distribution gate

Health records now distinguish initialization, title selection, details, chapter lookup, page list, page download and image decode. Decode failures no longer masquerade as network errors. Restricted responses record HTTP status, and unsupported host hooks record a typed browser or parser image transformation feature. Reports expose only state, stage, status, feature and timestamp, never response contents or exception messages. Synthetic tests check isolated failures, cancellation, diagnostic redaction and readiness. This does not implement arbitrary website JavaScript or protected image reconstruction, and no new live source is enabled.

Verified at exact pinned revision 3432a85ac02ce99824cf841aca40bfd3af78ed3f: Kotatsu-Redo LICENSE is GNU GPL version 3. The AniLumos root LICENSE retains AniLife Source-Available License 1.0, including redistribution restrictions and written-approval requirements. The current research branch deliberately does not link the parser dependency to :app. Before distribution of a combined APK, obtain an appropriate licensing grant or a GPL-compatible integration and distribution arrangement reviewed by qualified counsel; merely licensing original contributions MIT does not relicense upstream code. Separate provider content rights also apply. No release or integration authorization implied by this checkpoint.

## Reader integration preparation

The project owner reports having permission from both upstream developers (AniLife and Kotatsu-Redo). Scope, contributor coverage and written terms have not been independently inspected; retain the relevant grant and applicable upstream notices before combined distribution. This statement does not confer rights to third-party provider content.

Preserved UI baseline: backup/manga-ui-reader at aa3ec2bfb82d7347649f868c765c427e89884c0c. MangaScreen.kt, MangaReadingStore.kt, ReaderPreferences.kt and ContentMode.kt are intact. Do not copy the deprecated native source, raw URL image loader, old health registry or provider management back into the app. The internal ReaderSessionBridge prepares catalog pagination, metadata, chapter and page handles for a future Compose adapter, returning decoded bitmaps through scoped PageDelivery rather than passing remote URLs and headers into Coil. Foreign handles and closed sessions are rejected. The UI must explicitly dispose sessions/bitmaps, sanitize/render descriptions as text, provide scoped cover loading, re-resolve saved IDs, and map filter options, collections and gesture navigation. No source or app dependency restored yet. Synthetic bridge tests use offline sessions, so this does not assert live compatibility beyond prior checks. No release, merge or app changes.

## Local-only Compose restoration checkpoint

Restore preserved Home/Browse/My List, detail and reader design, reading preferences, bookmarks, local ZIP/CBZ import, ambient and magnifier UI. Restore mobile/tablet edge hold-and-swipe to switch modes, leave TV/PiP in anime mode, and hide the downloads tab in manga mode. No old provider registry, automatic scraping, provider settings or HTTP image loader restored; local images only, no online provider activation. Existing saved remote entries are left on disk but not rendered in this local checkpoint. Engine bridge still isolated: mobile minSdk 23 and TV 22 versus engine 26; integration must use a deliberate variant/API-compatible strategy without silently raising stable support. Added app compile and local unit-test CI, no APK upload or release. Physical-device gesture and UI behavior unverified. Owner reports upstream permissions; terms and provider rights remain separate.

## Mobile minimum SDK and dependency checkpoint

Owner approved Android 8.0 (API 26) minimum for mobile/tablet on feature/manga-v2. Default/TV minSdk remains 22 (Android 5.1). Add :manga-engine only to mobileImplementation; TV has no parser engine dependency. Restore pinned Telephoto 0.16.0 from the backup branch for the shared Compose reader and use existing AndroidX HtmlCompat rather than adding a Jsoup dependency. This addresses compile errors from run 45980088724. Mobile dependency wiring does not yet activate online providers or connect internal engine session handles to the Compose reader. No release or main changes; verify mobile compilation, device tests and eventual TV compile. Older mobile Android 6/7 installs can keep their existing version but cannot install this future variant.

## Mobile engine connection — reviewed sample

The mobile-only adapter now connects the restored Compose reader to a public narrow ApprovedReader contract in :manga-engine. The only reviewed live profile is ATSUMOE / Chico chapter 3 / atsu.moe and cdn.atsu.moe. An explicit foreground Check reviewed sample action performs title/chapter resolution and verifies the first decoded page before exposing Atsu in the source selector. No background network, bulk scans, browser challenge or other provider activation. The bridge exposes only display metadata, chapter and page index, and bounded bitmaps; no remote URL or headers enter Coil. Page/ambient loading, pinch zoom, reader filters and session cleanup are integrated; local archive support remains. TV has a stub and no engine dependency. Saved sample IDs are retained but reopening after screen disposal requires the explicit sample check. Online covers are intentionally absent pending scoped cover delivery. Only one authorized chapter is offered; no general catalog certification or distribution authorization claimed. CI and physical-device validation pending.
