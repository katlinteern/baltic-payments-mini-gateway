package com.baltic.gateway.transactions.application.history;

import java.math.BigDecimal;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.transactions.domain.transaction.Transaction;

public record TransactionView(Transaction transaction, String fromName, String toName,
                              BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter, EntryDirection side) {}
