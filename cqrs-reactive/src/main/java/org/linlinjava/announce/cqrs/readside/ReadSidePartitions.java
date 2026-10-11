package org.linlinjava.announce.cqrs.readside;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads how many partitions each OpenCQRS event handling group is configured with
 * ({@code opencqrs.event-handling.standard.life-cycle.partitions} and the per-group override), without
 * depending on OpenCQRS's own properties bean, which does not exist yet when the progress tracker is built.
 */
public final class ReadSidePartitions {

    private ReadSidePartitions() {
    }

    /** Group id to configured partitions, for every group listed under {@code opencqrs.event-handling.groups}. */
    public static Map<String, Long> configured(Environment environment) {
        Binder binder = Binder.get(environment);
        long standard = binder.bind("opencqrs.event-handling.standard.life-cycle.partitions", Long.class).orElse(1L);
        Map<String, GroupSettings> groups = binder.bind("opencqrs.event-handling.groups", Bindable.mapOf(String.class, GroupSettings.class))
                .orElse(Map.of());
        Map<String, Long> partitions = new LinkedHashMap<>();
        groups.forEach((group, settings) -> partitions.put(group,
                settings.getLifeCycle().getPartitions() == null ? standard : settings.getLifeCycle().getPartitions()));
        return partitions;
    }

    /** The slice of a group's settings this class cares about; other keys of the group are ignored by the binder. */
    public static class GroupSettings {
        private LifeCycle lifeCycle = new LifeCycle();

        public LifeCycle getLifeCycle() {
            return lifeCycle;
        }

        public void setLifeCycle(LifeCycle lifeCycle) {
            this.lifeCycle = lifeCycle;
        }

        public static class LifeCycle {
            private Long partitions;

            public Long getPartitions() {
                return partitions;
            }

            public void setPartitions(Long partitions) {
                this.partitions = partitions;
            }
        }
    }
}
