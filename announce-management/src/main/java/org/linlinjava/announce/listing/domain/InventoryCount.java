package org.linlinjava.announce.listing.domain;

/**
 * One group of the inventory query: rows of a source with a status and a grade ({@code grade} null for rows imported
 * before grading existed), with how many lack a usable image, how many have a dead link and how many were checked.
 */
public record InventoryCount(ListingSource source, ListingStatus status, ListingGrade grade, long total,
                             long missingImage, long deadLink, long checked) {
}
