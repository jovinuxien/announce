package org.linlinjava.announce.goods.interfaces.rest.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.goods.application.announce.AnnounceBrowsing;
import org.linlinjava.announce.goods.application.announce.AnnounceNotFoundException;
import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.AnnounceSummary;
import org.linlinjava.announce.goods.domain.announce.Condition;
import org.linlinjava.announce.goods.domain.announce.GoodsDetails;
import org.linlinjava.announce.goods.domain.announce.Kind;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.linlinjava.announce.goods.infrastructure.configuration.security.CatalogSecurityConfig;
import org.linlinjava.announce.cqrs.command.CqrsProblemDetailsHandler;
import org.linlinjava.announce.goods.interfaces.rest.Callers;
import org.linlinjava.announce.goods.interfaces.rest.CatalogExceptionHandler;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.linlinjava.announce.goods.application.search.AnnounceReindexService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The staff controller: no owner header, no forced status, moderation actions carry the moderator and a reason. */
@WebFluxTest(controllers = AnnounceAdminController.class)
@Import({CatalogExceptionHandler.class, CatalogSecurityConfig.class, CqrsProblemDetailsHandler.class})
class AnnounceAdminControllerTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 12, 0);

    @Autowired WebTestClient anonymous;
    WebTestClient client;   // what the admin edge forwards for staff member 42

    @BeforeEach
    void asStaff() {
        client = anonymous.mutate().defaultHeader(Callers.USER_HEADER, "42").defaultHeader(Callers.TYPE_HEADER, Callers.ADMIN).build();
    }
    @MockBean AnnouncePublishing publishing;
    @MockBean AnnounceBrowsing browsing;
    @MockBean AnnounceReindexService reindex;

    static Announce announce(AnnounceStatus status) {
        AnnounceContent content = new AnnounceContent("Bike", "brief", "<p>d</p>", List.of(), List.of("http://x/1.jpg"),
                new CategoryId(3), Location.inRegion(1, "Stockholm", "11122"));
        return new Announce("a1", new SellerId(7), content, GoodsDetails.fixedPrice(Money.of("1500"), Condition.GOOD), status, NOW, NOW.plusDays(60), NOW, NOW);
    }

    static AnnounceSummary summary(AnnounceSummary.Moderation moderation) {
        return new AnnounceSummary("a1", Kind.GOODS, new SellerId(7), "Bike", "brief", "http://x/1.jpg", AnnounceStatus.WITHDRAWN,
                new CategoryId(3), Location.inRegion(1, "Stockholm", "11122"), NOW, NOW, NOW.plusDays(60),
                new AnnounceSummary.KindFacts(Money.of("1500"), SaleType.FIXED_PRICE, Condition.GOOD, null, null, null, null, null, null, null),
                moderation);
    }

    @Test
    void listingKeepsTheRequestedStatusAndSellerAndExposesTheModeration() {
        when(browsing.count(any())).thenReturn(Mono.just(1L));
        when(browsing.browse(any())).thenReturn(Flux.just(summary(new AnnounceSummary.Moderation(42, "spam"))));

        client.get().uri("/api/admin/announces?kind=GOODS&status=WITHDRAWN&seller=7&region=1&q=bike&page=2&size=5").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.total").isEqualTo(1).jsonPath("$.page").isEqualTo(2)
                .jsonPath("$.items[0].status").isEqualTo("WITHDRAWN")
                .jsonPath("$.items[0].moderatedBy").isEqualTo(42).jsonPath("$.items[0].moderationReason").isEqualTo("spam");

        ArgumentCaptor<AnnounceQuery> q = ArgumentCaptor.forClass(AnnounceQuery.class);
        verify(browsing).count(q.capture());
        assertEquals(AnnounceStatus.WITHDRAWN, q.getValue().status());
        assertEquals(7, q.getValue().sellerId());
        assertEquals(1, q.getValue().regionId());
        assertEquals("bike", q.getValue().keywords());
    }

    @Test
    void listingWithoutStatusListsEveryStatus() {
        when(browsing.count(any())).thenReturn(Mono.just(0L));
        when(browsing.browse(any())).thenReturn(Flux.empty());
        client.get().uri("/api/admin/announces").exchange().expectStatus().isOk();
        ArgumentCaptor<AnnounceQuery> q = ArgumentCaptor.forClass(AnnounceQuery.class);
        verify(browsing).count(q.capture());
        assertNull(q.getValue().status());
        assertNull(q.getValue().sellerId());
    }

    @Test
    void inspectShowsAnAnnounceInAnyStatus() {
        when(browsing.inspect("a1")).thenReturn(Mono.just(announce(AnnounceStatus.DRAFT)));
        client.get().uri("/api/admin/announces/a1").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("DRAFT").jsonPath("$.sellerId").isEqualTo(7);
        when(browsing.inspect("nope")).thenReturn(Mono.error(new AnnounceNotFoundException("nope")));
        client.get().uri("/api/admin/announces/nope").exchange().expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void withdrawAndExpireDispatchTheModerationCommandsWithTheModeratorAndReason() {
        when(publishing.moderateWithdraw(eq("a1"), eq(42), eq("spam"), eq("c-1"))).thenReturn(Mono.just(announce(AnnounceStatus.WITHDRAWN)));
        when(publishing.moderateExpire(eq("a1"), eq(42), eq("stale"), isNull())).thenReturn(Mono.just(announce(AnnounceStatus.EXPIRED)));

        client.post().uri("/api/admin/announces/a1/withdraw")
                .header(AnnounceAdminController.COMMAND_ID_HEADER, "c-1")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"reason\":\"spam\"}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("WITHDRAWN");
        client.post().uri("/api/admin/announces/a1/expire")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"reason\":\"stale\"}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("EXPIRED");
    }

    @Test
    void everyRouteNeedsAStaffIdentity() {
        anonymous.post().uri("/api/admin/announces/a1/withdraw").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"spam\"}").exchange().expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo(Callers.NO_PRINCIPAL);
        anonymous.get().uri("/api/admin/announces").exchange().expectStatus().isUnauthorized();
        anonymous.get().uri("/api/admin/announces/a1").exchange().expectStatus().isUnauthorized();
        anonymous.post().uri("/api/admin/announces/reindex").exchange().expectStatus().isUnauthorized();

        // a member (what the customer edge forwards) is refused on every admin route
        WebTestClient member = anonymous.mutate().defaultHeader(Callers.USER_HEADER, "42").defaultHeader(Callers.TYPE_HEADER, Callers.MEMBER).build();
        member.post().uri("/api/admin/announces/a1/withdraw").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"spam\"}").exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(Callers.NOT_STAFF);
        member.get().uri("/api/admin/announces").exchange().expectStatus().isForbidden();
        member.get().uri("/api/admin/announces/a1").exchange().expectStatus().isForbidden();
        member.post().uri("/api/admin/announces/reindex").exchange().expectStatus().isForbidden();
        org.mockito.Mockito.verifyNoInteractions(publishing, browsing, reindex);
    }
}
