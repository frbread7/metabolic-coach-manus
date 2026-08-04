# User guide

## Before you use Metabolic Coach

Metabolic Coach provides wellness prompts based on data supplied by other applications. It is not a
medical device and does not provide glucose alarms, diagnosis, treatment, insulin guidance, or
emergency advice.

Keep the official CGM application and its alerts enabled. Confirm a glucose value in the official
source before acting when the reading is unexpected, stale, low, high, or inconsistent with how you
feel. Follow your clinician's advice and personal care plan.

This engineering-alpha build is intended for development and supervised testing, not daily
safety-critical reliance.

## Requirements

- Android phone paired to a Galaxy Watch8 or other Wear OS 6 watch;
- Metabolic Coach phone, Wear, and watch-face packages installed;
- Google Play services on both phone and watch;
- a reachable Nightscout server that contains current glucose from the existing CGM pipeline;
- health and notification permissions you choose to grant.

Version 1 expects `CareSens Air → CareSens Air app → xDrip+ → Nightscout`. Metabolic Coach does not
connect directly to CareSens or receive xDrip broadcasts. Verify CareSens device/app compatibility
in your country using the official
[CareSens compatibility page](https://caresensair.com/en_US/content/compatibility/).

## Install a development build

Generate development APKs from the current checkout with `./scripts/build-apks.sh`. On a successful
fresh run they are copied to:

```text
artifacts/metabolic-coach-phone-debug.apk
artifacts/metabolic-coach-wear-debug.apk
artifacts/metabolic-coach-watchface-debug.apk
```

Install the phone APK on the phone. Install the Wear and watch-face APKs on the watch. All debug
packages should come from the same trusted checkout/build. Do not install APKs received from an
unverified source. Current debug hashes are recorded in
[Testing](TESTING.md#current-local-verification); regenerate them after every later source/build
change.

For a distributed release, follow the store/install instructions supplied with that signed build
rather than sideloading.

## First-time setup

### 1. Prepare the sources

For Nightscout glucose:

1. Confirm the normal CareSens app → xDrip+ → Nightscout pipeline is updating.
2. Open Nightscout in a trusted browser and verify a recent reading and trend are present.
3. Record only the base URL, such as `https://example.fly.dev`. Do not append `/api/v1`, a query
   string, username, password, token, or `API_SECRET`.
4. Use HTTPS for any internet-hosted or daily-use server.

Version 1 supports public Nightscout endpoints. Authenticated-server credential setup is not yet
implemented. Do not weaken a private server's security merely to connect this alpha.

For Health Connect activity, confirm Health Connect is available and that steps, floors, heart
rate, exercise, and active calories are present if you want those fields. Health Connect is not the
Version 1 glucose source.

### 2. Open the phone app

1. Tap **Connect Health Connect** if you want activity data.
2. Review and grant only the desired activity read permissions.
3. Open **Settings → Nightscout glucose**.
4. Enter a server name and base URL, keep **Require HTTPS** enabled, and tap **Use this server**.
5. Optionally configure another named server. Exactly one server is active; there is no automatic
   failover.
6. Review polling interval, connection timeout, retry interval, and retry-attempt settings.
7. Tap **Enable coaching notifications** if you want prompts.
8. Review the user-facing threshold and time-window settings; defaults are not medical
   recommendations.
9. Tap **Save settings**, return to **Today**, and tap **Refresh**.

If provider status does not say it is ready, resolve that status before relying on watch data.
If the active server stops responding, the app retains cached data and displays its age; it does not
switch to another configured server. Select another server only as an intentional source change.
Disabling **Require HTTPS** allows cleartext HTTP for a local/test server and can expose glucose on
the network.

### 3. Open the Wear app

1. Launch Metabolic Coach on the watch.
2. Tap **Enable reminders** if watch prompts are desired.
3. Wait for the phone's current state.
4. Confirm glucose value, trend, age, steps, and floors match the phone/source closely enough for
   the test.

### 4. Select the watch face

1. Long-press the current watch face using the Wear OS system interaction.
2. Add/select **Metabolic Coach**.
3. Choose an accent option if offered.
4. Confirm glucose and activity complications populate.

Install and open the Wear app before selecting the face. The face is resource-only and obtains
dynamic data from complication providers in the Wear app.

## Daily use

### Phone Today screen

- **Current glucose** shows value, trend, delta when available, and minutes since measurement.
- **Recommendation** shows the current action or a safety/information state.
- **Mark meal** starts the configurable post-meal timing window.
- **Refresh** requests one-off phone refresh and watch publication.
- **Today** summarizes stable readings within the configured range, completed walk/stair sessions,
  steps, floors, and Health Connect exercise-session count/total duration for the local day.
- **Personal observations** appear only after their applicable sample and comparison gates pass.

Meal detection is manual in the current build. Mark the meal close to its actual time.

Activity-effect summaries use completed sessions with finalized, distinct baseline/follow-up
readings, valid timestamps, the same exact source, and a low-glucose threshold captured at start.
Follow-ups below that threshold and older rows without that safety provenance are excluded. Phase 3
timing observations are
prospective-only: the app does not infer missing trigger or recommendation details for older or
manual sessions. A coached timing sample must retain the exact trigger, recommendation/version,
activity type and duration/floor dose, baseline reading/source/rate/threshold, and planned/actual
follow-up provenance captured during the session lifecycle.

Generic and post-meal timing bucket widths are configurable; their conservative defaults are 5 and
15 minutes. Each bucket needs at least the greater of the configured overall observation minimum
and timing-bucket minimum (default eight). The comparable-bucket minimum is configurable (default
two). Follow-up-delay matching width and baseline glucose band are configurable too. A result
appears only for a unique lowest observed median with strict quartile separation from every
comparison bucket.

The analyzer excludes incomplete or mixed-source samples, invalid timing, reused readings,
overlapping recorded sessions, intervening recorded meals, unsafe below-threshold follow-ups,
unmatched post-meal markers, and duplicate actions from one trigger. Medication, unrecorded meals or
activity, adherence, and the user's choice of which sessions to complete can still affect the
result. Observations never change settings, reminders, thresholds, or recommendation timing
automatically, and must not be read as causal, medical, best, or ideal timing guidance.

### Glycemic Goal Planner

Open **Planner** on the phone to review CGM-derived rolling metrics for 30, 60, and 90 days. The
planner also evaluates a 14-day recent safety baseline for goal scenarios. It shows mean glucose,
the GMI estimate, time in range, time below range, very-low
exposure, covered-time percentage, and the largest unfilled gap. A target is optional. When you
enter one, record whether it is user-entered or clinician-agreed and select a fixed 30-, 60-, or
90-day horizon. The scenario answers only: “What future mean glucose would be mathematically
consistent with this target if the selected window were completed?”

For 30 days, the calculation uses the preceding 60 days; for 60 days, it uses the preceding 30
days; for 90 days, it uses the full future window. It is a planning scenario, not a prediction,
medical recommendation, medication instruction, or laboratory HbA1c result. The app may show
**insufficient coverage**, **source discontinuity**, or **suppressed for low-glucose risk** instead
of a number. Keep the official CGM app and clinician plan authoritative.

#### Saved planning milestones

Use **Saved planning milestones** to keep several personal targets without changing the active
coach. Each row can have an optional title, target GMI, provenance, and a fixed 30-, 60-, or
90-day horizon. Select exactly one row to see its detailed remaining-window scenario. The list
keeps future/due milestones first, then past and archived history in deterministic order.

Future milestones can be edited. Once a milestone is due or past, its target, date, and horizon are
locked; only its title can be changed. Archive a row to retain it as history or delete it to remove
that saved intention. Milestones do not change glucose retrieval, current readings, Wear state,
coaching decisions, reminders, notifications, or the watch face.

### Local glucose history (v0.5.0)

The phone stores normalized Nightscout rows locally after refresh. Open **Settings → Local glucose
history** to inspect the stored row count and local date range. Choose **90 days** (the default),
**1 year**, or **Keep all downloaded**. Saving a different choice only stages it; press **Confirm
and apply policy** before any retention pruning can occur. The app never prunes records silently,
and pruning is isolated per source while preserving each source's newest record.

After confirming 1 year or Keep all downloaded, press **Download one older range** to request one
bounded 90-day range and repeat for another range. The operation is resumable and reports paused or
failed state after interruption/network loss. It does not alter the current glucose state or watch.
### History Explorer (v0.5.1)

Open **History** to read glucose already stored on this phone. Choose 24 hours, 7 days, 14 days,
30 days, or 90 days. **Custom** accepts 14–90 completed local calendar days and must end before
today. Missing periods are shown as disconnected segments; coverage and largest-gap labels explain
how complete the period is.

Numeric selected-period GMI is available only for eligible periods of at least 14 days with
sufficient coverage. The screen also shows time-weighted mean, time-in-range/below-range metrics,
missing duration, and safety qualifiers. This is a CGM-derived estimate, not a laboratory HbA1c
measurement, treatment instruction, or medication target.

Opening History does not download data. Use the existing Settings history controls when older data
must be downloaded. The last fixed period is remembered; custom dates are not a saved goal.

### Wear app

Use touch scrolling and large buttons:

- **Start N-min walk** starts the configured local walk timer and sends the action to the phone.
  The countdown remains visible on Coach/Home and the session screen; at zero the watch displays
  **Time complete** and emits one haptic, but you still tap **Complete** to record completion.
- **Climb N floors** starts a stair session and sends the action to the phone.
- **Complete** ends the active session, reconciles it with phone storage, and schedules a glucose
  observation at the configured follow-up delay.
- **Snooze** suppresses coaching for the configured interval.

Each quick-action invocation resolves locally to an explicit queued or rejected outcome. Queued
means the watch saved work for delivery; the phone remains authoritative. Notification and
complication entry points display the terminal result instead of silently closing. Session
start/completion uses the persistent pending/tombstone replica. Snooze uses a separate bounded
durable command outbox, suppresses the current prompt immediately, retries Data Layer delivery, and
is deduplicated by the phone if the same command is replayed.

Swipe horizontally among three pages:

1. **Coach/Home** — current glucose, recommendation, and active session;
2. **Actions** — walk, stairs, snooze, and completion controls;
3. **Today** — daily step/floor progress and phone/watch battery status.

The app is touch-only and does not require a rotating bezel. Tap or long-press the glucose panel to
show or hide additional trend detail. Motion is intentionally minimal: horizontal pager movement
and the return to Coach/Home after a session starts. Decorative and ambient animation are avoided;
smoothness and battery behavior still require physical Galaxy Watch8 acceptance.

Starts and completions are saved as pending watch mutations until the phone publishes a matching
result. A pending local start cannot be erased by an older phone state, and a completed session
cannot be revived while its tombstone is pending. Offline starts and snoozes can expire. A delayed
completion still closes a session already known to the phone and preserves its recorded completion
time; an expired completion whose start never reached the phone is rejected rather than retried
forever. If an action expires, is unsafe in its recorded start context, or conflicts with a
different phone session, the watch adopts the phone state and displays an explanation.
For a delayed coached start, the phone checks the recorded tap time against the original prompt
window and the stored glucose context at that time, not the later reconnect time. The activity dose
and recommendation provenance come from the immutable prompt snapshot authored on the phone; the
watch cannot replace them during delayed delivery.

### Watch face

The interactive face shows:

- large clock;
- glucose, trend, and delta when available;
- age since the CGM measurement;
- steps/floors;
- watch battery;
- coach prompt when present.

Tap an actionable coach prompt to start the suggested walk or stair session. The WFF face uses
fixed complication slots; other general taps/swipes and long-press behavior remain controlled by
Wear OS. Its accent can be set to metabolic, white, or cyan through watch-face configuration.

In always-on/ambient mode, nonessential content is hidden to reduce visual clutter and power use.
Do not expect the action button or all activity fields to remain visible in ambient mode.

## Coaching behavior

The coach evaluates in this order:

1. missing, future-dated, or stale glucose;
2. below-low-threshold safety pause;
3. glucose falling at or faster than the configured exercise-pause rate;
4. notifications and quiet hours;
5. snooze;
6. cooldown/daily notification cap;
7. rapid-rise walk;
8. post-meal walk;
9. working-hours inactivity stairs, or a walk when stair reminders are disabled and walking
   reminders remain enabled.

Rapid rise, post-meal, and inactivity can overlap; the earlier eligible item wins. Working hours
currently constrain inactivity coaching, while quiet hours suppress exercise actions but do not
hide higher-priority safety information generated by the phone.

Actions have stable IDs and explicit expiry times. Phone and Wear reevaluate them on minute
boundaries, so an action disappears when its glucose becomes stale, its post-meal window ends,
quiet hours begin, a session is active, notifications are disabled, or shared glucose safety is no
longer satisfied. A notification also times out at the action's validity boundary.

Phone and watch may each show a locally generated notification. Notification bridging is disabled
to avoid the phone notification being mirrored as an additional bridge notification.

## Settings

All coaching thresholds are configurable on the phone:

- Nightscout server names/URLs, explicit active server, HTTPS policy, polling interval, connection
  timeout, retry interval/count, and glucose display unit;
- low and target glucose thresholds;
- rapid-rise threshold, exercise-pause fall rate, and stale age;
- walk duration and stair target;
- inactivity duration;
- post-meal delay and window;
- cooldown, snooze, and daily prompt limit;
- quiet and working hours;
- daily step and floor goals;
- minimum observation samples;
- timing samples per bucket and comparable timing-bucket minimum;
- general and post-meal timing-bucket widths;
- follow-up-delay matching width and baseline glucose matching band;
- intervention follow-up delay and quick-action expiry;
- optional Glycemic Goal Planner target GMI, target provenance, 30/60/90-day horizon, low/very-low
  boundaries, maximum low-exposure limits, and any number of saved planning milestones with one
  selected detail target;
- reminder enable/disable switches;
- theme and font scale.

The settings screen and validator share the same full valid ranges, so a value selectable in the UI
is accepted by persistence. Quiet- and working-hour controls have separate hour and minute editors
and preserve exact one-minute values rather than rounding to 15-minute increments.

Glucose thresholds are currently edited and stored in mg/dL even when mmol/L display is selected.
System, dark, and high-contrast themes plus font scale are applied across the phone and Wear
Compose applications. Watch-face accent remains a separate WFF configuration.

## Reading freshness

“N min” is measured from the source sample timestamp, not from when it reached the watch.

- Periodic phone work is scheduled at the configured Nightscout polling interval when an active
  server exists. WorkManager enforces a 15-minute minimum and Android may still defer it.
- Manual Refresh directly runs a foreground provider read and does not require background access.
- Missing Health Connect background activity permission does not cancel Nightscout polling.
- The Data Layer buffers state during disconnection and sends it after reconnection.

Always look at sample age. A later arrival does not make an old sample current.

## Troubleshooting

### No glucose

- Open the phone app and read provider status.
- Confirm a configured Nightscout URL is selected as the active server in Settings.
- Open that server in a trusted browser and confirm it has a recent reading.
- Confirm the base URL contains no `/api/v1`, query string, fragment, or credentials.
- Keep **Require HTTPS** enabled for an internet server and check the phone's network/TLS access.
- If cached data is shown, inspect its age. Select a different configured server only as an
  intentional source change; Metabolic Coach never switches automatically.
- Tap Refresh.
- Check the official CGM app and phone connectivity.
- Version 1 cannot select direct CareSens, xDrip broadcast, or Health Connect glucose. Any legacy
  provider selection is migrated to Nightscout.

### Watch is not updating

- Confirm phone and watch remain paired and Google Play services are current.
- Open both Metabolic Coach apps once.
- Tap Refresh on the phone.
- Wait for reconnection if Bluetooth/Wi-Fi was unavailable.
- Reinstall both phone and Wear apps from the same signed build if signature mismatch is suspected.

### Watch face is empty

- Confirm the Wear app is installed.
- Open it and wait for current data.
- Reselect the face.
- If activity falls back to system steps, the Metabolic Coach complication provider has not
  supplied its combined steps/floors value.

### No reminder

- Check phone and watch notification permissions/channels.
- Check notifications and the specific reminder switch in Settings.
- Check quiet hours, snooze, cooldown, and daily limit.
- Check sample age, low threshold, and configured exercise-pause fall rate.
- Confirm an active Nightscout server and phone network access before expecting periodic glucose
  refresh. Health Connect background permission affects activity, not Nightscout polling.
- Remember that Android may delay periodic background work even when it is enabled.

### Settings do not save

Some values have validation limits, and target lower must remain below target upper. If validation
fails, the Settings screen lists the exact invalid bounds so they can be corrected before saving.

## Remove local data

Open **Settings → Your data** on the phone.

- **Export personal data** opens Android's document picker and writes a versioned JSON document
  containing coaching settings, Glycemic Goal Planner settings, and all Metabolic Coach phone history. Nightscout server
  configuration is not included, so this is not a connection-settings backup. The file is plain
  text, can contain sensitive health/source data, and is not encrypted by the app. Save it only to
  a location you trust.
- **Erase Metabolic Coach data** shows a confirmation before deleting phone history and resetting
  saved settings and provider memory caches. It also sends a durable reset that clears cached
  state, pending sessions, and queued actions on the watch when the watch next synchronizes.

Erase removes saved Nightscout settings from Metabolic Coach but does not delete or reconfigure the
Nightscout server, Health Connect, Samsung Health, CareSens, or xDrip source records and does not
revoke permissions. New records can be collected again after the app is reconfigured; revoke
Health Connect access and change Nightscout access separately if collection should stop. If watch
delivery is temporarily unavailable, the phone keeps the reset token and retries it in later state
publications. A delayed watch action from before the erase is rejected.

The JSON export is an engineering data copy, not a medical record or backup of the source system.
