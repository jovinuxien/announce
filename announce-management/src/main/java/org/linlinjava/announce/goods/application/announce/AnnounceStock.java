package org.linlinjava.announce.goods.application.announce;

import org.linlinjava.announce.goods.application.announce.AnnounceCommands.AddVariant;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.AdjustStock;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Use cases on the variants of an announce: add one, move its stock. */
@Service
public class AnnounceStock {

    private final AnnouncePublishing publishing;

    public AnnounceStock(AnnouncePublishing publishing) {
        this.publishing = publishing;
    }

    public Mono<Announce> addVariant(AddVariant command) {
        return publishing.change(command.announceId(), command.sellerId(),
                a -> a.addVariant(command.specifications(), command.price(), command.stock(), command.pictureUrl(), publishing.now()));
    }

    public Mono<Announce> adjustStock(AdjustStock command) {
        return publishing.change(command.announceId(), command.sellerId(),
                a -> a.adjustStock(command.variantId(), command.delta(), publishing.now()));
    }
}
