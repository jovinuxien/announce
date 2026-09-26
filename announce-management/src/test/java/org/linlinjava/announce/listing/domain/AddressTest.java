package org.linlinjava.announce.listing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressTest {

    @Test
    void danishPostalCodeThenCity() {
        Address a = Address.parse("\r\n    6580 Vamdrup\r\n");
        assertThat(a.postalCode()).isEqualTo("6580");
        assertThat(a.city()).isEqualTo("Vamdrup");
        assertThat(a.raw()).isEqualTo("6580 Vamdrup");
    }

    @Test
    void swedishFiveDigitsAndTwoWordCity() {
        Address a = Address.parse("113 45 Stockholm");
        assertThat(a.postalCode()).isEqualTo("113");   // a spaced Swedish code is not recognised: the first group wins
        assertThat(a.city()).isEqualTo("45 Stockholm");
        Address b = Address.parse("11345 Södra Sandby");
        assertThat(b.postalCode()).isEqualTo("11345");
        assertThat(b.city()).isEqualTo("Södra Sandby");
    }

    @Test
    void cityThenPostalCodeAndPlainCity() {
        assertThat(Address.parse("Vamdrup 6580")).isEqualTo(new Address("Vamdrup 6580", "6580", "Vamdrup"));
        assertThat(Address.parse("København")).isEqualTo(new Address("København", null, "København"));
        assertThat(Address.parse("  ")).isSameAs(Address.NONE);
        assertThat(Address.parse(null)).isSameAs(Address.NONE);
    }
}
