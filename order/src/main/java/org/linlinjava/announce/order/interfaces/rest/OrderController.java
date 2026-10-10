package org.linlinjava.announce.order.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import org.linlinjava.announce.cqrs.security.Callers;
import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.order.application.OrderService;
import org.linlinjava.announce.order.domain.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@code /api/orders}: the buyer requests, the seller accepts or declines, either cancels before payment, the buyer
 * confirms receipt or disputes; staff resolve disputes under {@code /api/admin}. Member from {@code X-User-Id},
 * staff from the same header with {@code X-User-Type=ADMIN} (see {@code Callers}), idempotency from {@code X-Command-Id}. Reads come from the read model; a member sees
 * only the deals they are party to.
 */
@RestController
@Validated
public class OrderController {

    public static final String USER_HEADER = Callers.USER_HEADER;
    public static final String COMMAND_ID_HEADER = "X-Command-Id";

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    public record PurchaseRequest(@NotBlank String announceId) {}
    public record ReasonRequest(String reason) {}
    public record ResolutionRequest(@NotBlank String resolution, String note) {}

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Order> request(@RequestHeader HttpHeaders headers, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                               @RequestBody PurchaseRequest body) {
        Integer user = Callers.member(headers);
        return orders.requestPurchase(body.announceId(), user, commandId);
    }

    @PostMapping("/api/orders/{id}/accept")
    public Mono<Order> accept(@PathVariable String id, @RequestHeader HttpHeaders headers, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        Integer user = Callers.member(headers);
        return orders.acceptPurchase(id, user, commandId);
    }

    @PostMapping("/api/orders/{id}/decline")
    public Mono<Order> decline(@PathVariable String id, @RequestHeader HttpHeaders headers, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                               @RequestBody(required = false) ReasonRequest body) {
        Integer user = Callers.member(headers);
        return orders.declinePurchase(id, user, body == null ? null : body.reason(), commandId);
    }

    @PostMapping("/api/orders/{id}/cancel")
    public Mono<Order> cancel(@PathVariable String id, @RequestHeader HttpHeaders headers, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                              @RequestBody(required = false) ReasonRequest body) {
        Integer user = Callers.member(headers);
        return orders.cancel(id, user, body == null ? null : body.reason(), commandId);
    }

    @PostMapping("/api/orders/{id}/confirm-receipt")
    public Mono<Order> confirmReceipt(@PathVariable String id, @RequestHeader HttpHeaders headers, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        Integer user = Callers.member(headers);
        return orders.confirmReceipt(id, user, commandId);
    }

    @PostMapping("/api/orders/{id}/dispute")
    public Mono<Order> dispute(@PathVariable String id, @RequestHeader HttpHeaders headers, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                               @RequestBody ReasonRequest body) {
        Integer user = Callers.member(headers);
        return orders.openDispute(id, user, body.reason(), commandId);
    }

    @GetMapping("/api/orders/{id}")
    public Mono<AnnounceOrder> get(@PathVariable String id, @RequestHeader HttpHeaders headers) {
        Integer user = Callers.member(headers);
        return orders.findOrder(id)
                .filter(o -> user.equals(o.getBuyerId()) || user.equals(o.getSellerId()))
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order " + id)));
    }

    @GetMapping("/api/orders")
    public Flux<AnnounceOrder> list(@RequestHeader HttpHeaders headers, @RequestParam(defaultValue = "buyer") String role,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Integer user = Callers.member(headers);
        return "seller".equals(role) ? orders.findBySeller(user, (long) page * size, size) : orders.findByBuyer(user, (long) page * size, size);
    }

    @PostMapping("/api/admin/orders/{id}/resolve-dispute")
    public Mono<Order> resolve(@PathVariable String id, @RequestHeader HttpHeaders headers, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                               @RequestBody ResolutionRequest body) {
        Integer admin = Callers.staff(headers);
        return orders.resolveDispute(id, admin, body.resolution(), body.note(), commandId);
    }

    @GetMapping("/api/admin/orders/{id}")
    public Mono<AnnounceOrder> adminGet(@PathVariable String id, @RequestHeader HttpHeaders headers) {
        Integer admin = Callers.staff(headers);
        return orders.findOrder(id).switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order " + id)));
    }

    /** Runs the release pass once; for operators and tests. */
    @PostMapping("/api/admin/orders/{id}/release")
    public Mono<Order> release(@PathVariable String id, @RequestHeader HttpHeaders headers, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        Integer admin = Callers.staff(headers);
        return orders.releasePayout(id, commandId);
    }
}
