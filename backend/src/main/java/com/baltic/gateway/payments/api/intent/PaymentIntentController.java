package com.baltic.gateway.payments.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import com.baltic.gateway.payments.api.dto.ConfirmationRequest;
import com.baltic.gateway.payments.api.dto.PaymentIntentDto;
import com.baltic.gateway.payments.api.dto.PaymentIntentRequest;
import com.baltic.gateway.payments.application.PaymentIntentCommand;
import com.baltic.gateway.payments.application.PaymentIntentService;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ClientIp;
import com.baltic.gateway.platform.web.ErrorCode;
import com.baltic.gateway.platform.web.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only way to pay: prepare a payment, confirm it with strong authentication, and poll it until it is executed.
 * Preparing includes the beneficiary name check, so it is rate limited like one: the check reveals whether a guessed
 * name is right.
 */
@RestController
@RequestMapping("/api/payments/intents")
public class PaymentIntentController {
    private final PaymentIntentService intents;
    private final SlidingWindowRateLimiter nameCheckLimiter;
    private final SlidingWindowRateLimiter paymentLimiter;

    public PaymentIntentController(PaymentIntentService intents,
                                   @Qualifier("payeeVerificationRateLimiter") SlidingWindowRateLimiter nameCheckLimiter,
                                   @Qualifier("paymentRateLimiter") SlidingWindowRateLimiter paymentLimiter) {
        this.intents = intents;
        this.nameCheckLimiter = nameCheckLimiter;
        this.paymentLimiter = paymentLimiter;
    }

    @PostMapping
    @Operation(summary = "Prepare a payment: checks the accounts and the beneficiary name, moves no money")
    public ResponseEntity<PaymentIntentDto> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentIntentRequest body, HttpServletRequest request) {
        nameCheckLimiter.acquire(principal.id().toString());
        var command = new PaymentIntentCommand(body.fromIban(), body.toIban(), body.beneficiaryName(), toMoney(body),
                body.reference());
        var view = intents.create(principal, command, ClientIp.of(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentIntentDto.from(view));
    }

    @PostMapping("/{id}/confirmation")
    @Operation(summary = "Ask the authentication app to approve the payment; the response carries the code to compare")
    public PaymentIntentDto confirm(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestBody(required = false) ConfirmationRequest body, HttpServletRequest request) {
        paymentLimiter.acquire(principal.id().toString());
        boolean acknowledged = body != null && body.acknowledgeNameMismatch();
        return PaymentIntentDto.from(intents.startConfirmation(principal, id, acknowledged, ClientIp.of(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "The payment's state; an approved authentication is executed when it is read")
    public PaymentIntentDto get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            HttpServletRequest request) {
        return PaymentIntentDto.from(intents.get(principal, id, ClientIp.of(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a payment that has not been executed")
    public PaymentIntentDto cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            HttpServletRequest request) {
        return PaymentIntentDto.from(intents.cancel(principal, id, ClientIp.of(request)));
    }

    private static Money toMoney(PaymentIntentRequest body) {
        try {
            return Money.of(body.amount(), body.currency());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, ex.getMessage());
        }
    }
}
