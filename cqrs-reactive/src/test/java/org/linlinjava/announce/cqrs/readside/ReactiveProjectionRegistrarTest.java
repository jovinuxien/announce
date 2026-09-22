package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.framework.eventhandler.EventHandlerDefinition;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveProjectionRegistrarTest {

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        static ReactiveProjectionRegistrar registrar() {
            return new ReactiveProjectionRegistrar();
        }

        @Bean(name = ReactiveProjectionSupport.BEAN_NAME)
        ReactiveProjectionSupport support() {
            return new ReactiveProjectionSupport(CqrsTracing.disabled(), Duration.ofSeconds(1));
        }

        @Bean
        ReactiveProjection summary() {
            return new ReactiveProjection() {
                @Override
                public String group() {
                    return "summary";
                }

                @Override
                public void configure(ProjectionHandlers handlers) {
                    handlers.on(String.class, event -> reactor.core.publisher.Mono.empty());
                }
            };
        }
    }

    @Test
    void registersOneEventHandlerDefinitionPerProjectionBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            Map<String, EventHandlerDefinition> definitions = context.getBeansOfType(EventHandlerDefinition.class);

            assertThat(definitions).containsOnlyKeys("summary" + ReactiveProjectionRegistrar.SUFFIX);
            EventHandlerDefinition<?> definition = definitions.values().iterator().next();
            assertThat(definition.group()).isEqualTo("summary");
            assertThat(definition.handler()).isInstanceOf(ReactiveProjectionAdapter.class);
        }
    }
}
