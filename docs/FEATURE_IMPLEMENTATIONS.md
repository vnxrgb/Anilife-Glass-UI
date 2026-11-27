# Original AniLumos feature implementations

Part of the [AniLumos technical documentation](TECHNICAL_GUIDE.md).

**Source baseline:** [`6ebaead`](https://github.com/vnxrgb/Anilumos/tree/6ebaead4093a31b76a40bef0718827ca8b48167b) on `main`. This describes inspected source, not a new device-test result. Original additions are licensed under [MIT](../LICENSE-ANILUMOS) only to the extent owned by vnxrgb; inherited and third-party components retain their terms. See [COPYRIGHT.md](../COPYRIGHT.md).

## Afterglow

**Purpose:** make the end of a completed anime a deliberate experience rather than an abrupt exit.

**Flow:** Media3's episode-end callback in WatchScreen checks the sleep timer, next-episode availability, autoplay, device mode and the Afterglow setting. The completion predicate requires declared status FINISHED and either a movie or a known positive episode count reached by the current episode. If there is no next episode, Afterglow opens on supported mobile layouts, outside PiP. This is not triggered simply by reaching the end of an arbitrary episode.

The dialog loads up to three recommendations via Recommendations.afterglow, with a seven-second timeout and an empty fallback on failure. The selected title seeds the recommendation engine; known titles and the seed are excluded. Actions open details or start playback of a suggested title, open the franchise guide where available, return Home, and offer rating and journal editing. Portrait and landscape layouts differ. The existing playback callbacks and recommendation infrastructure are reused.

**Code:** [AfterglowCompletionDialog.kt](../app/src/main/java/com/anilili/ui/watch/AfterglowCompletionDialog.kt) · [WatchScreen.kt](../app/src/main/java/com/anilili/ui/watch/WatchScreen.kt) · [Recommendations.kt](../app/src/main/java/com/anilili/data/recs/Recommendations.kt).

**Checks:** final episode/movie versus ongoing series, missing count, next episode present, sleep timer, PiP, disabled setting, missing recommendations. See [AfterglowCompletionTest](../app/src/test/java/com/anilili/ui/watch/AfterglowCompletionTest.kt).

## Anime Journal and personalization

**Purpose:** user-authored notes, reviews, ratings and favorite moments linked to an anime or episode, plus a floating shortcut and library.

JournalOverlay receives JournalContext (anime identity, title, episode, artwork and fullscreen state). It supports library browsing, editing, selection/deletion, grouped presentation, text sharing and image export. JournalStore exposes StateFlows and stores serialized entries in private Android SharedPreferences named anilumos_journal. Entries are keyed by ID; anime/episode lookups normalize episode labels. Empty drafts are not persisted as entries. Updates are sorted newest first. The store bounds entries to 1,000 and serialized length to 1,500,000 characters, and refuses writes when existing serialized data cannot be read. Legacy episode notes can be migrated on a successful save.

The floating shortcut has saved coordinates, enabled/minimized state and a position reset. The editable display name is trimmed, strips control characters and is limited to 48 characters; Journal and Wrapped share this name. Name inclusion in sharing defaults to off. Deletion and restoration update the same entry store. This is local storage, not a claim of encrypted cloud sync.

JournalImageShare draws a PNG on an Android Canvas using text layouts and available cover artwork, writes it to the app cache, and shares a FileProvider URI using Android's share sheet. The overlay also exposes Android's document creation flow for saving an image. Text sharing is separate. Artwork retains its original rights.

**Code:** [JournalStore.kt](../app/src/main/java/com/anilili/data/journal/JournalStore.kt) · [JournalOverlay.kt](../app/src/main/java/com/anilili/ui/journal/JournalOverlay.kt) · [JournalImageShare.kt](../app/src/main/java/com/anilili/ui/journal/JournalImageShare.kt) · [WatchScreen.kt](../app/src/main/java/com/anilili/ui/watch/WatchScreen.kt) · [ProfileScreen.kt](../app/src/main/java/com/anilili/ui/profile/ProfileScreen.kt).

**Checks:** note round-trip, normalized episode identity, empty draft, large input, delete/restore, missing artwork, image save/share, floating position and name privacy.

## AniLumos Wrapped

**Purpose:** present recorded viewing activity as a yearly summary and shareable artwork card.

wrappedStats filters positive daily counts from January 1 through the current local date, aggregates monthly totals, completed-episode counts and active days, and obtains the activity streak from the underlying activity helper. The screen uses history and activity data, displays metrics and uses the shared journal display name. It cannot infer activity that was never recorded.

WrappedExport renders an image locally and shares it through FileProvider. Its personality labels are deterministic, not AI-generated psychological assessments: rules include no activity, a streak of at least 14 days, at least 100 episodes with an average of four per active day, at least ten titles and five genres, and leading Slice of Life or Action genres. Rule order determines which label wins. The PNG is stored in the wrapped cache directory. Availability of artwork and history affects the result.

**Code:** [AnimeWrappedScreen.kt](../app/src/main/java/com/anilili/ui/profile/AnimeWrappedScreen.kt) · [WrappedExport.kt](../app/src/main/java/com/anilili/ui/profile/WrappedExport.kt) · [JournalStore.kt](../app/src/main/java/com/anilili/data/journal/JournalStore.kt) · [LibraryStore.kt](../app/src/main/java/com/anilili/data/library/LibraryStore.kt).

**Checks:** year rollover, no history, sparse activity, streak calculation, missing artwork, shared name and exported PNG readability.

## Mood Discovery

**Purpose:** make genre-oriented discovery more relevant than a generic random title.

The selected mood is mapped to a genre by the dice-source model. RandomAnimeViewModel creates a mood-specific deck using discovery requests with excluded genres and started-title filtering; personalized results provide additional candidates. MoodPolicy requires the selected genre, removes incompatible genres, excludes unreleased/cancelled titles, and checks strong non-spoiler tags (rank at least 65). For example, Slice of Life excludes Action, Horror, Thriller, Psychological and Ecchi, and rejects strong Gore, Tragedy, Bullying, Survival and Body Horror tags. Other moods have their own rules. Selection is persisted and the deck supports preload and skip behavior. Recommendations depend on catalogue metadata, which may be incomplete.

**Code:** [DiceSource.kt](../app/src/main/java/com/anilili/ui/random/DiceSource.kt) · [MoodPolicy.kt](../app/src/main/java/com/anilili/ui/random/MoodPolicy.kt) · [RandomAnimeDeck.kt](../app/src/main/java/com/anilili/ui/random/RandomAnimeDeck.kt) · [RandomAnimeViewModel.kt](../app/src/main/java/com/anilili/ui/random/RandomAnimeViewModel.kt) · [RandomAnimeUi.kt](../app/src/main/java/com/anilili/ui/random/RandomAnimeUi.kt).

**Checks:** [MoodPolicyTest](../app/src/test/java/com/anilili/ui/random/MoodPolicyTest.kt), incompatible genre, contradictory tag, empty deck, network failure and repeated picks.

## Personalized recommendations

This extends existing AniLife recommendation foundations rather than claiming to originate the entire recommendation engine. Recommendations.forYou combines local neighbour-graph results and taste signals from ratings, played titles and feedback with hybrid ranking; known and signal titles are excluded from suggestions. The graph is cached; missing graph data can yield an empty local candidate list. AniList card hydration batches IDs (up to 50 per request as documented in source), preserves ranking order and applies the hide-adult setting. Afterglow uses a title-specific seed; dice uses a shuffled sample from the For You pool. Additional community metadata, including Tenrai, is integrated as documented in the [data guide](DATA_AND_RELEASES.md). No guaranteed match or complete coverage is implied.

**Code:** [Recommendations.kt](../app/src/main/java/com/anilili/data/recs/Recommendations.kt) · [MiruroRepository.kt](../app/src/main/java/com/anilili/data/MiruroRepository.kt) · [TenraiParsing.kt](../app/src/main/java/com/anilili/data/remote/TenraiParsing.kt).

## Franchise watch order

AniLumos adds dedicated franchise-guide presentation and curated route integration on top of the existing series relationships. WatchOrder builds grouped entries; CuratedWatchOrders loads community JSON and Anime Metadata DB index/series data, associates routes using AniList IDs, records optional steps and rejects routes with fewer than two entries. Source responses are cached in the franchise-orders cache directory with an 8 MiB size bound; remote failures are handled without assuming curated data exists. Routes can be labeled by source; absent routes must not be represented as authoritative chronology. Completion and next-unfinished presentation use existing watch progress.

**Code:** [WatchOrder.kt](../app/src/main/java/com/anilili/ui/detail/WatchOrder.kt) · [CuratedWatchOrders.kt](../app/src/main/java/com/anilili/ui/detail/CuratedWatchOrders.kt) · [DetailScreen.kt](../app/src/main/java/com/anilili/ui/detail/DetailScreen.kt).

**Credits:** [Anime Watch Order API](https://github.com/Bas1874/Anime-Watch-Order-Api), based on r/anime community guides; [Anime Metadata DB](https://github.com/michael-freling/anime-metadata-db), ODbL/DbCL. Their data is not covered by the AniLumos MIT grant.

## Guided tour and Hidden Sparks

WhatsNewTour extends the inherited walkthrough with versioned steps and registered UI targets for Journal, personalization and franchise navigation. It records completion, waits for target positioning and provides a fallback when targets are missing. HiddenSparks is a separate local feature that receives events such as episode completion, theme changes and deliberate interactions, then publishes short messages with sequence IDs. The implementation tracks occurrence conditions and avoids assuming a message must appear on every interaction. Discovery triggers are intentionally not exhaustively listed here.

**Code:** [WhatsNewTour.kt](../app/src/main/java/com/anilili/ui/tour/WhatsNewTour.kt) · [HiddenSparks.kt](../app/src/main/java/com/anilili/ui/easteregg/HiddenSparks.kt).

**Checks:** first-run and reopened tour, missing target, theme readability and message lifecycle.
