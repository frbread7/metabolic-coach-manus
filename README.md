# Metabolic Coach

Metabolic Coach is a phone-and-watch wellness application focused on one question:

> What is the best action I can take right now to support my metabolic health?

The project contains an Android phone data hub, a touch-first Wear OS application, and a
resource-only Watch Face Format (WFF) watch face for Wear OS 6. The primary hardware target is the
Samsung Galaxy Watch8.

> **Development status:** locally verified engineering alpha. The architecture and core coaching
> flows are implemented, including review-driven safety, provenance, and exactly-once command
> reconciliation. The 2026-07-23 local pipeline passed 163 JUnit executions covering 144 distinct
> test cases, debug/release lint and assembly, APK checks, and WFF v4 schema/memory validation. The
> repository has not yet passed the
> authorized-provider, physical-device, instrumentation, production-signing, or store-policy gates
> required for daily use. It is a wellness tool, not a medical device, and must not replace the CGM
> vendor app, glucose alarms, professional advice, or a personal care plan.

## What is implemented

| Area | Current implementation |
| --- | --- |
| Phone hub | Health Connect foreground refresh and feature/permission-gated background reads, explicit per-package glucose-origin pinning, debug-only xDrip compatibility, Room schema v7 history, settings, coaching rules, notifications, daily summary with exercise-session count/duration, streaming JSON export, confirmation-gated local erase, and revisioned Wear Data Layer publishing |
| Wear app | Three-page touch-only horizontal pager, glucose/activity display, walk and stair actions, home countdown with completion haptic, explicit queued/rejected action results, durable snooze outbox, pending-session reconciliation, direct watch notifications, and complication providers; no bezel dependency |
| Watch face | Separate WFF v4 resource-only package with clock, glucose/trend/delta/age, steps/floors, battery, coach action, reduced ambient content, and selectable accent configuration |
| Coaching | Rapid-rise, post-meal, and inactivity rules with a walk fallback when stair reminders are disabled, stable recommendation IDs, explicit validity windows, minute-boundary reevaluation, quiet/working hours, cooldown, snooze, daily limit, and shared missing/future/stale/low/fast-fall safety policy |
| Intervention lifecycle | Idempotent start/complete commands, immutable phone-authored recommendation snapshots, deferred completion reconciliation, configurable start/snooze expiry, durable completion of an existing offline session, terminal expiry for orphan completions, and exact trigger, recommendation, activity-dose, baseline, source, and follow-up provenance |
| Personal observations | Cautious effect summaries plus prospective-only timing observations. Generic/post-meal bucket widths, per-bucket sample floor, comparable-bucket count, follow-up matching width, and baseline glucose band are configurable (conservative defaults: 5/15 minutes, 8 samples, 2 buckets, 15 minutes, and 20 mg/dL); a unique lowest observed median must also have strict quartile separation. Results never change coaching automatically and remain experimental |
| Synchronization | Versioned `DataMap` state with persistent phone instance/revision/reset metadata, stale pre-erase command rejection, terminal replay deduplication for every quick action, a bounded Wear outbox for non-session commands such as snooze, and a pending-mutation/tombstone session replica over Wear OS Data Layer (`play-services-wearable` 20.0.1) |
| Presentation | Dark, system, and high-contrast themes plus configurable font scale on phone and Wear; watch-face accent is configured through WFF, and motion is intentionally limited to useful pager/session transitions |
| Extensibility | Provider, repository, use-case, storage, and synchronization boundaries suitable for additional CGMs, Samsung Health Data SDK, cloud sync, or future recommendation engines |

## Important integration status

- **Health Connect is the only supported public pull integration in the current build.** It reads
  blood glucose, steps, floors, heart rate, daily exercise-session count/duration aggregates, and
  active calories when those records exist and the user grants access. Glucose is pinned to one
  Health Connect writer package: one discovered writer is saved automatically; multiple writers
  require an explicit Settings choice before glucose display or coaching resumes.
- **CareSens Air direct access is not implemented.** No authorized public CareSens Air SDK/API or
  documented inter-app contract is configured. The repository intentionally contains a
  partner-approval stub instead of scraping app data or connecting directly to the sensor.
- **CareSens Air → Samsung Health → Health Connect is a candidate route, not a verified guarantee.**
  Availability, record types, update latency, region, and device compatibility must be proven on
  the intended phone and Galaxy Watch8.
- **xDrip compatibility is debug-only.** The release manifest and release settings UI exclude it,
  and the release data layer replaces any persisted debug-only xDrip selection with Health Connect
  until an upstream sender contract is documented and verified end to end. Debug builds on Android
  14+ accept the compatibility broadcast only while that provider is selected and only when Android
  reports `com.eveningoutpost.dexdrip` as the sender package. This is package provenance, not
  certificate pinning; see [Integrations](docs/INTEGRATIONS.md).
- **Release provider choices are Health Connect and the CareSens partner placeholder.** Health
  Connect is the only implemented release data route; the placeholder reports that partner
  approval is required and does not return readings.
- **Direct Samsung Health Data SDK access is inactive.** An explicit provider boundary is present,
  but the partner SDK is not bundled. Public distribution requires Samsung approval plus
  registration of the package and release signing certificate.

## Project structure

| Module | Responsibility |
| --- | --- |
| `:core:model` | Platform-neutral glucose, activity, settings, coaching, session, and sync models |
| `:core:domain` | Repository contracts, coaching and shared exercise-safety rules, follow-up selection, settings validation, and personal observation analysis |
| `:core:data` | Room schema v7 with exported schemas 1–7, DataStore, Health Connect origin selection, immutable recommendation snapshots, CareSens/xDrip/Samsung provider boundaries, and repository implementations |
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
match, and copies deliverables into `artifacts/`.

The current debug artifacts were regenerated by the 2026-07-23 successful pipeline. Their hashes
are recorded in [Testing](docs/TESTING.md#latest-local-verification). A signed release requires
environment-only `MC_RELEASE_*` credentials; see
[Development](docs/DEVELOPMENT.md) and [Release and signing](docs/RELEASE.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [CareSens, Health Connect, Samsung Health, and xDrip integrations](docs/INTEGRATIONS.md)
- [Development and build guide](docs/DEVELOPMENT.md)
- [Testing strategy and release gates](docs/TESTING.md)
- [User guide](docs/USER_GUIDE.md)
- [Privacy and safety](docs/PRIVACY_AND_SAFETY.md)
- [Release, signing, and distribution](docs/RELEASE.md)

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
