package org.linlinjava.announce.gatewayadmin.machine;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.gatewayadmin.config.MachineTokenProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MachineTokensTest {

    private static MachineTokens at(long epochSeconds, String secret, String issuer) {
        return new MachineTokens(new MachineTokenProperties(secret, issuer), Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
    }

    /**
     * The vector shared with cqrs-reactive's {@code ServiceTokensTest} (the verifier in the backends) and with
     * gateway-api's {@code MachineTokensTest}: change it on every side or not at all.
     */
    @Test
    void mintsTheVectorTheBackendsVerify() {
        assertThat(at(1767225600L, "test-secret", "gateway-api").token())
                .isEqualTo("v1.gateway-api.1767225600.57caecf4b153b16e1c7f6c994128cee947306e0f24b597dca596b1d3e1b66adc");
    }

    @Test
    void verifiesItsOwnFreshTokenOnly() {
        String token = at(1_000_000, "s3cret", "gateway-admin").token();
        assertThat(token).startsWith("v1.gateway-admin.1000000.");
        assertThat(at(1_000_030, "s3cret", "gateway-admin").verify(token, 60)).isTrue();
        assertThat(at(1_000_100, "s3cret", "gateway-admin").verify(token, 60)).as("stale").isFalse();
        assertThat(at(1_000_000, "other", "gateway-admin").verify(token, 60)).as("wrong secret").isFalse();
        assertThat(at(1_000_000, "s3cret", "gateway-admin").verify("gateway-admin.1000000.abc", 60)).as("former layout").isFalse();
        assertThat(at(1_000_000, "s3cret", "gateway-admin").verify(null, 60)).isFalse();
    }
}
