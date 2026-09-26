package org.linlinjava.announce.goods.domain.announce;

import com.opencqrs.framework.command.Command;
import org.linlinjava.announce.cqrs.command.Subjects;

import java.time.Duration;

/**
 * Commands of the Announce aggregate, dispatched through {@code ReactiveCommandRouter} to subject
 * {@code /announce/{id}}. Every member command carries the seller so ownership can be enforced by the aggregate;
 * {@link ExpireAnnounce} is issued by the scheduler; {@link ModerateWithdraw} and {@link ModerateExpire} by staff through
 * the admin edge (no owner check, the moderator and a reason are recorded on the event).
 */
public final class AnnounceCommands {

    public static final String AGGREGATE = "announce";

    private AnnounceCommands() {}

    public static String subjectOf(String id) {
        return Subjects.of(AGGREGATE, id);
    }

    /** Creates a draft of the given kind; the client (or the facade) chooses the id. */
    public record PostAnnounce(String id, SellerId sellerId, AnnounceContent content, AnnounceKind details) implements Command {
        @Override public String getSubject() { return subjectOf(id); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
    }

    /** Replaces content and kind-specific details; the kind stays. */
    public record EditAnnounce(String id, SellerId sellerId, AnnounceContent content, AnnounceKind details) implements Command {
        @Override public String getSubject() { return subjectOf(id); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** {@code validity} null means {@link Announce#DEFAULT_VALIDITY}. */
    public record PublishAnnounce(String id, SellerId sellerId, Duration validity) implements Command {
        @Override public String getSubject() { return subjectOf(id); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record WithdrawAnnounce(String id, SellerId sellerId) implements Command {
        @Override public String getSubject() { return subjectOf(id); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record MarkSold(String id, SellerId sellerId) implements Command {
        @Override public String getSubject() { return subjectOf(id); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** System command: expires the announce when its date has passed, otherwise does nothing. */
    public record ExpireAnnounce(String id) implements Command {
        @Override public String getSubject() { return subjectOf(id); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** Staff takes a published announce down; {@code moderatorId} is the admin, {@code reason} is mandatory. */
    public record ModerateWithdraw(String id, Integer moderatorId, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(id); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** Staff ends a published announce before its date; unlike {@link ExpireAnnounce} it is not conditional on the date. */
    public record ModerateExpire(String id, Integer moderatorId, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(id); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }
}
