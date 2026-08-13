# v0.6.2 Prolonged-inactivity walk coaching

Status: `Engineering: PASS / Physical: DEFERRED`.

## APOS architecture decision

The repository-bound ChatGPT Pro architecture gate returned `GO WITH CONDITIONS` on 2026-08-12.
The approved increment activates one prolonged-inactivity **WALK** opportunity through the existing
canonical coaching pipeline. It does not activate automated stairs.

The existing configurable `staleReadingMinutes` value is the conservative maximum age for both
glucose and activity data used to authorize coached exercise in this milestone. Its default and
allowed range must not change. If real-device testing shows that normal activity latency makes the
feature unavailable, development must stop for a separate activity-freshness architecture review;
the shared limit must not be loosened as a workaround.

## Required behavior

- A pure `InactivityConfirmationPolicy` must fail closed for missing, blank-source, future,
  internally inconsistent, previous-day, cross-midnight, or stale activity data.
- Eligibility begins exactly at the configured inactivity threshold. Activity becomes stale exactly
  at the configured freshness boundary.
- Automated inactivity coaching always produces `WALK` when walking reminders are enabled.
  `stairRemindersEnabled` has no effect on this branch; manual stair actions remain unchanged.
- Production priority is post-meal, then confirmed rapid rise, then prolonged inactivity.
- Algorithm-v4 episode identity is derived from the reason, activity source, last movement,
  threshold crossing, and algorithm version. Glucose refreshes, activity refresh time, step/floor
  totals, and notification time must not change the identity.
- Candidate validity is bounded by the earlier of glucose freshness and activity-snapshot
  freshness. A persisted snapshot remains immutable and must not gain a later expiry.
- New movement, activity-source change, activity invalidation, settings mismatch, working-hours
  exit, glucose-source change, unsafe glucose, expiry, active session, or consumption invalidates
  the action.
- Working hours and activity identity are rechecked during generation, phone/Watch display, and
  phone Start. Delayed commands may fail closed.
- Phone remains the only recommendation publisher. Wear verifies current display/action context but
  never generates a replacement recommendation.
- The phone card, notification, and Wear state must resolve from the same coordinator-published
  immutable snapshot. Merely synthesizing or storing an inactivity candidate is not sufficient to
  expose it on the phone card.
- Notification and complication visibility ends at the earliest immutable validity, quiet-hours
  start, or inactivity working-hours end. A not-yet-applied delayed Start fails closed after
  invalidation; an identical replay of an already applied Start remains idempotently applied.

## Frozen boundaries

This milestone must not change:

- Nightscout or activity provider retrieval behavior;
- polling cadence, WorkManager jobs, or background wake behavior;
- glucose safety classification/outcomes, safety thresholds, or freshness-setting bounds;
- Room entities, schema, migrations, export, or reset guarantees;
- Wear Data Layer fields or schema;
- notification PendingIntent/action contracts;
- generic command replay, acknowledgement, session, or follow-up semantics;
- automated stair availability;
- current-glucose selection, source isolation, History, Planner, or GMI behavior.

## Engineering gate

Before `PROLONGED_INACTIVITY` enters the production allowlist, automated tests must cover WALK-only
behavior, stair exclusion, priority, all activity timestamp/freshness/day boundaries, stable
identity, movement/source/settings supersession, working/quiet hours, snooze/cooldown/daily cap,
immutable snapshot retention, phone Start revalidation, phone/Wear parity, reconnect/replay, and
idempotent session creation. All frozen regression suites, lint, APK/WFF/signature/package checks,
and independent code/architecture review must pass.

The targeted domain/data/phone/Wear gate reported `BUILD SUCCESSFUL` with 154 actionable tasks on
2026-08-12. The forced authoritative pipeline then reported `BUILD SUCCESSFUL in 8m 11s` with all
333 tasks executed on 2026-08-13. Fresh reports contain 61 JUnit suites/420 executions with zero
failures, errors, or skips. Lint, APK assembly/signatures, certificate continuity, WFF source/APK
validation, five-file packaging, and the standalone metadata/privacy verifier passed.

Independent review found and resolved replay-idempotency, episode-consumption/resurrection,
immutable-snapshot supersession, display-deadline, publication-parity, and phone-card publication
defects before the feature commit. Final APOS review returned `FINAL GO` with no blockers. It
records one non-blocking reliability characteristic: Watch publication precedes phone publication
bookkeeping, so process failure in that narrow interval fails closed toward a missed prompt rather
than a duplicate or resurrected prompt. Reconnect/replay for that interval remains in the physical
backlog.

All modules use versionName `0.6.2` and versionCode `11` for the engineering artifact. Device checks
remain in the cumulative physical-test backlog for the later `v0.7.0` integrated walk-coaching RC.
This milestone must never be described as physically accepted without a pinned real-device result.

Completion evidence:

```text
Architecture gate commit: 9659927694532c3d4960bebc970337017174ddce
Feature commit: 09c977a734af51f88709bd3f88b3ccbc2b01fbb1
Full pipeline: BUILD SUCCESSFUL in 8m 11s; 333/333 tasks executed
JUnit XML: 61 suites; 420 executions; 0 failures/errors/skips
Independent review: PASS after all blocker/high findings were resolved
Final APOS decision: FINAL GO; no blockers
Phone SHA-256: 67449bb4a7a1b80fba86127a8c768167afc28714102eab0e08f09f1391be53b8
Wear SHA-256: f8f75c528dcceec34aef0b0dc86b5e6716ba30a3c92c802696dbe876d89db364
Watch-face SHA-256: 4d84ff84dc0fa6128a761e4687ad6b8ba9360328984273acbbd7b0bc25e9b753
ZIP SHA-256: aad8dbafbf0a66ddcd8202a51be0b6b4c18fde8ea030b9adc1bb7097ef755e01
Signing certificate SHA-256: 7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4
```

## Stop conditions

Return for architecture review before any dedicated activity-freshness setting, provider query,
faster scheduling, safety change, persistence migration, Wear protocol field, notification-action
change, session/replay change, cross-source inference, episode analytics, or stair activation.
Require an earlier physical gate if engineering exposes repeated alerts for one episode, stale
activity authorizing Start, phone/Watch policy disagreement, unexpected background/battery behavior,
or any automated stairs path.
