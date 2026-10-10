package org.linlinjava.announce.cqrs.security;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallersTest {

    private static HttpHeaders headers(String... pairs) {
        HttpHeaders headers = new HttpHeaders();
        for (int i = 0; i < pairs.length; i += 2) {
            headers.add(pairs[i], pairs[i + 1]);
        }
        return headers;
    }

    static final HttpHeaders MEMBER_7 = headers(Callers.USER_HEADER, "7", Callers.TYPE_HEADER, "MEMBER");
    static final HttpHeaders STAFF_42 = headers(Callers.USER_HEADER, "42", Callers.TYPE_HEADER, "ADMIN", Callers.ROLES_HEADER, "ROLE_ADMIN, role_super");
    static final HttpHeaders NOBODY = headers();

    private static void rejected(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CommandRejectedException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void readsMemberAndStaffWithTheirRoles() {
        assertThat(Callers.caller(MEMBER_7)).contains(new Caller(7, Caller.Type.MEMBER, java.util.Set.of()));
        Caller staff = Callers.caller(STAFF_42).orElseThrow();
        assertThat(staff.id()).isEqualTo(42);
        assertThat(staff.staff()).isTrue();
        assertThat(staff.hasRole("ROLE_SUPER")).as("roles are trimmed and upper-cased").isTrue();
        assertThat(Callers.caller(NOBODY)).isEmpty();
    }

    @Test
    void memberRoutesTakeMembersOnly() {
        assertThat(Callers.member(MEMBER_7)).isEqualTo(7);
        rejected(() -> Callers.member(STAFF_42), HttpStatus.FORBIDDEN, Callers.NOT_A_MEMBER);
        rejected(() -> Callers.member(NOBODY), HttpStatus.UNAUTHORIZED, Callers.NO_PRINCIPAL);
    }

    @Test
    void staffRoutesTakeStaffOnly() {
        assertThat(Callers.staff(STAFF_42)).isEqualTo(42);
        rejected(() -> Callers.staff(MEMBER_7), HttpStatus.FORBIDDEN, Callers.NOT_STAFF);
        rejected(() -> Callers.staff(NOBODY), HttpStatus.UNAUTHORIZED, Callers.NO_PRINCIPAL);
    }

    @Test
    void anyTakesBothAndMemberIfAnyNeverThrowsForStaffOrVisitors() {
        assertThat(Callers.any(MEMBER_7).member()).isTrue();
        assertThat(Callers.any(STAFF_42).staff()).isTrue();
        rejected(() -> Callers.any(NOBODY), HttpStatus.UNAUTHORIZED, Callers.NO_PRINCIPAL);
        assertThat(Callers.memberIfAny(MEMBER_7)).contains(7);
        assertThat(Callers.memberIfAny(STAFF_42)).isEmpty();
        assertThat(Callers.memberIfAny(NOBODY)).isEmpty();
    }

    @Test
    void anIdWithoutAKnownTypeIsNotAnIdentity() {
        // the former single headers carry no type: neither X-Seller-Id nor X-Staff-Id nor X-Admin-Id is read
        assertThat(Callers.caller(headers("X-Staff-Id", "1", "X-Admin-Id", "1", "X-Seller-Id", "1"))).isEmpty();
        rejected(() -> Callers.caller(headers(Callers.USER_HEADER, "7")), HttpStatus.FORBIDDEN, Callers.UNKNOWN_TYPE);
        rejected(() -> Callers.caller(headers(Callers.USER_HEADER, "7", Callers.TYPE_HEADER, "ROOT")), HttpStatus.FORBIDDEN, Callers.UNKNOWN_TYPE);
        assertThatThrownBy(() -> Callers.caller(headers(Callers.USER_HEADER, "seven", Callers.TYPE_HEADER, "MEMBER")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
