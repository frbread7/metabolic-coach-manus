# Install Metabolic Coach

The packaging script adds the archive's exact build variant and APK version above this guide.
Read that package metadata before installing.

## Safety and release status

Metabolic Coach is a wellness engineering project, not a medical device or replacement for the
CareSens application, Nightscout, CGM alarms, professional advice, or a personal care plan. A debug
package is for controlled testing only. Do not treat it as a production daily-use release.

## Archive contents

```text
phone.apk
wear.apk
watchface.apk
CHANGELOG.md
INSTALL.md
```

Keep all three APKs from the same ZIP together. Phone and Wear Data Layer communication requires
matching package names and signing identities.

## Requirements

- The Metabolic Coach phone APK supports Android 9/API 28 or newer. For the primary Samsung
  Galaxy Watch8 target, Samsung requires a compatible Samsung Galaxy phone running Android 12 or
  newer; the APK minimum and the watch-pairing requirement are different constraints.
- Samsung Galaxy Watch8 or another Wear OS 6/API 36 device for this WFF v4 watch face.
- A reachable Nightscout server. Use HTTPS for any internet-hosted or daily-use server.
- Bugjaeger or an equivalent trusted ADB client if installing the watch APKs from the phone.
- Developer options and wireless debugging enabled temporarily on the watch.
- The phone running Bugjaeger and the watch must be on the same Wi-Fi network, and that network
  must permit device-to-device traffic. Guest networks and access-point isolation can prevent ADB
  pairing or connection.

