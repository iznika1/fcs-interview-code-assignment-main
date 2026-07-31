#!/usr/bin/env bash
# Opens a PR for $1 against master, unless it has no commits or already has one open.
# Body is generated from the branch's actual commits and changed files.
set -euo pipefail

BRANCH="$1"
BASE="master"

if [ "$BRANCH" = "$BASE" ]; then
  echo "skip $BRANCH: is the base branch"
  exit 0
fi

git fetch origin "$BASE" "$BRANCH" --quiet

AHEAD=$(git rev-list --count "origin/$BASE..origin/$BRANCH")
if [ "$AHEAD" -eq 0 ]; then
  echo "skip $BRANCH: 0 commits ahead of $BASE"
  exit 0
fi

EXISTING=$(gh pr list --head "$BRANCH" --state open --json number --jq '.[0].number // empty')
if [ -n "$EXISTING" ]; then
  echo "skip $BRANCH: PR #$EXISTING already open"
  exit 0
fi

TITLE=$(git log -1 --pretty=%s "origin/$BRANCH")

COMMITS=$(git log --reverse --pretty='- %s' "origin/$BASE..origin/$BRANCH")
FILES=$(git diff --name-only "origin/$BASE...origin/$BRANCH" | sed 's|^|- `|; s|$|`|')

BODY=$(cat <<EOF
## Lane

\`$BRANCH\` → \`$BASE\` ($AHEAD commit(s))

## Commits

$COMMITS

## Files changed

$FILES

---

### Reviewer checklist

- [ ] Changes stay inside this lane's declared file ownership (WORKPLAN.md §11)
- [ ] No edits to the frozen zone: \`pom.xml\`, \`import.sql\`, \`application.properties\`, \`warehouse-openapi.yaml\`, \`domain/ports/**\`, \`domain/models/**\`
- [ ] No new Maven dependencies (contract C9)
- [ ] Domain layer imports no \`jakarta.ws.rs\` / \`jakarta.persistence\` (contract C5)
- [ ] Acceptance rows owned by this lane are green, or a red is explained

_Opened automatically by the Auto PR workflow._
EOF
)

gh pr create --base "$BASE" --head "$BRANCH" --title "$TITLE" --body "$BODY"
echo "opened PR for $BRANCH"
