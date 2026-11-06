#!/usr/bin/env bash
#
# Tests of the functions of selective-tests.sh that tell whether the changes of the workflow file
# matter to a job (`workflow_part`, `job_needs`, `workflow_relevant_part`, `workflow_changed`),
# and checks that ci.yml has the layout these functions rely on.
#
# Usage: .github/scripts/selective-tests-test.sh
#
# The layout check needs a YAML parser, yq or Python with PyYAML. It's skipped when there's none,
# unless SELECTIVE_TESTS_TEST_REQUIRE_YAML_PARSER is set to a non-empty value.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"

# shellcheck source=selective-tests.sh
source "$script_dir/selective-tests.sh"

failures=0

pass() {
  echo "ok: $*"
}

fail() {
  echo "FAIL: $*" >&2
  failures=$((failures + 1))
}

# Prints $1 with the first occurrence of $2 replaced by $3, fails if $2 isn't in $1
replace() {
  OLD="$2" NEW="$3" awk '
    { content = content $0 "\n" }
    END {
      old = ENVIRON["OLD"]
      i = index(content, old)
      if (!i) {
        print "Not found in the workflow: " old > "/dev/stderr"
        exit 1
      }
      printf "%s", substr(content, 1, i - 1) ENVIRON["NEW"] substr(content, i + length(old))
    }
  ' <<< "$1"
}

fixture="$(cat << 'EOF'
name: CI
on:
  pull_request:
env:
  FOO: bar
jobs:
  setup:
    runs-on: ubuntu-latest
    outputs:
      value: ${{ steps.value.outputs.value }}
    steps:
    - id: value
      run: echo value=1 >> "$GITHUB_OUTPUT"

  # Runs the tests
  tests:
    needs: setup
    runs-on: ubuntu-latest
    steps:
    - run: |
        cat > input.txt << INPUT
        # not a comment
        a

        b
        INPUT
        ./run-tests.sh input.txt
    - run: echo "multi-line
        # still part of the string"

  lint:
    runs-on: ubuntu-latest
    steps:
    # Lints
    - run: ./lint.sh

  other:
    needs: [setup, "tests"]
    runs-on: ubuntu-latest
    steps:
    - run: echo other

  flow:
    needs: [
      lint,
      'other'
    ]
    runs-on: ubuntu-latest
    steps:
    - run: echo flow

  release:
    needs:
      # after the others
      - other # see above
      - 'lint'
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    steps:
    - run: echo release
EOF
)"

all_jobs="setup tests lint other flow release"

# Checks that the jobs of $3 see the change from the fixture to $2 as relevant, and those of $4
# don't
expect() {
  local desc="$1" head="$2" changed="$3" same="$4" job expected actual
  for job in $changed $same; do
    case " $changed " in
      *" $job "*) expected=changed ;;
      *) expected=same ;;
    esac
    if [ "$(workflow_relevant_part "$fixture" "$job")" = "$(workflow_relevant_part "$head" "$job")" ]; then
      actual=same
    else
      actual=changed
    fi
    if [ "$actual" = "$expected" ]; then
      pass "$desc: $job $actual"
    else
      fail "$desc: expected $job to be $expected, got $actual"
    fi
  done
}

echo "# job_needs"
check_needs() {
  local job="$1" expected="$2" actual
  # unquoted on purpose, to normalize white spaces
  # shellcheck disable=SC2046
  actual="$(echo $(workflow_part job "$job" <<< "$fixture" | job_needs))"
  if [ "$actual" = "$expected" ]; then
    pass "needs of $job: '$actual'"
  else
    fail "needs of $job: expected '$expected', got '$actual'"
  fi
}
check_needs setup ""
check_needs tests "setup"
check_needs lint ""
check_needs other "setup tests"
check_needs flow "lint other"
check_needs release "other lint"
check_needs missing ""

echo "# workflow_part"
if [ -z "$(workflow_part job missing <<< "$fixture")" ]; then
  pass "no definition for a missing job"
else
  fail "got a definition for a missing job"
fi
expected_header="$(printf '%s\n' "name: CI" "on:" "  pull_request:" "env:" "  FOO: bar")"
if [ "$(workflow_part header <<< "$fixture")" = "$expected_header" ]; then
  pass "header"
else
  fail "unexpected header: $(workflow_part header <<< "$fixture")"
fi

echo "# workflow_relevant_part"
expect "no change" "$fixture" "" "$all_jobs"

# Lines that look like comments or are blank can be part of the value of a scalar
expect "blank line in a heredoc" \
  "$(replace "$fixture" $'        a\n\n        b\n' $'        a\n        b\n')" \
  "tests other flow release" "setup lint"
expect "comment-like line in a heredoc" \
  "$(replace "$fixture" "# not a comment" "# not a comment at all")" \
  "tests other flow release" "setup lint"
expect "comment-like line in a multi-line string" \
  "$(replace "$fixture" "# still part of the string" "# still part of it")" \
  "tests other flow release" "setup lint"
expect "trailing blank line of a job" \
  "$(replace "$fixture" $'    - run: echo flow\n' $'    - run: echo flow\n\n')" \
  "flow" "setup tests lint other release"
# Comments are kept too, so that the above can't be missed
expect "comment in a job" \
  "$(replace "$fixture" "# Lints" "# Lints the code")" \
  "lint flow release" "setup tests other"

expect "step of a job needed transitively" \
  "$(replace "$fixture" "echo value=1" "echo value=2")" \
  "setup tests other flow release" "lint"
