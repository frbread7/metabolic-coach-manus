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
| `CoachRuleEngineTest` / `RapidRiseConfirmationPolicyTest` / `InactivityConfirmationPolicyTest` | Reason-specific IDs/validity; two-reading exact-source rapid confirmation; threshold, trend fallback, gap/timestamp/tie failure; post-meal/rapid/inactivity priority; WALK-only inactivity; activity missing/source/timestamp/day/freshness/threshold boundaries; stable algorithm-v4 episode identity; shared missing/future/stale/low/fast-fall safety; quiet/working hours, snooze, cap, and cooldown |
| `ExerciseSafetyPolicyTest` | Phone/Wear safety parity, complete source/trigger/safety provenance, rapid-pair and inactivity-episode supersession, exact action-expiry boundaries, activity/working-hour revalidation, quiet hours, active-session suppression, and falling-rate fallback |
| `ActionDisplayDeadlinePolicyTest` | Exact ordinary/overnight quiet and working-hour display deadlines, earlier immutable validity, equal-boundary semantics, non-inactivity isolation, and fail-closed ambiguous/nonexistent DST boundaries |
| `SettingsValidatorTest` | Defaults plus shared coaching bounds, retained legacy Health Connect-origin validation, falling-rate, follow-up, command-expiry, activity goals, and all personal-observation analysis controls |
| `FollowUpReadingSelectorTest` | Exact-source filtering, at/after-due preference, pre-deadline waiting, deterministic deadline fallback, and missing finalization |
| `ObservationAnalyzerTest` | Manual effects, legacy safety-provenance exclusion, below-threshold exclusion for effects/timing, configurable generic/post-meal/follow-up/baseline buckets, configurable sample and comparable-cohort gates, unique separated median, exact-source/follow-up eligibility, meal provenance, and cautious wording |
| `NightscoutSettingsValidatorTest` | Multiple-server limits and selection, URL normalization, HTTPS enforcement, credential/query/fragment rejection, valid TCP ports, and polling/timeout/retry bounds |
| `NightscoutJsonParserTest` | Nightscout response parsing, trend mapping, stable IDs, ordering, delta/rate calculation, timestamp fallbacks, malformed/unusable rows, and bounded value/time acceptance |
| `NightscoutProviderTest` | Provider state flow, success, conditional cache reuse, retry/non-retry and response-size classification, retained cache on failure, cancellation, and per-server isolation/switching |
| `GlucoseHistoryRetentionPolicyTest` | Deterministic 90-day/1-year cutoffs and no-cutoff keep-all behavior |
| `NightscoutProviderTest` range case | Bounded older-range reads use the range endpoint without publishing an older current state |
| `GlycemicGoalPlannerTest` | GMI conversion, time-weighted 14/30/60/90-day metrics, coverage and long-gap handling, source discontinuity, horizon equations, remaining-window milestones, temporal states, deterministic ordering, and low-glucose-risk suppression |
| `HistoryExplorerTest` | Fixed/custom/DST range resolution, deterministic exact-source chart ordering, disconnected gaps, bounded aggregation with extrema preservation, duration/coverage gates, and neutral low-glucose GMI qualification |
| `HistoryExplorerLoaderTest` | Exact-source local-only loading with no refresh/provider call plus stale-result rejection after newer periods, source changes, custom-draft invalidation, and navigation away |
| `OkHttpNightscoutApiClientTest` | MockWebServer request path/query/headers, conditional responses, redirect refusal, bounded declared/streamed response size, and future-authenticator hook without a real server |
| `NightscoutSettingsJsonCodecTest` | Stable multi-server DataStore encoding/decoding and malformed stored-value recovery |
| `XdripGlucoseIngestorTest` | Retained inactive-adapter input validation; it does not prove or enable a Version 1 broadcast route |
| `HealthConnectOriginSelectionPolicyTest` | Single-origin auto-pinning, multi-origin configuration gating, exact filtering, unavailable saved-origin retention, and deterministic discovery order |
| `ReleaseGlucoseProviderModePolicyTest` | Migration and build policy force every persisted legacy provider mode to the Version 1 Nightscout mode |
| `ExerciseSessionSummaryTest` | Daily valid-session count, total-duration aggregation, latest end time, and reversed-interval rejection |
| `WatchStateCodecTest` | State/command round trips including backward-compatible legacy settings, configurable observation analysis, recommendation validity/provenance, phone revision, and session acknowledgement; Nightscout connection settings remain absent from Wear payloads |
| `InterventionDaoLifecycleTest` | Idempotent start, one-active-session semantics, and Room follow-up lifecycle persistence |
| `RecommendationSnapshotMapperTest` / `RecommendationSnapshotPersistenceTest` | Complete snapshot mapping, immutable canonical retry, publication-authority gating, and rapid/inactivity snapshot retention/invalidation across cooldown, new pairs or movement episodes, stable refreshes, expiry, and source changes |
| `QuickActionHandlerTest` | Selected-provider baseline provenance, required phone-authored snapshot lookup, echoed-provenance conflict rejection, exact rapid-pair and latest-activity inactivity action-time revalidation, WALK-only inactivity enforcement, snapshot-owned intervention dose, complete prospective recommendation/trigger/rate/threshold capture, no invented manual provenance, delayed-delivery safety boundaries, generic start expiry, idempotent start, deferred completion, delayed known-session completion, and terminal orphan expiry |
| `PhoneRefreshCoordinatorTest` | Canonical capture/revalidation before publication, invalid inactivity omission, phone/Watch effective-action parity, and policy-bounded phone-notification lifetime |
| `SyncSchedulerTest` | Nightscout connected-network scheduling, configured interval and 15-minute minimum, independence from Health Connect background permission, fallback behavior, and coroutine-cancellation propagation |
| `DeferredCompletionPolicyTest` | Rejected-prerequisite propagation and session matching for completion-first delivery |
| `PhoneCommandProcessorTest` | Exactly-once terminal replay: a persisted rejected command triggers watch-state republication without invoking mutation logic |
| `CommandDataEpochPolicyTest` | Legacy compatibility before erase and strict current-reset-token matching afterward |
| `PhoneDataMutationGateTest` | Process-wide serialization prevents boundary crossing; local operations preempt cancellable provider work and retain priority over already queued provider work |
| `GlycemicPlanningMilestoneRepositoryImplTest` | One-time legacy migration, fixed target dates, idempotent creation, selected-ID fallback, past-target edit freezing, and reset |
| `PersonalDataJsonWriterTest` | Deterministic versioned JSON, planner-settings/milestone export, escaping/control characters, non-finite-number handling, binary encoding, and empty-table separators |
| `SessionAckOrderingPolicyTest` | Completion-over-start ordering, older replay protection, and the rejected-start/completion/replayed-start chain |
| `WearSessionReplicaReducerTest` | Persistent pending start/completion, transport state, tombstone protection, and all acknowledgement outcomes |
| `WearCommandOutboxPolicyTest` | Generic command ordering, stable-ID deduplication, and removal |
| `WearActionPolicyTest` | Explicit local terminal rejection for busy, expired, and superseded quick actions |
| `SessionProgressTest` | Configured walk countdown bounds and zero-bound behavior |
| `RemoteStateOrderPolicyTest` | Same-instance revision ordering, new-instance epochs, and guarded legacy-state handling |
| `RemoteDataResetPolicyTest` | First-seen reset handling, same-token idempotence, legacy-state compatibility, and missing-cache recovery |
| `TimeSettingValueTest` | Exact minute/hour preservation and valid minute-of-day clamping |

