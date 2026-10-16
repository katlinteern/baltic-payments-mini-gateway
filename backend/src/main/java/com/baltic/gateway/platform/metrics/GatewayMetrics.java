package com.baltic.gateway.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import com.baltic.gateway.platform.money.Money;
import org.springframework.stereotype.Component;

/**
 * Business metrics exposed to Prometheus. Tag values come from small fixed sets (outcomes, currencies,
 * rejection titles) so cardinality stays bounded; never tag with user ids, IBANs or free text.
 * Prometheus requires every series of one metric name to carry the same tag keys, so {@code payments} always
 * has outcome, currency and reason; otherwise the registry silently drops the meters that disagree.
 */
@Component
public class GatewayMetrics {
    private static final String NO_REASON = "none";
    // The demo books EUR only; other currencies appear lazily on first use.
    private static final String DEFAULT_CURRENCY = "EUR";
    // Titles of the BusinessException rejections raised by the payment flow.
    private static final List<String> KNOWN_REJECTIONS = List.of("Forbidden", "Account not found", "Beneficiary name mismatch",
            "Insufficient funds", "Currency mismatch", "Idempotency key reuse", "Invalid amount", "Invalid transfer");

    private final MeterRegistry registry;
    private final Timer paymentTimer;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.paymentTimer = Timer.builder("payments.processing")
                .description("Time to execute a payment, including locking and persistence")
                .publishPercentileHistogram()
                .register(registry);
        registerZeroSeries();
    }

    /**
     * Prometheus' increase() and rate() cannot see the first increment of a series that did not exist before it,
     * so every series the dashboards and alerts rely on starts at zero.
     */
    private void registerZeroSeries() {
        payment("completed", DEFAULT_CURRENCY, NO_REASON);
        payment("replayed", DEFAULT_CURRENCY, NO_REASON);
        KNOWN_REJECTIONS.forEach(title -> payment("rejected", DEFAULT_CURRENCY, slug(title)));
        registry.counter("payments.value", "currency", DEFAULT_CURRENCY);
        List.of("success", "failed", "blocked").forEach(outcome -> registry.counter("auth.logins", "outcome", outcome));
    }

    public <T> T timePayment(Supplier<T> payment) {
        return paymentTimer.record(payment);
    }

    public void paymentCompleted(Money amount) {
        payment("completed", amount.currency(), NO_REASON).increment();
        registry.counter("payments.value", "currency", amount.currency()).increment(amount.amount().doubleValue());
    }

    public void paymentReplayed(Money amount) {
        payment("replayed", amount.currency(), NO_REASON).increment();
    }

    public void paymentRejected(Money amount, String reasonTitle) {
        payment("rejected", amount.currency(), slug(reasonTitle)).increment();
    }

    public void login(String outcome) {
        registry.counter("auth.logins", "outcome", outcome).increment();
    }

    private Counter payment(String outcome, String currency, String reason) {
        return registry.counter("payments", "outcome", outcome, "currency", currency, "reason", reason);
    }

    private static String slug(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
