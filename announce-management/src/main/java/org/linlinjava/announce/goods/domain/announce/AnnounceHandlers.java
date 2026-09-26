package org.linlinjava.announce.goods.domain.announce;

import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.EditAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ExpireAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.MarkSold;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ModerateExpire;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ModerateWithdraw;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.PostAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.PublishAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.WithdrawAnnounce;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceEdited;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePosted;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceSold;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceWithdrawn;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Command handling and state rebuilding of the Announce aggregate as explicit OpenCQRS definitions (the style of
 * the cqrs-reactive sample). Every command handler checks {@link AnnounceRules}, publishes one event and returns
 * the resulting {@link Announce} state, so callers can answer without waiting for the read model.
 * {@link #definitions(Clock)} is what tests hand to {@code CommandFixture.handlers(...)}.
 */
@Configuration(proxyBeanMethods = false)
public class AnnounceHandlers {

    // ------------------------------------------------------------------ state rebuilding

    public static final StateRebuildingHandlerDefinition<Announce, AnnouncePosted> ON_POSTED = new StateRebuildingHandlerDefinition<>(
            Announce.class, AnnouncePosted.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<Announce, AnnouncePosted>) (state, event, metaData, subject) ->
                    Announce.apply(Subjects.idOf(subject), event));

    public static final StateRebuildingHandlerDefinition<Announce, AnnounceEdited> ON_EDITED = new StateRebuildingHandlerDefinition<>(
            Announce.class, AnnounceEdited.class, (StateRebuildingHandler.FromObject<Announce, AnnounceEdited>) Announce::apply);

    public static final StateRebuildingHandlerDefinition<Announce, AnnouncePublished> ON_PUBLISHED = new StateRebuildingHandlerDefinition<>(
            Announce.class, AnnouncePublished.class, (StateRebuildingHandler.FromObject<Announce, AnnouncePublished>) Announce::apply);

    public static final StateRebuildingHandlerDefinition<Announce, AnnounceWithdrawn> ON_WITHDRAWN = new StateRebuildingHandlerDefinition<>(
            Announce.class, AnnounceWithdrawn.class, (StateRebuildingHandler.FromObject<Announce, AnnounceWithdrawn>) Announce::apply);

    public static final StateRebuildingHandlerDefinition<Announce, AnnounceSold> ON_SOLD = new StateRebuildingHandlerDefinition<>(
            Announce.class, AnnounceSold.class, (StateRebuildingHandler.FromObject<Announce, AnnounceSold>) Announce::apply);

    public static final StateRebuildingHandlerDefinition<Announce, AnnounceExpired> ON_EXPIRED = new StateRebuildingHandlerDefinition<>(
            Announce.class, AnnounceExpired.class, (StateRebuildingHandler.FromObject<Announce, AnnounceExpired>) Announce::apply);

    // ------------------------------------------------------------------ command handling

    public static CommandHandlerDefinition<Announce, PostAnnounce, Announce> post(Clock clock) {
        return new CommandHandlerDefinition<>(Announce.class, PostAnnounce.class,
                (CommandHandler.ForCommand<Announce, PostAnnounce, Announce>) (command, publisher) -> {
                    if (command.sellerId() == null) {
                        throw new IllegalArgumentException("The seller is required");
                    }
                    if (command.content() == null) {
                        throw new IllegalArgumentException("The announce content is required");
                    }
                    AnnounceRules.checkDetails(command.details());
                    AnnouncePosted event = new AnnouncePosted(command.sellerId(), command.content(), command.details(), now(clock));
                    publisher.publish(event);
                    return Announce.apply(command.id(), event);
                });
    }

    public static CommandHandlerDefinition<Announce, EditAnnounce, Announce> edit(Clock clock) {
        return new CommandHandlerDefinition<>(Announce.class, EditAnnounce.class,
                (CommandHandler.ForInstanceAndCommand<Announce, EditAnnounce, Announce>) (state, command, publisher) -> {
                    AnnounceRules.checkOwner(state, command.sellerId());
                    if (command.content() == null) {
                        throw new IllegalArgumentException("The announce content is required");
                    }
                    AnnounceRules.checkEdit(state, command.details());
                    AnnounceEdited event = new AnnounceEdited(command.content(), command.details(), now(clock));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    public static CommandHandlerDefinition<Announce, PublishAnnounce, Announce> publish(Clock clock) {
        return new CommandHandlerDefinition<>(Announce.class, PublishAnnounce.class,
                (CommandHandler.ForInstanceAndCommand<Announce, PublishAnnounce, Announce>) (state, command, publisher) -> {
                    AnnounceRules.checkOwner(state, command.sellerId());
                    Duration validity = command.validity() == null ? Announce.DEFAULT_VALIDITY : command.validity();
                    LocalDateTime now = now(clock);
                    AnnounceRules.checkPublish(state, validity, now);
                    AnnouncePublished event = new AnnouncePublished(now, now.plus(validity));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    public static CommandHandlerDefinition<Announce, WithdrawAnnounce, Announce> withdraw(Clock clock) {
        return new CommandHandlerDefinition<>(Announce.class, WithdrawAnnounce.class,
                (CommandHandler.ForInstanceAndCommand<Announce, WithdrawAnnounce, Announce>) (state, command, publisher) -> {
                    AnnounceRules.checkOwner(state, command.sellerId());
                    AnnounceRules.checkWithdraw(state);
                    AnnounceWithdrawn event = new AnnounceWithdrawn(now(clock));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    public static CommandHandlerDefinition<Announce, MarkSold, Announce> markSold(Clock clock) {
        return new CommandHandlerDefinition<>(Announce.class, MarkSold.class,
                (CommandHandler.ForInstanceAndCommand<Announce, MarkSold, Announce>) (state, command, publisher) -> {
                    AnnounceRules.checkOwner(state, command.sellerId());
                    AnnounceRules.checkMarkSold(state);
                    AnnounceSold event = new AnnounceSold(now(clock));
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    /** Idempotent: an announce that is not due stays as it is and no event is written. */
    public static CommandHandlerDefinition<Announce, ExpireAnnounce, Announce> expire(Clock clock) {
        return new CommandHandlerDefinition<>(Announce.class, ExpireAnnounce.class,
                (CommandHandler.ForInstanceAndCommand<Announce, ExpireAnnounce, Announce>) (state, command, publisher) -> {
                    LocalDateTime now = now(clock);
                    if (!AnnounceRules.isDueToExpire(state, now)) {
                        return state;
                    }
                    AnnounceExpired event = new AnnounceExpired(now);
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    /** Staff withdrawal: no owner check; the moderator and the reason travel on the event. */
    public static CommandHandlerDefinition<Announce, ModerateWithdraw, Announce> moderateWithdraw(Clock clock) {
        return new CommandHandlerDefinition<>(Announce.class, ModerateWithdraw.class,
                (CommandHandler.ForInstanceAndCommand<Announce, ModerateWithdraw, Announce>) (state, command, publisher) -> {
                    AnnounceRules.checkModeration(state, command.moderatorId(), command.reason());
                    AnnounceWithdrawn event = new AnnounceWithdrawn(now(clock), command.moderatorId(), command.reason().trim());
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    /** Staff expiry: ends a published announce now, whatever its date. */
    public static CommandHandlerDefinition<Announce, ModerateExpire, Announce> moderateExpire(Clock clock) {
        return new CommandHandlerDefinition<>(Announce.class, ModerateExpire.class,
                (CommandHandler.ForInstanceAndCommand<Announce, ModerateExpire, Announce>) (state, command, publisher) -> {
                    AnnounceRules.checkModeration(state, command.moderatorId(), command.reason());
                    AnnounceExpired event = new AnnounceExpired(now(clock), command.moderatorId(), command.reason().trim());
                    publisher.publish(event);
                    return state.apply(event);
                });
    }

    /** All definitions, for {@code CommandFixture.handlers(...)}. */
    public static List<Object> definitions(Clock clock) {
        return List.of(ON_POSTED, ON_EDITED, ON_PUBLISHED, ON_WITHDRAWN, ON_SOLD, ON_EXPIRED,
                post(clock), edit(clock), publish(clock), withdraw(clock), markSold(clock), expire(clock),
                moderateWithdraw(clock), moderateExpire(clock));
    }

    private static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock);
    }

    // ------------------------------------------------------------------ beans

    @Bean StateRebuildingHandlerDefinition<Announce, AnnouncePosted> announceOnPosted() { return ON_POSTED; }
    @Bean StateRebuildingHandlerDefinition<Announce, AnnounceEdited> announceOnEdited() { return ON_EDITED; }
    @Bean StateRebuildingHandlerDefinition<Announce, AnnouncePublished> announceOnPublished() { return ON_PUBLISHED; }
    @Bean StateRebuildingHandlerDefinition<Announce, AnnounceWithdrawn> announceOnWithdrawn() { return ON_WITHDRAWN; }
    @Bean StateRebuildingHandlerDefinition<Announce, AnnounceSold> announceOnSold() { return ON_SOLD; }
    @Bean StateRebuildingHandlerDefinition<Announce, AnnounceExpired> announceOnExpired() { return ON_EXPIRED; }
    @Bean CommandHandlerDefinition<Announce, PostAnnounce, Announce> announcePost(Clock clock) { return post(clock); }
    @Bean CommandHandlerDefinition<Announce, EditAnnounce, Announce> announceEdit(Clock clock) { return edit(clock); }
    @Bean CommandHandlerDefinition<Announce, PublishAnnounce, Announce> announcePublish(Clock clock) { return publish(clock); }
    @Bean CommandHandlerDefinition<Announce, WithdrawAnnounce, Announce> announceWithdraw(Clock clock) { return withdraw(clock); }
    @Bean CommandHandlerDefinition<Announce, MarkSold, Announce> announceMarkSold(Clock clock) { return markSold(clock); }
    @Bean CommandHandlerDefinition<Announce, ExpireAnnounce, Announce> announceExpire(Clock clock) { return expire(clock); }
    @Bean CommandHandlerDefinition<Announce, ModerateWithdraw, Announce> announceModerateWithdraw(Clock clock) { return moderateWithdraw(clock); }
    @Bean CommandHandlerDefinition<Announce, ModerateExpire, Announce> announceModerateExpire(Clock clock) { return moderateExpire(clock); }
}
