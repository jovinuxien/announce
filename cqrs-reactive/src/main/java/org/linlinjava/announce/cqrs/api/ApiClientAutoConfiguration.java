package org.linlinjava.announce.cqrs.api;

import org.linlinjava.announce.cqrs.CqrsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Service clients: {@link HttpExchangeClients} over the auto-configured {@link WebClient.Builder}, LoadBalancer
 * and Resilience4j.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration",
        "org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerBeanPostProcessorAutoConfiguration",
        "org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerClientAutoConfiguration",
        "org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration"})
@ConditionalOnClass({WebClient.class, ReactiveCircuitBreakerFactory.class})
@EnableConfigurationProperties(CqrsProperties.class)
public class ApiClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HttpExchangeClients httpExchangeClients(ObjectProvider<WebClient.Builder> webClientBuilder,
                                                   ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancerFilter,
                                                   ObjectProvider<ReactiveCircuitBreakerFactory<?, ?>> circuitBreakerFactory,
                                                   CqrsProperties properties) {
        return new HttpExchangeClients(webClientBuilder.getIfAvailable(WebClient::builder),
                loadBalancerFilter.getIfAvailable(), circuitBreakerFactory.getIfAvailable(), properties.getApi());
    }
}
