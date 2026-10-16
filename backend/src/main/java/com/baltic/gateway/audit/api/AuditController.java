package com.baltic.gateway.audit.api;

import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.platform.web.PageRequests;
import com.baltic.gateway.platform.web.PageResponse;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService service;
    public AuditController(AuditService service) { this.service = service; }
    @GetMapping
    public PageResponse<AuditDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var result = service.list(pageable);
        return PageResponse.from(result.map(AuditDto::from));
    }
}
