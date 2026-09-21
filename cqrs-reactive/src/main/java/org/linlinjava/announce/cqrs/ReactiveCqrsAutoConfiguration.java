package org.linlinjava.announce.cqrs;

import com.opencqrs.framework.command.CommandRouter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers a {@link ReactiveCommandRouter} whenever OpenCQRS has provided a {@link CommandRouter}.
 */
@AutoConfiguration(afterName = "com.opencqrs.framework.command.CommandRouterAutoConfiguration")
@ConditionalOnBean(CommandRouter.class)
public class ReactiveCqrsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveCommandRouter reactiveCommandRouter(CommandRouter commandRouter) {
        return new ReactiveCommandRouter(commandRouter);
    }
}
