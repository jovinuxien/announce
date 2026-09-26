package org.linlinjava.announce.messaging.domain.conversation;

import com.opencqrs.framework.command.Command;
import org.linlinjava.announce.cqrs.command.Subjects;

/** The commands of the Conversation aggregate; all address {@code /conversation/{id}}. */
public final class ConversationCommands {

    public static final String AGGREGATE = "conversation";

    private ConversationCommands() {
    }

    /**
     * Opens the one conversation between a buyer and the seller of an announce. The id is
     * {@link ConversationIds#listing(String, Integer)}, so a second open of the same pair finds the existing
     * conversation and writes nothing (condition NONE).
     */
    public record OpenListingConversation(String id, String announceId, Integer buyerId, Integer sellerId) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.NONE;
        }
    }

    /** A member asks for support; the conversation waits until a staff member joins. */
    public record OpenSupportConversation(String id, Integer memberId, String subject) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.PRISTINE;
        }
    }

    /** A staff member joins (support queue pick-up, or moderation of a listing conversation). Idempotent. */
    public record JoinConversation(String id, Integer staffId) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.EXISTS;
        }
    }

    /**
     * Posts a message. {@code clientMsgId} makes it idempotent: a repeat with the same id answers with the original
     * message and writes no event. {@code offerAmountCents} and {@code currency} apply to OFFER messages only.
     */
    public record PostMessage(String id, String clientMsgId, Sender sender, ContentType contentType, String content,
                              String attachmentUrl, Long offerAmountCents, String currency) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.EXISTS;
        }
    }

    /** The seller accepts the latest open offer. */
    public record AcceptOffer(String id, Integer sellerId) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.EXISTS;
        }
    }

    /** The seller declines the latest open offer. */
    public record DeclineOffer(String id, Integer sellerId) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.EXISTS;
        }
    }

    /** A participant or a staff member closes the conversation. Idempotent. */
    public record CloseConversation(String id, Sender by) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.EXISTS;
        }
    }

    /** Staff blocks the conversation. Idempotent. */
    public record BlockConversation(String id, Integer staffId, String reason) implements Command {
        @Override
        public String getSubject() {
            return Subjects.of(AGGREGATE, id);
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return SubjectCondition.EXISTS;
        }
    }
}
