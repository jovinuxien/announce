package org.linlinjava.announce.messaging.domain.conversation;

import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.AcceptOffer;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.BlockConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.CloseConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.DeclineOffer;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.JoinConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.OpenListingConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.OpenSupportConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.PostMessage;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationBlocked;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationClosed;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationOpened;
import org.linlinjava.announce.messaging.domain.conversation.events.MessagePosted;
import org.linlinjava.announce.messaging.domain.conversation.events.OfferAccepted;
import org.linlinjava.announce.messaging.domain.conversation.events.OfferDeclined;
import org.linlinjava.announce.messaging.domain.conversation.events.ParticipantJoined;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Command handling and state rebuilding of the Conversation aggregate as explicit OpenCQRS definitions (the style
 * of the cqrs-reactive sample and of announce-management). Every handler checks {@link ConversationRules},
 * publishes at most one event and returns the resulting state (or the {@link PostResult} of a post), so callers can
 * answer without waiting for the read model. {@link #definitions(Clock, int)} is what tests hand to
 * {@code CommandFixture.handlers(...)}.
 */
@Configuration(proxyBeanMethods = false)
public class ConversationHandlers {

    /** Default for {@code messaging.max-text-length}. */
    public static final int DEFAULT_MAX_TEXT_LENGTH = 4000;

    // ------------------------------------------------------------------ state rebuilding

    public static final StateRebuildingHandlerDefinition<Conversation, ConversationOpened> ON_OPENED = new StateRebuildingHandlerDefinition<>(
            Conversation.class, ConversationOpened.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<Conversation, ConversationOpened>) (state, event, metaData, subject) ->
                    Conversation.apply(Subjects.idOf(subject), event));

    public static final StateRebuildingHandlerDefinition<Conversation, ParticipantJoined> ON_JOINED = new StateRebuildingHandlerDefinition<>(
            Conversation.class, ParticipantJoined.class, (StateRebuildingHandler.FromObject<Conversation, ParticipantJoined>) Conversation::apply);

    public static final StateRebuildingHandlerDefinition<Conversation, MessagePosted> ON_POSTED = new StateRebuildingHandlerDefinition<>(
            Conversation.class, MessagePosted.class, (StateRebuildingHandler.FromObject<Conversation, MessagePosted>) Conversation::apply);

    public static final StateRebuildingHandlerDefinition<Conversation, OfferAccepted> ON_OFFER_ACCEPTED = new StateRebuildingHandlerDefinition<>(
            Conversation.class, OfferAccepted.class, (StateRebuildingHandler.FromObject<Conversation, OfferAccepted>) Conversation::apply);

    public static final StateRebuildingHandlerDefinition<Conversation, OfferDeclined> ON_OFFER_DECLINED = new StateRebuildingHandlerDefinition<>(
            Conversation.class, OfferDeclined.class, (StateRebuildingHandler.FromObject<Conversation, OfferDeclined>) Conversation::apply);

    public static final StateRebuildingHandlerDefinition<Conversation, ConversationClosed> ON_CLOSED = new StateRebuildingHandlerDefinition<>(
            Conversation.class, ConversationClosed.class, (StateRebuildingHandler.FromObject<Conversation, ConversationClosed>) Conversation::apply);

    public static final StateRebuildingHandlerDefinition<Conversation, ConversationBlocked> ON_BLOCKED = new StateRebuildingHandlerDefinition<>(
            Conversation.class, ConversationBlocked.class, (StateRebuildingHandler.FromObject<Conversation, ConversationBlocked>) Conversation::apply);

    // ------------------------------------------------------------------ command handling

    /** Idempotent: when the conversation exists (the id is derived from announce and buyer) it is returned as is. */
    public static CommandHandlerDefinition<Conversation, OpenListingConversation, Conversation> openListing(Clock clock) {
        return new CommandHandlerDefinition<>(Conversation.class, OpenListingConversation.class,
                (CommandHandler.ForInstanceAndCommand<Conversation, OpenListingConversation, Conversation>) (state, command, publisher) -> {
                    if (state != null) {
                        return state;
                    }
                    ConversationRules.checkOpenListing(command);
                    Participant buyer = Participant.buyer(command.buyerId());
                    ConversationOpened event = new ConversationOpened(ConversationType.LISTING, command.announceId(), command.buyerId(),
                            command.sellerId(), null, ConversationStatus.OPEN, buyer, List.of(buyer, Participant.seller(command.sellerId())), now(clock));
                    publisher.publish(event);
                    return Conversation.apply(command.id(), event);
                });
    }

    public static CommandHandlerDefinition<Conversation, OpenSupportConversation, Conversation> openSupport(Clock clock) {
        return new CommandHandlerDefinition<>(Conversation.class, OpenSupportConversation.class,
                (CommandHandler.ForCommand<Conversation, OpenSupportConversation, Conversation>) (command, publisher) -> {
                    if (command.memberId() == null) {
                        throw new IllegalArgumentException("The member is required");
                    }
                    Participant member = Participant.member(command.memberId());
                    ConversationOpened event = new ConversationOpened(ConversationType.SUPPORT, null, command.memberId(), null,
                            command.subject(), ConversationStatus.WAITING, member, List.of(member), now(clock));
                    publisher.publish(event);
                    return Conversation.apply(command.id(), event);
                });
    }

    /** Idempotent: a staff member already in the conversation joins nothing. */
    public static CommandHandlerDefinition<Conversation, JoinConversation, Conversation> join(Clock clock) {
        return new CommandHandlerDefinition<>(Conversation.class, JoinConversation.class,
                (CommandHandler.ForInstanceAndCommand<Conversation, JoinConversation, Conversation>) (state, command, publisher) -> {
                    ConversationRules.checkStaff(command.staffId());
                    ConversationRules.checkWritable(state);
                    if (state.participant(ParticipantType.ADMIN, command.staffId()).isPresent()) {
                        return state;
                    }
                    ConversationStatus after = state.status() == ConversationStatus.WAITING ? ConversationStatus.OPEN : state.status();
                    ParticipantJoined event = new ParticipantJoined(Participant.csr(command.staffId()), after, now(clock));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    /** Idempotent on the client message id: a repeat answers with the original message and writes no event. */
    public static CommandHandlerDefinition<Conversation, PostMessage, PostResult> post(Clock clock, int maxTextLength) {
        return new CommandHandlerDefinition<>(Conversation.class, PostMessage.class,
                (CommandHandler.ForInstanceAndCommand<Conversation, PostMessage, PostResult>) (state, command, publisher) -> {
                    var duplicate = state.messageByClientId(command.clientMsgId());
                    if (duplicate.isPresent()) {
                        return new PostResult(state.id(), duplicate.get().messageId(), duplicate.get().seq(), command.clientMsgId(), true, state.updatedAt());
                    }
                    ConversationRules.checkWritable(state);
                    ConversationRules.checkParticipant(state, command.sender());
                    ConversationRules.checkMessage(state, command, maxTextLength);
                    boolean offer = command.contentType() == ContentType.OFFER;
                    LocalDateTime now = now(clock);
                    MessagePosted event = new MessagePosted(ConversationIds.message(), state.lastSeq() + 1, command.sender(), command.contentType(),
                            command.content() == null ? "" : command.content(), command.attachmentUrl(),
                            offer ? command.offerAmountCents() : null, offer ? command.currency().toUpperCase() : null, command.clientMsgId(), now);
                    publisher.publish(event);
                    return new PostResult(state.id(), event.messageId(), event.seq(), command.clientMsgId(), false, now);
                });
    }

    public static CommandHandlerDefinition<Conversation, AcceptOffer, Conversation> acceptOffer(Clock clock) {
        return new CommandHandlerDefinition<>(Conversation.class, AcceptOffer.class,
                (CommandHandler.ForInstanceAndCommand<Conversation, AcceptOffer, Conversation>) (state, command, publisher) -> {
                    ConversationRules.checkSeller(state, command.sellerId());
                    ConversationRules.checkWritable(state);
                    Offer offer = ConversationRules.checkOpenOffer(state);
                    OfferAccepted event = new OfferAccepted(offer.messageId(), state.announceId(), state.buyerId(), state.sellerId(),
                            offer.amountCents(), offer.currency(), now(clock));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    public static CommandHandlerDefinition<Conversation, DeclineOffer, Conversation> declineOffer(Clock clock) {
        return new CommandHandlerDefinition<>(Conversation.class, DeclineOffer.class,
                (CommandHandler.ForInstanceAndCommand<Conversation, DeclineOffer, Conversation>) (state, command, publisher) -> {
                    ConversationRules.checkSeller(state, command.sellerId());
                    ConversationRules.checkWritable(state);
                    Offer offer = ConversationRules.checkOpenOffer(state);
                    OfferDeclined event = new OfferDeclined(offer.messageId(), now(clock));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    /** Idempotent: closing a closed conversation changes nothing; a blocked one cannot be closed. */
    public static CommandHandlerDefinition<Conversation, CloseConversation, Conversation> close(Clock clock) {
        return new CommandHandlerDefinition<>(Conversation.class, CloseConversation.class,
                (CommandHandler.ForInstanceAndCommand<Conversation, CloseConversation, Conversation>) (state, command, publisher) -> {
                    if (command.by() == null) {
                        throw new IllegalArgumentException("Who closes is required");
                    }
                    ConversationRules.checkMayClose(state, command.by());
                    if (state.status() == ConversationStatus.CLOSED) {
                        return state;
                    }
                    ConversationRules.checkWritable(state);
                    ConversationClosed event = new ConversationClosed(command.by(), now(clock));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    /** Idempotent: blocking a blocked conversation changes nothing. */
    public static CommandHandlerDefinition<Conversation, BlockConversation, Conversation> block(Clock clock) {
        return new CommandHandlerDefinition<>(Conversation.class, BlockConversation.class,
                (CommandHandler.ForInstanceAndCommand<Conversation, BlockConversation, Conversation>) (state, command, publisher) -> {
                    ConversationRules.checkStaff(command.staffId());
                    if (state.status() == ConversationStatus.BLOCKED) {
                        return state;
                    }
                    ConversationBlocked event = new ConversationBlocked(command.staffId(), command.reason(), now(clock));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    /** Every definition, for {@code CommandFixture.handlers(...)}. */
    public static List<Object> definitions(Clock clock, int maxTextLength) {
        return List.of(ON_OPENED, ON_JOINED, ON_POSTED, ON_OFFER_ACCEPTED, ON_OFFER_DECLINED, ON_CLOSED, ON_BLOCKED,
                openListing(clock), openSupport(clock), join(clock), post(clock, maxTextLength), acceptOffer(clock), declineOffer(clock),
                close(clock), block(clock));
    }

    private static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    // ------------------------------------------------------------------ beans

    @Bean
    StateRebuildingHandlerDefinition<Conversation, ConversationOpened> conversationOnOpened() {
        return ON_OPENED;
    }

    @Bean
    StateRebuildingHandlerDefinition<Conversation, ParticipantJoined> conversationOnJoined() {
        return ON_JOINED;
    }

    @Bean
    StateRebuildingHandlerDefinition<Conversation, MessagePosted> conversationOnPosted() {
        return ON_POSTED;
    }

    @Bean
    StateRebuildingHandlerDefinition<Conversation, OfferAccepted> conversationOnOfferAccepted() {
        return ON_OFFER_ACCEPTED;
    }

    @Bean
    StateRebuildingHandlerDefinition<Conversation, OfferDeclined> conversationOnOfferDeclined() {
        return ON_OFFER_DECLINED;
    }

    @Bean
    StateRebuildingHandlerDefinition<Conversation, ConversationClosed> conversationOnClosed() {
        return ON_CLOSED;
    }

    @Bean
    StateRebuildingHandlerDefinition<Conversation, ConversationBlocked> conversationOnBlocked() {
        return ON_BLOCKED;
    }

    @Bean
    CommandHandlerDefinition<Conversation, OpenListingConversation, Conversation> conversationOpenListing(Clock clock) {
        return openListing(clock);
    }

    @Bean
    CommandHandlerDefinition<Conversation, OpenSupportConversation, Conversation> conversationOpenSupport(Clock clock) {
        return openSupport(clock);
    }

    @Bean
    CommandHandlerDefinition<Conversation, JoinConversation, Conversation> conversationJoin(Clock clock) {
        return join(clock);
    }

    @Bean
    CommandHandlerDefinition<Conversation, PostMessage, PostResult> conversationPost(Clock clock,
                                                                                     @Value("${messaging.max-text-length:4000}") int maxTextLength) {
        return post(clock, maxTextLength);
    }

    @Bean
    CommandHandlerDefinition<Conversation, AcceptOffer, Conversation> conversationAcceptOffer(Clock clock) {
        return acceptOffer(clock);
    }

    @Bean
    CommandHandlerDefinition<Conversation, DeclineOffer, Conversation> conversationDeclineOffer(Clock clock) {
        return declineOffer(clock);
    }

    @Bean
    CommandHandlerDefinition<Conversation, CloseConversation, Conversation> conversationClose(Clock clock) {
        return close(clock);
    }

    @Bean
    CommandHandlerDefinition<Conversation, BlockConversation, Conversation> conversationBlock(Clock clock) {
        return block(clock);
    }
}
