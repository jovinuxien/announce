package org.linlinjava.announce.auction.domain;

import com.opencqrs.framework.command.Command;
import org.linlinjava.announce.cqrs.command.Subjects;

import java.time.LocalDateTime;

/**
 * Commands of the Auction aggregate, dispatched through {@code ReactiveCommandRouter} to {@code /auction/{announceId}}.
 * Amounts are minor units. {@link CloseAuction} comes from the closing job, {@link OpenAuction} and the system
 * {@link CancelAuction} from the announce.v1 subscriber; the rest from members and staff over REST.
 */
public final class AuctionCommands {

    public static final String AGGREGATE = "auction";

    private AuctionCommands() {}

    public static String subjectOf(String announceId) {
        return Subjects.of(AGGREGATE, announceId);
    }

    public record OpenAuction(String announceId, Integer sellerId, String title, String mainPicture, String currency,
                              long initialPriceCents, Long reservePriceCents, Long snagItPriceCents, long incrementCents,
                              LocalDateTime startsAt, LocalDateTime endsAt, int autoExtendSec) implements Command {
        @Override public String getSubject() { return subjectOf(announceId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
    }

    /** {@code bidId} is chosen by the caller so a retried request stays one bid. */
    public record PlaceBid(String announceId, String bidId, Integer bidderId, long amountCents, Long maxAmountCents) implements Command {
        @Override public String getSubject() { return subjectOf(announceId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record SnagIt(String announceId, Integer buyerId) implements Command {
        @Override public String getSubject() { return subjectOf(announceId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** System command (closing job): closes when the end has passed, otherwise does nothing. */
    public record CloseAuction(String announceId) implements Command {
        @Override public String getSubject() { return subjectOf(announceId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** {@code actorType} USER (the seller, only while no bid), ADMIN (staff, any time) or SYSTEM (announce gone; a no-op when not open). */
    public record CancelAuction(String announceId, Integer actorId, String actorType, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(announceId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RetractBid(String announceId, String bidId, Integer bidderId) implements Command {
        @Override public String getSubject() { return subjectOf(announceId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record CancelBid(String announceId, String bidId, Integer adminId, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(announceId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }
}
