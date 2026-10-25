package org.linlinjava.announce.messaging.infrastructure.announce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.goods.api.AnnounceApi;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceResponse;
import org.linlinjava.announce.goods.api.AnnounceKindV1;
import org.linlinjava.announce.goods.api.AnnounceStatusV1;
import org.linlinjava.announce.messaging.application.AnnounceLookup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The lookup over the shared descriptor: a found announce yields its facts; a 4xx about the id (404, 400, 422)
 * means "no announce" and completes empty; a refused service token (401, 403), a 5xx or a transport failure
 * become a 503 {@code announce-lookup-failed} instead of an unhandled error (which the controller rendered as 500).
 */
class AnnounceLookupConfigTest {

    final AnnounceApi api = mock(AnnounceApi.class);
    final AnnounceLookup lookup = AnnounceLookupConfig.over(api);

    @Test
    void foundAnnounceYieldsItsFacts() {
        when(api.view("a1")).thenReturn(Mono.just(new AnnounceResponse("a1", AnnounceKindV1.GOODS, 7, AnnounceStatusV1.PUBLISHED, "Bike", "brief",
                "detail", List.of(), List.of(), 12, null, null, null, null, null, null, null, null)));

        StepVerifier.create(lookup.find("a1"))
                .assertNext(facts -> {
                    assertThat(facts.id()).isEqualTo("a1");
                    assertThat(facts.sellerId()).isEqualTo(7);
                    assertThat(facts.title()).isEqualTo("Bike");
                    assertThat(facts.status()).isEqualTo("PUBLISHED");
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 400, 422})
    void aClientErrorAboutTheIdMeansNoAnnounce(int status) {
        when(api.view(anyString())).thenReturn(Mono.error(http(status)));

        StepVerifier.create(lookup.find("bad id")).verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 502, 503})
    void aRefusedTokenOrAFailingServiceIsA503Problem(int status) {
        when(api.view(anyString())).thenReturn(Mono.error(http(status)));

        StepVerifier.create(lookup.find("a1"))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOfSatisfying(CommandRejectedException.class, rejected -> {
                    assertThat(rejected.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(rejected.getCode()).isEqualTo(AnnounceLookupConfig.LOOKUP_FAILED);
                }))
                .verify();
    }

    @Test
    void aTransportFailureIsA503Problem() {
        when(api.view(anyString())).thenReturn(Mono.error(new IOException("Connection refused")));

        StepVerifier.create(lookup.find("a1"))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOfSatisfying(CommandRejectedException.class,
                        rejected -> assertThat(rejected.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)))
                .verify();
    }

    static WebClientResponseException http(int status) {
        return WebClientResponseException.create(status, HttpStatus.valueOf(status).getReasonPhrase(), new HttpHeaders(),
                new byte[0], StandardCharsets.UTF_8);
    }
}
