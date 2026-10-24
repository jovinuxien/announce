package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.linlinjava.announce.goods.api.AnnounceApi;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceResponse;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceSummaryResponse;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.PageResponse;
import org.linlinjava.announce.goods.api.AnnounceKindV1;
import org.linlinjava.announce.goods.api.AnnounceStatusV1;
import org.linlinjava.announce.goods.api.SaleTypeV1;
import org.linlinjava.announce.goods.application.announce.AnnounceBrowsing;
import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.interfaces.rest.Callers;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceRequest;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.PublishRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Announce API for goods, jobs and events. The read slice ({@code view}, {@code browse}) implements the
 * {@link AnnounceApi} descriptor of {@code announce-management-api}: the interface carries the mappings
 * ({@code @HttpExchange("/api/announces")}, {@code @GetExchange}), so this class has no {@code @RequestMapping} and
 * those two methods no {@code @GetMapping}; the viewer comes from the Reactor context ({@link Callers#viewer()},
 * written by {@code CallerContextFilter}) because a descriptor method cannot take {@code HttpHeaders}. The writes
 * are commands on the aggregate and answer with the state the command produced; they are not on the descriptor yet
 * and keep their own {@code @PostMapping} / {@code @PutMapping} (legal across methods, never on one method together
 * with an {@code @HttpExchange} annotation). The seller is the authenticated member the customer edge forwards as
 * {@code X-User-Id} with {@code X-User-Type=MEMBER} (see {@link Callers}): required on every write, optional on
 * reads (owners see their own drafts). An optional {@code X-Command-Id} header makes a write idempotent.
 */
@RestController
public class AnnounceController implements AnnounceApi {

    public static final String COMMAND_ID_HEADER = "X-Command-Id";

    private final AnnouncePublishing publishing;
    private final AnnounceBrowsing browsing;

    public AnnounceController(AnnouncePublishing publishing, AnnounceBrowsing browsing) {
        this.publishing = publishing;
        this.browsing = browsing;
    }

    // ---- the descriptor: reads ------------------------------------------------------------------------------

    @Override
    public Mono<PageResponse<AnnounceSummaryResponse>> browse(AnnounceKindV1 kind, List<Integer> category, Integer brand, Integer seller,
                                                              Integer region, String q, SaleTypeV1 saleType, AnnounceStatusV1 status,
                                                              int page, int size, String sort) {
        return viewerIfAny().flatMap(viewer -> {
            // only a seller looking at their own announces may filter on another status than PUBLISHED
            boolean ownListing = viewer.isPresent() && viewer.get().value().equals(seller);
            AnnounceStatus effectiveStatus = ownListing ? AnnounceApiMapper.toDomain(status) : AnnounceStatus.PUBLISHED;
            AnnounceQuery query = new AnnounceQuery(AnnounceApiMapper.toDomain(kind), category, brand, seller, region, q,
                    AnnounceApiMapper.toDomain(saleType), effectiveStatus, page, size, sort);
            return browsing.count(query).flatMap(total -> browsing.browse(query).map(AnnounceApiMapper::summary).collectList()
                    .map(items -> new PageResponse<>(items, query.page(), query.size(), total)));
        });
    }

    @Override
    public Mono<AnnounceResponse> view(String id) {
        return viewerIfAny().flatMap(viewer -> browsing.view(id, viewer.orElse(null))).map(AnnounceApiMapper::response);
    }

    private static Mono<Optional<SellerId>> viewerIfAny() {
        return Callers.viewer().map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    // ---- writes: member-only, not on the descriptor -------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AnnounceResponse> post(@RequestHeader HttpHeaders headers,
                                       @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                       @RequestBody AnnounceRequest body) {
        return publishing.post(Callers.member(headers), body.toContent(), body.toDetails(), commandId).map(AnnounceApiMapper::response);
    }

    @PutMapping("/{id}")
    public Mono<AnnounceResponse> edit(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                       @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                       @RequestBody AnnounceRequest body) {
        return publishing.edit(id, Callers.member(headers), body.toContent(), body.toDetails(), commandId).map(AnnounceApiMapper::response);
    }

    @PostMapping("/{id}/publish")
    public Mono<AnnounceResponse> publish(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                          @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                          @RequestBody(required = false) PublishRequest body) {
        Duration validity = body == null || body.validityDays() == null ? null : Duration.ofDays(body.validityDays());
        return publishing.publish(id, Callers.member(headers), validity, commandId).map(AnnounceApiMapper::response);
    }

    @PostMapping("/{id}/withdraw")
    public Mono<AnnounceResponse> withdraw(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                           @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        return publishing.withdraw(id, Callers.member(headers), commandId).map(AnnounceApiMapper::response);
    }

    @PostMapping("/{id}/sold")
    public Mono<AnnounceResponse> markSold(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                           @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        return publishing.markSold(id, Callers.member(headers), commandId).map(AnnounceApiMapper::response);
    }
}
