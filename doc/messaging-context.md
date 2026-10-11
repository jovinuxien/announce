# The messaging context: chat between members and with staff

Status: implemented 2026-09-26 (worktree `messaging`). Modules `messaging` (service, port 8086) and
`messaging-api` (published language). Built on `cqrs-reactive` (`doc/cqrs-reactive.md`) and the
`db-reactive` mappers; the contract with the order context is in `doc/order-context.md` §9.

## 1. What it is for

A buyer asks a seller about an announce and may propose a price; a member asks the support staff for
help and waits in a queue until a staff member picks the conversation up; staff broadcast bulletins to
their dashboards. Nothing here moves money: an accepted offer is published as `OfferAcceptedV1` and the
order context turns it into a deal.

## 2. Aggregate `Conversation` (`/conversation/{id}`)

| Command | Who | Rule | Event |
|---|---|---|---|
| OpenListingConversation(announceId, buyerId, sellerId) | buyer | id is `nameUUID("listing:" + announceId + ":" + buyerId)`, so the pair has one conversation; a second open returns it (no event); buyer != seller | ConversationOpened (OPEN, participants BUYER + SELLER) |
| OpenSupportConversation(memberId, subject) | member | | ConversationOpened (WAITING, participant MEMBER) |
| JoinConversation(staffId) | staff | idempotent; WAITING -> OPEN | ParticipantJoined(CSR, status) |
| PostMessage(clientMsgId, sender, contentType, content, attachmentUrl, amountCents, currency) | participant or SYSTEM | conversation writable (WAITING or OPEN); sender is a participant; TEXT needs text (<= `messaging.max-text-length`), IMAGE an URL, OFFER a positive amount and a currency and only the buyer of a LISTING conversation while no offer is accepted; a repeated `clientMsgId` answers with the original message and writes nothing | MessagePosted(messageId, seq, ...) |
| AcceptOffer / DeclineOffer | seller | there is an open offer (the latest OFFER message; a newer offer supersedes the previous one) | OfferAccepted / OfferDeclined |
| CloseConversation(by) | participant or staff | idempotent; a blocked conversation stays blocked | ConversationClosed |
| BlockConversation(staffId, reason) | staff | idempotent | ConversationBlocked |

Rejections are `CommandRejectedException` with a stable code: `not-participant` (403), `not-buyer` (403),
`not-seller` (403), `not-staff` (403), `buyer-is-seller` (409), `conversation-closed` (409),
`conversation-blocked` (409), `offer-not-allowed` (409), `offer-already-accepted` (409), `no-open-offer`
(409); malformed input is 400. The framework maps them to RFC 7807 problem details.

MarkRead is deliberately not a command: a read marker is a read-model write (`last_read_seq` on the
participant row), see §4.

## 3. Read model (`db-reactive/sql/announce_messaging.sql`)

| Table | Key | Written by |
|---|---|---|
| `announce_conversation` | `id` varchar(36) | ConversationOpened (upsert), ParticipantJoined (status), MessagePosted (last message, count, open offer), OfferAccepted / OfferDeclined (offer columns), ConversationClosed, ConversationBlocked; `last_event_id` records the last event applied |
| `announce_conversation_participant` | (`conversation_id`, `participant_type`, `participant_id`) | ConversationOpened, ParticipantJoined (upsert); `last_read_seq` by MarkRead |
| `announce_chat_message` | `id` varchar(36); unique (`conversation_id`, `seq`) and (`conversation_id`, `client_msg_id`) | MessagePosted (upsert); `offer_status` OPEN / ACCEPTED / DECLINED / SUPERSEDED |
| `announce_bulletin` | int auto | `POST /api/admin/bulletins` directly (not event-sourced) |

Projection group `conversation-read-model` (`ConversationReadModelProjection`), checkpoint in
`cqrs_readside_progress`. At least once: every write is an upsert keyed by the aggregate's ids.
Regenerate the mappers with `mvn -pl db-reactive -P mbg mybatis-generator:generate
-Dmybatis.generator.tableNames=announce_conversation,announce_conversation_participant,announce_chat_message,announce_bulletin`
then `reactify_mapper.py`; the hand-written statements (`upsert`, `updateOffers`, `selectByParticipant`,
`raiseLastReadSeq`, `updateOfferStatus`, `selectAfter`) are appended to the reactive mapper files.

