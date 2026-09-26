package org.linlinjava.announce.messaging.domain.conversation;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.OpenListingConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.PostMessage;
import org.springframework.http.HttpStatus;

/**
 * The rules that guard each command. Business rules throw {@link CommandRejectedException} with a stable
 * {@code code} (mapped to problem details by the framework); malformed input throws
 * {@link IllegalArgumentException} (400).
 */
public final class ConversationRules {

    public static final String NOT_PARTICIPANT = "not-participant";
    public static final String NOT_BUYER = "not-buyer";
    public static final String NOT_SELLER = "not-seller";
    public static final String NOT_STAFF = "not-staff";
    public static final String BUYER_IS_SELLER = "buyer-is-seller";
    public static final String CONVERSATION_CLOSED = "conversation-closed";
    public static final String CONVERSATION_BLOCKED = "conversation-blocked";
    public static final String OFFER_NOT_ALLOWED = "offer-not-allowed";
    public static final String OFFER_ALREADY_ACCEPTED = "offer-already-accepted";
    public static final String NO_OPEN_OFFER = "no-open-offer";

    private ConversationRules() {
    }

    public static void checkOpenListing(OpenListingConversation command) {
        requireText(command.announceId(), "The announce id is required");
        if (command.buyerId() == null || command.sellerId() == null) {
            throw new IllegalArgumentException("Buyer and seller are required");
        }
        if (command.buyerId().equals(command.sellerId())) {
            throw new CommandRejectedException(HttpStatus.CONFLICT, BUYER_IS_SELLER, "A seller cannot open a conversation about their own announce");
        }
    }

    public static void checkWritable(Conversation state) {
        switch (state.status()) {
            case CLOSED -> throw new CommandRejectedException(HttpStatus.CONFLICT, CONVERSATION_CLOSED, "The conversation is closed");
            case BLOCKED -> throw new CommandRejectedException(HttpStatus.CONFLICT, CONVERSATION_BLOCKED, "The conversation is blocked");
            default -> { }
        }
    }

    public static void checkParticipant(Conversation state, Sender sender) {
        if (!state.hasParticipant(sender)) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_PARTICIPANT, "Only participants may act on this conversation");
        }
    }

    /** A participant or any staff member may close. */
    public static void checkMayClose(Conversation state, Sender by) {
        if (by.isStaff()) {
            return;
        }
        checkParticipant(state, by);
    }

    public static void checkStaff(Integer staffId) {
        if (staffId == null) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_STAFF, "Only staff may do this");
        }
    }

    public static void checkSeller(Conversation state, Integer sellerId) {
        if (state.type() != ConversationType.LISTING || sellerId == null || !sellerId.equals(state.sellerId())) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_SELLER, "Only the seller may answer an offer");
        }
    }

    public static Offer checkOpenOffer(Conversation state) {
        if (state.openOffer() == null) {
            throw new CommandRejectedException(HttpStatus.CONFLICT, NO_OPEN_OFFER, "There is no open offer");
        }
        return state.openOffer();
    }

    /** Validates the content of a message against its type and, for offers, the sender and the conversation. */
    public static void checkMessage(Conversation state, PostMessage command, int maxTextLength) {
        if (command.sender() == null || command.contentType() == null) {
            throw new IllegalArgumentException("Sender and content type are required");
        }
        switch (command.contentType()) {
            case TEXT -> requireText(command.content(), maxTextLength);
            case IMAGE -> requireText(command.attachmentUrl(), "An IMAGE message needs an attachment URL");
            case SYSTEM -> {
                if (!command.sender().isSystem()) {
                    throw new IllegalArgumentException("Only the platform posts SYSTEM messages");
                }
                requireText(command.content(), maxTextLength);
            }
            case OFFER -> {
                if (state.type() != ConversationType.LISTING) {
                    throw new CommandRejectedException(HttpStatus.CONFLICT, OFFER_NOT_ALLOWED, "Offers are made in listing conversations only");
                }
                if (command.sender().type() != SenderType.USER || !command.sender().id().equals(state.buyerId())) {
                    throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_BUYER, "Only the buyer makes offers");
                }
                if (state.acceptedOffer() != null) {
                    throw new CommandRejectedException(HttpStatus.CONFLICT, OFFER_ALREADY_ACCEPTED, "An offer was already accepted");
                }
                if (command.offerAmountCents() == null || command.offerAmountCents() <= 0) {
                    throw new IllegalArgumentException("An offer needs a positive amount");
                }
                if (command.currency() == null || command.currency().length() != 3) {
                    throw new IllegalArgumentException("An offer needs a three-letter currency code");
                }
                if (command.content() != null && command.content().length() > maxTextLength) {
                    throw new IllegalArgumentException("The message is longer than " + maxTextLength + " characters");
                }
            }
        }
    }

    private static void requireText(String value, int maxLength) {
        requireText(value, "The message text is required");
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("The message is longer than " + maxLength + " characters");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
