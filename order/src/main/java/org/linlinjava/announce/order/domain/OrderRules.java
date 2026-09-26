package org.linlinjava.announce.order.domain;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * The invariants of the Order aggregate, each answered with an RFC 7807 problem carrying a stable {@code code}.
 * The release rule ({@code doc/order-context.md} §3): the payout is released only when the payment was confirmed,
 * the carrier reported delivery (or dispatch, per {@code order.release.on}), no dispute is open, and the grace has
 * elapsed or the buyer confirmed receipt.
 */
public final class OrderRules {
    private OrderRules() {}

    /** Outcome of the release check: an error to throw, a "not yet", or go. */
    public enum Release { GO, NOT_YET }

    public static void checkCreate(String announceId, Integer buyerId, Integer sellerId, long priceCents) {
        if (announceId == null || announceId.isBlank()) throw new IllegalArgumentException("The announce is required");
        if (buyerId == null || sellerId == null) throw new IllegalArgumentException("Buyer and seller are required");
        if (buyerId.equals(sellerId)) throw reject("own-announce", "A member cannot buy their own announce");
        if (priceCents < 0) throw new IllegalArgumentException("The price must not be negative");
    }

    public static void checkAccept(Order o, Integer sellerId) {
        checkSeller(o, sellerId);
        if (o.status() != OrderStatus.PROPOSED) throw reject("not-proposed", "Only a proposed purchase can be accepted or declined (status " + o.status() + ")");
    }

    public static void checkPaymentConfirmed(Order o) {
        if (!(o.status() == OrderStatus.AGREED || o.status() == OrderStatus.PAYMENT_PENDING)) {
            throw reject("not-payable", "A payment cannot be confirmed for an order in status " + o.status());
        }
    }

    public static void checkShipped(Order o) {
        if (o.status() != OrderStatus.PAID) throw reject("not-paid", "Only a paid order can be shipped (status " + o.status() + ")");
    }

    public static void checkDelivered(Order o) {
        if (o.status() != OrderStatus.SHIPPED && !(o.status() == OrderStatus.DISPUTED && o.statusBeforeDispute() == OrderStatus.SHIPPED)) {
            throw reject("not-shipped", "Only a shipped order can be delivered (status " + o.status() + ")");
        }
    }

    public static void checkConfirmReceipt(Order o, Integer buyerId) {
        checkBuyer(o, buyerId);
        if (o.status() != OrderStatus.SHIPPED && o.status() != OrderStatus.DELIVERED) {
            throw reject("not-shipped", "Receipt can be confirmed once the parcel is on its way (status " + o.status() + ")");
        }
    }

    public static void checkOpenDispute(Order o, OrderCommands.OpenDispute c) {
        if ("USER".equals(c.openedByType())) checkBuyer(o, c.openedBy());
        if (!o.status().isPaidAndOpen() || o.status() == OrderStatus.DISPUTED) {
            throw reject("not-disputable", "A dispute needs a paid, unsettled order (status " + o.status() + ")");
        }
    }

    public static void checkResolveDispute(Order o, OrderCommands.ResolveDispute c) {
        if (o.status() != OrderStatus.DISPUTED) throw reject("no-dispute", "No open dispute (status " + o.status() + ")");
        if (!"RELEASE".equals(c.resolution()) && !"REFUND".equals(c.resolution())) {
            throw new IllegalArgumentException("resolution must be RELEASE or REFUND");
        }
    }

    /** The release rule. Throws for hard blocks (dispute open, carrier not far enough), answers NOT_YET while the grace runs. */
    public static Release checkRelease(Order o, OrderPolicies p, LocalDateTime now) {
        if (o.status() == OrderStatus.DISPUTED) throw reject("dispute-open", "The payout is blocked while a dispute is open");
        if (o.status() == OrderStatus.RELEASING || o.status() == OrderStatus.SETTLED) return Release.NOT_YET;   // already asked
        if (o.paidAt() == null) throw reject("not-paid", "No confirmed payment");
        if (o.status() == OrderStatus.DELIVERED) {
            if (o.receiptConfirmedAt() != null || !o.deliveredAt().plus(p.deliveryGrace()).isAfter(now)) return Release.GO;
            return Release.NOT_YET;
        }
        if (o.status() == OrderStatus.SHIPPED) {
            if (p.releaseOn() == OrderPolicies.ReleaseOn.DISPATCH) {
                if (o.receiptConfirmedAt() != null || !o.shippedAt().plus(p.deliveryGrace()).isAfter(now)) return Release.GO;
                return Release.NOT_YET;
            }
            // DELIVERY mode: the buyer's confirmation counts as delivery; otherwise wait for the dispatch grace
            if (o.receiptConfirmedAt() != null || !o.shippedAt().plus(p.dispatchGrace()).isAfter(now)) return Release.GO;
            throw reject("not-delivered", "The carrier has not confirmed delivery yet");
        }
        throw reject("not-releasable", "The payout cannot be released in status " + o.status());
    }

    public static void checkCancel(Order o, OrderCommands.Cancel c) {
        if ("USER".equals(c.actorType()) && !o.isParty(c.actorId())) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, "not-party", "Only the buyer or the seller can cancel");
        }
        if (!o.status().isBeforePayment()) throw reject("already-paid", "A paid order cannot be cancelled; open a dispute (status " + o.status() + ")");
    }

    public static boolean isPayWindowOver(Order o, OrderPolicies p, LocalDateTime now) {
        return (o.status() == OrderStatus.AGREED || o.status() == OrderStatus.PAYMENT_PENDING) && !o.agreedAt().plus(p.payWindow()).isAfter(now);
    }

    public static boolean isShipWindowOver(Order o, OrderPolicies p, LocalDateTime now) {
        return o.status() == OrderStatus.PAID && !o.paidAt().plus(p.shipWindow()).isAfter(now);
    }

    public static void checkSeller(Order o, Integer sellerId) {
        if (!o.sellerId().equals(sellerId)) throw new CommandRejectedException(HttpStatus.FORBIDDEN, "not-seller", "Only the seller may do this");
    }

    public static void checkBuyer(Order o, Integer buyerId) {
        if (!o.buyerId().equals(buyerId)) throw new CommandRejectedException(HttpStatus.FORBIDDEN, "not-buyer", "Only the buyer may do this");
    }

    private static CommandRejectedException reject(String code, String detail) {
        return new CommandRejectedException(HttpStatus.CONFLICT, code, detail);
    }
}
