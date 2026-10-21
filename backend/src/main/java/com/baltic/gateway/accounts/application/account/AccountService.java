package com.baltic.gateway.accounts.application.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.account.Account;
import com.baltic.gateway.accounts.infrastructure.account.AccountRepository;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Optional<String> findHolderName(String iban) {
        return accounts.findByIban(iban).map(account -> account.getHolderName());
    }

    @Transactional(readOnly = true)
    public List<Account> list(UserPrincipal user) {
        return accounts.findByOwnerIdOrderByIban(user.id());
    }

    @Transactional(readOnly = true)
    public void requireOwnedBy(String iban, UUID ownerId) {
        if (!accounts.existsByIbanAndOwnerId(iban, ownerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Source account is not yours");
        }
    }
}
