package com.baltic.gateway.payments.application;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.baltic.gateway.payments.domain.PaymentTransaction;
import com.baltic.gateway.payments.infrastructure.TransactionRepository;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Search filters. {@code from} is inclusive and {@code to} exclusive; both are instants so the client decides
     * what "a day" means in its own time zone.
     */
    public record Criteria(String iban, Instant from, Instant to) {}

    @Transactional(readOnly = true)
    public Page<PaymentTransaction> search(UserPrincipal actor, Criteria criteria, int page, int size) {
        Specification<PaymentTransaction> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!actor.isPrivileged()) {
                predicates.add(cb.or(cb.equal(root.get("createdBy"), actor.id()), cb.equal(root.get("toOwnerId"), actor.id())));
            }
            if (criteria.iban() != null && !criteria.iban().isBlank()) {
                String normalized = PaymentService.normalize(criteria.iban());
                predicates.add(cb.or(cb.equal(root.get("fromIban"), normalized), cb.equal(root.get("toIban"), normalized)));
            }
            if (criteria.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), criteria.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return repository.findAll(specification, pageable);
    }
}
