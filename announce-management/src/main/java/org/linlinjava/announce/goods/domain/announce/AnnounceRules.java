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
 *   <li>an auction schedule exists only on AUCTION goods, starts before it ends, ends no later than the expiry,
 *       and is not already over when published; missing dates are resolved at publish ({@link #resolveSchedule})</li>
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
        if (details instanceof GoodsDetails goods && goods.hasSchedule()) {
            if (goods.saleType() != SaleType.AUCTION) {
                throw reject("auction-schedule-not-allowed", "Only an auction has a start and an end; this announce is " + goods.saleType());
            }
            checkScheduleOrder(goods.auctionStartsAt(), goods.auctionEndsAt());
        }
    }

    private static void checkScheduleOrder(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw reject("auction-schedule-order", "An auction must start before it ends");
        }
    }

    /**
     * The schedule a published auction runs on: a missing start is the publish time, a missing end is start + the
     * default duration capped by the expiry. Explicit dates are kept. Returns the details unchanged for anything
     * that is not an auction.
     */
    public static AnnounceKind resolveSchedule(AnnounceKind details, LocalDateTime publishedAt, LocalDateTime expiresAt, Duration defaultDuration) {
        if (!(details instanceof GoodsDetails goods) || goods.saleType() != SaleType.AUCTION) {
            return details;
        }
        LocalDateTime start = goods.auctionStartsAt() != null ? goods.auctionStartsAt() : publishedAt;
        LocalDateTime end = goods.auctionEndsAt();
        if (end == null) {
            end = start.plus(defaultDuration);
            if (expiresAt != null && expiresAt.isBefore(end)) {
                end = expiresAt;
            }
        }
        return goods.withSchedule(start, end);
    }

    /** The resolved schedule must be a window that is still ahead and inside the announce's lifetime. */
    public static void checkSchedule(AnnounceKind details, LocalDateTime expiresAt, LocalDateTime now) {
        if (!(details instanceof GoodsDetails goods) || goods.saleType() != SaleType.AUCTION) {
            return;
        }
        checkScheduleOrder(goods.auctionStartsAt(), goods.auctionEndsAt());
        if (expiresAt != null && goods.auctionEndsAt().isAfter(expiresAt)) {
            throw reject("auction-ends-after-expiry", "The auction cannot end after the announce expires (" + expiresAt + ")");
        }
        if (!goods.auctionEndsAt().isAfter(now)) {
            throw reject("auction-already-over", "The auction end " + goods.auctionEndsAt() + " has already passed");
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

    /**
     * The details an edit stores. On a published auction the schedule is never unset: a date the edit leaves null is
     * carried over from the current state, and the result must still be a window ahead of {@code now} and inside the
     * expiry. On a draft the dates are stored as given and resolved at publish.
     */
    public static AnnounceKind editedDetails(Announce state, AnnounceKind newDetails, LocalDateTime now) {
        if (!state.status().isPublished()
                || !(newDetails instanceof GoodsDetails goods) || goods.saleType() != SaleType.AUCTION
                || !(state.details() instanceof GoodsDetails current) || current.saleType() != SaleType.AUCTION) {
            return newDetails;
        }
        GoodsDetails merged = goods.withSchedule(
                goods.auctionStartsAt() != null ? goods.auctionStartsAt() : current.auctionStartsAt(),
                goods.auctionEndsAt() != null ? goods.auctionEndsAt() : current.auctionEndsAt());
        if (merged.auctionEndsAt() != null) {
            checkSchedule(merged, state.expiresAt(), now);
        }
        return merged;
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

    /** Relisting (a deal fell through) is a no-op unless the announce is SOLD. */
    public static boolean canBeRelisted(Announce state) {
        return state.status() == AnnounceStatus.SOLD;
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
