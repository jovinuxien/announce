package org.linlinjava.announce.goods.infrastructure.events;

import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEvent;
import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events as Spring application events. In-process listeners (search index, marketing) subscribe
 * with {@code @EventListener}; a Kafka bridge can be added as another listener without touching the domain.
 */
@Component
public class SpringDomainEventPublisher implements LitemallDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringDomainEventPublisher.class);
    private final ApplicationEventPublisher spring;

    public SpringDomainEventPublisher(ApplicationEventPublisher spring) {
        this.spring = spring;
    }

    @Override
    public void publish(LitemallDomainEvent event) {
        log.info("domain event {}", event);
        spring.publishEvent(event);
    }
}
