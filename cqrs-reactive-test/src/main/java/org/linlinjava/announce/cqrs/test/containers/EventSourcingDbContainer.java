package org.linlinjava.announce.cqrs.test.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Testcontainers wrapper for EventSourcingDB (HTTP API on port 3000, temporary data directory).
 * Register it with OpenCQRS through {@link CqrsContainers#register}.
 */
public class EventSourcingDbContainer extends GenericContainer<EventSourcingDbContainer> {

    public static final String DEFAULT_IMAGE = "thenativeweb/eventsourcingdb:1.2.0";
    public static final int HTTP_PORT = 3000;
    public static final String DEFAULT_API_TOKEN = "secret";

    private final String apiToken;

    public EventSourcingDbContainer() {
        this(DEFAULT_IMAGE, DEFAULT_API_TOKEN);
    }

    public EventSourcingDbContainer(String image, String apiToken) {
        super(DockerImageName.parse(image));
        this.apiToken = apiToken;
        withExposedPorts(HTTP_PORT);
        withCommand("run", "--api-token", apiToken, "--data-directory-temporary",
                "--http-enabled", "--http-port", String.valueOf(HTTP_PORT), "--https-enabled=false");
        waitingFor(Wait.forHttp("/api/v1/ping").forPort(HTTP_PORT).forStatusCodeMatching(code -> code < 500)
                .withStartupTimeout(Duration.ofSeconds(60)));
    }

    public String uri() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT);
    }

    public String apiToken() {
        return apiToken;
    }
}