Exported Room schemas 1–9 are committed. `DatabaseMigrationTest` is an Android instrumentation
suite for schema 1→9 and every supported starting version 2–8→9, including safe defaults, preserved
legacy rows, nullable prospective timing-provenance columns, and an initially empty recommendation
snapshot table plus the phone-only milestone table. Its source has compiled locally, but the suite has not executed because it requires
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

The physical-device rows for every milestone are indexed in
[Physical acceptance checklists by milestone](PHYSICAL_ACCEPTANCE_CHECKLISTS.md). That index
distinguishes accepted evidence from pending or draft gates; the detailed v0.3 and v0.5.0 documents
remain authoritative for their respective runs.

- `v0.2` implementation gate: the full static pipeline, isolated provider/repository tests,
  parsing, retry, cache, server switching, manifests, documentation, and debug package must pass.
- `v0.2` live acceptance gate: accepted by the user on 2026-08-01 after phone-side validation of
  configuration, current glucose, trend, delta, timestamp, offline cache, retry, and crash-free
  behavior. A history graph was outside the milestone and was not required for acceptance.
- `v0.3` gate: the user reported installation, synchronization, reconnect, stale-state, round/AOD,
  readability, stability, and battery checks complete on 2026-08-02. The report is retained as
  [the privacy-sanitized acceptance record](acceptance/V0_3_PHYSICAL_ACCEPTANCE.md); it is not a
  substitute for reproducible device logs.
