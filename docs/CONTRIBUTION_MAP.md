# Contribution and licensing map

Part of the [AniLumos technical documentation](TECHNICAL_GUIDE.md).

**Source baseline:** [`6ebaead`](https://github.com/vnxrgb/Anilumos/tree/6ebaead4093a31b76a40bef0718827ca8b48167b) on `main`. This describes inspected source, not a new device-test result. Original additions are licensed under [MIT](../LICENSE-ANILUMOS) only to the extent owned by vnxrgb; inherited and third-party components retain their terms. See [COPYRIGHT.md](../COPYRIGHT.md).

## Why this exists

A feature may contain an original UI, original integration glue, inherited application infrastructure and external data or libraries. A feature name alone is not proof of ownership of the entire implementation. This is a functional provenance map, not a legal determination of every line's copyright. Consult [COPYRIGHT.md](../COPYRIGHT.md), [NOTICE](../NOTICE), [LICENSE](../LICENSE), and [LICENSE-ANILUMOS](../LICENSE-ANILUMOS).

| Feature | AniLumos-specific contribution | Existing or third-party foundation |
| --- | --- | --- |
| Afterglow | Completion dialog, recommendation presentation and terminal-series workflow | Existing player, library, catalogue and taste/recommendation infrastructure |
| Anime Journal | Journal data model/store, editing/library overlay, PNG share and personalization | Android persistence/share APIs, existing anime identity and history |
| Wrapped | Summary presentation, activity aggregation, personality rules and PNG export contributions | Existing watch history and daily activity data, artwork rights |
| Mood Discovery | Mood deck integration, filtering rules and presentation | Existing discovery/catalogue and recommendation services |
| Optical Glass | UI integration, component presentation, theme/contrast refinements | Compose/Material3, Kyant Backdrop, inherited screens and settings |
| Ambient Home/details | Artwork-based UI treatment | Coil, catalogue artwork and inherited screens |
| Ambient Cinema | Player integration and ambient display adaptations | Morphe GPLv3 spatial rendering code, Media3, Android PixelCopy |
| Curated franchise guide | Curated route ingestion/presentation and progress integration | AniLife series relationship foundations, community and Anime Metadata DB data |
| Episode classifications and skip timing | Integration/presentation improvements | Existing AniSkip infrastructure, community timing/classification APIs |
| Up Next | Presentation/countdown and trigger refinements | Existing player/autoplay and episode advancement |
| Metadata diagnostics and caching | Diagnostic integrations and improvements | Existing repository/cache and metadata services |
| Guided tour and tablet polish | New walkthrough steps and layout refinements | Inherited tour, Compose and adaptive layout foundations |
| In-app updates | AniLumos release-channel selection and integration | Existing update/install verification infrastructure |
| Branding | Independently owned name/logo/artwork where applicable | Original asset provenance; trademark rights not granted by software MIT license |

The above describes implementation boundaries from the inspected source and project documentation. It does not claim all code in linked files was authored by vnxrgb. For exact attribution, inspect file history, original headers and upstream diffs; obtain permission where required. The MIT license covers only the eligible original software and documentation contributions owned by vnxrgb. Standalone branding and artwork are excluded unless separately designated. Existing AniLife terms and applicable redistribution permissions remain necessary. Morphe-derived code retains GPLv3 and required notices; the MIT grant does not resolve licensing compatibility of the combined app.



## For future additions

Record the feature's purpose, source files, data flow, original contributions, reused components, external licenses, failure behavior and validation. Keep third-party notices intact, and update this map as features merge into main.
