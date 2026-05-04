#!/usr/bin/env bash
# build-release.sh — Build a release APK for TAK Marketplace (Play Store) submission.
#
# Usage:
#   ./scripts/build-release.sh [OPTIONS]
#
# Options:
#   --flavor <civ|mil|gov>     Product flavor to build (default: civ)
#   --keystore <path>          Path to the signing keystore file
#                              (default: app/build/android_keystore)
#   --key-alias <alias>        Key alias inside the keystore (default: wintec_mapping)
#   --key-pass <password>      Key password (default: tnttnt)
#   --store-pass <password>    Keystore password (default: tnttnt)
#   --clean                    Run 'gradlew clean' before assembling
#   --skip-tests               Skip unit tests
#   -h, --help                 Show this help message
#
# Prerequisites:
#   - JDK 17 on PATH (JAVA_HOME must point to JDK 17)
#   - app/libs/main.jar and app/libs/atak-gradle-takdev.jar present
#   - app/libs/atak-civ/android_keystore present (or app/build/android_keystore)
#   - local.properties at repo root with sdk.dir set
#   - PLUGIN_VERSION in app/build.gradle bumped for the new release
#
# What this script does:
#   1. Validates prerequisites
#   2. Optionally runs unit tests
#   3. Assembles <flavor>Release with R8/ProGuard enabled
#   4. Prints the APK path and TAK.gov submission instructions
#
# After building, upload the APK to the TAK Marketplace at:
#   https://tak.gov  →  Developers  →  Plugin Submission
#
# For CI/CD use the GitHub Actions release workflow instead:
#   git tag v1.x.y && git push origin v1.x.y
#
# See README.md for the full deployment guide.

set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────────────────
FLAVOR="civ"
KEYSTORE_PATH=""   # resolved after REPO_ROOT is set
KEY_ALIAS="wintec_mapping"
KEY_PASS="tnttnt"
STORE_PASS="tnttnt"
DO_CLEAN=false
SKIP_TESTS=false

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --flavor)
            FLAVOR="${2:?'--flavor requires an argument (civ|mil|gov)'}"
            shift 2
            ;;
        --keystore)
            KEYSTORE_PATH="${2:?'--keystore requires a file path'}"
            shift 2
            ;;
        --key-alias)
            KEY_ALIAS="${2:?'--key-alias requires an argument'}"
            shift 2
            ;;
        --key-pass)
            KEY_PASS="${2:?'--key-pass requires an argument'}"
            shift 2
            ;;
        --store-pass)
            STORE_PASS="${2:?'--store-pass requires an argument'}"
            shift 2
            ;;
        --clean)
            DO_CLEAN=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -h|--help)
            sed -n '2,/^set -/p' "$0" | grep '^#' | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown option: $1  (use --help for usage)" >&2
            exit 1
            ;;
    esac
done

# ── Validate flavor ───────────────────────────────────────────────────────────
case "$FLAVOR" in
    civ|mil|gov) ;;
    *)
        echo "Error: --flavor must be one of: civ, mil, gov (got '${FLAVOR}')" >&2
        exit 1
        ;;
esac

# ── Locate the repo root ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

if [[ -z "${KEYSTORE_PATH}" ]]; then
    KEYSTORE_PATH="${REPO_ROOT}/app/build/android_keystore"
fi

echo "==> ATAK-TAK-ML plugin — release build (TAK Marketplace)"
echo "    Flavor    : ${FLAVOR}"
echo "    Keystore  : ${KEYSTORE_PATH}"
echo "    Key alias : ${KEY_ALIAS}"
echo "    Repo      : ${REPO_ROOT}"

# ── Prerequisite checks ───────────────────────────────────────────────────────
if ! java -version 2>&1 | grep -qE 'version "17[."-]'; then
    echo ""
    echo "Warning: Java 17 is required. Current version:" >&2
    java -version 2>&1 | head -1 >&2
    echo "         Continuing anyway — Gradle will report the exact error if wrong." >&2
fi

if [[ ! -x "${REPO_ROOT}/gradlew" ]]; then
    echo "Error: gradlew not found at ${REPO_ROOT}/gradlew" >&2
    exit 1
fi

