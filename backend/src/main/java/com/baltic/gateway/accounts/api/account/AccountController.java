package com.baltic.gateway.accounts.api.account;

import java.util.List;
import com.baltic.gateway.accounts.api.account.dto.AccountDto;
import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.platform.security.CurrentUser;
import com.baltic.gateway.platform.security.UserPrincipal;
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
    public List<AccountDto> list(@CurrentUser UserPrincipal user) {
        return service.list(user).stream().map(AccountDto::from).toList();
    }
}
