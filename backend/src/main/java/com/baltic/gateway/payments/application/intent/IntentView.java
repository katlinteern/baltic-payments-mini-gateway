package com.baltic.gateway.payments.application;

import java.math.BigDecimal;
import com.baltic.gateway.payments.domain.PaymentIntent;

/** An intent together with the payer's balance after it, which is known only once the payment has been executed. */
public record IntentView(PaymentIntent intent, BigDecimal balanceAfter) {}
