# v0.4.2 Saved Planning Milestones

## Scope

`v0.4.2` adds several saved, phone-only planning milestones to the existing Glycemic Goal
Planner. It does not change Nightscout retrieval, current-glucose freshness, Wear synchronization,
the watch face, coaching rules, notifications, or provider selection.

The feature is motivational planning support, not medical treatment guidance. The app uses
CGM-derived GMI terminology and never presents a milestone as a laboratory HbA1c result, a
prescription, or a guarantee.

## User behavior

- Save any number of active milestones with an optional neutral title, target GMI, provenance, and
  a 30-, 60-, or 90-day horizon.
- Each milestone receives a fixed target date when it is created. Changing the horizon before the
  target date requires confirmation and creates a new fixed date.
- Select exactly one milestone for the detailed remaining-window scenario and evaluation card.
- Archive or delete milestones independently. Archived rows remain visible for history and are
  excluded from active planning actions.
- Future milestones can be edited. Due and past milestones keep their target, date, and horizon
  immutable; only the title can be changed.
- The list is deterministic: active future/due dates first, active past dates newest first, then
  archived milestones by archive time. Creation time and ID break ties.

The selected milestone is a presentation choice. It is never sent to Wear, coaching, reminders,
notifications, or the watch face.

## Calculation contract

All provider records are already normalized as `GlucoseReading`. The milestone layer consumes that
model and does not import Nightscout classes.

- Before the fixed target date, the app shows the required future mean for the remaining portion of
  the original 30/60/90-day horizon, using the observed complementary history in the 90-day window.
- At or after the target date, the app evaluates the fixed 90-day window ending at that target
  date. It reports met/not met, insufficient data, source discontinuity, low-glucose safety
  suppression, or unavailable calculation.
- Coverage, source identity, long gaps, and configured low/very-low exposure limits remain safety
  gates. No evaluation auto-completes or archives a milestone.
- Target GMI is the canonical target. The app does not persist a second independent target mean.
- Calculation contract version `1` is persisted with each row so a future formula change can be
  versioned deliberately.

## Persistence and migration

Milestones are stored in the phone Room table `glycemic_planning_milestones` (schema version 8).
The selected ID and migration notice are stored in a separate Preferences DataStore. The existing
legacy singleton target is migrated once, with a stable ID and a fixed date derived from its
existing horizon. The old settings remain readable until migration succeeds; no ongoing mirrored
writes are introduced.

Personal-data export schema version 3 includes the legacy planner settings, selected milestone ID,
and deterministic milestone definitions. Erase clears the table and selection state and does not
delete source records from Nightscout, Health Connect, Samsung Health, CareSens, or xDrip.

## Automated verification

The v0.4.2 gate includes:

- domain tests for remaining-window math, temporal state, deterministic ordering, and safety
  evaluation;
- repository tests for one-time migration, fixed dates, idempotent creation, selection fallback,
  past-target edit freezing, and reset;
- JSON export tests for selected milestone and deterministic ordering;
- Room schema/migration source compilation through version 8;
- existing Nightscout, current-glucose, coaching, Wear sync, and watch-face checks unchanged.

## Physical phone acceptance gate

After the GitHub Actions artifact is verified, test only the phone planner on the accepted phone:

1. Upgrade using the pinned `MetabolicCoach-v0.4.2.zip` and verify package metadata and hashes.
2. Confirm the existing current glucose, trend, delta, timestamp, stale behavior, and manual
   refresh remain correct.
3. Open Planner and confirm the 30/60/90-day metrics are still present.
4. Create at least three milestones with different titles, target GMI values, and horizons.
5. Confirm all rows persist after process restart and that selecting one changes only the detailed
   planner card.
6. Edit a future milestone, confirm the horizon-change date confirmation, and verify the date is
   fixed afterward.
7. Confirm due/past rows cannot change target/date/horizon, while title editing remains possible.
8. Archive and delete rows; verify deterministic ordering and selection fallback.
9. Confirm migration notice behavior if upgrading from a build with a legacy singleton target.
10. Export data and verify milestone definitions are present without server URLs or credentials.
11. Confirm no change in Wear state, coaching decisions, notifications, or watch-face behavior.

Record only categorical results, artifact identity, device/build, and failure descriptions. Do not
record private glucose values, Nightscout addresses, or credentials. Stop after this acceptance
and wait for architecture review before planning the next milestone.
