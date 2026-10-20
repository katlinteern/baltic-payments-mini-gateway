package com.baltic.gateway.payments.application;

import com.baltic.gateway.platform.money.Money;

public record PaymentIntentCommand(String fromIban, String toIban, String beneficiaryName, Money amount,
                                   String reference) {}
