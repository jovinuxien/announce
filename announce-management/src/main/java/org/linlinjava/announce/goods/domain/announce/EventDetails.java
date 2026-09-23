package org.linlinjava.announce.goods.domain.announce;

import java.time.LocalDateTime;

/**
 * Something happening at a time and place. Never biddable ({@code saleType} is normally null, see {@link JobDetails}).
 * The venue is free text; the announce's {@link Location} gives region, city and coordinates. Start before end and
 * end in the future are publish rules ({@link AnnounceRules}), because a draft may still be incomplete.
 */
public record EventDetails(LocalDateTime startsAt,
                           LocalDateTime endsAt,
                           String venue,
                           String organizer,
                           boolean ticketed,
                           Money ticketPrice,
                           Integer capacity,
                           SaleType saleType) implements AnnounceKind {

    public EventDetails {
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("An event announce needs a start and an end date-time");
        }
        venue = venue == null ? "" : venue.trim();
        organizer = organizer == null ? "" : organizer.trim();
        if (ticketed && (ticketPrice == null || ticketPrice.isZero())) {
            throw new IllegalArgumentException("A ticketed event needs a ticket price");
        }
        if (!ticketed) {
            ticketPrice = null;
        }
        if (capacity != null && capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive when given");
        }
    }

    public static EventDetails free(LocalDateTime startsAt, LocalDateTime endsAt, String venue, String organizer) {
        return new EventDetails(startsAt, endsAt, venue, organizer, false, null, null, null);
    }

    @Override
    public Kind kind() {
        return Kind.EVENT;
    }
}
