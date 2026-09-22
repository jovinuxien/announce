package org.linlinjava.announce.cqrs.sample;

import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.cqrs.sample.ListingCommands.ChangeListingPrice;
import org.linlinjava.announce.cqrs.sample.ListingCommands.CloseListing;
import org.linlinjava.announce.cqrs.sample.ListingCommands.CreateListing;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Commands come in over HTTP with an optional {@code X-Command-Id} header for idempotent retries; queries
 * read the {@code listing_summary} read model.
 */
@RestController
@RequestMapping("/listings")
public class ListingController {

    public record CreateRequest(String id, String title, long priceCents) {
    }

    public record PriceRequest(long priceCents) {
    }

    private final ReactiveCommandRouter router;
    private final ListingSummaryProjection summaries;

    public ListingController(ReactiveCommandRouter router, ListingSummaryProjection summaries) {
        this.router = router;
        this.summaries = summaries;
    }

    private static Map<String, ?> metaData(String commandId) {
        return commandId == null ? Map.of() : Map.of(MetaDataKeys.COMMAND_ID, commandId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, String>> create(@RequestBody CreateRequest request,
                                            @RequestHeader(value = "X-Command-Id", required = false) String commandId) {
        return router.send(new CreateListing(request.id(), request.title(), request.priceCents()), metaData(commandId), String.class)
                .map(id -> Map.of("id", id));
    }

    @PutMapping("/{id}/price")
    public Mono<Map<String, Long>> changePrice(@PathVariable String id, @RequestBody PriceRequest request,
                                               @RequestHeader(value = "X-Command-Id", required = false) String commandId) {
        return router.send(new ChangeListingPrice(id, request.priceCents()), metaData(commandId), Long.class)
                .map(price -> Map.of("priceCents", price));
    }

    @PostMapping("/{id}/close")
    public Mono<ResponseEntity<Void>> close(@PathVariable String id,
                                            @RequestHeader(value = "X-Command-Id", required = false) String commandId) {
        return router.send(new CloseListing(id), metaData(commandId), Void.class)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ListingSummaryProjection.Summary>> get(@PathVariable String id) {
        return summaries.find(id).map(summary -> summary.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()));
    }
}
