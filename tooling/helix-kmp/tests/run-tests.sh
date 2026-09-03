#!/usr/bin/env bash
#
# End-to-end test for the helix-kmp control plane (stage P1).
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
AGENTS_FILE="$REPO_ROOT/AGENTS.md"

# The working tree carries in-flight work and settings.gradle.kts is itself modified, so the file
# is restored from a saved copy. `git checkout -- settings.gradle.kts` would discard that work.
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/helix-kmp-tests.XXXXXX")"
SETTINGS_BACKUP="$WORK_DIR/settings.gradle.kts"
AGENTS_BACKUP="$WORK_DIR/AGENTS.md"
STATUS_BEFORE="$WORK_DIR/status-before.txt"
STATUS_AFTER="$WORK_DIR/status-after.txt"
POSTS_PROBE="$REPO_ROOT/feature/posts/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/posts/PostContent.kt"
POSTS_PROBE_BACKUP="$WORK_DIR/PostContent.kt"

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
    if [ -f "$AGENTS_BACKUP" ]; then
        cp "$AGENTS_BACKUP" "$AGENTS_FILE"
    fi
    if [ -f "$POSTS_PROBE_BACKUP" ]; then
        cp "$POSTS_PROBE_BACKUP" "$POSTS_PROBE"
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

expect_no_grep() {
    local pattern="$1" file="$2"
    if grep -q "$pattern" "$REPO_ROOT/$file"; then
        fail "$file must not match /$pattern/"
    else
        log "  ok: $file does not match /$pattern/"
    fi
}

# The canonical Cell shape (source-of-truth §30.4): fills width, never owns its size or scrolling,
# and fails fast when a host reuses a placement key for a different id. The templates are
# hand-derived from `:feature:posts`, so both the reference and the generated Cells are checked.
expect_cell_shape() {
    local file="$1" id="$2"
    expect_no_grep "fillMaxSize" "$file"
    expect_no_grep "verticalScroll" "$file"
    expect_grep "contentPadding: PaddingValues = PaddingValues()," "$file"
    expect_grep "check(viewModel.$id == $id)" "$file"
}

# --------------------------------------------------------------------------
# 0. baseline
# --------------------------------------------------------------------------
cp "$SETTINGS_FILE" "$SETTINGS_BACKUP"
cp "$AGENTS_FILE" "$AGENTS_BACKUP"
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
"$CLI" help | grep -q "extract and migrate" || fail "help does not disclose the P2 commands"
if "$CLI" create feature "Bad Name" > /dev/null 2>&1; then
    fail "an invalid module name was accepted"
else
    log "  ok: invalid module names are rejected"
fi
"$CLI" verify --fast --affected --dry-run > /dev/null || fail "verify --dry-run"

# Isolate the affected-module mapper from unrelated in-flight working-tree edits by putting a
# minimal Git status producer first on PATH. The real file is still changed and restored here; the
# shim only makes that one change the mapper's complete input.
log "checking affected verification maps one Feature file to one module lifecycle"
FAKE_BIN="$WORK_DIR/bin"
FAKE_GIT="$FAKE_BIN/git"
mkdir -p "$FAKE_BIN"
printf '%s\n' \
    '#!/usr/bin/env bash' \
    'case "$*" in' \
    '    *" diff --name-only "*) exit 0 ;;' \
    '    *" status --porcelain"*) echo " M feature/posts/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/posts/PostContent.kt"; exit 0 ;;' \
    'esac' \
    'exec "$HELIX_KMP_REAL_GIT" "$@"' > "$FAKE_GIT"
chmod +x "$FAKE_GIT"
cp "$POSTS_PROBE" "$POSTS_PROBE_BACKUP"
printf '\n' >> "$POSTS_PROBE"
AFFECTED_DRY_RUN="$(
    PATH="$FAKE_BIN:$PATH" HELIX_KMP_REAL_GIT="$(command -v git)" \
        "$CLI" verify --fast --affected --base HEAD --dry-run
)" || fail "affected Feature dry-run"
cp "$POSTS_PROBE_BACKUP" "$POSTS_PROBE"
AFFECTED_MODULE_TASKS="$(
    printf '%s\n' "$AFFECTED_DRY_RUN" | tr ' ' '\n' | grep ':verifyFastModule$' || true
)"
if [ "$AFFECTED_MODULE_TASKS" = ":feature:posts:verifyFastModule" ]; then
    log "  ok: only :feature:posts:verifyFastModule was selected"
