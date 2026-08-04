# Physical Acceptance Checklists by Milestone

This is the index and reusable physical-test checklist for Metabolic Coach milestones. It answers
which device checks belong to each version and where the detailed evidence is recorded.

The checklist is a test plan, not a claim that a milestone passed. A milestone is physically
accepted only after the user runs the required rows against one pinned artifact set and reports the
results. Automated builds, emulator runs, and a successful install do not replace the required
physical gate.

## Status and evidence rules

Use one status for every row:

- `PASS`: the pinned artifact produced the expected result.
- `FAIL`: the test ran and the observed result differed from the expected result.
- `BLOCKED`: a prerequisite or observable acceptance criterion was unavailable.
- `NOT RUN`: no valid attempt has been made against the pinned artifact.

Do not record actual glucose values, personal Nightscout URLs, credentials, tokens, names, health
exports, or screenshots containing health data. Use categorical observations such as “value matched
the phone-authored state” and synthetic data where possible. Keep raw diagnostics outside Git.

Before every gate, record the repository commit, ZIP/APK hashes, version name/code, build variant,
signing-certificate hash, phone and watch builds, test UTC interval, and a sanitized evidence
location. A changed APK, certificate, provider setting, schema, or relevant configuration starts a
new pinned run.

## Milestone index

| Version | Physical scope | Current status | Authoritative checklist/evidence |
| --- | --- | --- | --- |
| `v0.1` | Baseline phone, Wear, watch-face installation and package contract | Historical baseline; no separate signed record was retained | This document, §v0.1 |
| `v0.2` | Phone-only Nightscout live behavior | Accepted by user 2026-08-01 | This document, §v0.2; [Testing](TESTING.md) |
| `v0.3` | Galaxy Watch8 installation, Wear Data Layer, face, touch, AOD, reconnect, stability | Accepted by user report 2026-08-02 | [V0.3 Wear checklist](V0.3_WEAR_ACCEPTANCE.md) and [sanitized record](acceptance/V0_3_PHYSICAL_ACCEPTANCE.md) |
| `v0.4.0` | Original phone planner/freshness artifact | Not accepted; stale-current defect evidence is preserved | [v0.4.1 hotfix report](V0.4_1_FRESHNESS_HOTFIX.md) |
| `v0.4.1` | Phone current-glucose freshness regression gate | Accepted by user report | [v0.4.1 hotfix report](V0.4_1_FRESHNESS_HOTFIX.md) |
| `v0.4.2` | Phone saved planning milestones and planner regressions | Accepted by user report | [v0.4.2 checklist](V0.4_2_SAVED_MILESTONES.md) |
| `v0.5.0` | Phone local history retention, backfill, export/reset | Accepted by user report 2026-08-04 | [v0.5.0 history checklist](V0_5_0_HISTORY_FOUNDATION.md) and [sanitized record](acceptance/V0_5_0_PHYSICAL_ACCEPTANCE.md) |
| `v0.5.1` | Phone trend chart and selected-period GMI | Accepted by user report 2026-08-04 | [v0.5.1 phone checklist](V0_5_1_HISTORY_EXPLORER.md) |
| `v0.6.0` | One approved coaching increment | Planned; scope must be approved before this gate is pinned | This document, §v0.6.0 (draft gate) |
| `v0.5` beta | One-week daily-use reliability and safety observation | Planned after feature gates | This document, §one-week beta |
| `v1.0` | Production daily-use release | Planned | This document, §v1.0 |

The version labels follow the repository milestone table. `v0.4.0` remains a historical failed
artifact and must never be relabeled as `v0.4.1`; future physical runs use the exact artifact named
in the run record.

## v0.1 — Infrastructure baseline

This is a retroactive baseline checklist. It is useful when rebuilding the foundation or auditing
the original package contract; it does not recreate missing historical evidence.

