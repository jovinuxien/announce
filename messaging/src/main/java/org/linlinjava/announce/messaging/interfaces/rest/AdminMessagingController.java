package org.linlinjava.announce.messaging.interfaces.rest;

import org.linlinjava.announce.messaging.application.Bulletins;
import org.linlinjava.announce.messaging.application.Bulletins.BulletinView;
import org.linlinjava.announce.messaging.application.ConversationQueries;
import org.linlinjava.announce.messaging.application.ConversationQueries.ConversationView;
import org.linlinjava.announce.messaging.application.Conversations;
import org.linlinjava.announce.messaging.domain.conversation.ConversationStatus;
import org.linlinjava.announce.messaging.domain.conversation.Sender;
import org.linlinjava.announce.messaging.interfaces.Principals;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.BlockRequest;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.BulletinRequest;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.ConversationResponse;
import org.linlinjava.announce.messaging.interfaces.rest.MessagingDtos.PageResponse;
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
 * Staff side (book: the CSR dashboard): the support queue, joining and moderating conversations, bulletins.
 * Every route needs staff: {@code X-User-Id} with {@code X-User-Type=ADMIN} (401 without an identity, 403 for a member).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMessagingController {

    private final Conversations conversations;
    private final ConversationQueries queries;
    private final Bulletins bulletins;

    public AdminMessagingController(Conversations conversations, ConversationQueries queries, Bulletins bulletins) {
        this.conversations = conversations;
        this.queries = queries;
        this.bulletins = bulletins;
    }

    /** The queue: {@code status=WAITING} lists support conversations nobody has picked up yet, oldest first. */
    @GetMapping("/conversations")
    public Mono<PageResponse<ConversationView>> byStatus(@RequestHeader HttpHeaders headers,
                                                         @RequestParam(defaultValue = "WAITING") ConversationStatus status,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        Integer staffId = Principals.staff(headers);
        return queries.byStatus(status, page, size).collectList().map(items -> new PageResponse<>(items, page, size));
    }

    @PostMapping("/conversations/{id}/join")
    public Mono<ConversationResponse> join(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                           @RequestHeader(value = Principals.COMMAND_ID_HEADER, required = false) String commandId) {
        Integer staffId = Principals.staff(headers);
        return conversations.join(id, staffId, commandId).map(ConversationResponse::of);
    }

    @PostMapping("/conversations/{id}/block")
    public Mono<ConversationResponse> block(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                            @RequestHeader(value = Principals.COMMAND_ID_HEADER, required = false) String commandId,
                                            @RequestBody(required = false) BlockRequest body) {
        Integer staffId = Principals.staff(headers);
        return conversations.block(id, staffId, body == null ? null : body.reason(), commandId).map(ConversationResponse::of);
    }

    @PostMapping("/conversations/{id}/close")
    public Mono<ConversationResponse> close(@PathVariable String id, @RequestHeader HttpHeaders headers,
                                            @RequestHeader(value = Principals.COMMAND_ID_HEADER, required = false) String commandId) {
        Integer staffId = Principals.staff(headers);
        return conversations.close(id, Sender.staff(staffId), commandId).map(ConversationResponse::of);
    }

    @PostMapping("/bulletins")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<BulletinView> postBulletin(@RequestHeader HttpHeaders headers, @RequestBody BulletinRequest body) {
        Integer staffId = Principals.staff(headers);
        return bulletins.post(staffId, body.audience(), body.clientType(), body.content(), body.expiresAt());
    }

    @GetMapping("/bulletins")
    public Mono<List<BulletinView>> bulletins(@RequestHeader HttpHeaders headers,
                                              @RequestParam(defaultValue = "STAFF") String audience) {
        Integer staffId = Principals.staff(headers);
        return bulletins.current(audience).collectList();
    }
}
