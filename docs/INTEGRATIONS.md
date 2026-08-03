# Data integrations

Evidence in this document was reviewed on 2026-08-03. Vendor and platform behavior can change;
recheck the linked primary sources before a release.

## Provider status

| Route | Version 1 status | Release conclusion |
| --- | --- | --- |
| Nightscout API v1 | Implemented as the active glucose provider | Primary Version 1 route; requires an explicitly configured active server |
| Additional Nightscout servers | Implemented with separate source IDs and explicit selection | No automatic failover or history/cache mixing |
| Nightscout authentication | Request-authenticator boundary only | Public endpoints only in Version 1; secure credential UI/storage is future work |
| Health Connect activity | Implemented | Activity source for steps, floors, heart rate, exercise aggregates, and active calories |
| Health Connect blood glucose | Retained inactive adapter | Future provider; not selectable in Version 1 |
| CareSens Air official SDK/API | Capability stub only | No direct communication in Version 1 |
| CareSens companion-app IPC or broadcast | Not implemented | Do not reverse engineer, scrape, or invent a contract |
| xDrip local broadcast | Retained inactive adapter code; receiver unregistered | Not a Version 1 source |
| Samsung Health Data SDK | Inactive provider boundary; SDK not bundled | Blocked on approval, package/certificate registration, and hardware testing |
| Direct CGM Bluetooth | Intentionally absent | Out of scope |

## Version 1 source decision

The existing daily pipeline is the source of truth:

```text
CareSens Air sensor
    -> CareSens Air app
    -> xDrip+
    -> Nightscout
    -> Metabolic Coach phone
    -> Room -> coaching engine -> Wear Data Layer -> watch
```

Metabolic Coach joins this pipeline at Nightscout. It does not communicate with the sensor, inspect
CareSens private storage, call undocumented CareSens IPC, receive an xDrip broadcast, or reverse
engineer Bluetooth. The watch remains provider-agnostic and never calls Nightscout.

This decision reuses a stable server that can already support several independent clients, avoids a
new sensor-side integration, and keeps provider-specific failure and authentication behavior on the
phone. It does not make Nightscout or the upstream apps a medical safety system. The CareSens app
and its alarms remain authoritative for CGM safety.

