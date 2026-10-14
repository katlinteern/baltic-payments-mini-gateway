package lt.baltic.gateway.services;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import lt.baltic.gateway.entities.PaymentTransaction;
import lt.baltic.gateway.enums.TxStatus;
import lt.baltic.gateway.repositories.TransactionRepository;
import lt.baltic.gateway.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private final TransactionRepository repository;
    public TransactionService(TransactionRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> search(UserPrincipal actor, String iban, TxStatus status,
                                               LocalDate from, LocalDate to, int page, int size) {
        Specification<PaymentTransaction> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!actor.isPrivileged()) predicates.add(cb.or(cb.equal(root.get("createdBy"), actor.id()), cb.equal(root.get("toOwnerId"), actor.id())));
            if (iban != null && !iban.isBlank()) {
                String normalized = PaymentService.normalize(iban);
                predicates.add(cb.or(cb.equal(root.get("fromIban"), normalized), cb.equal(root.get("toIban"), normalized)));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.<java.time.Instant>get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (to != null) predicates.add(cb.lessThan(root.<java.time.Instant>get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = repository.findAll(specification, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return result;
    }
}
