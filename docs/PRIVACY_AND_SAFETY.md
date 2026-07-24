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
| Glucose | Value, trend/rate, delta, sample/import time, source package | Health Connect or debug-only xDrip broadcast | Phone Room; latest state on watch |
| Activity | Steps, floors, heart rate, active calories, last movement, daily exercise-session count/duration | Health Connect | Phone Room; latest state on watch |
| Behavior | Meal markers, walk/stair sessions, completion, immutable phone-authored recommendation snapshots, and exact trigger/recommendation/activity-dose/baseline/source/follow-up provenance | User actions, coach actions, settings-at-start, and selected glucose source | Phone Room; active/pending session replica and generic command outbox on watch |
| Preferences | Thresholds, time windows, units, reminders, theme/font, selected Health Connect glucose-writer package, phone sync instance/revision/reset token, and bounded terminal-command history | User and application-generated metadata | Phone DataStore; synchronized watch state |
| Device | Phone/watch battery percentage | Android system | Latest synchronized/display state |

The app requests read-only Health Connect record permissions. It does not write back to Health
Connect.

## Data movement

```text
Authorized source -> phone provider -> local Room/DataStore
                                    -> Wear Data Layer -> watch local cache
```

There is no project cloud account, analytics SDK, advertising SDK, or custom backend in the current
source. That does not mean all data stays on a direct Bluetooth link: the official Wear OS
[Data Layer overview](https://developer.android.com/training/wearables/data/overview) says traffic
can route through Google infrastructure when Bluetooth is unavailable and is end-to-end encrypted.

Health Connect, Google Play services, Samsung Health, CareSens, and any xDrip installation used for
debug testing each have their own privacy behavior and policies. Metabolic Coach documentation
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
- Do not silently select an unofficial provider when an official route is unavailable.
- Do not silently switch between Health Connect glucose writers. Multiple unconfigured writer
  packages pause glucose coaching until the user saves one.

The current alpha asks for background-read access only when Health Connect reports that feature.
Periodic work is enabled only after the separate permission is granted; unsupported or denied
access cancels periodic work while manual foreground Refresh remains available. The permission
explanation and real-device grant, denial, revocation, reboot, and scheduling behavior still need
validation before background coaching can be considered reliable.

## Safety behavior in code

The rule engine:

- receives glucose only from the exact Health Connect writer package selected on the phone;
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
- Health Connect publication latency from CareSens/Samsung is unverified.
- Health Connect writer-package discovery and persisted selection have not been validated against
  real CareSens/Samsung duplicate-writer, revocation, or source-reappearance scenarios.
- No direct CareSens contract supplies vendor trend semantics.
- Health Connect trends are inferred from consecutive values.
- The debug-only xDrip sender-package provenance is checked only on Android 14+, no signing
  certificate is pinned, and the sender contract is not verified end to end. Release builds omit
  its receiver, permission, and provider selection.
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

## xDrip threat boundary

The xDrip receiver is exported only in the debug phone variant for inter-app delivery. It rejects
Android versions below 14, requires `BroadcastReceiver.getSentFromPackage()` to report
`com.eveningoutpost.dexdrip`, and rejects malformed, out-of-range, stale, future, wrong-mode, and
implausible-rate samples. The release manifest has no xDrip receiver or receive permission, and
the release UI does not expose the provider. Release repository policy also replaces any xDrip
selection retained from a debug install with Health Connect before data is read.

The package check provides platform-reported provenance, not certificate pinning. A malicious or
repackaged application occupying that package identity remains a risk, and the upstream sender
contract is not yet verified. This is why the compatibility path remains debug-only. Any proposal
to enable it in production requires a documented sender contract, authenticated or
signature-protected trust decision, and explicit security review.

## Logging and diagnostics

Production logging must not include:

- glucose values, trends, or timestamps;
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

Most current retention is local and indefinite until Android clears application data or the app is
uninstalled. Expired recommendation snapshots are opportunistically pruned when a later
recommendation is authored once their validity ended more than seven days earlier. There is no
configurable retention period for glucose, activity, meal, or intervention history.

The phone Settings screen can export effective settings and every application Room row through the
Android document picker. The schema-versioned JSON is written directly to the selected destination,
is not encrypted by Metabolic Coach, and may contain sensitive health/source metadata. The user
must choose and protect an appropriate destination. Technical phone command-history/reset metadata
is not part of the user data document.

Confirmed in-app erase:

- serializes with export and all known phone-owned local data writers so no operation crosses the
  deletion boundary;
- cancels known Metabolic Coach refresh and intervention follow-up work on a best-effort basis;
- deletes every application Room table and clears the complete coaching-settings DataStore;
- clears the phone coaching notification;
- rotates the synchronization instance and durable reset token;
- publishes an empty state so Wear clears its state/session and queued-command stores, including
  after an offline reconnection;
- rejects delayed watch commands from the pre-erase data epoch.

Erase does not revoke Health Connect/CGM permissions or delete records in source applications.
Opening or refreshing the app, receiving supported provider data, or a future scheduled run can
therefore collect new records again. A user who wants collection to stop must revoke the applicable
source permissions separately.

Uninstalling:

- the phone app removes its local database/settings;
- the Wear app removes its local cache/session;
- the watch-face package removes face resources;
- source data in Health Connect/Samsung Health/CareSens, or a debug xDrip source, is not deleted.

A production release still needs configurable retention policy, target-device lifecycle testing of
export/erase, and legal review of the export/deletion disclosures.

## Minimum public privacy policy content

The published policy must accurately describe:

- legal entity/contact and policy date;
- health/activity/device data types;
- purposes for collection and processing;
- Health Connect and Data Layer use;
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

1. prove one authorized provider's freshness and outage behavior;
2. validate foreground/background Health Connect behavior through denial, reboot, and process
   death on target phones;
3. validate intervention/follow-up recovery and observation wording with controlled synthetic
   scenarios;
4. complete Galaxy Watch8 touch/AOD/battery/reboot/disconnect testing;
5. verify xDrip remains absent from the release manifest and provider UI, and threat-model exported
   components plus Data Layer command/revision/acknowledgement handling;
6. perform wellness/medical wording review;
7. publish user-facing privacy and deletion controls;
8. complete production signing, instrumentation, and Play policy review;
9. run a supervised pilot with explicit stop criteria and the official CGM app retained.
