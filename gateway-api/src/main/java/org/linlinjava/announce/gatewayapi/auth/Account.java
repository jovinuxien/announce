package org.linlinjava.announce.gatewayapi.auth;

/** A member of {@code announce_user} as the edge needs it. {@code enabled} is status 0 and not deleted. */
public record Account(Integer id, String username, String passwordHash, String nickname, String mobile, String avatar, boolean enabled,
                      Integer gender, java.time.LocalDate birthday) {

    /** The shape before profile editing (2026-10-11): no gender, no birthday. */
    public Account(Integer id, String username, String passwordHash, String nickname, String mobile, String avatar, boolean enabled) {
        this(id, username, passwordHash, nickname, mobile, avatar, enabled, 0, null);
    }
}
