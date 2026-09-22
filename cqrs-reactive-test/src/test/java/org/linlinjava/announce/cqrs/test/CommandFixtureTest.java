package org.linlinjava.announce.cqrs.test;

import com.opencqrs.framework.command.CommandEventPublisher;
import com.opencqrs.framework.command.CommandHandling;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import com.opencqrs.framework.command.StateRebuilding;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.sample.Listing;
import org.linlinjava.announce.cqrs.sample.ListingCommands.ChangeListingPrice;
import org.linlinjava.announce.cqrs.sample.ListingCommands.CloseListing;
import org.linlinjava.announce.cqrs.sample.ListingCommands.CreateListing;
import org.linlinjava.announce.cqrs.sample.ListingClosed;
import org.linlinjava.announce.cqrs.sample.ListingCreated;
import org.linlinjava.announce.cqrs.sample.ListingHandlers;
import org.linlinjava.announce.cqrs.sample.ListingPriceChanged;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommandFixtureTest {

    private CommandFixture<Listing> fixture() {
        return CommandFixture.forAggregate(Listing.class).handlers(ListingHandlers.definitions());
    }

    @Test
    void createOnPristineSubjectPublishesCreated() {
        CommandFixture<Listing>.Then then = fixture()
                .when(new CreateListing("1", "Bike", 12000))
                .thenExpectEvents(new ListingCreated("Bike", 12000))
                .thenExpectResult("1");
        assertThat(then.state()).isEqualTo(new Listing("1", "Bike", 12000, Listing.Status.OPEN));
        assertThat(then.capturedEvents().get(0).subject()).isEqualTo("/listing/1");
    }

    @Test
    void createOnExistingSubjectFails() {
        fixture().given(new ListingCreated("Bike", 12000))
                .when(new CreateListing("1", "Bike", 12000))
                .thenExpectException(CommandSubjectAlreadyExistsException.class);
    }

    @Test
    void changePriceRebuildsStateFromGivenEvents() {
        fixture().given(new ListingCreated("Bike", 12000), new ListingPriceChanged(11000))
                .when(new ChangeListingPrice("1", 9900))
                .thenExpectEvents(new ListingPriceChanged(9900))
                .thenExpectResult(9900L);

        fixture().given(new ListingCreated("Bike", 12000))
                .when(new ChangeListingPrice("1", 12000))
                .thenExpectNoEvents();
    }

    @Test
    void businessRulesAndSubjectConditions() {
        CommandRejectedException rejected = fixture().given(new ListingCreated("Bike", 12000), new ListingClosed())
                .when(new ChangeListingPrice("1", 100))
                .thenExpectException(CommandRejectedException.class);
        assertThat(rejected.getCode()).isEqualTo("listing-closed");

        fixture().when(new CloseListing("1")).thenExpectException(CommandSubjectDoesNotExistException.class);
        fixture().given(new ListingCreated("Bike", 12000)).when(new CloseListing("1")).thenExpectEvents(new ListingClosed()).thenExpectNoResult();
    }

    // --- annotated handler style ------------------------------------------------------------------------

    record Counter(int value) {
    }

    record Incremented(int by) {
    }

    record Increment(String id, int by) implements com.opencqrs.framework.command.Command {
        @Override
        public String getSubject() {
            return "/counter/" + id;
        }
    }

    static class CounterHandlers {
        @StateRebuilding
        public Counter on(Counter counter, Incremented event) {
            return new Counter((counter == null ? 0 : counter.value()) + event.by());
        }

        @CommandHandling
        public int handle(Counter counter, Increment command, Map<String, ?> metaData, CommandEventPublisher<Counter> publisher) {
            if (command.by() <= 0) {
                throw new IllegalArgumentException("by must be positive");
            }
            publisher.publish(new Incremented(command.by()));
            return (counter == null ? 0 : counter.value()) + command.by();
        }
    }

    @Test
    void scansAnnotatedHandlerMethods() {
        CommandFixture<Counter>.Then then = CommandFixture.forAggregate(Counter.class).handlers(new CounterHandlers())
                .given(new Incremented(2))
                .when(new Increment("c", 3))
                .thenExpectEvents(new Incremented(3))
                .thenExpectResult(5);
        assertThat(then.state()).isEqualTo(new Counter(5));

        CommandFixture.forAggregate(Counter.class).handlers(new CounterHandlers())
                .when(new Increment("c", 0)).thenExpectException(IllegalArgumentException.class);
    }
}
