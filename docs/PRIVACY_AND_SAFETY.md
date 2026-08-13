# Privacy and safety

## Product classification

Metabolic Coach is designed as a fitness/wellness aid. It does not:

- diagnose or treat a condition;
- replace the CGM vendor application or its alarms;
- recommend medication or insulin;
- detect emergencies;
- guarantee glucose accuracy or freshness;
- prove that an intervention caused a glucose change.

Before public distribution, product wording, Play health-app declarations, regional obligations,
and the final privacy policy require qualified legal/regulatory review.

## Data handled

| Category | Examples | Source | Local destination |
| --- | --- | --- | --- |
| Glucose | Value, trend/rate, delta, sample/import time, exact server-derived source ID | Active Nightscout server | Phone Room; latest normalized state on watch |
| Activity | Steps, floors, heart rate, active calories, last movement, daily exercise-session count/duration | Health Connect | Phone Room; latest state on watch |
| Behavior | Meal markers, walk/stair sessions, completion, immutable phone-authored recommendation snapshots, and exact trigger/recommendation/activity-dose/baseline/source/follow-up provenance | User actions, coach actions, settings-at-start, and selected glucose source | Phone Room; active/pending session replica and generic command outbox on watch |
| Nightscout configuration | Server names/base URLs, explicit active server, HTTPS policy, polling/timeout/retry values | User | Phone DataStore only; never synchronized to watch |
| Preferences | Thresholds, time windows, units, reminders, theme/font, phone sync instance/revision/reset token, and bounded terminal-command history | User and application-generated metadata | Phone DataStore; only provider-agnostic display/coaching state is synchronized |
| Device | Phone/watch battery percentage | Android system | Latest synchronized/display state |

The app requests read-only Health Connect activity-record permissions. It does not request
Health Connect blood-glucose permission for Version 1 and does not write back to Health Connect.

## Data movement

```text
CareSens app -> xDrip+ -> Nightscout server
                            -> phone NightscoutProvider -> local Room/DataStore
                                                        -> Wear Data Layer -> watch local cache
```

