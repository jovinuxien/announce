package org.linlinjava.announce.gatewayapi.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineTokensTest {

    private final MachineTokenProperties props = new MachineTokenProperties("gateway-api", "s3cret");

    /**
     * The vector shared with cqrs-reactive's {@code ServiceTokensTest} (the verifier in the backends) and with
     * gateway-admin's {@code MachineTokensTest}: change it on every side or not at all.
     */
    @Test
    void mintsTheVectorTheBackendsVerify() {
        assertEquals("v1.gateway-api.1767225600.57caecf4b153b16e1c7f6c994128cee947306e0f24b597dca596b1d3e1b66adc",
                MachineTokens.mint(new MachineTokenProperties("gateway-api", "test-secret"), 1767225600L));
    }

    @Test
    void mintedTokenVerifiesWithinTheSkew() {
        String token = MachineTokens.mint(props, 1_000_000);
        assertTrue(token.startsWith("v1.gateway-api.1000000."));
        assertTrue(MachineTokens.verify(props, token, 1_000_030, 60));
        assertFalse(MachineTokens.verify(props, token, 1_000_100, 60), "stale");
        assertFalse(MachineTokens.verify(new MachineTokenProperties("gateway-api", "other"), token, 1_000_000, 60), "wrong secret");
        assertFalse(MachineTokens.verify(new MachineTokenProperties("gateway-admin", "s3cret"), token, 1_000_000, 60), "wrong service");
        assertFalse(MachineTokens.verify(props, "v1.gateway-api.abc.def", 1_000_000, 60));
        assertFalse(MachineTokens.verify(props, null, 1_000_000, 60));
    }
}
