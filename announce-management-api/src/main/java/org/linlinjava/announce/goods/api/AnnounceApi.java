package org.linlinjava.announce.goods.api;

import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceResponse;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceSummaryResponse;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.PageResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service descriptor of the announce read API (Lagom's {@code Service.descriptor}, as an {@code @HttpExchange}
 * interface). announce-management's controller implements it, so the mapping lives here once; a consumer turns it
 * into a client with {@code HttpExchangeClients.create(AnnounceApi.class, "announce-management")}.
 *
 * <p>Parameters are client-legal only: path variables, request parameters and named headers. The caller's
 * identity is not a parameter: an edge forwards it as {@code X-User-Id} / {@code X-User-Type}, and the provider
 * reads it from the request in a filter. A service calling through the client carries no identity and sees what a
 * visitor sees: PUBLISHED announces only.
 *
 * <p>The write side (post, edit, publish, withdraw, sold) is not on the descriptor yet; it stays a member-only
 * REST surface of announce-management.
 */
@HttpExchange("/api/announces")
public interface AnnounceApi {

    /** One announce; 404 (problem detail) when it does not exist or is not visible to the caller. */
    @GetExchange("/{id}")
    Mono<AnnounceResponse> view(@PathVariable("id") String id);

    /**
     * A page of summaries. {@code status} other than PUBLISHED is honoured only for the member looking at their own
     * announces ({@code seller} equal to the caller's id); every other caller sees PUBLISHED announces.
     */
    @GetExchange
    Mono<PageResponse<AnnounceSummaryResponse>> browse(@RequestParam(name = "kind", required = false) AnnounceKindV1 kind,
                                                       @RequestParam(name = "category", required = false) List<Integer> category,
                                                       @RequestParam(name = "brand", required = false) Integer brand,
                                                       @RequestParam(name = "seller", required = false) Integer seller,
                                                       @RequestParam(name = "region", required = false) Integer region,
                                                       @RequestParam(name = "q", required = false) String q,
                                                       @RequestParam(name = "saleType", required = false) SaleTypeV1 saleType,
                                                       @RequestParam(name = "status", required = false) AnnounceStatusV1 status,
                                                       @RequestParam(name = "page", defaultValue = "1") int page,
                                                       @RequestParam(name = "size", defaultValue = "20") int size,
                                                       @RequestParam(name = "sort", defaultValue = "newest") String sort);
}
