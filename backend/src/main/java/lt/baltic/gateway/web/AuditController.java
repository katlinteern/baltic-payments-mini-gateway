package lt.baltic.gateway.web;

import lt.baltic.gateway.repo.AuditLogRepository;
import lt.baltic.gateway.web.Dtos.AuditDto;
import lt.baltic.gateway.web.Dtos.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditLogRepository repository;
    public AuditController(AuditLogRepository repository) { this.repository = repository; }
    @GetMapping
    public PageResponse<AuditDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = repository.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(result.map(AuditDto::from));
    }
}