- `v0.4.1` gate: the phone-side freshness fix was accepted by user report; its tests and historical
  artifact remain evidence, not a replacement for the next gate.
- `v0.4.2` gate: run the planner/milestone/provider regression tests, schema-8 source compilation,
  persistence/export checks, phone UI compile/lint, and a phone-only milestone acceptance test.
  Verify that current glucose freshness remains correct and that Wear payloads, coaching
  decisions, notifications, and watch-face resources are unchanged. Validate CGM-derived GMI
  wording, fixed target dates, one selected detail milestone, safety suppression, deterministic
  ordering, and no automatic milestone completion.
- `v0.5.0` gate: run history retention/backfill/provider tests, schema-9 migration source
  compilation, export/reset checks, phone compile/lint, and unchanged current-freshness/Wear/
  coaching/notification/watch-face regression suites. Physical phone acceptance must verify that
  the default 90-day history is preserved, policy changes require confirmation, pruning is
  source-scoped, one older range can pause/resume, process interruption presents a resumable
  checkpoint, and no network work is triggered by future chart surfaces. The user reported this
  physical phone gate passed on 2026-08-04; see the
  [privacy-sanitized acceptance record](acceptance/V0_5_0_PHYSICAL_ACCEPTANCE.md).
- `v0.5.1` gate: the APOS architecture review returned `GO WITH CONDITIONS`. Run the History
  range/chart/GMI/local-loader race tests, complete unchanged module suites, lint, release
  isolation, WFF validation, and aligned version/signature/ZIP audit. Then execute
  [the v0.5.1 phone checklist](V0_5_1_HISTORY_EXPLORER.md). No later coaching milestone is
  unlocked until that result is reviewed.
- `v0.6.1` gate: APOS returned `GO WITH CONDITIONS` for a two-reading same-exact-source rapid-rise
  walk increment. Post-meal must win overlap; production inactivity/stairs remain disabled; action
  display and Start must fail closed on incomplete, changed-source, or superseded provenance. Run
  the full local milestone pipeline and retain all device-only checks as physically deferred.
- `v0.6.2` gate: APOS returned `GO WITH CONDITIONS` for fail-closed prolonged-inactivity WALK
  coaching. Post-meal and then confirmed rapid rise retain priority. Test exact inactivity and
  activity-freshness boundaries, malformed/future/previous-day/cross-midnight activity, stable
  algorithm-v4 identity, immutable snapshot expiry, movement/source/settings supersession,
  phone/Wear display parity, processing-time Start revalidation, replay/idempotence, and explicit
  automated-stair exclusion. No provider request, scheduler/polling change, Room migration, Wear
  schema field, notification-action change, or session/replay semantic change is permitted.

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

On 2026-08-01, the user separately accepted the phone-side live Nightscout behavior listed in the
milestone gate above. This is user-reported physical evidence; it does not imply Galaxy Watch8,
Android instrumentation, production-signing, or extended outage/lifecycle acceptance.

The `v0.3` pre-install rerun on 2026-08-01 executed the sync, phone, and Wear unit suites plus
phone, Wear, and watch-face debug lint with cache/incremental reuse disabled. Gradle reported
`BUILD SUCCESSFUL` in 9m22s with all 200 selected tasks executed. No source code was changed for
this gate.

The failed `v0.4.0` CI run and artifact are preserved for comparison: workflow run `30795488452`,
artifact `8849324941`, with signing certificate SHA-256
`7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4`. They are historical
evidence and are not overwritten or relabeled.

Historical v0.4.1 focused provider/repository evidence (2026-08-03) passed locally with the ARM
host's Android linker compatibility prefix:

