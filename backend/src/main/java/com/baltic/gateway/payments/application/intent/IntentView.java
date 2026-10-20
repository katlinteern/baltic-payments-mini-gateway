package com.baltic.gateway.payments.application.intent;

import java.math.BigDecimal;
import com.baltic.gateway.payments.domain.intent.PaymentIntent;

public record IntentView(PaymentIntent intent, BigDecimal balanceAfter) {}
