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
@Import({CatalogExceptionHandler.class, CatalogSecurityConfig.class})
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

    @Test
    void postRequiresTheSellerHeader() {
        client.post().uri("/api/announces").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"GOODS\",\"title\":\"Bike\",\"categoryId\":3,\"goods\":{\"price\":10}}")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void postGoodsReturnsTheCreatedAnnounceWithItsKind() {
        when(publishing.post(any(), any(), any(), any())).thenReturn(Mono.just(announce("a1", GoodsDetails.fixedPrice(Money.of("10"), Condition.NEW))));
        client.post().uri("/api/announces").header(AnnounceController.SELLER_HEADER, "7").header(AnnounceController.COMMAND_ID_HEADER, "c-1")
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
        client.post().uri("/api/announces").header(AnnounceController.SELLER_HEADER, "7").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"JOB\",\"title\":\"Barista\",\"categoryId\":9,\"job\":{\"employer\":\"Acme\",\"contractType\":\"PART_TIME\",\"howToApply\":\"jobs@acme.invalid\"}}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.kind").isEqualTo("JOB").jsonPath("$.job.employer").isEqualTo("Acme").jsonPath("$.goods").doesNotExist();
    }

    @Test
    void aKindWithoutItsDetailsObjectIsABadRequest() {
        client.post().uri("/api/announces").header(AnnounceController.SELLER_HEADER, "7").contentType(MediaType.APPLICATION_JSON)
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
    void unknownAnnounceIs404Problem() {
        when(browsing.view(eq("nope"), any())).thenReturn(Mono.error(new AnnounceNotFoundException("nope")));
        client.get().uri("/api/announces/nope").exchange().expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
