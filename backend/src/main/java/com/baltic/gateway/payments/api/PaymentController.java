package com.baltic.gateway.payments.api;

import com.baltic.gateway.payments.application.payment.PaymentConfirmationService;
import com.baltic.gateway.payments.application.payment.PaymentDetails;
import com.baltic.gateway.payments.application.payment.PaymentService;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.error.ErrorCode;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.ratelimit.SlidingWindowRateLimiter;
import com.baltic.gateway.platform.security.CurrentUser;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService payments;
    private final PaymentConfirmationService confirmation;

    @Qualifier("payeeVerificationRateLimiter")
    private final SlidingWindowRateLimiter nameCheckLimiter;

    @Qualifier("paymentRateLimiter")
    private final SlidingWindowRateLimiter paymentLimiter;

    @PostMapping
    @Operation(summary = "Prepare a payment: checks the accounts and the beneficiary name, moves no money")
    public ResponseEntity<PaymentDto> create(
            @CurrentUser UserPrincipal user, @Valid @RequestBody PaymentRequest body, HttpServletRequest request) {
        nameCheckLimiter.acquire(user.id().toString());
        var details = new PaymentDetails(
                body.fromIban(), body.toIban(), body.beneficiaryName(), toMoney(body), body.reference());
        var view = payments.create(user, details, ClientIp.of(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentDto.from(view));
    }

    @PostMapping("/{id}/confirmation")
    @Operation(summary = "Ask the authentication app to approve the payment; the response carries the code to compare")
    public PaymentDto confirm(
            @CurrentUser UserPrincipal user,
            @PathVariable UUID id,
            @RequestBody(required = false) PaymentConfirmationRequest body,
            HttpServletRequest request) {
        paymentLimiter.acquire(user.id().toString());
        boolean acknowledged = body != null && body.acknowledgeNameMismatch();
        return PaymentDto.from(payments.startConfirmation(user, id, acknowledged, ClientIp.of(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "The payment's state; an approved authentication is executed when it is read")
    public PaymentDto get(@CurrentUser UserPrincipal user, @PathVariable UUID id, HttpServletRequest request) {
        return PaymentDto.from(confirmation.checkConfirmation(user, id, ClientIp.of(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a payment that has not been executed")
    public PaymentDto cancel(@CurrentUser UserPrincipal user, @PathVariable UUID id, HttpServletRequest request) {
        return PaymentDto.from(payments.cancel(user, id, ClientIp.of(request)));
    }

    private static Money toMoney(PaymentRequest body) {
        try {
            return Money.of(body.amount(), body.currency());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, ex.getMessage());
        }
    }
}
