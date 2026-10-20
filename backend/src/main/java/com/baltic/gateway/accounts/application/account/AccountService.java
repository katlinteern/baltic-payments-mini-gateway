package com.baltic.gateway.accounts.application.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.account.Account;
import com.baltic.gateway.accounts.infrastructure.account.AccountRepository;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads accounts and answers who owns them. Moving funds is {@code TransferService}'s job. */
@Service
public class AccountService {
    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Optional<String> findHolderName(String iban) {
        return accounts.findByIban(iban).map(Account::getHolderName);
    }

    @Transactional(readOnly = true)
    public List<Account> list(UserPrincipal user) {
        return accounts.findByOwnerIdOrderByIban(user.id());
    }

    /**
     * Checks ownership without taking a lock, so a caller who does not own the account can never make others wait.
     * An unknown IBAN gets the same answer as a foreign one, which does not reveal which accounts exist.
     */
    @Transactional(readOnly = true)
    public void requireOwnedBy(String iban, UUID ownerId) {
        if (!accounts.existsByIbanAndOwnerId(iban, ownerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Source account is not yours");
        }
    }
}
