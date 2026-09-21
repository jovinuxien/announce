package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.goods.application.announce.AnnounceBrowsing;
import org.linlinjava.announce.goods.application.announce.AnnounceNotFoundException;
import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.linlinjava.announce.goods.application.announce.AnnounceStock;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceDetails;
import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.AnnounceRuleViolation;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.linlinjava.announce.goods.infrastructure.configuration.security.CatalogSecurityConfig;
import org.linlinjava.announce.goods.interfaces.rest.CatalogExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AnnounceController.class)
@Import({CatalogExceptionHandler.class, CatalogSecurityConfig.class})
class AnnounceControllerTest {

    @Autowired WebTestClient client;
    @MockBean AnnouncePublishing publishing;
    @MockBean AnnounceStock stock;
    @MockBean AnnounceBrowsing browsing;

    static Announce announce() {
        Announce a = Announce.draft(new SellerId(7), new AnnounceDetails("Bike", "b", "d", List.of("k"), List.of("http://x/1.jpg"), "pcs",
                new CategoryId(3), null, SaleType.FIXED_PRICE, Money.of("10"), Location.inRegion(1, "Stockholm", "11122"), null, null),
                LocalDateTime.of(2026, 9, 20, 12, 0));
        a.assignId(new AnnounceId(42));
        return a;
    }

    @Test
    void draftRequiresTheSellerHeader() {
        client.post().uri("/api/announces").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Bike\",\"categoryId\":3,\"price\":10,\"location\":{\"city\":\"Stockholm\"}}")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void draftReturnsCreatedAnnounce() {
        when(publishing.draft(any())).thenReturn(Mono.just(announce()));
        client.post().uri("/api/announces").header(AnnounceController.SELLER_HEADER, "7").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Bike\",\"categoryId\":3,\"price\":10,\"location\":{\"regionId\":1,\"city\":\"Stockholm\",\"postalCode\":\"11122\"}}")
                .exchange().expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(42)
                .jsonPath("$.status").isEqualTo("DRAFT")
                .jsonPath("$.location.city").isEqualTo("Stockholm")
                .jsonPath("$.price").isEqualTo(10.0);
    }

    @Test
    void unknownAnnounceIs404AndRuleViolationIs409() {
        when(browsing.view(any(), any())).thenReturn(Mono.error(new AnnounceNotFoundException(new AnnounceId(9))));
        client.get().uri("/api/announces/9").exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Announce 9 does not exist");
        when(publishing.withdraw(any())).thenReturn(Mono.error(new AnnounceRuleViolation("Only a published announce can be withdrawn")));
        client.post().uri("/api/announces/42/withdraw").header(AnnounceController.SELLER_HEADER, "7").exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void invalidBodyIs400() {
        client.post().uri("/api/announces").header(AnnounceController.SELLER_HEADER, "7").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"\",\"categoryId\":3}")
                .exchange().expectStatus().isBadRequest();
    }
}
