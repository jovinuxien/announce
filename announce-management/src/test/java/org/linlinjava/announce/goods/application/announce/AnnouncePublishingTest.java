package org.linlinjava.announce.goods.application.announce;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.DraftAnnounce;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.PublishAnnounce;
import org.linlinjava.announce.goods.application.category.CategoryNotFoundException;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceDetails;
import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.AnnounceRepository;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.brand.BrandRepository;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.linlinjava.announce.goods.domain.category.CategoryRepository;
import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEvent;
import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnouncePublishingTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    static final SellerId OWNER = new SellerId(7);

    AnnounceRepository announces = mock(AnnounceRepository.class);
    CategoryRepository categories = mock(CategoryRepository.class);
    BrandRepository brands = mock(BrandRepository.class);
    List<LitemallDomainEvent> published = new ArrayList<>();
    AnnouncePublishing service;

    @BeforeEach
    void setUp() {
        LitemallDomainEventPublisher publisher = published::add;
        service = new AnnouncePublishing(announces, categories, brands, publisher, CLOCK);
        when(announces.save(any())).thenAnswer(inv -> {
            Announce a = inv.getArgument(0);
            if (a.id() == null) a.assignId(new AnnounceId(500));
            return Mono.just(a);
        });
    }

    static AnnounceDetails details() {
        return new AnnounceDetails("Sofa", "", "", null, null, null, new CategoryId(3), null, SaleType.FIXED_PRICE, Money.of("40"),
                Location.inRegion(1, "Uppsala", null), null, null);
    }

    @Test
    void draftRefusesUnknownCategory() {
        when(categories.existsVisible(new CategoryId(3))).thenReturn(Mono.just(false));
        StepVerifier.create(service.draft(new DraftAnnounce(OWNER, details())))
                .expectError(CategoryNotFoundException.class)
                .verify();
    }

    @Test
    void draftSavesAndAssignsId() {
        when(categories.existsVisible(new CategoryId(3))).thenReturn(Mono.just(true));
        StepVerifier.create(service.draft(new DraftAnnounce(OWNER, details())))
                .assertNext(a -> {
                    assertEquals(new AnnounceId(500), a.id());
                    assertEquals(AnnounceStatus.DRAFT, a.status());
                })
                .verifyComplete();
        assertEquals(0, published.size(), "drafting publishes nothing");
    }

    @Test
    void onlyTheOwnerCanPublish() {
        Announce a = Announce.draft(OWNER, details(), NOW);
        a.assignId(new AnnounceId(500));
        when(announces.findById(new AnnounceId(500))).thenReturn(Mono.just(a));
        when(announces.findById(new AnnounceId(999))).thenReturn(Mono.empty());
        StepVerifier.create(service.publish(new PublishAnnounce(new AnnounceId(500), new SellerId(8), null)))
                .expectError(NotAnnounceOwnerException.class)
                .verify();
        StepVerifier.create(service.publish(new PublishAnnounce(new AnnounceId(999), OWNER, null)))
                .expectError(AnnounceNotFoundException.class)
                .verify();
    }

    @Test
    void publishUsesDefaultValidityAndEmitsEvent() {
        Announce a = Announce.draft(OWNER, details(), NOW);
        a.assignId(new AnnounceId(500));
        when(announces.findById(new AnnounceId(500))).thenReturn(Mono.just(a));
        StepVerifier.create(service.publish(new PublishAnnounce(new AnnounceId(500), OWNER, null)))
                .assertNext(saved -> assertEquals(NOW.plus(Announce.DEFAULT_VALIDITY), saved.expiresAt()))
                .verifyComplete();
        assertEquals(1, published.size());
        assertInstanceOf(AnnouncePublished.class, published.get(0));
    }

    @Test
    void expireDueExpiresOnlyWhatIsDue() {
        Announce due = Announce.draft(OWNER, details(), NOW.minusDays(90));
        due.assignId(new AnnounceId(1));
        due.publish(Duration.ofDays(30), NOW.minusDays(90));
        Announce fresh = Announce.draft(OWNER, details(), NOW);
        fresh.assignId(new AnnounceId(2));
        fresh.publish(Duration.ofDays(30), NOW);
        due.pullEvents(); fresh.pullEvents();
        when(announces.findPublishedExpiredBefore(any(), anyInt())).thenReturn(Flux.just(due, fresh));
        StepVerifier.create(service.expireDue(100)).expectNext(1L).verifyComplete();
        assertEquals(AnnounceStatus.EXPIRED, due.status());
        assertEquals(AnnounceStatus.PUBLISHED, fresh.status());
        assertEquals(1, published.size());
        assertInstanceOf(AnnounceExpired.class, published.get(0));
    }
}
