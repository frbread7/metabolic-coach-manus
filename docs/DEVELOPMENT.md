# Development guide

## Toolchain

The project is configured for:

- JDK 17;
- Gradle 8.13 through the checked-in wrapper;
- Android Gradle Plugin 8.11.1;
- Kotlin 2.2.20;
- compile/target SDK 36;
- phone minimum SDK 28;
- Wear app minimum SDK 30;
- WFF v4 watch face minimum SDK 36;
- Room 2.8.4 with database schema version 9;
- Health Connect client 1.1.0;
- OkHttp 5.3.0 with MockWebServer for Nightscout transport tests;
- kotlinx-serialization JSON 1.9.0 for provider/settings parsing;
- Google Play services Wearable 20.0.1.

Wear OS 6 is based on API 36. Follow the official
[Wear OS 6 setup guide](https://developer.android.com/training/wearables/versions/6/setup) when
creating an emulator.

Install Android SDK Platform 36, matching build tools, platform tools, an Android phone image if
needed, and a Wear OS 6 image. A physical Galaxy Watch8 is required for final validation.

## Open and sync

1. Open the repository root in Android Studio.
2. Select JDK 17 for Gradle.
3. Allow Gradle to download dependencies.
4. Confirm SDK Platform 36 is installed.
5. Run a domain test before launching an app:

```bash
./gradlew :core:domain:test
```

Use the Gradle wrapper rather than a machine-global Gradle installation.

## Common commands

```bash
# Full debug verification pipeline and APK handoff
./scripts/build-apks.sh

# WFF XML-only validation
./scripts/validate-watchface.sh
```

`build-apks.sh` validates WFF, runs the model, domain, data, sync, phone, and Wear local test suites,
compiles the Room Android migration-test source, runs the selected variant's phone, Wear, and
watch-face lint tasks, assembles all three APKs, validates the built face, verifies every APK
signature, verifies that phone/Wear certificates match, and writes named artifacts plus SHA-256
hashes under `artifacts/`. It forces every selected task to rerun, disables Gradle build-cache
reuse and Kotlin incremental compilation, and therefore cannot inherit a filtered test result or
combine bytecode compiled against different shared-model constructor ABIs. Migration-test
compilation does not execute instrumentation.

Targeted iteration remains available:

```bash
./gradlew :core:model:test \
  :core:domain:test \
  :core:data:testDebugUnitTest \
  :core:data:compileDebugAndroidTestKotlin \
  :core:sync:testDebugUnitTest \
  :phone:testDebugUnitTest \
  :wear:testDebugUnitTest
./gradlew :phone:compileDebugKotlin :wear:compileDebugKotlin
```

Room exports 1–9 are committed under `core/data/schemas/`. The v9 `DatabaseMigrationTest` Android
instrumentation source covers migration from schema 1 and every supported starting version 2–8
through schema 9, including the phone-only saved-planning-milestone and history-management tables. It has compiled locally,
but its migrations still need execution on an Android runtime:

```bash
./gradlew :core:data:connectedDebugAndroidTest
```

Do not report the migration suite as executed when no device or emulator completed that command.

Do not report a command as passing without reading its fresh result. Avoid `clean` during ordinary
iteration; targeted tasks are faster and preserve useful outputs.

### Release APK pipeline

The release variant is minified and reads signing material only from the environment. Set all four
variables:

```bash
export MC_RELEASE_KEYSTORE_PATH=/absolute/path/to/release.keystore
export MC_RELEASE_STORE_PASSWORD='...'
export MC_RELEASE_KEY_ALIAS='...'
export MC_RELEASE_KEY_PASSWORD='...'
MC_BUILD_VARIANT=release ./scripts/build-apks.sh
```

The script rejects a release invocation when any variable is missing or the keystore path does not
exist. Keep these values out of shell history, source control, screenshots, and build logs; use an
approved CI secret manager for real releases.

Running raw `assembleRelease` without the variables can produce minified unsigned APKs for static
package validation:

```bash
./gradlew :phone:assembleRelease :wear:assembleRelease :watchface:assembleRelease
```

Those `*-release-unsigned.apk` files are not production deliverables. The current Nightscout
evidence is recorded in [Testing](TESTING.md#current-local-verification); regenerate it after every
future source/build change. Production signing and device/store validation remain separate gates.

## Run configurations

### Phone

Run `:phone` on an Android device with network access to a synthetic/test Nightscout server.
Manual Refresh directly executes a foreground provider refresh. A configured active server
schedules connected-network WorkManager work at the selected interval, subject to Android's
15-minute periodic minimum and inexact execution. Health Connect remains the activity source;
grant only the activity record types needed for testing and use synthetic data.

### Wear

Run `:wear` on a Wear OS 6 device/emulator paired with the Android test device. Phone and Wear APKs
must have the same package name and signing certificate for Data Layer communication. Debug builds
from one checkout normally share the Android debug certificate.

### Watch face

Run `:watchface` separately. WFF packages cannot contain Wear app logic. Install `:wear` first so
the fixed complication provider components exist, then install/select the face.

Android Studio provides WFF-aware editing and deployment. Also run:

```bash
./scripts/validate-watchface.sh \
  watchface/build/outputs/apk/debug/watchface-debug.apk
```

The script uses checksum-pinned artifacts from the official
[Google WFF validator](https://github.com/google/watchface), validates schema v4, rejects DEX in
the package, and measures active/ambient memory. Record the validator version and result from a
fresh run against each release candidate.

## Code conventions

- Put business models in `:core:model`.
- Put deterministic rules and interfaces in `:core:domain`.
- Adapt Android/vendor APIs in `:core:data` or a dedicated infrastructure module.
- Keep phone/watch serialization in `:core:sync`; increment the schema version for incompatible
  changes.
- Keep third-party access on the phone.
- Preserve measured and received timestamps separately.
- Store glucose internally in mg/dL and convert only for display.
- Add no vendor SDK until its license, authorization, data contract, and test strategy are known.
- Never log glucose values, Nightscout bodies/headers/URLs/credentials, Health Connect records, raw
  broadcast extras, or user identifiers in production.
- Preserve the existing Room 1→2, 2→3, 3→4, 4→5, 5→6, and 6→7 migrations plus 7→8; add a
  migration and exported schema whenever the database version changes.
- Preserve exact recommendation, trigger, activity-dose, baseline, source, and follow-up provenance
  across model, Room, sync, action, and analysis changes. Never reconstruct prospective timing
  provenance for legacy or manual sessions.
- Keep recommendation snapshots phone-authored and immutable by stable ID. Publication retries and
  local phone actions must use the canonical persisted snapshot; watch-echoed fields may detect a
  conflict but must not author session dose or provenance.
- Keep Nightscout server selection explicit. Partition cache, conditional-request metadata, Room
  queries, baselines/follow-ups, and observations by exact source ID; never auto-failover or merge
  configured servers.
- Keep Nightscout settings and future credentials phone-only. Reject credentials in URLs and use a
  secure per-server credential store behind `NightscoutRequestAuthenticator` before adding
  authenticated-server support.
- Preserve phone instance/revision metadata, terminal-command replay history, completion-over-start
  acknowledgement ordering, durable data-reset token/command epoch matching, the Wear
  pending-mutation/tombstone reducer, and the bounded generic command outbox when changing Data
  Layer state.
- Route phone-owned export, erase, provider ingestion/refresh, follow-up finalization,
  settings/meal writes, and quick-action mutations through `PhoneDataMutationGate`. Where both the
  gate and the command-processor mutex are needed, acquire the mutation gate first. Slow,
  coroutine-cancellable provider work uses `withPreemptibleProviderLock`; local mutations use
  `withLock` so they cancel that work before waiting without weakening the erase boundary.
- Keep the personal-data export schema versioned, deterministic, bounded-memory, and complete for
  every Room table/exported coaching setting. Nightscout connection configuration and future
  credentials stay excluded unless a separately reviewed export contract changes that boundary.
  Update export tests and documentation whenever storage changes.
- Keep every user-facing settings control and validator on the shared `CoachSettingsBounds`
  contract. Quiet/working-hour controls must preserve one-minute precision.

## Version 1 provider boundary

Nightscout is the only active glucose provider in both debug and release builds. Both manifests omit
the xDrip receiver and receive permission, the Settings UI exposes no alternate glucose selector,
and the repository policy converts every persisted legacy provider mode to Nightscout. Keep the
mode-migration policy test in both variant verification paths.

`GlucoseProvider`, normalized `GlucoseReading`, repository contracts, Hilt multibindings, coaching,
and Wear synchronization remain provider-agnostic. Retained CareSens, Health Connect glucose, and
xDrip adapter code is an inactive extension boundary, not permission to enable it.

For a new provider:

1. document authorization, units, timestamps, source identity, outage, cache, and secret semantics;
2. implement the adapter and observable provider state without importing it into coaching or sync;
3. add explicit configuration/selection and migration policy;
4. add synthetic parser/network/retry/cache/switching tests;
5. update privacy, user, integration, release, and test documentation.

Do not register direct CareSens or xDrip components, reverse engineer Bluetooth, or add automatic
fallback without a separate reviewed design.

## Adding coaching behavior

1. Define a configurable setting instead of a literal threshold.
2. Add validation bounds.
3. Add a deterministic rule test for positive, negative, missing, future, stale, low, fast-fall,
   quiet-hours, cooldown, and daily-limit cases as applicable.
4. Preserve the shared phone/Wear safety policy, deterministic recommendation ID, and explicit
   validity window.
5. Use observational language and avoid causal, medical, best, or ideal timing claims.
6. Verify phone notification, watch notification, app button, and complication action paths.

## Changing prospective timing analysis

Treat the current timing policy as a versioned safety boundary. Generic/post-meal bucket widths,
the timing-specific sample floor, comparable-bucket minimum, actual-follow-up matching width, and
baseline glucose band are user settings with conservative defaults. Each comparison bucket
requires at least `max(minimumObservationSamples, minimumTimingBucketSamples)` samples, the
configured comparable-bucket count must qualify, and the unique lowest observed median must also
pass strict quartile separation. Preserve the matched source, trigger, recommendation algorithm,
activity dose, baseline state, threshold, and follow-up-delay cohort dimensions plus the
implemented meal/overlap/low-follow-up exclusions. Any new analysis threshold must be added to
`CoachSettings`, validation, DataStore, Data Layer sync, phone UI, and positive/suppression tests
rather than introduced as a private algorithm constant.

Timing observations are display-only. Do not feed them into settings, thresholds, rules, or
recommendation timing without a separately reviewed feature and evidence model.

## Adding UI behavior

The Galaxy Watch8 target is round and touch-only. The Wear app currently uses a three-page
horizontal pager for coach/home, quick actions, and Today. Preserve large targets, short labels,
high contrast, synchronized theme/font scale, and no bezel dependency. Verify clipped text and
system-edge gesture behavior at every supported font scale. Keep motion functional and minimal:
pager movement and the session-start return to Home may animate, while ambient/decorative motion
stays off. Verify countdown completion haptics, smoothness, and power cost on physical hardware.

WFF watch faces cannot host Compose logic. Put interactions in the Wear app or complication
providers and use a complication tap action.

## Local data during development

Use synthetic glucose and activity records. Do not copy real health databases, screenshots,
exports, logcat dumps, or personally identifiable source metadata into the repository.

The in-app JSON export is plain text and can contain sensitive data. Never use a real personal
export as a fixture, attach it to an issue, or leave it in a shared build directory.

Resetting an app with `adb shell pm clear` is destructive to that app's local development data.
Use it only when the data is disposable and the target package/device has been verified.

## Troubleshooting

### Phone and watch do not synchronize

- Confirm both use `com.young.metaboliccoach`.
- Confirm both were built with the same signing identity.
- Confirm Google Play services and pairing are healthy.
- Open both apps once after installation.
- Inspect Data Layer service logs without printing health payloads.
- Test disconnect/reconnect; `DataItem` state should synchronize after reconnection.

### Nightscout returns no glucose

- Confirm a nonblank server is selected and Settings validation passes.
- Inspect the normalized base URL; do not include `/api/v1`, credentials, query parameters, or a
  fragment.
- Use MockWebServer for reproducible development before testing a live server.
- Check DNS, TLS, timeout, HTTP status, and response shape without logging glucose or secrets.
- Confirm `/api/v1/entries/sgv.json?count=300` returns a JSON array with usable `sgv` and
  `date`/`dateString` values.
- Use manual Refresh to isolate foreground retrieval from inexact periodic WorkManager behavior.
- When testing multiple servers, prove only the selected server is called and source histories do
  not mix.

### Health Connect returns no activity

- Check SDK availability and the specific activity permissions.
- Confirm the source app actually writes the requested record type.
- Inspect Health Connect's data and access screens.
- Compare source timestamps and local timezone.
- Check background-read feature support and permission only for activity background behavior;
  their absence must not cancel Nightscout polling.

### Watch face shows placeholders

- Install and launch the Wear app first.
- Wait for a phone state publication.
- Confirm complication services are enabled in the Wear manifest.
- Reselect the face after reinstalling either watch package.

### Linux ARM64 build host

Android build-tool distributions may include host binaries that are not native to an ARM64 Linux
machine. If AAPT2 reports an execution or timeout error, reproduce on a supported x86-64 host or
configure a verified emulation environment. Do not treat an emulation timeout as an application
compile error, and do not ship an APK that has not been rebuilt and verified on a supported release
host.
