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
- a supported source that places glucose in Health Connect; debug developers may deliberately
  enable the separate xDrip compatibility path;
- health and notification permissions you choose to grant.

CareSens Air direct integration is not included. Verify CareSens device/app compatibility in your
country using the official [CareSens compatibility page](https://caresensair.com/en_US/content/compatibility/).

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
unverified source. The current artifact hashes and local verification date are recorded in
[Testing](TESTING.md#latest-local-verification); regenerate them after any source/build change.

For a distributed release, follow the store/install instructions supplied with that signed build
rather than sideloading.

## First-time setup

### 1. Prepare the source

For Health Connect:

1. Confirm Health Connect is available.
2. In the CGM/Samsung Health ecosystem, enable only the supported sharing options you intend to
   use.
3. Open Health Connect and confirm a recent blood glucose record is actually present.
4. Check that steps, floors, heart rate, exercise, and active calories are present if you want those
   fields.

CareSens-to-Samsung-to-Health-Connect transfer is not guaranteed. If no blood glucose record appears
in Health Connect, Metabolic Coach cannot retrieve it through this route.

For debug xDrip testing, read [xDrip compatibility](INTEGRATIONS.md#xdrip-compatibility) before
enabling it. The receiver and provider selection are absent from release builds because the sender
contract is not verified. The debug path is unofficial, requires Android 14+, verifies the
platform-reported xDrip package, and does not pin the app's signing certificate.

### 2. Open the phone app

1. Tap **Connect Health Connect**.
2. Review and grant only the desired read permissions.
3. Return to **Today** and tap **Refresh** so the phone can discover glucose-writing packages.
4. Open **Settings** and select the intended glucose provider.
5. If Health Connect lists more than one **Health Connect glucose source**, select exactly one
   package. Coaching remains paused until that choice is saved.
6. Tap **Enable coaching notifications** if you want prompts.
7. Review the user-facing threshold and time-window settings; defaults are not medical
   recommendations.
8. Tap **Save settings**, return to **Today**, and tap **Refresh**.

If provider status does not say it is ready, resolve that status before relying on watch data.
When only one recent Health Connect writer exists, the app saves it automatically. A saved package
remains pinned if it stops writing temporarily; it appears as **no recent records** instead of
silently switching to another app.

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

- provider and glucose display unit;
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

- Periodic phone work is scheduled every 15 minutes only when Health Connect reports background
  support and its separate permission is granted; Android may still defer it.
- Manual Refresh directly runs a foreground provider read and does not require background access.
- Debug-only xDrip ingestion schedules a refresh after an accepted broadcast.
- The Data Layer buffers state during disconnection and sends it after reconnection.

Always look at sample age. A later arrival does not make an old sample current.

## Troubleshooting

### No glucose

- Open the phone app and read provider status.
- Confirm the chosen provider in Settings.
- For Health Connect, confirm a recent glucose record exists and permission is granted. After
  Refresh, choose one listed writer package if status says configuration is required.
- If the saved writer says **no recent records**, verify that exact source. Select a different
  package only as an intentional source change; Metabolic Coach will not switch automatically.
- Tap Refresh.
- Check the official CGM app and phone connectivity.
- Release builds cannot select xDrip and replace any debug selection retained during an upgrade
  with Health Connect. In debug testing, do not use it merely to bypass an
  authorization problem without understanding its limitations.

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
- Confirm Health Connect background-read support and permission before expecting periodic refresh.
- Remember that Android may delay periodic background work even when it is enabled.

### Settings do not save

Some values have validation limits, and target lower must remain below target upper. If validation
fails, the Settings screen lists the exact invalid bounds so they can be corrected before saving.

## Remove local data

Open **Settings → Your data** on the phone.

- **Export personal data** opens Android's document picker and writes a versioned JSON document
  containing current saved settings and all Metabolic Coach phone history. The file is plain text,
  can contain sensitive health/source data, and is not encrypted by the app. Save it only to a
  location you trust.
- **Erase Metabolic Coach data** shows a confirmation before deleting phone history and resetting
  saved settings. It also sends a durable reset that clears cached state, pending sessions, and
  queued actions on the watch when the watch next synchronizes.

Erase does not delete Health Connect, Samsung Health, CareSens, or debug xDrip source records and
does not revoke permissions. New records can be collected again when you continue using or restart
the app; revoke source permissions separately if you want collection to stop. If watch delivery is
temporarily unavailable, the phone keeps the reset token and retries it in later state
publications. A delayed watch action from before the erase is rejected.

The JSON export is an engineering data copy, not a medical record or backup of the source system.
