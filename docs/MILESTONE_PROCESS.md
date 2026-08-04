# Milestone development process

## Purpose

Metabolic Coach uses gated development because it handles health data and spans a phone, Wear OS,
Google Play services, a physical watch, and a watch face. Automated verification cannot prove
radio recovery, round-screen layout, Always-On Display behavior, or real battery impact.

This process is permanent unless an architecture review explicitly replaces it.

## Required sequence

Every milestone follows this order:

1. Architecture discussion defines the objective, exclusions, safety boundaries, acceptance
   criteria, and artifact/version strategy.
2. Codex implements only the approved scope.
3. Automated verification runs and records exact commands and results.
4. The user performs the written physical-device checklist against pinned artifacts.
5. The user reports results using `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN` without exposing private
   health data.
6. Architecture review evaluates the evidence, defects, and whether the milestone can close.
7. Only then is the next milestone planned and unlocked.

The version-by-version checklist index is [PHYSICAL_ACCEPTANCE_CHECKLISTS.md](PHYSICAL_ACCEPTANCE_CHECKLISTS.md).
It is an index and planning aid; a detailed milestone record remains authoritative when one exists.

Automated success never substitutes for physical acceptance. Physical acceptance never substitutes
for source review, reproducible builds, or automated tests.

## Gate rules

- While a physical gate is open, production code is frozen.
- Allowed work is limited to read-only verification, installation or test documentation, evidence
  capture, and a critical pre-install defect that prevents safe installation or synchronization.
- New features, opportunistic refactors, dependency changes, UI redesigns, and work assigned to a
  later milestone are prohibited.
- When the user reports physical results, stop implementation. Record and review the evidence before
  deciding whether a narrowly scoped fix, a retest, or the next milestone is appropriate.
- A milestone passes only when every required item is `PASS`. `BLOCKED` and `NOT RUN` do not count as
  acceptance.
- Failed items remain in the record. A retest appends new evidence and references the defect/fix;
  it does not overwrite the original result.

A **critical pre-install defect** is limited to an issue that prevents installation, prevents the
required phone/watch path from functioning, corrupts or misrepresents synchronized health data,
causes a crash/ANR in the required path, or creates a material security/privacy risk. Cosmetic
preferences and future functionality do not qualify.

## Evidence record

Before physical testing, pin:

- repository, branch, and commit;
- dirty-worktree state;
- archive filename, APK version/build variant, archive SHA-256, and individual APK SHA-256 values;
- phone/Wear package identity and signing-certificate SHA-256;
- phone model/Android build and watch model/size/Wear OS build;
- acceptance thresholds selected before observing results;
- UTC start and completion times;
- privacy-sanitized evidence locations.

Each check records its expected result, actual result, result code, timestamp, and evidence or a
short sanitized note. Never place a personal Nightscout URL, credential, glucose value, health
screenshot, data export, or unsanitized log in Git, chat handoffs, or test reports.

## Cross-session coordination

When the installed ChatGPT Pro bridge is available, Codex may continue a user-designated existing
conversation through a repository-scoped alias. It must fail closed: bind only the supplied
conversation, run the live doctor, verify alias status, export recent history, and confirm project
continuity before sending. Do not create a replacement room, silently repair to another
conversation, upload repository context unless explicitly authorized, or record the private
conversation URL in project documentation. If any continuity check fails, use an explicit
user-mediated handoff instead.

Every outbound bridge call must use a focused, privacy-sanitized handoff and retain its local
receipt/transcript outside Git. The exact sent and received messages are echoed to the user. The
repository alias remains unchanged after the call.

Use this template in either direction:

```text
Goal/current milestone:
Gate state:
Repository / branch / commit:
Dirty files:
Artifact ZIP / APK version / build variant / SHA-256:
Signing status and certificate SHA-256:
Devices and OS builds:
Completed checks and evidence:
Failed / blocked / not-run checks:
Decisions and invariants:
Explicit exclusions:
Known risks:
Exact next action:
Stop condition:
Files changed:
Tests/checks run:
UTC handoff time:
```

The receiving session must treat the newest user-provided evidence as authoritative, retain all
non-conflicting earlier constraints, and avoid inferring acceptance that the user did not report.

## Current gate

- `v0.2` phone-side Nightscout acceptance: accepted by the user on 2026-08-01.
- `v0.3` physical Galaxy Watch8 acceptance: the user reported completion on 2026-08-02. The
  privacy-sanitized record is [V0_3_PHYSICAL_ACCEPTANCE.md](acceptance/V0_3_PHYSICAL_ACCEPTANCE.md);
  it records user evidence and does not fabricate device logs or private health values.
- Architecture review: the continued ChatGPT architecture conversation returned `READY WITH
  CONDITIONS` for the staged history/chart/GMI/coaching roadmap. v0.5.0 is limited to the local
  history foundation; the handoff is recorded in `docs/V0_5_0_HISTORY_FOUNDATION.md`.
- Automated preflight: the historical v0.3 sync/phone/Wear pre-install suite passed with all 200
  selected Gradle tasks executed. The current v0.4.2 authoritative local package run passed 333
  selected tasks, 52 local test suites/288 executions, lint, APK/WFF validation, and packaging.
- `v0.5.0` physical acceptance: the user reported the phone-only history checklist passed on
  2026-08-04. The privacy-sanitized record is
  [V0_5_0_PHYSICAL_ACCEPTANCE.md](acceptance/V0_5_0_PHYSICAL_ACCEPTANCE.md).
- `v0.5.1` physical acceptance: the required APOS review returned `GO WITH CONDITIONS` for the
  phone-only local trend chart and selected-period GMI, and the user reported the
  [phone physical gate](V0_5_1_HISTORY_EXPLORER.md) passed on 2026-08-04. Wear, watch-face,
  coaching, notification, and current-glucose freshness behavior remained frozen.
- `v0.6.0` architecture gate: APOS returned `GO WITH CONDITIONS` for one post-meal delayed walk
  workflow. Implementation is limited to this trigger and stops for the phone/Galaxy Watch8
  checklist in [V0_6_0_POST_MEAL_COACHING.md](V0_6_0_POST_MEAL_COACHING.md).

The completed physical test was defined in [V0.3_WEAR_ACCEPTANCE.md](V0.3_WEAR_ACCEPTANCE.md).
