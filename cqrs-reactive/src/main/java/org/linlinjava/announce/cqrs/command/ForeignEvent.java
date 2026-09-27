package org.linlinjava.announce.cqrs.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Stand-in for an event whose type name resolves to no class on this service's classpath: another service's
 * internal event met while a processor reads a shared subject tree (for example {@code /} or {@code /announce}
 * from the order module). It deserialises from any payload and matches no projection handler, so the
 * {@code readside} adapter skips it and the processor keeps its checkpoint moving instead of failing on
 * {@code EventTypeResolutionException} until its retry policy gives up. See {@link TolerantEventTypeResolver}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForeignEvent() {
}