```text
QEMU_LD_PREFIX=/usr/x86_64-linux-gnu ./gradlew --no-daemon --no-parallel --max-workers=1 \
  --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process \
  :core:data:testDebugUnitTest \
  --tests com.young.metaboliccoach.core.data.provider.nightscout.NightscoutProviderTest \
  --tests com.young.metaboliccoach.core.data.repository.GlucoseRepositoryImplTest
BUILD SUCCESSFUL
```

The full v0.4.1 GitHub Actions result and hashes are historical evidence. The v0.4.2 GitHub Actions
artifact and phone physical acceptance are separate gates and are not inferred from local tests.

On 2026-08-03, the v0.4.2 saved-milestones checkout passed the authoritative local package script:

```text
QEMU_LD_PREFIX=/usr/x86_64-linux-gnu ./scripts/build-apks.sh
BUILD SUCCESSFUL in 7m06s
333 actionable tasks: 333 executed
```

The run passed model/domain/data-debug/data-release/sync/phone/Wear unit suites, Android
migration-test source compilation, phone/Wear/watch-face debug lint, all three debug assemblies,
APK signature checks, phone/Wear certificate equality, WFF v4 schema/memory validation, and exact
five-file ZIP packaging. The generated local test reports contain 52 suites, 288 test executions,
0 failures, 0 errors, and 0 skipped. Android instrumentation was compiled but not executed.

The local v0.4.2 debug package passed the metadata/signature/ZIP/privacy verifier with
`MC_EXPECTED_VERSION_NAME=0.4.2`, `MC_EXPECTED_VERSION_CODE=6`, and the expected v0.3 certificate
digest. It is still an engineering build; GitHub Actions must repeat the same continuity check from
the committed revision before phone physical acceptance.

Local v0.4.2 SHA-256 values:

```text
c079b323c076b8185d931e9f63e7cefa947b41411ac86cfd12dc7c1cc30a74f6  metabolic-coach-phone-debug.apk
9c3bcae9289edb4cd17da806c5eaa1c63e4fc5ed3dbffba6ec7fd42609eeb347  metabolic-coach-wear-debug.apk
fdf856f2d5ad6164a0b2b610d3ac39c1dbc0816cafc8305f3ae5ef47f6d52a10  metabolic-coach-watchface-debug.apk
5786592c93793a613a1cd7968c66d76bef0f2e90717b37080bafb9f8cf91cd41  MetabolicCoach-v0.4.2.zip
```

These local hashes are engineering evidence only. The GitHub Actions artifact must be built from
the committed revision with the accepted v0.3 certificate before phone physical acceptance.

### v0.4.2 CI engineering artifact gate

The v0.4.2 debug artifact must be built on GitHub Actions before the phone milestone retest.
The workflow requires the accepted v0.3 engineering keystore through the encrypted
`MC_DEBUG_KEYSTORE_BASE64` repository secret; it never generates or commits a replacement key.
The gate records the commit SHA, workflow run ID, signing certificate digest, and SHA-256 hashes,
and uploads exactly:

```text
metabolic-coach-phone-debug.apk
metabolic-coach-wear-debug.apk
metabolic-coach-watchface-debug.apk
MetabolicCoach-v0.4.2.zip
```

The v0.4.2 run is accepted only when all automated checks pass, every APK reports version
`0.4.2`/code `6`, the ZIP contains exactly `CHANGELOG.md`, `INSTALL.md`, `phone.apk`,
`watchface.apk`, and `wear.apk`, the v0.3 certificate matches, and the credential/privacy audit is
clean. A successful artifact build does not claim new Wear, watch-face, synchronization, or
coaching behavior; those modules are rebuilt only to keep the package metadata aligned. The failed
v0.4.0 run and artifact remain unchanged for comparison and audit.

### v0.5.0 local engineering artifact gate

On 2026-08-03, the v0.5.0 foundation passed the authoritative local package script with the ARM
host compatibility prefix:

```text
QEMU_LD_PREFIX=/usr/x86_64-linux-gnu ./scripts/build-apks.sh
BUILD SUCCESSFUL in 7m05s
333 actionable tasks: 333 executed
```

The run passed model/domain/data-debug/data-release/sync/phone/Wear unit suites, including the
history repository checkpoint/source-containment tests, Android migration-test source compilation,
phone/Wear/watch-face debug lint, all three debug assemblies, APK signature checks, phone/Wear
certificate equality, WFF v4 schema/memory validation, and exact five-file ZIP packaging. The fresh
JVM reports contain 55 suites, 296 test executions, 0 failures, 0 errors, and 0 skipped. Android
instrumentation was compiled but not executed because no Android runtime was attached.

