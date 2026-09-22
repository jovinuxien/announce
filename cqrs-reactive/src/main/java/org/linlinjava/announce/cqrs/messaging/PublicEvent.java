package org.linlinjava.announce.cqrs.messaging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a versioned public event, the published language of a service. Public event classes live in the
 * service's {@code <name>-api} module so consumers depend on the contract, never on the internal events.
 *
 * <pre>{@code
 * @PublicEvent(type = "announce.published", version = 1)
 * public record AnnouncePublishedV1(String announceId, String title, long priceCents) {}
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PublicEvent {

    /** Stable, dotted type name carried in the {@code event-type} header. */
    String type();

    /** Schema version carried in the {@code event-version} header; bump on incompatible changes. */
    int version() default 1;
}
