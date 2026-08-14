# Physical test backlog

This is the cumulative privacy-safe backlog for behavior that automated tests cannot prove on a
real Android phone and Samsung Galaxy Watch8. It is not an acceptance record.

## Status model

Milestones use two independent axes:

- **Engineering:** `PASS` or `FAIL`.
- **Physical:** `NOT_REQUIRED`, `DEFERRED`, `ACCEPTED`, or `FAILED/BLOCKED`.

`Engineering: PASS / Physical: DEFERRED` permits later engineering milestones under the rules in
[docs/MILESTONE_PROCESS.md](docs/MILESTONE_PROCESS.md), but it must never be shortened to
"accepted". Only user-reported real-device evidence can set `Physical: ACCEPTED`.

For individual checks use `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN`. Record categorical observations
only. Never record glucose values, server addresses, credentials, health screenshots, exports, or
unsanitized logs.

## Deferred checks

| ID | Milestone / commit | Surface | Device behavior requiring verification | Prerequisite state | Status | Later dependency |
| --- | --- | --- | --- | --- | --- | --- |
| PTB-V060-IN | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Phone + Watch | Matched phone, Wear, and watch-face APKs upgrade without clearing accepted settings/history/planner data. | Accepted `v0.5.1` installation | `NOT RUN` | `v0.7.0` RC |
| PTB-V060-TR | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Phone | A real meal marker produces no early prompt and at most one eligible post-meal walk prompt inside the configured window. | Real marker; ordinary fresh/safe state; reminders enabled | `NOT RUN` | `v0.7.0` RC |
| PTB-V060-SF | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Phone + Watch | Missing, future, stale, low, or rapidly falling state never leaves an actionable exercise prompt. | Category can be observed without recording readings | `NOT RUN` | `v0.7.0` RC; safety gate |
| PTB-V060-SY | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Phone + Watch + face | One canonical recommendation converges across phone card, notification, Wear app, and watch face without duplicate alerts. | Paired/connected devices; notification permission state recorded categorically | `NOT RUN` | `v0.7.0` RC |
| PTB-V060-AC | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Phone + Watch | Start, replay, completion, and reconnect converge to exactly one session; consumed or expired prompts do not revive. | Valid prompt; test Start separately from phone and Watch | `NOT RUN` | `v0.7.0` RC; command integrity |
| PTB-V060-SN | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Phone + Watch | Snooze hides the prompt and permits no more than the bounded valid/safe redelivery behavior. | Valid prompt with enough remaining validity | `NOT RUN` | `v0.7.0` RC |
| PTB-V060-RC | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Phone + Watch | Phone/watch restart, Bluetooth loss, network loss, and reconnect do not duplicate, revive, or strand recommendation/session state. | Pinned matched artifacts | `NOT RUN` | `v0.7.0` RC; foundational if failed |
| PTB-V060-AO | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Galaxy Watch8 face | Coaching state remains readable and non-stale through interactive/AOD transitions without clipping or repeated wake behavior. | Installed WFF face; AOD enabled | `NOT RUN` | `v0.7.0` RC |
| PTB-V060-ST | `v0.6.0` / `46a2a898757f9caca67752537b25da364955f95b` | Phone + Watch | Representative use has no crash, ANR, excess heat, obvious battery/network loop, or notification storm. | Normal daily configuration | `NOT RUN` | `v0.7.0` RC |
| PTB-V061-TR | `v0.6.1` / `8b46771594809400dbf7620410eafd7dd24f504e` | Phone | A confirmed same-source rapid rise can produce one short-walk recommendation through normal refresh evaluation; no immediacy promise. | Engineering artifact complete; ordinary fresh/safe state | `NOT RUN` | `v0.7.0` RC |
| PTB-V061-AR | `v0.6.1` / `8b46771594809400dbf7620410eafd7dd24f504e` | Phone + Watch | When post-meal and rapid-rise opportunities overlap, exactly one post-meal recommendation wins; global snooze/cooldown/cap behavior remains coherent. | Both opportunities occur naturally; no synthetic production history | `NOT RUN` | `v0.7.0` RC |
| PTB-V061-SY | `v0.6.1` / `8b46771594809400dbf7620410eafd7dd24f504e` | Phone + Watch + face | Rapid-rise reason, action, expiry, Start/Snooze, reconnect, and completion reuse the existing canonical transport without duplicates. | Pinned matched engineering artifacts | `NOT RUN` | `v0.7.0` RC |
| PTB-V061-SF | `v0.6.1` / `8b46771594809400dbf7620410eafd7dd24f504e` | Phone + Watch | A newer stable/nonqualifying reading, changed source, stale state, or incomplete state removes the rapid action; an already visible stale action cannot start a session. | Observe categories only; do not force unsafe health conditions | `NOT RUN` | `v0.7.0` RC; safety gate |
| PTB-V061-RP | `v0.6.1` / `8b46771594809400dbf7620410eafd7dd24f504e` | Phone + Watch | Repeated refresh, process restart, Bluetooth loss, queued Start, and reconnect neither republish a superseded pair nor duplicate a session/notification. | Valid naturally occurring prompt; pinned artifacts | `NOT RUN` | `v0.7.0` RC; command integrity |
| PTB-V061-ST | `v0.6.1` / `8b46771594809400dbf7620410eafd7dd24f504e` | Phone + Watch | Representative use shows no crash, ANR, excess heat, notification storm, or obvious extra battery/network loop from rapid evaluation. | Normal daily configuration | `NOT RUN` | `v0.7.0` RC |
| PTB-V062-TR | `v0.6.2` / `09c977a734af51f88709bd3f88b3ccbc2b01fbb1` | Phone | One naturally eligible prolonged-inactivity episode produces at most one WALK recommendation after post-meal and rapid-rise priority is applied; automated stairs never appear. | Matched pinned RC artifacts; walking reminders enabled; ordinary fresh/safe state | `NOT RUN` | `v0.7.0` RC |
| PTB-V062-AF | `v0.6.2` / `09c977a734af51f88709bd3f88b3ccbc2b01fbb1` | Phone + Watch | Missing/blank-source, future, inconsistent, previous-day, cross-midnight, or stale activity context never leaves an actionable inactivity prompt. | Observe categories without recording health values or creating unsafe conditions | `NOT RUN` | `v0.7.0` RC; safety gate |
| PTB-V062-ID | `v0.6.2` / `09c977a734af51f88709bd3f88b3ccbc2b01fbb1` | Phone + Watch + face | Routine refreshes retain one immutable episode/action, while new movement, activity-source change, settings mismatch, working-hours exit, expiry, source change, unsafe glucose, consumption, or an active session removes it without resurrection. | Naturally eligible prompt; pinned artifacts | `NOT RUN` | `v0.7.0` RC; identity/integrity gate |
| PTB-V062-AC | `v0.6.2` / `09c977a734af51f88709bd3f88b3ccbc2b01fbb1` | Phone + Watch | A not-yet-applied delayed Start revalidates the latest activity context and fails closed after movement, expiry, or context change; an identical replay of an already applied Start remains idempotently applied and never creates a second WALK session. | Test phone and Watch Start separately against a naturally eligible prompt | `NOT RUN` | `v0.7.0` RC; command integrity |
| PTB-V062-SY | `v0.6.2` / `09c977a734af51f88709bd3f88b3ccbc2b01fbb1` | Phone + Watch + face | The phone-authored inactivity recommendation, expiry, Start/Snooze result, reconnect, and completion converge through the existing Wear payload without duplicates or stale actions. | Pinned matched RC artifacts; paired devices | `NOT RUN` | `v0.7.0` RC |
| PTB-V062-BG | `v0.6.2` / `09c977a734af51f88709bd3f88b3ccbc2b01fbb1` | Phone + Watch | Representative inactivity evaluation creates no extra polling cadence, background wake loop, notification storm, crash, ANR, excess heat, or obvious battery regression. Include reconnect/replay behavior around the fail-closed Watch-publish/phone-record interval. | Normal daily configuration; compare categorically with prior accepted behavior | `NOT RUN` | `v0.7.0` RC; foundational if failed |
| PTB-V071-IN | `v0.7.1` / `bfe1f3c4d002edd641ff895933b43d8032080b3d` | Phone | The matched phone APK upgrades without clearing settings, history, or planner data and reports versionName `0.7.1`, versionCode `13`. | Prior compatible engineering installation; matching certificate | `NOT RUN` | Batched phone RC |
| PTB-V071-HI | `v0.7.1` / `bfe1f3c4d002edd641ff895933b43d8032080b3d` | Phone | The 6h and 12h History choices render the matching local elapsed windows with disconnected gaps and remain responsive. | Existing local exact-source history | `NOT RUN` | Batched phone RC |
| PTB-V071-RC | `v0.7.1` / `bfe1f3c4d002edd641ff895933b43d8032080b3d` | Phone | Rapid 6h → 12h → 24h changes publish only the final selection; restart restores the last fixed choice without visible refresh/backfill work. | Local history; normal connectivity | `NOT RUN` | Batched phone RC |
| PTB-V071-GM | `v0.7.1` / `bfe1f3c4d002edd641ff895933b43d8032080b3d` | Phone | Six- and twelve-hour periods retain the insufficient-duration state and never display numeric selected-period GMI. | Either period selected | `NOT RUN` | Health-wording regression |
| PTB-V071-ST | `v0.7.1` / `bfe1f3c4d002edd641ff895933b43d8032080b3d` | Phone + Watch | Repeated History use causes no crash, ANR, abnormal heat, obvious network/backfill activity, or regression in Today, Settings, Planner, coaching, Wear, or watch-face behavior. | Matched artifacts; representative use | `NOT RUN` | Batched integration RC |
| PTB-V072-IN | `v0.7.2` / `PENDING` | Phone | The matched phone APK upgrades without clearing settings, history, or planner data and reports versionName `0.7.2`, versionCode `14`. | Prior compatible engineering installation; matching certificate | `NOT RUN` | Batched phone RC |
| PTB-V072-GE | `v0.7.2` / `PENDING` | Phone | Pinch zoom, horizontal pan, vertical page scroll, and visible zoom/reset controls arbitrate correctly from 30 minutes through the full selected period. | Existing exact-source local history | `NOT RUN` | Batched phone RC |
| PTB-V072-DA | `v0.7.2` / `PENDING` | Phone | Rapid interaction publishes only the final exact-source window, preserves extrema/disconnected gaps, and never relabels old rendered geometry while loading or after failure. | Local history with representative gaps; normal use | `NOT RUN` | Data-integrity regression |
| PTB-V072-GM | `v0.7.2` / `PENDING` | Phone | Zoom, pan, Reset, and retry leave selected-period GMI, qualifiers, coverage, dates, and disclaimer unchanged. | An eligible or ineligible selected-period result | `NOT RUN` | Health-wording regression |
| PTB-V072-ST | `v0.7.2` / `PENDING` | Phone + Watch | Repeated interaction causes no crash, ANR, excess heat, unexpected network/backfill activity, or regression in Today, Settings, Planner, coaching, Wear, or watch-face behavior. | Matched artifacts; representative use | `NOT RUN` | Batched integration RC |

The detailed `v0.6.0` procedure remains in
[docs/V0_6_0_POST_MEAL_COACHING.md](docs/V0_6_0_POST_MEAL_COACHING.md). The `v0.6.1` engineering
contract is in
[docs/V0_6_1_RAPID_RISE_COACHING.md](docs/V0_6_1_RAPID_RISE_COACHING.md). The `v0.6.2` engineering
contract and its fail-closed activity boundaries are in
[docs/V0_6_2_INACTIVITY_WALK_COACHING.md](docs/V0_6_2_INACTIVITY_WALK_COACHING.md). The rows are
pinned to the final v0.6.2 feature commit and are consolidated into the `v0.7.0` integrated
walk-coaching release-candidate checklist.

The consolidated RC procedure now exists at
[docs/V0_7_0_INTEGRATED_WALK_RC.md](docs/V0_7_0_INTEGRATED_WALK_RC.md). Every row above remains
`NOT RUN`; the user's decision to defer this batch is recorded as `Physical: DEFERRED`, not as a
waiver or acceptance. The RC introduces no automated stairs and no new device behavior.
