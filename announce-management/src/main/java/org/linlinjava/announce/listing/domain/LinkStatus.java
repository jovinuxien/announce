package org.linlinjava.announce.listing.domain;

/** What a liveness probe of a url found. */
public enum LinkStatus {
    /** 2xx or 3xx: the resource is there. */
    OK,
    /** 404 or 410: the resource is gone for good. */
    GONE,
    /** Any other status (403, 429, 5xx): the resource could not be confirmed either way. */
    ERROR,
    /** No HTTP answer at all (DNS, connect, timeout). */
    UNREACHABLE;

    public static LinkStatus ofHttp(int code) {
        if (code >= 200 && code < 400) {
            return OK;
        }
        return code == 404 || code == 410 ? GONE : ERROR;
    }
}
