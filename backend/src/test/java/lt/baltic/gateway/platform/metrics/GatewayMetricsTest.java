package lt.baltic.gateway.platform.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import lt.baltic.gateway.platform.money.Money;
import org.junit.jupiter.api.Test;

class GatewayMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GatewayMetrics metrics = new GatewayMetrics(registry);

    private double counter(String name, String... tags) {
        var counter = registry.find(name).tags(tags).counter();
        assertNotNull(counter, name + " with " + String.join(",", tags) + " must be registered");
        return counter.count();
    }

    @Test void seriesUsedByDashboardsExistAtZeroBeforeAnyEvent() {
        assertEquals(0.0, counter("payments", "outcome", "completed", "currency", "EUR", "reason", "none"));
        assertEquals(0.0, counter("payments", "outcome", "rejected", "currency", "EUR", "reason", "insufficient_funds"));
        assertEquals(0.0, counter("payments", "outcome", "rejected", "currency", "EUR", "reason", "beneficiary_name_mismatch"));
        assertEquals(0.0, counter("auth.logins", "outcome", "blocked"));
        assertEquals(0.0, counter("payments.value", "currency", "EUR"));
    }

    @Test void completedPaymentCountsOnceAndAddsItsValue() {
        metrics.paymentCompleted(Money.of(new BigDecimal("12.50"), "EUR"));

        assertEquals(1.0, counter("payments", "outcome", "completed", "currency", "EUR", "reason", "none"));
        assertEquals(12.5, counter("payments.value", "currency", "EUR"));
    }

    @Test void rejectionReasonIsReducedToABoundedSlug() {
        metrics.paymentRejected(Money.of(BigDecimal.ONE, "EUR"), "Idempotency key reuse");

        assertEquals(1.0, counter("payments", "outcome", "rejected", "currency", "EUR", "reason", "idempotency_key_reuse"));
    }

    @Test void allPaymentSeriesShareTheSameTagKeysSoPrometheusKeepsThem() {
        metrics.paymentCompleted(Money.of(BigDecimal.ONE, "USD"));
        metrics.paymentReplayed(Money.of(BigDecimal.ONE, "USD"));
        metrics.paymentRejected(Money.of(BigDecimal.ONE, "USD"), "Forbidden");

        registry.find("payments").counters().forEach(counter ->
                assertEquals(java.util.Set.of("outcome", "currency", "reason"),
                        counter.getId().getTags().stream().map(io.micrometer.core.instrument.Tag::getKey)
                                .collect(java.util.stream.Collectors.toSet())));
    }
}
