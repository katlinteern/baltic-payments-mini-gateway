package lt.baltic.gateway.accounts.api;

import java.util.List;
import lt.baltic.gateway.accounts.application.AccountService;
import lt.baltic.gateway.platform.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return service.list(principal).stream().map(AccountDto::from).toList();
    }
}
