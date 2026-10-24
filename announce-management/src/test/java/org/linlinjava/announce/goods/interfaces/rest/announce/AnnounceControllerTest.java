package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.goods.application.announce.AnnounceBrowsing;
import org.linlinjava.announce.goods.application.announce.AnnounceNotFoundException;
import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.Condition;
import org.linlinjava.announce.goods.domain.announce.ContractType;
import org.linlinjava.announce.goods.domain.announce.GoodsDetails;
import org.linlinjava.announce.goods.domain.announce.JobDetails;
import org.linlinjava.announce.goods.domain.announce.Kind;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.linlinjava.announce.goods.infrastructure.configuration.security.CatalogSecurityConfig;
import org.linlinjava.announce.cqrs.command.CqrsProblemDetailsHandler;
import org.linlinjava.announce.goods.interfaces.rest.CallerContextFilter;
import org.linlinjava.announce.goods.interfaces.rest.Callers;
import org.linlinjava.announce.goods.interfaces.rest.CatalogExceptionHandler;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AnnounceController.class)
// the filter is part of the contract under test: view and browse read the viewer from the Reactor context it fills
@Import({CatalogExceptionHandler.class, CatalogSecurityConfig.class, CqrsProblemDetailsHandler.class, CallerContextFilter.class})
class AnnounceControllerTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 12, 0);

    @Autowired WebTestClient client;
    @MockBean AnnouncePublishing publishing;
    @MockBean AnnounceBrowsing browsing;

    static Announce announce(String id, AnnounceKind details) {
        AnnounceContent content = new AnnounceContent("Bike", "b", "d", List.of("k"), List.of("http://x/1.jpg"), new CategoryId(3),
                Location.inRegion(1, "Stockholm", "11122"));
        return new Announce(id, new SellerId(7), content, details, AnnounceStatus.DRAFT, null, null, NOW, NOW);
    }

    static final String GOODS_BODY = "{\"kind\":\"GOODS\",\"title\":\"Bike\",\"categoryId\":3,\"goods\":{\"price\":10}}";

    @Test
    void postRequiresAMemberIdentity() {
        client.post().uri("/api/announces").contentType(MediaType.APPLICATION_JSON).bodyValue(GOODS_BODY)
                .exchange().expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo(Callers.NO_PRINCIPAL);
        // the former owner header is no longer an identity
        client.post().uri("/api/announces").header("X-Seller-Id", "7").contentType(MediaType.APPLICATION_JSON).bodyValue(GOODS_BODY)
                .exchange().expectStatus().isUnauthorized();
        // staff ids are another number space: an administrator is not the member with the same number
        client.post().uri("/api/announces").header(Callers.USER_HEADER, "7").header(Callers.TYPE_HEADER, Callers.ADMIN)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(GOODS_BODY)
                .exchange().expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo(Callers.NOT_A_MEMBER);
        client.post().uri("/api/announces").header(Callers.USER_HEADER, "7").contentType(MediaType.APPLICATION_JSON).bodyValue(GOODS_BODY)
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/announces").header(Callers.USER_HEADER, "seven").header(Callers.TYPE_HEADER, Callers.MEMBER)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(GOODS_BODY).exchange().expectStatus().isBadRequest();
        org.mockito.Mockito.verifyNoInteractions(publishing);
    }

    @Test
    void postGoodsReturnsTheCreatedAnnounceWithItsKind() {
        when(publishing.post(any(), any(), any(), any())).thenReturn(Mono.just(announce("a1", GoodsDetails.fixedPrice(Money.of("10"), Condition.NEW))));
        client.post().uri("/api/announces").header(Callers.USER_HEADER, "7").header(Callers.TYPE_HEADER, Callers.MEMBER).header(AnnounceController.COMMAND_ID_HEADER, "c-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"GOODS\",\"title\":\"Bike\",\"categoryId\":3,\"location\":{\"city\":\"Stockholm\"},"
                        + "\"goods\":{\"price\":10,\"condition\":\"NEW\",\"saleType\":\"FIXED_PRICE\"}}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo("a1").jsonPath("$.kind").isEqualTo("GOODS")
                .jsonPath("$.goods.price").isEqualTo(10).jsonPath("$.goods.condition").isEqualTo("NEW")
                .jsonPath("$.job").doesNotExist().jsonPath("$.status").isEqualTo("DRAFT");
        ArgumentCaptor<AnnounceKind> details = ArgumentCaptor.forClass(AnnounceKind.class);
        verify(publishing).post(eq(new SellerId(7)), any(), details.capture(), eq("c-1"));
        assertEquals(Kind.GOODS, details.getValue().kind());
    }

    @Test
    void postJobMapsTheJobObject() {
        JobDetails job = JobDetails.of("Acme", ContractType.PART_TIME, null, null, "jobs@acme.invalid");
        when(publishing.post(any(), any(), any(), any())).thenReturn(Mono.just(announce("j1", job)));
        client.post().uri("/api/announces").header(Callers.USER_HEADER, "7").header(Callers.TYPE_HEADER, Callers.MEMBER).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"JOB\",\"title\":\"Barista\",\"categoryId\":9,\"job\":{\"employer\":\"Acme\",\"contractType\":\"PART_TIME\",\"howToApply\":\"jobs@acme.invalid\"}}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.kind").isEqualTo("JOB").jsonPath("$.job.employer").isEqualTo("Acme").jsonPath("$.goods").doesNotExist();
    }

    @Test
    void aKindWithoutItsDetailsObjectIsABadRequest() {
        client.post().uri("/api/announces").header(Callers.USER_HEADER, "7").header(Callers.TYPE_HEADER, Callers.MEMBER).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"EVENT\",\"title\":\"Party\",\"categoryId\":9,\"goods\":{\"price\":10}}")
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("'event'"));
    }

    @Test
    void listingFiltersByKindAndShowsOnlyPublishedToVisitors() {
        when(browsing.count(any())).thenReturn(Mono.just(0L));
        when(browsing.browse(any())).thenReturn(Flux.empty());
        client.get().uri("/api/announces?kind=JOB&status=DRAFT&page=2&size=5").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.total").isEqualTo(0).jsonPath("$.page").isEqualTo(2);
        ArgumentCaptor<AnnounceQuery> query = ArgumentCaptor.forClass(AnnounceQuery.class);
        verify(browsing).count(query.capture());
        assertEquals(Kind.JOB, query.getValue().kind());
        assertEquals(AnnounceStatus.PUBLISHED, query.getValue().status(), "a visitor cannot ask for drafts");
        assertEquals(5, query.getValue().size());
    }

    @Test
    void onlyTheMemberLookingAtTheirOwnAnnouncesMayAskForDrafts() {
        when(browsing.count(any())).thenReturn(Mono.just(0L));
        when(browsing.browse(any())).thenReturn(Flux.empty());
        ArgumentCaptor<AnnounceQuery> query = ArgumentCaptor.forClass(AnnounceQuery.class);

        client.get().uri("/api/announces?seller=7&status=DRAFT").header(Callers.USER_HEADER, "7").header(Callers.TYPE_HEADER, Callers.MEMBER)
                .exchange().expectStatus().isOk();
        client.get().uri("/api/announces?seller=8&status=DRAFT").header(Callers.USER_HEADER, "7").header(Callers.TYPE_HEADER, Callers.MEMBER)
                .exchange().expectStatus().isOk();
        client.get().uri("/api/announces?seller=7&status=DRAFT").header(Callers.USER_HEADER, "7").header(Callers.TYPE_HEADER, Callers.ADMIN)
                .exchange().expectStatus().isOk();
        client.get().uri("/api/announces?seller=7&status=DRAFT").header("X-Seller-Id", "7").exchange().expectStatus().isOk();

        verify(browsing, org.mockito.Mockito.times(4)).count(query.capture());
        assertEquals(AnnounceStatus.DRAFT, query.getAllValues().get(0).status(), "the owner sees their drafts");
        assertEquals(AnnounceStatus.PUBLISHED, query.getAllValues().get(1).status(), "another member does not");
        assertEquals(AnnounceStatus.PUBLISHED, query.getAllValues().get(2).status(), "staff number 7 is not member 7");
        assertEquals(AnnounceStatus.PUBLISHED, query.getAllValues().get(3).status(), "X-Seller-Id is ignored");
    }

    @Test
    void theOwnerIsPassedToTheDetailViewAndAVisitorIsNot() {
        when(browsing.view(eq("a1"), any())).thenReturn(Mono.just(announce("a1", GoodsDetails.fixedPrice(Money.of("10"), Condition.NEW))));
        client.get().uri("/api/announces/a1").header(Callers.USER_HEADER, "7").header(Callers.TYPE_HEADER, Callers.MEMBER).exchange().expectStatus().isOk();
        verify(browsing).view("a1", new SellerId(7));
        client.get().uri("/api/announces/a1").exchange().expectStatus().isOk();
        verify(browsing).view("a1", null);
    }

    @Test
    void unknownAnnounceIs404Problem() {
        when(browsing.view(eq("nope"), any())).thenReturn(Mono.error(new AnnounceNotFoundException("nope")));
        client.get().uri("/api/announces/nope").exchange().expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
