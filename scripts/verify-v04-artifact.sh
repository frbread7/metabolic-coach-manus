#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

variant="${MC_BUILD_VARIANT:-debug}"
expected_version_name="${MC_EXPECTED_VERSION_NAME:-0.4.0}"
expected_version_code="${MC_EXPECTED_VERSION_CODE:-4}"
expected_certificate="${MC_EXPECTED_V03_CERT_SHA256:-}"
artifacts_dir="$repo_root/artifacts"

case "$variant" in
    debug|release)
        ;;
    *)
        echo "MC_BUILD_VARIANT must be 'debug' or 'release'." >&2
        exit 2
        ;;
esac

resolve_tool() {
    local command_name="$1"
    local candidate
    if command -v "$command_name" >/dev/null 2>&1; then
        command -v "$command_name"
        return
    fi
    local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -n "$sdk_root" ]]; then
        candidate="$(find "$sdk_root/build-tools" -maxdepth 2 -type f -name "$command_name" \
            -print 2>/dev/null | sort -V | tail -n 1)"
        if [[ -n "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return
        fi
    fi
    echo "$command_name was not found; install Android SDK Build Tools." >&2
    exit 2
}

aapt="$(resolve_tool aapt)"
apksigner="$(resolve_tool apksigner)"

phone_apk="$artifacts_dir/metabolic-coach-phone-$variant.apk"
wear_apk="$artifacts_dir/metabolic-coach-wear-$variant.apk"
watchface_apk="$artifacts_dir/metabolic-coach-watchface-$variant.apk"
archive="$artifacts_dir/MetabolicCoach-v0.4.zip"

for apk in "$phone_apk" "$wear_apk" "$watchface_apk"; do
    if [[ ! -f "$apk" ]]; then
        echo "Expected APK is missing: $apk" >&2
        exit 1
    fi

    badging="$($aapt dump badging "$apk")"
    version_name="$(sed -n "s/^package:.* versionName='\\([^']*\\)'.*/\\1/p" <<<"$badging" | head -n 1)"
    version_code="$(sed -n "s/^package:.* versionCode='\\([^']*\\)'.*/\\1/p" <<<"$badging" | head -n 1)"
    if [[ "$version_name" != "$expected_version_name" ||
        "$version_code" != "$expected_version_code" ]]; then
        echo "Unexpected metadata in $(basename "$apk"): " \
            "versionName=$version_name versionCode=$version_code" >&2
        exit 1
    fi
    debuggable_count="$(grep -c '^application-debuggable$' <<<"$badging" || true)"
    if [[ "$variant" == "debug" && ( "$apk" == "$phone_apk" || "$apk" == "$wear_apk" ) &&
        "$debuggable_count" != "1" ]]; then
        echo "Expected a debuggable phone/Wear APK: $apk" >&2
        exit 1
    fi

    certificate="$($apksigner verify --print-certs "$apk" |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1 |
        tr '[:upper:]' '[:lower:]')"
    if [[ -z "$certificate" ]]; then
        echo "Could not read the signing certificate from $apk." >&2
        exit 1
    fi
    if [[ -n "$expected_certificate" && "$certificate" != "${expected_certificate,,}" ]]; then
        echo "Signing certificate mismatch in $(basename "$apk")." >&2
        echo "Expected v0.3 certificate: $expected_certificate" >&2
        echo "Actual certificate: $certificate" >&2
        exit 1
    fi
    printf '%s certificate: %s\n' "$(basename "$apk")" "$certificate"
done

if [[ ! -f "$archive" ]]; then
    echo "Expected v0.4 archive is missing: $archive" >&2
    exit 1
fi
unzip -tqq "$archive"
expected_entries=$'CHANGELOG.md\nINSTALL.md\nphone.apk\nwatchface.apk\nwear.apk'
actual_entries="$(unzip -Z1 "$archive" | LC_ALL=C sort)"
if [[ "$actual_entries" != "$expected_entries" ]]; then
    echo "v0.4 archive contents do not match the five-file contract." >&2
    printf 'Expected:\n%s\nActual:\n%s\n' "$expected_entries" "$actual_entries" >&2
    exit 1
fi

archive_text="$(unzip -p "$archive" CHANGELOG.md INSTALL.md)"
if grep -nE '(NIGHTSCOUT_TOKEN|API_SECRET|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY)' <<<"$archive_text"; then
    echo "A credential or private key marker was found in the v0.4 archive." >&2
    exit 1
fi

if git grep -nE '(API_SECRET|NIGHTSCOUT_TOKEN|MC_DEBUG_KEYSTORE_BASE64)[[:space:]]*=[[:space:]]*[^$<{[:space:]]+' -- ':!build' ':!artifacts'; then
    echo "A credential-like assignment was found in tracked project files." >&2
    exit 1
fi
if git grep -nE 'BEGIN (RSA|OPENSSH|EC) PRIVATE KEY' -- ':!build' ':!artifacts'; then
    echo "A private key was found in tracked project files." >&2
    exit 1
fi

echo "v0.4 artifact metadata, signature continuity, ZIP integrity, and credential audit passed."
sha256sum "$phone_apk" "$wear_apk" "$watchface_apk" "$archive"
