package org.linlinjava.announce.gatewayapi.auth;

/** A member of {@code announce_user} as the edge needs it. {@code enabled} is status 0 and not deleted. */
public record Account(Integer id, String username, String passwordHash, String nickname, String mobile, String avatar, boolean enabled) {
}
