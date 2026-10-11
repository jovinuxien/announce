package org.linlinjava.announce.messaging;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.linlinjava.announce.cqrs.test.containers.EventSourcingDbContainer;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationParticipantMapper;
import org.linlinjava.announce.db.domain.AnnounceConversation;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipant;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipantExample;
import org.linlinjava.announce.messaging.application.AnnounceLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tuning step 9 of {@code doc/messaging-architecture.md}: where the cost of one statement goes. The same point
 * query on one conversation row is run in sequence at every layer of the reactive MySQL path, and the latency of
 * each call as a caller sees it (subscribe to complete) is reported as p50 / p95 / max:
 *
 * <ol>
 *   <li>the driver alone: one r2dbc-mysql connection, no pool, no Spring;</li>
 *   <li>the pooled connection factory bean: acquire, statement, release, as the framework does;</li>
 *   <li>the mapper: reactive MyBatis session, Spring executor, transaction-aware connection, pool, driver, result mapping;</li>
 *   <li>the mapper inside one transaction: every call on the connection the transaction holds;</li>
 *   <li>a wider mapper statement (participants by example) to separate result mapping from the round trip;</li>
 *   <li>the mapper with four callers at once, the shape of the inbox load.</li>
 * </ol>
 *
 * <p>Runs only on request ({@code -Dprobe=true -Dtest=StatementCostProbeTest}), against the live read model with
 * the test profile (EventSourcingDB and Kafka from Testcontainers, as the end-to-end suite), and prints its table.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
@EnabledIfSystemProperty(named = "probe", matches = "true")
class StatementCostProbeTest {

    static final String CONVERSATION = "probe-" + System.nanoTime();
    static final int ROUNDS = Integer.getInteger("probe.rounds", 200);
    static final int CALLERS = Integer.getInteger("probe.callers", 4);

    static {
        MessagingEndToEndTest.cleanLiveDatabase();
    }

