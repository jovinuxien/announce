package org.linlinjava.announce.cqrs.sample;

import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.sample.ListingCommands.ChangeListingPrice;
import org.linlinjava.announce.cqrs.sample.ListingCommands.CloseListing;
import org.linlinjava.announce.cqrs.sample.ListingCommands.CreateListing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Command handling and state rebuilding of the listing aggregate as explicit OpenCQRS definitions.
 * The {@code definitions()} list is what tests hand to {@code CommandFixture.handlers(...)}.
 */
@Configuration(proxyBeanMethods = false)
public class ListingHandlers {

    public static final StateRebuildingHandlerDefinition<Listing, ListingCreated> ON_CREATED = new StateRebuildingHandlerDefinition<>(
            Listing.class, ListingCreated.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<Listing, ListingCreated>) (listing, event, metaData, subject) ->
                    new Listing(org.linlinjava.announce.cqrs.command.Subjects.idOf(subject), event.title(), event.priceCents(), Listing.Status.OPEN));

    public static final StateRebuildingHandlerDefinition<Listing, ListingPriceChanged> ON_PRICE_CHANGED = new StateRebuildingHandlerDefinition<>(
            Listing.class, ListingPriceChanged.class,
            (StateRebuildingHandler.FromObject<Listing, ListingPriceChanged>) (listing, event) ->
                    new Listing(listing.id(), listing.title(), event.priceCents(), listing.status()));

    public static final StateRebuildingHandlerDefinition<Listing, ListingClosed> ON_CLOSED = new StateRebuildingHandlerDefinition<>(
            Listing.class, ListingClosed.class,
            (StateRebuildingHandler.FromObject<Listing, ListingClosed>) (listing, event) ->
                    new Listing(listing.id(), listing.title(), listing.priceCents(), Listing.Status.CLOSED));

    public static final CommandHandlerDefinition<Listing, CreateListing, String> CREATE = new CommandHandlerDefinition<>(
            Listing.class, CreateListing.class,
            (CommandHandler.ForCommand<Listing, CreateListing, String>) (command, publisher) -> {
                if (command.title() == null || command.title().isBlank()) {
                    throw new IllegalArgumentException("title must not be blank");
                }
                requirePositivePrice(command.priceCents());
                publisher.publish(new ListingCreated(command.title(), command.priceCents()));
                return command.id();
            });

    public static final CommandHandlerDefinition<Listing, ChangeListingPrice, Long> CHANGE_PRICE = new CommandHandlerDefinition<>(
            Listing.class, ChangeListingPrice.class,
            (CommandHandler.ForInstanceAndCommand<Listing, ChangeListingPrice, Long>) (listing, command, publisher) -> {
                requirePositivePrice(command.priceCents());
                if (listing.status() == Listing.Status.CLOSED) {
                    throw new CommandRejectedException(HttpStatus.CONFLICT, "listing-closed", "a closed listing cannot change price");
                }
                if (listing.priceCents() != command.priceCents()) {
                    publisher.publish(new ListingPriceChanged(command.priceCents()));
                }
                return command.priceCents();
            });

    public static final CommandHandlerDefinition<Listing, CloseListing, Void> CLOSE = new CommandHandlerDefinition<>(
            Listing.class, CloseListing.class,
            (CommandHandler.ForInstanceAndCommand<Listing, CloseListing, Void>) (listing, command, publisher) -> {
                if (listing.status() == Listing.Status.CLOSED) {
                    throw new CommandRejectedException(HttpStatus.CONFLICT, "listing-closed", "listing is already closed");
                }
                publisher.publish(new ListingClosed());
                return null;
            });

    public static List<Object> definitions() {
        return List.of(ON_CREATED, ON_PRICE_CHANGED, ON_CLOSED, CREATE, CHANGE_PRICE, CLOSE);
    }

    private static void requirePositivePrice(long priceCents) {
        if (priceCents <= 0) {
            throw new CommandRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "price-not-positive", "price must be positive");
        }
    }

    @Bean
    StateRebuildingHandlerDefinition<Listing, ListingCreated> onCreated() {
        return ON_CREATED;
    }

    @Bean
    StateRebuildingHandlerDefinition<Listing, ListingPriceChanged> onPriceChanged() {
        return ON_PRICE_CHANGED;
    }

    @Bean
    StateRebuildingHandlerDefinition<Listing, ListingClosed> onClosed() {
        return ON_CLOSED;
    }

    @Bean
    CommandHandlerDefinition<Listing, CreateListing, String> create() {
        return CREATE;
    }

    @Bean
    CommandHandlerDefinition<Listing, ChangeListingPrice, Long> changePrice() {
        return CHANGE_PRICE;
    }

    @Bean
    CommandHandlerDefinition<Listing, CloseListing, Void> close() {
        return CLOSE;
    }
}
