# v0.7.0 Integrated WALK coaching release candidate

Status: `Engineering: PASS / Physical: DEFERRED`.

`v0.7.0` is the behavior-neutral integration release candidate for the three independently
engineered WALK paths:

1. post-meal delayed WALK (`v0.6.0`);
2. confirmed rapid-rise WALK (`v0.6.1`);
3. fail-closed prolonged-inactivity WALK (`v0.6.2`).

The user explicitly deferred the unavailable device/instrumentation gate and asked engineering to
continue. That decision does not convert any `NOT RUN` row into `PASS`. The three source milestones
and this RC remain physically unaccepted until the user runs the consolidated checklist below on
one pinned phone/Wear/watch-face artifact set.

## Engineering boundary

This milestone may:

- align phone, Wear, and watch-face metadata at versionName `0.7.0`, versionCode `12`;
- rerun the complete local regression, lint, WFF, signature, privacy, and packaging gates;
- consolidate the previously deferred device checks into one installable RC;
- correct release/testing documentation without changing production behavior.

This milestone must not:

- change coaching eligibility, priority, thresholds, safety suppression, recommendation identity,
  expiry, session, replay, notification, or WorkManager behavior;
- activate automated stairs or add a new coaching reason;
- change Nightscout requests, current-reading selection, history persistence, Room schema,
  source isolation, export/reset behavior, or Wear Data Layer fields/version;
- add chart gestures, new History periods, short-period estimates, AI, new providers, or synthetic
  production health events;
- claim Android instrumentation, phone, watch, AOD, reconnect, notification, or battery evidence
  that was not actually run.

The previously approved `v0.6.0`–`v0.6.2` architecture and safety contracts remain authoritative.
A continuation architecture review was requested through the repository's ChatGPT Pro line before
this transition. Two verified sends failed closed with `response.possibly_stale`, and live history
confirmed that no assistant verdict was captured. This is recorded as a tooling limitation, not as
an approval. Proceeding is limited to this existing behavior-neutral integration boundary under the
user's explicit deferral instruction.

## Automated engineering gate

The RC is `Engineering: PASS` only after all of the following are freshly demonstrated against one
pinned commit:

1. all three modules report versionName `0.7.0` and versionCode `12`;
2. the full forced local build runs every model/domain/data/sync/phone/Wear unit-test target,
   compiles Android migration-test sources, runs phone/Wear/watch-face lint, assembles all three
   APKs, and exits successfully;
3. the post-meal, rapid-rise, inactivity, cross-reason priority, source/provenance, safety,
   immutable-expiry, no-resurrection, idempotent replay, session, coordinator-publication, and
   phone/Wear effective-action regression suites remain green;
4. WFF v4 source/APK validation and memory-footprint validation pass;
5. every APK signature verifies, phone and Wear certificate identities match, and the accepted
   engineering certificate continuity check passes;
6. the deterministic five-file `MetabolicCoach-v0.7.0.zip` contains exactly `phone.apk`,
   `wear.apk`, `watchface.apk`, `CHANGELOG.md`, and `INSTALL.md`;
7. the metadata/signature/ZIP/privacy verifier passes with versionCode `12`;
8. independent code/boundary review finds no new production behavior and no severity-high defect.

Android instrumentation remains `NOT RUN` unless a compatible Android runtime is actually attached.

### Recorded engineering evidence

- Transition commit: `9f891832f5435ecc1ecee81e8b444ab5483e9466`.
- Forced pipeline: `BUILD SUCCESSFUL in 8m 25s`; 333/333 actionable tasks executed.
- JUnit: 62 suites/432 executions; zero failures, errors, or skips.
- Phone, Wear, and watch-face lint: passed.
- Android migration-test source: compiled; instrumentation execution: `NOT RUN`.
- WFF v4 source and APK memory-footprint validation: passed.
- APK signatures and accepted engineering certificate continuity: passed.
- Standalone version/signature/five-file-ZIP/privacy verifier: passed.
- Independent preflight: `GO WITH CONDITIONS`; every checklist condition resolved before commit.
- Independent final review: `SHIP`; no blockers.

```text
d067383f4e91824911593540acfe0edc6e700682bdacb8dd17cc2d6acded9bbe  metabolic-coach-phone-debug.apk
0a2cf5f77bf85919a1d74a46b5ac2f0f82140e1c92218ea6c300c3ea3358f32e  metabolic-coach-wear-debug.apk
afb5b0f45123a6ee84f87e4e937a99e4eea93b4c187115eae22bef1a41ca2573  metabolic-coach-watchface-debug.apk
7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4  signing certificate
```

