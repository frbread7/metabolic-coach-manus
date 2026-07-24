#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

variant="${MC_BUILD_VARIANT:-debug}"
case "$variant" in
    debug)
        task_suffix="Debug"
        ;;
    release)
        task_suffix="Release"
        required_signing_vars=(
            MC_RELEASE_KEYSTORE_PATH
            MC_RELEASE_STORE_PASSWORD
            MC_RELEASE_KEY_ALIAS
            MC_RELEASE_KEY_PASSWORD
        )
        for variable in "${required_signing_vars[@]}"; do
            if [[ -z "${!variable:-}" ]]; then
                echo "Release build requires $variable." >&2
                exit 2
            fi
        done
        if [[ ! -f "$MC_RELEASE_KEYSTORE_PATH" ]]; then
            echo "Release keystore does not exist: $MC_RELEASE_KEYSTORE_PATH" >&2
            exit 2
        fi
        ;;
    *)
        echo "MC_BUILD_VARIANT must be 'debug' or 'release'." >&2
        exit 2
        ;;
esac

gradle_args=(
    --no-daemon
    --no-parallel
    --max-workers=1
    --no-configuration-cache
    --no-build-cache
    -Pkotlin.compiler.execution.strategy=in-process
    -Pkotlin.incremental=false
)

if [[ "$(uname -m)" == "aarch64" || "$(uname -m)" == "arm64" ]]; then
    export QEMU_LD_PREFIX="${QEMU_LD_PREFIX:-/usr/x86_64-linux-gnu}"
fi

./scripts/validate-watchface.sh

./gradlew "${gradle_args[@]}" \
    :core:model:test \
    :core:domain:test \
    :core:data:testDebugUnitTest \
    :core:data:testReleaseUnitTest \
    :core:data:compileDebugAndroidTestKotlin \
    :core:sync:testDebugUnitTest \
    :phone:testDebugUnitTest \
    :wear:testDebugUnitTest \
    ":phone:lint$task_suffix" \
    ":wear:lint$task_suffix" \
    ":watchface:lint$task_suffix" \
    ":phone:assemble$task_suffix" \
    ":wear:assemble$task_suffix" \
    ":watchface:assemble$task_suffix"

./scripts/validate-watchface.sh \
    "$repo_root/watchface/build/outputs/apk/$variant/watchface-$variant.apk"

resolve_apksigner() {
    if command -v apksigner >/dev/null 2>&1; then
        command -v apksigner
        return
    fi

    local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -n "$sdk_root" ]]; then
        local candidate
        candidate="$(find "$sdk_root/build-tools" -maxdepth 2 -type f -name apksigner \
            -print 2>/dev/null | sort -V | tail -n 1)"
        if [[ -n "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return
        fi
    fi

    echo "apksigner was not found; install Android SDK Build Tools." >&2
    exit 2
}

apksigner="$(resolve_apksigner)"
phone_apk="$repo_root/phone/build/outputs/apk/$variant/phone-$variant.apk"
wear_apk="$repo_root/wear/build/outputs/apk/$variant/wear-$variant.apk"
watchface_apk="$repo_root/watchface/build/outputs/apk/$variant/watchface-$variant.apk"

for apk in "$phone_apk" "$wear_apk" "$watchface_apk"; do
    "$apksigner" verify --verbose "$apk"
done

certificate_digest() {
    "$apksigner" verify --print-certs "$1" |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
        head -n 1
}

phone_digest="$(certificate_digest "$phone_apk")"
wear_digest="$(certificate_digest "$wear_apk")"
if [[ -z "$phone_digest" || "$phone_digest" != "$wear_digest" ]]; then
    echo "Phone and Wear APK signing certificates do not match." >&2
    exit 1
fi
echo "Phone/Wear signing certificate SHA-256: $phone_digest"

mkdir -p "$repo_root/artifacts"
install -m 0644 \
    "$phone_apk" \
    "$repo_root/artifacts/metabolic-coach-phone-$variant.apk"
install -m 0644 \
    "$wear_apk" \
    "$repo_root/artifacts/metabolic-coach-wear-$variant.apk"
install -m 0644 \
    "$watchface_apk" \
    "$repo_root/artifacts/metabolic-coach-watchface-$variant.apk"

sha256sum "$repo_root"/artifacts/metabolic-coach-*-"$variant".apk
