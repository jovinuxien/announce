package org.linlinjava.announce.messaging.infrastructure.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every {@code messaging.*} timer publishes p50, p95 and p99 computed in the process, so that a plain scrape of
 * {@code /actuator/prometheus} (what the load driver reads) shows them as {@code quantile} samples. Set here
 * rather than in {@code application.yml} so that a copied configuration cannot lose it.
 *
 * <p>No percentile histogram: with the Prometheus client Micrometer uses now, a meter that has histogram buckets
 * publishes no quantile samples, so the two are exclusive. When a Prometheus server scrapes the service, set
 * {@code management.metrics.distribution.percentiles-histogram.messaging=true} and read p95 with
 * {@code histogram_quantile} instead.
 */
@Configuration(proxyBeanMethods = false)
public class MessagingMetricsConfig {

    static final String PREFIX = "messaging.";
    static final double[] PERCENTILES = {0.5, 0.95, 0.99};

    @Bean
    MeterFilter messagingTimersPublishPercentiles() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getType() == Meter.Type.TIMER && id.getName().startsWith(PREFIX)) {
                    return DistributionStatisticConfig.builder().percentiles(PERCENTILES).build().merge(config);
                }
                return config;
            }
        };
    }
}
