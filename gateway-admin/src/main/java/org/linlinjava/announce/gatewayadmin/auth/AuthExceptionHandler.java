package org.linlinjava.announce.gatewayadmin.auth;

import org.linlinjava.announce.gatewayadmin.config.RefreshProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.time.Duration;

/** Problem details for the {@code /auth} endpoints; a bad refresh token also clears the cookie. */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    private final RefreshProperties refresh;

    public AuthExceptionHandler(RefreshProperties refresh) {
        this.refresh = refresh;
    }

    @ExceptionHandler(AuthenticationFailure.class)
    public ResponseEntity<ProblemDetail> badCredentials(AuthenticationFailure e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.UNAUTHORIZED, "bad-credentials", e.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> badRefreshToken(InvalidRefreshTokenException e) {
        ResponseCookie clear = ResponseCookie.from(refresh.cookieName(), "").httpOnly(true).secure(refresh.cookieSecure())
                .path("/auth").sameSite("Strict").maxAge(Duration.ZERO).build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .body(problem(HttpStatus.UNAUTHORIZED, "invalid-refresh-token", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ServerWebInputException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.BAD_REQUEST, "invalid-request", e instanceof ServerWebInputException s ? s.getReason() : e.getMessage()));
    }

    static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        p.setType(URI.create("urn:announce:auth:" + type));
        return p;
    }
}
