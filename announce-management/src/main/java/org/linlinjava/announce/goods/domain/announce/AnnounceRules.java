package org.linlinjava.announce.goods.domain.announce;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The invariants of the Announce aggregate, in one place. Each method throws {@link CommandRejectedException}
 * (an RFC 7807 problem with a stable {@code code}) when the command must not run.
 *
 * <ul>
 *   <li>a job or an event cannot have an auction sale type; only goods are biddable</li>
 *   <li>the kind of an announce never changes</li>
 *   <li>to publish: a location; a goods announce needs a price unless it is a giveaway or an auction;
 *       an event needs start before end and an end in the future</li>
 *   <li>SOLD applies to goods only; jobs and events end as EXPIRED or WITHDRAWN</li>
 * </ul>
 */
public final class AnnounceRules {
    private AnnounceRules() {}

    public static void checkDetails(AnnounceKind details) {
        if (details == null) {
            throw new IllegalArgumentException("The kind-specific details are required");
        }
        if (details.saleType() == SaleType.AUCTION && details.kind() != Kind.GOODS) {
            throw reject("not-biddable", "A " + details.kind().name().toLowerCase() + " announce cannot be auctioned; only goods are biddable");
        }
    }

    public static void checkEdit(Announce state, AnnounceKind newDetails) {
        checkDetails(newDetails);
        if (state.status() == AnnounceStatus.SOLD) {
            throw reject("announce-sold", "A sold announce cannot be edited");
        }
        if (newDetails.kind() != state.kind()) {
            throw reject("kind-immutable", "An announce posted as " + state.kind() + " cannot become " + newDetails.kind());
        }
    }

    public static void checkPublish(Announce state, Duration validity, LocalDateTime now) {
        if (!state.status().canBePublished()) {
            throw reject("not-publishable", "An announce in status " + state.status() + " cannot be published");
        }
        if (validity == null || validity.isNegative() || validity.isZero()) {
            throw reject("invalid-validity", "Validity must be positive");
        }
        if (state.content().location() == null) {
            throw reject("location-required", "An announce needs a location before it is published");
        }
        switch (state.details()) {
            case GoodsDetails goods -> {
                if (goods.saleType() == SaleType.FIXED_PRICE && !goods.hasPrice()) {
                    throw reject("price-required", "A fixed-price goods announce needs a price");
                }
            }
            case EventDetails event -> {
                if (!event.startsAt().isBefore(event.endsAt())) {
                    throw reject("event-dates", "An event must start before it ends");
                }
                if (!event.endsAt().isAfter(now)) {
                    throw reject("event-in-the-past", "An event that has already ended cannot be published");
                }
            }
            case JobDetails job -> {
                if (job.applicationDeadline() != null && !job.applicationDeadline().isAfter(now)) {
                    throw reject("deadline-passed", "A job whose application deadline has passed cannot be published");
                }
            }
        }
    }

    public static void checkWithdraw(Announce state) {
        if (state.status() != AnnounceStatus.PUBLISHED) {
            throw reject("not-published", "Only a published announce can be withdrawn (status " + state.status() + ")");
        }
    }

    public static void checkMarkSold(Announce state) {
        if (state.kind() != Kind.GOODS) {
            throw reject("sold-not-applicable", "Only goods can be sold; a " + state.kind().name().toLowerCase() + " ends as expired or withdrawn");
        }
        if (state.status() != AnnounceStatus.PUBLISHED) {
            throw reject("not-published", "Only a published announce can be marked sold (status " + state.status() + ")");
        }
    }

    /** Expiry is a no-op unless the announce is published and its date has passed. */
    public static boolean isDueToExpire(Announce state, LocalDateTime now) {
        return state.status() == AnnounceStatus.PUBLISHED && state.expiresAt() != null && !state.expiresAt().isAfter(now);
    }

    /** Staff actions: the moderator and a reason are mandatory (400), and only a published announce can be acted on (409). */
    public static void checkModeration(Announce state, Integer moderatorId, String reason) {
        if (moderatorId == null) {
            throw new IllegalArgumentException("The moderator is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required");
        }
        if (reason.length() > 255) {
            throw new IllegalArgumentException("The reason must not exceed 255 characters");
        }
        if (state.status() != AnnounceStatus.PUBLISHED) {
            throw reject("not-published", "Only a published announce can be moderated (status " + state.status() + ")");
        }
    }

    public static void checkOwner(Announce state, SellerId seller) {
        if (!state.isOwnedBy(seller)) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, "not-owner", "Announce " + state.id() + " does not belong to member " + seller.value());
        }
    }

    private static CommandRejectedException reject(String code, String detail) {
        return new CommandRejectedException(HttpStatus.CONFLICT, code, detail);
    }
}
