#!/usr/bin/env bash
# Start the announce stack on this machine for manual testing:
#
#   docker/cqrs (EventSourcingDB, Kafka, Elasticsearch, OCS)  <- started if a container is missing
#   MySQL announce_reactive_db on localhost:3306              <- must already run
#   announce-management  :8085   order :8083   messaging :8086
#   customer edge + SPA  :8090   admin edge + SPA :8091
#
# Jars are built on the first run (or with --build); messaging has no bootable jar and runs with
# spring-boot:run. Logs and pid files go to $STACK_HOME (default ~/.announce-stack). Stop with
# scripts/stop-stack.sh. Accounts, URLs and a test walk-through: doc/stack-test-guide.pdf.
#
#   scripts/start-stack.sh [--build] [--no-build] [--without-order] [--search=ocs|noop] [--skip-infra]
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STACK_HOME="${STACK_HOME:-$HOME/.announce-stack}"
LOGS="$STACK_HOME/logs"
PIDS="$STACK_HOME/pids"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JAVA_HOME/bin/java"
MVN="${MVN:-mvn}"

PORT_AM="${PORT_AM:-8085}"
PORT_ORDER="${PORT_ORDER:-8083}"
PORT_MSG="${PORT_MSG:-8086}"
PORT_CUSTOMER="${PORT_CUSTOMER:-8090}"   # 8080 is usually taken on this machine
PORT_ADMIN="${PORT_ADMIN:-8091}"

DB_USER="${DB_USER:-announce}"
DB_PASS="${DB_PASS:-Calliste_1006}"
DB_NAME="${DB_NAME:-announce_reactive_db}"

AM_JAR="$ROOT/announce-management/target/announce-management-0.0.1-SNAPSHOT.jar"   # repackaged in place (no classifier)
ORDER_JAR="$ROOT/order/target/order-0.0.1-SNAPSHOT-exec.jar"
GW_JAR="$ROOT/gateway-api/target/gateway-api-0.0.1-SNAPSHOT.jar"
GA_JAR="$ROOT/gateway-admin/target/gateway-admin-0.0.1-SNAPSHOT.jar"

INFRA_CONTAINERS=(announce_esdb announce_kafka announce_elasticsearch announce_ocs_indexer announce_ocs_searcher announce_ocs_suggest)
COMMON_FLAGS=(--eureka.client.enabled=false --spring.cloud.config.enabled=false --spring.cloud.discovery.enabled=false)

BUILD=auto          # auto: build what is missing; yes: rebuild everything; no: never build
WITH_ORDER=1
SEARCH_MODE=ocs     # ocs: index into docker/cqrs and search for real; noop: record index calls only
INFRA=1

for arg in "$@"; do
  case "$arg" in
    --build) BUILD=yes ;;
    --no-build) BUILD=no ;;
    --without-order) WITH_ORDER=0 ;;
    --search=ocs|--search=noop) SEARCH_MODE="${arg#--search=}" ;;
    --skip-infra) INFRA=0 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

say() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

port_in_use() { ss -ltnH "sport = :$1" 2>/dev/null | grep -q .; }

alive() { [ -f "$PIDS/$1.pid" ] && kill -0 "$(cat "$PIDS/$1.pid")" 2>/dev/null; }

# ---------------------------------------------------------------- preconditions
mkdir -p "$LOGS" "$PIDS"
[ -x "$JAVA" ] || die "no java at $JAVA (set JAVA_HOME to a Java 21)"

for s in announce-management order messaging gateway-api gateway-admin; do
  if alive "$s"; then die "$s is already running (pid $(cat "$PIDS/$s.pid")); run scripts/stop-stack.sh first"; fi
done

ports=("$PORT_AM" "$PORT_MSG" "$PORT_CUSTOMER" "$PORT_ADMIN")
[ "$WITH_ORDER" = 1 ] && ports+=("$PORT_ORDER")
for p in "${ports[@]}"; do
  if port_in_use "$p"; then
    ss -ltnp "sport = :$p" | tail -n +2 >&2
    die "port $p is already in use; stop that process or override PORT_* in the environment"
  fi
done

say "MySQL $DB_NAME on localhost:3306"
mysql -h127.0.0.1 -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e 'select 1' >/dev/null 2>&1 \
  || die "cannot reach MySQL database $DB_NAME as $DB_USER (see db-reactive/sql/build_announce_reactive_db.sh)"

if [ "$INFRA" = 1 ]; then
  say "docker/cqrs containers"
  running="$(docker ps --format '{{.Names}}')"
  missing=0
  for c in "${INFRA_CONTAINERS[@]}"; do
    grep -qx "$c" <<<"$running" || { echo "    $c is not running"; missing=1; }
  done
  if [ "$missing" = 1 ]; then
    say "starting docker/cqrs"
    docker compose -f "$ROOT/docker/cqrs/docker-compose.yml" up -d
  fi
  for c in announce_esdb announce_kafka announce_elasticsearch; do
    for _ in $(seq 1 60); do
      [ "$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null)" = healthy ] && break
      sleep 2
    done
    [ "$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null)" = healthy ] || die "$c did not become healthy"
  done
fi

# ---------------------------------------------------------------- build
need_backends=0; need_edges=0
if [ "$BUILD" = yes ]; then
  need_backends=1; need_edges=1
  rm -f "$AM_JAR" "$ORDER_JAR" "$GW_JAR" "$GA_JAR"
