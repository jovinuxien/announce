package org.linlinjava.announce.gatewayadmin.auth;

/** Wrong credentials, unknown or disabled account, or an account without any enabled role: always a 401 to the client. */
public class AuthenticationFailure extends RuntimeException {
    public AuthenticationFailure(String message) {
        super(message);
    }
}
