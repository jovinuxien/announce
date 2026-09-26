package org.linlinjava.announce.messaging.infrastructure.messaging;

import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelay;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelays;
import org.linlinjava.announce.messaging.api.ConversationOpenedV1;
import org.linlinjava.announce.messaging.api.OfferAcceptedV1;
import org.linlinjava.announce.messaging.application.live.ConversationStatusLive;
import org.linlinjava.announce.messaging.application.live.MessageLive;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationBlocked;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationClosed;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationOpened;
import org.linlinjava.announce.messaging.domain.conversation.events.MessagePosted;
import org.linlinjava.announce.messaging.domain.conversation.events.OfferAccepted;
import org.linlinjava.announce.messaging.domain.conversation.events.OfferDeclined;
import org.linlinjava.announce.messaging.domain.conversation.events.ParticipantJoined;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The two relays of this service, both at least once with their own checkpoint:
 * <ul>
 *   <li>{@code messaging-public} maps to the published language ({@code messaging-api}) on binding
 *       {@code messaging-out-0}, topic {@code messaging.v1}: the order context consumes {@code OfferAcceptedV1};</li>
 *   <li>{@code messaging-live} maps every conversation event a client should see to a live record on binding
 *       {@code messaging-live-out-0}, topic {@code messaging.live}, keyed by the subject (the conversation id):
 *       every node subscribes and pushes to its own WebSocket sessions.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class MessagingRelays {

    public static final String PUBLIC_GROUP = "messaging-public";
    public static final String PUBLIC_BINDING = "messaging-out-0";
    public static final String LIVE_GROUP = "messaging-live";
    public static final String LIVE_BINDING = "messaging-live-out-0";

    @Bean
    PublicEventRelay messagingPublicEvents(PublicEventRelays relays) {
        return relays.forGroup(PUBLIC_GROUP).binding(PUBLIC_BINDING)
                .map(ConversationOpened.class, ConversationOpenedV1.class, (event, envelope) ->
                        new ConversationOpenedV1(Subjects.idOf(envelope.subject()), event.type().name(), event.announceId(), event.buyerId(),
                                event.sellerId(), event.at()))
                .map(OfferAccepted.class, OfferAcceptedV1.class, (event, envelope) ->
                        new OfferAcceptedV1(Subjects.idOf(envelope.subject()), event.announceId(), event.buyerId(), event.sellerId(),
                                event.amountCents(), event.currency(), event.at()))
                .build();
    }

    @Bean
    PublicEventRelay messagingLiveEvents(PublicEventRelays relays) {
        return relays.forGroup(LIVE_GROUP).binding(LIVE_BINDING)
                .map(MessagePosted.class, MessageLive.class, (event, envelope) ->
                        new MessageLive(Subjects.idOf(envelope.subject()), event.messageId(), event.seq(), event.sender().type().name(),
                                event.sender().id(), event.contentType().name(), event.content(), event.attachmentUrl(), event.offerAmountCents(),
                                event.currency(), event.clientMsgId(), event.at()))
                .map(ParticipantJoined.class, ConversationStatusLive.class, (event, envelope) ->
                        new ConversationStatusLive(Subjects.idOf(envelope.subject()), ConversationStatusLive.JOINED, event.status().name(), null,
                                null, null, event.participant().type().name(), event.participant().id(), event.at()))
                .map(OfferAccepted.class, ConversationStatusLive.class, (event, envelope) ->
                        new ConversationStatusLive(Subjects.idOf(envelope.subject()), ConversationStatusLive.OFFER_ACCEPTED, "OPEN",
                                event.messageId(), event.amountCents(), event.currency(), "USER", event.sellerId(), event.at()))
                .map(OfferDeclined.class, ConversationStatusLive.class, (event, envelope) ->
                        new ConversationStatusLive(Subjects.idOf(envelope.subject()), ConversationStatusLive.OFFER_DECLINED, "OPEN",
                                event.messageId(), null, null, null, null, event.at()))
                .map(ConversationClosed.class, ConversationStatusLive.class, (event, envelope) ->
                        new ConversationStatusLive(Subjects.idOf(envelope.subject()), ConversationStatusLive.CLOSED, "CLOSED", null, null, null,
                                event.by().type().name(), event.by().id(), event.at()))
                .map(ConversationBlocked.class, ConversationStatusLive.class, (event, envelope) ->
                        new ConversationStatusLive(Subjects.idOf(envelope.subject()), ConversationStatusLive.BLOCKED, "BLOCKED", null, null, null,
                                "ADMIN", event.staffId(), event.at()))
                .build();
    }
}
