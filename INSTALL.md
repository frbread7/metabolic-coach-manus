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

- Android phone with Android 9/API 28 or newer.
- Samsung Galaxy Watch8 or another Wear OS 6/API 36 device for this WFF v4 watch face.
- A reachable Nightscout server. Use HTTPS for any internet-hosted or daily-use server.
- Bugjaeger or an equivalent trusted ADB client if installing the watch APKs from the phone.
- Developer options and wireless debugging enabled temporarily on the watch.

## Install from a phone with Bugjaeger

1. Download the ZIP from the build VM and verify that its filename and embedded package metadata
   identify the intended version and build variant.
2. Extract the ZIP into one folder on the phone.
3. On the watch, enable developer options, then enable **ADB debugging** and **Wireless debugging**.
4. Use the watch's **Pair new device** screen and pair Bugjaeger using the displayed pairing
   address, port, and code. Then connect Bugjaeger to the watch's wireless-debugging address.
5. In Bugjaeger, install `wear.apk` on the connected watch and wait for a successful result.
6. Install `watchface.apk` on the watch and select **Metabolic Coach** in the watch-face picker.
7. Install `phone.apk` on the phone. Android may require permission for the chosen installer to
   install unknown applications.
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