The official [CareSens Air compatibility page](https://caresensair.com/en_US/content/compatibility/)
states general Wear OS requirements and warns that untested models may not work correctly. Its
ability to display a reading in an official app does not imply a supported API for this project.

## Nightscout

### API contract

Version 1 requests:

```http
GET {baseUrl}/api/v1/entries/sgv.json?count=300
Accept: application/json
```

For a cold-cache historical request the client uses bounded seven-day requests, for example:

```http
GET {baseUrl}/api/v1/entries/sgv.json?find[dateString][$gte]=<start-iso>&find[dateString][$lte]=<end-iso>&count=2500
Accept: application/json
```

The range request is used only for the phone's recent (at most 90-day) history backfill. Each
response is capped, retried under the configured policy, normalized, and merged by stable reading
ID. A range failure does not mix another server into the active source and does not fabricate
missing readings. Nightscout deployments that expose only a different historical query contract
must be verified with a synthetic or staging server before daily use.

The parser accepts Nightscout `sgv`, `date` or ISO `dateString`, `_id`, and `direction`, ignores
unknown fields, orders entries by measurement time, deduplicates stable records, and normalizes
values to `GlucoseReading` in mg/dL. Delta and rate are calculated locally between valid ordered
readings; provider-specific JSON never enters the coaching or synchronization layers.

Each normalized record contains:

- glucose in mg/dL;
- mapped trend direction;
- delta and rate when adjacent readings are valid;
- measurement and import timestamps;
- a source ID derived from the configured server ID and normalized URL.

The source ID prevents histories, intervention baselines/follow-ups, observations, and caches from
mixing across servers. Changing a server URL creates a different source identity even when the
display name and slot ID are unchanged.

### Glycemic Goal Planner data contract

The planner consumes only normalized `GlucoseReading` records from the selected Nightscout source.
It displays rolling 30/60/90-day mean glucose, GMI, time-in-range, time-below-range,
very-low exposure, coverage, largest gap, and sample count. Means and exposure are weighted by
elapsed covered time; gaps longer than the configured interpolation bound reduce coverage. A source
change inside a requested window is a discontinuity, never a concatenation of server histories.
The 14-day window is used as the recent safety baseline for goal scenarios.
The UI calls the result **CGM-derived GMI**, not real-time or laboratory HbA1c. The estimate follows
the published GMI relationship described by Bergenstal et al. (see the
[GMI publication](https://pubmed.ncbi.nlm.nih.gov/30224348/)); laboratory A1C remains a separate
clinical test (see [NIDDK's A1C overview](https://www.niddk.nih.gov/health-information/diagnostic-tests/a1c-test)).
Goal scenarios are mathematical displays only and are suppressed when recent low-glucose exposure
or the required data coverage makes a lower target unsafe to present.

### Saved planning milestones (`v0.4.2`)

The milestone repository stores phone-only planning intentions above this provider-independent
contract. A milestone contains a canonical target GMI, provenance, original 30/60/90-day horizon,
fixed target date, lifecycle state, and calculation-contract version. It never stores a second
target mean and never enters the provider, coaching, notification, or Wear payload paths.

The selected milestone is a single presentation pointer; any number of active or archived rows may
exist. Future rows can be edited, while due/past target/date/horizon fields are frozen. Before the
fixed date, the domain calculation uses the actual remaining days and the observed complementary
history. At or after the date, it evaluates the fixed 90-day window ending at that date. Source
discontinuity, insufficient coverage, and low-glucose exposure remain explicit non-success states.

The legacy singleton planner target is migrated once to a stable milestone ID. Room owns milestone
definitions; Preferences DataStore owns the selected ID and migration notice. A future provider
implementation only needs to emit normalized `GlucoseReading` values; it must not know about
milestone storage or UI state.

Primary references:

- [Nightscout API overview](https://github.com/nightscout/cgm-remote-monitor/blob/7e0e77f88fc113a76fe363504125f5b36b8a3fe3/README.md#L210-L229)
- [Nightscout API v1 schema](https://github.com/nightscout/cgm-remote-monitor/blob/7e0e77f88fc113a76fe363504125f5b36b8a3fe3/lib/server/swagger.yaml#L655-L710)
- [Nightscout conditional entry responses](https://github.com/nightscout/cgm-remote-monitor/blob/7e0e77f88fc113a76fe363504125f5b36b8a3fe3/lib/api/entries/index.js#L132-L182)

### Server settings and selection

The phone Settings screen stores:

- up to eight named server URLs;
- one explicit active server;
- polling interval from 15 to 1,440 minutes;
- connection timeout from 2 to 60 seconds;
- retry interval from 5 to 60 seconds;
- zero to three retry attempts;
- whether HTTPS is required.

Two empty server slots are present by default and additional slots can be added. A blank slot is not
queried. Selecting another server is an explicit action. The application does not probe alternatives
or silently fail over when the active server is unavailable. That policy prevents a family member,
test server, or stale replica from unexpectedly becoming the glucose authority.

URLs are normalized before storage. User information, query strings, and fragments are rejected.
When **Require HTTPS** is enabled, only HTTPS is accepted. When it is disabled, HTTP is also accepted
for an explicitly configured local or test server. Cleartext traffic can expose glucose to the
network; internet-hosted and daily-use servers should always use HTTPS.
Redirects are not followed. Every request stays on the validated scheme, host, port, and base
path, so a redirect cannot move glucose traffic or future authentication headers to another
origin.

Nightscout settings stay on the phone. The Wear state contains only normalized glucose and general
coaching settings, never a Nightscout URL, active-server ID, timeout/retry policy, or credential.

### Connectivity, retry, and cache behavior

The OkHttp call is asynchronous and coroutine-cancellable; UI code observes flows and is never
blocked by a network request. Responses are capped at 1 MiB. Current-entry refreshes intentionally
do not send a conditional validator, so an intermediary cannot turn a stale cache into a successful
current refresh. Historical range requests may return an empty/304 result, but only the matching
source cache is retained and the newest valid timestamp remains authoritative. Each server's
process-memory cache is bounded to the 90-day planner lookback plus a one-day interpolation cushion.

Transport errors, timeouts, HTTP 408, HTTP 429, and HTTP 5xx can be retried with exponential delay
from the configured base interval, capped at 60 seconds per delay and bounded by the configured
maximum attempts. Invalid configuration or JSON and HTTP 4xx responses such as 401/403 are not
retried. When all attempts fail, the provider publishes degraded/error state and retains its
per-server memory cache plus previously persisted Room readings. Cached age remains visible so
normal stale-reading safety policy can suppress exercise coaching. No synthetic reading or silent
server switch occurs.

WorkManager uses a connected-network constraint when Nightscout is configured. Android periodic
work has a 15-minute minimum and is inexact, so the polling interval is a request, not a freshness
guarantee. Manual foreground refresh remains available.

### Authentication boundary

Version 1 supports public Nightscout endpoints. A no-op
`NightscoutRequestAuthenticator` is injected separately from the API client so a future adapter can
add Nightscout access tokens without changing repositories, coaching, or Wear synchronization.

Do not place a token, `API_SECRET`, username, or password in a Nightscout URL. Future authentication
must use a phone-only secure credential store, redact secrets from logs/errors/exports, scope each
credential to one server, and avoid Wear Data Layer entirely. Review current
[Nightscout API security guidance](https://github.com/nightscout/cgm-remote-monitor/wiki/API-v1-Security)
before implementing it.

## Health Connect activity

The phone requests read access to:

| Health Connect record | Use |
| --- | --- |
| `StepsRecord` | Daily steps and last-movement estimate |
| `FloorsClimbedRecord` | Daily floors |
| `HeartRateRecord` | Latest heart-rate sample |
| `ExerciseSessionRecord` | Daily session count and total-duration aggregates; latest end time for last movement |
| `ActiveCaloriesBurnedRecord` | Daily active calories |

The official [Health Connect data type reference](https://developer.android.com/health-and-fitness/health-connect/data-types)
defines these records and permissions. Exercise aggregation pages through the local-day window,
ignores reversed intervals, and stores only the valid session count, summed whole-minute duration,
and latest end time; it does not retain route, exercise type, or detailed per-session history.

Manual Refresh performs a foreground activity read. The permission request includes
`READ_HEALTH_DATA_IN_BACKGROUND` only when the installed provider advertises
`FEATURE_READ_HEALTH_DATA_IN_BACKGROUND`. Missing or denied background activity access does not
cancel Nightscout periodic work. The implementation still requires target-device validation of
feature reporting, grant/denial, revocation, process death, reboot, battery restrictions, and
actual WorkManager execution.

The Health Connect glucose adapter and exact-writer selection logic remain isolated behind the
provider abstraction for future evaluation. Version 1 policy always selects Nightscout and does not
request Health Connect blood-glucose permission.

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

## Future xDrip provider

xDrip participates upstream of Nightscout in the user's existing pipeline, but Metabolic Coach does
not receive xDrip broadcasts in Version 1. Neither debug nor release manifests register the
receiver, Settings does not expose `XDRIP_BROADCAST`, and persisted legacy provider modes migrate to
Nightscout.

The repository retains isolated xDrip adapter and validation code as a future offline-provider
starting point. Retention is not support or enablement. Before any future xDrip provider is
activated:

- verify the upstream action/extras contract against a supported xDrip build;
- define source identity, duplicate/order, unit, timestamp, and outage semantics;
- verify the sender beyond a caller-controlled package name, preferably with a
  signature-protected or explicitly authenticated contract;
- keep selection explicit and never auto-fallback from Nightscout;
- add target-version manifest, sender-provenance, malformed-input, and end-to-end tests;
- preserve the official CGM app as the safety source.

An exported cross-application receiver expands the attack surface. The previously explored
package-provenance check is not certificate pinning and does not by itself meet the Version 1 trust
boundary. xDrip is not an i-SENS API and is not endorsed here by i-SENS.

## Adding another provider

Every provider must implement:

```kotlin
interface GlucoseProvider {
    val id: String
    fun observeState(): Flow<GlucoseProviderState>
    suspend fun status(): ProviderStatus
    suspend fun readSince(startEpochMillis: Long): List<GlucoseReading>
    suspend fun readSinceExactSource(
        sourceId: String,
        startEpochMillis: Long,
    ): List<GlucoseReading>
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
- explicit selection and whether failover is prohibited or supported;
- cache partitioning and authentication-secret boundaries;
- test fixtures that contain no real health data.

Provider selection is explicit. There is no silent fallback from an unavailable authorized provider
to an unofficial one.

## Required end-to-end validation

For each supported route, capture:

- Nightscout version, deployment platform, base URL host without credentials, and whether HTTPS is
  enforced;
- upstream source-app versions without copying private health data;
- phone model, Android version, region, timezone, and locale;
- Galaxy Watch8 size/model and Wear OS build;
- consent screenshots and granted permissions;
- at least 24 hours of sensor, Nightscout, phone-import, and watch-display timestamps;
- manual active-server switching with distinct synthetic fixtures and proof that no history or
  cache crosses sources;
- timeout, DNS failure, TLS failure, HTTP 304/401/403/408/429/5xx, malformed JSON, empty response,
  bounded retry, cancellation, and recovery behavior;
- reconnect, reboot, force-stop, Bluetooth-off, and battery-saver behavior;
- low, high, rising, falling, duplicate, stale, and missing samples;
- comparison against the vendor app without copying identifiable health data into bug reports.

Pass criteria must be defined before calling any route reliable. A successful compile or one
displayed reading is not sufficient.
