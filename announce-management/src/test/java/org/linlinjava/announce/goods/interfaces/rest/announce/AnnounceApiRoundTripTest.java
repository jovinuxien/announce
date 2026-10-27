package org.linlinjava.announce.goods.interfaces.rest.announce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.CqrsProperties;
import org.linlinjava.announce.cqrs.api.HttpExchangeClients;
import org.linlinjava.announce.cqrs.command.CqrsProblemDetailsHandler;
import org.linlinjava.announce.cqrs.security.ServiceTokenAutoConfiguration;
import org.linlinjava.announce.cqrs.security.ServiceTokenMinter;
import org.linlinjava.announce.cqrs.test.EdgeRequests;
import org.linlinjava.announce.goods.api.AnnounceApi;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceResponse;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceSummaryResponse;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.PageResponse;
import org.linlinjava.announce.goods.api.AnnounceKindV1;
import org.linlinjava.announce.goods.api.AnnounceStatusV1;
import org.linlinjava.announce.goods.api.SaleTypeV1;
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
import org.linlinjava.announce.goods.interfaces.rest.CallerContextFilter;
import org.linlinjava.announce.goods.interfaces.rest.CatalogExceptionHandler;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.HttpHandlerConnector;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The descriptor round trip: {@link AnnounceApi} turned into a client by {@link HttpExchangeClients} (base URL
 * pinned, service token on) against the controller that implements it. The client's records must be the same JSON
 * the controller serves to a signed direct request, a client without the token is refused, and a client call
 * carries no member identity, so it sees what a visitor sees.
 */
@WebFluxTest(controllers = AnnounceController.class, properties = {EdgeRequests.ENFORCE, EdgeRequests.SECRET_PROPERTY, "spring.application.name=announce-management"})
@Import({CatalogExceptionHandler.class, CatalogSecurityConfig.class, CqrsProblemDetailsHandler.class, CallerContextFilter.class})
@ImportAutoConfiguration(ServiceTokenAutoConfiguration.class)
class AnnounceApiRoundTripTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);
    static final String SERVICE = "announce-management";

    @Autowired ApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired ServiceTokenMinter minter;
    @MockBean AnnouncePublishing publishing;
    @MockBean AnnounceBrowsing browsing;

    WebTestClient direct;          // a signed request straight at the controller, as the edge would send it
    AnnounceApi api;               // the descriptor as a service client, signing as this service
    AnnounceApi unsigned;          // a client that forgot to opt into the service token

    @BeforeEach
    void clients() {
        HttpHandlerConnector connector = new HttpHandlerConnector(WebHttpHandlerBuilder.applicationContext(context).build());
        direct = WebTestClient.bindToApplicationContext(context).build().mutate().filter(EdgeRequests.signing()).build();
        WebClient.Builder builder = WebClient.builder().clientConnector(connector);
        api = new HttpExchangeClients(builder, null, null, pinned(true), minter).create(AnnounceApi.class, SERVICE);
        unsigned = new HttpExchangeClients(builder, null, null, pinned(false), minter).create(AnnounceApi.class, SERVICE);
    }

    private static CqrsProperties.Api pinned(boolean serviceToken) {
        CqrsProperties.Api api = new CqrsProperties.Api();
        CqrsProperties.Api.Client client = new CqrsProperties.Api.Client();
        client.setBaseUrl("http://announce-management.test");   // any absolute URL: the connector routes to the test context
        client.setServiceToken(serviceToken);
        api.getClients().put(SERVICE, client);
        return api;
    }

    static Announce announce(String id) {
        AnnounceContent content = new AnnounceContent("Bike", "b", "d", List.of("k"), List.of("http://x/1.jpg"), new CategoryId(3),
                Location.inRegion(1, "Stockholm", "11122"));
        return new Announce(id, new SellerId(7), content, GoodsDetails.fixedPrice(Money.of("10"), Condition.NEW), AnnounceStatus.PUBLISHED,
                NOW, NOW.plusDays(30), NOW, NOW);
    }

    static AnnounceSummary summary(String id) {
        return new AnnounceSummary(id, Kind.GOODS, new SellerId(7), "Bike", "b", "http://x/1.jpg", AnnounceStatus.PUBLISHED, new CategoryId(3),
                Location.inRegion(1, "Stockholm", "11122"), NOW, NOW, NOW.plusDays(30),
                new AnnounceSummary.KindFacts(Money.of("10"), SaleType.FIXED_PRICE, Condition.NEW, null, null, null, null, null, null, null, null, null), null);
    }

    @Test
    void viewThroughTheClientIsTheJsonTheControllerServes() throws Exception {
        when(browsing.view(eq("a1"), any())).thenReturn(Mono.just(announce("a1")));

        String served = direct.get().uri("/api/announces/a1").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        AnnounceResponse viaClient = api.view("a1").block();

        assertThat(viaClient).isNotNull();
        assertThat(viaClient.kind()).isEqualTo(AnnounceKindV1.GOODS);
        assertThat(viaClient.status()).isEqualTo(AnnounceStatusV1.PUBLISHED);
        assertThat(viaClient.goods().saleType()).isEqualTo(SaleTypeV1.FIXED_PRICE);
        assertThat(json.readTree(json.writeValueAsString(viaClient))).isEqualTo(json.readTree(served));
        // the client carries no X-User-Id: the service asked as a visitor, both times
        verify(browsing, org.mockito.Mockito.times(2)).view("a1", null);
    }

    @Test
    void browseThroughTheClientIsTheJsonTheControllerServesAndSeesOnlyPublished() throws Exception {
        when(browsing.count(any())).thenReturn(Mono.just(1L));
        when(browsing.browse(any())).thenReturn(Flux.just(summary("a1")));

        String served = direct.get().uri("/api/announces?kind=GOODS&seller=7&status=DRAFT&page=2&size=5").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        PageResponse<AnnounceSummaryResponse> viaClient = api.browse(AnnounceKindV1.GOODS, null, null, 7, null, null, null,
                AnnounceStatusV1.DRAFT, 2, 5, "newest").block();

        assertThat(viaClient).isNotNull();
        assertThat(viaClient.items()).hasSize(1);
        assertThat(viaClient.items().get(0).kind()).isEqualTo(AnnounceKindV1.GOODS);
        assertThat(viaClient.page()).isEqualTo(2);
        JsonNode servedTree = json.readTree(served);
        assertThat(json.readTree(json.writeValueAsString(viaClient))).isEqualTo(servedTree);

        ArgumentCaptor<AnnounceQuery> query = ArgumentCaptor.forClass(AnnounceQuery.class);
        verify(browsing, org.mockito.Mockito.times(2)).count(query.capture());
        assertThat(query.getAllValues()).allSatisfy(q -> {
            assertThat(q.kind()).isEqualTo(Kind.GOODS);
            assertThat(q.status()).as("nobody signed in: drafts are not shown").isEqualTo(AnnounceStatus.PUBLISHED);
            assertThat(q.size()).isEqualTo(5);
        });
    }

    @Test
    void aMissingAnnounceIsA404OnTheClientSide() {
        when(browsing.view(eq("nope"), any())).thenReturn(Mono.error(new AnnounceNotFoundException("nope")));
        StepVerifier.create(api.view("nope")).expectError(WebClientResponseException.NotFound.class).verify();
    }

    @Test
    void aClientWithoutTheServiceTokenIsRefused() {
        StepVerifier.create(unsigned.view("a1")).expectError(WebClientResponseException.Unauthorized.class).verify();
    }
}
