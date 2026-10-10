package org.linlinjava.announce.shipping.interfaces.rest;

import org.linlinjava.announce.cqrs.security.Callers;
import org.linlinjava.announce.db.domain.AnnounceShipment;
import org.linlinjava.announce.shipping.application.ShippingService;
import org.linlinjava.announce.shipping.domain.Shipment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** {@code /api/orders/{id}/shipment}: the seller books after payment and both parties read the tracking state; carrier webhooks under {@code /api/shipping/webhook/{carrier}}. */
@RestController
public class ShipmentController {

    public static final String USER_HEADER = Callers.USER_HEADER;
    public static final String COMMAND_ID_HEADER = "X-Command-Id";
    public static final String FAKE_SIGNATURE_HEADER = "X-Fake-Signature";

    private final ShippingService shipping;

    public ShipmentController(ShippingService shipping) {
        this.shipping = shipping;
    }

    public record ShipmentRequest(String service, String parcelSize, String fromAddress, String toAddress) {}

    @PostMapping("/api/orders/{id}/shipment")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Shipment> request(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                  @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId, @RequestBody ShipmentRequest body) {
        Integer user = Callers.member(headers);
        return shipping.request(id, user, body.service(), body.parcelSize(), body.fromAddress(), body.toAddress(), commandId);
    }

    @GetMapping("/api/orders/{id}/shipment")
    public Mono<AnnounceShipment> get(@PathVariable String id, @RequestHeader HttpHeaders headers) {
        Integer user = Callers.member(headers);
        return shipping.find(id)
                .filter(s -> user.equals(s.getBuyerId()) || user.equals(s.getSellerId()))
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No shipment for order " + id)));
    }

    /** Body: the fake carrier's event as JSON; header {@code X-Fake-Signature}: HMAC-SHA256 hex of the raw body with {@code shipping.fake.secret}. */
    @PostMapping("/api/shipping/webhook/fake")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> fakeWebhook(@RequestBody String body, @RequestHeader(value = FAKE_SIGNATURE_HEADER, required = false) String signature) {
        return shipping.handleFakeWebhook(body, signature);
    }

    @PostMapping("/api/shipping/webhook/postnord")
    public Mono<Void> postnordWebhook(@RequestBody String body) {
        return Mono.error(new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "PostNord webhooks are not implemented yet"));
    }
}
