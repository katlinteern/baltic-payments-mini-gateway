package com.baltic.gateway.payments.application.transaction;

import java.math.BigDecimal;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.payments.domain.transaction.PaymentTransaction;

public record TransactionView(PaymentTransaction transaction, String fromName, String toName,
                              BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter, EntryDirection side) {}
