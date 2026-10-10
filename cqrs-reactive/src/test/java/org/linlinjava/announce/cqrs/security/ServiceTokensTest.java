package org.linlinjava.announce.cqrs.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTokensTest {

    /** The vector shared with the edges' own MachineTokens tests: change it on both sides or not at all. */
    static final String SECRET = "test-secret";
    static final long EPOCH = 1767225600L;
    static final String VECTOR = "v1.gateway-api.1767225600.57caecf4b153b16e1c7f6c994128cee947306e0f24b597dca596b1d3e1b66adc";

    @Test
    void mintsTheSharedVector() {
        assertThat(ServiceTokens.mint("gateway-api", SECRET, EPOCH)).isEqualTo(VECTOR);
    }

    @Test
    void verifiesAFreshTokenAndNamesItsIssuer() {
        assertThat(ServiceTokens.verify(SECRET, VECTOR, EPOCH + 30, 60)).contains("gateway-api");
        assertThat(ServiceTokens.verify(SECRET, VECTOR, EPOCH - 30, 60)).contains("gateway-api");
    }

    @Test
    void rejectsStaleForgedAndMalformedTokens() {
        assertThat(ServiceTokens.verify(SECRET, VECTOR, EPOCH + 61, 60)).isEmpty();
        assertThat(ServiceTokens.verify("another-secret", VECTOR, EPOCH, 60)).isEmpty();
        assertThat(ServiceTokens.verify(SECRET, VECTOR.replace("gateway-api", "gateway-admin"), EPOCH, 60)).isEmpty();
        assertThat(ServiceTokens.verify(SECRET, VECTOR.replace("1767225600", "1767225601"), EPOCH, 60)).isEmpty();
        assertThat(ServiceTokens.verify(SECRET, "v2" + VECTOR.substring(2), EPOCH, 60)).isEmpty();
        // the admin edge's former three-part layout is no longer accepted
        assertThat(ServiceTokens.verify(SECRET, "gateway-admin.1767225600.abcdef", EPOCH, 60)).isEmpty();
        assertThat(ServiceTokens.verify(SECRET, "v1.gateway-api.soon.abcdef", EPOCH, 60)).isEmpty();
        assertThat(ServiceTokens.verify(SECRET, null, EPOCH, 60)).isEmpty();
        assertThat(ServiceTokens.verify(SECRET, "", EPOCH, 60)).isEmpty();
        assertThat(ServiceTokens.verify("", VECTOR, EPOCH, 60)).isEmpty();
    }

    @Test
    void refusesToMintWithoutASecretOrWithADottedName() {
        assertThatThrownBy(() -> ServiceTokens.mint("order", "", EPOCH)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ServiceTokens.mint("order.v1", SECRET, EPOCH)).isInstanceOf(IllegalArgumentException.class);
    }
}
