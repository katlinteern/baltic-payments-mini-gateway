package com.baltic.gateway.payments.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.baltic.gateway.payments.application.PaymentCommand;
import com.baltic.gateway.payments.application.PaymentService;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ClientIp;
import com.baltic.gateway.platform.web.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService payments;
    private final SlidingWindowRateLimiter rateLimiter;

    public PaymentController(PaymentService payments,
                             @Qualifier("paymentRateLimiter") SlidingWindowRateLimiter rateLimiter) {
        this.payments = payments;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @Operation(summary = "Transfer money between accounts; retries use Idempotency-Key")
    public ResponseEntity<TransactionDto> pay(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PaymentRequest body,
            HttpServletRequest request) {
        rateLimiter.acquire(principal.id().toString());
        if (key.length() < 8 || key.length() > 64 || !key.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key", "Key must contain 8-64 letters, digits, underscores, or hyphens");
        }
        var command = new PaymentCommand(body.fromIban(), body.toIban(), body.beneficiaryName(), toMoney(body),
                body.reference(), body.proceedDespiteNameMismatch());
        var result = payments.execute(principal, command, key, ClientIp.of(request));
        TransactionDto response = TransactionDto.from(result.transaction());
        if (result.replay()) return ResponseEntity.ok().header("Idempotent-Replay", "true").body(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private static Money toMoney(PaymentRequest body) {
        try {
            return Money.of(body.amount(), body.currency());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid amount", ex.getMessage());
        }
    }
}
