# v0.5.0 Local History Foundation

## Physical acceptance status

`USER_REPORTED_PASS` on 2026-08-04. The privacy-sanitized result is recorded in
[V0_5_0_PHYSICAL_ACCEPTANCE.md](acceptance/V0_5_0_PHYSICAL_ACCEPTANCE.md). The exact installed
artifact hash was not re-supplied with the report; the CI artifact remains the reproducible build
evidence.

## Scope

v0.5.0 is the phone-only history foundation approved after the architecture review. It makes
local Nightscout history explicit and inspectable without changing the current-reading refresh,
Wear Data Layer payloads, watch face, coaching rules, or notifications.

The release intentionally does not include a trend chart, selected-period GMI, new coaching
behavior, analytics, or a new provider. Those are separate gates after this foundation is accepted.

## What is stored today

Normalized `GlucoseReading` rows already live in the phone Room table `glucose_readings`. A normal
refresh stores the current snapshot first and then the provider's bounded recent history (currently
90 days). Rows are keyed by provider identity and timestamped with both measurement and receipt
time. The active source query is isolated from other configured Nightscout servers.

Before v0.5.0 there was no explicit retention contract or resumable older-history download. The
absence of pruning did not constitute a supported long-term-history guarantee. v0.5.0 adds that
contract without deleting existing rows automatically.

## Retention and backfill

Settings offers three choices:

- 90 days (the default)
- 1 year
- Keep all downloaded

Changing a policy clears its confirmation. No records are pruned until the user presses **Confirm
and apply policy**. Bounded policies prune by measurement time and source identity, transactionally,
while preserving the newest record for every source. `Keep all downloaded` disables automatic
pruning; it does not promise that a Nightscout server has unlimited history.

For a 1-year or keep-all policy, **Download one older range** retrieves one bounded 90-day range at
a time from the selected provider. The source-scoped checkpoint is stored in Room before and after
the request, so an interrupted or failed range can be retried without restarting from the newest
data. Older range reads never publish a reading as the current state and never alter Wear data.

## Privacy and reset

The existing Android backup rules exclude the database and app-private files from both cloud backup
and device transfer. Personal-data export includes the raw glucose table plus the retention and
backfill state in deterministic source/timestamp order. Erase local data clears these tables and
their checkpoints through the existing reset path; it does not delete Nightscout records or revoke
source permissions.

## Acceptance gate

Automated verification must cover Room migration 8→9, deterministic retention cutoffs, range-only
Nightscout reads, checkpoint persistence/error handling, source isolation, export/reset containment,
and the existing current-freshness/Wear/coaching regression suites. Physical phone acceptance must
confirm policy confirmation, one-range pause/resume, no silent deletion, restart recovery, and no
change to current glucose behavior. Stop after that phone acceptance and review before planning
v0.5.1.

## Phone acceptance checklist

Record only pass/fail and sanitized notes; do not record glucose values, server URLs, tokens, or
credentials.

- [x] Install the v0.5.0 phone APK over the accepted same-certificate phone build.
- [x] Existing current glucose still refreshes and displays with the same age/trend/delta behavior.
- [x] Settings shows stored row count and oldest/newest local dates after a successful refresh.
- [x] The default 90-day policy is visible and no deletion occurs before confirmation.
- [x] Selecting 1 year requires a new confirmation; confirming does not remove recent history.
- [x] Selecting Keep all downloaded requires a new confirmation and does not silently prune rows.
- [x] One older-range download advances the checkpoint and updates the local date range when data is
      returned.
- [x] A temporary network loss reports failure/paused state and a subsequent retry resumes the same
      bounded operation without a network storm.
- [x] Force-closing and reopening presents an interrupted running checkpoint as resumable/paused.
- [x] Switching configured Nightscout servers does not mix row counts/date ranges or backfill state.
- [x] Export includes history rows and management-state tables without credentials or endpoints.
- [x] Erase confirmation clears local rows/checkpoints and leaves source records untouched.
- [x] No chart, GMI, Wear payload, watch-face, coaching, notification, or quick-action behavior
      changes are observed.
- [x] No crash, ANR, or unacceptable battery/network behavior is observed during the test.

Stop after this checklist. Do not begin v0.5.1 chart/GMI implementation until the results are
reviewed in the architecture conversation.
