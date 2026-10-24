package org.linlinjava.announce.cqrs.sample;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.cqrs.sample.ListingCommands.ChangeListingPrice;
import org.linlinjava.announce.cqrs.sample.ListingCommands.CloseListing;
import org.linlinjava.announce.cqrs.sample.ListingCommands.CreateListing;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The service implementation implements the descriptor: {@link ListingApi} carries the class-level path and the
 * mappings of {@code post} and {@code view}, so this class has no {@code @RequestMapping} and those two methods no
 * {@code @PostMapping} / {@code @GetMapping}. {@code @ResponseStatus} is not a mapping annotation and stays on the
 * implementation. The other routes are not on the descriptor and keep their own {@code @PutMapping} /
 * {@code @PostMapping}, combined with the interface's {@code /listings} prefix; mixing the two annotation families
 * is legal across methods, never on one method or on the class itself.
 *
 * <p>Commands come in over HTTP with an optional {@code X-Command-Id} header for idempotent retries; queries read
 * the {@code listing_summary} read model.
 */
@RestController
public class ListingController implements ListingApi {

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

    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, String>> post(CreateRequest request, String commandId) {
        return router.send(new CreateListing(request.id(), request.title(), request.priceCents()), metaData(commandId), String.class)
                .map(id -> Map.of("id", id));
    }

    @Override
    public Mono<ListingSummaryProjection.Summary> view(String id) {
        return summaries.find(id).flatMap(summary -> summary.map(Mono::just).orElseGet(() ->
                Mono.error(new CommandRejectedException(HttpStatus.NOT_FOUND, "listing-not-found", "No listing " + id))));
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
}
