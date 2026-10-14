package lt.baltic.gateway.controller;

import java.time.LocalDate;
import lt.baltic.gateway.domain.TxStatus;
import lt.baltic.gateway.dto.PageResponse;
import lt.baltic.gateway.dto.TransactionDto;
import lt.baltic.gateway.security.UserPrincipal;
import lt.baltic.gateway.service.TransactionService;
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
    public TransactionController(TransactionService service) { this.service = service; }
    @GetMapping
    public PageResponse<TransactionDto> search(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String iban, @RequestParam(required = false) TxStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return PageResponse.from(service.search(principal, iban, status, dateFrom, dateTo, page, size)
            .map(TransactionDto::from));
    }
}
