package org.linlinjava.announce.messaging.application;

import com.opencqrs.framework.client.ConcurrencyException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationParticipantMapper;
import org.linlinjava.announce.messaging.domain.conversation.ContentType;
import org.linlinjava.announce.messaging.domain.conversation.Conversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.AcceptOffer;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.BlockConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.CloseConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.DeclineOffer;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.JoinConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.OpenListingConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.OpenSupportConversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationCommands.PostMessage;
import org.linlinjava.announce.messaging.domain.conversation.ConversationIds;
import org.linlinjava.announce.messaging.domain.conversation.ParticipantType;
import org.linlinjava.announce.messaging.domain.conversation.PostResult;
import org.linlinjava.announce.messaging.domain.conversation.Sender;
import org.linlinjava.announce.messaging.infrastructure.metrics.MessagingMeters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Use cases on a conversation. The rules live in the aggregate; this facade resolves what the aggregate cannot see
 * (the seller of an announce) and dispatches through the framework router. A caller-supplied command id makes a
 * dispatch idempotent; the client message id of a post is idempotent on its own.
 *
 * <p>Reading positions are not domain facts: {@link #markRead} writes the read model directly.
 *
 * <p>Two commands racing on one conversation: the event store refuses the later write with a
 * {@link ConcurrencyException} and writes nothing, so the refused command is dispatched again on the new state,
 * at most {@code messaging.command-retry.max-retries} times with a short jittered back-off. Each re-dispatch counts
 * in {@code messaging.command.retries}. A command that still loses after the last attempt fails with the
 * {@code ConcurrencyException} (409 {@code concurrent-modification} for the client, who may send it again).
 *
 * <p>Every dispatch is timed as {@code messaging.command.latency} (tags: command type, outcome), retries included.
 */
@Service
public class Conversations {

    public static final String ANNOUNCE_NOT_FOUND = "announce-not-found";

    private final ReactiveCommandRouter router;
    private final AnnounceLookup announces;
    private final AnnounceConversationParticipantMapper participants;
    private final Clock clock;
    private final MeterRegistry meters;
    private final MessagingProperties.CommandRetry retry;

    public Conversations(ReactiveCommandRouter router, AnnounceLookup announces, AnnounceConversationParticipantMapper participants, Clock clock,
                         MeterRegistry meters, MessagingProperties properties) {
        this.router = router;
        this.announces = announces;
        this.participants = participants;
        this.clock = clock;
        this.meters = meters;
        this.retry = properties.getCommandRetry();
    }

    /** Opens, or finds, the conversation between this buyer and the seller of the announce. */
    public Mono<Conversation> openListing(Integer buyerId, String announceId, String commandId) {
        if (announceId == null || announceId.isBlank()) {
            return Mono.error(new IllegalArgumentException("The announce id is required"));
        }
        return announces.find(announceId)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.NOT_FOUND, ANNOUNCE_NOT_FOUND, "Announce " + announceId + " does not exist")))
                .flatMap(announce -> openListing(announceId, buyerId, announce.sellerId(), commandId));
    }

    /** Same, when the caller already knows the seller (events of the order context carry it). */
    public Mono<Conversation> openListing(String announceId, Integer buyerId, Integer sellerId, String commandId) {
        return send(new OpenListingConversation(ConversationIds.listing(announceId, buyerId), announceId, buyerId, sellerId), commandId, Conversation.class);
    }

    public Mono<Conversation> openSupport(Integer memberId, String subject, String commandId) {
        return send(new OpenSupportConversation(ConversationIds.support(), memberId, subject), commandId, Conversation.class);
    }

    public Mono<Conversation> join(String id, Integer staffId, String commandId) {
        return send(new JoinConversation(id, staffId), commandId, Conversation.class);
    }

    public Mono<PostResult> post(String id, Sender sender, ContentType contentType, String content, String attachmentUrl,
                                 Long offerAmountCents, String currency, String clientMsgId, String commandId) {
        return send(new PostMessage(id, clientMsgId, sender, contentType, content, attachmentUrl, offerAmountCents, currency), commandId, PostResult.class);
    }

    /** A message from the platform, idempotent on {@code clientMsgId} (use the id of the event that caused it). */
    public Mono<PostResult> postSystem(String id, String content, String clientMsgId) {
        return post(id, Sender.system(), ContentType.SYSTEM, content, null, null, null, clientMsgId, null);
    }

    public Mono<Conversation> acceptOffer(String id, Integer sellerId, String commandId) {
        return send(new AcceptOffer(id, sellerId), commandId, Conversation.class);
    }

    public Mono<Conversation> declineOffer(String id, Integer sellerId, String commandId) {
        return send(new DeclineOffer(id, sellerId), commandId, Conversation.class);
    }

    public Mono<Conversation> close(String id, Sender by, String commandId) {
        return send(new CloseConversation(id, by), commandId, Conversation.class);
    }

    public Mono<Conversation> block(String id, Integer staffId, String reason, String commandId) {
        return send(new BlockConversation(id, staffId, reason), commandId, Conversation.class);
    }

    /** Raises the participant's read marker to {@code seq}; a lower value changes nothing. */
    public Mono<Void> markRead(String id, ParticipantType participantType, Integer participantId, int seq) {
        return participants.raiseLastReadSeq(id, participantType.name(), participantId, seq, LocalDateTime.now(clock)).then();
    }

    private <R> Mono<R> send(com.opencqrs.framework.command.Command command, String commandId, Class<R> resultType) {
        Map<String, ?> metaData = commandId == null ? Map.of() : Map.of(MetaDataKeys.COMMAND_ID, commandId);
        String name = command.getClass().getSimpleName();
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meters);
            return retrying(router.send(command, metaData, resultType), name)
                    .doOnSuccess(r -> sample.stop(MessagingMeters.commandLatency(meters, name, MessagingMeters.OUTCOME_OK)))
                    .doOnError(e -> sample.stop(MessagingMeters.commandLatency(meters, name, e instanceof CommandRejectedException
                            ? MessagingMeters.OUTCOME_REJECTED : MessagingMeters.OUTCOME_ERROR)));
        });
    }

    /**
     * Re-subscribes to the dispatch when the event store refused the write because the conversation changed in
     * the meantime. Safe for every command: nothing was written, and the handler runs again on the new state
     * (a post gets the next {@code seq}, an idempotent command finds its work done). The router releases a claimed
     * command id on failure, so the re-dispatch claims it again.
     */
    private <R> Mono<R> retrying(Mono<R> dispatch, String command) {
        if (retry.getMaxRetries() <= 0) {
            return dispatch;
        }
        return dispatch.retryWhen(Retry.backoff(retry.getMaxRetries(), retry.getMinBackoff())
                .maxBackoff(retry.getMaxBackoff())
                .filter(ConcurrencyException.class::isInstance)
                .doBeforeRetry(signal -> MessagingMeters.commandRetries(meters, command).increment())
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }
}
