package org.linlinjava.announce.cqrs.test.containers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Factories for the containers a CQRS service test needs and the bridge to Spring properties.
 *
 * <pre>{@code
 * @SpringBootTest
 * @Testcontainers
 * class MyServiceTest {
 *     @Container static final EventSourcingDbContainer esdb = CqrsContainers.eventSourcingDb();
 *     @Container static final KafkaContainer kafka = CqrsContainers.kafka();
 *     @Container static final MySQLContainer<?> mysql = CqrsContainers.mysql();
 *
 *     @DynamicPropertySource
 *     static void properties(DynamicPropertyRegistry registry) {
 *         CqrsContainers.register(registry, esdb, kafka, mysql);
 *     }
 * }
 * }</pre>
 */
public final class CqrsContainers {

    public static final String KAFKA_IMAGE = "apache/kafka-native:3.9.1";
    public static final String MYSQL_IMAGE = "mysql:8.4";

    private CqrsContainers() {
    }

    public static EventSourcingDbContainer eventSourcingDb() {
        return new EventSourcingDbContainer();
    }

    /** Single-node KRaft Kafka. */
    public static KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));
    }

    /** MySQL 8.4 for read models, the progress table and the command log. */
    public static MySQLContainer<?> mysql() {
        return new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE)).withDatabaseName("cqrs_test")
                .withUsername("cqrs").withPassword("cqrs");
    }

    public static String r2dbcUrl(MySQLContainer<?> mysql) {
        return "r2dbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(MySQLContainer.MYSQL_PORT) + "/" + mysql.getDatabaseName();
    }

    /** Registers every container that is not {@code null} with the matching Spring properties. */
    public static void register(DynamicPropertyRegistry registry, EventSourcingDbContainer esdb, KafkaContainer kafka, MySQLContainer<?> mysql) {
        if (esdb != null) {
            registry.add("esdb.server.uri", esdb::uri);
            registry.add("esdb.server.api-token", esdb::apiToken);
        }
        if (kafka != null) {
            registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        }
        if (mysql != null) {
            registry.add("spring.r2dbc.url", () -> r2dbcUrl(mysql));
            registry.add("spring.r2dbc.username", mysql::getUsername);
            registry.add("spring.r2dbc.password", mysql::getPassword);
        }
    }
}
