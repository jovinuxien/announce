package org.linlinjava.announce.shipping.domain;

/** OPEN (paid, not booked) -> REQUESTED -> DISPATCHED -> IN_TRANSIT -> DELIVERED; FAILED from any carrier state; CANCELLED before dispatch. */
public enum ShipmentStatus {
    OPEN, REQUESTED, DISPATCHED, IN_TRANSIT, DELIVERED, FAILED, CANCELLED;

    /** The carrier still has something to report. */
    public boolean isTracked() {
        return this == REQUESTED || this == DISPATCHED || this == IN_TRANSIT;
    }
}
