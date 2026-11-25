# Native provider test (Anitaku)

This experiment reuses AnivexaClient, ProviderCatalog, existing episode rows and the native player. There is no extension engine. It is disabled by default.

## Configuration

Set both of the following Gradle properties (or equivalent environment variables) locally for an authorized test build:

| Gradle property | Environment variable |
| --- | --- |
| anitakuEndpoint | ANITAKU_ENDPOINT |
| anitakuTestCatalog | ANITAKU_TEST_CATALOG |

The endpoint must be an operator-approved HTTPS API endpoint, without credentials, query or fragment. The catalog is a single-line JSON object keyed by AniList ID. Each row contains explicitly confirmed positive integer episode numbers for `sub` and/or `dub`. Do not use AniList's episode count as a substitute for availability. Example shape only (not verified content): `{"123":{"sub":[1,3],"dub":[2]}}`.

These values are compiled into the APK and must not contain secrets. Missing/invalid configuration disables the provider. Keep the catalog restricted to authorized test episodes. Automatic catalog discovery is not implemented.

## Checks

- `./gradlew :app:testMobileDebugUnitTest --tests '*Anitaku*'`
- `./gradlew :app:assembleMobileDebug`
- Install the debug APK alongside stable (debug application ID suffix).
- Confirm the provider is absent with blank configuration and for unlisted titles.
- With approved configuration, select a listed episode and the Anitaku test server; verify playback, subtitles, sub/dub separation and fallback on unavailable streams.
- Verify ordinary providers and Watch still work; inspect diagnostics on errors.

CI without configuration covers compilation and fixture tests, not live availability or device playback. Do not merge or release this experiment until those checks pass.
