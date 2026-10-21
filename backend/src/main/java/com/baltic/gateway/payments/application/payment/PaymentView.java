package com.baltic.gateway.payments.application.payment;

import com.baltic.gateway.payments.domain.payment.Payment;

import java.math.BigDecimal;

public record PaymentView(Payment payment, BigDecimal balanceAfter) {}