expect "step of a job needed by none" \
  "$(replace "$fixture" "echo release" "echo release now")" \
  "release" "setup tests lint other flow"
expect "new job" \
  "$(replace "$fixture" $'  release:\n' \
    $'  new-job:\n    runs-on: ubuntu-latest\n    steps:\n    - run: echo new\n\n  release:\n')" \
  "new-job" "$all_jobs"
expect "renamed job" \
  "$(replace "$fixture" $'  lint:\n' $'  lint2:\n')" \
  "lint lint2 flow release" "setup tests other"
expect "workflow settings" \
  "$(replace "$fixture" "FOO: bar" "FOO: baz")" \
  "$all_jobs" ""

echo "# workflow_changed"
tmp_repo="$(mktemp -d)"
trap 'rm -rf "$tmp_repo"' EXIT
git_test() {
  git -C "$tmp_repo" -c user.name=Test -c user.email=test@example.com \
    -c commit.gpgsign=false "$@"
}
git_test init -q
echo "README" > "$tmp_repo/README"
git_test add README
git_test commit -q -m "No workflow"
no_workflow_commit="$(git_test rev-parse HEAD)"
mkdir -p "$tmp_repo/.github/workflows"
printf '%s\n' "$fixture" > "$tmp_repo/$workflow"
git_test add "$workflow"
git_test commit -q -m "Workflow"
base_commit="$(git_test rev-parse HEAD)"
replace "$(replace "$fixture" "echo release" "echo release now")" $'  release:\n' \
  $'  new-job:\n    runs-on: ubuntu-latest\n    steps:\n    - run: echo new\n\n  release:\n' \
  > "$tmp_repo/$workflow"

check_changed() {
  local job="$1" base="$2" expected="$3" actual output
  if output="$(cd "$tmp_repo" && GITHUB_JOB="$job" workflow_changed "$base" 2>&1)"; then
    actual=changed
  else
    actual=same
  fi
  if [ "$actual" = "$expected" ]; then
    pass "workflow_changed for job '$job': $actual"
  else
    fail "workflow_changed for job '$job': expected $expected, got $actual, output: $output"
  fi
}
check_changed tests "$base_commit" same
check_changed release "$base_commit" changed
check_changed new-job "$base_commit" changed
check_changed missing "$base_commit" same
# the whole file is compared without GITHUB_JOB
check_changed "" "$base_commit" changed
check_changed tests "$no_workflow_commit" changed

echo "# Layout of ci.yml"
# The keys workflow_part sees, and those a YAML parser sees, must be the same: other lines at
# their indentation levels would be mistaken for keys, or be lines of values spanning several
# lines (like under-indented quoted strings) that could end up in the wrong part of the workflow.
ci_yml="$repo_root/$workflow"
awk_top_level_keys="$(awk '/^[^ #]/ { sub(/:.*/, ""); print }' "$ci_yml" | sort)"
awk_job_ids="$(awk -v quotes="[\"']" '
  /^jobs:/ { in_jobs = 1; next }
  in_jobs && /^[^ #]/ { in_jobs = 0 }
  in_jobs && /^  [^ #]/ { sub(/^  /, ""); sub(/:.*/, ""); gsub(quotes, ""); print }
' "$ci_yml" | sort)"
parser=""
if command -v yq > /dev/null 2>&1 && yq --version 2>&1 | grep -q mikefarah; then
  parser=yq
  yaml_top_level_keys="$(yq 'keys | .[]' "$ci_yml" | sort)"
  yaml_job_ids="$(yq '.jobs | keys | .[]' "$ci_yml" | sort)"
elif command -v python3 > /dev/null 2>&1 && python3 -I -c "import yaml" > /dev/null 2>&1; then
  parser=PyYAML
  # PyYAML follows YAML 1.1, where the 'on' key is the boolean true
  yaml_keys() {
    python3 -I -c '
import sys, yaml
with open(sys.argv[1]) as f:
    workflow = yaml.safe_load(f)
node = workflow["jobs"] if sys.argv[2] == "jobs" else workflow
for key in node:
    print("on" if key is True else key)
' "$ci_yml" "$1" | tr -d '\r' | sort
  }
  yaml_top_level_keys="$(yaml_keys top-level)"
  yaml_job_ids="$(yaml_keys jobs)"
fi
if [ -z "$parser" ]; then
  if [ -n "${SELECTIVE_TESTS_TEST_REQUIRE_YAML_PARSER:-}" ]; then
    fail "no YAML parser (yq or PyYAML) to check the layout of ci.yml"
  else
    echo "skipped: no YAML parser (yq or PyYAML) to check the layout of ci.yml"
  fi
else
  if [ "$awk_top_level_keys" = "$yaml_top_level_keys" ]; then
    pass "top-level keys of ci.yml (checked with $parser)"
  else
    fail "top-level keys of ci.yml differ, awk: $(echo $awk_top_level_keys), $parser: $(echo $yaml_top_level_keys)"
  fi
  if [ "$awk_job_ids" = "$yaml_job_ids" ]; then
    pass "job ids of ci.yml (checked with $parser)"
  else
    fail "job ids of ci.yml differ, awk: $(echo $awk_job_ids), $parser: $(echo $yaml_job_ids)"
  fi
fi

if [ "$failures" -gt 0 ]; then
  echo "$failures failure(s)" >&2
  exit 1
fi
echo "All tests passed"