MAIN_JAR="${REPO_ROOT}/app/libs/main.jar"
if [[ ! -f "${MAIN_JAR}" ]]; then
    echo ""
    echo "Error: ATAK SDK main.jar not found at app/libs/main.jar" >&2
    echo "       Clone https://github.com/3rdEyeOperation/ATAK-SDK and copy main.jar into app/libs/" >&2
    exit 1
fi

TAKDEV_JAR=""
for candidate in "${REPO_ROOT}/app/libs/atak-gradle-takdev.jar" "${REPO_ROOT}/libs/atak-gradle-takdev.jar"; do
    if [[ -f "$candidate" ]]; then
        TAKDEV_JAR="$candidate"
        break
    fi
done
if [[ -z "${TAKDEV_JAR}" ]]; then
    echo ""
    echo "Error: atak-gradle-takdev.jar not found in app/libs/ or libs/." >&2
    echo "       Copy it from the ATAK-SDK repository." >&2
    exit 1
fi

if [[ ! -f "${REPO_ROOT}/local.properties" ]]; then
    echo ""
    echo "Error: local.properties not found." >&2
    echo "       Create it at the repo root with at least:" >&2
    echo "         sdk.dir=/path/to/your/Android/Sdk" >&2
    exit 1
fi

if [[ ! -f "${KEYSTORE_PATH}" ]]; then
    echo ""
    echo "Warning: Keystore not found at ${KEYSTORE_PATH}" >&2
    echo "         The build may fail. Copy android_keystore from the ATAK SDK to app/libs/atak-civ/." >&2
fi

echo "    SDK JAR   : ${MAIN_JAR}"
echo "    takdev    : ${TAKDEV_JAR}"

# ── Current PLUGIN_VERSION reminder ──────────────────────────────────────────
CURRENT_VERSION=$(grep -m1 'PLUGIN_VERSION' "${REPO_ROOT}/app/build.gradle" \
    | sed 's/.*PLUGIN_VERSION *= *"\(.*\)".*/\1/')
echo ""
echo "    Current PLUGIN_VERSION: ${CURRENT_VERSION}"
echo "    → Make sure this has been incremented since the last TAK.gov submission."

# ── Optional clean ────────────────────────────────────────────────────────────
if [[ "${DO_CLEAN}" == true ]]; then
    echo ""
    echo "==> Cleaning previous build output..."
    ./gradlew clean
fi

# ── Unit tests ────────────────────────────────────────────────────────────────
if [[ "${SKIP_TESTS}" == false ]]; then
    echo ""
    echo "==> Running unit tests..."
    ./gradlew test
    echo "    Unit tests passed."
fi

# ── Assemble release APK ──────────────────────────────────────────────────────
FLAVOR_CAP="${FLAVOR^}"
TASK="assemble${FLAVOR_CAP}Release"

echo ""
echo "==> Running: ./gradlew ${TASK} -Datak.proguard.mapping=/dev/null"
./gradlew "${TASK}" -Datak.proguard.mapping=/dev/null

# ── Locate output APK ─────────────────────────────────────────────────────────
APK_DIR="${REPO_ROOT}/app/build/outputs/apk/${FLAVOR}/release"
APK_FILE=$(find "${APK_DIR}" -name '*.apk' 2>/dev/null | head -1)

if [[ -z "${APK_FILE}" ]]; then
    echo ""
    echo "Error: Build succeeded but no APK found in ${APK_DIR}" >&2
    exit 1
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════════════════════"
echo "  Release build successful!"
echo ""
echo "  APK:     ${APK_FILE}"
echo "  Version: ${CURRENT_VERSION}"
echo ""
echo "  Next steps — TAK Marketplace submission:"
echo ""
echo "  1. Go to https://tak.gov → Developers → Plugin Submission"
echo "  2. Upload the APK above with the following details:"
echo "     Package name : com.atakmap.android.takml.plugin"
echo "     ATAK API ver : com.atakmap.app@5.7.0.CIV"
echo "     Min ATAK ver : 5.7.0"
echo ""
echo "  3. TAK.gov reviews the submission (≈ 1–4 weeks), re-signs the APK"
echo "     with the production certificate, and publishes to Google Play."
echo ""
echo "  See README.md for the full submission checklist."
echo "════════════════════════════════════════════════════════════════════"
