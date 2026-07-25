# Changelog

All notable Metabolic Coach package changes are recorded here.

## v0.2 — 2026-07-25 — Nightscout integration

### Added

- Nightscout as the only active Version 1 glucose provider, with asynchronous current/history
  retrieval, normalized trend and delta, typed provider-state flow, bounded retry, conditional
  requests, response-size limits, and retained cache.
- Phone-only multi-server Nightscout settings with one explicit active server, isolated source
  histories and observations, HTTPS enforcement, configurable polling/timeout/retry, and no
  automatic failover.
- MockWebServer and unit coverage for JSON parsing, redirects, connectivity failure, retry,
  caching, server switching, exact-source repository routing, and runtime cache erasure.
- Versioned five-file ZIP packaging for repeatable phone, Wear, and watch-face test handoff.

### Changed

- Provider selection now migrates every legacy mode to Nightscout while retaining the normalized
  `GlucoseProvider` abstraction and Hilt registry for future xDrip, Health Connect glucose,
  CareSens partner, Dexcom, Libre, or other adapters.
- Coaching, follow-up observations, and Wear synchronization consume normalized
  `GlucoseReading` data and cannot depend on or mix Nightscout-specific server data.
- Background refresh requires connectivity, remains cancellable, and yields to local
  data-erasure/command operations without crossing their safety boundary.
- Direct CareSens communication, Bluetooth reverse engineering, and xDrip broadcast reception are
  excluded from Version 1.

### Verification

- The repository's complete static pipeline executes all selected tasks, including model, domain,
  data debug/release, sync, phone, and Wear JVM tests; debug lint and assembly; Android
  migration-test source compilation; WFF v4 schema and memory checks; APK signature/certificate
  validation; and exact five-file ZIP packaging.
- Nightscout tests use synthetic responses and MockWebServer and do not contact the configured
  personal server.
- Independent architecture review returned `CLEAR`; independent code review returned `APPROVE`
  with no remaining findings.

### Known acceptance gaps

- `v0.2` is a debug-signed engineering milestone unless its embedded `INSTALL.md` explicitly
  identifies a signed release build.
- Live Nightscout compatibility and latency, Android instrumentation, physical phone/Galaxy Watch8
  behavior, production signing, and store/privacy review remain required.
- Additional coaching work is frozen until the phone-first Nightscout live acceptance gate passes.

## v0.1 — 2026-07-24 — Infrastructure

### Added

- Modular Android companion, touch-first Wear OS application, and resource-only Watch Face Format
  v4 package.
- Clean Architecture, MVVM, repositories, Hilt, Coroutines/Flow, Room/DataStore persistence, and
  shared normalized domain models.
- Revisioned Wear Data Layer synchronization, offline command reconciliation, reset epochs, and
  exactly-once terminal command handling.
- Configurable coaching foundation, notifications, daily summaries, export, and
  confirmation-gated local erasure.
- Initial build, test, signing-verification, documentation, and APK handoff infrastructure.
