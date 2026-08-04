# v0.5.0 Phone Physical Acceptance Record

## Status

`USER_REPORTED_PASS`

On 2026-08-04, the user reported completing the v0.5.0 phone acceptance checklist and that the
build worked very well. No failed or blocked checklist item was reported. This record contains no
glucose values, Nightscout URLs, credentials, exports, screenshots, or device identifiers.

The reported installed version was `v0.5.0`. The exact installed APK/ZIP SHA-256 was not re-supplied
with the physical-test report, so this record does not claim an independent hash comparison on the
device. The successful GitHub Actions engineering artifact and its hashes remain the reproducible
build evidence documented in [Testing](../TESTING.md).

## Sanitized acceptance results

| Acceptance area | Result | Sanitized note |
| --- | --- | --- |
| Same-certificate phone install/upgrade | `PASS` | User reported checklist completion. |
| Current glucose freshness, age, trend, and delta regression | `PASS` | No glucose values retained. |
| Stored row count and local oldest/newest date status | `PASS` | No dates or counts retained. |
| Confirmation-gated 90-day, 1-year, and keep-all retention | `PASS` | No local history copied into the record. |
| One-range older-history backfill and checkpoint progress | `PASS` | No provider endpoint retained. |
| Temporary network failure, retry, and process-restart recovery | `PASS` | No raw diagnostics retained. |
| Nightscout source isolation | `PASS` | No server identity retained. |
| Privacy-safe export and confirmation-gated local erase | `PASS` | No export retained. |
| No chart/GMI/coaching/Wear/watch-face/notification scope leak | `PASS` | User reported no unexpected behavior. |
| Crash, ANR, battery, and network behavior | `PASS` | User reported the build worked very well. |

## Gate decision

The v0.5.0 physical phone gate is accepted by user report. This closes the local-history foundation
milestone and authorizes the required APOS architecture review for v0.5.1.

It does not by itself authorize implementation beyond the architecture-reviewed v0.5.1 scope, make
Metabolic Coach a medical device, or replace longer battery/reliability beta testing.

