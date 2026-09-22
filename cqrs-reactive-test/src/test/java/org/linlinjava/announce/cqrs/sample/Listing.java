package org.linlinjava.announce.cqrs.sample;

/** Aggregate state, rebuilt from the events under {@code /listing/{id}}. */
public record Listing(String id, String title, long priceCents, Status status) {

    public enum Status { OPEN, CLOSED }
}
