package com.baltic.gateway.payments.application.transaction;

import java.math.BigDecimal;
import com.baltic.gateway.payments.domain.transaction.PaymentTransaction;

/**
 * A transaction as one particular caller sees it.
 *
 * @param fromName     holder of the source account
 * @param toName       holder of the destination account; for a payment the payer sent despite a name mismatch it is the
 *                     name the payer typed, because the registered name is not disclosed to the payer
 * @param fromBalanceAfter balance of the source account right after the payment; {@code null} unless the caller owns
 *                         that account (or the posting is unknown)
 * @param toBalanceAfter   the same for the destination account
 * @param side         for a transfer between the caller's own accounts, which of its two entries this is; otherwise {@code null}
 */
public record TransactionView(PaymentTransaction transaction, String fromName, String toName,
                              BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter, EntrySide side) {}
