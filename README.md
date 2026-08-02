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
> Watch8, Android instrumentation, production-signing, and store-policy gates remain outstanding.
> It is a wellness tool, not a medical device, and must not replace the CGM vendor app, glucose
> alarms, professional advice, or a personal care plan.

## Milestones

These are acceptance milestones, not a claim that later-stage code is absent. Existing
synchronization and coaching foundations remain frozen while the current milestone is validated.

| Version | Acceptance milestone | Status |
| --- | --- | --- |
| `v0.1` | Infrastructure: phone, Wear app, WFF watch face, shared architecture, persistence, and build/package pipeline | Complete |
| `v0.2` | Nightscout integration: provider isolation, configuration, retry/cache behavior, normalized repository flow, and phone-first live acceptance | Accepted 2026-08-01 |
| `v0.3` | Watch synchronization: validate the existing Data Layer path and packaged watch components on a physical phone and Galaxy Watch8 | Physical acceptance ready; production-code freeze active |
| `v0.4` | Metabolic Coach: validate and tune the existing walk/stair recommendations without adding medical claims | Blocked on `v0.3` |
| `v0.5` | One-week personal beta with documented reliability, battery, and safety observations | Planned |
| `v1.0` | Stable daily-use release with production signing and all release gates complete | Planned |

Do not add production functionality while the `v0.3` physical gate is open. Work is limited to
verification, installation documentation, the physical acceptance record, and critical defects
that would prevent safe installation or synchronization. After the user reports results, stop for
architecture review before planning `v0.4`. See the [milestone process](docs/MILESTONE_PROCESS.md)
and [v0.3 Wear acceptance checklist](docs/V0.3_WEAR_ACCEPTANCE.md).

## What is implemented

| Area | Current implementation |
| --- | --- |
| Phone hub | Nightscout glucose retrieval with explicit multi-server selection, bounded retry and cache retention; Health Connect activity reads; Room schema v7 history; settings; coaching rules; notifications; daily summary; streaming JSON export; confirmation-gated local erase; and revisioned Wear Data Layer publishing |
| Wear app | Three-page touch-only horizontal pager, glucose/activity display, walk and stair actions, home countdown with completion haptic, explicit queued/rejected action results, durable snooze outbox, pending-session reconciliation, direct watch notifications, and complication providers; no bezel dependency |
| Watch face | Separate WFF v4 resource-only package with clock, glucose/trend/delta/age, steps/floors, battery, coach action, reduced ambient content, and selectable accent configuration |
| Coaching | Rapid-rise, post-meal, and inactivity rules with a walk fallback when stair reminders are disabled, stable recommendation IDs, explicit validity windows, minute-boundary reevaluation, quiet/working hours, cooldown, snooze, daily limit, and shared missing/future/stale/low/fast-fall safety policy |
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

## Project structure

| Module | Responsibility |
| --- | --- |
| `:core:model` | Platform-neutral glucose, activity, settings, coaching, session, and sync models |
| `:core:domain` | Repository contracts, coaching and shared exercise-safety rules, follow-up selection, settings validation, and personal observation analysis |
| `:core:data` | Room schema v7 with exported schemas 1–7, DataStore, asynchronous Nightscout client/parser/cache/retry implementation, immutable recommendation snapshots, retained future-provider boundaries, and repository implementations |
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
match, copies deliverables into `artifacts/`, and creates a versioned five-file installation ZIP.

The current app version packages as `artifacts/MetabolicCoach-v0.2.zip`, containing `phone.apk`,
`wear.apk`, `watchface.apk`, `CHANGELOG.md`, and an archive-specific `INSTALL.md`. The version is
derived from all three APK manifests and packaging fails if they disagree. Run
`./scripts/package-release.sh` to repackage already-verified artifacts without rebuilding. A
repeat debug build refreshes its same-version engineering ZIP only while that archive remains a
debug build. Debug APKs can never replace a same-version release archive. A different release
archive for an existing version is not overwritten unless `MC_PACKAGE_OVERWRITE=1` is set
intentionally.

The current debug artifacts are generated by the Nightscout milestone verification pipeline;
exact evidence and hashes are recorded in [Testing](docs/TESTING.md). They remain debug-signed
engineering artifacts, not production releases. A signed release requires environment-only
`MC_RELEASE_*` credentials; see
[Development](docs/DEVELOPMENT.md) and [Release and signing](docs/RELEASE.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Nightscout, Health Connect activity, and future provider integrations](docs/INTEGRATIONS.md)
- [Milestone development and cross-session handoff process](docs/MILESTONE_PROCESS.md)
- [v0.3 Galaxy Watch8 physical acceptance checklist](docs/V0.3_WEAR_ACCEPTANCE.md)
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
