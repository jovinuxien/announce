package org.linlinjava.announce.cqrs.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lowest-priority defaults for the OpenCQRS properties the framework relies on. A service overrides any of
 * them in its own configuration:
 * <ul>
 *   <li>command metadata {@code correlationId}, {@code causationId} and {@code commandId} propagate to the
 *       events a handler publishes (kept when the handler already set them);</li>
 *   <li>event handling processors checkpoint through {@code r2dbcProgressTracker} unless
 *       {@code cqrs.readside.progress.tracker=in-memory}.</li>
 * </ul>
 */
public class CqrsDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String PROPERTY_SOURCE = "cqrsReactiveDefaults";
    public static final String R2DBC_TRACKER_BEAN = "r2dbcProgressTracker";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("opencqrs.metadata.propagation.mode", "KEEP_IF_PRESENT");
        defaults.put("opencqrs.metadata.propagation.keys",
                MetaDataKeys.CORRELATION_ID + "," + MetaDataKeys.CAUSATION_ID + "," + MetaDataKeys.COMMAND_ID);
        String tracker = environment.getProperty("cqrs.readside.progress.tracker", "r2dbc");
        if (!"in-memory".equalsIgnoreCase(tracker) && !"in_memory".equalsIgnoreCase(tracker)) {
            defaults.put("opencqrs.event-handling.standard.progress.tracker-ref", R2DBC_TRACKER_BEAN);
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE, defaults));
    }
}
