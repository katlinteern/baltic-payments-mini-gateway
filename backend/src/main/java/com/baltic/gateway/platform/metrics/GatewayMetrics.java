package com.baltic.gateway.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.function.Supplier;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.web.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Tag values come from small fixed sets, so never tag with user ids, IBANs or free text. Every series of one metric name
 * must carry the same tag keys, or the Prometheus registry silently drops the meters that disagree.
 */
@Component
public class GatewayMetrics {
    private static final String NO_REASON = "none";
    // The demo books EUR only; other currencies appear on first use.
    private static final String DEFAULT_CURRENCY = "EUR";
    // Start at zero, so alerts and dashboards see the first rejection.
    private static final List<ErrorCode> KNOWN_REJECTIONS = List.of(ErrorCode.FORBIDDEN, ErrorCode.ACCOUNT_NOT_FOUND,
            ErrorCode.BENEFICIARY_NAME_MISMATCH, ErrorCode.INSUFFICIENT_FUNDS, ErrorCode.CURRENCY_MISMATCH,
            ErrorCode.IDEMPOTENCY_KEY_REUSE, ErrorCode.INVALID_AMOUNT, ErrorCode.INVALID_TRANSFER);

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

    /** increase() and rate() cannot see the first increment of a series that did not exist before it. */
    private void registerZeroSeries() {
        payment("completed", DEFAULT_CURRENCY, NO_REASON);
        payment("replayed", DEFAULT_CURRENCY, NO_REASON);
        KNOWN_REJECTIONS.forEach(code -> payment("rejected", DEFAULT_CURRENCY, code.metricReason()));
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

    public void paymentRejected(Money amount, ErrorCode reason) {
        payment("rejected", amount.currency(), reason.metricReason()).increment();
    }

    public void login(String outcome) {
        registry.counter("auth.logins", "outcome", outcome).increment();
    }

    private Counter payment(String outcome, String currency, String reason) {
        return registry.counter("payments", "outcome", outcome, "currency", currency, "reason", reason);
    }
}
