#!/usr/bin/env bash
#
# End-to-end test for the helix-kmp control plane (stage P0).
#
# Scaffolds a throwaway Capability, two Features and a Cell, compiles and checks them with the
# repository's own quality gates, then removes every trace. The test fails if `git status --short`
# is not byte-identical before and after, so a failed run cannot leave sample modules behind.
#
# This is deliberately NOT wired into `verifyFast`: it mutates settings.gradle.kts and runs Gradle
# recursively, so it belongs in the tooling's own loop, not in every developer's inner loop.
# Run it by hand after changing the CLI or the templates:
#
#   tooling/helix-kmp/tests/run-tests.sh
#
# Portability: bash 3.2 (the macOS system bash).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$TOOL_DIR/../.." && pwd)"
CLI="$TOOL_DIR/helix-kmp"
SETTINGS_FILE="$REPO_ROOT/settings.gradle.kts"

# The working tree carries in-flight work and settings.gradle.kts is itself modified, so the file
# is restored from a saved copy. `git checkout -- settings.gradle.kts` would discard that work.
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/helix-kmp-tests.XXXXXX")"
SETTINGS_BACKUP="$WORK_DIR/settings.gradle.kts"
STATUS_BEFORE="$WORK_DIR/status-before.txt"
STATUS_AFTER="$WORK_DIR/status-after.txt"

SAMPLE_DIRS="
capability/sample-api
capability/sample-impl
feature/sample
feature/sample-linked
"

FAILURES=0
CLEANED=0

log() { echo "[helix-kmp-tests] $*"; }

fail() {
    echo "[helix-kmp-tests] FAIL: $*" >&2
    FAILURES=$((FAILURES + 1))
}

cleanup() {
    [ "$CLEANED" = "1" ] && return 0
    CLEANED=1
    log "cleaning up"
    local dir
    for dir in $SAMPLE_DIRS; do
        rm -rf "${REPO_ROOT:?}/$dir"
    done
    rmdir "$REPO_ROOT/capability" 2> /dev/null || true
    rmdir "$REPO_ROOT/feature" 2> /dev/null || true
    if [ -f "$SETTINGS_BACKUP" ]; then
        cp "$SETTINGS_BACKUP" "$SETTINGS_FILE"
    fi
}

trap cleanup EXIT INT TERM

expect_file() {
    if [ -f "$REPO_ROOT/$1" ]; then
        log "  ok: $1"
    else
        fail "expected file $1"
    fi
}

expect_grep() {
    local pattern="$1" file="$2"
    if grep -q "$pattern" "$REPO_ROOT/$file"; then
        log "  ok: $file matches /$pattern/"
    else
        fail "$file does not match /$pattern/"
    fi
}

# --------------------------------------------------------------------------
# 0. baseline
# --------------------------------------------------------------------------
cp "$SETTINGS_FILE" "$SETTINGS_BACKUP"
git -C "$REPO_ROOT" status --short > "$STATUS_BEFORE"
log "baseline recorded ($(wc -l < "$STATUS_BEFORE" | tr -d ' ') entries in git status --short)"

for dir in $SAMPLE_DIRS; do
    if [ -e "$REPO_ROOT/$dir" ]; then
        echo "[helix-kmp-tests] refusing to run: $dir already exists" >&2
        exit 1
    fi
done

# --------------------------------------------------------------------------
# 1. the CLI's own surface
# --------------------------------------------------------------------------
log "checking the CLI surface"
bash -n "$CLI" || fail "bash -n on the CLI"
"$CLI" --version > /dev/null || fail "--version"
"$CLI" help | grep -q "Not implemented here" || fail "help does not disclose the unimplemented commands"
if "$CLI" create feature "Bad Name" > /dev/null 2>&1; then
    fail "an invalid module name was accepted"
else
    log "  ok: invalid module names are rejected"
fi
"$CLI" verify --fast --affected --dry-run > /dev/null || fail "verify --dry-run"

