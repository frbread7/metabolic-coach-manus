# v0.6.1 Confirmed rapid-rise walk coaching

Status during implementation: `Engineering: VERIFICATION / Physical: DEFERRED`.

## Architecture decision

The APOS architecture gate returned `GO WITH CONDITIONS` for an engineering-only rapid-rise walk
increment. This milestone reuses the normalized glucose, coaching safety, immutable recommendation,
notification, quick-action, session, Wear Data Layer, and follow-up paths already present in
`v0.6.0`.

It does not add a provider call, faster polling, WorkManager job, Room entity/migration, Wear field,
notification action, stair action, prediction, or automatic personalization.

## Trigger contract

A rapid-rise action is eligible only when:

1. the selected current reading and its immediate deterministic predecessor belong to the same
   exact source;
2. their IDs differ and timestamps increase strictly;
3. their separation is positive and no longer than the configured stale-reading window;
4. both effective rates (`rateMgDlPerMinute`, otherwise the normalized trend fallback) are at or
   above the configured rapid-rise threshold;
5. the existing exercise-safety, settings, quiet-hours, snooze, global cooldown, and daily-cap
   gates allow the action.

Post-meal wins when both post-meal and rapid-rise candidates are eligible. Inactivity and stairs
remain disabled in production.

The rapid algorithm is version 3. Its bounded deterministic ID fingerprints the exact source, both
reading IDs/timestamps, and algorithm version. Legacy rapid snapshots therefore cannot collide
with this milestone. The pair identity and newest reading are preserved in existing trigger and
safety-provenance fields; no Wear schema change is required.

## Fail-closed lifecycle

- No prior local exact-source reading means no rapid action and no extra network request.
- A changed source, timestamp tie, excessive gap, nonqualifying reading, new pair, expiry,
  consumption, active snooze, quiet hours, or reminder disablement hides the retained rapid action.
- The same immutable pair snapshot may remain visible during global cooldown/daily-cap evaluation;
  repeated publication cannot create extra delivery or session records.
- Phone and Wear require complete trigger/source/safety provenance. Rapid Start revalidates the
  newest exact-source pair as of the original tap time before the existing safety and idempotent
  session path runs.

## Deferred physical checks

The privacy-safe device rows are cumulative in
[PHYSICAL_TEST_BACKLOG.md](../PHYSICAL_TEST_BACKLOG.md). They cover natural trigger observation,
post-meal arbitration, source/pair supersession, notification/card/watch convergence, Start/Snooze,
offline/reconnect replay, AOD/watch-face behavior, and stability without recording private glucose
values or source details.

This milestone must not be described as physically accepted until the user reports a passing
real-device run against a pinned artifact.
