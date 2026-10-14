package lt.baltic.gateway.web;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lt.baltic.gateway.security.UserPrincipal;
import lt.baltic.gateway.service.PaymentService;
import lt.baltic.gateway.web.Dtos.PaymentRequest;
import lt.baltic.gateway.web.Dtos.TransactionDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments) { this.payments = payments; }
    @PostMapping
    @Operation(summary = "Transfer money between accounts; retries use Idempotency-Key")
    public ResponseEntity<TransactionDto> pay(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PaymentRequest body,
            HttpServletRequest request) {
        if (key.length() < 8 || key.length() > 64 || !key.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key", "Key must contain 8-64 letters, digits, underscores, or hyphens");
        }
        var result = payments.execute(principal, body, key, ClientIp.of(request));
        if (result.replay()) return ResponseEntity.ok().header("Idempotent-Replay", "true").body(result.transaction());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.transaction());
    }
}
