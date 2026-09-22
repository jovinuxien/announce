package org.linlinjava.announce.cqrs.command;

import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.client.ConcurrencyException;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

class CqrsProblemDetailsHandlerTest {

    private record Cmd(String subject) implements Command {
        @Override
        public String getSubject() {
            return subject;
        }
    }

    @RestController
    static class FailingController {
        @GetMapping("/fail/{kind}")
        Mono<String> fail(@PathVariable String kind) {
            Command command = new Cmd("/announce/1");
            return Mono.error(switch (kind) {
                case "missing" -> new CommandSubjectDoesNotExistException("no such announce", command);
                case "exists" -> new CommandSubjectAlreadyExistsException("already there", command);
                case "concurrent" -> new ConcurrencyException("changed", new RuntimeException());
                case "duplicate" -> new DuplicateCommandException("cmd-1", "/announce/1", false);
                case "rejected" -> new CommandRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "price-negative", "price must be positive");
                case "invalid" -> new IllegalArgumentException("title is blank");
                case "transient" -> new CqrsFrameworkException.TransientException("esdb down");
                default -> new CqrsFrameworkException.NonTransientException("broken");
            });
        }
    }

    private final WebTestClient client = WebTestClient.bindToController(new FailingController())
            .controllerAdvice(new CqrsProblemDetailsHandler()).build();

    private WebTestClient.BodyContentSpec problem(String kind, int status, String type) {
        return client.get().uri("/fail/" + kind).exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(status)
                .jsonPath("$.type").isEqualTo("urn:announce:cqrs:" + type);
    }

    @Test
    void mapsCommandSideFailuresToProblemDetails() {
        problem("missing", 404, "subject-not-found").jsonPath("$.subject").isEqualTo("/announce/1");
        problem("exists", 409, "subject-already-exists");
        problem("concurrent", 409, "concurrent-modification");
        problem("duplicate", 409, "duplicate-command").jsonPath("$.commandId").isEqualTo("cmd-1");
        problem("rejected", 422, "command-rejected").jsonPath("$.code").isEqualTo("price-negative")
                .jsonPath("$.detail").isEqualTo("price must be positive");
        problem("invalid", 400, "invalid-command");
        problem("transient", 503, "event-store-unavailable");
        problem("other", 500, "framework-failure");
    }
}
