package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.linlinjava.announce.goods.application.announce.AnnounceBrowsing;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.AddVariant;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.AdjustStock;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.ChangeAnnouncePrice;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.DraftAnnounce;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.EditAnnounce;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.MarkAnnounceSold;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.PublishAnnounce;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.WithdrawAnnounce;
import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.linlinjava.announce.goods.application.announce.AnnounceStock;
import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceRequest;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceResponse;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceSummaryResponse;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.PageResponse;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.PriceRequest;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.PublishRequest;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.StockRequest;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.VariantRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * Announce API. Until the gateway propagates the authenticated member, the seller is taken from the
 * {@code X-Seller-Id} header: required on every write, optional on reads (owners see their own drafts).
 */
@RestController
@RequestMapping("/api/announces")
public class AnnounceController {

    public static final String SELLER_HEADER = "X-Seller-Id";

    private final AnnouncePublishing publishing;
    private final AnnounceStock stock;
    private final AnnounceBrowsing browsing;

    public AnnounceController(AnnouncePublishing publishing, AnnounceStock stock, AnnounceBrowsing browsing) {
        this.publishing = publishing;
        this.stock = stock;
        this.browsing = browsing;
    }

    @GetMapping
    public Mono<PageResponse<AnnounceSummaryResponse>> browse(@RequestParam(required = false) List<Integer> category,
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
        AnnounceQuery query = new AnnounceQuery(category, brand, seller, region, q, saleType, effectiveStatus, page, size, sort);
        return browsing.count(query).flatMap(total -> browsing.browse(query).map(AnnounceSummaryResponse::of).collectList()
                .map(items -> new PageResponse<>(items, query.page(), query.size(), total)));
    }

    @GetMapping("/{id}")
    public Mono<AnnounceResponse> view(@PathVariable Integer id, @RequestHeader(value = SELLER_HEADER, required = false) Integer viewer) {
        return browsing.view(new AnnounceId(id), viewer == null ? null : new SellerId(viewer)).map(AnnounceResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AnnounceResponse> draft(@RequestHeader(SELLER_HEADER) Integer seller, @RequestBody AnnounceRequest body) {
        return publishing.draft(new DraftAnnounce(new SellerId(seller), body.toDetails())).map(AnnounceResponse::of);
    }

    @PutMapping("/{id}")
    public Mono<AnnounceResponse> edit(@PathVariable Integer id, @RequestHeader(SELLER_HEADER) Integer seller, @RequestBody AnnounceRequest body) {
        return publishing.edit(new EditAnnounce(new AnnounceId(id), new SellerId(seller), body.toDetails())).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/publish")
    public Mono<AnnounceResponse> publish(@PathVariable Integer id, @RequestHeader(SELLER_HEADER) Integer seller,
                                          @RequestBody(required = false) PublishRequest body) {
        Duration validity = body == null || body.validityDays() == null ? null : Duration.ofDays(body.validityDays());
        return publishing.publish(new PublishAnnounce(new AnnounceId(id), new SellerId(seller), validity)).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/withdraw")
    public Mono<AnnounceResponse> withdraw(@PathVariable Integer id, @RequestHeader(SELLER_HEADER) Integer seller) {
        return publishing.withdraw(new WithdrawAnnounce(new AnnounceId(id), new SellerId(seller))).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/sold")
    public Mono<AnnounceResponse> markSold(@PathVariable Integer id, @RequestHeader(SELLER_HEADER) Integer seller) {
        return publishing.markSold(new MarkAnnounceSold(new AnnounceId(id), new SellerId(seller))).map(AnnounceResponse::of);
    }

    @PatchMapping("/{id}/price")
    public Mono<AnnounceResponse> changePrice(@PathVariable Integer id, @RequestHeader(SELLER_HEADER) Integer seller, @RequestBody PriceRequest body) {
        return publishing.changePrice(new ChangeAnnouncePrice(new AnnounceId(id), new SellerId(seller), Money.of(body.price()))).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AnnounceResponse> addVariant(@PathVariable Integer id, @RequestHeader(SELLER_HEADER) Integer seller, @RequestBody VariantRequest body) {
        return stock.addVariant(new AddVariant(new AnnounceId(id), new SellerId(seller), body.specifications(),
                        Money.of(body.price()), body.stock() == null ? 0 : body.stock(), body.pictureUrl()))
                .map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/variants/{variantId}/stock")
    public Mono<AnnounceResponse> adjustStock(@PathVariable Integer id, @PathVariable Integer variantId,
                                              @RequestHeader(SELLER_HEADER) Integer seller, @RequestBody StockRequest body) {
        return stock.adjustStock(new AdjustStock(new AnnounceId(id), new SellerId(seller), variantId, body.delta() == null ? 0 : body.delta()))
                .map(AnnounceResponse::of);
    }
}
