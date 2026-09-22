package org.linlinjava.announce.cqrs.sample;

/**
 * Internal event of the listing aggregate. Event classes are top level: OpenCQRS uses the class name as the
 * EventSourcingDB event type, and EventSourcingDB rejects the {@code $} of a nested class name.
 */
public record ListingCreated(String title, long priceCents) {
}
