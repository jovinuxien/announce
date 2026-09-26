package org.linlinjava.announce.gatewayadmin.web.admin;

import java.util.List;

/** The page shape shared with announce-management ({@code items, page, size, total}). */
public record PageResponse<T>(List<T> items, int page, int size, long total) {}
