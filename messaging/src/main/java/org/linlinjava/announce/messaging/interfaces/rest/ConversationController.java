package org.linlinjava.announce.messaging.interfaces.rest;

import org.linlinjava.announce.messaging.application.ConversationQueries;
import org.linlinjava.announce.messaging.application.ConversationQueries.ConversationView;
import org.linlinjava.announce.messaging.application.ConversationQueries.MessageView;
import org.linlinjava.announce.messaging.application.ConversationQueries.Viewer;
import org.linlinjava.announce.messaging.application.Conversations;
import org.linlinjava.announce.messaging.domain.conversation.ContentType;
import org.linlinjava.announce.messaging.domain.conversation.ConversationType;
import org.linlinjava.announce.messaging.domain.conversation.PostResult;
import org.linlinjava.announce.messaging.interfaces.Principals;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.ConversationResponse;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.OpenConversationRequest;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.PageResponse;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.PostMessageRequest;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.ReadRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Conversations of a member (or staff): open, read the inbox and the history, and the REST fallback for what the
 * WebSocket does live (post, offers, read marker, close). Writes are commands and answer with the state the
 * command produced; reads come from the read model, which follows a moment later.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final Conversations conversations;
    private final ConversationQueries queries;

    public ConversationController(Conversations conversations, ConversationQueries queries) {
        this.conversations = conversations;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ConversationResponse> open(@RequestHeader HttpHeaders headers,
                                           @RequestHeader(value = Principals.COMMAND_ID_HEADER, required = false) String commandId,
                                           @RequestBody OpenConversationRequest body) {
        Integer userId = Principals.user(headers).id();
        ConversationType type = body.type() == null ? ConversationType.LISTING : ConversationType.valueOf(body.type().toUpperCase());
        Mono<org.linlinjava.announce.messaging.domain.conversation.Conversation> opened = switch (type) {
            case LISTING -> conversations.openListing(userId, body.announceId(), commandId);
            case SUPPORT -> conversations.openSupport(userId, body.subject(), commandId);
        };
        return opened.map(ConversationResponse::of);
    }

    @GetMapping
    public Mono<PageResponse<ConversationView>> inbox(@RequestHeader HttpHeaders headers,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Viewer viewer = Principals.viewer(headers);
        return queries.inbox(viewer, page, size).collectList().map(items -> new PageResponse<>(items, page, size));
    }

    @GetMapping("/{id}")
    public Mono<ConversationView> view(@PathVariable String id, @RequestHeader HttpHeaders headers) {
        return queries.view(id, Principals.viewer(headers));
    }

    /** Messages with {@code seq > after}, oldest first: the initial load and the catch-up after a reconnect. */
    @GetMapping("/{id}/messages")
    public Mono<List<MessageView>> messages(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                            @RequestParam(defaultValue = "0") int after,
                                            @RequestParam(defaultValue = "50") int limit) {
        return queries.messages(id, Principals.viewer(headers), after, limit).collectList();
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PostResult> post(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                 @RequestHeader(value = Principals.COMMAND_ID_HEADER, required = false) String commandId,
                                 @RequestBody PostMessageRequest body) {
        Viewer viewer = Principals.viewer(headers);
        ContentType type = body.contentType() == null ? ContentType.TEXT : ContentType.valueOf(body.contentType().toUpperCase());
        return conversations.post(id, Principals.sender(viewer), type, body.content(), body.attachmentUrl(), body.amountCents(), body.currency(),
                body.clientMsgId(), commandId);
    }

    @PostMapping("/{id}/offers/accept")
    public Mono<ConversationResponse> acceptOffer(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                                  @RequestHeader(value = Principals.COMMAND_ID_HEADER, required = false) String commandId) {
        Integer userId = Principals.user(headers).id();
        return conversations.acceptOffer(id, userId, commandId).map(ConversationResponse::of);
    }

    @PostMapping("/{id}/offers/decline")
    public Mono<ConversationResponse> declineOffer(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                                   @RequestHeader(value = Principals.COMMAND_ID_HEADER, required = false) String commandId) {
        Integer userId = Principals.user(headers).id();
        return conversations.declineOffer(id, userId, commandId).map(ConversationResponse::of);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> markRead(@PathVariable String id, @RequestHeader HttpHeaders headers, @RequestBody ReadRequest body) {
        Viewer viewer = Principals.viewer(headers);
        return conversations.markRead(id, viewer.type(), viewer.id(), body.seq());
    }

    @PostMapping("/{id}/close")
    public Mono<ConversationResponse> close(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                            @RequestHeader(value = Principals.COMMAND_ID_HEADER, required = false) String commandId) {
        return conversations.close(id, Principals.sender(Principals.viewer(headers)), commandId).map(ConversationResponse::of);
    }
}
