package org.linlinjava.announce.cqrs.sample;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The sample's service descriptor (Lagom's {@code Service.descriptor}): an {@code @HttpExchange} interface that
 * {@link ListingController} implements on the server side (Spring Framework 6.2) and that a consumer turns into a
 * client with {@code HttpExchangeClients.create(ListingApi.class, "listing-sample")}. In a real service the
 * interface and its records live in the provider's {@code <name>-api} module; here they sit next to the sample.
 *
 * <p>Parameters are client-legal only: path variables, request parameters, request bodies and <em>named</em>
 * headers such as the optional {@code X-Command-Id}. No {@code HttpHeaders}, no {@code ServerWebExchange}: a
 * caller's identity is read by a filter on the provider side, never declared on the descriptor.
 */
@HttpExchange("/listings")
public interface ListingApi {

    record CreateRequest(String id, String title, long priceCents) {
    }

    /** Creates a listing; answers 201 with {@code {"id": ...}}. Idempotent when {@code X-Command-Id} is given. */
    @PostExchange
    Mono<Map<String, String>> post(@RequestBody CreateRequest request,
                                   @RequestHeader(value = "X-Command-Id", required = false) String commandId);

    /** The read model's row; 404 (problem detail {@code listing-not-found}) when unknown. */
    @GetExchange("/{id}")
    Mono<ListingSummaryProjection.Summary> view(@PathVariable("id") String id);
}
