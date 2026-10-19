package com.baltic.gateway.payments.application;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.baltic.gateway.accounts.application.AccountService;
import com.baltic.gateway.accounts.domain.LedgerEntry;
import com.baltic.gateway.payments.domain.NameCheckResult;
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
    private final AccountService accounts;

    public TransactionService(TransactionRepository repository, AccountService accounts) {
        this.repository = repository;
        this.accounts = accounts;
    }

    /**
     * Search filters. {@code from} is inclusive and {@code to} exclusive; both are instants so the client decides
     * what "a day" means in its own time zone.
     */
    public record Criteria(String iban, Instant from, Instant to) {}

    @Transactional(readOnly = true)
    public Page<TransactionView> search(UserPrincipal actor, Criteria criteria, int page, int size) {
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
        return view(actor, repository.findAll(specification, pageable));
    }

    private Page<TransactionView> view(UserPrincipal actor, Page<PaymentTransaction> page) {
        var names = accounts.holderNames(page.getContent().stream()
                .flatMap(tx -> Stream.of(tx.getFromAccountId(), tx.getToAccountId())).distinct().toList());
        Map<UUID, Map<UUID, BigDecimal>> balances = new HashMap<>();
        for (LedgerEntry entry : accounts.postingsOf(page.getContent().stream().map(PaymentTransaction::getId).toList())) {
            balances.computeIfAbsent(entry.getTransactionId(), id -> new HashMap<>())
                    .put(entry.getAccountId(), entry.getBalanceAfter());
        }
        return page.map(tx -> {
            boolean payer = actor.id().equals(tx.getCreatedBy());
            boolean payee = actor.id().equals(tx.getToOwnerId());
            boolean hideRegisteredName = tx.getNameCheck() == NameCheckResult.NO_MATCH && !actor.isPrivileged() && !payee;
            Map<UUID, BigDecimal> postings = balances.getOrDefault(tx.getId(), Map.of());
            return new TransactionView(tx, names.get(tx.getFromAccountId()),
                    hideRegisteredName ? tx.getBeneficiaryName() : names.get(tx.getToAccountId()),
                    payer ? postings.get(tx.getFromAccountId()) : null,
                    payee ? postings.get(tx.getToAccountId()) : null);
        });
    }
}