else
    fail "affected Feature dry-run selected unexpected module tasks: $AFFECTED_MODULE_TASKS"
fi

# --------------------------------------------------------------------------
# 2. fast P1 graph-backed surface (no scaffolding)
# --------------------------------------------------------------------------
log "refreshing the schema-2 graph for fast P1 command checks"
if (cd "$REPO_ROOT" && ./gradlew checkModuleGraph -q); then
    log "  ok: graph refreshed"
else
    fail "could not refresh module graph"
fi

GRAPH_JSON="$WORK_DIR/graph.json"
IMPACT_JSON="$WORK_DIR/impact.json"
DOCTOR_JSON="$WORK_DIR/doctor.json"
DOCTOR_TEXT="$WORK_DIR/doctor.txt"
CONTEXT_TEXT="$WORK_DIR/context.txt"
CONTEXT_JSON="$WORK_DIR/context.json"
GALLERY_TEXT="$WORK_DIR/gallery.txt"
GALLERY_JSON="$WORK_DIR/gallery.json"

"$CLI" graph --json --no-refresh > "$GRAPH_JSON" || fail "graph --json"
python3 -c 'import json,sys; value=json.load(open(sys.argv[1])); assert value["schema"] == 2; assert len(value["nodes"]) == 19; assert "reverseEdges" in value' "$GRAPH_JSON" || fail "graph JSON shape or node count"

"$CLI" impact :capability:posts-api --json --no-refresh > "$IMPACT_JSON" || fail "impact module"
python3 -c 'import json,sys; value=json.load(open(sys.argv[1])); assert ":feature:posts" in value["directReverseDependents"]; assert ":app:shared" in value["directReverseDependents"]' "$IMPACT_JSON" || fail "impact module reverse dependents"
"$CLI" impact capability/posts-api/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/capability/posts/api/PostsCapability.kt --json --no-refresh | grep -q ':capability:posts-api' || fail "impact file resolution"
"$CLI" impact PostsQueries --json --no-refresh | grep -q ':capability:posts-api' || fail "impact symbol resolution"

"$CLI" doctor --json --no-refresh > "$DOCTOR_JSON" || fail "doctor clean-tree exit"
python3 -c 'import json,sys; assert json.load(open(sys.argv[1]))["findings"] == []' "$DOCTOR_JSON" || fail "doctor clean findings"
"$CLI" doctor --no-refresh --explain > "$DOCTOR_TEXT" || fail "doctor explanatory text"
grep -q 'git co-change with a Feature:' "$DOCTOR_TEXT" || fail "doctor git co-change evidence"
if grep -q 'git history unavailable' "$DOCTOR_TEXT"; then
    fail "doctor skipped available git history"
fi

"$CLI" context :feature:posts --files-only --no-refresh > "$CONTEXT_TEXT" || fail "context files-only"
for heading in "TASK TARGET" "PUBLIC ENTRY POINTS" "GRAPH SLICE" "RULES" "SOURCE" "DEPENDENCY CONTEXT" "ACCEPTANCE" "VERIFY"; do
    grep -qx "$heading" "$CONTEXT_TEXT" || fail "context heading $heading"
done
grep -q 'tooling/helix-kmp/helix-kmp verify --fast --affected' "$CONTEXT_TEXT" || fail "context verify command"
if grep -q "{'" "$CONTEXT_TEXT"; then
    fail "context text contains a Python dict repr"
fi
if grep -Eq '^  [A-Za-z]+\.kt: $' "$CONTEXT_TEXT"; then
    fail "context text contains an empty public declaration"
fi
if grep -q 'rememberUpdatedState' "$CONTEXT_TEXT"; then
    fail "context public entries include a function-local declaration"
fi
"$CLI" context :feature:posts --files-only --no-refresh --json > "$CONTEXT_JSON" || fail "context files-only JSON"
python3 -c 'import json,sys; value=json.load(open(sys.argv[1])); expected={"TASK TARGET","PUBLIC ENTRY POINTS","GRAPH SLICE","RULES","SOURCE","DEPENDENCY CONTEXT","ACCEPTANCE","VERIFY"}; assert set(value["sections"]) == expected' "$CONTEXT_JSON" || fail "context JSON shape or section count"

