# Changelog

All notable Metabolic Coach package changes are recorded here.

## v0.7.1 — 2026-08-13 — Six- and twelve-hour local History windows

### Added

- The phone History screen adds fixed **6h** and **12h** selections before the existing 24h,
  7d, 14d, 30d, 90d, and custom choices.
- Both new choices resolve exact elapsed UTC half-open ranges and reuse the existing captured-source
  local Room query, deterministic gap-aware chart path, and stale-result generation gate.
- The last fixed-period preference restores the new choices while retaining a defensive 24h
  fallback for custom, missing, blank, unknown, or malformed stored values.

### Safety, scope, and verification status

- The APOS architecture review returned `GO` with no blocker and required this milestone to remain
  strictly local History/UI-state work.
- Numeric selected-period GMI remains unavailable for 6h and 12h. No GMI formula, eligibility,
  disclaimer, provider/current-reading, retention/backfill, Room schema, coaching, notification,
  Wear, or watch-face behavior changed.
- Phone, Wear, and watch-face metadata is aligned at versionName `0.7.1`, versionCode `13` for the
  deterministic package contract; the feature itself is phone-only.
- The forced local pipeline passed all 333 tasks with 64 JUnit suites/442 executions and zero
  failures, errors, or skips. Lint, WFF v4 source/APK memory validation, signatures/certificate
  continuity, deterministic packaging, and the standalone metadata/ZIP/privacy verifier passed.
- Independent preflight found no blocker/high defect and returned `GO WITH CONDITIONS`; the full
  forced pipeline satisfied its remaining engineering condition.
- Status is `Engineering: PASS / Physical: DEFERRED`. Android instrumentation and every phone/
  Watch row remain `NOT RUN`, never accepted.

## v0.7.0 — 2026-08-13 — Integrated WALK coaching release candidate

### Changed

- Phone, Wear, and watch-face modules are aligned at versionName `0.7.0`, versionCode `12` for one
  integrated engineering release candidate containing the previously completed post-meal,
  confirmed rapid-rise, and prolonged-inactivity WALK paths.
- The cumulative device backlog is consolidated into one privacy-safe phone/Galaxy Watch8
  acceptance checklist.
- GitHub Actions artifact metadata is aligned with `v0.7.0`; the authoritative artifact for this
  milestone is still built locally to conserve the user's hosted Actions quota.

### Safety, scope, and verification status

- This integration milestone changes no production coaching, provider, current-glucose,
  persistence, WorkManager, notification/action, session/replay, Wear protocol, History, Planner,
  export/reset, or watch-face behavior. Automated stairs remain production-disabled.
- Android instrumentation and every phone/Galaxy Watch8 row remain `NOT RUN`. The artifact must be
  described as `Physical: DEFERRED`, never physically accepted, until the user tests the pinned RC.
- The forced local pipeline passed all 333 tasks, with 62 JUnit suites/432 executions and zero
  failures, errors, or skips. Phone, Wear, and watch-face lint, WFF v4 source/APK memory checks,
  APK signature/certificate continuity, and the standalone metadata/ZIP/privacy verifier passed.
- Independent preflight review returned `GO WITH CONDITIONS`; the checklist conditions were
  resolved before the pinned commit. Fresh final review returned `SHIP` with no blockers.
- Current status is `Engineering: PASS / Physical: DEFERRED`. Android instrumentation and every
  phone/Galaxy Watch8 row remain `NOT RUN`.

## v0.6.2 — 2026-08-13 — Prolonged-inactivity WALK coaching

### Added

- One fail-closed prolonged-inactivity `WALK` opportunity can enter the existing canonical
  recommendation pipeline when walking reminders are enabled and current same-day activity context
  confirms that the configured inactivity threshold has been reached.
- Algorithm-v4 stable episode identity uses reason, activity source, last movement, threshold
  crossing, and algorithm version. Routine glucose or activity refreshes cannot create a new
  episode ID.
- Phone, Wear display, and phone Start revalidate activity identity, working hours, expiry, and the
  existing glucose safety/provenance rules. A delayed Start uses the latest persisted activity
  snapshot and fails closed if movement or activity context changed.
- The phone card remains hidden until the coordinator has both persisted and recorded publication
  of the same authoritative inactivity snapshot. Phone, Wear notification, and complication
  visibility are bounded by the earliest action-validity, quiet-hours, or inactivity working-hours
  deadline.

### Changed

- Candidate priority is post-meal, then confirmed rapid rise, then prolonged inactivity.
- Inactivity validity ends at the earlier of glucose freshness and activity freshness. A persisted
  recommendation snapshot remains immutable and cannot gain a later expiry after refresh.
- Phone, Wear, and watch-face modules are aligned at versionName `0.6.2`, versionCode `11`.

### Safety, scope, and verification status

- Automated inactivity coaching is WALK-only. Stair settings do not select an automated stair
  recommendation; manual stair actions remain unchanged.
