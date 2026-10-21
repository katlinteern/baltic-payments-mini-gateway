package com.baltic.gateway.accounts.api;

import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.platform.security.CurrentUser;
import com.baltic.gateway.platform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService service;

    @GetMapping
    public List<AccountDto> list(@CurrentUser UserPrincipal user) {
        return service.list(user).stream().map(AccountDto::from).toList();
    }
}
