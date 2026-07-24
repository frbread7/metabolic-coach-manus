# Data integrations

Evidence in this document was reviewed on 2026-07-23. Vendor and platform behavior can change; recheck
the linked primary sources before a release.

## Provider status

| Route | Code status | Support status | Release conclusion |
| --- | --- | --- | --- |
| CareSens Air official SDK/API | Capability stub only | No authorized public contract is configured | Blocked pending an i-SENS-supported interface and authorization |
| Health Connect blood glucose | Implemented | Public Android API | Preferred current route when the CGM ecosystem writes timely records |
| CareSens companion-app IPC | Not implemented | No documented contract identified | Do not reverse engineer or scrape |
| CareSens Broadcast Intent | Not implemented | No documented CareSens contract identified | Do not invent an action/extras schema |
| xDrip local broadcast | Implemented in debug builds only | Unofficial compatibility path; sender contract not verified end to end | Excluded from release manifest and release provider UI |
| Samsung Health Data SDK | Inactive provider boundary; SDK not bundled | Official partner SDK | Blocked on approval, package/certificate registration, and hardware testing |
| Direct CGM Bluetooth | Intentionally absent | Would require vendor authorization and safety validation | Out of scope |

## CareSens Air investigation

The project follows the requested investigation order rather than hard-coding a speculative
integration:

1. **Official SDK/API:** no authorized public CareSens Air read API is configured. The
   `CareSensAirProvider` therefore reports `PARTNER_APPROVAL_REQUIRED` and returns no readings.
2. **Health Connect:** implemented as the public interoperability route. It can read glucose records
   that another authorized application has written.
3. **Companion app interface:** no documented CareSens app content provider, bound service, or other
   stable contract is used.
4. **Broadcast intent:** no CareSens-defined broadcast is used.
5. **xDrip compatibility:** implemented only as an explicitly unofficial, debug-build route while
   the sender contract remains unverified.
6. **Official alternative:** an inactive `SamsungHealthPartnerDataProvider` keeps approved Samsung
   activity access behind the provider interface. It reports `PARTNER_APPROVAL_REQUIRED` until an
   authorized SDK integration, package, and certificate are supplied.

