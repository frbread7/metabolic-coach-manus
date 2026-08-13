# Metabolic Coach

Metabolic Coach is a phone-and-watch wellness application focused on one question:

> What is the best action I can take right now to support my metabolic health?

The project contains an Android phone data hub, a touch-first Wear OS application, and a
resource-only Watch Face Format (WFF) watch face for Wear OS 6. The primary hardware target is the
Samsung Galaxy Watch8.

> **Development status:** engineering alpha. Version 1 uses a user-configured Nightscout server as
> its glucose source while Health Connect remains the activity source. The Nightscout refactor
> passed the repository's local static pipeline on 2026-07-25. The user accepted the phone-side
> `v0.2` Nightscout gate on 2026-08-01 after validating URL configuration, current glucose, trend,
> delta, timestamp, offline cache, retry behavior, and crash-free operation. Physical Galaxy
> Watch8 `v0.3` acceptance was user-reported complete on 2026-08-02 and is recorded in a
> privacy-sanitized acceptance record. Android instrumentation, production-signing, and
> store-policy gates remain outstanding. The user accepted the phone-side `v0.4.1` freshness fix.
> `v0.4.2` was physically accepted by user report. The user reported both the `v0.5.0` phone-only
> local-history gate and the APOS-reviewed `v0.5.1` History Explorer gate passed on 2026-08-04.
> The bounded `v0.6.0` post-meal walk increment is `Engineering: PASS / Physical: DEFERRED`.
> The APOS-approved `v0.6.1` confirmed rapid-rise walk increment is
> `Engineering: PASS / Physical: DEFERRED`. The APOS-approved `v0.6.2` prolonged-inactivity WALK
> increment is `Engineering: PASS / Physical: DEFERRED`; its full local pipeline, independent
> review, final APOS gate, and artifact checks passed. Its real-device rows remain `NOT RUN`.
> The behavior-neutral `v0.7.0` integrated WALK coaching RC is in engineering verification; its
> consolidated phone/Galaxy Watch8 gate remains explicitly deferred.
> Deferred work is never described as physically accepted.
> It is a wellness tool, not a medical device, and must not replace the CGM vendor app, glucose
> alarms, professional advice, or a personal care plan.

## Milestones

These are acceptance milestones, not a claim that later-stage code is absent. Existing
synchronization and coaching foundations remain frozen while the current milestone is validated.

| Version | Acceptance milestone | Status |
| --- | --- | --- |
| `v0.1` | Infrastructure: phone, Wear app, WFF watch face, shared architecture, persistence, and build/package pipeline | Complete |
| `v0.2` | Nightscout integration: provider isolation, configuration, retry/cache behavior, normalized repository flow, and phone-first live acceptance | Accepted 2026-08-01 |
| `v0.3` | Watch synchronization: validate the existing Data Layer path and packaged watch components on a physical phone and Galaxy Watch8 | Accepted by user report 2026-08-02; sanitized record stored |
| `v0.4` | Glycemic Goal Planner plus current-glucose freshness validation | `v0.4.1` phone acceptance reported complete; superseded by the `v0.4.2` planner milestone gate |
| `v0.4.2` | Saved planning milestones: multiple targets, one selected detail, fixed dates, migration, and export | Accepted by user report |
| `v0.5.0` | Phone-only local history foundation: explicit retention, source-scoped resumable backfill, export/reset safety | Accepted by user report 2026-08-04 |
| `v0.5.1` | Phone-only local trend chart and selected-period GMI after history acceptance | Accepted by user report 2026-08-04 |
| `v0.6.0` | One post-meal delayed walk prompt with exact-source safety, Start/Snooze, completion, and descriptive follow-up | Engineering PASS / Physical DEFERRED |
| `v0.6.1` | Confirmed two-reading rapid-rise walk coaching through the existing canonical pipeline | Engineering PASS / Physical DEFERRED |
| `v0.6.2` | Fail-closed prolonged-inactivity WALK coaching only | Engineering PASS / Physical DEFERRED |
| `v0.7.0` | Behavior-neutral integrated walk-coaching release candidate | Engineering verification in progress / Physical DEFERRED |
| `v0.9.0` | One-week personal beta with documented reliability, battery, and safety observations | Planned after feature gates |
| `v1.0` | Stable daily-use release with production signing and all release gates complete | Planned |