"$CLI" gallery --no-refresh > "$GALLERY_TEXT" || fail "gallery"
grep -q 'PostDetailCell' "$GALLERY_TEXT" || fail "gallery PostDetailCell"
grep -q 'PostsFeatureFixtures.kt:detail' "$GALLERY_TEXT" || fail "gallery module-local detail fixture"
"$CLI" gallery --no-refresh --json > "$GALLERY_JSON" || fail "gallery JSON"
python3 -c 'import json,sys; value=json.load(open(sys.argv[1])); fixtures=[fixture for module in value["modules"] for cell in module["cells"] if cell["name"] == "PostDetailCell" for fixture in cell["fixtureStates"]]; assert any(fixture["file"] == "PostsFeatureFixtures.kt" and fixture["name"] == "detail" and fixture["visibility"] == "internal" for fixture in fixtures)' "$GALLERY_JSON" || fail "gallery internal fixture JSON visibility"

"$CLI" verify --agents > /dev/null || fail "agent instruction no-drift check"
python3 -c 'from pathlib import Path; p=Path(__import__("sys").argv[1]); p.write_text(p.read_text().replace("control plane P1", "control plane P0", 1))' "$AGENTS_FILE"
if "$CLI" verify --agents > "$WORK_DIR/agents.diff" 2>&1; then
    fail "agent instruction drift was accepted"
elif grep -q '^--- AGENTS.md' "$WORK_DIR/agents.diff"; then
    log "  ok: agent instruction drift produces a unified diff"
else
    fail "agent instruction drift did not produce a unified diff"
fi
cp "$AGENTS_BACKUP" "$AGENTS_FILE"

# --------------------------------------------------------------------------
# 3. scaffolding
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
expect_cell_shape "feature/sample/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/sample/api/SampleCell.kt" id
expect_file "feature/sample/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/sample/api/SampleScreen.kt"
expect_file "feature/sample/src/jvmTest/kotlin/dev/mayankmkh/basekmpproject/feature/sample/SampleContentTest.kt"

log "create cell sample Detail"
"$CLI" create cell sample Detail
expect_file "feature/sample/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/sample/api/DetailCell.kt"
expect_cell_shape "feature/sample/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/sample/api/DetailCell.kt" id
expect_cell_shape "feature/posts/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/posts/api/PostDetailCell.kt" postId
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
# 4. the generated modules must pass the repository's gates unmodified
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

# A denied edge must remain diagnosable from the report even though the Gradle producer exits 1.
log "doctor explains a scaffolded denied edge"
SAMPLE_BUILD="$REPO_ROOT/feature/sample/build.gradle.kts"
SAMPLE_BUILD_BACKUP="$WORK_DIR/sample-build.gradle.kts"
cp "$SAMPLE_BUILD" "$SAMPLE_BUILD_BACKUP"
python3 -c 'from pathlib import Path; import sys; p=Path(sys.argv[1]); text=p.read_text(); needle="api(projects.foundation.presentation)"; p.write_text(text.replace(needle, needle + "\n                implementation(projects.capability.sampleImpl)", 1))' "$SAMPLE_BUILD"
(cd "$REPO_ROOT" && ./gradlew checkModuleGraph -q --continue) > /dev/null 2>&1 || true
if "$CLI" doctor --no-refresh --explain > "$WORK_DIR/doctor-negative.txt" 2>&1; then
    fail "doctor returned zero for a denied edge"
else
    DOCTOR_STATUS=$?
    [ "$DOCTOR_STATUS" = "1" ] || fail "doctor denied-edge exit was $DOCTOR_STATUS instead of 1"
fi
grep -q 'DEP-ROLE-DENIED' "$WORK_DIR/doctor-negative.txt" || fail "doctor missing denied-edge rule ID"
grep -q 'Approved repair:' "$WORK_DIR/doctor-negative.txt" || fail "doctor missing approved repair"
cp "$SAMPLE_BUILD_BACKUP" "$SAMPLE_BUILD"
(cd "$REPO_ROOT" && ./gradlew checkModuleGraph -q) || fail "graph did not recover after denied-edge test"

# --------------------------------------------------------------------------
# 5. removal must restore the tree exactly
# --------------------------------------------------------------------------
cleanup
(cd "$REPO_ROOT" && ./gradlew checkModuleGraph -q) || fail "could not restore the 19-node graph report"
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
