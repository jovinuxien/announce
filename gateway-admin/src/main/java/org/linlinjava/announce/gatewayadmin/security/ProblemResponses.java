package org.linlinjava.announce.gatewayadmin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/** Writes an RFC 7807 body for the edge's own 401 / 403 answers, in the shape the backends use. */
public final class ProblemResponses {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProblemResponses() {}

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:announce:edge:" + type));
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body;
        try {
            body = JSON.writeValueAsBytes(problem);
        } catch (Exception e) {
            body = ("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase() + "\"}").getBytes();
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
