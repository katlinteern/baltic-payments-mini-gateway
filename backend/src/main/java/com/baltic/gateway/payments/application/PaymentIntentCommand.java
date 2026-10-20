package com.baltic.gateway.payments.application;

import com.baltic.gateway.platform.money.Money;

/** What the payer typed into the payment form; the beneficiary name is checked against the account holder. */
public record PaymentIntentCommand(String fromIban, String toIban, String beneficiaryName, Money amount,
                                   String reference) {}
