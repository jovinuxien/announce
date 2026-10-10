package org.linlinjava.announce.goods.interfaces.rest.admin;

import org.linlinjava.announce.goods.application.announce.AnnounceBrowsing;
import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.linlinjava.announce.goods.application.search.AnnounceReindexService;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.Kind;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceResponse;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceSummaryResponse;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.PageResponse;
import org.linlinjava.announce.goods.interfaces.rest.Callers;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Staff view of the announces, reached only through the admin edge ({@code gateway-admin}), which authenticates the
 * administrator and injects {@code X-User-Id} with {@code X-User-Type=ADMIN}; every route answers 401 without that
 * identity and 403 to a member (see {@link Callers#staff}). Unlike {@code /api/announces} there is no owner and no
 * status restriction: every announce of every seller in every status is listed, and moderation actions (withdraw,
 * expire) dispatch the {@code Moderate*} commands with the moderator and a mandatory reason. {@code POST /reindex}
 * re-pushes every PUBLISHED announce from the read model to the search index (after an index rebuild, or for the
 * seed rows that never produced an event).
 */
@RestController
@RequestMapping("/api/admin/announces")
public class AnnounceAdminController {

    public static final String USER_HEADER = Callers.USER_HEADER;
    public static final String COMMAND_ID_HEADER = "X-Command-Id";

    private final AnnouncePublishing publishing;
    private final AnnounceBrowsing browsing;
    private final AnnounceReindexService reindex;

    public AnnounceAdminController(AnnouncePublishing publishing, AnnounceBrowsing browsing, AnnounceReindexService reindex) {
        this.publishing = publishing;
        this.browsing = browsing;
        this.reindex = reindex;
    }

    /** The reason is mandatory; it is recorded on the event and shown in the admin listing. */
    public record ModerationRequest(String reason) {}

    @GetMapping
    public Mono<PageResponse<AnnounceSummaryResponse>> list(@RequestParam(required = false) Kind kind,
                                                            @RequestParam(required = false) AnnounceStatus status,
                                                            @RequestParam(required = false) Integer seller,
                                                            @RequestParam(required = false) Integer region,
                                                            @RequestParam(required = false) List<Integer> category,
                                                            @RequestParam(required = false) SaleType saleType,
                                                            @RequestParam(required = false) String q,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size,
                                                            @RequestParam(defaultValue = "newest") String sort,
                                                            @RequestHeader HttpHeaders headers) {
        Callers.staff(headers);
        AnnounceQuery query = new AnnounceQuery(kind, category, null, seller, region, q, saleType, status, page, size, sort);
        return browsing.count(query).flatMap(total -> browsing.browse(query).map(AnnounceSummaryResponse::of).collectList()
                .map(items -> new PageResponse<>(items, query.page(), query.size(), total)));
    }

    @GetMapping("/{id}")
    public Mono<AnnounceResponse> inspect(@PathVariable String id, @RequestHeader HttpHeaders headers) {
        Callers.staff(headers);
        return browsing.inspect(id).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/withdraw")
    public Mono<AnnounceResponse> withdraw(@PathVariable String id,
                                           @RequestHeader HttpHeaders headers,
                                           @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                           @RequestBody ModerationRequest body) {
        return publishing.moderateWithdraw(id, Callers.staff(headers), body.reason(), commandId).map(AnnounceResponse::of);
    }

    @PostMapping("/{id}/expire")
    public Mono<AnnounceResponse> expire(@PathVariable String id,
                                         @RequestHeader HttpHeaders headers,
                                         @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                         @RequestBody ModerationRequest body) {
        return publishing.moderateExpire(id, Callers.staff(headers), body.reason(), commandId).map(AnnounceResponse::of);
    }

    /** Full pass over the PUBLISHED announces of the read model; answers {@code {"indexed": n}}. */
    @PostMapping("/reindex")
    public Mono<Map<String, Long>> reindex(@RequestHeader HttpHeaders headers) {
        Callers.staff(headers);
        return reindex.reindexPublished().map(n -> Map.of("indexed", n));
    }
}
