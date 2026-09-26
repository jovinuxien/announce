package org.linlinjava.announce.goods.infrastructure.projection;

import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.goods.application.search.AnnounceDocument;
import org.linlinjava.announce.goods.application.search.AnnounceIndexer;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceReadRepository;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceEdited;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceSold;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceWithdrawn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Second projection of {@code /announce/*}: keeps the search index (OCS, index {@code announce_index}) in step with
 * the announces that are visible. Group {@code announce-search}, own checkpoint.
 *
 * <p>The document is built from the read model, so a published or edited announce is indexed with exactly what the
 * {@code announce} tables hold. The read-model group may lag behind this one; a row that has not yet applied this
 * event (its {@code last_event_id} is older) makes the handler fail, and the framework retries it with back-off until
 * the read model has caught up. Withdrawn, sold and expired announces are removed from the index.
 */
@Component
public class AnnounceSearchProjection implements ReactiveProjection {

    public static final String GROUP = "announce-search";

    private static final Logger log = LoggerFactory.getLogger(AnnounceSearchProjection.class);

    private final AnnounceReadRepository readModel;
    private final AnnounceIndexer indexer;

    public AnnounceSearchProjection(AnnounceReadRepository readModel, AnnounceIndexer indexer) {
        this.readModel = readModel;
        this.indexer = indexer;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        handlers.on(AnnouncePublished.class, (event, envelope) -> indexFromReadModel(envelope));
        handlers.on(AnnounceEdited.class, (event, envelope) -> indexFromReadModel(envelope));
        handlers.on(AnnounceWithdrawn.class, (event, envelope) -> indexer.remove(idOf(envelope)));
        handlers.on(AnnounceSold.class, (event, envelope) -> indexer.remove(idOf(envelope)));
        handlers.on(AnnounceExpired.class, (event, envelope) -> indexer.remove(idOf(envelope)));
    }

    private Mono<Void> indexFromReadModel(EventEnvelope envelope) {
        String id = idOf(envelope);
        // one row read: the applied event id and the state come from the same snapshot
        return readModel.findApplied(id)
                .switchIfEmpty(Mono.error(() -> new ReadModelBehindException(id, envelope.eventId())))
                .flatMap(applied -> {
                    if (applied.lastEventId() == null || isBefore(applied.lastEventId(), envelope.eventId())) {
                        return Mono.error(new ReadModelBehindException(id, envelope.eventId()));
                    }
                    Announce a = applied.announce();
                    log.debug("announce {} after event {} (read model at {}): status {}, {}", id, envelope.eventId(), applied.lastEventId(),
                            a.status(), a.isVisible() ? "indexing" : "removing from the index");
                    return a.isVisible() ? indexer.index(AnnounceDocument.of(a)) : indexer.remove(a.id());
                });
    }

    /** EventSourcingDB ids are increasing integers; compare numerically, else lexically. */
    static boolean isBefore(String applied, String wanted) {
        try {
            return Long.parseLong(applied) < Long.parseLong(wanted);
        } catch (NumberFormatException e) {
            return applied.compareTo(wanted) < 0;
        }
    }

    private static String idOf(EventEnvelope envelope) {
        return Subjects.idOf(envelope.subject());
    }

    /** Transient: the read-model projection has not applied this event yet; the processor retries. */
    public static class ReadModelBehindException extends RuntimeException {
        public ReadModelBehindException(String id, String eventId) {
            super("read model of announce " + id + " has not applied event " + eventId + " yet");
        }
    }
}
