#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
artifacts_dir="$repo_root/artifacts"
variant="${MC_BUILD_VARIANT:-debug}"

case "$variant" in
    debug | release)
        ;;
    *)
        echo "MC_BUILD_VARIANT must be 'debug' or 'release'." >&2
        exit 2
        ;;
esac

if [[ "$(uname -m)" == "aarch64" || "$(uname -m)" == "arm64" ]]; then
    export QEMU_LD_PREFIX="${QEMU_LD_PREFIX:-/usr/x86_64-linux-gnu}"
fi

phone_apk="$artifacts_dir/metabolic-coach-phone-$variant.apk"
wear_apk="$artifacts_dir/metabolic-coach-wear-$variant.apk"
watchface_apk="$artifacts_dir/metabolic-coach-watchface-$variant.apk"

for required_file in \
    "$phone_apk" \
    "$wear_apk" \
    "$watchface_apk" \
    "$repo_root/CHANGELOG.md" \
    "$repo_root/INSTALL.md"; do
    if [[ ! -f "$required_file" ]]; then
        echo "Required package input is missing: $required_file" >&2
        exit 2
    fi
done

resolve_aapt() {
    if command -v aapt >/dev/null 2>&1; then
        command -v aapt
        return
    fi

    local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -n "$sdk_root" ]]; then
        local candidate
        candidate="$(find "$sdk_root/build-tools" -maxdepth 2 -type f -name aapt \
            -print 2>/dev/null | sort -V | tail -n 1)"
        if [[ -n "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return
        fi
    fi

    echo "aapt was not found; install Android SDK Build Tools." >&2
    exit 2
}

apk_version_name() {
    "$aapt" dump badging "$1" |
        sed -n "s/^package:.* versionName='\\([^']*\\)'.*/\\1/p" |
        head -n 1
}

apk_build_variant() {
    local badging
    badging="$("$aapt" dump badging "$1")"
    if grep -qx "application-debuggable" <<<"$badging"; then
        printf 'debug\n'
    else
        printf 'release\n'
    fi
}

aapt="$(resolve_aapt)"
phone_version="$(apk_version_name "$phone_apk")"
wear_version="$(apk_version_name "$wear_apk")"
watchface_version="$(apk_version_name "$watchface_apk")"

if [[ -z "$phone_version" ]]; then
    echo "Could not read the phone APK version name." >&2
    exit 1
fi
if [[ "$phone_version" != "$wear_version" || "$phone_version" != "$watchface_version" ]]; then
    echo "APK version mismatch: phone=$phone_version wear=$wear_version watchface=$watchface_version" >&2
    exit 1
fi
for apk in "$phone_apk" "$wear_apk"; do
    actual_variant="$(apk_build_variant "$apk")"
    if [[ "$actual_variant" != "$variant" ]]; then
        echo "APK build variant mismatch: expected $variant, found $actual_variant in $apk" >&2
        exit 1
    fi
done

package_version="${MC_PACKAGE_VERSION:-$phone_version}"
if [[ ! "$package_version" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
    echo "Package version contains unsafe filename characters: $package_version" >&2
    exit 2
fi

archive_name="MetabolicCoach-v$package_version.zip"
archive_path="$artifacts_dir/$archive_name"
staging_dir="$(mktemp -d "${TMPDIR:-/tmp}/metabolic-coach-package.XXXXXX")"
trap 'rm -rf "$staging_dir"' EXIT

install -m 0644 "$phone_apk" "$staging_dir/phone.apk"
install -m 0644 "$wear_apk" "$staging_dir/wear.apk"
install -m 0644 "$watchface_apk" "$staging_dir/watchface.apk"
install -m 0644 "$repo_root/CHANGELOG.md" "$staging_dir/CHANGELOG.md"

{
    printf '# Package metadata\n\n'
    printf -- '- Archive: `%s`\n' "$archive_name"
    printf -- '- APK version name: `%s`\n' "$phone_version"
    printf -- '- Build variant: `%s`\n' "$variant"
    if [[ "$variant" == "debug" ]]; then
        printf -- '- Signing status: debug-signed engineering build; not a production release.\n\n'
    else
        printf -- '- Signing status: release-signed; verify the release certificate before use.\n\n'
    fi
    cat "$repo_root/INSTALL.md"
} > "$staging_dir/INSTALL.md"

temporary_archive="$staging_dir/$archive_name"
(
    cd "$staging_dir"
    jar \
        --create \
        --file "$temporary_archive" \
        --no-manifest \
        --date=1980-01-01T00:00:02Z \
        phone.apk \
        wear.apk \
        watchface.apk \
        CHANGELOG.md \
        INSTALL.md
)

unzip -tqq "$temporary_archive"
expected_entries=$'CHANGELOG.md\nINSTALL.md\nphone.apk\nwatchface.apk\nwear.apk'
actual_entries="$(unzip -Z1 "$temporary_archive" | LC_ALL=C sort)"
if [[ "$actual_entries" != "$expected_entries" ]]; then
    echo "Package contents do not match the five-file release contract." >&2
    printf 'Expected:\n%s\nActual:\n%s\n' "$expected_entries" "$actual_entries" >&2
    exit 1
fi

package_action="Created"
if [[ -e "$archive_path" ]]; then
    if cmp -s "$temporary_archive" "$archive_path"; then
        package_action="Unchanged"
    else
        existing_phone_apk="$staging_dir/existing-phone.apk"
        if ! unzip -p "$archive_path" phone.apk > "$existing_phone_apk"; then
            echo "Refusing to replace an unreadable existing package." >&2
            exit 2
        fi
        existing_apk_variant="$(apk_build_variant "$existing_phone_apk")"
        existing_variant="$(
            unzip -p "$archive_path" INSTALL.md 2>/dev/null |
                sed -n 's/^- Build variant: `\([^`]*\)`$/\1/p' |
                head -n 1 ||
                true
        )"
        if [[ "$existing_variant" != "$existing_apk_variant" ]]; then
            echo "Refusing to replace a package with inconsistent build metadata." >&2
            exit 2
        fi
        if [[ "$variant" == "debug" && "$existing_variant" == "release" ]]; then
            echo "Refusing to replace a release package with debug APKs." >&2
            echo "Increment the app version or set MC_PACKAGE_VERSION to a debug-only label." >&2
            exit 2
        fi
        if [[ "$variant" == "debug" && "$existing_variant" != "debug" ]]; then
            echo "Refusing to replace a package whose existing build variant is unknown." >&2
            exit 2
        fi
        if [[ "$variant" == "debug" || "${MC_PACKAGE_OVERWRITE:-0}" == "1" ]]; then
            package_action="Updated"
            install -m 0644 "$temporary_archive" "$archive_path"
        else
            echo "Refusing to overwrite a different package for v$package_version." >&2
            echo "Increment the APK version or set MC_PACKAGE_OVERWRITE=1 intentionally." >&2
            exit 2
        fi
    fi
else
    install -m 0644 "$temporary_archive" "$archive_path"
fi

printf '%s %s (%s, APK version %s)\n' \
    "$package_action" "$archive_path" "$variant" "$phone_version"
sha256sum "$archive_path"
unzip -l "$archive_path"