    @Container
    static final EventSourcingDbContainer esdb = CqrsContainers.eventSourcingDb();
    @Container
    static final KafkaContainer kafka = CqrsContainers.kafka();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        CqrsContainers.register(registry, esdb, kafka, null);
        registry.add("messaging.announce-lookup", () -> "stub");
    }

    @TestConfiguration
    static class Stubs {
        @Bean
        AnnounceLookup stubAnnounceLookup() {
            return id -> Mono.empty();
        }

        @Bean
        Consumer<Message<byte[]>> publicEventsProbe() {
            return message -> {
            };
        }
    }

    @Autowired ConnectionFactory pooled;
    @Autowired AnnounceConversationMapper conversations;
    @Autowired AnnounceConversationParticipantMapper participants;
    @Autowired TransactionalOperator tx;
    @Value("${spring.r2dbc.mybatis.r2dbc-url}") String url;
    @Value("${spring.r2dbc.mybatis.username}") String user;
    @Value("${spring.r2dbc.mybatis.password}") String password;

    static final String SQL = "select id, `type`, announce_id, buyer_id, seller_id, subject, `status`, created_by_type, created_by, last_message_id, "
            + "last_message_seq, last_message_time, message_count, open_offer_message_id, open_offer_amount, open_offer_currency, "
            + "accepted_offer_amount, accepted_offer_currency, accepted_offer_time, closed_time, blocked_reason, last_event_id, add_time, "
            + "update_time, deleted from announce_conversation where id = ?";

    @BeforeEach
    void oneRow() {
        AnnounceConversation row = new AnnounceConversation();
        row.setId(CONVERSATION);
        row.setType("SUPPORT");
        row.setStatus("OPEN");
        row.setCreatedByType("USER");
        row.setCreatedBy(1);
        row.setSubject("probe");
        row.setLastMessageSeq(0);
        row.setMessageCount(0);
        row.setAddTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleted(false);
        conversations.upsert(row).block(Duration.ofSeconds(30));
        for (int i = 0; i < 2; i++) {
            AnnounceConversationParticipant p = new AnnounceConversationParticipant();
            p.setConversationId(CONVERSATION);
            p.setParticipantType(i == 0 ? "USER" : "ADMIN");
            p.setParticipantId(i == 0 ? 1 : 2);
            p.setRole(i == 0 ? "MEMBER" : "CSR");
            p.setJoinedTime(LocalDateTime.now());
            p.setLastReadSeq(0);
            p.setMuted(false);
            p.setAddTime(LocalDateTime.now());
            p.setUpdateTime(LocalDateTime.now());
            p.setDeleted(false);
            participants.upsert(p).block(Duration.ofSeconds(30));
        }
    }

    @AfterEach
    void removeTheRow() {
        AnnounceConversationParticipantExample example = new AnnounceConversationParticipantExample();
        example.or().andConversationIdEqualTo(CONVERSATION);
        participants.deleteByExample(example).block(Duration.ofSeconds(30));
        conversations.deleteByPrimaryKey(CONVERSATION).block(Duration.ofSeconds(30));
    }

    @Test
    void whereOneStatementCosts() throws Exception {
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(url).mutate()
                .option(ConnectionFactoryOptions.USER, user).option(ConnectionFactoryOptions.PASSWORD, password).build();
        ConnectionFactory raw = ConnectionFactories.get(options);
        Connection one = Mono.from(raw.create()).block(Duration.ofSeconds(30));
        List<String> report = new ArrayList<>();
        try {
            report.add(measure("1 driver alone, one connection, no pool", () -> query(one)));
            report.add(measure("2 pooled factory: acquire, statement, release", () ->
                    Mono.usingWhen(pooled.create(), this::query, Connection::close)));
            report.add(measure("3 mapper selectByPrimaryKey", () -> conversations.selectByPrimaryKey(CONVERSATION)));
            report.add(measureOnce("4 mapper, " + ROUNDS + " calls in one transaction (per call)", () ->
                    tx.transactional(Flux.range(0, ROUNDS).concatMap(i -> conversations.selectByPrimaryKey(CONVERSATION))).then(), ROUNDS));
            report.add(measure("5 mapper selectByExample, 2 participant rows", () -> {
                AnnounceConversationParticipantExample example = new AnnounceConversationParticipantExample();
                example.or().andConversationIdEqualTo(CONVERSATION).andDeletedEqualTo(false);
                return participants.selectByExample(example).collectList();
            }));
            report.add(measure("6 pooled factory, bare 'select 1'", () ->
                    Mono.usingWhen(pooled.create(), c -> Flux.from(c.createStatement("select 1").execute())
                            .flatMap(r -> r.map((row, meta) -> row.get(0))).collectList(), Connection::close)));
            report.add(measureConcurrent("7 mapper selectByPrimaryKey, " + CALLERS + " callers at once", () -> conversations.selectByPrimaryKey(CONVERSATION)));
            report.add(measureConcurrent("8 pooled factory, " + CALLERS + " callers at once", () ->
                    Mono.usingWhen(pooled.create(), this::query, Connection::close)));
        } finally {
            Mono.from(one.close()).block(Duration.ofSeconds(10));
        }
        System.out.println("=== statement cost probe, " + ROUNDS + " rounds per layer, latency of one call as the caller sees it");
        report.forEach(System.out::println);
    }

    Mono<Object> query(Connection c) {
        return Flux.from(c.createStatement(SQL).bind(0, CONVERSATION).execute())
                .flatMap(result -> result.map((row, meta) -> (Object) row.get("id"))).next();
    }

    String measure(String label, Supplier<Mono<?>> call) {
        call.get().block(Duration.ofSeconds(30));          // warm-up: caches, first connection
        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            long start = System.nanoTime();
            call.get().block(Duration.ofSeconds(30));
            samples.add(System.nanoTime() - start);
        }
        return line(label, samples);
    }

    String measureOnce(String label, Supplier<Mono<?>> call, int calls) {
        call.get().block(Duration.ofSeconds(60));
        long start = System.nanoTime();
        call.get().block(Duration.ofSeconds(60));
        long perCall = (System.nanoTime() - start) / calls;
        return String.format("%-55s mean=%.1fms (one measurement of %d calls)", label, perCall / 1e6, calls);
    }

    String measureConcurrent(String label, Supplier<Mono<?>> call) throws InterruptedException {
        List<Long> samples = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < CALLERS; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < ROUNDS / CALLERS; i++) {
                    long start = System.nanoTime();
                    call.get().block(Duration.ofSeconds(30));
                    long elapsed = System.nanoTime() - start;
                    synchronized (samples) {
                        samples.add(elapsed);
                    }
                }
            });
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }
        return line(label, samples);
    }

    static String line(String label, List<Long> samples) {
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(null);
        return String.format("%-55s p50=%.1fms p95=%.1fms max=%.1fms (n=%d)", label, ms(percentile(sorted, 0.5)), ms(percentile(sorted, 0.95)),
                ms(sorted.get(sorted.size() - 1)), sorted.size());
    }

    static long percentile(List<Long> sorted, double p) {
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    static double ms(long nanos) {
        return nanos / 1e6;
    }
}