# --------------------------------------------------------------------------
# 2. scaffolding
# --------------------------------------------------------------------------
log "create capability sample"
"$CLI" create capability sample
expect_file "capability/sample-api/build.gradle.kts"
expect_file "capability/sample-api/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/capability/sample/api/SampleCapability.kt"
expect_file "capability/sample-impl/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/capability/sample/impl/SampleCapabilityModule.kt"
expect_file "capability/sample-impl/src/commonTest/kotlin/dev/mayankmkh/basekmpproject/capability/sample/impl/SampleCapabilityImplTest.kt"

log "create feature sample"
"$CLI" create feature sample
expect_file "feature/sample/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/sample/api/SampleCell.kt"
expect_file "feature/sample/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/sample/api/SampleScreen.kt"
expect_file "feature/sample/src/jvmTest/kotlin/dev/mayankmkh/basekmpproject/feature/sample/SampleContentTest.kt"

log "create cell sample Detail"
"$CLI" create cell sample Detail
expect_file "feature/sample/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/sample/api/DetailCell.kt"
expect_grep "DetailViewModel" "feature/sample/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/sample/api/sampleFeatureModule.kt"

log "create feature sample-linked --capability sample"
"$CLI" create feature sample-linked --capability sample
expect_grep "projects.capability.sampleApi" "feature/sample-linked/build.gradle.kts"
expect_grep "SampleQueries" "feature/sample-linked/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/samplelinked/SampleLinkedViewModel.kt"

log "settings.gradle.kts"
expect_grep '":capability:sample-api",' "settings.gradle.kts"
expect_grep '":feature:sample-linked",' "settings.gradle.kts"
if diff <(grep -c '^    ":' "$SETTINGS_BACKUP") <(grep -c '^    ":' "$SETTINGS_FILE") > /dev/null; then
    fail "settings.gradle.kts gained no include entries"
else
    log "  ok: include entries were added"
fi

# `create` must refuse to overwrite.
if "$CLI" create feature sample > /dev/null 2>&1; then
    fail "create feature overwrote an existing module"
else
    log "  ok: create refuses to overwrite an existing module"
fi

# --------------------------------------------------------------------------
# 3. the generated modules must pass the repository's gates unmodified
# --------------------------------------------------------------------------
GRADLE_TASKS="${HELIX_KMP_TEST_TASKS:-
:capability:sample-api:jvmTest
:capability:sample-impl:jvmTest
:feature:sample:jvmTest
:feature:sample-linked:jvmTest
:capability:sample-api:spotlessCheck
:capability:sample-impl:spotlessCheck
:feature:sample:spotlessCheck
:feature:sample-linked:spotlessCheck
:capability:sample-api:detektAll
:capability:sample-impl:detektAll
:feature:sample:detektAll
:feature:sample-linked:detektAll
checkModuleGraph
checkHelixPolicySync
}"

log "gradle: $(echo $GRADLE_TASKS | tr '\n' ' ')"
# shellcheck disable=SC2086
if ( cd "$REPO_ROOT" && ./gradlew --console=plain $GRADLE_TASKS ); then
    log "  ok: the scaffolded modules compile, test, and pass spotless, detekt and the module graph"
else
    fail "Gradle rejected the scaffolded modules"
fi

# --------------------------------------------------------------------------
# 4. removal must restore the tree exactly
# --------------------------------------------------------------------------
cleanup
git -C "$REPO_ROOT" status --short > "$STATUS_AFTER"
if diff -u "$STATUS_BEFORE" "$STATUS_AFTER"; then
    log "  ok: git status --short is unchanged"
else
    fail "the working tree was not restored (diff above)"
fi

if [ "$FAILURES" = "0" ]; then
    log "PASS"
    rm -rf "$WORK_DIR"
    exit 0
fi
echo "[helix-kmp-tests] $FAILURES check(s) failed" >&2
echo "[helix-kmp-tests] artefacts kept in $WORK_DIR" >&2
exit 1
