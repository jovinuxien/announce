package org.linlinjava.announce.goods.interfaces.rest;

import org.linlinjava.announce.cqrs.security.ServiceTokenWebFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Resolves the caller once per request and hands it to the routes through the Reactor context, so a controller
 * method can implement a client-legal {@code @HttpExchange} descriptor (no {@code HttpHeaders} parameter) and
 * still know who is asking: {@link Callers#member()}, {@link Callers#viewer()} and {@link Callers#staff(String)}
 * read what this filter wrote. Runs after the framework's {@link ServiceTokenWebFilter}, which is what makes the
 * identity headers trustworthy. A malformed identity is not refused here but when a route reads it, so the answer
 * is the same problem detail the header-based overloads produce.
 */
@Component
@Order(CallerContextFilter.ORDER)
public class CallerContextFilter implements WebFilter {

    /** Right after the service token check. */
    public static final int ORDER = Integer.MIN_VALUE + 300;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Callers.Resolution resolution = Callers.Resolution.of(exchange.getRequest().getHeaders());
        return chain.filter(exchange).contextWrite(context -> context.put(Callers.CONTEXT_KEY, resolution));
    }
}
