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
| PTB-V061-TR | `v0.6.1` / commit pending | Phone | A confirmed same-source rapid rise can produce one short-walk recommendation through normal refresh evaluation; no immediacy promise. | Engineering artifact complete; ordinary fresh/safe state | `NOT RUN` | `v0.7.0` RC |
| PTB-V061-AR | `v0.6.1` / commit pending | Phone + Watch | When post-meal and rapid-rise opportunities overlap, exactly one post-meal recommendation wins; global snooze/cooldown/cap behavior remains coherent. | Both opportunities occur naturally; no synthetic production history | `NOT RUN` | `v0.7.0` RC |
| PTB-V061-SY | `v0.6.1` / commit pending | Phone + Watch + face | Rapid-rise reason, action, expiry, Start/Snooze, reconnect, and completion reuse the existing canonical transport without duplicates. | Pinned matched engineering artifacts | `NOT RUN` | `v0.7.0` RC |

The detailed `v0.6.0` procedure remains in
[docs/V0_6_0_POST_MEAL_COACHING.md](docs/V0_6_0_POST_MEAL_COACHING.md). The backlog will be expanded
and consolidated before the promoted `v0.7.0` integrated walk-coaching release candidate.
