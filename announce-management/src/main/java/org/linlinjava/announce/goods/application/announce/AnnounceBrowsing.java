package org.linlinjava.announce.goods.application.announce;

import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceReadRepository;
import org.linlinjava.announce.goods.domain.announce.AnnounceSummary;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Read side of the catalog over the read-model tables: one announce in full, or a page of summaries. */
@Service
public class AnnounceBrowsing {

    private final AnnounceReadRepository announces;

    public AnnounceBrowsing(AnnounceReadRepository announces) {
        this.announces = announces;
    }

    /** A visitor sees only published announces; the owner sees their own in any status. */
    public Mono<Announce> view(String id, SellerId viewer) {
        return announces.findById(id)
                .filter(a -> a.isVisible() || (viewer != null && a.isOwnedBy(viewer)))
                .switchIfEmpty(Mono.error(() -> new AnnounceNotFoundException(id)));
    }

    public Flux<AnnounceSummary> browse(AnnounceQuery query) {
        return announces.browse(query);
    }

    public Mono<Long> count(AnnounceQuery query) {
        return announces.count(query);
    }
}
