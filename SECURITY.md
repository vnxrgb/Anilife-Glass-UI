# Security Policy

## Supported versions

Anilumos has a published [v1.0.0 stable release](https://github.com/vnxrgb/Anilumos/releases/tag/v1.0.0). Security fixes are targeted at the latest supported Anilumos release; older builds and preview APKs are not guaranteed to receive fixes.

Anilumos is an independent fork of [AniLife](https://github.com/anilifecommunity/android-and-tv). Report vulnerabilities affecting Anilumos here, not as if they were official AniLife releases.

## Reporting a vulnerability

Please use a private [GitHub Security Advisory](https://github.com/vnxrgb/Anilumos/security/advisories/new) instead of opening a public issue.

Include the affected release or commit, device and Android version, steps to reproduce, and a description of the impact. Share only the smallest useful diagnostic sample, with account tokens, cookies, private stream URLs, watch history, and personal identifiers removed.

Reports will be reviewed privately, and disclosure should be coordinated so users can receive a fix before technical details are published. Response and fix times are not guaranteed.

## Exposed credentials

Treat any credential committed to Git or printed in public CI logs as compromised. Revoke or rotate it at its provider, update the relevant GitHub Actions secret, and invalidate affected sessions where possible. Never paste the exposed secret into a public issue or pull request.

The Anilumos APK must not include OAuth client secrets, Discord bot tokens, user credentials, the Android signing keystore, or the update-manifest private key.

For maintainers: see the [incident-response procedure](docs/security/incident-response.md).

## Upstream and third-party code

Anilumos uses code from AniLife, Morphe, and other dependencies. Their respective licenses and notices remain in place; see the [README](README.md) and [LICENSE](LICENSE).
