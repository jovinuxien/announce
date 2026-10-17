#!/usr/bin/env bash
set -euo pipefail

# Open one git worktree per module in its own gnome-terminal window.
# Each worktree is on a branch feat/<short> off BASE_BRANCH (created if
# missing) and lives at ../announce-wt/<short>.
#
# Re-running is safe: existing worktrees and branches are reused.
#
# Override without editing this file:
#   MODULES="cqrs-reactive auction" ./open-fix-worktrees.sh
#   FRESH=1 ./open-fix-worktrees.sh      # deliver the assignment prompt instead of resuming
#   BASE_BRANCH=main ./open-fix-worktrees.sh

REPO_ROOT="$(git -C "$(dirname "$(readlink -f "$0")")" rev-parse --show-toplevel)"
WT_BASE="$(readlink -f "$REPO_ROOT/..")/announce-wt"
# The announce work lives on announce-jo-branch; `master` here is still
# upstream litemall's master and contains none of the announce modules.
BASE_BRANCH="${BASE_BRANCH:-announce-jo-branch}"
BRANCH_PREFIX="feat"

# Active worktrees (2026-10-17): crawler-inventory (new; launch it with
# FRESH=1 MODULES=crawler-inventory). The others are merged into announce-jo-branch
# and kept for follow-ups.
# Each name must have a matching '### Worktree: `<short>`' block in CLAUDE.md
# under '## Per-worktree tasks'; a short name with no block sends the session
# hunting for an assignment that does not exist.
read -r -a MODULES <<< "${MODULES:-order-reactive messaging gateway-api gateway-admin}"

if ! git -C "$REPO_ROOT" show-ref --verify --quiet "refs/heads/$BASE_BRANCH"; then
  echo "base branch '$BASE_BRANCH' does not exist" >&2
  exit 1
fi

mkdir -p "$WT_BASE"

for short in "${MODULES[@]}"; do
  branch="$BRANCH_PREFIX/$short"
  wt_path="$WT_BASE/$short"

  if [[ -d "$wt_path" ]]; then
    echo "[$short] worktree exists at $wt_path — reusing"
  elif git -C "$REPO_ROOT" show-ref --verify --quiet "refs/heads/$branch"; then
    echo "[$short] branch $branch exists — attaching worktree at $wt_path"
    git -C "$REPO_ROOT" worktree add "$wt_path" "$branch"
  else
    echo "[$short] creating branch $branch off $BASE_BRANCH at $wt_path"
    git -C "$REPO_ROOT" worktree add -b "$branch" "$wt_path" "$BASE_BRANCH"
  fi
done

for short in "${MODULES[@]}"; do
  wt_path="$WT_BASE/$short"
  branch="$BRANCH_PREFIX/$short"
  prompt="You are the '$short' worktree (branch $branch, working dir $wt_path). Read CLAUDE.md and locate the block '### Worktree: \`$short\`' under '## Per-worktree tasks'. Treat the Task and Acceptance lines in that block as your sole assignment. Per the repo's process rule, produce a written plan and get my approval before editing code."
  # Resume an interrupted session when one exists for this worktree (its
  # project dir is the worktree path with [/_.] flattened to '-'), otherwise
  # start fresh with the assignment prompt. The trailing `exec bash` keeps the
  # window open either way so a startup error is readable, not a vanish.
  # FRESH=1 skips the resume — use it at the start of a NEW assignment,
  # otherwise --continue reopens the finished previous conversation.
  proj_dir="$HOME/.claude/projects/$(echo "$wt_path" | tr '/_.' '---')"
  if [[ "${FRESH:-0}" != "1" ]] && ls "$proj_dir"/*.jsonl >/dev/null 2>&1; then
    echo "[$short] previous session found — resuming it"
    inner_cmd="claude --continue || claude $(printf %q "$prompt"); exec bash"
  else
    inner_cmd="claude $(printf %q "$prompt"); exec bash"
  fi
  # Every gnome-terminal window is hosted by ONE gnome-terminal-server process; when it
  # crashes (VTE 0.60 on Ubuntu 20.04 aborts in its accessibility code under Claude's
  # redraws, see the NO_AT_BRIDGE override in
  # ~/.config/systemd/user/gnome-terminal-server.service.d) every window dies at once.
  # With tmux installed the session runs detached inside tmux, survives the crash, and a
  # relaunch simply reattaches (`-A`) instead of starting a new conversation.
  if command -v tmux >/dev/null 2>&1; then
    if tmux has-session -t "=$short" 2>/dev/null; then
      echo "[$short] tmux session alive — reattaching"
    fi
    window_cmd=(tmux new-session -A -s "$short" -c "$wt_path" bash -c "$inner_cmd")
  else
    window_cmd=(bash -c "$inner_cmd")
  fi
  gnome-terminal --window \
    --working-directory="$wt_path" \
    --title="claude/$short" \
    -- "${window_cmd[@]}"
done
