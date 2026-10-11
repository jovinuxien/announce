package org.linlinjava.announce.gatewayapi.auth;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
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

    /** {@code PUT /auth/me}: what a member may change; the username stays the login. */
    public record ProfileUpdateRequest(
            @Size(max = 63) String nickname,
            @Size(max = 20) @Pattern(regexp = "[0-9+ ]*") String mobile,
            @Size(max = 255) String avatar,
            @Min(0) @Max(2) Integer gender,
            @PastOrPresent LocalDate birthday) {
    }

    /** {@code POST /auth/password}: the current password proves the member, the new one replaces it. */
    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 63) String newPassword) {
    }

    /** Gender as {@code announce_user.gender}: 0 unknown, 1 male, 2 female. */
    public record UserResponse(Integer id, String username, String nickname, String mobile, String avatar, Integer gender, LocalDate birthday) {

        static UserResponse of(Account a) {
            return new UserResponse(a.id(), a.username(), a.nickname(), a.mobile(), a.avatar(), a.gender(), a.birthday());
        }
    }

    /** The access token travels in the body; the refresh token only in the httpOnly cookie. */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
