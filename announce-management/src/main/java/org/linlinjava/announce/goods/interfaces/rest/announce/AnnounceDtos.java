package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.linlinjava.announce.goods.api.AnnounceApiDtos.EventDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.GoodsDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.JobDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.LocationDto;
import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.Kind;

import java.util.List;

/**
 * Request bodies of the announce write API, which is not on the {@code announce-management-api} descriptor yet. A
 * request carries the {@code kind} discriminator and exactly one of {@code goods}, {@code job}, {@code event}, using
 * the same detail records the responses use; {@link AnnounceApiMapper} translates it to domain values (which
 * validate themselves). The response records live in {@code org.linlinjava.announce.goods.api.AnnounceApiDtos}.
 */
public final class AnnounceDtos {
    private AnnounceDtos() {}

    public record AnnounceRequest(Kind kind, String title, String brief, String description, List<String> keywords, List<String> gallery,
                                  Integer categoryId, LocationDto location, GoodsDto goods, JobDto job, EventDto event) {
        public AnnounceContent toContent() {
            return AnnounceApiMapper.toContent(this);
        }

        /** The details object must match {@code kind}; a missing kind defaults to GOODS for compatibility. */
        public AnnounceKind toDetails() {
            return AnnounceApiMapper.toDetails(this);
        }
    }

    public record PublishRequest(Integer validityDays) {}
}
