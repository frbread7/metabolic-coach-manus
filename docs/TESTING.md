# Testing strategy

## Verification principle

Define the claim, run the smallest test that proves it, read the result, and retain concise
evidence. Compilation is not functional validation; an emulator run is not Galaxy Watch8
validation; a displayed glucose value is not proof of reliable coaching latency.

The authoritative `scripts/build-apks.sh` evidence path forces every selected task to rerun and
disables Gradle build-cache reuse and Kotlin incremental compilation. This prevents a prior
filtered test invocation from being reused as complete-suite evidence and deliberately favors
reproducible cross-module ABI validation over build speed when shared data classes change.

## Automated tests

The current source includes these JVM and Android local-test suites:

| Suite | Coverage |
| --- | --- |
| `GlucoseReadingDisplayTest` | Unit-aware glucose rate display and threshold conversion round trips |
| `CoachRuleEngineTest` | Recommendation IDs/validity, rapid rise, shared missing/future/stale/low/fast-fall safety, post-meal timing, inactivity stair/walk fallback, working/quiet hours, and cooldown |
| `ExerciseSafetyPolicyTest` | Phone/Wear safety parity, exact action-expiry boundaries, quiet hours, active-session suppression, and falling-rate fallback |
| `SettingsValidatorTest` | Defaults plus shared coaching bounds, retained legacy Health Connect-origin validation, falling-rate, follow-up, command-expiry, activity goals, and all personal-observation analysis controls |
| `FollowUpReadingSelectorTest` | Exact-source filtering, at/after-due preference, pre-deadline waiting, deterministic deadline fallback, and missing finalization |
| `ObservationAnalyzerTest` | Manual effects, legacy safety-provenance exclusion, below-threshold exclusion for effects/timing, configurable generic/post-meal/follow-up/baseline buckets, configurable sample and comparable-cohort gates, unique separated median, exact-source/follow-up eligibility, meal provenance, and cautious wording |
| `NightscoutSettingsValidatorTest` | Multiple-server limits and selection, URL normalization, HTTPS enforcement, credential/query/fragment rejection, valid TCP ports, and polling/timeout/retry bounds |
| `NightscoutJsonParserTest` | Nightscout response parsing, trend mapping, stable IDs, ordering, delta/rate calculation, timestamp fallbacks, malformed/unusable rows, and bounded value/time acceptance |
| `NightscoutProviderTest` | Provider state flow, success, conditional cache reuse, retry/non-retry and response-size classification, retained cache on failure, cancellation, and per-server isolation/switching |
| `OkHttpNightscoutApiClientTest` | MockWebServer request path/query/headers, conditional responses, redirect refusal, bounded declared/streamed response size, and future-authenticator hook without a real server |
| `NightscoutSettingsJsonCodecTest` | Stable multi-server DataStore encoding/decoding and malformed stored-value recovery |
| `XdripGlucoseIngestorTest` | Retained inactive-adapter input validation; it does not prove or enable a Version 1 broadcast route |
| `HealthConnectOriginSelectionPolicyTest` | Single-origin auto-pinning, multi-origin configuration gating, exact filtering, unavailable saved-origin retention, and deterministic discovery order |
| `ReleaseGlucoseProviderModePolicyTest` | Migration and build policy force every persisted legacy provider mode to the Version 1 Nightscout mode |
| `ExerciseSessionSummaryTest` | Daily valid-session count, total-duration aggregation, latest end time, and reversed-interval rejection |
| `WatchStateCodecTest` | State/command round trips including backward-compatible legacy settings, configurable observation analysis, recommendation validity/provenance, phone revision, and session acknowledgement; Nightscout connection settings remain absent from Wear payloads |
| `InterventionDaoLifecycleTest` | Idempotent start, one-active-session semantics, and Room follow-up lifecycle persistence |
| `RecommendationSnapshotMapperTest` / `RecommendationSnapshotPersistenceTest` | Complete snapshot mapping plus immutable canonical retry behavior for a stable recommendation ID |
| `QuickActionHandlerTest` | Selected-provider baseline provenance, required phone-authored snapshot lookup, echoed-provenance conflict rejection, snapshot-owned intervention dose, complete prospective recommendation/trigger/rate/threshold capture, no invented manual provenance, action-time recommendation/safety boundaries under delayed delivery, generic start expiry, idempotent start, deferred completion, delayed known-session completion, and terminal orphan expiry |
| `SyncSchedulerTest` | Nightscout connected-network scheduling, configured interval and 15-minute minimum, independence from Health Connect background permission, fallback behavior, and coroutine-cancellation propagation |
| `DeferredCompletionPolicyTest` | Rejected-prerequisite propagation and session matching for completion-first delivery |
| `PhoneCommandProcessorTest` | Exactly-once terminal replay: a persisted rejected command triggers watch-state republication without invoking mutation logic |
| `CommandDataEpochPolicyTest` | Legacy compatibility before erase and strict current-reset-token matching afterward |
| `PhoneDataMutationGateTest` | Process-wide serialization prevents boundary crossing; local operations preempt cancellable provider work and retain priority over already queued provider work |
| `PersonalDataJsonWriterTest` | Deterministic versioned JSON, escaping/control characters, non-finite-number handling, binary encoding, and empty-table separators |
| `SessionAckOrderingPolicyTest` | Completion-over-start ordering, older replay protection, and the rejected-start/completion/replayed-start chain |
| `WearSessionReplicaReducerTest` | Persistent pending start/completion, transport state, tombstone protection, and all acknowledgement outcomes |
| `WearCommandOutboxPolicyTest` | Generic command ordering, stable-ID deduplication, and removal |
| `WearActionPolicyTest` | Explicit local terminal rejection for busy, expired, and superseded quick actions |
| `SessionProgressTest` | Configured walk countdown bounds and zero-bound behavior |
| `RemoteStateOrderPolicyTest` | Same-instance revision ordering, new-instance epochs, and guarded legacy-state handling |
| `RemoteDataResetPolicyTest` | First-seen reset handling, same-token idempotence, legacy-state compatibility, and missing-cache recovery |
| `TimeSettingValueTest` | Exact minute/hour preservation and valid minute-of-day clamping |