The local v0.5.0 debug package passed the metadata/signature/ZIP/privacy verifier with version name
`0.5.0`, version code `7`, and the accepted v0.3 engineering certificate
`7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4`:

```text
435f867469ea611c9734b51b2602cae2906e0a3f66fc4af7a43347fdfb0e91c0  metabolic-coach-phone-debug.apk
fce9de3ba029a064f439956456484b7ed158336908c924d014cfbf42c4fa8397  metabolic-coach-wear-debug.apk
5c00457c40ebd2b9f386a89c66b535cdc39dc1ae3c35aa1b59bbc757ec8d123c  metabolic-coach-watchface-debug.apk
3fe5f6d3eff7a35361061e7b24280f92cf72153462456c2b75efb551637ed503  MetabolicCoach-v0.5.0.zip
```

These are local engineering artifacts only. The GitHub Actions run must repeat the certificate,
metadata, privacy, and ZIP checks from the pushed revision. Physical phone acceptance is still
required and must stop before chart/GMI or coaching work is unlocked.

### v0.5.1 local engineering artifact gate

On 2026-08-04, the v0.5.1 History Explorer candidate passed the authoritative local package script:

```text
QEMU_LD_PREFIX=/usr/x86_64-linux-gnu ./scripts/build-apks.sh
BUILD SUCCESSFUL in 7m37s
333 actionable tasks: 333 executed
```

The run passed model/domain/data-debug/data-release/sync/phone/Wear unit suites, Android migration-
test source compilation, phone/Wear/watch-face debug lint and assembly, WFF v4 schema and memory
validation, APK signature checks, and the exact five-file ZIP contract. The fresh reports contain
57 suites and 321 test executions with 0 failures, 0 errors, and 0 skipped. Android instrumentation
was compiled but not executed because no Android runtime was attached.

The separate CI-equivalent unsigned release check passed in 7m03s with 243 actionable tasks (73
executed, 16 from cache, 154 up-to-date). Release lint and assembly passed for all three modules,
and three generated phone release manifests contained no unofficial xDrip permission or receiver.

The final local debug package passed the version/signature/ZIP/credential verifier with version
name `0.5.1`, version code `8`, and the accepted v0.3 engineering certificate
`7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4`:

```text
645f449093a69b00244088253499a7cce1c0e27e0d508573edc5b87c21f86a67  metabolic-coach-phone-debug.apk
a3faab446c77d714630b51be86220868ee1570419abb0f54e594b75a0cc51e6f  metabolic-coach-wear-debug.apk
189617641d0023150f6b47ca8771f6fc25b9c2c5ebb4f32318f5dfee2dd54242  metabolic-coach-watchface-debug.apk
2d64a613e957867441c68bef7acbfc84e727ba9cac61948e5fd02044cf4a0761  MetabolicCoach-v0.5.1.zip
```

This is a local engineering candidate, not a physical acceptance result. The v0.5.1 phone checklist
must pass before any coaching milestone begins.

### v0.6.1 local engineering artifact gate

On 2026-08-12, feature revision `8b46771594809400dbf7620410eafd7dd24f504e` passed the
authoritative local milestone pipeline:

```text
./scripts/build-apks.sh
BUILD SUCCESSFUL in 10m 54s
333 actionable tasks: 333 executed
```

The run passed model/domain/data-debug/data-release/sync/phone/Wear unit suites, including rapid
pair ordering/identity, post-meal arbitration, cooldown/snooze/daily-cap behavior, immutable
snapshot retention and invalidation, exact-source action-time revalidation, production allowlist,
Wear codec/session replay, and phone/Wear fail-closed provenance. Android migration-test sources
compiled; phone/Wear/watch-face lint and debug assembly passed; all APK signatures and certificate
continuity passed; WFF v4 source/schema and APK memory-footprint validation passed; and the exact
five-file ZIP was produced.

Fresh JUnit reports contain 58 suites and 365 executions with 0 failures, 0 errors, and 0 skipped.
Android instrumentation was compiled but not executed because no Android runtime was attached.
Independent static review returned code `PASS` and architecture/safety `GO`, with no blockers.