- The increment adds no provider request, polling or WorkManager change, Room migration, Wear Data
  Layer field/schema change, notification-action contract, or session/replay semantic change.
- The targeted v0.6.2 test gate passed with exit code 0 across 154 actionable tasks. The forced
  authoritative pipeline then passed all 333 tasks, 61 JUnit suites/420 executions with zero
  failures, errors, or skips, lint, WFF validation, APK signature/certificate continuity, and the
  five-file package/privacy verifier.
- Independent review found and resolved the recommendation replay, episode-resurrection,
  publication, and display-deadline defects before the feature commit. The final APOS review
  returned `FINAL GO` with no blockers.
- Current status is `Engineering: PASS / Physical: DEFERRED`. All device-only checks remain
  `NOT RUN` and are backlogged for the `v0.7.0` integrated walk-coaching release candidate.

## v0.6.1 — 2026-08-12 — Confirmed rapid-rise walk coaching

### Added

- Production rapid-rise walk coaching requires two consecutive normalized readings from the same
  exact source, strictly increasing timestamps, a configurable freshness-bounded gap, and both
  effective rates at or above the configured threshold.
- Rapid opportunities use an algorithm-v3 SHA-256 pair identity derived from source, both reading
  IDs/timestamps, and algorithm version, so legacy snapshots cannot shadow a new action.
- Phone action handling revalidates the exact rapid pair at the original tap time. Phone and Wear
  hide incomplete, cross-source, or superseded rapid actions before Start is offered.

### Changed

- Post-meal remains the deterministic winner when post-meal and rapid-rise are both eligible.
- The repository retains the same immutable rapid snapshot during global cooldown/daily-cap
  reevaluation, but invalidates it after source change, pair change, a newer nonqualifying reading,
  expiry, consumption, active snooze, quiet hours, or reminder disablement.
- Phone, Wear, and watch-face modules are aligned at versionName `0.6.1`, versionCode `10`.

### Safety and scope

- The existing missing/future/stale/low/fast-fall policy, global snooze/cooldown/daily cap,
  canonical session lifecycle, notifications, and Wear command transport remain authoritative.
- This milestone adds no provider request, faster polling, WorkManager job, Room migration, Wear
  schema field, stair automation, prediction, or automatic personalization.
- The recorded automated/review gate passed. Status is `Engineering: PASS / Physical: DEFERRED`;
  real phone and Galaxy Watch8 behavior remains in `PHYSICAL_TEST_BACKLOG.md`.

## v0.6.0 — 2026-08-04 — Post-meal walk coaching

### Added

- A real meal marker schedules one unique, configurable delayed Nightscout refresh and post-meal
  safety evaluation.
- One phone/notification/Wear walk recommendation can be started or snoozed, completed once, and
  followed by the existing same-source descriptive observation path.
- Durable meal-trigger consumption and bounded one-time snooze redelivery prevent repeat sessions
  or repeated prompts for the same meal.
- Coached Start atomically consumes its recommendation and inserts one session; active-source
  changes invalidate old-source actions, and delayed refresh retries stop at the meal-window or
  three-attempt boundary.

### Safety and scope

- Production action generation is limited to `POST_MEAL_WINDOW`; rapid-rise and inactivity/stair
  automation are explicitly deferred.
- Missing, future, stale, low, or rapidly falling glucose suppresses the action. Action-time safety
  uses the exact source captured in an immutable phone-authored recommendation snapshot.
- The wording is wellness guidance, not a medical claim or treatment instruction. Physical phone
  and Galaxy Watch8 acceptance is required before another feature milestone.

## v0.5.1 — 2026-08-04 — Local History Explorer and selected-period GMI (phone-only)

### Added

- A fourth phone destination with fixed 24-hour/7/14/30/90-day local trend periods and custom
  14–90 completed-local-day periods.
- A dependency-free Compose Canvas trend that preserves disconnected gaps and bounded,
  deterministic min/max/time-weighted aggregation.
- Descriptive selected-period GMI for eligible periods with coverage, range, missing-data, and
  low-glucose qualifiers plus explicit laboratory-HbA1c wording.
- Exact-source local loader and request-generation guards for rapid range changes, source changes,
  and navigation away during loading.

### Safety and scope

- History navigation cannot call provider refresh, Nightscout range APIs, or backfill and cannot
  mutate the current-reading path.
- There is no Room migration, Wear/watch-face change, coaching change, notification change, chart
  gesture, or new provider.
- Physical phone acceptance is required before any later milestone.

## v0.5.0 — 2026-08-03 — Local glucose history foundation (phone-only)

### Added

- Explicit local-history retention choices: 90 days, 1 year, or keep all downloaded records.
- Confirmation-gated, source-scoped pruning that preserves the newest record for each source.
- Durable 90-day range backfill checkpoints with pause/failure recovery for older Nightscout data.
- Settings status for stored row count, local date range, source identity, and backfill state.
- Room schema version 9 plus deterministic export of history-management state.

