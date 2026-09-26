package org.linlinjava.announce.auction.infrastructure.messaging;

import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.linlinjava.announce.auction.application.AuctionService;
import org.linlinjava.announce.auction.domain.AuctionCommands.OpenAuction;
import org.linlinjava.announce.auction.domain.events.AuctionCancelled;
import org.linlinjava.announce.auction.domain.events.AuctionClosedUnsold;
import org.linlinjava.announce.auction.domain.events.AuctionClosedWon;
import org.linlinjava.announce.auction.domain.events.AuctionOpened;
import org.linlinjava.announce.auction.domain.events.AuctionSnagged;
import org.linlinjava.announce.auction.domain.events.BidPlaced;
import org.linlinjava.announce.auction.infrastructure.config.AuctionProperties;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelay;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelays;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscriber;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.db.dao.reactive.AnnounceAuctionMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceAuctionSellerMapper;
import org.linlinjava.announce.db.domain.AnnounceAuctionSeller;
import org.linlinjava.announce.goods.api.AnnounceExpiredV1;
import org.linlinjava.announce.goods.api.AnnouncePublishedV1;
import org.linlinjava.announce.goods.api.AnnounceWithdrawnV1;
import org.linlinjava.announce.order.api.AuctionCancelledV1;
import org.linlinjava.announce.order.api.AuctionClosedUnsoldV1;
import org.linlinjava.announce.order.api.AuctionClosedWonV1;
import org.linlinjava.announce.order.api.AuctionOpenedV1;
import org.linlinjava.announce.order.api.AuctionSnaggedV1;
import org.linlinjava.announce.order.api.BidPlacedV1;
import org.linlinjava.announce.order.api.SellerAccountUpdatedV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * The auction context over Kafka. <b>Out:</b> relay {@code auction-public} on {@code auction-out-0} (topic
 * {@code auction.v1}); the relay needs the seller on every record, so the closing and cancel mappings read it from
 * the read model. <b>In:</b> {@code auctionAnnounceEvents} opens an auction for every published AUCTION announce
 * (defaults from {@code auction.*} when the announce carries no auction settings) and cancels it when the announce
 * is withdrawn or expires; {@code auctionPaymentEvents} keeps the context's copy of who can be paid.
 */
@Configuration(proxyBeanMethods = false)
public class AuctionMessaging {

    public static final String RELAY_GROUP = "auction-public";
    public static final String OUT_BINDING = "auction-out-0";

    private static final Logger log = LoggerFactory.getLogger(AuctionMessaging.class);

    @Bean
    PublicEventRelay auctionPublicEvents(PublicEventRelays relays, AnnounceAuctionMapper auctions) {
        return relays.forGroup(RELAY_GROUP).binding(OUT_BINDING)
                .map(AuctionOpened.class, AuctionOpenedV1.class, (e, env) -> new AuctionOpenedV1(id(env), e.sellerId(), e.initialPriceCents(),
                        e.reservePriceCents(), e.snagItPriceCents(), e.incrementCents(), e.currency(), e.startsAt(), e.endsAt()))
                .map(BidPlaced.class, BidPlacedV1.class, (e, env) -> new BidPlacedV1(id(env), e.bidId(), e.bidderId(), e.amountCents(),
                        seller(auctions, env).currency(), e.at(), e.endsAt()))
                .map(AuctionClosedWon.class, AuctionClosedWonV1.class, (e, env) -> {
                    SellerAndCurrency s = seller(auctions, env);
                    return new AuctionClosedWonV1(id(env), s.sellerId(), e.winnerId(), e.winningBidId(), e.priceCents(), s.currency(), e.at());
                })
                .map(AuctionClosedUnsold.class, AuctionClosedUnsoldV1.class, (e, env) ->
                        new AuctionClosedUnsoldV1(id(env), seller(auctions, env).sellerId(), e.reason(), e.at()))
                .map(AuctionSnagged.class, AuctionSnaggedV1.class, (e, env) -> {
                    SellerAndCurrency s = seller(auctions, env);
                    return new AuctionSnaggedV1(id(env), s.sellerId(), e.buyerId(), e.priceCents(), s.currency(), e.at());
                })
                .map(AuctionCancelled.class, AuctionCancelledV1.class, (e, env) ->
                        new AuctionCancelledV1(id(env), seller(auctions, env).sellerId(), e.reason(), e.at()))
                .build();
    }