The `v0.3` physical gate is closed by the user's report, with the sanitized record retained for
audit. `v0.4` remains deliberately limited to the phone-side planner foundation plus the scoped
freshness hotfix: bounded Nightscout
history backfill, provider-independent calculations, configurable targets, and phone UI. It does
not change Wear synchronization, the watch face, coaching rules, or notifications. `v0.4.2` adds
saved phone-only milestones on top of that foundation and preserves the same boundary. `v0.5.0`
adds the explicit local-history foundation. `v0.5.1` adds a read-only exact-source History Explorer
and descriptive selected-period GMI without changing current glucose, Wear, or coaching paths.
See the
[milestone process](docs/MILESTONE_PROCESS.md), [physical acceptance checklists by milestone](docs/PHYSICAL_ACCEPTANCE_CHECKLISTS.md),
[cumulative physical-test backlog](PHYSICAL_TEST_BACKLOG.md),
[v0.3 Wear acceptance checklist](docs/V0.3_WEAR_ACCEPTANCE.md),
and [the v0.3 acceptance record](docs/acceptance/V0_3_PHYSICAL_ACCEPTANCE.md).

## What is implemented

| Area | Current implementation |
| --- | --- |
| Phone hub | Nightscout glucose retrieval with explicit multi-server selection, bounded retry/cache retention, source-scoped local history and resumable range backfill; a local-only exact-source trend explorer and selected-period GMI; Health Connect activity reads; Room schema v10 coaching provenance and saved planner milestones; configurable Glycemic Goal Planner metrics/scenarios; settings; coaching rules; notifications; daily summary; streaming JSON export; confirmation-gated local erase; and revisioned Wear Data Layer publishing |
| Wear app | Three-page touch-only horizontal pager, glucose/activity display, walk and stair actions, home countdown with completion haptic, explicit queued/rejected action results, durable snooze outbox, pending-session reconciliation, direct watch notifications, and complication providers; no bezel dependency |
| Watch face | Separate WFF v4 resource-only package with clock, glucose/trend/delta/age, steps/floors, battery, coach action, reduced ambient content, and selectable accent configuration |
| Coaching | `v0.7.0` integrates the unchanged post-meal, confirmed rapid-rise, and fail-closed prolonged-inactivity WALK paths in one engineering RC. Inactivity requires current same-day Health Connect activity context, uses an algorithm-v4 stable episode identity, is revalidated for display and Start, and expires at the earlier glucose/activity freshness boundary. Automated stairs remain disabled. |
| Intervention lifecycle | Idempotent start/complete commands, immutable phone-authored recommendation snapshots, deferred completion reconciliation, configurable start/snooze expiry, durable completion of an existing offline session, terminal expiry for orphan completions, and exact trigger, recommendation, activity-dose, baseline, source, and follow-up provenance |
| Personal observations | Cautious effect summaries plus prospective-only timing observations. Generic/post-meal bucket widths, per-bucket sample floor, comparable-bucket count, follow-up matching width, and baseline glucose band are configurable (conservative defaults: 5/15 minutes, 8 samples, 2 buckets, 15 minutes, and 20 mg/dL); a unique lowest observed median must also have strict quartile separation. Results never change coaching automatically and remain experimental |
| Synchronization | Versioned `DataMap` state with persistent phone instance/revision/reset metadata, stale pre-erase command rejection, terminal replay deduplication for every quick action, a bounded Wear outbox for non-session commands such as snooze, and a pending-mutation/tombstone session replica over Wear OS Data Layer (`play-services-wearable` 20.0.1) |
| Presentation | Dark, system, and high-contrast themes plus configurable font scale on phone and Wear; watch-face accent is configured through WFF, and motion is intentionally limited to useful pager/session transitions |
| Extensibility | A normalized `GlucoseProvider` boundary, repository/use-case/storage boundaries, Hilt multibindings, and provider-agnostic coaching and Wear synchronization suitable for future xDrip, Health Connect glucose, direct vendor, or other CGM providers |

## Important integration status

- **Nightscout is the only active Version 1 glucose provider.** The intended daily pipeline is
  `CareSens Air sensor → CareSens Air app → xDrip+ → Nightscout → Metabolic Coach phone`.
  Metabolic Coach calls Nightscout's HTTP API; it does not connect to the sensor, CareSens app, or
  xDrip broadcast interface.
