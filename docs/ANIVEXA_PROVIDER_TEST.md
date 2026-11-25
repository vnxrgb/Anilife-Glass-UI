# Additional Anivexa providers

AnimeNoSub and AnimeDunya share a native HTTP adapter. Episode availability comes from the provider's reported episode list, not AniList's episode count or a manual manifest. AnimeDunya is treated as sub-only. The existing playback path is reused for direct HTTPS HLS, MP4 or DASH streams and WebVTT captions.

Configure `ANIVEXA_PROVIDER_ENDPOINT` (or Gradle property `anivexaProviderEndpoint`) with an operator-authorized HTTPS Anivexa API deployment. An empty or invalid value leaves both providers unregistered. No public endpoint is embedded. The deployment must implement the documented Anivexa episode and watch response contracts.

Before distributing an APK, confirm access authorization, compile and execute the mobile unit tests, verify catalog and playback using authorized test media on a device, and check existing provider regressions. This branch is an experiment and is not merged into main.