elif [ "$BUILD" = auto ]; then
  { [ -f "$AM_JAR" ] && { [ "$WITH_ORDER" = 0 ] || [ -f "$ORDER_JAR" ]; }; } || need_backends=1
  { [ -f "$GW_JAR" ] && [ -f "$GA_JAR" ]; } || need_edges=1
fi

if [ "$need_backends" = 1 ]; then
  # The whole dependency chain (db-reactive, cqrs-reactive, the api modules) is built in one reactor so the
  # services never pick up a stale SNAPSHOT another worktree installed into ~/.m2. announce-management's
  # repackage is bound to its webapp profile, whose npm step is skipped here.
  say "building the backends (first run takes a few minutes)"
  (cd "$ROOT" && "$MVN" -pl announce-management,order,messaging -am -P webapp -Dmaven.test.skip=true \
      -Dskip.npm=true -Dskip.installnodenpm=true package) > "$LOGS/build-backends.log" 2>&1 \
    || die "backend build failed, see $LOGS/build-backends.log"
fi
if [ "$need_edges" = 1 ]; then
  say "building the two edges with their SPAs (npm; first run downloads node and the packages)"
  (cd "$ROOT" && "$MVN" -pl gateway-api,gateway-admin -P webapp -DskipTests package) > "$LOGS/build-edges.log" 2>&1 \
    || die "edge build failed, see $LOGS/build-edges.log"
fi
[ -f "$AM_JAR" ] || die "missing $AM_JAR (run with --build)"
[ "$WITH_ORDER" = 0 ] || [ -f "$ORDER_JAR" ] || die "missing $ORDER_JAR (run with --build)"
[ -f "$GW_JAR" ] || die "missing $GW_JAR (run with --build)"
[ -f "$GA_JAR" ] || die "missing $GA_JAR (run with --build)"

# ---------------------------------------------------------------- start
start_jar() { # name jar args...
  local name="$1" jar="$2"; shift 2
  say "starting $name"
  (cd "$(dirname "$jar")" && nohup "$JAVA" -jar "$jar" "$@" > "$LOGS/$name.log" 2>&1 & echo $! > "$PIDS/$name.pid")
}

start_messaging() {
  say "starting messaging (spring-boot:run; no bootable jar for this module)"
  local args="--server.port=$PORT_MSG ${COMMON_FLAGS[*]}"
  (cd "$ROOT" && nohup "$MVN" -pl messaging -Dmaven.test.skip=true spring-boot:run \
      -Dspring-boot.run.arguments="$args" > "$LOGS/messaging.log" 2>&1 & echo $! > "$PIDS/messaging.pid")
}

wait_http() { # name url timeout-seconds accept-regex
  local name="$1" url="$2" timeout="$3" accept="$4" code=000 i
  for i in $(seq 1 "$timeout"); do
    alive "$name" || die "$name died while starting, see $LOGS/$name.log"
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$url" || true)"
    if [[ "$code" =~ $accept ]]; then printf '    %s answers %s after %ss\n' "$name" "$code" "$i"; return 0; fi
    sleep 1
  done
  die "$name did not answer on $url within ${timeout}s (last code $code), see $LOGS/$name.log"
}

ROUTES=(--announce.routes.announce-management="http://localhost:$PORT_AM"
        --announce.routes.order="http://localhost:$PORT_ORDER"
        --announce.routes.messaging="http://localhost:$PORT_MSG"
        --announce.routes.messaging-ws="ws://localhost:$PORT_MSG")

start_jar announce-management "$AM_JAR" --server.port="$PORT_AM" --announce.search.mode="$SEARCH_MODE" "${COMMON_FLAGS[@]}"
[ "$WITH_ORDER" = 1 ] && start_jar order "$ORDER_JAR" --server.port="$PORT_ORDER" "${COMMON_FLAGS[@]}"
start_messaging
start_jar gateway-api "$GW_JAR" --server.port="$PORT_CUSTOMER" "${ROUTES[@]}" "${COMMON_FLAGS[@]}"
start_jar gateway-admin "$GA_JAR" --server.port="$PORT_ADMIN" "${ROUTES[@]}" "${COMMON_FLAGS[@]}"

say "waiting for the services"
# The backends' /actuator/health reports DOWN (503) locally because the Redis and Kafka-binder indicators
# cannot reach their hosts; any HTTP answer means the service is up.
wait_http announce-management "http://localhost:$PORT_AM/actuator/health" 180 '^(200|503)$'
[ "$WITH_ORDER" = 1 ] && wait_http order "http://localhost:$PORT_ORDER/actuator/health" 180 '^(200|503)$'
wait_http messaging "http://localhost:$PORT_MSG/actuator/health" 300 '^(200|503)$'
wait_http gateway-api "http://localhost:$PORT_CUSTOMER/" 120 '^200$'
wait_http gateway-admin "http://localhost:$PORT_ADMIN/" 120 '^200$'

cat <<EOF

  Customer SPA   http://localhost:$PORT_CUSTOMER/          member user123 / user123
  Admin SPA      http://localhost:$PORT_ADMIN/          staff  admin123 / admin123 (super)
  Backends       announce-management :$PORT_AM   order :$PORT_ORDER   messaging :$PORT_MSG   (401 without X-Service-Token: go through an edge)
  Event store UI http://localhost:3000/  (token: secret)
  Logs           $LOGS      Stop: scripts/stop-stack.sh

  Accounts, URLs and the test walk-through: doc/stack-test-guide.pdf
EOF
