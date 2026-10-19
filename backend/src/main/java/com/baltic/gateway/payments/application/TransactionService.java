package com.baltic.gateway.payments.application;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.baltic.gateway.accounts.application.AccountService;
import com.baltic.gateway.accounts.application.HolderNameSearch;
import com.baltic.gateway.accounts.domain.LedgerEntry;
import com.baltic.gateway.payments.domain.NameCheckResult;
import com.baltic.gateway.payments.domain.PaymentTransaction;
import com.baltic.gateway.payments.infrastructure.TransactionRepository;
import com.baltic.gateway.platform.persistence.Like;
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
     * Search filters. {@code text} is free text matched against the payer/payee name, either IBAN and the reference.
     * {@code from} is inclusive and {@code to} exclusive; both are instants so the client decides
     * what "a day" means in its own time zone.
     */
    public record Criteria(String text, Instant from, Instant to) {}

    /**
     * One page of the history. Paging counts payments, so {@code totalElements} is the number of payments; a transfer
     * between the caller's own accounts contributes two rows (debit and credit), which can make {@code rows} longer
     * than {@code size}.
     */
    public record SearchResult(List<TransactionView> rows, long totalElements, int page, int size) {}

    @Transactional(readOnly = true)
    public SearchResult search(UserPrincipal actor, Criteria criteria, int page, int size) {
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<PaymentTransaction> payments = repository.findAll(specification(actor, criteria), pageable);
        // A transfer between the caller's own accounts is two entries (debit, then credit).
        List<Entry> entries = payments.getContent().stream()
                .flatMap(tx -> isInternal(actor, tx)
                        ? Stream.of(new Entry(tx, EntrySide.DEBIT), new Entry(tx, EntrySide.CREDIT))
                        : Stream.of(new Entry(tx, null)))
                .toList();
        return new SearchResult(view(actor, entries), payments.getTotalElements(), pageable.getPageNumber(),
                pageable.getPageSize());
    }

    private static Specification<PaymentTransaction> specification(UserPrincipal actor, Criteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!actor.isPrivileged()) {
                predicates.add(cb.or(cb.equal(root.get("createdBy"), actor.id()), cb.equal(root.get("toOwnerId"), actor.id())));
            }
            if (criteria.text() != null && !criteria.text().isBlank()) {
                String text = criteria.text().trim();
                String term = Like.contains(text);
                String ibanTerm = Like.contains(PaymentService.normalize(text));
                Subquery<UUID> holders = HolderNameSearch.accountIdsMatching(query, cb, text);
                // The registered payee name is hidden from a payer whose name check failed, so it must not be searchable either.
                Predicate payeeNameVisible = actor.isPrivileged() ? cb.conjunction()
                        : cb.or(cb.notEqual(root.get("nameCheck"), NameCheckResult.NO_MATCH), cb.equal(root.get("toOwnerId"), actor.id()));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("fromIban")), ibanTerm, Like.ESCAPE),
                        cb.like(cb.lower(root.<String>get("toIban")), ibanTerm, Like.ESCAPE),
                        cb.like(cb.lower(root.<String>get("reference")), term, Like.ESCAPE),
                        cb.like(cb.lower(root.<String>get("beneficiaryName")), term, Like.ESCAPE),
                        root.get("fromAccountId").in(holders),
                        cb.and(root.get("toAccountId").in(holders), payeeNameVisible)));
            }
            if (criteria.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), criteria.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean isInternal(UserPrincipal actor, PaymentTransaction tx) {
        return !actor.isPrivileged() && actor.id().equals(tx.getCreatedBy()) && actor.id().equals(tx.getToOwnerId());
    }

    private record Entry(PaymentTransaction transaction, EntrySide side) {}

    private List<TransactionView> view(UserPrincipal actor, List<Entry> entries) {
        var names = accounts.holderNames(entries.stream().map(Entry::transaction)
                .flatMap(tx -> Stream.of(tx.getFromAccountId(), tx.getToAccountId())).distinct().toList());
        Map<UUID, Map<UUID, BigDecimal>> balances = new HashMap<>();
        for (LedgerEntry entry : accounts.postingsOf(entries.stream().map(entry -> entry.transaction().getId()).distinct().toList())) {
            balances.computeIfAbsent(entry.getTransactionId(), id -> new HashMap<>())
                    .put(entry.getAccountId(), entry.getBalanceAfter());
        }
        return entries.stream().map(entry -> {
            PaymentTransaction tx = entry.transaction();
            boolean payer = actor.id().equals(tx.getCreatedBy());
            boolean payee = actor.id().equals(tx.getToOwnerId());
            boolean hideRegisteredName = tx.getNameCheck() == NameCheckResult.NO_MATCH && !actor.isPrivileged() && !payee;
            Map<UUID, BigDecimal> postings = balances.getOrDefault(tx.getId(), Map.of());
            return new TransactionView(tx, names.get(tx.getFromAccountId()),
                    hideRegisteredName ? tx.getBeneficiaryName() : names.get(tx.getToAccountId()),
                    payer ? postings.get(tx.getFromAccountId()) : null,
                    payee ? postings.get(tx.getToAccountId()) : null, entry.side());
        }).toList();
    }
}
