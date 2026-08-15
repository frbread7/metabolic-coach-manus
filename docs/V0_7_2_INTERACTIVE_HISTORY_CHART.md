# v0.7.2 Interactive local History chart

Status: `Engineering: PASS / Physical: DEFERRED`.

`v0.7.2` is a phone-only History usability increment. It adds horizontal pinch zoom, horizontal
drag pan, visible Zoom in/Zoom out/Reset controls, and an explicit visible-window label to the
existing exact-source local chart. The selected History period and its GMI result remain separate
from the transient chart viewport.

The repository-scoped APOS review returned `GO WITH CONDITIONS`. This milestone must stop if it
requires any provider/current-reading, network refresh, backfill, retention, Room schema,
export/reset, GMI policy/formula, coaching, notification, Wear protocol, or watch-face behavior
change.

## Architecture contract

- `selectedRange` is the full immutable History period. It continues to define selected-period GMI.
- `requestedViewport` is transient gesture/control intent within the selected range.
- `RenderedHistoryViewport` atomically binds source identity, selected range, rendered viewport,
  and rendered chart. A request never relabels or geometrically magnifies the prior chart.
- Every changed viewport is debounced for 200 ms and then reloads canonical rows through
  `GlucoseRepository.readingsBetweenExactSource(...)` only.
- The query is the half-open interval `[viewport start - 20 minutes, viewport end)`. The inclusive
  Room DAO endpoint is therefore passed `endExclusive - 1`.
- Visible chart preparation ignores the parent preset's fixed aggregation. It renders raw points at
  or below the 400-bucket cap and uses the existing deterministic adaptive/extrema-preserving path
  only above that cap. Missing periods remain disconnected.
- A matching success replaces the rendered viewport/chart bundle atomically. Cancellation plus a
  source/range/viewport/generation gate prevents delayed results from publishing. Failure keeps the
  last rendered chart and accurate error state.
- Pinch zoom is horizontal-time-only around the gesture centroid. One-finger horizontal drag pans;
  clearly vertical movement remains available to the parent page scroll.
- The minimum visible duration is 30 elapsed minutes; the maximum is the full selected period.
  Zoom in, Zoom out, and Reset are visible accessibility alternatives to gestures.
- Viewport work never invokes GMI calculation and never clears, recalculates, or rewrites the
  selected-period GMI result.

## Automated gate

Engineering completion requires:

1. pure viewport math tests for focal zoom, pan direction/bounds, 30-minute/full-range clamps,
   reset, daylight-saving independence, invalid input, and horizontal-intent arbitration;
2. exact-source loader tests for the 20-minute lead, half-open endpoint, defensive source filtering,
   viewport-specific detail, and absence of provider/refresh calls;
3. request-state tests for the 200 ms debounce, stale generation/source/range/viewport rejection,
   atomic requested/rendered publication, failure retention, and unchanged GMI;
4. Compose UI test-source compilation for accessible controls and gesture intent. Instrumentation
   execution remains `NOT RUN` without an attached Android runtime;
5. the complete forced regression, lint, APK, WFF, signature, certificate, privacy, and deterministic
   five-file ZIP pipeline;
6. independent boundary/code review with no unresolved high-severity defect.

### Recorded engineering evidence

- Pinned feature/build commit: `501c11314d7b55549989b3a3fed4465560e8ebc0` (`build: pin verified WFF validation release assets`).
- Focused phone unit-test gate: `:phone:testDebugUnitTest` passed after the Android SDK Platform 36 and JDK 17 environment was configured.
- Forced local pipeline: `./scripts/build-apks.sh` passed with 342 actionable tasks executed.
- JUnit evidence: 65 XML suites, 450 test cases, and zero failures, errors, or skipped tests.
- Lint, Room Android-test-source compilation, WFF v4 source validation, WFF APK resource-only and memory-footprint validation, APK assembly, v2 signature verification, and phone/Wear signing-certificate continuity all passed.
- Android instrumentation execution: `NOT RUN` (no attached Android runtime; deferred and not equivalent to pass).
- Phone/Wear signing certificate SHA-256: `2af021e5729d9386961ced398f4d2632baa842f9ae632ff2437af52838e88f61`.
- Debug artifact SHA-256 values:

```text
f91ec999ce74d9ac79e88c2fb79f5acbd882cbe4c50429316f0fac9744759282  metabolic-coach-phone-debug.apk
28b0d922d60dfd28db79bdaea79755fa46a01dfa07837e799ecdaf024d7927c0  metabolic-coach-wear-debug.apk
3635dc34d9ffbb2682fee941ad5ab240f938f60a40425b84f11d00ff4951a1fd  metabolic-coach-watchface-debug.apk
6637cc563a93ec2f2e611684e981755c7e57d40ef3df6486fdb73c453710117f  MetabolicCoach-v0.7.2.zip
```

## Deferred phone acceptance checklist

Record only `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN` plus privacy-safe categorical notes. Do not
record glucose values, Nightscout addresses, credentials, or health screenshots.

| ID | Required physical result | Status | Sanitized notes |
| --- | --- | --- | --- |
| V072-IN-01 | Install the matched `v0.7.2` phone APK over the prior engineering build without clearing settings, history, or planner data; verify versionName `0.7.2` and versionCode `14` through package/APK inspection. | `NOT RUN` | |
| V072-UI-01 | History visibly separates the selected period from the visible chart window and provides usable Zoom out, Zoom in, and Reset controls. | `NOT RUN` | |
| V072-GE-01 | Two-finger pinch zooms horizontally around the fingers, respects the 30-minute/full-period limits, and never stretches old chart pixels while new detail is loading. | `NOT RUN` | |
| V072-GE-02 | One-finger horizontal drag pans within the selected period; vertical swipes that begin over the chart still scroll the page. | `NOT RUN` | |
| V072-DA-01 | Zoomed windows show locally stored detail appropriate to the visible interval, preserve extrema, and leave missing periods disconnected. | `NOT RUN` | |
| V072-ST-01 | Rapid zoom/pan/control input publishes only the final requested window; failure/retry never relabels an older rendered chart as the new window. | `NOT RUN` | |
| V072-SO-01 | If an active source change is available, no chart or GMI from the previous source remains visible. Otherwise record `NOT RUN`. | `NOT RUN` | |
| V072-GM-01 | Zoom, pan, Reset, and chart retry do not change the selected-period GMI result, qualifier, coverage, dates, or disclaimer. | `NOT RUN` | |
| V072-REG-01 | Today current glucose/manual refresh, Settings retention/backfill, Planner, coaching, notifications, export/reset, Wear, and watch face behave as before. | `NOT RUN` | |
| V072-STB-01 | Repeated interaction causes no crash, ANR, excessive heat, obvious battery regression, or unexpected provider/network/backfill activity. | `NOT RUN` | |

The user has deferred this device gate. Every row remains `NOT RUN`; that is not a waiver and is
never physical acceptance.

## Version, artifact, and stop condition

Phone, Wear, and watch face use versionName `0.7.2` and versionCode `14` for the deterministic
package contract; production behavior changes only in the phone History screen. The expected archive
is `MetabolicCoach-v0.7.2.zip`. It must not overwrite or relabel any earlier artifact.

The automated local gate is complete. The physical checklist remains deferred and must not be
represented as device acceptance. Request a new APOS review before another feature or milestone
transition.
