package com.baltic.gateway.platform.metrics;

import com.baltic.gateway.platform.error.ErrorCode;
import com.baltic.gateway.platform.money.Money;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

@Component
public class GatewayMetrics {
    private static final String NO_REASON = "none";
    private static final String DEFAULT_CURRENCY = "EUR";
    private static final List<ErrorCode> KNOWN_REJECTIONS = List.of(
            ErrorCode.FORBIDDEN,
            ErrorCode.ACCOUNT_NOT_FOUND,
            ErrorCode.BENEFICIARY_NAME_MISMATCH,
            ErrorCode.INSUFFICIENT_FUNDS,
            ErrorCode.CURRENCY_MISMATCH,
            ErrorCode.IDEMPOTENCY_KEY_REUSE,
            ErrorCode.INVALID_AMOUNT,
            ErrorCode.INVALID_TRANSFER);

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
        registry.counter("payments.value", "currency", amount.currency())
                .increment(amount.amount().doubleValue());
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