    @Bean
    Consumer<Message<byte[]>> auctionAnnounceEvents(PublicEventSubscribers subscribers, AuctionService auctions, AuctionProperties props) {
        return subscribers.forTopic("announce")
                .on(AnnouncePublishedV1.class, (e, headers) -> {
                    if (!"AUCTION".equals(e.saleType())) {
                        return Mono.empty();
                    }
                    LocalDateTime start = e.publishedAt();
                    LocalDateTime end = start.plus(props.getDefaultDuration());
                    if (e.expiresAt() != null && e.expiresAt().isBefore(end)) {
                        end = e.expiresAt();
                    }
                    OpenAuction open = new OpenAuction(e.announceId(), e.sellerId(), e.title(), e.mainPicture(), e.currency(), e.priceCents(),
                            null, null, props.getDefaultIncrementCents(), start, end, props.getDefaultAutoExtendSec());
                    return auctions.open(open, commandId("open", headers)).then()
                            .onErrorResume(CommandSubjectAlreadyExistsException.class, x -> {
                                log.debug("auction {} already open", e.announceId());
                                return Mono.empty();
                            })
                            .onErrorResume(CommandRejectedException.class, x -> {
                                log.warn("announce {} published as AUCTION but no auction opened: {}", e.announceId(), x.getMessage());
                                return Mono.empty();
                            });
                })
                .on(AnnounceWithdrawnV1.class, (e, headers) -> cancel(auctions, e.announceId(), "announce withdrawn", headers))
                .on(AnnounceExpiredV1.class, (e, headers) -> cancel(auctions, e.announceId(), "announce expired", headers))
                .build();
    }

    @Bean
    Consumer<Message<byte[]>> auctionPaymentEvents(PublicEventSubscribers subscribers, AnnounceAuctionSellerMapper sellers) {
        return subscribers.forTopic("payment")
                .on(SellerAccountUpdatedV1.class, (e, headers) -> {
                    AnnounceAuctionSeller row = new AnnounceAuctionSeller();
                    row.setId(e.userId());
                    row.setPayoutsEnabled(e.payoutsEnabled());
                    row.setAccountStatus(e.status());
                    row.setLastEventId(headers.eventId().orElse(null));
                    row.setUpdateTime(e.at());
                    return sellers.selectByPrimaryKey(e.userId()).hasElement()
                            .flatMap(exists -> exists ? sellers.updateByPrimaryKeySelective(row) : sellers.insertSelective(row)).then();
                })
                .build();
    }

    private static Mono<Void> cancel(AuctionService auctions, String announceId, String reason, PublicEventSubscriber.Headers headers) {
        return auctions.cancelBySystem(announceId, reason, commandId("cancel", headers)).then()
                .onErrorResume(CommandSubjectDoesNotExistException.class, x -> Mono.empty());   // never an auction
    }

    private static String commandId(String action, PublicEventSubscriber.Headers headers) {
        return headers.eventId().map(id -> action + ":" + id).orElse(null);
    }

    private static String id(org.linlinjava.announce.cqrs.readside.EventEnvelope env) {
        return Subjects.idOf(env.subject());
    }

    private record SellerAndCurrency(Integer sellerId, String currency) {}

    /** The relay maps synchronously on the processor thread (never the event loop), so a read-model lookup may block here. */
    private static SellerAndCurrency seller(AnnounceAuctionMapper auctions, org.linlinjava.announce.cqrs.readside.EventEnvelope env) {
        var row = auctions.selectByPrimaryKey(id(env)).block();
        if (row == null) {
            throw new IllegalStateException("auction " + id(env) + " not in the read model yet");   // the framework retries
        }
        return new SellerAndCurrency(row.getSellerId(), row.getCurrency());
    }
}
