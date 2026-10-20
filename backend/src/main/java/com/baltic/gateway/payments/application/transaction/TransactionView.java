package com.baltic.gateway.payments.application.transaction;

import java.math.BigDecimal;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.payments.domain.transaction.Transaction;

public record TransactionView(Transaction transaction, String fromName, String toName,
                              BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter, EntryDirection side) {}
