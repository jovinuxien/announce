package org.linlinjava.announce.cqrs.command;

import com.opencqrs.framework.types.ClassNameEventTypeResolver;
import com.opencqrs.framework.types.EventTypeResolutionException;
import com.opencqrs.framework.types.EventTypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link EventTypeResolver} that maps a class to its name like OpenCQRS's {@link ClassNameEventTypeResolver} and,
 * when a stored type name is not a class of this service, answers {@link ForeignEvent} instead of throwing.
 *
 * <p>Every service of the platform shares one EventSourcingDB, and a processor's fetch subject may cover events
 * written by another service (the order module's payment context reads {@code /} because it owns two subject
 * roots). With the strict resolver one foreign event stops the processor for good; with this one it is decoded
 * as a {@link ForeignEvent}, dispatched to no handler and skipped. Each unknown type is logged once.
 *
 * <p>Writing is unchanged: a {@link ForeignEvent} can never be published, its type name is refused.
 */
public class TolerantEventTypeResolver implements EventTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(TolerantEventTypeResolver.class);

    private final EventTypeResolver delegate;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public TolerantEventTypeResolver(ClassLoader classLoader) {
        this(new ClassNameEventTypeResolver(classLoader));
    }

    public TolerantEventTypeResolver(EventTypeResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getEventType(Class<?> clazz) throws EventTypeResolutionException {
        if (ForeignEvent.class.equals(clazz)) {
            throw new EventTypeResolutionException("ForeignEvent is a read-side placeholder and cannot be published", null);
        }
        return delegate.getEventType(clazz);
    }

    @Override
    public Class<?> getJavaClass(String eventType) throws EventTypeResolutionException {
        try {
            return delegate.getJavaClass(eventType);
        } catch (EventTypeResolutionException e) {
            if (reported.add(eventType)) {
                log.info("Event type {} is not a class of this service; its events are skipped", eventType);
            }
            return ForeignEvent.class;
        }
    }

    /** The unknown type names met so far, for diagnostics and tests. */
    public Set<String> unknownTypes() {
        return Set.copyOf(reported);
    }
}
