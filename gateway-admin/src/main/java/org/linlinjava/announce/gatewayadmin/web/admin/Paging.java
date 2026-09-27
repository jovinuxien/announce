package org.linlinjava.announce.gatewayadmin.web.admin;

/** 1-based page, size clamped to 1..100, as in the backends' listings. */
public record Paging(int page, int size) {
    public static final int MAX_SIZE = 100;

    public static Paging of(Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);
        return new Paging(p, s);
    }

    public long offset() {
        return (long) (page - 1) * size;
    }

    public static String like(String q) {
        return "%" + q.trim() + "%";
    }

    public static boolean has(String q) {
        return q != null && !q.isBlank();
    }
}
