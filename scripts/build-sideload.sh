#!/usr/bin/env bash
# build-sideload.sh — Build a debug (sideload) APK for the ATAK-TAK-ML plugin.
#
# Usage:
#   ./scripts/build-sideload.sh [OPTIONS]
#
# Options:
#   --flavor <civ|mil|gov>   Product flavor to build (default: civ)
#   --install                Install the APK on the connected ADB device after build
#   --device <serial>        ADB device serial to target (implies --install)
#   --clean                  Run 'gradlew clean' before assembling
#   -h, --help               Show this help message
#
# Prerequisites:
#   - JDK 17 on PATH (JAVA_HOME must point to JDK 17)
#   - app/libs/main.jar and app/libs/atak-gradle-takdev.jar present
#   - app/libs/atak-civ/android_keystore present (or app/build/android_keystore)
#   - local.properties at repo root with sdk.dir set
#
# Examples:
#   ./scripts/build-sideload.sh
#   ./scripts/build-sideload.sh --flavor mil
#   ./scripts/build-sideload.sh --install
#   ./scripts/build-sideload.sh --install --device emulator-5554
#   ./scripts/build-sideload.sh --clean --install

set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────────────────
FLAVOR="civ"
DO_INSTALL=false
ADB_DEVICE=""
DO_CLEAN=false

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --flavor)
            FLAVOR="${2:?'--flavor requires an argument (civ|mil|gov)'}"
            shift 2
            ;;
        --install)
            DO_INSTALL=true
            shift
            ;;
        --device)
            ADB_DEVICE="${2:?'--device requires a serial number'}"
            DO_INSTALL=true
            shift 2
            ;;
        --clean)
            DO_CLEAN=true
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

# ── Locate the repo root (script may be called from any directory) ─────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

echo "==> ATAK-TAK-ML plugin — sideload build"
echo "    Flavor  : ${FLAVOR}"
echo "    Repo    : ${REPO_ROOT}"

# ── Prerequisite checks ───────────────────────────────────────────────────────
# Java 17
if ! java -version 2>&1 | grep -qE 'version "17[."-]'; then
    echo ""
    echo "Warning: Java 17 is required. Current version:" >&2
    java -version 2>&1 | head -1 >&2
    echo "         Set JAVA_HOME to a JDK 17 installation and ensure 'java' is on PATH." >&2
    echo "         Continuing anyway — Gradle will report the exact error if the version is wrong." >&2
fi

# Gradle wrapper
if [[ ! -x "${REPO_ROOT}/gradlew" ]]; then
    echo "Error: gradlew not found at ${REPO_ROOT}/gradlew" >&2
    exit 1
fi

# ATAK SDK main.jar
MAIN_JAR="${REPO_ROOT}/app/libs/main.jar"
if [[ ! -f "${MAIN_JAR}" ]]; then
    echo ""
    echo "Error: ATAK SDK main.jar not found at app/libs/main.jar" >&2
    echo "       Clone https://github.com/3rdEyeOperation/ATAK-SDK and copy main.jar into app/libs/" >&2
    exit 1
fi

# atak-gradle-takdev.jar
TAKDEV_JAR=""
for candidate in "${REPO_ROOT}/app/libs/atak-gradle-takdev.jar" "${REPO_ROOT}/libs/atak-gradle-takdev.jar"; do
    if [[ -f "$candidate" ]]; then
        TAKDEV_JAR="$candidate"
        break
    fi
done
if [[ -z "${TAKDEV_JAR}" ]]; then
    echo ""
    echo "Error: atak-gradle-takdev.jar not found." >&2
    echo "       Copy it to app/libs/ from the ATAK-SDK repository." >&2
    exit 1
fi

# local.properties
if [[ ! -f "${REPO_ROOT}/local.properties" ]]; then
    echo ""
    echo "Error: local.properties not found." >&2
    echo "       Create it at the repo root with at least:" >&2
    echo "         sdk.dir=/path/to/your/Android/Sdk" >&2
    exit 1
fi

echo "    SDK JAR : ${MAIN_JAR}"
echo "    takdev  : ${TAKDEV_JAR}"

# ── Build task name ───────────────────────────────────────────────────────────
FLAVOR_CAP="${FLAVOR^}"
TASK="assemble${FLAVOR_CAP}Debug"

# ── Optional clean ────────────────────────────────────────────────────────────
if [[ "${DO_CLEAN}" == true ]]; then
    echo ""
    echo "==> Cleaning previous build output..."
    ./gradlew clean
fi

# ── Assemble ──────────────────────────────────────────────────────────────────
echo ""
echo "==> Running: ./gradlew ${TASK}"
./gradlew "${TASK}"

# ── Locate output APK ─────────────────────────────────────────────────────────
APK_DIR="${REPO_ROOT}/app/build/outputs/apk/${FLAVOR}/debug"
APK_FILE=$(find "${APK_DIR}" -name '*.apk' 2>/dev/null | head -1)

if [[ -z "${APK_FILE}" ]]; then
    echo ""
    echo "Error: Build succeeded but no APK found in ${APK_DIR}" >&2
    exit 1
fi

echo ""
echo "==> Build successful!"
echo "    APK: ${APK_FILE}"

# ── Optional ADB install ──────────────────────────────────────────────────────
if [[ "${DO_INSTALL}" == true ]]; then
    if ! command -v adb &>/dev/null; then
        echo ""
        echo "Error: 'adb' not found on PATH. Install Android platform-tools and add to PATH." >&2
        exit 1
    fi

    ADB_ARGS=()
    if [[ -n "${ADB_DEVICE}" ]]; then
        ADB_ARGS+=("-s" "${ADB_DEVICE}")
        echo ""
        echo "==> Installing on device: ${ADB_DEVICE}"
    else
        echo ""
        echo "==> Installing on default ADB device..."
    fi

    adb "${ADB_ARGS[@]}" install -r "${APK_FILE}"
    echo ""
    echo "==> Installed successfully."
    echo "    Open ATAK → hamburger menu → Settings → Manage Plugins to activate."
fi
