package com.baltic.gateway.payments.api.transaction;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import com.baltic.gateway.payments.api.transaction.dto.TransactionDto;
import com.baltic.gateway.payments.application.transaction.TransactionHistoryService;
import com.baltic.gateway.platform.security.CurrentUser;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.paging.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionHistoryService service;

    public TransactionController(TransactionHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TransactionDto> search(@CurrentUser UserPrincipal user,
            @RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        var result = service.search(user, new TransactionHistoryService.Criteria(q, from, to), page, size);
        return new PageResponse<>(result.rows().stream().map(TransactionDto::from).toList(), result.totalElements(),
                result.page(), result.size());
    }
}
