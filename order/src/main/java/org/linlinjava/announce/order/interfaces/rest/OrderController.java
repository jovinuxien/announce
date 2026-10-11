package org.linlinjava.announce.order.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import org.linlinjava.announce.cqrs.security.Callers;
import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.order.application.OrderService;
import org.linlinjava.announce.order.application.Payouts;
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

import java.util.List;

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
    /** The admin page envelope ({@code page} is 1-based), as the admin edge and announce-management answer it. */
    public record PageResponse<T>(List<T> items, int page, int size, long total) {}
    /** {@code GET /api/admin/payouts}: the sums over every order holding money plus one page of lines. */
    public record PayoutOverview(Payouts.PayoutTotals totals, PageResponse<Payouts.PayoutRow> page) {}

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

    /** Staff: every order in any status, newest change first; filters {@code status}, {@code seller}, {@code buyer}, {@code q} (title). */
    @GetMapping("/api/admin/orders")
    public Mono<PageResponse<AnnounceOrder>> adminList(@RequestHeader HttpHeaders headers,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Integer seller,
                                                       @RequestParam(required = false) Integer buyer,
                                                       @RequestParam(required = false) String q,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Callers.staff(headers);
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        List<String> statuses = status == null || status.isBlank() ? List.of() : List.of(status.trim().toUpperCase(java.util.Locale.ROOT));
        OrderService.OrderFilter filter = new OrderService.OrderFilter(statuses, seller, buyer, q);
        return orders.countOrders(filter).flatMap(total -> orders.findOrders(filter, (long) (p - 1) * s, s).collectList()
                .map(items -> new PageResponse<>(items, p, s, total)));
    }

    /**
     * Staff: where the sellers' money stands. {@code status} is a {@link Payouts.PayoutStatus}
     * (PENDING_RELEASE, BLOCKED_DISPUTE, RELEASING, SENT, REFUNDED); without it every order that holds or held
     * money is listed. The totals always cover all of them.
     */
    @GetMapping("/api/admin/payouts")
    public Mono<PayoutOverview> adminPayouts(@RequestHeader HttpHeaders headers,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Integer seller,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Callers.staff(headers);
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        List<String> statuses = status == null || status.isBlank() ? Payouts.PayoutStatus.allOrderStatusNames()
                : Payouts.PayoutStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)).orderStatusNames();
        OrderService.OrderFilter filter = new OrderService.OrderFilter(statuses, seller, null, null);
        Mono<PageResponse<Payouts.PayoutRow>> lines = orders.countOrders(filter)
                .flatMap(total -> orders.findOrders(filter, (long) (p - 1) * s, s).concatMap(orders::payoutRow).collectList()
                        .map(items -> new PageResponse<>(items, p, s, total)));
        return Mono.zip(orders.payoutTotals(), lines, PayoutOverview::new);
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
