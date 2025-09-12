# CryptoTrader Handoff Bundle

This bundle contains all **handoff material**: Gradle wiring, contracts, stubs, fixtures, CI, and the design spec.

- See **docs/Design.md** for the full architecture and implementation plan.
- Tracks A–I are in **docs/Tracks.md** with acceptance criteria.
- Contracts (stable interfaces) live in **contracts/**.
- Fixtures for tests in **fixtures/**.
- CI runs `./gradlew ktlintCheck detekt test` via **.github/workflows/ci.yml**.

Local quickstart:
- Java 17, Android SDK (only needed for `:app`).
- Run checks: `./gradlew ktlintCheck detekt test -x lint`.

Generated: 2025-09-12