The official [CareSens Air compatibility page](https://caresensair.com/en_US/content/compatibility/)
states general Wear OS requirements and warns that untested models may not work correctly. The
compatible-model list reviewed for this project did not explicitly establish Galaxy Watch8
compatibility. That absence is not proof of incompatibility; it makes physical-device and regional
validation mandatory.

An official CareSens Wear app, where available, is separate from this project. Its ability to show a
reading on a watch does not imply that it exposes the reading to third-party apps.

## Health Connect

### Implemented records

The phone requests read access to:

| Health Connect record | Use |
| --- | --- |
| `BloodGlucoseRecord` | Current/history glucose; local trend and delta calculation |
| `StepsRecord` | Daily steps and last-movement estimate |
| `FloorsClimbedRecord` | Daily floors |
| `HeartRateRecord` | Latest heart-rate sample |
| `ExerciseSessionRecord` | Daily session count and total-duration aggregates; latest end time for last movement |
| `ActiveCaloriesBurnedRecord` | Daily active calories |

The official [Health Connect data type reference](https://developer.android.com/health-and-fitness/health-connect/data-types)
defines these records and permissions.

### Data quality contract

- `measuredAtEpochMillis` is the source record time.
- `receivedAtEpochMillis` is when Metabolic Coach imports the record.
- Health Connect's `DataOrigin.packageName` is retained in `sourceId`.
- Glucose is normalized to integer mg/dL.
- A rate/trend is calculated only between increasing timestamps from the same writer package.
- Duplicate IDs are ignored by Room.
- The coach stops exercise actions when the latest sample exceeds the configurable stale limit.
- Intervention sessions retain exact baseline and follow-up reading IDs, values, measurement
  timestamps, and source IDs. Coached sessions also retain trigger, recommendation/version,
  activity-dose, baseline-rate/threshold, due-time, and finalization provenance; prospective timing
  observations require complete provenance and matching exact sources.

### Glucose writer selection

A Health Connect permission grants access to a record type, not necessarily to one CGM writer.
Metabolic Coach therefore discovers writer packages from blood-glucose record metadata over the
24-hour import window and applies this contract:

- exactly one discovered package is selected and persisted automatically;
- two or more discovered packages with no saved selection produce
  `CONFIGURATION_REQUIRED`; glucose display and coaching remain paused until the user saves one
  package in Settings;
- only records from the selected exact package are imported and queried for coaching;
- a saved package remains pinned if it temporarily disappears, so a newer record from another
  writer can never silently take over;
- the unavailable saved package remains visible in Settings as **no recent records**.

Changing the selection is an explicit user action. Existing history remains attributed to its
original package; daily summaries, baselines, follow-ups, and observations use the currently
selected exact source and never combine glucose series from different writers.

Health Connect does not guarantee that CareSens or Samsung Health publishes every desired record or
publishes it with CGM-like latency. Validate the actual source package, timezone, update interval,
historical gaps, duplicates, and phone restart behavior.

Exercise aggregation pages through all records in the local-day window, ignores reversed intervals,
and stores only the valid session count, summed whole-minute duration, and latest end time. It does
not retain per-session exercise type, route, or detailed workout history.

### Foreground and background behavior

Manual Refresh performs a foreground Health Connect read directly. The permission request checks
`FEATURE_READ_HEALTH_DATA_IN_BACKGROUND` and includes
`READ_HEALTH_DATA_IN_BACKGROUND` only when the installed provider advertises it. Periodic
WorkManager refresh is scheduled only when that feature is available and the background permission
is granted; otherwise periodic work is cancelled and foreground/manual reads remain available.

The implementation still requires target-device validation of feature reporting, grant/denial,
revocation, process death, reboot, battery restrictions, and actual WorkManager execution. Even
when granted, Android scheduling is inexact and the source application may publish late.

### Candidate CareSens path

The provisional public path is:

```text
CareSens Air app or approved service
        -> Samsung Health and/or Health Connect
        -> HealthConnectGlucoseProvider
        -> Room -> coach -> watch
```

This path is usable only if a test reading appears in Health Connect with acceptable freshness.
Do not assume that enabling Samsung Health automatically makes CareSens blood glucose available to
Health Connect.

## Samsung Health

Current code does not call Samsung APIs. It reads records from Health Connect when Samsung Health
or another source has made them available. The repository includes an inactive
`ActivityDataProvider` implementation so the partner SDK can later be added without leaking its
contract into repository or UI code.

Direct Samsung integration should use the current **Samsung Health Data SDK**, not the deprecated
Samsung Health SDK for Android. Samsung's primary documentation states:

- the SDK runs on Android 10+ and requires Samsung Health 6.30.2+ and Java 17;
- the SDK does not support emulators;
- developer mode is for development/testing;
- public distribution requires registration of the app package and signing certificate SHA-256;
- unregistered public builds can be rejected by the SDK.

Sources:

- [Samsung Health Data SDK introduction](https://developer.samsung.com/health/data/guide/introduction.html)
- [App creation and partnership process](https://developer.samsung.com/health/data/process.html)
- [App verification](https://developer.samsung.com/health/data/guide/app-verification.html)

Before adding the SDK:

1. confirm the precise data types needed beyond Health Connect;
2. obtain the SDK through Samsung's official distribution;
3. isolate the AAR behind an adapter in `:core:data`;
4. avoid committing the AAR, access code, keys, or partner correspondence unless licensing permits;
5. test on a physical Samsung phone and Galaxy Watch8;
6. register the final package name and release certificate;
7. retain explicit user consent and provider status.

Floors, per-session exercise details beyond the current daily aggregates, activity classification,
and source-attribution behavior are priority items for this validation.

## xDrip compatibility

xDrip compatibility is a development aid, not a release integration. Only the debug manifest
declares the exported receiver and xDrip receive permission, and only debug settings expose
`XDRIP_BROADCAST`. The release data layer also converts any xDrip mode retained from a debug install
to Health Connect before provider selection. Release builds expose Health Connect and the CareSens
partner placeholder; the placeholder is nonfunctional until partner approval and an authorized
contract are supplied.

In a debug build, xDrip ingestion is disabled unless the user selects `XDRIP_BROADCAST`. The
receiver expects:

```text
action: com.eveningoutpost.dexdrip.BgEstimate
extras:
  com.eveningoutpost.dexdrip.Extras.BgEstimate   numeric mg/dL
  com.eveningoutpost.dexdrip.Extras.BgSlope      optional numeric mg/dL/millisecond
  com.eveningoutpost.dexdrip.Extras.Time         epoch milliseconds
  com.eveningoutpost.dexdrip.Extras.SourceInfo   optional string
```

The slope is converted to mg/dL/minute before trend classification and storage.

Samples are rejected when:

- Android is older than 14/API 34;
- `BroadcastReceiver.getSentFromPackage()` does not report
  `com.eveningoutpost.dexdrip`;
- the action is wrong;
- the selected provider is not xDrip;
- value/time is missing or nonnumeric;
- glucose is outside 20–600 mg/dL;
- the timestamp is older than 24 hours or more than 5 minutes in the future;
- converted slope is non-finite or outside ±20 mg/dL/minute.

### Security limitation

The Android receiver must be exported to accept a cross-application broadcast. On Android 14+ the
receiver verifies the sender package reported by the platform; older Android versions are rejected
because they cannot provide that provenance. Numeric/time/rate validation further reduces malformed
input.

The route does not pin or verify the xDrip signing certificate, and the expected action/extras
contract has not been verified end to end against a supported upstream sender. A malicious or
repackaged application able to occupy the expected package identity remains outside the trust
model. Use only trusted software in debug testing. A future production proposal must first verify
the upstream contract and investigate a signature-protected or explicitly authenticated
alternative.

xDrip is not an i-SENS API, is not endorsed here by i-SENS, and may change independently. Preserve
the official CGM app as the safety source. Do not describe xDrip as a supported release route while
the receiver and selector remain intentionally debug-only.

## Adding another provider

Every provider must implement:

```kotlin
interface GlucoseProvider {
    val id: String
    suspend fun status(): ProviderStatus
    suspend fun readSince(startEpochMillis: Long): List<GlucoseReading>
}
```

A provider implementation must document:

- authorization and licensing;
- supported countries, devices, and versions;
- measured vs received timestamps and timezone handling;
- units and conversion;
- adjusted/raw values if relevant;
- update latency and outage behavior;
- duplicate/order semantics;
- revocation and data deletion;
- test fixtures that contain no real health data.

Provider selection is explicit. There is no silent fallback from an unavailable authorized provider
to an unofficial one.

## Required end-to-end validation

For each supported route, capture:

- source app and version;
- phone model, Android version, region, timezone, and locale;
- Galaxy Watch8 size/model and Wear OS build;
- consent screenshots and granted permissions;
- at least 24 hours of source/import timestamps;
- reconnect, reboot, force-stop, Bluetooth-off, and battery-saver behavior;
- low, high, rising, falling, duplicate, stale, and missing samples;
- comparison against the vendor app without copying identifiable health data into bug reports.

Pass criteria must be defined before calling any route reliable. A successful compile or one
displayed reading is not sufficient.