Exported Room schemas 1–7 are committed. `DatabaseMigrationTest` is an Android instrumentation
suite for schema 1→7 and every supported starting version 2–6→7, including safe defaults, preserved
legacy rows, nullable prospective timing-provenance columns, and an initially empty recommendation
snapshot table. Its source has compiled locally, but the suite has not executed because it requires
an Android device or emulator.

Run them with:

```bash
./gradlew :core:model:test
./gradlew :core:domain:test
./gradlew :core:data:testDebugUnitTest
./gradlew :core:data:testReleaseUnitTest
./gradlew :core:data:compileDebugAndroidTestKotlin
./gradlew :core:sync:testDebugUnitTest
./gradlew :phone:testDebugUnitTest
./gradlew :wear:testDebugUnitTest
```

Execute the migration suite on an Android runtime with:

```bash
./gradlew :core:data:connectedDebugAndroidTest
```

## Milestone test gates

- `v0.2` implementation gate: the full static pipeline, isolated provider/repository tests,
  parsing, retry, cache, server switching, manifests, documentation, and debug package must pass.
- `v0.2` live acceptance gate: on the phone, configure a test or personal Nightscout server,
  confirm current/history normalization and recovery from connectivity loss, and record latency
  without exposing health data. Do not install the watch package before this gate passes.
- `v0.3` gate: only after `v0.2`, install matching phone/Wear/watch-face artifacts and validate the
  existing Data Layer flow, reconnect behavior, stale-state handling, and physical Watch8 UI.
- `v0.4` coaching work remains frozen until the preceding gates pass. Existing walk/stair behavior
  may be regression-tested, but no new coaching feature or medical claim belongs in `v0.2`.

## Current local verification

The `v0.2` Nightscout milestone was verified locally on 2026-07-25:

- `./scripts/build-apks.sh` completed successfully in 6m58s with all 333 actionable tasks executed.
  It passed
  model/domain/data-debug/data-release/sync/phone/Wear tests, Android migration-test source
  compilation, debug lint, all three debug assemblies, APK signature checks, phone/Wear
  certificate equality, WFF v4 schema/memory validation, and exact five-file ZIP packaging.
- The resulting 49 JUnit XML suites contain 249 executions, 0 failures, 0 errors, and 0 skipped.
  The data tests intentionally execute under both debug and release.
