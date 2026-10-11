package org.linlinjava.announce.order.application;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderSellerMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderSnapshotMapper;
import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.db.domain.AnnounceOrderExample;
import org.linlinjava.announce.db.domain.AnnounceOrderSnapshot;
import org.linlinjava.announce.order.domain.Order;
import org.linlinjava.announce.order.domain.OrderCommands.AcceptPurchase;
import org.linlinjava.announce.order.domain.OrderCommands.Cancel;
import org.linlinjava.announce.order.domain.OrderCommands.ConfirmReceipt;
import org.linlinjava.announce.order.domain.OrderCommands.CreateFromAuction;
import org.linlinjava.announce.order.domain.OrderCommands.CreateFromOffer;
import org.linlinjava.announce.order.domain.OrderCommands.DeclinePurchase;
import org.linlinjava.announce.order.domain.OrderCommands.ExpireShipWindow;
import org.linlinjava.announce.order.domain.OrderCommands.OpenDispute;
import org.linlinjava.announce.order.domain.OrderCommands.ReleasePayout;
import org.linlinjava.announce.order.domain.OrderCommands.RequestPurchase;
import org.linlinjava.announce.order.domain.OrderCommands.ResolveDispute;
import org.linlinjava.announce.order.infrastructure.config.OrderProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Use cases of the order context. The state machine lives in the aggregate; this facade supplies what the aggregate
 * cannot see (the announce snapshot, whether the seller can be paid), dispatches through the framework router and
 * answers queries from {@code announce_order}, never from the event store.
 */
@Service
public class OrderService {

    private final ReactiveCommandRouter router;
    private final AnnounceOrderMapper orders;
    private final AnnounceOrderSnapshotMapper snapshots;
    private final AnnounceOrderSellerMapper sellers;
    private final OrderProperties properties;
    private final Clock clock;

