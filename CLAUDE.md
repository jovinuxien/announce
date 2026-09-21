# CLAUDE.md — announce-reactive

## Project

Classifieds / marketplace app (blocket.se, dba.dk, tradera.com style) being
carved out of a litemall fork. Java 21, Spring Boot 3.5.11, Spring Cloud
2025.0.3, WebFlux, reactive MyBatis over R2DBC (MySQL), OpenCQRS 1.0.0 on
EventSourcingDB, DDD bounded contexts (one Maven module per context).

Build facts:
- Parent is `org.joviste:announce:0.0.1-SNAPSHOT`. Run `mvn -N install` once
  so child modules resolve the parent, then build modules with `mvn -pl <module>`.
- Compile with `mvn -P '!webapp' -DskipTests compile`; the announce-management
  `webapp` profile runs `npm run build` and fails locally.
- Modules that compile: config, eureka, gateway, db-reactive, cqrs-reactive,
  announce-management. core, auth-server and order still carry legacy Boot 2 code.
- Tests in db-reactive and announce-management need the live MySQL database
  `announce_reactive_db` (user `announce`) on localhost:3306. Never touch the
  `litemall` database on the same server.
- `java` on PATH is 11; Maven runs on `/usr/lib/jvm/java-21-openjdk-amd64`.
- Design notes: `doc/announce-management-context.md`, `doc/auction-chat-schema.md`.

Process rule: in a worktree session, write a plan and get approval before
editing code. Commit only when asked. Never commit `node/`, `node_modules/`
or `target/`.

## Per-worktree tasks

> Worktrees are launched with `open-fix-worktrees.sh`. Each Claude session
> opens in `../announce-wt/<short>` on branch `feat/<short>` and is told to
> read the matching `### Worktree: <short>` block below as its sole task.
> Edit the Task / Acceptance lines to redirect a worktree.

### Worktree: `cqrs-reactive` — Lagom-style reactive CQRS framework module (2026-09-21)

- **Task:** grow `cqrs-reactive` into the framework every business service is
  built on. Keep it ONE Maven module with packages `command`, `readside`,
  `messaging`, `api` and `observability`, plus a sibling `cqrs-reactive-test`
  module for the test kit. Do not split into more modules yet.
  1. `command`: keep `ReactiveCommandRouter`; add subject conventions
     (`/announce/{id}`), command-ID idempotency, and error mapping to WebFlux
     problem details.
  2. `readside`: reactive projection adapter over OpenCQRS event handling
     processors (`com.opencqrs.framework.eventhandler`), so a projection can
     return `Mono`/`Flux`; an R2DBC-backed `ProgressTracker` storing the
     checkpoint in MySQL next to the read model; replay/rebuild support.
  3. `messaging`: an event handler group that maps internal events to
     versioned public events and publishes through Spring Cloud Stream
     `StreamBridge` (Kafka); a subscriber helper for consuming another
     service's topic; at-least-once semantics documented.
  4. `api`: factory turning an `@HttpExchange` interface into a discovered,
     load-balanced, Resilience4j-wrapped `WebClient`.
  5. `observability`: correlation and causation IDs in event metadata and
     Kafka headers, wired to Micrometer tracing.
  6. `cqrs-reactive-test`: given/when/then command fixture, projection
     harness, Testcontainers for EventSourcingDB and Kafka.
  7. `docker/` compose file with EventSourcingDB and Kafka for local dev.
  Public contracts (DTOs, public events, `@HttpExchange` interfaces) belong in
  a per-service `<name>-api` module, NOT in the framework.
- **Acceptance:** `mvn -pl cqrs-reactive,cqrs-reactive-test test` passes with
  Testcontainers only (no live MySQL, no running EventSourcingDB); one sample
  aggregate + projection + topic in `cqrs-reactive-test` exercises the whole
  path end to end; announce-management still compiles against the module;
  `doc/cqrs-reactive.md` explains how a new service uses each package.
