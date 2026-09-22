package org.linlinjava.announce.goods.domain.announce;

import java.time.LocalDateTime;

/**
 * A job offer. Never biddable: {@code saleType} exists only so the aggregate can reject a client that tries to
 * auction a job; it is normally null.
 */
public record JobDetails(String employer,
                         ContractType contractType,
                         SalaryRange salaryRange,
                         LocalDateTime applicationDeadline,
                         String howToApply,
                         SaleType saleType) implements AnnounceKind {

    public JobDetails {
        if (employer == null || employer.isBlank()) {
            throw new IllegalArgumentException("A job announce needs an employer");
        }
        employer = employer.trim();
        contractType = contractType == null ? ContractType.FULL_TIME : contractType;
        if (howToApply == null || howToApply.isBlank()) {
            throw new IllegalArgumentException("A job announce needs to say how to apply (url or email)");
        }
        howToApply = howToApply.trim();
    }

    public static JobDetails of(String employer, ContractType contractType, SalaryRange salaryRange,
                                LocalDateTime applicationDeadline, String howToApply) {
        return new JobDetails(employer, contractType, salaryRange, applicationDeadline, howToApply, null);
    }

    @Override
    public Kind kind() {
        return Kind.JOB;
    }
}
