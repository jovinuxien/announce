package org.linlinjava.announce.messaging.domain.conversation;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.test.CommandFixture;
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
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Conversation aggregate through the framework's given/when/then fixture: every rule of the worktree block. */
class ConversationTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    static final String ANNOUNCE = "a1";
    static final int BUYER = 7;
    static final int SELLER = 8;
    static final int STRANGER = 9;
    static final int STAFF = 3;
    static final String ID = ConversationIds.listing(ANNOUNCE, BUYER);
    static final Participant BUYER_P = Participant.buyer(BUYER);
    static final Participant SELLER_P = Participant.seller(SELLER);

    static CommandFixture<Conversation> fixture() {
        return CommandFixture.forAggregate(Conversation.class)
                .handlers(ConversationHandlers.definitions(CLOCK, 20)).subject(Subjects.of(ConversationCommands.AGGREGATE, ID));
    }

    static ConversationOpened listingOpened() {
        return new ConversationOpened(ConversationType.LISTING, ANNOUNCE, BUYER, SELLER, null, ConversationStatus.OPEN, BUYER_P,
                List.of(BUYER_P, SELLER_P), NOW.minusHours(1));
    }

    static ConversationOpened supportOpened() {
        return new ConversationOpened(ConversationType.SUPPORT, null, BUYER, null, "Help", ConversationStatus.WAITING,
                Participant.member(BUYER), List.of(Participant.member(BUYER)), NOW.minusHours(1));
    }

    static MessagePosted text(int seq, Sender sender, String clientMsgId) {
        return new MessagePosted("m" + seq, seq, sender, ContentType.TEXT, "hi " + seq, null, null, null, clientMsgId, NOW.minusMinutes(seq));
    }

    static MessagePosted offer(int seq, long cents) {
        return new MessagePosted("m" + seq, seq, Sender.user(BUYER), ContentType.OFFER, "", null, cents, "SEK", "c" + seq, NOW.minusMinutes(seq));
    }

    static PostMessage post(Sender sender, String clientMsgId, String content) {
        return new PostMessage(ID, clientMsgId, sender, ContentType.TEXT, content, null, null, null);
    }

    static PostMessage offer(Sender sender, long cents) {
        return new PostMessage(ID, "offer-1", sender, ContentType.OFFER, null, null, cents, "sek");
    }

    @Test
    void listingIdIsStablePerAnnounceAndBuyer() {
        assertEquals(ID, ConversationIds.listing(ANNOUNCE, BUYER));
        assertNotEquals(ID, ConversationIds.listing(ANNOUNCE, STRANGER));
        assertNotEquals(ID, ConversationIds.listing("a2", BUYER));
    }

    @Test
    void openingAListingConversationAddsBuyerAndSellerAndIsIdempotent() {
        CommandFixture<Conversation>.Then then = fixture().when(new OpenListingConversation(ID, ANNOUNCE, BUYER, SELLER));
        then.thenExpectEvents(new ConversationOpened(ConversationType.LISTING, ANNOUNCE, BUYER, SELLER, null, ConversationStatus.OPEN, BUYER_P,
                List.of(BUYER_P, SELLER_P), NOW));
        Conversation state = then.state();
        assertEquals(ID, state.id());
        assertEquals(ConversationStatus.OPEN, state.status());
        assertTrue(state.hasParticipant(Sender.user(SELLER)));

        fixture().given(listingOpened()).when(new OpenListingConversation(ID, ANNOUNCE, BUYER, SELLER)).thenExpectNoEvents();
    }

    @Test
    void aSellerCannotOpenAConversationWithThemself() {
        CommandRejectedException e = fixture().when(new OpenListingConversation(ID, ANNOUNCE, SELLER, SELLER))
                .thenExpectException(CommandRejectedException.class);
        assertEquals(ConversationRules.BUYER_IS_SELLER, e.getCode());
    }

    @Test
    void messagesAreNumberedAndTheSecondBuyerCannotPost() {
        CommandFixture<Conversation>.Then then = fixture().given(listingOpened(), text(1, Sender.user(BUYER), "c1"))
                .when(post(Sender.user(SELLER), "c2", "sure"));
        then.thenExpectNoError();
        MessagePosted posted = (MessagePosted) then.events().get(0);
        assertEquals(2, posted.seq());
        assertEquals("c2", posted.clientMsgId());
        assertEquals(Sender.user(SELLER), posted.sender());
        PostResult result = (PostResult) then.result().orElseThrow();
        assertEquals(posted.messageId(), result.messageId());
        assertFalse(result.duplicate());

        CommandRejectedException e = fixture().given(listingOpened()).when(post(Sender.user(STRANGER), "c3", "me too"))
                .thenExpectException(CommandRejectedException.class);
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals(ConversationRules.NOT_PARTICIPANT, e.getCode());
    }

    @Test
    void aDuplicateClientMessageIdPostsOnce() {
        CommandFixture<Conversation>.Then then = fixture().given(listingOpened(), text(1, Sender.user(BUYER), "c1"))
                .when(post(Sender.user(BUYER), "c1", "hi again"));
        then.thenExpectNoEvents();
        PostResult result = (PostResult) then.result().orElseThrow();
        assertTrue(result.duplicate());
        assertEquals("m1", result.messageId());
        assertEquals(1, result.seq());
    }

    @Test
    void textIsRequiredAndBounded() {
        fixture().given(listingOpened()).when(post(Sender.user(BUYER), "c1", " ")).thenExpectException(IllegalArgumentException.class);
        fixture().given(listingOpened())
                .when(post(Sender.user(BUYER), "c1", "x".repeat(21))).thenExpectException(IllegalArgumentException.class);
        fixture().given(listingOpened())
                .when(new PostMessage(ID, "c1", Sender.user(BUYER), ContentType.SYSTEM, "fake", null, null, null)).thenExpectException(IllegalArgumentException.class);
        fixture().given(listingOpened()).when(new PostMessage(ID, null, Sender.system(), ContentType.SYSTEM, "Order paid", null, null, null))
                .thenExpectNoError();
    }

    @Test
    void offersAreMadeByTheBuyerInListingConversationsOnly() {
        CommandFixture<Conversation>.Then then = fixture().given(listingOpened()).when(offer(Sender.user(BUYER), 90000));
        then.thenExpectNoError();
        MessagePosted posted = (MessagePosted) then.events().get(0);
        assertEquals(90000L, posted.offerAmountCents());
        assertEquals("SEK", posted.currency());
        assertEquals(new Offer(posted.messageId(), 90000, "SEK"), then.state().openOffer());

        assertEquals(ConversationRules.NOT_BUYER, fixture().given(listingOpened()).when(offer(Sender.user(SELLER), 90000))
                .thenExpectException(CommandRejectedException.class).getCode());
        assertEquals(ConversationRules.OFFER_NOT_ALLOWED, fixture().given(supportOpened(), new ParticipantJoined(Participant.csr(STAFF), ConversationStatus.OPEN, NOW))
                .when(offer(Sender.user(BUYER), 90000)).thenExpectException(CommandRejectedException.class).getCode());
        fixture().given(listingOpened()).when(offer(Sender.user(BUYER), 0)).thenExpectException(IllegalArgumentException.class);
    }

    @Test
    void aNewOfferSupersedesTheOpenOneAndTheSellerAnswersTheLatest() {
        Conversation state = fixture().given(listingOpened(), offer(1, 80000), offer(2, 85000)).when(new AcceptOffer(ID, SELLER))
                .thenExpectEvents(new OfferAccepted("m2", ANNOUNCE, BUYER, SELLER, 85000, "SEK", NOW)).state();
        assertNull(state.openOffer());
        assertEquals(new Offer("m2", 85000, "SEK"), state.acceptedOffer());

        fixture().given(listingOpened(), offer(1, 80000)).when(new DeclineOffer(ID, SELLER)).thenExpectEvents(new OfferDeclined("m1", NOW));
        assertEquals(ConversationRules.NOT_SELLER, fixture().given(listingOpened(), offer(1, 80000)).when(new AcceptOffer(ID, BUYER))
                .thenExpectException(CommandRejectedException.class).getCode());
        assertEquals(ConversationRules.NO_OPEN_OFFER, fixture().given(listingOpened()).when(new AcceptOffer(ID, SELLER))
                .thenExpectException(CommandRejectedException.class).getCode());
        assertEquals(ConversationRules.OFFER_ALREADY_ACCEPTED, fixture()
                .given(listingOpened(), offer(1, 80000), new OfferAccepted("m1", ANNOUNCE, BUYER, SELLER, 80000, "SEK", NOW))
                .when(offer(Sender.user(BUYER), 70000)).thenExpectException(CommandRejectedException.class).getCode());
    }

    @Test
    void aSupportConversationWaitsUntilStaffJoins() {
        CommandFixture<Conversation>.Then then = fixture().when(new OpenSupportConversation(ID, BUYER, "Help"));
        then.thenExpectNoError();
        assertEquals(ConversationStatus.WAITING, then.state().status());
        assertEquals(List.of(Participant.member(BUYER)), then.state().participants());

        // the member may write while waiting; a staff member joining opens it
        fixture().given(supportOpened()).when(post(Sender.user(BUYER), "c1", "anyone?")).thenExpectNoError();
        Conversation joined = fixture().given(supportOpened()).when(new JoinConversation(ID, STAFF))
                .thenExpectEvents(new ParticipantJoined(Participant.csr(STAFF), ConversationStatus.OPEN, NOW)).state();
        assertEquals(ConversationStatus.OPEN, joined.status());
        assertTrue(joined.hasParticipant(Sender.staff(STAFF)));
        fixture().given(supportOpened(), new ParticipantJoined(Participant.csr(STAFF), ConversationStatus.OPEN, NOW))
                .when(new JoinConversation(ID, STAFF)).thenExpectNoEvents();
        fixture().given(supportOpened(), new ParticipantJoined(Participant.csr(STAFF), ConversationStatus.OPEN, NOW))
                .when(post(Sender.staff(STAFF), "s1", "How can I help?")).thenExpectNoError();
    }

    @Test
    void closedAndBlockedConversationsTakeNoMessages() {
        fixture().given(listingOpened()).when(new CloseConversation(ID, Sender.user(BUYER)))
                .thenExpectEvents(new ConversationClosed(Sender.user(BUYER), NOW));
        fixture().given(listingOpened(), new ConversationClosed(Sender.user(BUYER), NOW)).when(new CloseConversation(ID, Sender.user(SELLER)))
                .thenExpectNoEvents();
        assertEquals(ConversationRules.NOT_PARTICIPANT, fixture().given(listingOpened()).when(new CloseConversation(ID, Sender.user(STRANGER)))
                .thenExpectException(CommandRejectedException.class).getCode());
        assertEquals(ConversationRules.CONVERSATION_CLOSED, fixture().given(listingOpened(), new ConversationClosed(Sender.user(BUYER), NOW))
                .when(post(Sender.user(BUYER), "c9", "late")).thenExpectException(CommandRejectedException.class).getCode());

        fixture().given(listingOpened()).when(new BlockConversation(ID, STAFF, "spam"))
                .thenExpectEvents(new ConversationBlocked(STAFF, "spam", NOW));
        assertEquals(ConversationRules.CONVERSATION_BLOCKED, fixture().given(listingOpened(), new ConversationBlocked(STAFF, "spam", NOW))
                .when(post(Sender.user(SELLER), "c9", "late")).thenExpectException(CommandRejectedException.class).getCode());
        assertEquals(ConversationRules.NOT_STAFF, fixture().given(listingOpened()).when(new BlockConversation(ID, null, "spam"))
                .thenExpectException(CommandRejectedException.class).getCode());
        // staff may close any conversation
        fixture().given(listingOpened()).when(new CloseConversation(ID, Sender.staff(STAFF))).thenExpectNoError();
    }
}
