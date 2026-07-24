#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tools_dir="${XDG_CACHE_HOME:-$HOME/.cache}/metabolic-coach/wff-tools"
validator="$tools_dir/wff-validator.jar"
memory_tool="$tools_dir/memory-footprint.jar"
watchface_xml="$repo_root/watchface/src/main/res/raw/watchface.xml"
watchface_apk="${1:-}"

validator_url="https://github.com/google/watchface/releases/download/latest/wff-validator.jar"
validator_sha256="3a10def0521ab97f41ab1b7e27a35649370af51580603b5bf656604d88f1aa29"
memory_url="https://github.com/google/watchface/releases/download/latest/memory-footprint.jar"
memory_sha256="9aab2dc1c3e9694afaf58ef0f504d376dcaf7c1db6fd2505e3a66c6081fee44a"

mkdir -p "$tools_dir"

download_verified() {
    local url="$1"
    local expected_sha256="$2"
    local output="$3"

    if [[ -f "$output" ]] &&
        echo "$expected_sha256  $output" | sha256sum --check --status; then
        return
    fi

    curl --fail --location --retry 3 "$url" --output "$output"
    echo "$expected_sha256  $output" | sha256sum --check
}

download_verified "$validator_url" "$validator_sha256" "$validator"
java -jar "$validator" 4 --stop-on-fail "$watchface_xml"

if [[ -n "$watchface_apk" ]]; then
    if [[ ! -f "$watchface_apk" ]]; then
        echo "Watch-face APK not found: $watchface_apk" >&2
        exit 1
    fi
    if unzip -Z1 "$watchface_apk" | grep -Eq '^classes([0-9]+)?\.dex$'; then
        echo "WFF APK must be resource-only but contains executable DEX code." >&2
        exit 1
    fi
    download_verified "$memory_url" "$memory_sha256" "$memory_tool"
    java -jar "$memory_tool" \
        --watch-face "$watchface_apk" \
        --schema-version 4 \
        --ambient-limit-mb 10 \
        --active-limit-mb 100 \
        --apply-v1-offload-limitations \
        --estimate-optimization
fi
