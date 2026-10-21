package com.baltic.gateway.payments.application.payee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.accounts.domain.account.Account;
import com.baltic.gateway.accounts.infrastructure.account.AccountRepository;
import com.baltic.gateway.transactions.domain.transaction.NameCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayeeVerificationServiceTest {
    private static final String IBAN = "EE382200221020145685";
    @Mock AccountRepository accountRepository;
    private PayeeVerificationService service;

    @BeforeEach void setUp() {
        service = new PayeeVerificationService(new AccountService(accountRepository));
    }

    private void accountHeldBy(String holder) {
        when(accountRepository.findByIban(IBAN)).thenReturn(
                Optional.of(new Account(UUID.randomUUID(), UUID.randomUUID(), IBAN, "EUR", holder, BigDecimal.TEN)));
    }

    @Test void exactNameMatchesWithoutDisclosingTheRegisteredName() {
        accountHeldBy("Bob Tamm");

        var verification = service.verify("EE38 2200 2210 2014 5685", "tamm bob");

        assertEquals(NameCheckResult.MATCH, verification.result());
        assertNull(verification.registeredName());
    }

    @Test void closeMatchReturnsTheRegisteredNameSoThePayerCanConfirmIt() {
        accountHeldBy("Bob Tamm");

        var verification = service.verify(IBAN, "Bob");

        assertEquals(NameCheckResult.CLOSE_MATCH, verification.result());
        assertEquals("Bob Tamm", verification.registeredName());
    }

    @Test void mismatchDoesNotRevealTheRegisteredName() {
        accountHeldBy("Bob Tamm");

        var verification = service.verify(IBAN, "Mallory Evil");

        assertEquals(NameCheckResult.NO_MATCH, verification.result());
        assertNull(verification.registeredName());
    }

    @Test void unknownAccountCannotBeVerified() {
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.empty());

        assertEquals(NameCheckResult.NOT_AVAILABLE, service.verify(IBAN, "Bob").result());
    }
}