The final archive hash is pinned after the approved documentation-only deterministic repack. This
engineering evidence does not change any physical row below.

## Consolidated physical acceptance checklist

Record only `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN` plus non-sensitive categorical observations.
Do not record glucose values, server addresses, credentials, health screenshots, or unsanitized
logs. Do not manufacture unsafe glucose conditions or synthetic production history to force a
trigger; use naturally occurring eligible states and mark unavailable rows `NOT RUN`.

| ID | Required physical result | Status | Sanitized notes |
| --- | --- | --- | --- |
| V070-IN-01 | Install the matched `v0.7.0` phone, Wear, and watch-face APKs as in-place upgrades without clearing accepted settings, history, or planner data; verify version metadata with Bugjaeger, the package manager, or equivalent APK/package inspection. | `NOT RUN` | |
| V070-FR-01 | Today current-glucose freshness, manual refresh, stale age, source isolation, and offline recovery remain consistent with the accepted phone behavior. | `NOT RUN` | |
| V070-PM-01 | A real meal marker produces no early prompt and at most one eligible post-meal WALK prompt inside the configured window. | `NOT RUN` | |
| V070-RR-01 | A naturally occurring confirmed same-source rapid rise can produce at most one rapid-rise WALK prompt; a newer nonqualifying/source-changed context removes it. | `NOT RUN` | |
| V070-IA-01 | One naturally eligible prolonged-inactivity episode can produce at most one WALK prompt; new movement or invalid/stale activity removes it and routine refresh never renews or resurrects it. | `NOT RUN` | |
| V070-AR-01 | When opportunities overlap, exactly one canonical action is visible with priority post-meal, then rapid rise, then inactivity; automated stairs never appear. | `NOT RUN` | |
| V070-SF-01 | Missing, future, stale, low, rapidly falling, cross-source, incomplete, expired, or otherwise invalid current context leaves no actionable coached exercise prompt. | `NOT RUN` | |
| V070-NF-01 | Quiet/working hours, reminder enablement, cooldown, daily cap, active-session suppression, notification permission, expiry, and display deadlines behave coherently; Snooze hides the prompt and permits no more than the bounded still-valid/still-safe redelivery behavior. | `NOT RUN` | |
| V070-AC-01 | Start from the phone and separately from the Watch creates exactly one authenticated WALK session; delayed invalid Starts fail closed and duplicate replays remain idempotent. | `NOT RUN` | |
| V070-CP-01 | Completion, queued/rejected acknowledgements, reconnect reconciliation, and descriptive follow-up finish once without contaminating observations or creating a second session. | `NOT RUN` | |
| V070-SY-01 | Phone card, phone notification, Wear app, Wear notification, complication, and watch face converge on the same phone-authored action, expiry, and terminal result without stale or duplicate actions. | `NOT RUN` | |
| V070-RC-01 | Phone/watch restart, process death, Bluetooth interruption, temporary network loss, queued action, and reconnect recover without duplicate prompts, revived actions, stranded sessions, or a sync loop; include the fail-closed interval where Watch publication succeeds immediately before phone publication bookkeeping. | `NOT RUN` | |
| V070-UI-01 | Galaxy Watch8 round layout, touch targets, outdoor readability, font/theme behavior, and interactive/AOD transitions remain readable and unclipped without bezel input. | `NOT RUN` | |
| V070-BG-01 | Representative use shows no crash, ANR, notification storm, unexpected polling/background wake, excess heat, or obvious battery regression. | `NOT RUN` | |
| V070-PR-01 | User-visible wording remains optional observational wellness guidance and does not present a treatment instruction, medical claim, or laboratory result. | `NOT RUN` | |

Detailed source-milestone checks remain in the cumulative
[physical-test backlog](../PHYSICAL_TEST_BACKLOG.md). A failure in current glucose, safety,
source/data integrity, Wear compatibility, notification/command replay, session duplication,
export/reset, or persistent background behavior stops promotion and returns to a critical-defect
gate.

## Versioning, artifact, and stop condition

The engineering artifact is `MetabolicCoach-v0.7.0.zip`, with all three APKs at versionName
`0.7.0`, versionCode `12`. Preserve every earlier artifact and hash; never overwrite or relabel
`v0.6.2` or a failed artifact.

After automated verification and independent review, record the exact commit, APK/ZIP hashes,
certificate digest, suite/execution counts, lint/WFF/package results, and the instrumentation gap.
Then mark this document `Engineering: PASS / Physical: DEFERRED` and stop production-behavior work
at this RC boundary. Subsequent non-device milestones require their own APOS request. Automated
stairs remain production-disabled until separately reviewed and eventually verified on hardware.