Compatibility and wireless-debugging prerequisites are based on Samsung's
[Galaxy Watch8 specifications](https://www.samsung.com/us/watches/galaxy-watch8/) and Android's
[Wear OS wireless debugging guide](https://developer.android.com/training/wearables/get-started/debug-wifi).

## v0.3 physical-acceptance preflight

The `v0.3` milestone validates the unchanged `v0.2.0` APKs; it is not a relabeled `v0.3` binary
release. Before installation, record the ZIP filename and externally published SHA-256, embedded
APK version/build variant, phone/watch models and OS builds, and test date. Use
`docs/V0.3_WEAR_ACCEPTANCE.md` from the project as the authoritative result sheet.

If the already accepted phone APK is installed from this same build/signing identity, do not
uninstall it. Uninstalling would delete its local settings and history. Install only the missing
Wear and watch-face components unless an intentional same-certificate upgrade is required.

## v0.4.2 CI artifact preflight (historical)

The v0.4.2 package was a debug-signed engineering candidate for the phone-only saved-planning-
milestones acceptance gate. Verify the ZIP SHA-256, commit SHA, versionName `0.4.2`, versionCode
`6`, and signing certificate recorded in the GitHub Actions run summary before installing. It is an
in-place update only when its certificate matches the accepted v0.3 package. Wear and watch-face
APKs are rebuilt to keep the package metadata aligned; this milestone does not claim a new physical
acceptance for those components.

After installation, use [the v0.4.2 acceptance checklist](docs/V0_4_2_SAVED_MILESTONES.md) and
stop after recording the phone results.

## v0.6.0 local engineering artifact preflight

The v0.6.0 package is a debug-signed engineering candidate for the APOS-reviewed post-meal walk
coaching increment. Verify the ZIP SHA-256, commit SHA, versionName `0.6.0`, versionCode `9`, and
signing certificate before installing all three APKs over the accepted build without clearing app
data. This is not a production-signed release.

After installation, use [the v0.6.0 physical acceptance document](docs/V0_6_0_POST_MEAL_COACHING.md).
Stop after reporting the checklist; do not proceed to another feature milestone.

## v0.6.1 local engineering artifact preflight

The v0.6.1 package is a debug-signed engineering checkpoint for confirmed rapid-rise walk
coaching. Verify the ZIP SHA-256, commit SHA, versionName `0.6.1`, versionCode `10`, and signing
certificate before any intentional installation. Keep the matched phone, Wear, and watch-face APKs
together and install them as an in-place update only when the signing certificate matches the
accepted installation.

Its status is `Engineering: PASS / Physical: DEFERRED` only after the recorded local automated and
review gates pass. Installation is optional until the integrated coaching release candidate. If it
is installed earlier, record only privacy-safe categorical results in
[the cumulative physical-test backlog](PHYSICAL_TEST_BACKLOG.md); do not call the milestone
physically accepted without a user-reported real-device pass.

## v0.6.2 local engineering artifact preflight

The v0.6.2 package is a debug-signed engineering checkpoint for fail-closed prolonged-inactivity
WALK coaching with status `Engineering: PASS / Physical: DEFERRED`. Verify the ZIP SHA-256,
feature commit SHA, versionName `0.6.2`, versionCode `11`, and
signing certificate before any intentional installation. Keep the matched phone, Wear, and
watch-face APKs together and install them as an in-place update only when the signing certificate
matches the accepted installation.

Automated inactivity coaching is WALK-only; stair settings do not create an automated stair
prompt. The artifact remains `Physical: DEFERRED` and is intended to be exercised with the
integrated `v0.7.0` coaching release-candidate checklist. Record only privacy-safe categorical
results in [the cumulative physical-test backlog](PHYSICAL_TEST_BACKLOG.md), and never call this
milestone physically accepted without a user-reported real-device pass.

## v0.7.0 integrated WALK release-candidate preflight

The v0.7.0 package combines the unchanged post-meal, confirmed rapid-rise, and
prolonged-inactivity WALK paths in one matched engineering RC. Verify the externally recorded ZIP
SHA-256, pinned commit, versionName `0.7.0`, versionCode `12`, and signing certificate before any
installation. Keep the phone, Wear, and watch-face APKs from the same ZIP together and install only
as an in-place update when their certificate matches the accepted installation.

This artifact is `Physical: DEFERRED`. Android instrumentation and all consolidated device rows
remain `NOT RUN`; skipping them does not make them pass. Automated stairs remain disabled. If the
user later tests this RC, use
[the consolidated v0.7.0 checklist](docs/V0_7_0_INTEGRATED_WALK_RC.md), record categorical results
only, and do not include health values, endpoints, credentials, or private screenshots.

## Historical v0.5.1 CI artifact preflight

The v0.5.1 package is a debug-signed engineering candidate for the phone-only local History
Explorer and selected-period GMI. Verify the ZIP SHA-256, commit SHA, versionName `0.5.1`,
versionCode `8`, and signing
certificate recorded in the GitHub Actions run summary before installing. It is an in-place update
only when its certificate matches the accepted v0.3 package. Wear and watch-face APKs are rebuilt
only to keep the three-module package metadata aligned; their behavior is unchanged.

After installation, use [the v0.5.1 History Explorer acceptance document](docs/V0_5_1_HISTORY_EXPLORER.md).
Confirm the fixed and custom local-only periods, disconnected gaps, selected-period GMI eligibility
and wording, stale-request protection, and frozen current-glucose/Wear/coaching behavior. Do not
record private glucose values, server addresses, or credentials. Stop after recording the phone
acceptance results; do not proceed to coaching work.

## v0.4.1 CI artifact preflight (historical)

The v0.4.1 package is a debug-signed engineering candidate for the freshness-defect phone retest. Verify
the ZIP SHA-256, commit SHA, and signing certificate recorded in the GitHub Actions run summary
before installing. It is an in-place update only when its certificate matches the accepted v0.3
package; otherwise stop and treat it as clean-install-only. The failed `v0.4.0` artifact remains a
separate historical record; do not relabel it or use it as the retest artifact. Wear and watch-face
APKs are rebuilt only to keep package metadata aligned and are not being re-accepted by this gate.

## Install from a phone with Bugjaeger

1. Download the ZIP from the build VM and verify that its filename, externally recorded SHA-256,
   and embedded package metadata identify the intended version and build variant.
2. Extract the ZIP into one folder on the phone.
3. Put the phone and watch on the same non-isolated Wi-Fi network. On the watch, enable developer
   options, then enable **ADB debugging** and **Wireless debugging**.
4. Use the watch's **Pair new device** screen and pair Bugjaeger using the displayed pairing
   address, port, and code. Then connect Bugjaeger to the separate wireless-debugging address and
   connection port shown one level above **Pair new device**.
5. In Bugjaeger, install `wear.apk` on the connected watch and wait for a successful result.
6. Install `watchface.apk` on the watch and select **Metabolic Coach** in the watch-face picker.
7. If the matching phone build is not already installed, install `phone.apk` on the phone. Android
   may require permission for the chosen installer to install unknown applications. Do not
   uninstall the accepted phone build merely to repeat this step.
8. Open the phone app, review the safety notice, and grant only the Health Connect activity and
   notification permissions you intend to use.
9. In phone Settings, enter a name and base URL for Nightscout, keep **Require HTTPS** enabled, and
   select that entry as the active server. Enter only the server base URL, such as
   `https://example.fly.dev`; do not include `/api/v1`, a query string, or credentials.
10. Save Settings and use **Refresh now**. Confirm the phone shows the expected current glucose,
    trend, delta, and update age before relying on watch display.
11. Open the Wear app and confirm that normalized phone state reaches the watch before testing quick
    actions.
12. Disable wireless debugging on the watch when installation is complete.

You may configure more than one Nightscout server, but the app uses only the explicitly selected
active server. It does not fail over automatically. Switching servers also switches the visible
source history; verify the active name before testing.

Disabling **Require HTTPS** permits cleartext HTTP for an explicit local/test server. Cleartext can
expose glucose to the network and is not suitable for an internet-hosted server.

Bugjaeger labels can change between versions. Use its APK/package installation action for the
currently connected watch; do not use destructive package-data or uninstall actions during an
upgrade.

## Upgrade rules

- Install all components from the same archive.
- An APK can update an installed app only when its package and signing certificate match.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` usually means the certificate changed. Do not uninstall
  automatically if local history matters, because uninstalling deletes that app's local data.
- Export Metabolic Coach data from the phone Settings screen before any intentional uninstall.
- After an upgrade, verify the selected Nightscout server and HTTPS policy, refresh behavior,
  phone/watch synchronization, Health Connect activity and notification permissions, quick
  actions, complications, and Always-On Display behavior.

## Removal

Uninstalling Metabolic Coach deletes its local app data, including saved Nightscout server settings,
but does not delete Nightscout, Health Connect, Samsung Health, CareSens, xDrip, or other source
records. Revoke Health Connect permissions and change server access separately if data collection
should stop.
