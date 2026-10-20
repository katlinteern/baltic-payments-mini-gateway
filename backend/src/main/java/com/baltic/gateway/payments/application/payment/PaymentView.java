package com.baltic.gateway.payments.application.payment;

import java.math.BigDecimal;
import com.baltic.gateway.payments.domain.payment.Payment;

public record PaymentView(Payment payment, BigDecimal balanceAfter) {}
