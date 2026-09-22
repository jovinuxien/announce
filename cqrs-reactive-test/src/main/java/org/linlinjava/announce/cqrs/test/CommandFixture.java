package org.linlinjava.announce.cqrs.test;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandEventCapturer;
import com.opencqrs.framework.command.CommandEventPublisher;
import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.CommandHandling;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import com.opencqrs.framework.command.StateRebuilding;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import com.opencqrs.framework.persistence.CapturedEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Given/when/then fixture for one aggregate, the {@code PersistentEntityTestDriver} of this framework.
 * It rebuilds the state from the given events with the aggregate's state rebuilding handlers, runs the
 * command handler with a capturing publisher and exposes the published events, the result or the error.
 * No EventSourcingDB is involved.
 *
 * <pre>{@code
 * CommandFixture.forAggregate(Listing.class)
 *         .handlers(listingDefinitions)            // CommandHandlerDefinition / StateRebuildingHandlerDefinition beans
 *         .given(new ListingCreated("Bike", 12000))
 *         .when(new ChangeListingPrice("1", 9900))
 *         .thenExpectEvents(new ListingPriceChanged(9900));
 * }</pre>
 * Handlers can also be given as lambdas ({@link #commandHandling}, {@link #stateRebuilding}) or as objects
 * carrying {@link CommandHandling @CommandHandling} / {@link StateRebuilding @StateRebuilding} methods.
 */
public final class CommandFixture<I> {

    private final Class<I> instanceClass;
    private final List<StateRebuildingHandlerDefinition<I, Object>> stateRebuilding = new ArrayList<>();
    private final List<CommandHandlerDefinition<I, Command, Object>> commandHandling = new ArrayList<>();
    private String subject;

    private CommandFixture(Class<I> instanceClass) {
        this.instanceClass = Objects.requireNonNull(instanceClass, "instanceClass");
    }

    public static <I> CommandFixture<I> forAggregate(Class<I> instanceClass) {
        return new CommandFixture<>(instanceClass);
    }

    /** Fixes the subject the given events belong to; defaults to the subject of the command under test. */
    public CommandFixture<I> subject(String subject) {
        this.subject = subject;
        return this;
    }

    @SuppressWarnings("unchecked")
    public CommandFixture<I> stateRebuilding(StateRebuildingHandlerDefinition<I, ?>... definitions) {
        for (StateRebuildingHandlerDefinition<I, ?> definition : definitions) {
            stateRebuilding.add((StateRebuildingHandlerDefinition<I, Object>) definition);
        }
        return this;
    }

    public <E> CommandFixture<I> stateRebuilding(Class<E> eventClass, StateRebuildingHandler.FromObject<I, E> handler) {
        return stateRebuilding(new StateRebuildingHandlerDefinition<>(instanceClass, eventClass, handler));
    }

    @SuppressWarnings("unchecked")
    public CommandFixture<I> commandHandling(CommandHandlerDefinition<I, ?, ?>... definitions) {
        for (CommandHandlerDefinition<I, ?, ?> definition : definitions) {
            commandHandling.add((CommandHandlerDefinition<I, Command, Object>) definition);
        }
        return this;
    }

    public <C extends Command, R> CommandFixture<I> commandHandling(Class<C> commandClass, CommandHandler.ForInstanceAndCommand<I, C, R> handler) {
        return commandHandling(new CommandHandlerDefinition<>(instanceClass, commandClass, handler));
    }

    public <C extends Command, R> CommandFixture<I> commandHandling(Class<C> commandClass, CommandHandler.ForCommand<I, C, R> handler) {
        return commandHandling(new CommandHandlerDefinition<>(instanceClass, commandClass, handler));
    }

    /**
     * Adds handlers found in the given objects: {@link CommandHandlerDefinition} and
     * {@link StateRebuildingHandlerDefinition} instances (or collections of them) are taken as they are; any other
     * object is scanned for {@code @CommandHandling} and {@code @StateRebuilding} methods following the OpenCQRS
     * parameter conventions (aggregate instance, command or event, metadata map, subject, raw event, publisher).
     */
    @SuppressWarnings("unchecked")
    public CommandFixture<I> handlers(Object... sources) {
        for (Object source : sources) {
            if (source instanceof Collection<?> collection) {
                handlers(collection.toArray());
            } else if (source instanceof CommandHandlerDefinition<?, ?, ?> definition) {
                commandHandling((CommandHandlerDefinition<I, ?, ?>) definition);
            } else if (source instanceof StateRebuildingHandlerDefinition<?, ?> definition) {
                stateRebuilding((StateRebuildingHandlerDefinition<I, ?>) definition);
            } else {
                scan(source);
            }
        }
        return this;
    }

    public Given given(Object... events) {
        return new Given(List.of(events));
    }

    public Then when(Command command) {
        return given().when(command);
    }

    public final class Given {
        private final List<Object> events;

        private Given(List<Object> events) {
            this.events = events;
        }

        public Then when(Command command) {
            return when(command, Map.of());
        }

        public Then when(Command command, Map<String, ?> metaData) {
            Objects.requireNonNull(command, "command");
            String effectiveSubject = subject != null ? subject : command.getSubject();
            I state = replay(effectiveSubject, events);
            CommandHandlerDefinition<I, Command, Object> definition = commandHandling.stream()
                    .filter(d -> d.commandClass().isInstance(command)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No command handler registered for " + command.getClass().getName()));
            CommandEventCapturer<I> capturer = new CommandEventCapturer<>(state, effectiveSubject, stateRebuilding);
            try {
                checkSubjectCondition(command, events.isEmpty());
                Object result = invoke(definition.handler(), state, command, metaData, capturer);
                return new Then(state, capturer.getEvents(), Optional.ofNullable(result), Optional.empty(), effectiveSubject);
            } catch (RuntimeException e) {
                return new Then(state, capturer.getEvents(), Optional.empty(), Optional.of(e), effectiveSubject);
            }
        }

        private void checkSubjectCondition(Command command, boolean pristine) {
            switch (command.getSubjectCondition()) {
                case PRISTINE -> {
                    if (!pristine) {
                        throw new CommandSubjectAlreadyExistsException("Subject already exists: " + command.getSubject(), command);
                    }
                }
                case EXISTS -> {
                    if (pristine) {
                        throw new CommandSubjectDoesNotExistException("Subject does not exist: " + command.getSubject(), command);
                    }
                }
                default -> {
                }
            }
        }
    }

    public final class Then {
        private final I stateBefore;
        private final List<CapturedEvent> captured;
        private final Optional<Object> result;
        private final Optional<Throwable> error;
        private final String subject;

        private Then(I stateBefore, List<CapturedEvent> captured, Optional<Object> result, Optional<Throwable> error, String subject) {
            this.stateBefore = stateBefore;
            this.captured = List.copyOf(captured);
            this.result = result;
            this.error = error;
            this.subject = subject;
        }

        public Then thenExpectEvents(Object... expected) {
            thenExpectNoError();
            assertThat(events()).as("published events").containsExactly(expected);
            return this;
        }

        public Then thenExpectNoEvents() {
            assertThat(events()).as("published events").isEmpty();
            return this;
        }

        public Then thenExpectResult(Object expected) {
            thenExpectNoError();
            assertThat(result).as("handler result").contains(expected);
            return this;
        }

        public Then thenExpectNoResult() {
            thenExpectNoError();
            assertThat(result).as("handler result").isEmpty();
            return this;
        }

        public <T extends Throwable> T thenExpectException(Class<T> type) {
            assertThat(error).as("handler error").isPresent();
            assertThat(error.get()).isInstanceOf(type);
            return type.cast(error.get());
        }

        public Then thenExpectNoError() {
            if (error.isPresent()) {
                throw new AssertionError("Command handler failed", error.get());
            }
            return this;
        }

        /** The published event payloads, in order. */
        public List<Object> events() {
            return captured.stream().map(CapturedEvent::event).toList();
        }

        public List<CapturedEvent> capturedEvents() {
            return captured;
        }

        public Optional<Object> result() {
            return result;
        }

        public Optional<Throwable> error() {
            return error;
        }

        /** The aggregate state after the published events were applied. */
        public I state() {
            List<Object> all = new ArrayList<>();
            List<Object> published = events();
            I current = stateBefore;
            for (Object event : published) {
                current = apply(current, subject, event);
            }
            return current;
        }
    }

    private I replay(String subject, List<Object> events) {
        I current = null;
        for (Object event : events) {
            current = apply(current, subject, event);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private I apply(I current, String subject, Object event) {
        AtomicReference<I> state = new AtomicReference<>(current);
        boolean applied = false;
        for (StateRebuildingHandlerDefinition<I, Object> definition : stateRebuilding) {
            if (!definition.eventClass().isInstance(event)) {
                continue;
            }
            applied = true;
            StateRebuildingHandler<I, Object> handler = definition.handler();
            I next;
            if (handler instanceof StateRebuildingHandler.FromObject<I, Object> h) {
                next = h.on(state.get(), event);
            } else if (handler instanceof StateRebuildingHandler.FromObjectAndMetaData<I, Object> h) {
                next = h.on(state.get(), event, Map.of());
            } else if (handler instanceof StateRebuildingHandler.FromObjectAndMetaDataAndSubject<I, Object> h) {
                next = h.on(state.get(), event, Map.of(), subject);
            } else if (handler instanceof StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<I, Object> h) {
                next = h.on(state.get(), event, Map.of(), subject, null);
            } else if (handler instanceof StateRebuildingHandler.FromObjectAndRawEvent<I, Object> h) {
                next = h.on(state.get(), event, null);
            } else {
                throw new IllegalStateException("Unsupported state rebuilding handler " + handler.getClass().getName());
            }
            state.set(next);
        }
        if (!applied) {
            throw new IllegalStateException("No state rebuilding handler for " + event.getClass().getName());
        }
        return state.get();
    }

    private Object invoke(CommandHandler<I, Command, Object> handler, I state, Command command, Map<String, ?> metaData,
                          CommandEventPublisher<I> publisher) {
        if (handler instanceof CommandHandler.ForCommand<I, Command, Object> h) {
            return h.handle(command, publisher);
        }
        if (handler instanceof CommandHandler.ForInstanceAndCommand<I, Command, Object> h) {
            return h.handle(state, command, publisher);
        }
        if (handler instanceof CommandHandler.ForInstanceAndCommandAndMetaData<I, Command, Object> h) {
            return h.handle(state, command, metaData, publisher);
        }
        throw new IllegalStateException("Unsupported command handler " + handler.getClass().getName());
    }

    // --- annotation scanning -------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void scan(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(CommandHandling.class)) {
                Class<?>[] types = method.getParameterTypes();
                int commandIndex = indexOf(types, Command.class::isAssignableFrom);
                if (commandIndex < 0) {
                    throw new IllegalArgumentException(method + " has no Command parameter");
                }
                Class<Command> commandClass = (Class<Command>) types[commandIndex];
                CommandHandler.ForInstanceAndCommandAndMetaData<I, Command, Object> handler = (state, command, metaData, publisher) -> {
                    Object[] args = new Object[types.length];
                    for (int i = 0; i < types.length; i++) {
                        args[i] = i == commandIndex ? command
                                : CommandEventPublisher.class.isAssignableFrom(types[i]) ? publisher
                                : Map.class.isAssignableFrom(types[i]) ? metaData
                                : types[i].isAssignableFrom(instanceClass) ? state
                                : null;
                    }
                    return call(bean, method, args);
                };
                commandHandling.add(new CommandHandlerDefinition<>(instanceClass, commandClass, handler));
            } else if (method.isAnnotationPresent(StateRebuilding.class)) {
                Class<?>[] types = method.getParameterTypes();
                boolean onAggregate = instanceClass.isAssignableFrom(bean.getClass()) && !Modifier.isStatic(method.getModifiers());
                int eventIndex = indexOf(types, type -> !(onAggregate ? false : type.isAssignableFrom(instanceClass) && type != Object.class)
                        && !Map.class.isAssignableFrom(type) && type != String.class && type != Event.class);
                if (eventIndex < 0) {
                    throw new IllegalArgumentException(method + " has no event parameter");
                }
                Class<Object> eventClass = (Class<Object>) types[eventIndex];
                StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<I, Object> handler = (state, event, metaData, subject, raw) -> {
                    Object[] args = new Object[types.length];
                    for (int i = 0; i < types.length; i++) {
                        args[i] = i == eventIndex ? event
                                : Map.class.isAssignableFrom(types[i]) ? metaData
                                : types[i] == String.class ? subject
                                : types[i] == Event.class ? raw
                                : state;
                    }
                    Object target = onAggregate ? state : bean;
                    if (onAggregate && target == null) {
                        throw new IllegalStateException(method + " is an instance method but there is no state yet");
                    }
                    return (I) call(target, method, args);
                };
                stateRebuilding.add(new StateRebuildingHandlerDefinition<>(instanceClass, eventClass, handler));
            }
        }
    }

    private static int indexOf(Class<?>[] types, java.util.function.Predicate<Class<?>> test) {
        for (int i = 0; i < types.length; i++) {
            if (test.test(types[i])) {
                return i;
            }
        }
        return -1;
    }

    private static Object call(Object target, Method method, Object[] args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
