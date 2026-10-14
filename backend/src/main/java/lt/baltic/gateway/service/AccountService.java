package lt.baltic.gateway.service;

import java.util.List;
import lt.baltic.gateway.entity.Account;
import lt.baltic.gateway.repository.AccountRepository;
import lt.baltic.gateway.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    public AccountService(AccountRepository accounts) { this.accounts = accounts; }
    @Transactional(readOnly = true)
    public List<Account> list(UserPrincipal user) {
        var rows = user.isPrivileged() ? accounts.findAllByOrderByIban() : accounts.findByOwnerIdOrderByIban(user.id());
        return rows;
    }
}
