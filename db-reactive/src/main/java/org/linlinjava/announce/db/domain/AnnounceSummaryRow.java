package org.linlinjava.announce.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the announce listing query ({@code AnnounceBaseMapper.selectSummaries}): the shared columns of
 * {@code announce} joined with the few columns a list needs from the detail table of each kind. Hand-written
 * (not generated): the detail columns of the other kinds are null.
 */
public class AnnounceSummaryRow {
    private String id;
    private String kind;
    private Integer sellerId;
    private Integer categoryId;
    private String title;
    private String brief;
    private String[] gallery;
    private Integer regionId;
    private String city;
    private String postalCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime addTime;
    private Integer moderatedBy;
    private String moderationReason;
    // goods
    private BigDecimal price;
    private String saleType;
    private String condition;
    private Integer brandId;
    // job
    private String employer;
    private String contractType;
    private LocalDateTime applicationDeadline;
    private LocalDateTime auctionStartsAt;
    private LocalDateTime auctionEndsAt;
    // event
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Boolean ticketed;
    private BigDecimal ticketPrice;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Integer getSellerId() { return sellerId; }
    public void setSellerId(Integer sellerId) { this.sellerId = sellerId; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBrief() { return brief; }
    public void setBrief(String brief) { this.brief = brief; }
    public String[] getGallery() { return gallery; }
    public void setGallery(String[] gallery) { this.gallery = gallery; }
    public Integer getRegionId() { return regionId; }
    public void setRegionId(Integer regionId) { this.regionId = regionId; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }
    public Integer getModeratedBy() { return moderatedBy; }
    public void setModeratedBy(Integer moderatedBy) { this.moderatedBy = moderatedBy; }
    public String getModerationReason() { return moderationReason; }
    public void setModerationReason(String moderationReason) { this.moderationReason = moderationReason; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getSaleType() { return saleType; }
    public void setSaleType(String saleType) { this.saleType = saleType; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public Integer getBrandId() { return brandId; }
    public void setBrandId(Integer brandId) { this.brandId = brandId; }
    public String getEmployer() { return employer; }
    public void setEmployer(String employer) { this.employer = employer; }
    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }
    public LocalDateTime getApplicationDeadline() { return applicationDeadline; }
    public void setApplicationDeadline(LocalDateTime applicationDeadline) { this.applicationDeadline = applicationDeadline; }
    public LocalDateTime getAuctionStartsAt() { return auctionStartsAt; }
    public void setAuctionStartsAt(LocalDateTime auctionStartsAt) { this.auctionStartsAt = auctionStartsAt; }
    public LocalDateTime getAuctionEndsAt() { return auctionEndsAt; }
    public void setAuctionEndsAt(LocalDateTime auctionEndsAt) { this.auctionEndsAt = auctionEndsAt; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public Boolean getTicketed() { return ticketed; }
    public void setTicketed(Boolean ticketed) { this.ticketed = ticketed; }
    public BigDecimal getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(BigDecimal ticketPrice) { this.ticketPrice = ticketPrice; }
}
