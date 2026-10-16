package lt.baltic.gateway.audit.api;

import lt.baltic.gateway.audit.application.AuditService;
import lt.baltic.gateway.platform.web.PageResponse;
import org.springframework.data.domain.PageRequest;
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
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        var result = service.list(pageable);
        return PageResponse.from(result.map(AuditDto::from));
    }
}
