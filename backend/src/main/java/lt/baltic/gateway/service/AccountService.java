package lt.baltic.gateway.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lt.baltic.gateway.domain.Account;
import lt.baltic.gateway.repo.AccountRepository;
import lt.baltic.gateway.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    public record AccountDto(UUID id, String iban, String currency, BigDecimal balance) {
        static AccountDto from(Account account) { return new AccountDto(account.getId(), account.getIban(), account.getCurrency(), account.getBalance()); }
    }
    private final AccountRepository accounts;
    public AccountService(AccountRepository accounts) { this.accounts = accounts; }
    @Transactional(readOnly = true)
    public List<AccountDto> list(UserPrincipal user) {
        var rows = user.isPrivileged() ? accounts.findAllByOrderByIban() : accounts.findByOwnerIdOrderByIban(user.id());
        return rows.stream().map(AccountDto::from).toList();
    }
}
