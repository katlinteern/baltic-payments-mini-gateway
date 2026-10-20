package com.baltic.gateway.payments.api;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import com.baltic.gateway.payments.application.TransactionService;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TransactionDto> search(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        var result = service.search(principal, new TransactionService.Criteria(q, from, to), page, size);
        return new PageResponse<>(result.rows().stream().map(TransactionDto::from).toList(), result.totalElements(),
                result.page(), result.size());
    }
}
