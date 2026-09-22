package org.linlinjava.announce.goods.application.announce;

/** The announce does not exist in the read model or is not visible to the caller. */
public class AnnounceNotFoundException extends RuntimeException {
    public AnnounceNotFoundException(String id) {
        super("Announce " + id + " not found");
    }
}
