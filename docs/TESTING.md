# Testing strategy

## Verification principle

Define the claim, run the smallest test that proves it, read the result, and retain concise
evidence. Compilation is not functional validation; an emulator run is not Galaxy Watch8
validation; a displayed glucose value is not proof of reliable coaching latency.

The authoritative `scripts/build-apks.sh` evidence path disables Gradle build-cache reuse and
Kotlin incremental compilation. This deliberately favors reproducible cross-module ABI validation
over build speed when shared data classes change.

## Automated tests

The current source includes these JVM and Android local-test suites:

| Suite | Coverage |
| --- | --- |
| `GlucoseReadingDisplayTest` | Unit-aware glucose rate display and threshold conversion round trips |
| `CoachRuleEngineTest` | Recommendation IDs/validity, rapid rise, shared missing/future/stale/low/fast-fall safety, post-meal timing, inactivity stair/walk fallback, working/quiet hours, and cooldown |
| `ExerciseSafetyPolicyTest` | Phone/Wear safety parity, exact action-expiry boundaries, quiet hours, active-session suppression, and falling-rate fallback |
| `SettingsValidatorTest` | Defaults plus the shared full settings bounds, Health Connect origin-package validation, falling-rate, follow-up, command-expiry, activity goals, and all personal-observation analysis controls |
| `FollowUpReadingSelectorTest` | Exact-source filtering, at/after-due preference, pre-deadline waiting, deterministic deadline fallback, and missing finalization |
| `ObservationAnalyzerTest` | Manual effects, legacy safety-provenance exclusion, below-threshold exclusion for effects/timing, configurable generic/post-meal/follow-up/baseline buckets, configurable sample and comparable-cohort gates, unique separated median, exact-source/follow-up eligibility, meal provenance, and cautious wording |
| `XdripGlucoseIngestorTest` | Valid ingestion and rejection of wrong mode/action, malformed values, stale/future timestamps, and invalid data |
| `HealthConnectOriginSelectionPolicyTest` | Single-origin auto-pinning, multi-origin configuration gating, exact filtering, unavailable saved-origin retention, and deterministic discovery order |
| `ReleaseGlucoseProviderModePolicyTest` | Release-variant fallback from persisted debug-only xDrip mode to Health Connect while preserving supported modes |
| `ExerciseSessionSummaryTest` | Daily valid-session count, total-duration aggregation, latest end time, and reversed-interval rejection |
| `WatchStateCodecTest` | State/command round trips including selected Health Connect origin, configurable observation analysis, recommendation validity/provenance, phone revision, and session acknowledgement; backward-compatible optional settings plus schema and malformed-payload rejection |
| `InterventionDaoLifecycleTest` | Idempotent start, one-active-session semantics, and Room follow-up lifecycle persistence |
| `RecommendationSnapshotMapperTest` / `RecommendationSnapshotPersistenceTest` | Complete snapshot mapping plus immutable canonical retry behavior for a stable recommendation ID |
| `QuickActionHandlerTest` | Selected-provider baseline provenance, required phone-authored snapshot lookup, echoed-provenance conflict rejection, snapshot-owned intervention dose, complete prospective recommendation/trigger/rate/threshold capture, no invented manual provenance, action-time recommendation/safety boundaries under delayed delivery, generic start expiry, idempotent start, deferred completion, delayed known-session completion, and terminal orphan expiry |
| `SyncSchedulerTest` | Health Connect background-permission failure fallback and coroutine-cancellation propagation without cancelling periodic work |
| `DeferredCompletionPolicyTest` | Rejected-prerequisite propagation and session matching for completion-first delivery |
| `PhoneCommandProcessorTest` | Exactly-once terminal replay: a persisted rejected command triggers watch-state republication without invoking mutation logic |
| `CommandDataEpochPolicyTest` | Legacy compatibility before erase and strict current-reset-token matching afterward |
| `PhoneDataMutationGateTest` | Process-wide serialization prevents a second phone data operation from crossing an active export/erase/write boundary |
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

## Latest local verification

On 2026-07-23, the current checkout produced this evidence:

- `./scripts/build-apks.sh`: Gradle reported 333 actionable tasks; model/domain/data/sync/phone/Wear
  tests, Android migration-test source compilation, debug lint, all three debug assemblies, APK
  signature checks, phone/Wear certificate equality, and WFF v4 schema/memory validation passed.
- JUnit XML aggregate: 163 executions across build variants, covering 144 distinct test cases, with
  0 failures, 0 errors, and 0 skipped. The release data suite intentionally re-executes shared data
  tests in addition to its release-only provider-migration coverage.
- Release static gate: Gradle reported 243 actionable tasks for all three `lintRelease` and
  `assembleRelease` targets; R8/minified unsigned APK generation passed and all lint reports say
  `No issues found`.
- Release WFF v4 schema/memory validation passed, and both debug and unsigned-release watch-face
  APKs contain no DEX.
- Release manifest inspection confirmed the xDrip receiver and permission are absent; the debug
  manifest contains them as intended.
- APK inspection confirmed package/minimum/target SDK values of
  `com.young.metaboliccoach`/28/36 for phone,
  `com.young.metaboliccoach`/30/36 for Wear, and
  `com.young.metaboliccoach.watchface`/36/36 for the watch face in both build variants.
- A host SQLite schema-6→7 smoke applied the exact new table/index shape, preserved a legacy
  intervention row, found all 12 snapshot columns, and returned `PRAGMA integrity_check = ok`.
  This supplements migration-source compilation; it does not replace Android instrumentation.