- **The phone owns all Nightscout configuration and network access.** Settings support multiple
  named server URLs, one explicit active server, polling interval, connection timeout, retry
  interval/count, and HTTPS enforcement. Servers are isolated by source identity. The app never
  silently fails over to another configured server, and the watch never receives server URLs or
  credentials.
- **Public, unauthenticated Nightscout endpoints are supported in Version 1.** A request
  authentication boundary exists for a future credential-backed implementation. Credentials are
  rejected in URLs and must not be stored in ordinary settings or sent through Wear Data Layer.
- **HTTPS is the default and strongly recommended.** HTTP can be enabled explicitly for a local or
  test server, but it exposes glucose data to the network and is not appropriate for an
  internet-hosted server.
- **Health Connect is the activity source in Version 1.** It reads steps, floors, heart rate,
  exercise-session aggregates, and active calories when records exist and permission is granted.
  Its legacy glucose adapter remains behind the provider abstraction for future work but is not a
  selectable Version 1 glucose source.
- **Direct CareSens and xDrip broadcast glucose are not active in Version 1.** No Bluetooth
  reverse engineering, private-app scraping, CareSens IPC, or broadcast receiver is used. Legacy
  adapter code is retained only as an extension boundary, and persisted provider modes migrate to
  Nightscout.
- **Direct Samsung Health Data SDK access is inactive.** The partner SDK is not bundled. Public
  distribution would require Samsung approval plus final package and signing-certificate
  registration.
- **Glycemic Goal Planner is phone-only in `v0.4`/`v0.4.2`.** It estimates GMI from time-weighted CGM mean
  glucose over a 14-day safety baseline and 30/60/90-day rolling windows, and shows mathematical future-mean scenarios for a selected
  target. Multiple saved milestones are supported, but exactly one is selected for detailed
  evaluation. They are not laboratory HbA1c results, treatment instructions, insulin/dose
  guidance, or promises that a target will be achieved. Wear synchronization and coaching do not
  consume planner output.
- **v0.5.1 reads downloaded history locally for a trend chart and selected-period GMI.** The normal
  refresh and explicit Settings backfill remain the only ways data is collected. Opening History
  never performs network or backfill work, and 24-hour/7-day periods never show numeric GMI.

## Project structure

| Module | Responsibility |
| --- | --- |
| `:core:model` | Platform-neutral glucose, activity, history, settings, Glycemic Goal Planner, coaching, session, and sync models |
| `:core:domain` | Repository contracts, provider-independent time-weighted glycemic calculations/scenarios, coaching and shared exercise-safety rules, follow-up selection, settings validation, and personal observation analysis |
| `:core:data` | Room schema v10 with exported schemas 1–10, DataStore, asynchronous Nightscout client/parser/cache/retry/range-backfill implementation, source-scoped history checkpoints and pruning, planner-settings/milestone export, immutable recommendation snapshots, retained future-provider boundaries, and repository implementations |
| `:core:sync` | Wear Data Layer transport, versioned codec, paths, and sync repository |
| `:phone` | Android companion UI, permissions, background refresh, notifications, and watch-command handling |
| `:wear` | Wear OS UI, local state/session storage, notifications, and complication data sources |
| `:watchface` | WFF v4 XML and resources; no application code |

The phone and Wear app use `com.young.metaboliccoach` on different device classes so the Data
Layer can authenticate them as the same signed application. The watch face is a separate package,
`com.young.metaboliccoach.watchface`, because WFF bundles must be separate from Wear app logic.

## Build

Prerequisites:

- JDK 17
- Android SDK Platform 36 and current Build Tools
- Android Studio with Wear OS 6 support
- An Android phone or emulator for the phone app
- A Wear OS 6 device/emulator for the Wear app and WFF v4 face

```bash
./scripts/build-apks.sh
```

This validates WFF XML, runs the model, domain, data, sync, phone, and Wear unit tests, compiles the
Room Android migration-test source without executing it, runs the selected variant's phone, Wear,
and watch-face lint tasks, assembles all three APKs for that variant (debug by default), validates
the built watch-face APK, verifies APK signatures, checks that the phone and Wear certificates
match, copies deliverables into `artifacts/`, and creates a versioned five-file engineering ZIP.