- Android instrumentation was compiled but not executed; no emulator or physical device was
  attached.
- Nightscout provider/client tests used synthetic JSON and MockWebServer. No personal Nightscout
  URL was queried.
- Phone, Wear, and watch-face APKs passed APK Signature Scheme v2 verification with one signer.
  The phone/Wear certificate SHA-256 is
  `7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4`.
- Independent final review returned architecture `CLEAR` and code review `APPROVE`, with no
  remaining findings.

Current debug artifact SHA-256 values:

```text
eb1dc99bd612970b975d17ff6e02e63c3529e38581f8a7d0bd50e7866bd2dbee  metabolic-coach-phone-debug.apk
e373a79b2dee352d6cc1ea798f32d9c1c8e6eab277a94d155bd8c3673f09e8a6  metabolic-coach-wear-debug.apk
76b0b050e1a201e41c2ad8185ef0aeaf06a294a20caa2857546059a9a47cd23b  metabolic-coach-watchface-debug.apk
03ef7df91417243ae4f7862a0608c967d8d6e9d155e1d459c3b35091c8bb3767  MetabolicCoach-v0.2.zip
```

Current artifact sizes are 41,673,623 bytes for phone, 44,620,696 bytes for Wear, and 10,095 bytes
for the watch face. The five-file ZIP is 29,963,158 bytes. These are debug-signed engineering
artifacts. Live-server, Android instrumentation, physical-device, production-signing, and
store/privacy results are not implied.

## Required static and build checks

```bash
./scripts/build-apks.sh
```

The script runs the model/domain/data-debug/data-release/sync/phone/Wear tests, compiles the Android migration-test
source without executing instrumentation, runs the selected variant's phone/Wear/watch-face lint
tasks and APK assemblies, validates WFF XML and the built APK, verifies APK signatures, and checks
that phone/Wear certificates match.
For a signed release run, provide the environment-only signing variables described in
[Release](RELEASE.md) and use:

```bash
MC_BUILD_VARIANT=release ./scripts/build-apks.sh
```

For release, also inspect all warnings, merged manifests, dependency reports, APK/AAB contents,
and R8 output. Do not suppress a warning without understanding its release impact.

## Watch Face Format validation

An Android resource build alone does not prove that WFF XML conforms to the selected WFF version or
passes Play memory limits. `scripts/validate-watchface.sh` downloads checksum-pinned official
validator and memory-footprint JARs, validates schema v4, rejects DEX in the built APK, and applies
the active/ambient memory limits.

Before release:

1. validate `watchface/src/main/res/raw/watchface.xml` using Android Studio's version-aware WFF
   validation;
