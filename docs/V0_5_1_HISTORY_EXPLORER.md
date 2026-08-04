# v0.5.1 History Explorer and Selected-Period GMI

## Status

`v0.5.1` is a phone-only physical-acceptance candidate. It must not unlock `v0.6.0` or any new
coaching behavior until the checklist below is completed and reviewed.

The required APOS architecture review used the existing repository-scoped `main` conversation and
returned **GO WITH CONDITIONS** on 2026-08-04. The implementation follows those conditions: local
exact-source reads only, immutable UTC query ranges, deterministic disconnected gaps, bounded
render data, descriptive GMI safety wording, and stale-request publication guards.

## Scope

- A fourth phone destination: `Today | History | Planner | Settings`.
- Fixed read-only windows: 24 hours, 7 days, 14 days, 30 days, and 90 days.
- Custom periods of 14–90 completed local calendar days. The selected local-day boundaries are
  converted once to a UTC half-open interval, including daylight-saving transitions.
- A Compose Canvas glucose trend. Missing periods remain disconnected. Rendering is bounded to
  approximately 400 buckets and preserves first/last, minimum/maximum, time-weighted mean, and
  valid duration.
- Selected-period GMI for 14-day, 30-day, 90-day, and eligible custom periods. It reuses the
  planner's accepted time-weighted mean, coverage, gap, range, and low-glucose calculations.
- Only the last fixed period is stored as a presentation preference. Custom dates remain
  session-only.

## Frozen boundaries

- No provider refresh, Nightscout request, range backfill, or current-reading mutation is triggered
  by opening or navigating History.
- History reads only `GlucoseRepository.readingsBetweenExactSource(...)` for the source captured at
  request start.
- A newer range, a source change, or leaving History prevents an older in-flight result from being
  published.
- There is no Room migration and no chart dependency.
- Wear payloads, Wear UI, watch-face resources, coaching decisions, notifications, and quick
  actions are unchanged.
- GMI is descriptive wellness information. It is not laboratory HbA1c, a treatment target,
  medication guidance, or a coaching input.

## Phone physical acceptance checklist

Record only `PASS`, `FAIL`, or `NOT TESTED` plus non-sensitive observations. Do not record glucose
values, server addresses, credentials, or screenshots containing private health data.

| ID | Check | Result |
| --- | --- | --- |
| V051-IN-01 | Install the `v0.5.1` phone APK over the accepted `v0.5.0` build without clearing app data. | PASS |
| V051-IN-02 | Confirm the app reports version `0.5.1` and existing Nightscout/history/planner settings remain present. | NOT TESTED — settings remain present; installed-version display has not yet been located. |
| V051-NAV-01 | Confirm the bottom order is Today, History, Planner, Settings and all four destinations remain usable. | PASS |
| V051-HIS-01 | Open History and confirm the default/last fixed period loads from local storage without a manual backfill action. | PASS — interactive zoom/pan was requested as future scope. |
| V051-HIS-02 | Check 24h, 7d, 14d, 30d, and 90d selections; changes must not crash, freeze, or show a previous selection after the new one finishes. | PASS — 6-hour and 12-hour presets were requested as future scope. |
| V051-HIS-03 | Confirm visible missing-data periods are disconnected and coverage/gap text is understandable. | PASS |
| V051-HIS-04 | Rapidly change periods, leave History during loading, and return; an older result must never replace the current selection. | PASS |
| V051-HIS-05 | If more than one configured source is available, switch the active source and confirm data from the previous source is not shown. Otherwise record `NOT TESTED`. | NOT TESTED — only one source is in use. |
| V051-CUS-01 | Apply a valid 14–90 completed-day custom period and confirm the displayed dates match the selection. | PASS |
| V051-CUS-02 | Confirm fewer than 14 days, more than 90 days, a reversed range, and a range including today are rejected without a crash. | PASS |
| V051-CUS-03 | Restart the app: the last fixed preset may persist, but custom draft dates/result must not be treated as a saved period. | PASS |
| V051-GMI-01 | Confirm 24h and 7d never display a numeric selected-period GMI. | PASS — shorter-period estimates were requested as future scope. |
| V051-GMI-02 | Confirm eligible 14d/30d/90d/custom periods show GMI only when coverage is sufficient, with mean, coverage, range metrics, gaps, dates, units, and the laboratory-HbA1c disclaimer. | PASS — estimates with insufficient coverage were requested as future scope. |
| V051-GMI-03 | Confirm insufficient/no-data periods show a reason rather than a numeric GMI. | PASS — displaying a qualified estimate was requested as future scope. |
| V051-GMI-04 | If a low-glucose warning is present, confirm the wording is neutral and does not praise a lower GMI. Otherwise record `NOT TESTED`. | NOT TESTED — the warning was not observed. |
| V051-REG-01 | Confirm Today current glucose remains fresh and Refresh now still behaves as in the accepted build. | PASS |
| V051-REG-02 | Confirm Settings history retention/backfill controls and Planner saved milestones still behave as accepted. | PASS |
| V051-REG-03 | Confirm opening History does not itself show backfill/provider activity or alter the current reading. | PASS |
| V051-WEAR-01 | Confirm the connected watch and watch face continue to show current synchronized state with no new History/GMI surface. | PASS |
| V051-STB-01 | Exercise History repeatedly and confirm no crash, ANR, visibly unbounded rendering delay, or unexpected battery/heat behavior. | NOT TESTED — physical stability exercise is still pending. |

## Physical acceptance progress

Partial results were reported on 2026-08-04. No v0.5.1 failure has been reported. Acceptance remains
open until the installed version and stability checks are completed. Conditional single-source and
low-warning checks may remain `NOT TESTED` with the observations above.

The following requests are recorded for the next architecture discussion and are not interpreted as
v0.5.1 defects: interactive chart zoom/pan, 6-hour and 12-hour windows, and qualified estimates for
short or incomplete periods. APOS review is required before any of them is implemented.

## Stop condition

After reporting this checklist, stop for architecture/acceptance review. Do not implement coaching,
chart interaction, analytics, a new provider, or another milestone until `v0.5.1` is accepted.