| ID | Expected physical result | Status | Sanitized evidence/notes |
| --- | --- | --- | --- |
| V01-IN-01 | Phone APK installs and launches without crash or ANR. | `NOT RUN` | |
| V01-IN-02 | Wear APK installs on the paired Wear OS device. | `NOT RUN` | |
| V01-IN-03 | Watch-face APK installs separately and is selectable from the system face picker. | `NOT RUN` | |
| V01-SY-01 | Phone and Wear package identities and signing certificate satisfy the Data Layer contract. | `NOT RUN` | |
| V01-SY-02 | A synthetic phone-authored state reaches the Wear app/face through the Data Layer. | `NOT RUN` | |
| V01-UI-01 | Round-screen layout is readable; touch navigation does not require bezel rotation. | `NOT RUN` | |
| V01-AO-01 | Watch face enters/leaves AOD without crash, blank content, or severe clipping. | `NOT RUN` | |
| V01-ST-01 | Phone/watch restart and a temporary disconnect recover without data corruption. | `NOT RUN` | |

## v0.2 — Nightscout phone integration

Run on the phone with a synthetic or user-approved Nightscout source. The watch is out of scope;
the provider must remain phone-owned.

| ID | Expected physical result | Status | Sanitized evidence/notes |
| --- | --- | --- | --- |
| V02-IN-01 | Install/upgrade the pinned phone APK without a destructive reset. | `PASS` | User accepted the phone gate. |
| V02-CFG-01 | Configure a Nightscout server, polling/retry settings, and HTTPS policy in the phone UI. | `PASS` | Do not record the URL. |
| V02-GLU-01 | Current glucose, trend, delta, timestamp, and update age match the phone-authored normalized reading. | `PASS` | Record only categorical match. |
| V02-GLU-02 | Manual refresh publishes the newest available reading without blocking or crashing. | `PASS` | |
| V02-OFF-01 | With the provider unavailable, cached data remains visible with an accurate stale/failure state. | `PASS` | |
| V02-REC-01 | After connectivity returns, retry succeeds and the phone returns to the newest current state. | `PASS` | |
| V02-ISO-01 | Switching configured servers does not mix readings or cache state between sources. | `PASS` | |
| V02-ST-01 | Repeated launches, rotation, backgrounding, and notification permission changes do not crash. | `PASS` | |

History charts and long-term GMI were intentionally not part of `v0.2`.

## v0.3 — Galaxy Watch8 physical acceptance

The detailed, row-level gate is [V0.3_WEAR_ACCEPTANCE.md](V0.3_WEAR_ACCEPTANCE.md). Use that
document rather than this summary when running the device test.

| ID | Expected physical result | Status | Evidence |
| --- | --- | --- | --- |
| V03-IN-01 | Phone, Wear, and watch-face APKs install and the face can be selected. | `PASS` | User-reported pass; sanitized record retained. |
| V03-SY-01 | Initial and background phone-to-Wear synchronization converges to the newest phone state. | `PASS` | User-reported pass. |
| V03-SY-02 | Current glucose, trend, delta, timestamp/update age, activity, and coach state remain consistent. | `PASS` | No values retained. |
| V03-RC-01 | Phone/watch reconnect, Bluetooth interruption, restart, and temporary network loss recover. | `PASS` | User-reported pass. |
| V03-UI-01 | Touch-only navigation, one-handed targets, round layout, outdoor readability, and font/theme behavior pass. | `PASS` | User-reported pass. |
| V03-AO-01 | AOD content, burn-in-safe placement, wake transition, and cadence pass without excess animation. | `PASS` | User-reported pass. |
| V03-ST-01 | No crash/ANR, reconnect storm, duplicate action, or unacceptable battery observation. | `PASS` | User-reported pass; exact battery values omitted. |

## v0.4.0 — Original planner/freshness artifact

This artifact is retained for audit only. It is not an accepted release because physical testing
found a stale current-glucose defect. Do not rerun it as the current build and do not overwrite its
evidence with the `v0.4.1` result.

| ID | Expected physical result | Status | Evidence |
| --- | --- | --- | --- |
| V040-FR-01 | Refresh displays the newest current glucose rather than an older historical reading. | `FAIL` | Stale-current defect; see hotfix report. |
| V040-PL-01 | Planner calculations remain provider-agnostic and phone-only. | `NOT RUN` | Superseded by the defect gate. |

## v0.4.1 — Current-glucose freshness fix

This was a phone-only corrective gate. Wear, watch-face, coaching, and notification behavior were
not changed.

