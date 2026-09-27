package org.linlinjava.announce.shipping.domain;

import com.opencqrs.framework.command.Command;
import org.linlinjava.announce.cqrs.command.Subjects;

import java.time.LocalDateTime;

/**
 * Commands of the Shipment aggregate on {@code /shipment/{orderId}}. The carrier is called by the application
 * service <em>before</em> {@link RequestShipment}, which records the booking; carrier events (webhook or tracking
 * poll) arrive as {@link RecordCarrierEvent}, idempotent on the carrier event id.
 */
public final class ShipmentCommands {

    public static final String AGGREGATE = "shipment";

    private ShipmentCommands() {}

    public static String subjectOf(String orderId) {
        return Subjects.of(AGGREGATE, orderId);
    }

    /** From OrderPaidV1: the shipment exists and only the seller may book it. */
    public record OpenShipment(String orderId, Integer sellerId, Integer buyerId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
    }

    public record RequestShipment(String orderId, Integer sellerId, String carrier, String service, String parcelSize, String trackingNumber, String labelUrl) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RecordCarrierEvent(String orderId, String carrierEventId, CarrierEventKind kind, String reason, LocalDateTime at) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** From OrderCancelledV1 / OrderRefundedV1; a no-op once the parcel is with the carrier. */
    public record CancelShipment(String orderId, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }
}
