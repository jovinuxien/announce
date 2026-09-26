package org.linlinjava.announce.gatewayapi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Writes an RFC 7807 body for the edge's own failures (401, 403) so the SPA handles every error the same way. */
public final class ProblemDetails {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProblemDetails() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) return Mono.empty();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(exchange.getRequest().getURI());
        byte[] body;
        try {
            body = JSON.writeValueAsBytes(problem);
        } catch (Exception e) {
            body = ("{\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setContentLength(body.length);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
