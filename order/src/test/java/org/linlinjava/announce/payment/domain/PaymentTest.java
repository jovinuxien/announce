package org.linlinjava.announce.payment.domain;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.test.CommandFixture;
import org.linlinjava.announce.payment.domain.PaymentCommands.CreateIntent;
import org.linlinjava.announce.payment.domain.PaymentCommands.RecordProviderEvent;
import org.linlinjava.announce.payment.domain.PaymentCommands.RequestPayout;
import org.linlinjava.announce.payment.domain.PaymentCommands.RequestRefund;
import org.linlinjava.announce.payment.domain.events.PaymentConfirmed;
import org.linlinjava.announce.payment.domain.events.PaymentIntentCreated;
import org.linlinjava.announce.payment.domain.events.PayoutRequested;
import org.linlinjava.announce.payment.domain.events.PayoutSent;
import org.linlinjava.announce.payment.domain.events.RefundRequested;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Payment aggregate: provider webhooks are idempotent on their event id and checked against the state. */
class PaymentTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    static final String ORDER = "o-1";

    static CommandFixture<Payment> fixture() {
        return CommandFixture.forAggregate(Payment.class).handlers(PaymentHandlers.definitions(CLOCK)).subject(PaymentCommands.subjectOf(ORDER));
    }

    static PaymentIntentCreated intent() {
        return new PaymentIntentCreated("a-1", 9, 7, "FAKE", "pi_1", "pi_1_secret", 5000, 900, 4100, "DKK", NOW.minusHours(1));
    }

    @Test
    void anIntentIsRecordedAndConfirmedOnce() {
        fixture().when(new CreateIntent(ORDER, "a-1", 9, 7, 5000, 900, 4100, "DKK", "FAKE", "pi_1", "pi_1_secret"))
                .thenExpectEvents(new PaymentIntentCreated("a-1", 9, 7, "FAKE", "pi_1", "pi_1_secret", 5000, 900, 4100, "DKK", NOW));
        fixture().given(intent()).when(new RecordProviderEvent(ORDER, "evt-1", ProviderEventKind.CONFIRMED, null, null, NOW))
                .thenExpectEvents(new PaymentConfirmed("evt-1", NOW));
        // the same provider event again: ignored
        fixture().given(intent(), new PaymentConfirmed("evt-1", NOW)).when(new RecordProviderEvent(ORDER, "evt-1", ProviderEventKind.CONFIRMED, null, null, NOW))
                .thenExpectNoEvents();
        // a second confirmation under a new id: also ignored, the provider repeats itself
        fixture().given(intent(), new PaymentConfirmed("evt-1", NOW)).when(new RecordProviderEvent(ORDER, "evt-2", ProviderEventKind.CONFIRMED, null, null, NOW))
                .thenExpectNoEvents();
    }

    @Test
    void payoutsAndRefundsFollowTheState() {
        assertEquals("not-confirmed", rejected(fixture().given(intent()).when(new RequestPayout(ORDER, 4100, "tr_1"))).getCode());
        fixture().given(intent(), new PaymentConfirmed("evt-1", NOW)).when(new RequestPayout(ORDER, 4100, "tr_1")).thenExpectEvents(new PayoutRequested(4100, "tr_1", NOW));
        assertEquals("payout-too-high", rejected(fixture().given(intent(), new PaymentConfirmed("evt-1", NOW)).when(new RequestPayout(ORDER, 4200, "tr_1"))).getCode());
        assertEquals("payout-not-requested", rejected(fixture().given(intent(), new PaymentConfirmed("evt-1", NOW))
                .when(new RecordProviderEvent(ORDER, "evt-9", ProviderEventKind.PAYOUT_SENT, "tr_1", null, NOW))).getCode());
        fixture().given(intent(), new PaymentConfirmed("evt-1", NOW), new PayoutRequested(4100, "tr_1", NOW))
                .when(new RecordProviderEvent(ORDER, "evt-9", ProviderEventKind.PAYOUT_SENT, "tr_1", null, NOW)).thenExpectEvents(new PayoutSent("evt-9", "tr_1", NOW));
        fixture().given(intent(), new PaymentConfirmed("evt-1", NOW)).when(new RequestRefund(ORDER, 5000, "re_1", "ship window expired"))
                .thenExpectEvents(new RefundRequested(5000, "re_1", "ship window expired", NOW));
    }

    private static CommandRejectedException rejected(CommandFixture<Payment>.Then then) {
        return then.thenExpectException(CommandRejectedException.class);
    }
}