The app makes outbound network requests to the user-configured Nightscout server. There is no
project-owned cloud account, analytics SDK, advertising SDK, or custom Metabolic Coach backend in
the current source. That does not mean all data stays on a direct Bluetooth link: Nightscout is
already a remote service, and the official Wear OS
[Data Layer overview](https://developer.android.com/training/wearables/data/overview) says traffic
can route through Google infrastructure when Bluetooth is unavailable and is end-to-end encrypted.

Nightscout and its hosting provider, Health Connect, Google Play services, Samsung Health,
CareSens, and xDrip each have their own privacy behavior and policies. Metabolic Coach documentation
cannot replace those disclosures.

Android backup is disabled in the phone and Wear manifests. The app provides a user-initiated local
JSON export and confirmation-gated local erase. It does not provide cloud backup or account
recovery.

## Permission principles

- Ask only for the record types the feature uses.
- Explain purpose before requesting permission.
- Continue without crashing when access is denied or revoked.
- Never infer permission from a manifest declaration.
- Treat background health access as a separate explicit permission/feature gate.
- Require an explicit active Nightscout server; never probe or silently fail over to another
  configured server.
- Keep every server's history, conditional-request metadata, cache, and future credential isolated.
- Default to HTTPS. Allow HTTP only after an explicit warning-bearing opt-out for local/test use.

The current alpha asks for background-read access only when Health Connect reports that feature.
That permission affects activity reads. A configured Nightscout server can still schedule
connected-network periodic work when Health Connect background activity access is unavailable.
The permission explanation and real-device grant, denial, revocation, reboot, and scheduling
behavior still need validation before background activity data can be considered reliable.

## Safety behavior in code

The rule engine:

- receives only normalized `GlucoseReading` values for the exact Nightscout server selected on the
  phone and has no Nightscout-specific dependency;
- displays information instead of an exercise action when glucose is missing, future-dated, or
  stale;
- pauses exercise coaching below the user-configured low threshold;
- pauses exercise coaching when numeric rate or trend fallback falls at or beyond the configurable
  exercise-pause rate;
- honors snooze, quiet hours, cooldown, and a daily notification maximum;
- uses cautious “personal observation” wording;
- keeps personal observations display-only rather than feeding them into thresholds, rules,
  reminders, or recommendation timing.

Phone and Wear use the same exercise-safety policy. Cached actions also have explicit validity
windows and are suppressed when expired, during quiet hours, while notifications are disabled, or
while a session is active. Minute-boundary reevaluation removes an action without waiting for a new
provider record. Before a coached prompt is published, the phone stores an immutable authoritative
snapshot. Delayed start commands require that snapshot, must fall inside its validity window, and
cannot replace its intervention dose or provenance with watch-supplied fields.

These are risk-reduction controls, not medical safeguards. They rely on source data, timestamps,
settings, and application execution all being correct.

## Known safety limitations

- A 15-minute WorkManager interval is inexact and may be delayed.
- CareSens app → xDrip+ → Nightscout → phone → watch publication latency is unverified.
- A public Nightscout endpoint exposes glucose to anyone who can access its URL unless the server
  is protected by its own access policy. Version 1 has no credential UI or secure credential store.
- The app permits cleartext traffic so an explicit HTTP local/test configuration can function.
  HTTP exposes glucose and should not be used for an internet-hosted or daily-use server.
- Multiple configured servers do not provide automatic failover. An outage retains cached values,
  whose age must be checked, rather than silently switching authorities.
- Nightscout trend depends on upstream `direction`; delta/rate are inferred locally from consecutive
  valid values and may be unavailable when samples are sparse.
- Direct CareSens and xDrip broadcast providers are inactive. No Version 1 manifest registers an
  xDrip receiver.
- Prospective timing observations require complete trigger/recommendation/activity-dose/baseline/
  source/follow-up provenance, exact-source continuity, the configured sample and comparable-bucket
  gates, and a unique lowest observed median with strict quartile separation. Timing, follow-up,
  and baseline matching bands are configurable. Recorded intervening meals, overlapping sessions,
  unsafe below-threshold follow-ups, and other invalid samples are excluded. Medication, unrecorded
  meals/activity, adherence, and selection bias remain uncontrolled.
- Timing output is an observation only; it must not claim causation, medical benefit, or a best or
  ideal action window.
- User-configured thresholds can be clinically inappropriate.
- Wear shows a responsive local session before phone-authoritative idempotent reconciliation;
  disconnect/reconnect and reboot behavior has not been proven on physical devices.
- Terminal quick actions are deduplicated before mutation and retained for the maximum supported
  offline-command lifetime. Wear session mutations use the pending/tombstone replica, while generic
  commands such as snooze use a bounded durable outbox; physical process-kill/replay fault injection
  is still required.
- There is no emergency escalation or source-app deep link.
- Notification delivery can be denied, delayed, muted, or suppressed by the OS.

Until these limitations are resolved or explicitly accepted, do not position the app as stable for
daily safety-related use.

## Nightscout network and authentication boundary

Version 1 accepts public Nightscout base URLs and uses a no-op request authenticator. URLs may not
contain user information, query parameters, fragments, or credentials. Nightscout URLs and
connectivity settings stay in phone DataStore and are not sent to the watch, but the URL itself can
still reveal a host/account identity and must be treated as sensitive configuration.

HTTPS protects the request in transit subject to normal Android trust-store behavior. Disabling
**Require HTTPS** allows cleartext HTTP and exposes glucose and response metadata to the local
network path. The Nightscout client does not follow redirects, preventing a validated request from
moving to another origin or from HTTPS to cleartext. The application does not implement
certificate pinning.

Future authenticated support must use the separate request-authenticator boundary with a
phone-only secure credential store, per-server credential scoping, redacted diagnostics, and
explicit lifecycle/deletion behavior. Credentials must never enter a URL, ordinary DataStore,
personal-data export, Wear Data Layer, logs, or crash reports.

The retained inactive xDrip adapter is not a Version 1 trust path. Any proposal to register an
exported broadcast receiver requires a documented sender contract, authenticated or
signature-protected trust decision, malformed-input validation, and explicit security review.

## Logging and diagnostics

Production logging must not include:

- glucose values, trends, or timestamps;
- Nightscout response bodies, request/response headers, server URLs, tokens, or secrets;
- raw Health Connect records;
- broadcast extras;
- meal or intervention history;
- provider source metadata;
- device/account identifiers;
- permission screen captures.

Use synthetic fixtures for tests. Redact bug reports and screenshots. If crash/analytics tooling is
added later, perform a separate data-flow review and obtain appropriate consent before enabling
health-related breadcrumbs or user identifiers.

## Retention and deletion

Glucose readings are stored locally under an explicit phone-side retention choice: 90 days (the
default), 1 year, or Keep all downloaded. Changing the choice stages it without deleting data; no
retention pruning occurs until the user confirms the selected policy. A confirmed bounded policy
prunes by measurement time and source while preserving each source's newest reading. Keep all
downloaded disables retention pruning, but it neither downloads older data automatically nor
guarantees that the upstream Nightscout server retains unlimited history.

Activity snapshots, meal markers, and intervention sessions do not currently have separate
configurable retention periods. They remain local until confirmed in-app erase, Android clears app
data, or the app is uninstalled. Expired recommendation snapshots are opportunistically pruned when
a later recommendation is authored once their validity ended more than seven days earlier.

The phone Settings screen can export coaching settings and every application Room row through the
Android document picker. Nightscout server configuration and future credentials are excluded. The
schema-versioned JSON is written directly to the selected destination, is not encrypted by
Metabolic Coach, and may contain sensitive health/source metadata. The user must choose and protect
an appropriate destination. Technical phone command-history/reset metadata is not part of the user
data document.

Confirmed in-app erase:

- serializes with export and all known phone-owned local data writers so no operation crosses the
  deletion boundary;
- preempts an in-flight coroutine-cancellable provider request before entering that boundary;
- cancels known Metabolic Coach refresh and intervention follow-up work on a best-effort basis;
- deletes every application Room table and clears the complete coaching-settings DataStore;
- clears provider process-memory caches and conditional-request metadata;
- clears the phone coaching notification;
- rotates the synchronization instance and durable reset token;
- publishes an empty state so Wear clears its state/session and queued-command stores, including
  after an offline reconnection;
- rejects delayed watch commands from the pre-erase data epoch.

Erase removes saved Nightscout configuration and does not revoke Health Connect/CGM permissions or
delete records in Nightscout or source applications. After the user reconfigures a server, opening
or refreshing the app or a scheduled run can collect new records again. A user who wants
collection to stop must revoke Health Connect access and change Nightscout access separately.

Uninstalling:

- the phone app removes its local database/settings;
- the Wear app removes its local cache/session;
- the watch-face package removes face resources;
- source data in Nightscout, Health Connect, Samsung Health, CareSens, or xDrip is not deleted.

A production release still needs target-device lifecycle testing of retention/backfill,
export/erase, and legal review of the retention/export/deletion disclosures.

## Minimum public privacy policy content

The published policy must accurately describe:

- legal entity/contact and policy date;
- health/activity/device data types;
- purposes for collection and processing;
- Nightscout network access, Health Connect activity, and Data Layer use;
- whether Google infrastructure can relay watch synchronization;
- local retention, deletion, and any backup behavior;
- third-party providers and links to their policies;
- security measures and limitations;
- country/age restrictions;
- user rights and support process;
- any later cloud, telemetry, crash, or AI processing.

Do not claim “data never leaves the device” while using Wear Data Layer. Do not claim HIPAA,
medical-device, clinical, or regulatory compliance without a formal assessment.

## Safety review gate

Before daily-use recommendation:

1. prove the intended Nightscout deployment's freshness, TLS/access policy, outage behavior,
   bounded retry, cached/stale behavior, and explicit multi-server switching;
2. validate Nightscout WorkManager and Health Connect activity behavior through denial, network
   loss, reboot, battery restrictions, and process death on target phones;
3. validate intervention/follow-up recovery and observation wording with controlled synthetic
   scenarios;
4. complete Galaxy Watch8 touch/AOD/battery/reboot/disconnect testing;
5. verify direct CareSens and xDrip broadcast remain absent from both manifests and provider UI,
   and threat-model HTTP opt-out, exported components, and Data Layer
   command/revision/acknowledgement handling;
6. perform wellness/medical wording review;
7. publish user-facing privacy and deletion controls;
8. complete production signing, instrumentation, and Play policy review;
9. run a supervised pilot with explicit stop criteria and the official CGM app retained.
