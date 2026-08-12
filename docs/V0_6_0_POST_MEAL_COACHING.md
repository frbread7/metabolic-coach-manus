# v0.6.0 Post-meal walk coaching

Status: `Engineering: PASS / Physical: DEFERRED`.

## Architecture decision

The APOS architecture gate returned `GO WITH CONDITIONS`. This release implements one real
end-to-end behavior only:

`Mark meal → unique delayed work → Nightscout refresh → shared exercise-safety policy → one walk
prompt → Start or Snooze → completion → existing same-source descriptive follow-up`.

The phone remains authoritative. The watch never accesses Nightscout. A recommendation records the
meal identity/time, algorithm version, exact glucose source, and safety-reading identity/time. The
immutable phone snapshot is used to validate phone notification and Wear commands.

## Invariants

- Production can emit only `POST_MEAL_WINDOW` actions in this version.
- A newer meal replaces pending work for the older meal; a worker for a superseded meal exits.
- The recommendation identity is stable for the meal, exact source, and algorithm version.
- Repeated refreshes cannot create a second session for that recommendation.
- Recommendation consumption and coached-session insertion are one Room transaction.
- Snooze suppresses the prompt and allows at most one redelivery while the original validity and
  safety conditions still hold.
- Start consumes the recommendation. Completion cannot revive it.
- Source changes and incomplete legacy provenance make a coached action non-actionable.
- Delayed refresh failures stop after three total worker attempts or when the meal window expires,
  whichever comes first.
- No result is a medical claim, treatment instruction, or replacement for CGM alarms/care plans.

## Explicitly deferred

Rapid-rise coaching, inactivity/stair coaching, competing triggers, automatic personalization,
AI recommendations, chart gestures, new history/GMI rules, and new glucose providers are not part
of this milestone.

## Physical acceptance checklist

Record only `PASS`, `FAIL`, or `NOT TESTED` plus non-sensitive notes. Do not record glucose values,
server addresses, credentials, or health screenshots.

| ID | Check | Result |
| --- | --- | --- |
| V060-IN-01 | Install all three `0.6.0` APKs over the accepted build without clearing data; confirm settings/history/planner remain. | `NOT TESTED` |
| V060-CFG-01 | Enable post-meal reminders and temporarily select the shortest real delay/window suitable for testing. | `NOT TESTED` |
| V060-TR-01 | Mark one real meal; no prompt appears before the configured delay, and one prompt appears inside the configured window when glucose is fresh/safe. | `NOT TESTED` |
| V060-TR-02 | Mark a second meal before the first delay; only the newer meal can produce a prompt. | `NOT TESTED` |
| V060-SF-01 | With missing/future/stale/low/rapidly-falling state, no exercise Start action is offered; record categories only. | `NOT TESTED` |
| V060-SC-01 | A rapid rise without an eligible meal and prolonged inactivity do not produce production walk/stair prompts. | `NOT TESTED` |
| V060-SY-01 | The same post-meal prompt appears consistently on phone, notification, Wear app, and watch face without duplicates. | `NOT TESTED` |
| V060-AC-01 | Start from phone once; one walk session starts, Watch converges, and repeated taps/replayed delivery do not create another session. | `NOT TESTED` |
| V060-AC-02 | Repeat with Start from Wear; phone acknowledges the action and both devices converge to one active session. | `NOT TESTED` |
| V060-SN-01 | Snooze hides the prompt; it redelivers no more than once, only while still valid and safe. | `NOT TESTED` |
| V060-CO-01 | Complete the session once; it remains completed after refresh/reconnect and the meal prompt never returns. | `NOT TESTED` |
| V060-FU-01 | After the configured follow-up time, any observation is neutral/descriptive and does not claim causation or treatment benefit. | `NOT TESTED` |
| V060-RC-01 | Temporary phone network loss, Bluetooth loss, phone/watch process restart, and reconnect do not duplicate or revive the prompt/session. | `NOT TESTED` |
| V060-REG-01 | Today freshness, History, Planner, Settings, watch glucose, and watch face remain usable with no regression. | `NOT TESTED` |
| V060-ST-01 | Repeated normal use shows no crash, ANR, excessive heat, or obvious battery/network loop. | `NOT TESTED` |

These rows remain in the cumulative root `PHYSICAL_TEST_BACKLOG.md`. Under the APOS-approved
deferred process, later walk-coaching engineering may continue, but this milestone must not be
called physically accepted until the user reports a passing real-device run.
