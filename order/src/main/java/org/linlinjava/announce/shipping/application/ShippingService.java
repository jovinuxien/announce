package org.linlinjava.announce.shipping.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.db.dao.reactive.AnnounceShipmentMapper;
import org.linlinjava.announce.db.domain.AnnounceShipment;
import org.linlinjava.announce.db.domain.AnnounceShipmentExample;
import org.linlinjava.announce.shipping.domain.Carrier;
import org.linlinjava.announce.shipping.domain.CarrierEventKind;
import org.linlinjava.announce.shipping.domain.Shipment;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.CancelShipment;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.OpenShipment;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.RecordCarrierEvent;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.RequestShipment;
import org.linlinjava.announce.shipping.infrastructure.carrier.CarrierWebhookSignature;
import org.linlinjava.announce.shipping.infrastructure.config.ShippingProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Use cases of the shipping context: open on payment, book with the carrier, apply carrier events (webhook or poll), cancel. */
@Service
public class ShippingService {

    private final ReactiveCommandRouter router;
    private final Carrier carrier;
    private final AnnounceShipmentMapper shipments;
    private final ShippingProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public ShippingService(ReactiveCommandRouter router, Carrier carrier, AnnounceShipmentMapper shipments, ShippingProperties properties,
                           ObjectMapper json, Clock clock) {
        this.router = router;
        this.carrier = carrier;
        this.shipments = shipments;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    public Mono<Shipment> open(String orderId, Integer sellerId, Integer buyerId, String commandId) {
        return send(new OpenShipment(orderId, sellerId, buyerId), commandId)
                .onErrorResume(CommandSubjectAlreadyExistsException.class, e -> Mono.empty());
    }

    /** The seller books: the carrier is called first, then the booking is recorded. */
    public Mono<Shipment> request(String orderId, Integer sellerId, String service, String parcelSize, String from, String to, String commandId) {
        return shipments.selectByPrimaryKey(orderId)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.NOT_FOUND, "shipment-unknown", "No shipment for order " + orderId + " (is it paid?)")))
                .flatMap(s -> {
                    if (!sellerId.equals(s.getSellerId())) {
                        return Mono.error(new CommandRejectedException(HttpStatus.FORBIDDEN, "not-seller", "Only the seller books the shipment"));
                    }
                    if (!"OPEN".equals(s.getStatus())) {
                        return Mono.error(new CommandRejectedException(HttpStatus.CONFLICT, "already-requested", "The shipment is " + s.getStatus()));
                    }
                    return carrier.book(new Carrier.BookingRequest(orderId, service, parcelSize, from, to))
                            .flatMap(b -> send(new RequestShipment(orderId, sellerId, b.carrier(), b.service(), parcelSize, b.trackingNumber(), b.labelUrl()), commandId));
                });
    }

    public Mono<Shipment> recordCarrierEvent(String orderId, String carrierEventId, CarrierEventKind kind, String reason, LocalDateTime at, String commandId) {
        return send(new RecordCarrierEvent(orderId, carrierEventId, kind, reason, at), commandId);
    }

    public Mono<Shipment> cancel(String orderId, String reason, String commandId) {
        return send(new CancelShipment(orderId, reason), commandId)
                .onErrorResume(CommandSubjectDoesNotExistException.class, e -> Mono.empty());
    }

    /** The fake carrier's webhook: {@code {carrierEventId, orderId, kind, reason?, at?}} signed with HMAC-SHA256 over the raw body. */
    public Mono<Void> handleFakeWebhook(String rawBody, String signature) {
        if (!CarrierWebhookSignature.verify(properties.getFake().getSecret(), rawBody, signature)) {
            return Mono.error(new CommandRejectedException(HttpStatus.UNAUTHORIZED, "bad-signature", "Webhook signature does not match"));
        }
        JsonNode body;
        try {
            body = json.readTree(rawBody);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Webhook body is not JSON"));
        }
        String eventId = text(body, "carrierEventId");
        String orderId = text(body, "orderId");
        String kind = text(body, "kind");
        if (eventId == null || orderId == null || kind == null) {
            return Mono.error(new IllegalArgumentException("carrierEventId, orderId and kind are required"));
        }
        LocalDateTime at = body.hasNonNull("at") ? LocalDateTime.parse(body.get("at").asText()) : LocalDateTime.now(clock);
        return recordCarrierEvent(orderId, eventId, CarrierEventKind.valueOf(kind), text(body, "reason"), at, "carrier-webhook:" + eventId).then();
    }

    /** {@code ShipmentTrackingJob}: polls the carrier for every open shipment and records what is new. Returns how many shipments were polled. */
    public Mono<Long> pollOpenShipments(int batch) {
        AnnounceShipmentExample open = new AnnounceShipmentExample();
        open.or().andStatusIn(List.of("REQUESTED", "DISPATCHED", "IN_TRANSIT")).andTrackingNumberIsNotNull();
        open.setOrderByClause("last_polled_at asc");
        LocalDateTime now = LocalDateTime.now(clock);
        return shipments.selectPageByExample(open, 0, batch)
                .flatMap(s -> carrier.track(s.getTrackingNumber())
                        .concatMap(ev -> recordCarrierEvent(s.getId(), ev.carrierEventId(), ev.kind(), ev.reason(), ev.at(), "carrier-poll:" + ev.carrierEventId()))
                        .then(Mono.defer(() -> {
                            AnnounceShipment polled = new AnnounceShipment();
                            polled.setId(s.getId());
                            polled.setLastPolledAt(now);
                            return shipments.updateByPrimaryKeySelective(polled);
                        })), 4)
                .count();
    }

    public Mono<AnnounceShipment> find(String orderId) {
        return shipments.selectByPrimaryKey(orderId);
    }

    private Mono<Shipment> send(com.opencqrs.framework.command.Command command, String commandId) {
        Map<String, ?> metaData = commandId == null || commandId.isBlank() ? Map.of() : Map.of(MetaDataKeys.COMMAND_ID, commandId);
        return router.send(command, metaData, Shipment.class);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
