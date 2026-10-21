package com.baltic.gateway.transactions.application.history;

import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.transactions.domain.transaction.Transaction;

import java.math.BigDecimal;

public record TransactionView(
        Transaction transaction,
        String fromName,
        String toName,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        EntryDirection side) {}