## 4. REST (`interfaces.rest`)

Headers: `X-User-Id` with `X-User-Type` (`MEMBER` from the customer edge, `ADMIN` from the admin edge; read
through the framework's `Callers`, `X-Staff-Id` was dropped on 2026-10-10, `doc/frontend-edges.md` §11), optional
`X-Command-Id` for idempotent writes. An edge sets them after authenticating the caller; the service refuses
requests and WebSocket handshakes without a valid `X-Service-Token` (`announce.service-token.mode=enforce`,
`doc/frontend-edges.md` §10), and both edges strip whatever a client sent under these names.

| Route | Body / params | Answer |
|---|---|---|
| `POST /api/conversations` | `{type: LISTING, announceId}` or `{type: SUPPORT, subject}` | 201 the conversation state (LISTING: the existing one when already open) |
| `GET /api/conversations?page&size` | | the caller's conversations, latest activity first, with `unread`; two statements per page whatever `size` (the page, then every participant of it in one query) |
| `GET /api/conversations/{id}` | | 404 unknown, 403 not a participant (staff always may) |
| `GET /api/conversations/{id}/messages?after=0&limit=50` | | messages with `seq > after`, oldest first: the initial load and the catch-up after a reconnect |
| `POST /api/conversations/{id}/messages` | `{clientMsgId, contentType, content, attachmentUrl, amountCents, currency}` | 201 `{messageId, seq, clientMsgId, duplicate}` |
| | | A command for a conversation that already has one in flight on the node waits for it (`messaging.serialize-commands`); two participants posting at once therefore never cost a refused write on the same node |
| `POST /api/conversations/{id}/offers/accept` and `.../decline` | | the state |
| `POST /api/conversations/{id}/read` | `{seq}` | 204 |
| `POST /api/conversations/{id}/close` | | the state |
| `GET /api/admin/conversations?status=WAITING&page&size` | | the support queue (oldest first) |
| `POST /api/admin/conversations/{id}/join`, `.../block` (`{reason}`), `.../close` | | the state |
| `POST /api/admin/bulletins` | `{audience: STAFF/MEMBERS, clientType, content, expiresAt}` | 201 the bulletin, pushed to matching staff sockets |
| `GET /api/admin/bulletins?audience=STAFF` | | bulletins not yet expired |

## 5. WebSocket (`interfaces.ws`)

`ws://host/ws/conversations/{id}` with the same headers at the handshake. A caller without a principal is
closed with 4401, one who is neither a participant nor staff with 4403. History is not replayed on the
socket: load it with the REST route, then keep the socket for what follows.

The handshake authorises against the read model, which trails the command side by the projection delay
(about 0.1 s). A client may connect as soon as `POST /api/conversations` has answered: a conversation the
read model does not have yet is looked up again, `messaging.attach.rechecks` (5) times,
`messaging.attach.recheck-interval` (200 ms) apart, before the socket is closed with 4403. A conversation
that is there answers at once, so a stranger is not kept waiting; an id that never existed costs about one
second.

Frames the client sends (JSON, `type` first):

```json
{"type":"post","clientMsgId":"c1","contentType":"TEXT","content":"Is it available?"}
{"type":"post","clientMsgId":"c2","contentType":"IMAGE","attachmentUrl":"https://.../1.jpg"}
{"type":"offer","clientMsgId":"c3","amountCents":90000,"currency":"SEK","content":"Would you take 900?"}
{"type":"accept-offer"}   {"type":"decline-offer"}   {"type":"read","seq":12}   {"type":"close"}   {"type":"ping"}
```

Frames the client receives:

- `ack`: `{"type":"ack","action":"post","messageId":..,"seq":..,"clientMsgId":..,"duplicate":false}` (for
  `accept-offer`, `decline-offer`, `read`, `close` only `action`);
- `error`: `{"type":"error","action":..,"clientMsgId":..,"status":403,"code":"not-buyer","detail":..}`;
  status 409 with code `concurrent-modification` means the frame lost a race against other writers of the
  conversation even after the server's retries (below) and can be sent again as is;
- `message`: a `MessageLive` (`conversationId, messageId, seq, senderType, senderId, contentType, content,
  attachmentUrl, offerAmountCents, currency, clientMsgId, sentAt`) for every message of the conversation,
  the client's own included;
- `status`: a `ConversationStatusLive` (`change` JOINED / OFFER_ACCEPTED / OFFER_DECLINED / CLOSED /
  BLOCKED, `status`, `messageId`, `amountCents`, `currency`, `participantType`, `participantId`, `at`);
- `pong`.

`ws://host/ws/staff?clientType=desktop` with a staff identity (`X-User-Id` + `X-User-Type=ADMIN`) receives `bulletin` frames for its client type
(a bulletin without a client type reaches every staff socket).

**What the node does to every socket** (`messaging.socket`, `SocketLifecycle`): it sends a WebSocket
protocol ping every `ping-interval` (30 s), which browsers and client libraries answer by themselves; it
closes with **4409** a socket from which nothing arrived (the pong counts) for `idle-timeout` (75 s); and it
queues at most `buffer-frames` (256) frames for a socket whose connection is not taking them, after which the
frames it holds are still delivered and the socket is closed with **4408** (slow consumer). On 4408 and
4409 the client reconnects and reloads the messages after the last `seq` it has over REST; it has missed
nothing else, because a message is only ever lost from the socket, never from the conversation. A peer whose
connection stopped taking bytes altogether cannot receive the close frame either; the server-wide write
timeout (`write-timeout`, 30 s) ends that connection. Binary frames from the client are ignored; a text
frame that is not JSON gets `error` 400 `bad-frame`.

**Fan-out across nodes.** A message is a command; the aggregate writes the event; the relay group
`messaging-live` maps it to a live record on topic `messaging.live` (key = the conversation subject);
every node consumes the topic with its own consumer group, named after the node
(`messaging-live-${ANNOUNCE_NODE}`, falling back to host and port) and reset to the latest offset at every
start, and pushes to the sockets it holds (`LiveSessions`). Delivery is at least once: clients
de-duplicate on `messageId`. Bulletins reach the same topic through `StreamBridgeLiveFeed`. The live
topic is produced and consumed for latency, not durability (`acks=1`, no idempotence, no lingering, fetch
of one record): a lost live record costs a push the client recovers over REST, nothing more.

**Posts at the same moment.** Two commands on one conversation that are handled together read the same
state; EventSourcingDB accepts the first write and refuses the second, and nothing of the second is written.
`Conversations` dispatches the refused command again on the new state, at most
`messaging.command-retry.max-retries` (3) times with a short jittered back-off (`min-backoff` 5 ms,
`max-backoff` 50 ms), so both posts are acknowledged, each with its own `seq`. Each failed attempt means
another writer got through, so up to four writers at once always succeed; a writer that keeps losing against a
continuous stream gets 409 `concurrent-modification` (REST and socket) and may repeat the request with the
same `clientMsgId`. The re-dispatches are counted in `messaging.command.retries`.

## 6. Public events and subscriptions

| Direction | Topic | Records |
|---|---|---|
| messaging -> order | `messaging.v1` (binding `messaging-out-0`, relay group `messaging-public`) | `OfferAcceptedV1` (`messaging.offer-accepted`), `ConversationOpenedV1` (`messaging.conversation-opened`) |
| order -> messaging | `order.v1` (binding `orderEvents-in-0`, group `messaging`) | `OrderAgreedV1`, `OrderPaidV1`, `OrderShippedV1`, `OrderSettledV1` become SYSTEM messages in the listing conversation of buyer and seller, which is opened when it does not exist |
| messaging -> messaging | `messaging.live` | internal live records (§5) |

The `order.v1` records come from the `order-api` module (the local stand-ins were dropped when the order
module was merged on 2026-09-26; `OrderAgreedV1` carries `priceCents`, `feeCents` and `payoutCents`). The seller of an announce comes from the announce-management API (`AnnounceLookup`, pinned
by `cqrs.api.clients.announce-management.base-url`).

## 7. Running and testing

```
docker compose -f docker/cqrs/docker-compose.yml up -d      # EventSourcingDB, Kafka
mysql -uannounce -p announce_reactive_db < db-reactive/sql/announce_messaging.sql
mvn -pl db-reactive,messaging-api install -DskipTests
mvn -pl messaging spring-boot:run
```

`mvn -pl messaging test`: `ConversationTest` (aggregate through `CommandFixture`),
`ConversationControllerTest` (REST slice), `ConversationsRetryTest`, `ConversationQueriesAttachTest` and
`ConversationQueriesInboxTest` (the retry, the handshake re-check and the one-query page against mocks),
`StatementCostProbeTest` (only with `-Dprobe=true`: the latency of one statement at every layer of the reactive
MySQL path, printed as a table; the instrument of tuning step 9) and `MessagingEndToEndTest` (EventSourcingDB and Kafka
from Testcontainers, the read model in the live `announce_reactive_db`; the class removes the rows it writes
and clears the three groups' checkpoints and the test lock table `cqrs_readside_lock_test` before it starts).
The test profile runs the processors under leader election with the read model in two partitions, as in
production, and asserts the leases and the controllers. It also runs the sockets with a small outbound bound
(`messaging.socket.buffer-frames` 64), a 1 s ping, a 4 s idle timeout and a 10 s write timeout, and the suite
drives a client that stops reading (closed 4408 once it reads again, every accepted frame delivered first), one
that never reads (reclaimed by the write timeout) and one that answers no ping (closed 4409 while a normal
client stays open). `LiveSinkTest` and `SocketLifecycleOverNettyTest` cover the sink and the socket policy
without the application context. Several worktrees share `~/.m2`, so build with `-am` when another session
may have installed an older `db-reactive`. The reactive MyBatis library comes from `db-reactive`
(`mybatis-r2dbc-spring` 3.1.3 since 2026-10-11; 3.0.6 generated a ByteBuddy class for every statement, which cost
about 15 ms of CPU per statement in every service: `doc/messaging-architecture.md` section 7f, and
`ReactiveMybatisProxyTest` in db-reactive pins the fixed behaviour).

The command side keeps the state of recently used conversations in an in-memory LRU cache
(`opencqrs.command-handling.cache.type=in_memory`, `capacity` 1000 conversations per node, set in both
profiles). A command replays only the events written after the cached state, so a long conversation costs
the same as a new one; a stale entry on another node is read forward from the store, never served as is.
`doc/messaging-architecture.md` section 7b has the measurement.

Read side across nodes (since 2026-10-10): each group and partition runs on the one node holding its lease in
`cqrs_readside_lock` (`cqrs.readside.leader-election.enabled=true`, `life-cycle.controller=leader_election`;
lease 10 s, renewed every 500 ms), so two nodes no longer project every event twice or put two copies of each
record on `messaging.live`. `conversation-read-model` has four partitions (order per conversation is kept;
one checkpoint row per partition, seeded by the framework when the count rises). Changing the partition count
needs every node stopped. `doc/messaging-architecture.md` section 7c has the measurements, including a crash
of the node holding the leases (takeover after the lease, nothing lost).

After a test run the checkpoints in `cqrs_readside_progress` (one row per partition) hold event ids of the
test's EventSourcingDB container. Before starting a node against the local `docker/cqrs` store, set them to
that store's latest `/conversation` event id (partition 0 is enough: the start-up seeding copies it to the
other partitions when their rows are missing, so delete the rows of the other partitions) or delete them all
for a full rebuild; otherwise the node resumes from an id that means something else there and replays old
events onto `messaging.v1` and `messaging.live`.

Out of scope for this iteration: push notifications, attachment storage (URLs only), moderation beyond
Block, read receipts per message.
