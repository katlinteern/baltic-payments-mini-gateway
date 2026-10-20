package com.baltic.gateway.payments.application.intent;

import java.math.BigDecimal;
import com.baltic.gateway.payments.domain.intent.PaymentIntent;

public record PaymentIntentView(PaymentIntent intent, BigDecimal balanceAfter) {}
