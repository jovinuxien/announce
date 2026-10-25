#!/usr/bin/env bash
# Stop the services started by scripts/start-stack.sh (pid files in $STACK_HOME, default ~/.announce-stack).
# The docker/cqrs containers and MySQL stay up. With --all, also kill any announce service process of this
# user that was started by hand (announce-management, order, messaging, the two edge jars, their
# spring-boot:run parents). With --infra, also stop the docker/cqrs containers.
#
#   scripts/stop-stack.sh [--all] [--infra]
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STACK_HOME="${STACK_HOME:-$HOME/.announce-stack}"
PIDS="$STACK_HOME/pids"
ALL=0; INFRA=0
for arg in "$@"; do
  case "$arg" in
    --all) ALL=1 ;;
    --infra) INFRA=1 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

say() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }

stop_tree() { # pid: TERM the children (spring-boot:run forks the application), then the process itself
  local pid="$1"
  pkill -TERM -P "$pid" 2>/dev/null
  kill -TERM "$pid" 2>/dev/null
}

wait_gone() { # pids...
  local i
  for i in $(seq 1 30); do
    local left=0 p
    for p in "$@"; do kill -0 "$p" 2>/dev/null && left=1; done
    [ "$left" = 0 ] && return 0
    sleep 1
  done
  for p in "$@"; do kill -0 "$p" 2>/dev/null && { echo "    pid $p did not stop, killing"; kill -KILL "$p" 2>/dev/null; }; done
}

pids=()
for f in "$PIDS"/*.pid; do
  [ -f "$f" ] || continue
  name="$(basename "$f" .pid)"; pid="$(cat "$f")"
  if kill -0 "$pid" 2>/dev/null; then
    say "stopping $name (pid $pid)"
    children="$(pgrep -P "$pid" || true)"
    stop_tree "$pid"
    pids+=("$pid")
    for c in $children; do pids+=("$c"); done
  else
    echo "    $name was not running"
  fi
  rm -f "$f"
done
[ "${#pids[@]}" -gt 0 ] && wait_gone "${pids[@]}"

if [ "$ALL" = 1 ]; then
  say "stopping every announce service process of this user"
  # The bracket in each pattern keeps pkill from matching this script's own command line.
  for pat in '[A]nnounceGoodsManagement' '[a]nnounce-management-0.0.1-SNAPSHOT.jar' \
             '[O]rderServiceApplication' 'order-0.0.1-SNAPSHOT-[e]xec.jar' \
             '[M]essagingApplication' \
             '-pl ([a]nnounce-management|[o]rder|[m]essaging).*spring-boot:run' \
             'spring-boot:run.*-pl ([a]nnounce-management|[o]rder|[m]essaging)' \
             '[g]ateway-api-0.0.1-SNAPSHOT.jar' '[g]ateway-admin-0.0.1-SNAPSHOT.jar'; do
    pkill -TERM -u "$(id -u)" -f "$pat" 2>/dev/null && echo "    stopped: $pat"
  done
  sleep 3
  for pat in '[A]nnounceGoodsManagement' '[O]rderServiceApplication' '[M]essagingApplication' \
             '[g]ateway-api-0.0.1-SNAPSHOT.jar' '[g]ateway-admin-0.0.1-SNAPSHOT.jar'; do
    pkill -KILL -u "$(id -u)" -f "$pat" 2>/dev/null && echo "    killed: $pat"
  done
fi

if [ "$INFRA" = 1 ]; then
  say "stopping docker/cqrs"
  docker compose -f "$ROOT/docker/cqrs/docker-compose.yml" stop
fi

say "done"
for p in 8083 8085 8086 8090 8091; do
  ss -ltnH "sport = :$p" 2>/dev/null | grep -q . && echo "    note: port $p is still in use"
done
exit 0