| ID | Expected physical result | Status | Sanitized evidence/notes |
| --- | --- | --- | --- |
| V041-FR-01 | Manual refresh publishes the newest valid reading immediately. | `PASS` | User-reported acceptance. |
| V041-FR-02 | Older backfill or cached rows cannot regress the current-state pointer. | `PASS` | User-reported acceptance; no values retained. |
| V041-FR-03 | Offline failure retains cache while exposing accurate stale age/failure state. | `PASS` | |
| V041-PL-01 | Planner metrics and target scenarios remain unchanged by the freshness fix. | `PASS` | |
| V041-REG-01 | Existing Wear payloads, coaching decisions, notifications, and face resources remain unchanged. | `PASS` | |

## v0.4.2 — Saved planning milestones

The detailed phone gate is [V0.4_2_SAVED_MILESTONES.md](V0.4_2_SAVED_MILESTONES.md). It covers
multiple saved targets while keeping exactly one selected detail scenario.

| ID | Expected physical result | Status | Evidence |
| --- | --- | --- | --- |
| V042-PL-01 | Planner 30/60/90-day metrics and target scenarios display without a crash. | `PASS` | User-reported acceptance. |
| V042-MS-01 | At least three milestones can be created, saved, selected, edited, archived, and deleted. | `PASS` | User-reported acceptance. |
| V042-MS-02 | Milestones persist across restart; due/past milestones do not auto-complete or mutate. | `PASS` | |
| V042-PR-01 | Current glucose freshness, provider isolation, and export privacy remain intact. | `PASS` | |
| V042-REG-01 | Wear sync, coaching, notifications, and watch-face behavior remain unchanged. | `PASS` | |

## v0.5.0 — Local history foundation

Yes. The detailed checklist already exists in
[V0_5_0_HISTORY_FOUNDATION.md](V0_5_0_HISTORY_FOUNDATION.md), and it is the authoritative gate for
the current artifact. This milestone is phone-only; do not install it on the watch for acceptance.

| ID | Expected physical result | Status | Sanitized evidence/notes |
| --- | --- | --- | --- |
| V050-IN-01 | Upgrade the accepted phone build with the pinned `v0.5.0` certificate and no destructive reset. | `PASS` | User-reported pass. |
| V050-HS-01 | Normal refresh stores recent history while current glucose remains fresh and unchanged. | `PASS` | User-reported pass. |
| V050-RT-01 | Default 90-day retention does not prune data before explicit confirmation. | `PASS` | User-reported pass. |
| V050-RT-02 | One-year and keep-all policy changes require confirmation and preserve the expected rows. | `PASS` | User-reported pass. |
| V050-BF-01 | One older range downloads, shows progress/checkpoint state, and can pause/resume. | `PASS` | User-reported pass. |
| V050-BF-02 | Temporary network loss and process interruption pause safely without a network storm or current-state regression. | `PASS` | User-reported pass. |
| V050-ISO-01 | Backfill and pruning remain isolated by provider/source identity. | `PASS` | User-reported pass. |
| V050-PR-01 | Export contains local history/settings without credentials or server endpoints; erase clears local rows/checkpoints only. | `PASS` | User-reported pass. |
| V050-REG-01 | No chart, selected-period GMI, new coaching, Wear, face, notification, or quick-action behavior appears. | `PASS` | User-reported pass. |
| V050-ST-01 | Repeated refresh, background/foreground, low network, and settings changes do not crash or create unacceptable battery/network behavior. | `PASS` | User-reported pass. |

The user reported this phone gate passed on 2026-08-04. The required v0.5.1 APOS review subsequently
returned `GO WITH CONDITIONS`.

## v0.5.1 — Phone trend chart and selected-period GMI

The executable, privacy-safe checklist is maintained in
[V0_5_1_HISTORY_EXPLORER.md](V0_5_1_HISTORY_EXPLORER.md). It covers the exact 24h/7/14/30/90-day
and custom range contract, GMI eligibility, local-only reads, stale-result races, frozen regressions,
and the post-test stop condition. The user reported this gate passed on 2026-08-04.

## v0.6.0 — One coaching increment (draft gate)

The exact coaching increment must be approved in architecture review before the rows below are
made feature-specific. The gate must remain narrow; it cannot silently include a chart, new
provider, AI recommendation, or unrelated UI redesign.

