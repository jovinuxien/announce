package org.linlinjava.announce.goods.domain.announce;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** A non-negative amount with two decimals (the site trades in one currency). */
public record Money(BigDecimal amount) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money must not be negative");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    @JsonIgnore
    public boolean isZero() {
        return amount.signum() == 0;
    }
}