The final debug package passed the metadata/signature/ZIP/credential verifier with versionName
`0.6.1`, versionCode `10`, and certificate
`7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4`:

```text
0916fb2921d7ddbfae995414377602040f17b00656c703af6fec4434fdd14ae4  metabolic-coach-phone-debug.apk
9a3be72f6bab8bbd683694b6ac352f00eea20ae50e1e00725d559a9dc1b0cb24  metabolic-coach-wear-debug.apk
fa2f4f041ad56a3e36417a8f7cd428671bb26c7e87f8dd74f6e1ee2cbb4d7f0b  metabolic-coach-watchface-debug.apk
bed24eaf69170140c261b3d6fc6d7bea1b7644c9e9cd2a5db56c0aa3cde7be91  MetabolicCoach-v0.6.1.zip
```

Status is `Engineering: PASS / Physical: DEFERRED`. The ZIP is a local engineering checkpoint, not
the promoted integrated coaching release candidate; all device-only rows remain in the cumulative
physical-test backlog.

### v0.6.2 targeted engineering gate

On 2026-08-12, the targeted domain/data/phone/Wear verification for prolonged-inactivity WALK
coaching reported:

```text
BUILD SUCCESSFUL
154 actionable tasks
```

The targeted gate covers policy boundaries, WALK-only arbitration, immutable recommendation
snapshot behavior, phone Start revalidation, and phone/Wear action-context regressions. This is
intermediate evidence only. Engineering remains `IN PROGRESS`, physical testing remains
`DEFERRED`, and the full regression/lint/APK/WFF/signature/package pipeline, independent final
review, final APOS decision, artifact metadata, hashes, and commit identity are pending.

Historical v0.2 debug artifact SHA-256 values:

```text
eb1dc99bd612970b975d17ff6e02e63c3529e38581f8a7d0bd50e7866bd2dbee  metabolic-coach-phone-debug.apk
e373a79b2dee352d6cc1ea798f32d9c1c8e6eab277a94d155bd8c3673f09e8a6  metabolic-coach-wear-debug.apk
76b0b050e1a201e41c2ad8185ef0aeaf06a294a20caa2857546059a9a47cd23b  metabolic-coach-watchface-debug.apk
477b8fd3bc7d9a05f88259d6568cf3cb3377d5ad7eb644b526c015aec9665885  MetabolicCoach-v0.2.zip
```

Those historical artifact sizes are 41,673,623 bytes for phone, 44,620,696 bytes for Wear, and 10,095 bytes
for the watch face. The documentation-refreshed five-file ZIP is 29,964,055 bytes. These are
debug-signed engineering artifacts. Extended live-server lifecycle, Android instrumentation,
physical-watch, production-signing, and store/privacy results are not implied.

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
For the current milestone, execute and retain results in
[the v0.3 physical acceptance checklist](V0.3_WEAR_ACCEPTANCE.md); this general matrix is not a
substitute for that evidence record.

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
- absent prior local reading, cross-source pairs, duplicate/tied timestamps, out-of-order input, and
  a newer stable or qualifying exact-source reading replacing a published rapid pair;
- prolonged inactivity with stairs enabled or disabled, proving the automated candidate remains
  WALK-only whenever walking reminders are enabled and is suppressed when walking is disabled;
- missing/blank activity source, future or inconsistent activity timestamps, previous-day and
  cross-midnight context, exact inactivity threshold, exact activity-stale boundary, new movement,
  activity-source change, working-hours exit, and processing-time Start revalidation;
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

- extended live Nightscout lifecycle behavior across TLS/DNS/captive-portal, server upgrades,
  authentication changes, and long outages beyond the accepted phone-side v0.2 checks;
- CareSens app/xDrip/Nightscout/phone/watch end-to-end latency and outage recovery;
- multi-server switching and isolation on physical phones;
- TLS, DNS, captive-portal, battery-saver, reboot, and background WorkManager behavior;
- Galaxy Watch8 compatibility and power behavior;
- Samsung Health Data SDK partner access;
- physical Watch8 touch, AOD, reboot, and phone/watch disconnect behavior;
- Android instrumentation tests on representative devices/emulators;
- release signing and Google Play policy review;
- real-device Health Connect activity background execution after denial, reboot, and process death.