    public OrderService(ReactiveCommandRouter router, AnnounceOrderMapper orders, AnnounceOrderSnapshotMapper snapshots,
                        AnnounceOrderSellerMapper sellers, OrderProperties properties, Clock clock) {
        this.router = router;
        this.orders = orders;
        this.snapshots = snapshots;
        this.sellers = sellers;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ the three doors

    /** Fixed-price door: the buyer clicks Buy on a published, non-auction goods announce. */
    public Mono<Order> requestPurchase(String announceId, Integer buyerId, String commandId) {
        return snapshot(announceId).flatMap(s -> {
            if (!"PUBLISHED".equals(s.getStatus())) {
                return Mono.error(new CommandRejectedException(HttpStatus.CONFLICT, "announce-not-available", "Announce " + announceId + " is " + s.getStatus()));
            }
            if (!"GOODS".equals(s.getKind()) || "AUCTION".equals(s.getSaleType())) {
                return Mono.error(new CommandRejectedException(HttpStatus.CONFLICT, "not-purchasable", "Announce " + announceId + " is not for direct purchase"));
            }
            String orderId = UUID.randomUUID().toString();
            return send(new RequestPurchase(orderId, announceId, buyerId, s.getSellerId(), cents(s), s.getCurrency(), s.getTitle(),
                    s.getMainPicture(), s.getCategoryId()), commandId);
        });
    }

    /** Only the seller (403 otherwise); refused with {@code seller-not-payable} until the seller's payout account is enabled. */
    public Mono<Order> acceptPurchase(String orderId, Integer sellerId, String commandId) {
        return orders.selectByPrimaryKey(orderId)
                .filter(o -> !sellerId.equals(o.getSellerId()))
                .flatMap(o -> Mono.<Void>error(new CommandRejectedException(HttpStatus.FORBIDDEN, "not-seller", "Only the seller may accept")))
                .then(checkSellerPayable(sellerId))
                .then(Mono.defer(() -> send(new AcceptPurchase(orderId, sellerId), commandId)));
    }

    public Mono<Order> declinePurchase(String orderId, Integer sellerId, String reason, String commandId) {
        return send(new DeclinePurchase(orderId, sellerId, reason), commandId);
    }

    /** Auction door; the order id is derived from the announce so a redelivered closing event maps to the same order. */
    public Mono<Order> createFromAuction(String announceId, Integer buyerId, Integer sellerId, long priceCents, String currency, String commandId) {
        String orderId = deterministicId("auction:" + announceId);
        return snapshotOrEmpty(announceId).flatMap(s -> send(new CreateFromAuction(orderId, announceId, buyerId, sellerId, priceCents,
                currency == null ? properties.getCurrency() : currency, s.getTitle(), s.getMainPicture(), s.getCategoryId(), announceId), commandId));
    }

    /** Chat-offer door; one order per conversation. */
    public Mono<Order> createFromOffer(String conversationId, String announceId, Integer buyerId, Integer sellerId, long amountCents,
                                       String currency, String commandId) {
        String orderId = deterministicId("offer:" + conversationId);
        return snapshotOrEmpty(announceId).flatMap(s -> send(new CreateFromOffer(orderId, announceId, buyerId, sellerId, amountCents,
                currency == null ? properties.getCurrency() : currency, s.getTitle(), s.getMainPicture(), s.getCategoryId(), conversationId), commandId));
    }

    // ------------------------------------------------------------------ buyer, seller, staff

    public Mono<Order> cancel(String orderId, Integer userId, String reason, String commandId) {
        return send(new Cancel(orderId, userId, "USER", reason), commandId);
    }

    public Mono<Order> confirmReceipt(String orderId, Integer buyerId, String commandId) {
        return send(new ConfirmReceipt(orderId, buyerId), commandId);
    }

    public Mono<Order> openDispute(String orderId, Integer buyerId, String reason, String commandId) {
        return send(new OpenDispute(orderId, buyerId, "USER", reason), commandId);
    }

    public Mono<Order> resolveDispute(String orderId, Integer adminId, String resolution, String note, String commandId) {
        return send(new ResolveDispute(orderId, adminId, resolution, note), commandId);
    }

    public Mono<Order> releasePayout(String orderId, String commandId) {
        return send(new ReleasePayout(orderId), commandId);
    }

    /** Generic dispatch for the subscribers (facts from the other contexts). */
    public Mono<Order> send(com.opencqrs.framework.command.Command command, String commandId) {
        Map<String, ?> metaData = commandId == null || commandId.isBlank() ? Map.of() : Map.of(MetaDataKeys.COMMAND_ID, commandId);
        return router.send(command, metaData, Order.class);
    }

    // ------------------------------------------------------------------ jobs (read model -> commands)

    /** Pay window: agreed but unpaid orders past the window are cancelled. */
    public Mono<Long> expirePayWindows(int batch) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.getWindow().getPay());
        AnnounceOrderExample due = new AnnounceOrderExample();
        due.or().andStatusIn(List.of("AGREED", "PAYMENT_PENDING")).andAgreedAtLessThanOrEqualTo(cutoff).andDeletedEqualTo(false);
        return orders.selectPageByExample(due, 0, batch)
                .flatMap(o -> send(new Cancel(o.getId(), null, "SYSTEM", "pay window expired"), null), 4).count();
    }

    /** Ship window: paid orders the seller did not ship in time are refunded. */
    public Mono<Long> expireShipWindows(int batch) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.getWindow().getShip());
        AnnounceOrderExample due = new AnnounceOrderExample();
        due.or().andStatusEqualTo("PAID").andPaidAtLessThanOrEqualTo(cutoff).andDeletedEqualTo(false);
        return orders.selectPageByExample(due, 0, batch)
                .flatMap(o -> send(new ExpireShipWindow(o.getId()), null), 4).count();
    }

    /** Release: delivered (or shipped) orders get a ReleasePayout; the aggregate applies the grace and the guards. */
    public Mono<Long> releaseDue(int batch) {
        AnnounceOrderExample candidates = new AnnounceOrderExample();
        candidates.or().andStatusIn(List.of("DELIVERED", "SHIPPED")).andDeletedEqualTo(false);
        candidates.setOrderByClause("update_time asc");
        return orders.selectPageByExample(candidates, 0, batch)
                .flatMap(o -> send(new ReleasePayout(o.getId()), null)
                        .onErrorResume(CommandRejectedException.class, e -> Mono.empty()), 4)   // not-delivered yet: try again next run
                .count();
    }

    // ------------------------------------------------------------------ queries (read model)

    public Mono<AnnounceOrder> findOrder(String orderId) {
        return orders.selectByPrimaryKey(orderId).filter(o -> !Boolean.TRUE.equals(o.getDeleted()));
    }

    public Flux<AnnounceOrder> findByBuyer(Integer buyerId, long offset, int limit) {
        AnnounceOrderExample e = new AnnounceOrderExample();
        e.or().andBuyerIdEqualTo(buyerId).andDeletedEqualTo(false);
        e.setOrderByClause("update_time desc");
        return orders.selectPageByExample(e, offset, limit);
    }

    public Flux<AnnounceOrder> findBySeller(Integer sellerId, long offset, int limit) {
        AnnounceOrderExample e = new AnnounceOrderExample();
        e.or().andSellerIdEqualTo(sellerId).andDeletedEqualTo(false);
        e.setOrderByClause("update_time desc");
        return orders.selectPageByExample(e, offset, limit);
    }

    /** Staff listing filter: every field optional; {@code statuses} are order statuses, {@code q} matches the title. */
    public record OrderFilter(java.util.List<String> statuses, Integer sellerId, Integer buyerId, String q) {
        AnnounceOrderExample toExample() {
            AnnounceOrderExample example = new AnnounceOrderExample();
            var c = example.or().andDeletedEqualTo(false);
            if (statuses != null && !statuses.isEmpty()) c.andStatusIn(statuses);
            if (sellerId != null) c.andSellerIdEqualTo(sellerId);
            if (buyerId != null) c.andBuyerIdEqualTo(buyerId);
            if (q != null && !q.isBlank()) c.andTitleLike("%" + q.trim() + "%");
            example.setOrderByClause("update_time desc, id");
            return example;
        }
    }

    public Mono<Long> countOrders(OrderFilter filter) {
        return orders.countByExample(filter.toExample());
    }

    public Flux<AnnounceOrder> findOrders(OrderFilter filter, long offset, int limit) {
        return orders.selectPageByExample(filter.toExample(), offset, limit);
    }

    /** The payout line of an order: its money, the seller gate and when the release rule fires ({@link Payouts}). */
    public Mono<Payouts.PayoutRow> payoutRow(AnnounceOrder order) {
        return sellers.selectByPrimaryKey(order.getSellerId())
                .map(gate -> Payouts.row(order, gate, properties.toPolicies()))
                .defaultIfEmpty(Payouts.row(order, null, properties.toPolicies()));
    }

    /** Sums over every live order that holds or held money. */
    public Mono<Payouts.PayoutTotals> payoutTotals() {
        return orders.sumByStatus().collectList().map(rows -> Payouts.totals(rows, properties.getCurrency()));
    }

    // ------------------------------------------------------------------ helpers

    private Mono<AnnounceOrderSnapshot> snapshot(String announceId) {
        return snapshots.selectByPrimaryKey(announceId)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.NOT_FOUND, "announce-unknown", "Announce " + announceId + " is not known to the order context")));
    }

    /** For the auction and offer doors: the deal proceeds even when the snapshot is missing (title unknown). */
    private Mono<AnnounceOrderSnapshot> snapshotOrEmpty(String announceId) {
        return snapshots.selectByPrimaryKey(announceId).defaultIfEmpty(new AnnounceOrderSnapshot());
    }

    private Mono<Void> checkSellerPayable(Integer sellerId) {
        if (!properties.getSellerAccount().isRequired()) {
            return Mono.empty();
        }
        return sellers.selectByPrimaryKey(sellerId)
                .filter(s -> Boolean.TRUE.equals(s.getPayoutsEnabled()))
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.CONFLICT, "seller-not-payable",
                        "Seller " + sellerId + " has no enabled payout account; the purchase cannot be accepted")))
                .then();
    }

    private static long cents(AnnounceOrderSnapshot s) {
        return s.getPrice() == null ? 0L : s.getPrice().movePointRight(2).longValueExact();
    }

    static String deterministicId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
