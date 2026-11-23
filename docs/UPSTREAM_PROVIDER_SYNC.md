# Upstream streaming-provider change reviews

Anilumos stays independently branded while tracking provider-site changes made in [AniLife](https://github.com/anilifecommunity/android-and-tv).

## Automated review

The GitHub Actions workflow [provider-sync.yml](../.github/workflows/provider-sync.yml) lives on both `main` and `feature/unified-glass-ui`. GitHub runs schedules only from `main`. The monitor runs every Monday at 10:17 UTC and can also be started manually from Actions > Review upstream streaming-provider updates > Run workflow.

The workflow:

1. Fetches AniLife's `main` and finds the shared Git ancestor with the Anilumos feature branch.
2. Identifies upstream edits to site provider classes, selected extractors/resolvers, provider registration, and directly related tests.
3. Proposes those changes on `automation/upstream-provider-sync`, opening or refreshing a **draft** pull request targeting `feature/unified-glass-ui`.
4. Does not automatically merge, build an APK, change branding, or release anything.

If a provider was removed upstream, its removal appears in the proposed diff for review. A domain change or website update might need additional integrations outside the monitored paths.

## Before merging

- Read the provider diff for newly added sites, moved domains and removals; check security, legality and provenance.
- Verify transitive requirements: dependencies, app permissions, external service settings and source catalog registration. The workflow intentionally never copies these automatically.
- Preserve `com.anilumos.app`, app branding, the candle splash, player UI and all required AniLife/Morphe credits.
- Run `./gradlew :app:testMobileDebugUnitTest :app:lintMobileDebug :app:assembleMobileDebug --max-workers=2 --no-configuration-cache` and review any failures.
- Test affected providers on a physical device and check logins, source selection, playback, downloads and errors.
- Merge only reviewed changes into Anilumos, then run its separate APK workflow when appropriate.

## Notes

GitHub Actions must have permission to create pull requests for this repository (Settings > Actions > General > Workflow permissions > Allow GitHub Actions to create and approve pull requests). If it is disabled, the sync run will fail to open a PR until the repository owner enables it. GitHub may delay or disable scheduled Actions on inactive repositories. Review the Actions tab for failures.

This is **provider-code synchronization**, not a live remote feed; app users receive changed provider logic only after a vetted new APK is built and installed.
