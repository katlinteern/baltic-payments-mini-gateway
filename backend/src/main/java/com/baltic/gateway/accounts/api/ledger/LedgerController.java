package com.baltic.gateway.accounts.api.ledger;

import java.util.UUID;
import com.baltic.gateway.accounts.api.ledger.dto.LedgerEntryDto;
import com.baltic.gateway.accounts.application.ledger.LedgerService;
import com.baltic.gateway.platform.security.CurrentUser;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class LedgerController {
    private final LedgerService service;

    public LedgerController(LedgerService service) {
        this.service = service;
    }

    @GetMapping("/{id}/ledger")
    public PageResponse<LedgerEntryDto> statement(@CurrentUser UserPrincipal user,
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.statement(user, id, page, size).map(LedgerEntryDto::from));
    }
}
