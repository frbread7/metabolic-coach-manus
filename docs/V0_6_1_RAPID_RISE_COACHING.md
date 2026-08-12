# v0.6.1 Confirmed rapid-rise walk coaching

Status: `Engineering: PASS / Physical: DEFERRED`.

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

## Engineering evidence

The feature revision is `8b46771594809400dbf7620410eafd7dd24f504e`. The authoritative local
milestone pipeline ran from that clean revision on 2026-08-12:

```text
./scripts/build-apks.sh
BUILD SUCCESSFUL in 10m 54s
333 actionable tasks: 333 executed
```

The pipeline passed model/domain/data-debug/data-release/sync/phone/Wear unit tests, Android
migration-test source compilation, phone/Wear/watch-face lint and debug assembly, APK Signature
Scheme v2 verification, certificate continuity, WFF v4 schema and APK memory-footprint validation,
and deterministic five-file ZIP packaging. JUnit XML reports contain 58 suites and 365 executions,
with 0 failures, 0 errors, and 0 skipped. Android instrumentation was compiled but not executed
because no Android runtime was attached.

Independent static review returned code `PASS` and architecture/safety `GO`, with no blocking or
high-severity findings. The artifact verifier confirmed versionName `0.6.1`, versionCode `10`, the
accepted engineering certificate, exact ZIP contents, and a clean credential/privacy scan.

Final APOS review through the repository-bound ChatGPT Pro line returned `APPROVE ENGINEERING
PASS` with `Blocker: None`. The approval keeps this milestone at `Physical: DEFERRED` and permits
only a separate v0.6.2 inactivity-WALK architecture gate next; it does not authorize v0.6.2
implementation or stair coaching.

```text
0916fb2921d7ddbfae995414377602040f17b00656c703af6fec4434fdd14ae4  metabolic-coach-phone-debug.apk
9a3be72f6bab8bbd683694b6ac352f00eea20ae50e1e00725d559a9dc1b0cb24  metabolic-coach-wear-debug.apk
fa2f4f041ad56a3e36417a8f7cd428671bb26c7e87f8dd74f6e1ee2cbb4d7f0b  metabolic-coach-watchface-debug.apk
bed24eaf69170140c261b3d6fc6d7bea1b7644c9e9cd2a5db56c0aa3cde7be91  MetabolicCoach-v0.6.1.zip
```

All physical rows remain `NOT RUN`; this evidence establishes engineering completion only.