2. build and run the [official open-source WFF validator](https://github.com/google/watchface);
3. run the repository's memory-footprint evaluator;
4. inspect interactive and ambient rendering on Wear OS 6;
5. test all accent options and the no-data complication state.

Run XML-only validation with:

```bash
./scripts/validate-watchface.sh
```

Pass an APK to add resource-only and memory validation:

```bash
./scripts/validate-watchface.sh \
  watchface/build/outputs/apk/debug/watchface-debug.apk
```

Repeat these checks on every release candidate and archive the validator version and raw result.

## Fresh host verification

Run `./scripts/build-apks.sh` again after any source/build change before publishing evidence. Record
the exact commit, host/toolchain, commands, results, artifact hashes, and signing-certificate digest
from that same run. Instrumentation and physical-device suites remain unverified regardless of
local pipeline results.

## Phone test matrix

At minimum:

- Android 9/API 28 for app startup and graceful Health Connect unavailability;
- Android 10–13 with the Health Connect application where supported;
- Android 14, 15, and 16 with platform Health Connect;
- Samsung and non-Samsung phones;
- fresh install, upgrade, process death, reboot, force-stop, battery saver, no network, and low
  storage;
- locales using 12/24-hour time and decimal comma;
- timezone and daylight-saving transitions;
- notification denied, granted, later revoked, and channel disabled.

Health Connect must be tested with synthetic data for each requested record type and with missing
individual permissions.

## Galaxy Watch8 test matrix

Use the actual non-Classic Galaxy Watch8 target and record the model/size and Wear OS build.

Verify:

- no action depends on a rotating bezel;
- the three-page horizontal pager reaches coach/home, quick actions, and Today by touch;
- all controls are reachable by touch with one hand;
- round-screen clipping and edge rejection;
- readable glucose, delta, age, and action labels at every configured font scale;
- tap actions from face, app, and notification;
- explicit queued/rejected handling for in-app actions and terminal result text for notification and
  complication quick-action entry points;
- home/session walk countdown, one completion haptic, and manual completion after zero;
- long-press behavior remains the system watch-face flow;
- interactive-to-ambient and ambient-to-interactive transitions;
- smooth minimal pager/session-start motion without decorative or ambient animation;
- burn-in-safe ambient placement and content;
- screen-off/session behavior;
- notification vibration and duplicate behavior;
- phone/watch disconnect, reconnect, airplane mode, Bluetooth off, and Wi-Fi fallback;
- watch and phone reboot;
- Wear app absent while face is installed, and face absent while Wear app is installed.

An official Wear OS 6 emulator is useful for repeatable layout tests, but does not replace the
Samsung hardware pass.

## Provider scenarios

Use synthetic Nightscout fixtures and MockWebServer for automated tests; do not require or copy data
from a real server:

| Scenario | Expected result |
| --- | --- |
| No configured Nightscout server | `CONFIGURATION_REQUIRED`; no fabricated reading or periodic network work |
| One configured active server | Exact normalized URL is queried and readings use that server's source ID |
| Two configured servers | Only the explicitly active server is queried |
| Active server unavailable | Retry follows configured bounds; no automatic request to another server |
| Active server changed | UI/history switches to the selected exact source; old-server cache/history is not displayed |
| Same server slot URL changed | Source identity changes; readings from the old URL are not merged |
| HTTPS required with HTTP URL | Settings validation rejects the URL |
| HTTP explicitly allowed | Local/test URL is accepted with a visible security warning |
| URL contains credentials, query, or fragment | Settings validation rejects it |
| Authentication 401/403 | Non-retryable authentication failure; cached reading retained |
| HTTP 408/429/5xx | Bounded retry with exponential delay capped at 60 seconds |
| DNS, connection, or timeout failure | Bounded retry; degraded state and cached Room data retained |
| HTTP 304 | Matching per-server cache reused; no cross-server cache access |
| Malformed JSON or oversized response | Non-retryable response failure; no partial fabricated state |
| Empty valid array | No new reading; stale/cached behavior remains explicit |
| First reading | Value shown; delta/trend unknown until enough ordered data exists |
| Ordered readings | Direction maps and delta/rate calculate correctly |
| Duplicate record | No duplicate Room history |
| Out-of-order record | No invalid divide/order result |
| Missing/invalid individual row | Bad row skipped without corrupting valid rows |
| Entire response unusable | Parse failure; prior cache retained |
| Request cancelled | Coroutine cancellation reaches the HTTP call and is not retried |
| Stale reading | Informational stale state; no exercise action |
| Future-dated reading | Informational clock warning; no exercise action |
| Below configured low threshold | Exercise coaching paused |
| Falling at/beyond configured pause rate | Exercise coaching paused on phone and Wear |
| Source stops updating | Age increases and stale behavior occurs |
| Unit changed | Display changes; normalized value/history does not |
| Timezone changes | Daily and meal windows follow intended local day |
| Multiple exercise sessions | Daily count/duration and latest movement aggregate all valid records |
| Reversed exercise interval | Invalid interval is excluded from activity aggregates |

End-to-end testing must compare sensor, CareSens app, xDrip, Nightscout, phone-import, and
watch-display timestamps for at least 24 hours. Define acceptable latency and outage behavior before
acceptance. This live-server validation is separate from unit tests and must not place private
glucose data in source control or test reports.

## Coaching scenarios

Test boundary values, not just typical values:

- exactly below/at/above low and rapid-rise thresholds;
- exactly below/at/above the configured exercise-pause fall-rate threshold, using both numeric rate
  and trend fallback;
- stale age exactly at and just beyond the threshold;
- future-dated readings;
- quiet and working windows that cross midnight;
- recommendation validity exactly before/at/after expiry and reevaluation without a provider
  update;
- coached starts delivered after recommendation expiry but created inside the original window,
  including a different receipt-time glucose reading, plus creation/validity and generic-age
  boundaries;
- missing phone-authored recommendation snapshot, conflicting optional watch echoes, and a command
  with omitted echoes whose session dose/provenance still comes entirely from the phone snapshot;
- publication retry with the same recommendation ID but regenerated time/dose fields, proving the
  original immutable snapshot is republished and remains command-authoritative;
- quiet-hour entry/exit at a minute tick on both phone and Wear;
- post-meal delay/window start and end;
- missing movement timestamp;
- snooze expiry;
- cooldown expiry;
- daily maximum at zero, one below, and at limit;
- simultaneous rapid-rise, post-meal, and inactivity eligibility;
- prolonged inactivity with stairs enabled, with stairs disabled/walking enabled, and with both
  reminder types disabled;
- permission denied after recommendation generation;
- duplicate start/complete deliveries;
- completion arriving before start and later deferred reconciliation;
- delayed offline completion of a phone-known session after generic command expiry, preserving the
  recorded completion time, plus terminal expiry of a completion whose session never arrived;
- expired/unsafe command rejection, persisted terminal replay deduplication, revisioned state
  publication, awaited delivery enqueue, and terminal command-item deletion;
- durable generic Wear outbox ordering/retry for snooze, duplicate-ID collapse, and phone replay
  deduplication without extending snooze twice;
- process death after terminal persistence but before enqueue/deletion, including changed glucose
  or conflict conditions on replay;
- rejected start, deferred completion, completion acknowledgement, then out-of-order start replay;
- repeated same-session start and conflicting active-session rejection;
- missing and near-due follow-up readings;
- baseline/follow-up reading IDs, measurement timestamps, and exact-source persistence;
- rejection of cross-source observation samples;
- same-phone revision ordering, new-phone revision epochs, and guarded legacy states;
- pending start not erased by unacknowledged state;
- completion tombstone not revived by unacknowledged state;
- `APPLIED`, `REJECTED_EXPIRED`, `REJECTED_UNSAFE`, and `REJECTED_CONFLICT` reconciliation
  messages/state.

The recommendation priority documented in [Architecture](ARCHITECTURE.md) is a business contract.
Changing it requires explicit review and regression tests.

## Personal-observation validation

The current implementation captures exact trigger, recommendation, intervention-dose, baseline,
source, follow-up, and low-threshold provenance. Effect summaries require complete finalized
same-source outcomes and exclude follow-ups below the threshold captured at start; pre-v6 outcomes
without that safety provenance are not reconstructed. Phase 3 timing is prospective-only:
legacy/manual sessions without complete start-time recommendation provenance are excluded.

Generic and post-meal trigger-delay bucket widths are configurable, with defaults of 5 and 15
minutes. Each bucket needs at least
`max(minimumObservationSamples, minimumTimingBucketSamples)` eligible samples; the timing-specific
default is eight. The minimum comparable-bucket count is configurable with a default of two.
Actual-follow-up matching width and baseline glucose band are also configurable, with 15-minute and
20 mg/dL defaults. Output still requires one unique lowest observed median plus strict separation:
its upper quartile must be below every comparator's lower quartile. Validate both positive and
suppression cases, including tied/overlapping distributions and configuration boundaries.

Eligibility checks reject incomplete, mixed-source, reused-reading, chronologically invalid,
overlapping-session, intervening-recorded-meal, below-threshold follow-up, unmatched post-meal, and
duplicate-trigger samples. Cohort comparisons hold source, trigger reason, recommendation algorithm,
activity type/duration/floors, planned and actual follow-up timing, baseline glucose band/rate
direction, and the low-threshold value captured at start. Release validation still must:

- verify due-time scheduling, retry, and restart recovery on actual phones;
- verify every exclusion and cohort boundary with controlled fixtures and device-level lifecycle
  tests;
- document timezone and day grouping;
- assess survivorship/selection bias from sessions the user chooses to complete;
- account for medication, unrecorded meals/activity, adherence, and other uncaptured confounders;
- confirm observation copy contains no causal, medical, best, or ideal timing claim;
- confirm observations never alter settings, thresholds, rules, or recommendation timing.

Personal observations remain experimental.

## Battery and performance

Measure on Galaxy Watch8 over representative 24-hour periods:

- baseline without Metabolic Coach;
- Wear app and complications installed;
- WFF face active with AOD off;
- WFF face active with AOD on;
- normal provider cadence;
- reconnect storm and stale-provider conditions.

Record watch and phone battery deltas, wakeups, WorkManager executions, Data Layer transfers,
complication updates, and notification count. Confirm no one-second background loop runs outside an
active visible timer. Motion is intentionally limited to pager/session transitions; verify smooth
interaction on Galaxy Watch8 without adding ambient work or unacceptable battery cost.

Use Macrobenchmark/Baseline Profiles only after functional correctness and only if measured startup
or interaction performance warrants them.

## Security and privacy tests

- Verify Android backup remains disabled.
- Verify logs and crash reports contain no glucose values or raw health records.
- Verify both debug and release merged manifests contain no xDrip receiver or receive permission,
  Settings exposes no xDrip selection, and every persisted legacy provider mode migrates to
  Nightscout before repository access.
- Verify Nightscout URLs reject embedded credentials, query strings, and fragments.
- Verify future authentication credentials cannot enter DataStore, Wear Data Layer, export output,
  logs, errors, or crash reports.
- Verify HTTPS is the default, HTTP requires an explicit opt-out, and HTTP produces a visible
  warning. Threat-test cleartext transport before allowing it in any release policy.
- Verify one configured server can never receive another server's conditional-request metadata,
  cached reading, history query, or future credential.
- Threat-test the retained inactive xDrip adapter and its lack of certificate pinning before any
  future enablement.
- Verify Data Layer rejects mismatched package/signature installations.
- Verify permissions can be revoked without crashes.
- Verify uninstall removes local app data and does not delete source Health Connect records.
- Export a large synthetic multi-year history through local and cloud-backed document providers;
  verify bounded memory, valid schema/escaping, all six Room tables, every exported coaching
  setting, explicit exclusion of Nightscout configuration, cancellation/error behavior, and no
  extra cache copy.
- Confirm erase requires explicit confirmation, empties all Room tables, clears settings to
  defaults, removes phone notifications, and does not alter Health Connect/source records or
  permissions.
- Confirm erase while phone/watch are disconnected later clears Wear state, active/pending session,
  completion tombstone, and generic outbox; replaying the same reset is idempotent.
- Confirm delayed commands from before erase are rejected and deleted while commands created from
  the new watch reset epoch still apply normally.
- Confirm export, erase, provider ingestion/refresh, follow-up finalization, settings/meal writes,
  and phone/watch quick actions cannot cross the mutation boundary; verify local operations
  preempt a long-running provider request and canceled WorkManager jobs retry safely.
- Exercise erase against concurrent refresh, queued/running WorkManager work, process death, reboot,
  unavailable Google Play services, and a watch that reconnects days later.
- Perform dependency, manifest-export, PendingIntent, and release-signing review.

## Release acceptance

A release candidate is acceptable only when:

- all unit, lint, compile, assemble, WFF, and memory checks are green;
- signed phone and Wear builds synchronize on the final certificate;
- Nightscout configuration, active-server switching, isolated cache/history, bounded retry,
  recovery, HTTPS behavior, and public-endpoint compatibility are proven on target phones;
- the complete CareSens app → xDrip → Nightscout → phone → watch route meets a written
  latency/reliability target;
- Health Connect activity foreground and gated background behavior is proven on target devices;
- Galaxy Watch8 touch, AOD, complication, and battery tests pass;
- medical/wellness wording and privacy disclosures are reviewed;
- xDrip broadcast and direct CareSens communication remain absent from both manifests and the
  provider selector;
- no known severity-high defect remains;
- APK/AAB hashes and test evidence are archived.

## Currently unverified external gates

- the intended live Nightscout server and its public-access configuration;
- CareSens app/xDrip/Nightscout/phone/watch end-to-end latency and outage recovery;
- multi-server switching and isolation on physical phones;
- TLS, DNS, captive-portal, battery-saver, reboot, and background WorkManager behavior;
- Galaxy Watch8 compatibility and power behavior;
- Samsung Health Data SDK partner access;
- physical Watch8 touch, AOD, reboot, and phone/watch disconnect behavior;
- Android instrumentation tests on representative devices/emulators;
- release signing and Google Play policy review;
- real-device Health Connect activity background execution after denial, reboot, and process death.
