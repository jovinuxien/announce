package org.linlinjava.announce.order.api;

/** Kafka topics of the order module, one per bounded context. Consumers bind {@code <function>-in-0.destination} to these names. */
public final class OrderTopics {
    /** Auction lifecycle, keyed by {@code /auction/{announceId}}. */
    public static final String AUCTION_V1 = "auction.v1";
    /** Deal lifecycle, keyed by {@code /order/{orderId}}. */
    public static final String ORDER_V1 = "order.v1";
    /** Payment provider facts, keyed by {@code /payment/{orderId}}. */
    public static final String PAYMENT_V1 = "payment.v1";
    /** Carrier facts, keyed by {@code /shipment/{orderId}}. */
    public static final String SHIPPING_V1 = "shipping.v1";
    /** Published by the messaging module; the order module consumes {@code OfferAcceptedV1} from it. */
    public static final String MESSAGING_V1 = "messaging.v1";

    private OrderTopics() {}
}
