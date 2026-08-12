# v0.6.2 Prolonged-inactivity walk coaching

Status during implementation: `Engineering: IN PROGRESS / Physical: DEFERRED`.

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
- Algorithm-v4 episode identity is derived from the activity source, last movement, threshold
  crossing, and algorithm version. Glucose refreshes, activity refresh time, step/floor totals, and
  notification time must not change the identity.
- Candidate validity is bounded by the earlier of glucose freshness and activity-snapshot
  freshness. A persisted snapshot remains immutable and must not gain a later expiry.
- New movement, activity-source change, activity invalidation, settings mismatch, working-hours
  exit, glucose-source change, unsafe glucose, expiry, active session, or consumption invalidates
  the action.
- Working hours and activity identity are rechecked during generation, phone/Watch display, and
  phone Start. Delayed commands may fail closed.
- Phone remains the only recommendation publisher. Wear verifies current display/action context but
  never generates a replacement recommendation.

## Frozen boundaries

This milestone must not change:

- Nightscout or activity provider retrieval behavior;
- polling cadence, WorkManager jobs, or background wake behavior;
- `ExerciseSafetyPolicy`, safety thresholds, or freshness-setting bounds;
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

All modules use versionName `0.6.2` and versionCode `11` for the engineering artifact. Device checks
remain in the cumulative physical-test backlog for the later `v0.7.0` integrated walk-coaching RC.
This milestone must never be described as physically accepted without a pinned real-device result.

## Stop conditions

Return for architecture review before any dedicated activity-freshness setting, provider query,
faster scheduling, safety change, persistence migration, Wear protocol field, notification-action
change, session/replay change, cross-source inference, episode analytics, or stair activation.
Require an earlier physical gate if engineering exposes repeated alerts for one episode, stale
activity authorizing Start, phone/Watch policy disagreement, unexpected background/battery behavior,
or any automated stairs path.