Each milestone archive is named from its aligned module version and contains `phone.apk`,
`wear.apk`, `watchface.apk`, `CHANGELOG.md`, and an archive-specific `INSTALL.md`. The version is
derived from all three APK manifests and packaging fails if they disagree. Run
`./scripts/package-release.sh` to repackage already-verified artifacts without rebuilding. A
repeat debug build refreshes its same-version engineering ZIP only while that archive remains a
debug build. Debug APKs can never replace a same-version release archive. A different release
archive for an existing version is not overwritten unless `MC_PACKAGE_OVERWRITE=1` is set
intentionally. Local builds are preferred for engineering checkpoints to preserve GitHub Actions
quota; the workflow remains available for meaningful CI or release checkpoints.

The current debug artifacts are generated by the Nightscout milestone verification pipeline;
exact evidence and hashes are recorded in [Testing](docs/TESTING.md). They remain debug-signed
engineering artifacts, not production releases. A signed release requires environment-only
`MC_RELEASE_*` credentials; see
[Development](docs/DEVELOPMENT.md) and [Release and signing](docs/RELEASE.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Nightscout, Health Connect activity, and future provider integrations](docs/INTEGRATIONS.md)
- [Milestone development and cross-session handoff process](docs/MILESTONE_PROCESS.md)
- [Physical acceptance checklists by milestone](docs/PHYSICAL_ACCEPTANCE_CHECKLISTS.md)
- [Cumulative deferred physical-test backlog](PHYSICAL_TEST_BACKLOG.md)
- [v0.3 Galaxy Watch8 physical acceptance checklist](docs/V0.3_WEAR_ACCEPTANCE.md)
- [v0.3 privacy-sanitized physical acceptance record](docs/acceptance/V0_3_PHYSICAL_ACCEPTANCE.md)
- [v0.4.1 current-glucose freshness hotfix report](docs/V0.4_1_FRESHNESS_HOTFIX.md)
- [v0.6.1 confirmed rapid-rise engineering contract](docs/V0_6_1_RAPID_RISE_COACHING.md)
- [v0.6.2 prolonged-inactivity WALK engineering contract](docs/V0_6_2_INACTIVITY_WALK_COACHING.md)
- [v0.7.0 integrated WALK coaching RC and consolidated physical checklist](docs/V0_7_0_INTEGRATED_WALK_RC.md)
- [v0.5.0 local history foundation and phone acceptance gate](docs/V0_5_0_HISTORY_FOUNDATION.md)
- [v0.5.0 privacy-sanitized phone acceptance record](docs/acceptance/V0_5_0_PHYSICAL_ACCEPTANCE.md)
- [v0.5.1 History Explorer architecture and phone acceptance checklist](docs/V0_5_1_HISTORY_EXPLORER.md)
- [v0.4.2 saved planning milestones architecture and phone acceptance gate](docs/V0_4_2_SAVED_MILESTONES.md)
- [Development and build guide](docs/DEVELOPMENT.md)
- [Testing strategy, milestone acceptance, and release gates](docs/TESTING.md)
- [User guide](docs/USER_GUIDE.md)
- [Privacy and safety](docs/PRIVACY_AND_SAFETY.md)
- [Release, signing, and distribution](docs/RELEASE.md)
- [Installation guide](INSTALL.md)
- [Changelog](CHANGELOG.md)

## Platform rationale

- Wear OS 6 targets API 36, and WFF v4 has a minimum of Wear OS 6/API 36:
  [Wear OS 6 setup](https://developer.android.com/training/wearables/versions/6/setup) and
  [Watch Face Format](https://developer.android.com/training/wearables/wff).
- WFF packages are resource-only and must be separate from a Wear app containing logic:
  [WFF setup](https://developer.android.com/training/wearables/wff/setup).
- Persistent `DataItem` state is buffered when devices disconnect; matching package names and
  signatures protect phone/watch communication:
  [Wear OS Data Layer overview](https://developer.android.com/training/wearables/data/overview) and
  [Data items](https://developer.android.com/training/wearables/data/data-items).

## Safety

Metabolic Coach does not diagnose, treat, dose medication, predict emergencies, or provide a
substitute for clinical glucose alerts. Phone and Wear share one policy that suppresses exercise
actions for missing, future-dated, stale, below-threshold, or sufficiently fast-falling readings.
The falling-rate pause is configurable. These checks do not make the application a safety system.
Always verify glucose and follow the CGM vendor's instructions and your personal care plan.