| ID | Expected physical result | Status |
| --- | --- | --- |
| V060-TR-01 | The approved trigger is emitted only for the declared glucose/activity/time conditions. | `NOT RUN` |
| V060-SF-01 | Missing, future, stale, low, or rapidly falling readings suppress the intervention according to the shared safety policy. | `NOT RUN` |
| V060-AC-01 | The one-tap action starts the declared activity/session exactly once and reports queued/rejected/completed outcomes. | `NOT RUN` |
| V060-NF-01 | Quiet hours, working hours, cooldown, snooze, daily limits, and notification permission behavior are respected. | `NOT RUN` |
| V060-SY-01 | If Wear is in scope for the approved increment, phone-authored state and action results converge without duplicate or stale actions. | `NOT RUN` |
| V060-PR-01 | User-visible wording remains observational wellness guidance and makes no medical or treatment claim. | `NOT RUN` |
| V060-ST-01 | Reboot, disconnect, process death, and repeated notifications do not crash, duplicate, or create a battery/network loop. | `NOT RUN` |

## One-week personal beta (`v0.5` roadmap milestone)

Run only after the feature milestones above are individually accepted. Use a predeclared daily
script and record categorical observations; do not export or paste personal health data into the
repository or chat.

| ID | Expected physical result | Status |
| --- | --- | --- |
| BETA-01 | Seven days of normal use complete without a severity-high crash, ANR, data-loss event, or unrecoverable sync failure. | `NOT RUN` |
| BETA-02 | Daily current-glucose freshness, stale indication, and Nightscout recovery remain reliable across normal network changes. | `NOT RUN` |
| BETA-03 | Phone/watch reconnect, notification delivery, quick actions, and session completion remain deterministic. | `NOT RUN` |
| BETA-04 | Battery behavior is recorded against a comparable baseline with AOD and normal notification settings. | `NOT RUN` |
| BETA-05 | History retention/backfill, chart/GMI (if accepted), and milestone persistence do not silently change or lose local data. | `NOT RUN` |
| BETA-06 | Privacy review of exports, logs, screenshots, notifications, and Wear payloads finds no private endpoint, credential, or unintended health-data disclosure. | `NOT RUN` |
| BETA-07 | The user records observations and failure categories only; no medical claim or treatment decision is inferred from the beta. | `NOT RUN` |

## v1.0 — Stable daily-use release

This is the final release gate, not a substitute for milestone-specific tests.

| ID | Expected physical result | Status |
| --- | --- | --- |
| V10-RL-01 | Production-signed phone, Wear, and watch-face artifacts install/upgrade with the documented package and certificate identities. | `NOT RUN` |
| V10-RL-02 | Complete CareSens app → xDrip+ → Nightscout → phone → Wear/face route is current, recoverable, and privacy-reviewed without direct CareSens communication. | `NOT RUN` |
| V10-RL-03 | Galaxy Watch8 touch, round layout, AOD, complications, notifications, reconnect, and battery checks pass on the release artifact. | `NOT RUN` |
| V10-RL-04 | History, chart/GMI, planner, and approved coaching behavior pass their individual regression gates. | `NOT RUN` |
| V10-RL-05 | Fresh install, upgrade, process death, reboot, force-stop, no network, battery saver, low storage, timezone, locale, and permission changes recover safely. | `NOT RUN` |
| V10-RL-06 | Release ZIP/APK hashes, changelog, install guide, privacy/safety wording, and rollback/data-migration evidence are archived. | `NOT RUN` |
| V10-RL-07 | No known severity-high defect remains; production signing, instrumentation, store/privacy, and device evidence are complete. | `NOT RUN` |

## Run record template

Copy this block into a private test record for each physical run. Keep health values and identifying
server information out of the record.

```text
Milestone/version:
Gate state: NOT RUN | IN PROGRESS | ACCEPTED | BLOCKED
Repository / branch / commit:
Artifact ZIP / APK version / variant / SHA-256:
Signing certificate SHA-256:
Phone model / Android build:
Watch model / Wear OS build:
Test start UTC / end UTC:
Required PASS / FAIL / BLOCKED / NOT RUN counts:
Sanitized evidence location:
Failures or blockers (categorical only):
Tester report:
Reviewer decision:
```

When the user reports physical results, stop implementation. Review the evidence and decide whether
the milestone is accepted, requires a narrowly scoped defect fix, or remains blocked before planning
the next version.