- Host/toolchain: Linux aarch64, OpenJDK 17.0.19, Gradle 8.13, Android Gradle Plugin 8.11.1,
  Kotlin plugin 2.2.20, compile/target SDK 36, and WFF validator 1.7.0.
- Phone/Wear debug signing-certificate SHA-256:
  `7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4`.

Current debug artifact SHA-256 values:

```text
2151e714f6763230b9f735f5b6dda2e9fe444ee775e50d7ce715cb2993554d81  metabolic-coach-phone-debug.apk
1a9e888e0ee20f83e843c05edc3c96622c79522e36e172ea9251aba8e6d712c0  metabolic-coach-watchface-debug.apk
ae962f978a5b1b7c3bdaf593c5f4057a4d90edca10bb7142f7b172eec0c903f9  metabolic-coach-wear-debug.apk
```

Current debug artifact sizes are 40,810,124 bytes for phone, 10,019 bytes for watch face, and
44,833,728 bytes for Wear. The independently assembled unsigned release APKs are 3,672,542 bytes,
5,923 bytes, and 3,609,666 bytes respectively. Unsigned release APKs are static evidence only.

These are local debug artifacts, not production-signed releases. Instrumentation and physical
device results are not implied by this evidence.

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

For each supported provider:

| Scenario | Expected result |
| --- | --- |
| No permission/source app | Clear provider status; no fabricated reading |
| One Health Connect glucose writer | Package is auto-selected, persisted, and used exactly |
| Multiple Health Connect glucose writers, no selection | `CONFIGURATION_REQUIRED`; no glucose display or coaching until one package is saved |
| Saved Health Connect writer plus newer competing writer | Saved exact writer remains authoritative; no silent switch or mixed trend |
| Saved Health Connect writer temporarily absent | Selection is retained and shown as having no recent records; another writer does not take over |
| First reading | Value shown; delta/trend unknown until enough ordered data exists |
| Ordered readings | Delta/rate calculated correctly |
| Duplicate record | No duplicate Room history |
| Out-of-order record | No invalid divide/order result |
| Stale reading | Informational stale state; no exercise action |
| Future-dated reading | Informational clock warning; no exercise action |
| Below configured low threshold | Exercise coaching paused |
| Falling at/beyond configured pause rate | Exercise coaching paused on phone and Wear |
| Source stops updating | Age increases and stale behavior occurs |
| Unit changed | Display changes; normalized value/history does not |
| Timezone changes | Daily and meal windows follow intended local day |
| Multiple exercise sessions | Daily count/duration and latest movement aggregate all valid records |
| Reversed exercise interval | Invalid interval is excluded from activity aggregates |

CareSens-to-Health-Connect testing must compare vendor/source timestamps with import timestamps for
at least 24 hours. The allowable end-to-end delay must be defined before acceptance.

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
- In the debug variant, confirm xDrip is rejected below Android 14, rejects malformed samples, and
  accepts data only when Android reports `com.eveningoutpost.dexdrip` as the sender package.
- Verify the release merged manifest has no xDrip receiver or receive permission and the release UI
  cannot select xDrip; verify the release policy converts a persisted debug xDrip selection to
  Health Connect before repository access.
- Threat-test the remaining absence of xDrip certificate pinning before any future production
  enablement.
- Verify Data Layer rejects mismatched package/signature installations.
- Verify permissions can be revoked without crashes.
- Verify uninstall removes local app data and does not delete source Health Connect records.
- Export a large synthetic multi-year history through local and cloud-backed document providers;
  verify bounded memory, valid schema/escaping, all six Room tables, every effective setting,
  cancellation/error behavior, and no extra cache copy.
- Confirm erase requires explicit confirmation, empties all Room tables, clears settings to
  defaults, removes phone notifications, and does not alter Health Connect/source records or
  permissions.
- Confirm erase while phone/watch are disconnected later clears Wear state, active/pending session,
  completion tombstone, and generic outbox; replaying the same reset is idempotent.
- Confirm delayed commands from before erase are rejected and deleted while commands created from
  the new watch reset epoch still apply normally.
- Confirm export, erase, provider ingestion/refresh, follow-up finalization, settings/meal writes,
  and phone/watch quick actions remain serialized under long-running and cancellation scenarios.
- Exercise erase against concurrent refresh, queued/running WorkManager work, process death, reboot,
  unavailable Google Play services, and a watch that reconnects days later.
- Perform dependency, manifest-export, PendingIntent, and release-signing review.

## Release acceptance

A release candidate is acceptable only when:

- all unit, lint, compile, assemble, WFF, and memory checks are green;
- signed phone and Wear builds synchronize on the final certificate;
- Health Connect manual foreground and gated background behavior is proven on target devices;
- Health Connect multi-writer discovery, explicit selection, persistence, and missing-source
  behavior are proven on target phones;
- at least one authorized glucose route meets a written latency/reliability target;
- Galaxy Watch8 touch, AOD, complication, and battery tests pass;
- medical/wellness wording and privacy disclosures are reviewed;
- xDrip remains absent from the release manifest and provider selector until a verified sender
  contract and explicit security review justify changing that boundary;
- no known severity-high defect remains;
- APK/AAB hashes and test evidence are archived.

## Currently unverified external gates

- authorized CareSens Air data route;
- CareSens/Samsung Health/Health Connect end-to-end latency;
- Galaxy Watch8 compatibility and power behavior;
- Samsung Health Data SDK partner access;
- physical Watch8 touch, AOD, reboot, and phone/watch disconnect behavior;
- Android instrumentation tests on representative devices/emulators;
- release signing and Google Play policy review;
- real-device Health Connect background execution after denial, reboot, and process death.
