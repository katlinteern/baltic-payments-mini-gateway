package com.baltic.gateway.payments.application.payee;

import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.payments.application.execution.PaymentExecutionService;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.payments.domain.payee.PayeeNameMatcher;
import org.springframework.stereotype.Service;

/**
 * Verification of Payee as a separate step before the payment, so the payer sees the outcome and decides.
 * For a close match the registered name is returned so the payer can confirm it is the intended person; for a
 * mismatch nothing about the registered name is disclosed.
 */
@Service
public class PayeeVerificationService {
    public record Verification(NameCheckResult result, String registeredName) {}

    private final AccountService accounts;

    public PayeeVerificationService(AccountService accounts) {
        this.accounts = accounts;
    }

    public Verification verify(String iban, String providedName) {
        var holder = accounts.findHolderName(PaymentExecutionService.normalize(iban));
        if (holder.isEmpty()) return new Verification(NameCheckResult.NOT_AVAILABLE, null);
        NameCheckResult result = PayeeNameMatcher.check(providedName, holder.get());
        return new Verification(result, result == NameCheckResult.CLOSE_MATCH ? holder.get() : null);
    }
}
