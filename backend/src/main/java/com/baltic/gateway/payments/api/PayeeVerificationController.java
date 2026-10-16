package com.baltic.gateway.payments.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.baltic.gateway.payments.application.PayeeVerificationService;
import com.baltic.gateway.payments.domain.NameCheckResult;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.validation.ValidIban;
import com.baltic.gateway.platform.web.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/verify-payee")
public class PayeeVerificationController {
    public record Request(
            @NotBlank(message = "Beneficiary account is required") @ValidIban String toIban,
            @NotBlank(message = "Beneficiary name is required")
            @Size(max = 120, message = "Beneficiary name must be at most 120 characters") String beneficiaryName) {}

    /** The registered name is present only for {@code CLOSE_MATCH}. */
    public record Response(NameCheckResult result, String registeredName) {}

    private final PayeeVerificationService service;
    private final SlidingWindowRateLimiter rateLimiter;

    public PayeeVerificationController(PayeeVerificationService service,
            @Qualifier("payeeVerificationRateLimiter") SlidingWindowRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @Operation(summary = "Verification of Payee: compare the beneficiary name with the account holder before paying")
    public Response verify(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody Request body) {
        rateLimiter.acquire(principal.id().toString());
        var verification = service.verify(body.toIban(), body.beneficiaryName());
        return new Response(verification.result(), verification.registeredName());
    }
}