### Changed

- Versioned release ZIP names preserve the full APK version (`MetabolicCoach-v0.5.0.zip`).

### Safety and scope

- Nightscout range backfill never publishes an older record as current and does not change Wear
  synchronization, the watch face, coaching, notifications, or quick actions.
- Existing Android backup exclusions remain in force; raw history stays app-private.
- No trend chart, selected-period GMI, analytics, AI recommendation, or new coaching behavior is
  included. Those are separate milestones after v0.5.0 phone acceptance.

### Phone acceptance

- The user reported the v0.5.0 physical phone checklist passed on 2026-08-04. The sanitized record
  retains categorical results only; the exact installed artifact hash was not re-supplied with the
  report.

## v0.4.2 — 2026-08-03 — Saved planning milestones (phone-only)

### Added

- Multiple saved planning milestones with optional titles, target GMI, provenance, and fixed
  30/60/90-day horizons.
- Exactly one selected milestone for a detailed remaining-window scenario and fixed-date
  evaluation; active/past/archived ordering is deterministic.
- One-time migration of the legacy singleton planner target, milestone CRUD/archive/delete,
  immutable target/date/horizon fields after the due date, and milestone-aware personal-data
  export (schema version 3).

### Safety and scope

- Milestones remain phone-only presentation state. They do not change Nightscout freshness,
  provider selection, Wear synchronization, watch-face resources, coaching, notifications, or
  quick actions.
- The feature uses CGM-derived GMI terminology and is not a laboratory HbA1c result, treatment
  instruction, medication guidance, or guarantee. A new phone physical acceptance test is
  required before the next milestone.

## v0.4.1 — 2026-08-03 — Current glucose freshness hotfix

### Fixed

- Current Nightscout data is fetched and persisted before any 90-day historical backfill.
- Historical backfill is best effort and can no longer block, overwrite, or regress the current
  reading when a range request fails or completes later.
- Current refreshes no longer use conditional `If-Modified-Since` validators; an unexpected `304`
  is treated as a failed current refresh instead of silently presenting an old cache.
- Provider merges and Room latest-reading queries now use deterministic source- and timestamp-aware
  ordering, preserving source isolation and accurate stale-age reporting.

### Scope and verification

- This is a critical-defect hotfix only. Wear synchronization, watch-face resources, coaching,
  notifications, and quick actions are unchanged.
- The failed `v0.4.0` engineering artifact and its evidence remain preserved. This release uses
  versionName `0.4.1`, versionCode `5`, and a separate GitHub Actions artifact.
- The next required step after CI is a new phone physical acceptance test; no later milestone is
  unlocked by this hotfix.

## v0.4 — 2026-08-03 — Glycemic Goal Planner (phone-only)

### Added

- Phone-side Glycemic Goal Planner settings with optional target GMI, target provenance, fixed
  30/60/90-day horizons, configurable low/very-low boundaries, and low-exposure limits.
- Provider-independent time-weighted 30/60/90-day metrics plus a 14-day safety baseline for mean
  glucose, CGM-derived GMI, time in range, time below range, very-low exposure, covered time,
  missing duration, and largest gap.
- Mathematical target scenarios that use the observed complementary history (60 days before a
  30-day horizon, 30 days before a 60-day horizon, and the full 90-day target window) with explicit
  insufficient-data, source-discontinuity, unattainable, warning, and low-glucose-risk statuses.
- Bounded Nightscout historical backfill in seven-day ranges for a cold phone cache, capped at the
  recent 90-day planner window.

### Changed

- Personal-data export now includes planner settings and uses export schema version 2.
- Phone navigation adds a Planner destination. Planner output remains phone-only and is not sent to
  Wear, the watch face, coaching rules, or notifications.

### Safety and scope

- The UI and documentation use **CGM-derived GMI** terminology; this is not a laboratory HbA1c
  result, treatment instruction, insulin/dose recommendation, or promise of target attainment.
- No direct CareSens integration, new provider, Wear synchronization change, coaching improvement,
  history graph, analytics, or AI recommendation was added in this milestone.

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

### Phone acceptance

- On 2026-08-01, the user accepted the phone-side Nightscout gate after validating URL
  configuration, current glucose, trend, delta, timestamp, offline cache, retry behavior, and no
  crashes on the intended phone/server route.
- A glucose history graph was not implemented and was explicitly excluded from `v0.2` acceptance.
  Stored recent history and provider normalization remain covered by automated tests.

### Known acceptance gaps

- `v0.2` is a debug-signed engineering milestone unless its embedded `INSTALL.md` explicitly
  identifies a signed release build.
- Physical Galaxy Watch8 synchronization, Android instrumentation, production signing, and
  store/privacy review remain required.
- Production functionality is frozen until the `v0.3` physical Watch8 results are reviewed. The
  `v0.3` gate uses the unchanged `v0.2.0` APKs and must not be represented as a new binary release.

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
