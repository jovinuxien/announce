package org.linlinjava.announce.goods.domain.model.events;

/** Outbound port: hands domain events to whatever transport the infrastructure provides (Spring events, Kafka...). */
public interface LitemallDomainEventPublisher {

    void publish(LitemallDomainEvent event);
}
