package com.baltic.gateway.payments.application.payee;

import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.platform.validation.Iban;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.payments.domain.payee.PayeeNameMatcher;
import org.springframework.stereotype.Service;

/** A close match returns the registered name so the payer can confirm it; a mismatch discloses nothing about it. */
@Service
public class PayeeVerificationService {
    public record Verification(NameCheckResult result, String registeredName) {}

    private final AccountService accounts;

    public PayeeVerificationService(AccountService accounts) {
        this.accounts = accounts;
    }

    public Verification verify(String iban, String providedName) {
        var holder = accounts.findHolderName(Iban.normalize(iban));
        if (holder.isEmpty()) return new Verification(NameCheckResult.NOT_AVAILABLE, null);
        NameCheckResult result = PayeeNameMatcher.check(providedName, holder.get());
        return new Verification(result, result == NameCheckResult.CLOSE_MATCH ? holder.get() : null);
    }
}
