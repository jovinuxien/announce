package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.linlinjava.announce.goods.application.announce.AnnounceBrowsing;
import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.Kind;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceRequest;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceResponse;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceSummaryResponse;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.PageResponse;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.PublishRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Announce API for goods, jobs and events. Writes are commands on the aggregate and answer with the state the
 * command produced; reads come from the read model, which follows a moment later. Until the gateway propagates the
 * authenticated member, the seller is taken from the {@code X-Seller-Id} header: required on every write, optional
 * on reads (owners see their own drafts). An optional {@code X-Command-Id} header makes a write idempotent.
 */
@RestController
@RequestMapping("/api/announces")
public class AnnounceController {

    public static final String SELLER_HEADER = "X-Seller-Id";
    public static final String COMMAND_ID_HEADER = "X-Command-Id";

    private final AnnouncePublishing publishing;
    private final AnnounceBrowsing browsing;

    public AnnounceController(AnnouncePublishing publishing, AnnounceBrowsing browsing) {
        this.publishing = publishing;
        this.browsing = browsing;
    }

    @GetMapping
    public Mono<PageResponse<AnnounceSummaryResponse>> browse(@RequestParam(required = false) Kind kind,
                                                              @RequestParam(required = false) List<Integer> category,
                                                              @RequestParam(required = false) Integer brand,
                                                              @RequestParam(required = false) Integer seller,
                                                              @RequestParam(required = false) Integer region,
                                                              @RequestParam(required = false) String q,
                                                              @RequestParam(required = false) SaleType saleType,
                                                              @RequestParam(required = false) AnnounceStatus status,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              @RequestParam(defaultValue = "newest") String sort,
                                                              @RequestHeader(value = SELLER_HEADER, required = false) Integer viewer) {
        // only a seller looking at their own announces may filter on another status than PUBLISHED
        boolean ownListing = viewer != null && viewer.equals(seller);
        AnnounceStatus effectiveStatus = ownListing ? status : AnnounceStatus.PUBLISHED;
        AnnounceQuery query = new AnnounceQuery(kind, category, brand, seller, region, q, saleType, effectiveStatus, page, size, sort);
        return browsing.count(query).flatMap(total -> browsing.browse(query).map(AnnounceSummaryResponse::of).collectList()
                .map(items -> new PageResponse<>(items, query.page(), query.size(), total)));
    }

    @GetMapping("/{id}")
    public Mono<AnnounceResponse> view(@PathVariable String id, @RequestHeader(value = SELLER_HEADER, required = false) Integer viewer) {
        return browsing.view(id, viewer == null ? null : new SellerId(viewer)).map(AnnounceResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AnnounceResponse> post(@RequestHeader(SELLER_HEADER) Integer seller,
                                       @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                       @RequestBody AnnounceRequest body) {
        return publishing.post(new SellerId(seller), body.toContent(), body.toDetails(), commandId).map(AnnounceResponse::of);
    }

    @PutMapping("/{id}")
    public Mono<AnnounceResponse> edit(@PathVariable String id, @RequestHeader(SELLER_HEADER) Integer seller,
                                       @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                       @RequestBody AnnounceRequest body) {
        return publishing.edit(id, new SellerId(seller), body.toContent(), body.toDetails(), commandId).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/publish")
    public Mono<AnnounceResponse> publish(@PathVariable String id, @RequestHeader(SELLER_HEADER) Integer seller,
                                          @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                          @RequestBody(required = false) PublishRequest body) {
        Duration validity = body == null || body.validityDays() == null ? null : Duration.ofDays(body.validityDays());
        return publishing.publish(id, new SellerId(seller), validity, commandId).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/withdraw")
    public Mono<AnnounceResponse> withdraw(@PathVariable String id, @RequestHeader(SELLER_HEADER) Integer seller,
                                           @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        return publishing.withdraw(id, new SellerId(seller), commandId).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/sold")
    public Mono<AnnounceResponse> markSold(@PathVariable String id, @RequestHeader(SELLER_HEADER) Integer seller,
                                           @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        return publishing.markSold(id, new SellerId(seller), commandId).map(AnnounceResponse::of);
    }
}
