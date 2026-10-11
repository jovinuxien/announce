package org.linlinjava.announce.db.domain;

import java.math.BigDecimal;

/**
 * One row of {@code AnnounceOrderMapper.sumByStatus}: how many live orders are in a status and what their fees
 * and payouts add up to. Hand-written (not generated): the payouts overview of the order module reads it.
 */
public class AnnounceOrderStatusTotal {
    private String status;
    private Long orders;
    private BigDecimal feeAmount;
    private BigDecimal payoutAmount;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getOrders() { return orders; }
    public void setOrders(Long orders) { this.orders = orders; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public BigDecimal getPayoutAmount() { return payoutAmount; }
    public void setPayoutAmount(BigDecimal payoutAmount) { this.payoutAmount = payoutAmount; }
}
