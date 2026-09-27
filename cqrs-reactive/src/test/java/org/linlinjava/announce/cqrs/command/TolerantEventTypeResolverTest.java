package org.linlinjava.announce.cqrs.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.framework.types.EventTypeResolutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TolerantEventTypeResolverTest {

    private final TolerantEventTypeResolver resolver = new TolerantEventTypeResolver(getClass().getClassLoader());

    @Test
    void resolvesOwnClassesBothWays() throws Exception {
        assertThat(resolver.getEventType(Sample.class)).isEqualTo(Sample.class.getName());
        assertThat(resolver.getJavaClass(Sample.class.getName())).isEqualTo(Sample.class);
        assertThat(resolver.unknownTypes()).isEmpty();
    }

    @Test
    void unknownTypeBecomesForeignEventAndIsRememberedOnce() throws Exception {
        String other = "org.linlinjava.announce.goods.domain.announce.events.AnnouncePosted";
        assertThat(resolver.getJavaClass(other)).isEqualTo(ForeignEvent.class);
        assertThat(resolver.getJavaClass(other)).isEqualTo(ForeignEvent.class);
        assertThat(resolver.unknownTypes()).containsExactly(other);
    }

    @Test
    void foreignEventDeserialisesFromAnyPayload() throws Exception {
        ForeignEvent event = new ObjectMapper().readValue("{\"at\":\"2026-09-26T13:42:33\",\"content\":{\"title\":\"x\"}}", ForeignEvent.class);
        assertThat(event).isNotNull();
    }

    @Test
    void foreignEventCanNeverBePublished() {
        assertThatThrownBy(() -> resolver.getEventType(ForeignEvent.class)).isInstanceOf(EventTypeResolutionException.class);
    }

    record Sample(String value) {
    }
}
