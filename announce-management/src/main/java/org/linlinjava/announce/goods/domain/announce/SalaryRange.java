package org.linlinjava.announce.goods.domain.announce;

import java.math.BigDecimal;

/** Optional salary range of a job, with its ISO 4217 currency. */
public record SalaryRange(BigDecimal min, BigDecimal max, String currency) {

    public SalaryRange {
        if (min == null && max == null) {
            throw new IllegalArgumentException("A salary range needs a minimum or a maximum");
        }
        if (min != null && min.signum() < 0 || max != null && max.signum() < 0) {
            throw new IllegalArgumentException("A salary must not be negative");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("The minimum salary is above the maximum");
        }
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("A salary range needs a three-letter currency code");
        }
        currency = currency.trim().toUpperCase();
    }
}
