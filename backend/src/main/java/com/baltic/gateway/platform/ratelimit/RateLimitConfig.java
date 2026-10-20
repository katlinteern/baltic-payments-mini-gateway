package com.baltic.gateway.platform.ratelimit;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {
    @Bean
    @Qualifier("paymentRateLimiter")
    SlidingWindowRateLimiter paymentRateLimiter(Clock clock, @Value("${app.rate-limit.payments-per-minute}") int limit) {
        return new SlidingWindowRateLimiter(clock, limit, Duration.ofMinutes(1));
    }

    @Bean
    @Qualifier("payeeVerificationRateLimiter")
    SlidingWindowRateLimiter payeeVerificationRateLimiter(Clock clock,
            @Value("${app.rate-limit.payee-verifications-per-minute}") int limit) {
        return new SlidingWindowRateLimiter(clock, limit, Duration.ofMinutes(1));
    }
}
