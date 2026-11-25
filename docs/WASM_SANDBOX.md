# Android WASM provider sandbox (experimental)
This branch adds the first fail-closed boundary, not a working WebAssembly runtime. Existing native providers and playback are untouched. No external provider binary is loaded or distributed.

- Separate nonexported Android isolated service; no inherited application permissions. Application initialization skips isolated UIDs, including older Android versions.
- Validate the component header and authenticated expected SHA-256 before execution. Header validation is not full component validation.
- HTTPS host policy is independent of guest metadata. Future HTTP gateway must additionally block private/reserved DNS results, validate every redirect and actual connection address, prevent credential leakage, bound response size/time and enforce per-call quotas.
- Implement JNI Wasmtime Component Model integration only after Android ARM64 build/ABI proof. Keep ARMv7 native fallback.
- Support both source WIT contracts using separate versioned adapters; never assume same package version implies equal ABI.
- Community provider candidates are not enabled pending license, source review, ABI validation and playback tests with authorized content.
- No arbitrary module import or unsigned remote code. A verified signed manifest is required before remote updates; hashes alone do not authenticate publishers.
- Next: native runtime, bounded Binder IPC via file descriptors for large payloads, mediated networking, cancellation and resource limits, fixture component tests, then provider integration.
References: https://github.com/sanilili/desktoprepo1/blob/main/crates/core/src/plugin.rs and https://github.com/anilifecommunity/kotlindesktop/blob/main/creating-extension.md .

Second batch: dedicated HTTP gateway with public-address DNS validation, no inherited proxy/cookies/account auth, explicit header allowlist, response limits, request quotas and fail-closed redirects. Not wired to guest execution yet. Android CI runs on this branch. Redirect compatibility and total invocation budget still need integration tests.

Third batch: native Wasmtime component compilation/instantiation probe, JNI entry point and separate host/Android ARM64 CI proof. Empty import linker deliberately denies all host capabilities; real provider execution, WIT adapters and IPC remain disabled. Native library is not packaged until compile proof and isolation tests pass. Fixed Kotlin DNS interface compilation error from Android CI 45614322682.

Fourth batch: separate typed desktop/community WIT worlds and deny-by-default metadata inspection adapter. This deliberately does not link WASI imports or enable provider network access; real compiled modules may require restricted WASI before metadata inspection succeeds. ABI compile and fixture checks remain pending. WIT snapshots are interface specifications from the linked repositories; no third-party provider code or binary is bundled.

Fifth batch: enable Wasmtime anyhow integration (fixes native error conversion), link restricted default WASI Preview 2 for metadata inspection, add a fixture-only isolated Binder transaction, and test signature verification against a caller-pinned RSA public key. No production signing key is configured, no remote manifests are accepted, and no arbitrary module bytes cross IPC yet. Real provider execution and broker integration remain pending. CI must confirm the adapter and Android native target.

Sixth batch: both WIT adapters now have typed episode execution and a shared injectable HTTP broker contract. Modern metadata (subtitles, skip times and thumbnails) is retained; legacy optional fields are empty. The broker is installed only after instantiation. Android Binder/JNI broker and result validation are still required, and this code has not passed CI. No external modules are enabled.
