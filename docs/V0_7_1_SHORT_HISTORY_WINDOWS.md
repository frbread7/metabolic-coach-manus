# v0.7.1 Six- and twelve-hour local History windows

Status: `Engineering: PASS / Physical: DEFERRED`.

`v0.7.1` is a phone-only History usability increment. It adds fixed **6h** and **12h**
selections to the accepted local History Explorer and routes them through the same exact-source,
read-only Room path as the existing 24h/7d/14d/30d/90d/custom periods.

The APOS architecture review returned `GO` with no blocker. Its condition is strict: this milestone
must remain local-history/UI-state work. If implementation requires a provider or current-reading
change, automatic backfill, retention or Room-schema change, chart gestures or new aggregation
semantics, GMI policy/formula/wording change, coaching, notifications, Wear, or watch-face behavior,
the milestone stops for a new review.

## Architecture contract

- Each selection captures one evaluation instant and resolves an immutable UTC half-open interval:
  - 6h: `[T - 6 hours, T)`;
  - 12h: `[T - 12 hours, T)`.
- Durations are elapsed time, not local-calendar fractions. Daylight-saving and time-zone changes
  do not shorten or lengthen either interval.
- The active source identity is captured with the request. Only
  `GlucoseRepository.readingsBetweenExactSource(...)` may load rows.
- The existing request-generation gate prevents late ranges, old sources, and hidden-screen work
  from publishing.
- The existing raw chart path, deterministic ordering/de-duplication, disconnected gaps, and
  defensive 400-bucket cap remain unchanged. There is no zoom, pan, crosshair, viewport state, or
  chart dependency in this milestone.
- Six- and twelve-hour periods remain ineligible for numeric selected-period GMI. No short-period
  estimate is computed, displayed, or stored.
- The last fixed preset continues to use the existing DataStore string preference. Recognized new
  and old enum names restore; custom, missing, blank, unknown, or malformed values use the accepted
  24h fallback. There is no Room migration.
- Wear and watch-face APK metadata is aligned for deterministic packaging only. Their production
  behavior is unchanged and is not newly physically validated.

## Automated gate

Engineering completion requires:

1. focused range-resolution tests for exact 6h/12h elapsed durations across daylight-saving
   boundaries;
2. preference parsing/restoration tests for new, existing, malformed, missing, and custom values;
3. exact-source local-only loader tests proving no refresh/provider call for 6h/12h;
4. stale-result tests for rapid 6h → 12h → 24h selection and source changes;
5. negative tests proving 6h/12h cannot publish a numeric selected-period GMI;
6. the complete forced regression, lint, APK, WFF, signature, certificate, privacy, and deterministic
   five-file ZIP pipeline;
7. an independent boundary/code review with no unresolved severity-high defect.

Android instrumentation remains `NOT RUN` unless it actually executes on an attached Android
runtime. Automated evidence cannot promote physical status.

### Recorded engineering evidence

- Pinned feature/build commit: `bfe1f3c4d002edd641ff895933b43d8032080b3d`.
- Focused gate: 148 tasks; affected domain/data/phone tests and data/phone lint passed.
- Independent preflight: no blocker/high issue; `GO WITH CONDITIONS` limited the remaining work to
  the forced milestone pipeline and evidence completion.
- Forced pipeline: `BUILD SUCCESSFUL in 9m 3s`; all 333 actionable tasks executed.
- JUnit: 64 suites/442 executions; zero failures, errors, or skips.
- Phone, Wear, and watch-face lint: passed.
- Android migration-test source: compiled; instrumentation execution: `NOT RUN`.
- WFF v4 source and watch-face APK memory-footprint validation: passed.
- All three APK signatures: v2 verified, one signer; accepted engineering-certificate continuity
  and phone/Wear identity equality passed.
- Standalone version/signature/five-file-ZIP/privacy/credential verifier: passed for `0.7.1`/`13`.

```text
f1eb8539cd0fbe3316ca3b088155fe8a4f8cd9b77936ab711213e7ad3b91fbf9  metabolic-coach-phone-debug.apk
351d1f956a0466d3937b83ee80a5d1ba69b1fbec9d3e602a40929300ceffad7f  metabolic-coach-wear-debug.apk
e61511087c304f844fbfaf8af74d84cbe374b8ed5db01cb0f59f052ef488087b  metabolic-coach-watchface-debug.apk
7978094b10c81a65669d7cc077d15f350b37312d2c04abd73c6667da26c5fad4  signing certificate
```

The independent final review returned `SHIP`. The approved evidence-only deterministic repack
preserved all three APK hashes, passed the standalone verifier again, and produced:

```text
482914936d44f7d4cb11544d2d5159aa1bee03e61e6d6c1fa3bb6d792ae893a1  MetabolicCoach-v0.7.1.zip
```

This did not alter any APK byte or physical-test status. The predecessor
`MetabolicCoach-v0.7.0.zip` remains preserved at SHA-256
`e8165936f92bfe1b7c0d710d11dbc6c73cd366d8a375b962a5cf1729c8fd0af4`.

## Deferred phone acceptance checklist

Record only `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN` plus privacy-safe categorical notes. Do not
record glucose values, Nightscout addresses, credentials, or health screenshots.

| ID | Required physical result | Status | Sanitized notes |
| --- | --- | --- | --- |
| V071-IN-01 | Install the matched `v0.7.1` phone APK over the prior engineering build without clearing settings, history, or planner data; confirm versionName `0.7.1` and versionCode `13` through package/APK inspection. | `NOT RUN` | |
| V071-HIS-01 | History displays 6h and 12h before the existing 24h/7d/14d/30d/90d/custom choices. | `NOT RUN` | |
| V071-HIS-02 | Selecting 6h and 12h shows the matching local window, retains disconnected gaps, and remains readable/responsive. | `NOT RUN` | |
| V071-HIS-03 | Rapidly tap 6h → 12h → 24h; only the final selection appears after loading and no stale result replaces it. | `NOT RUN` | |
| V071-HIS-04 | Restart the phone app after selecting 6h and separately 12h; the last fixed period restores without a visible refresh/backfill operation. | `NOT RUN` | |
| V071-GMI-01 | Six- and twelve-hour selections show the existing insufficient-duration GMI state and never a numeric selected-period GMI. | `NOT RUN` | |
| V071-REG-01 | Today current glucose/manual refresh, Settings retention/backfill, Planner milestones, coaching, notifications, and export/reset behave as before. | `NOT RUN` | |
| V071-WEAR-01 | The connected Wear app and watch face retain the prior synchronized behavior; no History surface or new action appears. | `NOT RUN` | |
| V071-ST-01 | Repeated History use shows no crash, ANR, abnormal heat, obvious performance regression, or unexpected network/backfill activity. | `NOT RUN` | |

The user has deferred this device gate. Every row remains `NOT RUN`; that is not a waiver or a
physical acceptance result.

## Version, artifact, and stop condition

The engineering candidate uses versionName `0.7.1` and versionCode `13` across phone, Wear, and
watch face so the deterministic package contract remains aligned. The expected archive is
`MetabolicCoach-v0.7.1.zip`; it must not overwrite or relabel `v0.7.0` or any earlier artifact.

After the automated pipeline and independent review pass, record the pinned commit, suite/execution
counts, APK/certificate/ZIP hashes, and instrumentation gap here. Close only this milestone as
`Engineering: PASS / Physical: DEFERRED`, then request a new APOS review before the next feature or
milestone transition.
