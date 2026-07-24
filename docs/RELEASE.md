# Release, signing, and distribution

## Release posture

The repository is not release-ready merely because APKs assemble. A production release requires
signed bundles, an authorized and measured glucose route, Galaxy Watch8 verification, WFF
validation, privacy/health policy work, and all gates in [Testing](TESTING.md).

The 2026-07-23 local candidate passed 163 JUnit executions covering 144 distinct test cases, debug
and release lint/assembly, APK checks, release manifest isolation, and WFF v4 schema/memory
validation; exact evidence and debug hashes are in
[Testing](TESTING.md#latest-local-verification). Production signing, instrumentation, physical
Galaxy Watch8 validation, and store approval remain outstanding.

Release provider topology is intentionally narrow: Health Connect is the only implemented release
data route, and the CareSens partner option is a nonfunctional approval placeholder. The xDrip
receiver, permission, and provider selection are debug-only; release repository policy converts a
persisted debug xDrip selection to Health Connect. This remains pending a verified sender contract
and security review.

## Application identities

| Package | Modules/devices | Reason |
| --- | --- | --- |
| `com.young.metaboliccoach` | `:phone` on Android and `:wear` on Wear OS | Wear Data Layer requires matching package and signature |
| `com.young.metaboliccoach.watchface` | `:watchface` on Wear OS | WFF resource bundle must be separate from Wear logic |

Confirm these are final before production signing or Samsung partner registration. Changing a
package later breaks upgrades, registrations, Data Layer identity, and store continuity.

Phone and Wear versioning must remain coordinated. Use monotonically increasing version codes
appropriate for store multi-device delivery. The watch-face package has an independent store
version history.

## Debug APK handoff

Run:

```bash
./scripts/build-apks.sh
```

The script runs validation/tests/lint/assembly, verifies all APK signatures, requires phone and
Wear certificate equality, copies the artifacts, and prints SHA-256 hashes. It now includes the
model, domain, data, sync, phone, and Wear local test suites plus Android migration-test source
compilation. Compilation is not instrumentation execution. Record exact counts, results, sizes,
hashes, and signing certificate digest only from a fresh successful run against the commit being
handed off.

These are debug-signed engineering artifacts, not production releases.
The current debug files under `artifacts/` are linked to the 2026-07-23 evidence. Regenerate and
rehash them after any source/build change.

## Release APKs and signing

Phone, Wear, and watch-face release build types are minified and have environment-only signing
configuration. The release pipeline requires:

```bash
export MC_RELEASE_KEYSTORE_PATH=/absolute/path/to/release.keystore
export MC_RELEASE_STORE_PASSWORD='...'
export MC_RELEASE_KEY_ALIAS='...'
export MC_RELEASE_KEY_PASSWORD='...'
MC_BUILD_VARIANT=release ./scripts/build-apks.sh
```

`scripts/build-apks.sh` refuses release mode when any variable is absent or the keystore does not
exist. On success it additionally verifies signatures, verifies phone/Wear certificate equality,
and creates:

```text
artifacts/metabolic-coach-phone-release.apk
artifacts/metabolic-coach-wear-release.apk
artifacts/metabolic-coach-watchface-release.apk
```

No keystore or password is configured in source. The repository currently contains no
production-signed release artifacts.

Raw Gradle assembly without those variables produces minified unsigned APKs:

```bash
./gradlew :phone:assembleRelease :wear:assembleRelease :watchface:assembleRelease
```

When the command succeeds, unsigned outputs are expected at:

```text
phone/build/outputs/apk/release/phone-release-unsigned.apk
wear/build/outputs/apk/release/wear-release-unsigned.apk
watchface/build/outputs/apk/release/watchface-release-unsigned.apk
```

Current-checkout assembly and WFF memory/resource-only validation are recorded in
[Testing](TESTING.md#latest-local-verification) and must be rerun after any future source/build
change. Unsigned artifacts are static build evidence only and must not be handed off as installable
releases.

For Play bundle generation after secure signing configuration is injected:

```bash
./gradlew :phone:bundleRelease :wear:bundleRelease :watchface:bundleRelease
```

Expected AABs are under each module's `build/outputs/bundle/release/` directory.

## Signing policy

- Generate and store the upload/app-signing keys outside the repository.
- Use an approved secret manager for CI.
- Never place keystores, passwords, base64 keystores, signing properties, or certificate private
  material in source control or build logs.
- Ensure phone and Wear artifacts use the same application-signing identity.
- Record SHA-256 fingerprints through an approved secure release process.
- Keep watch-face signing stable for upgrades; using the same organization-controlled certificate
  is recommended even though it has a separate package.
- Test Data Layer sync using release-signed internal-track artifacts, not only debug builds.

If direct Samsung Health Data SDK support is added, submit the final package name and release
certificate SHA-256 through Samsung's official process:
[app verification](https://developer.samsung.com/health/data/guide/app-verification.html).

## Store topology

The phone and Wear packages belong to the same product/package identity and must be configured for
the correct device targets and delivery model in Google Play.

The WFF watch face must be uploaded as a separate watch-face app/bundle. Google explicitly requires
WFF bundles to be separate from bundles containing Wear app logic:
[WFF setup](https://developer.android.com/training/wearables/wff/setup).

Before upload:

- inspect manifest device features, minimum/target SDK, exported components, and permissions;
- verify phone/Wear package and signature identity;
- verify WFF package has `android:hasCode="false"`;
- repeat WFF v4 XML and memory validation with the final signed candidate and archive the validator
  version/result;
- provide phone, round-watch, and ambient screenshots from the release candidate;
- verify store listing does not imply i-SENS, Samsung, xDrip, or medical endorsement.

## Google Play health and privacy work

Complete, as applicable:

- Health apps declaration;
- Health Connect permission and prominent-disclosure requirements;
- Data safety form;
- public privacy policy;
- account/data deletion disclosure even though no account currently exists;
- target API and Wear quality requirements;
- watch-face policy and memory validation;
- notification and exact wording review;
- country availability based on provider/device support.

Recheck current policies at release time; store policy is time-sensitive.

## Release checklist

### Source and dependency

- [ ] Version names/codes updated intentionally.
- [ ] Dependency and license inventory reviewed.
- [ ] No credentials, private health data, or vendor-restricted binaries committed.
- [ ] Room v7, exported schemas 1–7, migrations 1→7, and migration-test execution reviewed.
- [ ] R8 rules and release warnings reviewed.
- [ ] Exported components and PendingIntents security-reviewed.

### Provider

- [ ] Authorized glucose source identified.
- [ ] CareSens/device/region support recorded.
- [ ] Health Connect or approved-provider latency meets a written target.
- [ ] Health Connect writer discovery, one-writer auto-selection, multi-writer configuration gate,
      persisted exact-source pinning, and missing-source behavior pass on target phones.
- [ ] Background permission and degraded-mode behavior pass.
- [ ] Release merged manifest contains no xDrip receiver or receive permission.
- [ ] Release provider UI cannot select xDrip.
- [ ] Release provider-policy test confirms a persisted debug xDrip selection becomes Health
      Connect before repository access.
- [ ] Any future xDrip enablement remains blocked on a verified sender contract and security review.
- [ ] Samsung registration complete if its direct SDK is included.

### Quality

- [ ] Unit tests pass.
- [ ] Lint/static checks pass.
- [ ] Phone, Wear, and watch-face release artifacts build.
- [ ] WFF v4 schema and memory validation pass.
- [ ] Galaxy Watch8 touch, round-layout, AOD, burn-in, and battery tests pass.
- [ ] Disconnect/reconnect/reboot/action idempotency and pending-mutation/tombstone reconciliation
      tests pass.
- [ ] Android phone matrix passes.
- [ ] Accessibility and localization pass.
- [ ] No high-severity known defect remains.

### Safety and privacy

- [ ] Medical/wellness claims reviewed.
- [ ] Missing, future, stale, low, and configurable fast-fall behavior is identical on phone and
      Wear.
- [ ] Recommendation ID, expiry, minute-tick, quiet-hour, and notification-timeout behavior is
      verified.
- [ ] Every coached command resolves to an immutable phone-authored recommendation snapshot;
      missing snapshots and conflicting watch echoes are terminally rejected, while delayed valid
      commands retain the snapshot's exact activity dose and provenance.
- [ ] Exact baseline/follow-up reading provenance, same-source filtering, observation timing, and
      wording are aligned.
- [ ] Prospective timing retains exact trigger/recommendation/source/activity-dose/follow-up/
      baseline provenance, applies the documented cohort/exclusion gates, remains display-only, and
      makes no causal, medical, best, or ideal timing claim.
- [ ] Phone instance/revision/session acknowledgement ordering and all rejection outcomes are
      verified across disconnect, restart, and reboot.
- [ ] Privacy policy and in-app disclosure match actual flows.
- [ ] Logging/crash tooling verified free of health data.
- [ ] Retention and erase behavior documented.
- [ ] Support and incident response owners assigned.

### Distribution

- [ ] Release certificate and Play signing verified.
- [ ] Phone/Wear sync proven with internal-track signing.
- [ ] APK/AAB SHA-256 hashes archived.
- [ ] ProGuard mapping/native symbols archived where applicable.
- [ ] Test evidence and device/build metadata archived.
- [ ] Phased rollout and rollback criteria approved.

## APK handoff manifest

For every delivered artifact, record:

```text
artifact filename:
module/package:
version name/code:
build variant:
git commit:
build timestamp UTC:
JDK/Gradle/AGP:
signing certificate SHA-256:
file SHA-256:
tests linked:
known limitations:
```

Do not rename an artifact in a way that obscures whether it is debug, unsigned, or release-signed.

## Internal installation smoke test

Use trusted release-candidate artifacts:

1. install phone and Wear packages on paired devices;
2. confirm certificate/package identity;
3. grant permissions through user-visible flows;
4. import synthetic Health Connect data;
5. confirm state synchronization and complication update;
6. execute walk, stair, snooze, and complete from app/notification/face;
7. disconnect and reconnect;
8. reboot both devices;
9. verify ambient mode and 24-hour battery behavior;
10. uninstall and confirm local-data behavior.

Never use personal medical data in store review screenshots or shared test evidence.

## Rollout and rollback

Start with an internal track, then a small closed test. Define automatic stop criteria for crashes,
stale/wrong readings, missing low-data pause, action duplication, excessive notification volume,
sync failure, or abnormal battery drain.

Rollback means shipping a higher-version corrective artifact or halting rollout; never reuse a
version code or lose the signing key. Because health data semantics are involved, preserve database
compatibility and do not destructively downgrade Room data.
