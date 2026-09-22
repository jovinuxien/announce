package org.linlinjava.announce.cqrs.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectsTest {

    @Test
    void buildsAndParsesConventionalSubjects() {
        assertThat(Subjects.of("announce", 42)).isEqualTo("/announce/42");
        assertThat(Subjects.child("/announce/42", "variant", "7")).isEqualTo("/announce/42/variant/7");
        assertThat(Subjects.typeOf("announce")).isEqualTo("/announce");
        assertThat(Subjects.idOf("/announce/42")).isEqualTo("42");
        assertThat(Subjects.idOf("/announce/42/variant/7")).isEqualTo("7");
        assertThat(Subjects.aggregateOf("/announce/42/variant/7")).isEqualTo("announce");
        assertThat(Subjects.rootOf("/announce/42/variant/7")).isEqualTo("/announce/42");
    }

    @Test
    void rejectsInvalidSegments() {
        assertThatThrownBy(() -> Subjects.of("announce", "4/2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Subjects.of("an nounce", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Subjects.idOf("announce/1")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Subjects.isValid("/announce/")).isFalse();
        assertThat(Subjects.isValid("/announce/abc-1_2.3~")).isTrue();
    }
}
