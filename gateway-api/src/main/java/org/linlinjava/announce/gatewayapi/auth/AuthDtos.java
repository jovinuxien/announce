package org.linlinjava.announce.gatewayapi.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies of {@link AuthController}; the SPA's {@code app/shared/model/auth.ts} mirrors them. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 63) @Pattern(regexp = "[A-Za-z0-9._@-]+") String username,
            @NotBlank @Size(min = 8, max = 63) String password,
            @Size(max = 63) String nickname,
            @Size(max = 20) String mobile) {
    }

    public record UserResponse(Integer id, String username, String nickname, String mobile, String avatar) {

        static UserResponse of(Account a) {
            return new UserResponse(a.id(), a.username(), a.nickname(), a.mobile(), a.avatar());
        }
    }

    /** The access token travels in the body; the refresh token only in the httpOnly cookie. */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
