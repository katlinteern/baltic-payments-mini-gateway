package com.baltic.gateway.payments.api.intent;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import com.baltic.gateway.payments.api.intent.dto.PaymentConfirmationRequest;
import com.baltic.gateway.payments.api.intent.dto.PaymentIntentDto;
import com.baltic.gateway.payments.api.intent.dto.PaymentIntentRequest;
import com.baltic.gateway.payments.application.payment.PaymentDetails;
import com.baltic.gateway.payments.application.intent.PaymentConfirmationService;
import com.baltic.gateway.payments.application.intent.PaymentIntentService;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.CurrentUser;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.web.ClientIp;
import com.baltic.gateway.platform.error.ErrorCode;
import com.baltic.gateway.platform.ratelimit.SlidingWindowRateLimiter;
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

@RestController
@RequestMapping("/api/payments/intents")
public class PaymentIntentController {
    private final PaymentIntentService intents;
    private final PaymentConfirmationService confirmation;
    private final SlidingWindowRateLimiter nameCheckLimiter;
    private final SlidingWindowRateLimiter paymentLimiter;

    public PaymentIntentController(PaymentIntentService intents, PaymentConfirmationService confirmation,
                                   @Qualifier("payeeVerificationRateLimiter") SlidingWindowRateLimiter nameCheckLimiter,
                                   @Qualifier("paymentRateLimiter") SlidingWindowRateLimiter paymentLimiter) {
        this.intents = intents;
        this.confirmation = confirmation;
        this.nameCheckLimiter = nameCheckLimiter;
        this.paymentLimiter = paymentLimiter;
    }

    @PostMapping
    @Operation(summary = "Prepare a payment: checks the accounts and the beneficiary name, moves no money")
    public ResponseEntity<PaymentIntentDto> create(@CurrentUser UserPrincipal user,
            @Valid @RequestBody PaymentIntentRequest body, HttpServletRequest request) {
        nameCheckLimiter.acquire(user.id().toString());
        var details = new PaymentDetails(body.fromIban(), body.toIban(), body.beneficiaryName(), toMoney(body),
                body.reference());
        var view = intents.create(user, details, ClientIp.of(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentIntentDto.from(view));
    }

    @PostMapping("/{id}/confirmation")
    @Operation(summary = "Ask the authentication app to approve the payment; the response carries the code to compare")
    public PaymentIntentDto confirm(@CurrentUser UserPrincipal user, @PathVariable UUID id,
            @RequestBody(required = false) PaymentConfirmationRequest body, HttpServletRequest request) {
        paymentLimiter.acquire(user.id().toString());
        boolean acknowledged = body != null && body.acknowledgeNameMismatch();
        return PaymentIntentDto.from(intents.startConfirmation(user, id, acknowledged, ClientIp.of(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "The payment's state; an approved authentication is executed when it is read")
    public PaymentIntentDto get(@CurrentUser UserPrincipal user, @PathVariable UUID id,
            HttpServletRequest request) {
        return PaymentIntentDto.from(confirmation.checkConfirmation(user, id, ClientIp.of(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a payment that has not been executed")
    public PaymentIntentDto cancel(@CurrentUser UserPrincipal user, @PathVariable UUID id,
            HttpServletRequest request) {
        return PaymentIntentDto.from(intents.cancel(user, id, ClientIp.of(request)));
    }

    private static Money toMoney(PaymentIntentRequest body) {
        try {
            return Money.of(body.amount(), body.currency());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, ex.getMessage());
        }
    }
}
