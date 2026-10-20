package com.baltic.gateway.payments.application.transaction;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.baltic.gateway.accounts.application.ledger.LedgerService;
import com.baltic.gateway.accounts.domain.ledger.LedgerEntry;
import com.baltic.gateway.payments.application.execution.PaymentExecutionService;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.payments.domain.transaction.PaymentTransaction;
import com.baltic.gateway.payments.infrastructure.transaction.PaymentTransactionRepository;
import com.baltic.gateway.platform.persistence.Like;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.PageRequests;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionHistoryService {
    private final PaymentTransactionRepository repository;
    private final LedgerService ledger;

    public TransactionHistoryService(PaymentTransactionRepository repository, LedgerService ledger) {
        this.repository = repository;
        this.ledger = ledger;
    }

    /**
     * Search filters. {@code text} is free text matched against the payer/payee name (as registered when the payment was
     * booked), either IBAN and the reference.
     * {@code from} is inclusive and {@code to} exclusive; both are instants so the client decides
     * what "a day" means in its own time zone.
     */
    public record Criteria(String text, Instant from, Instant to) {}

    /**
     * One page of the history. Paging counts rows as the client shows them, so a transfer between the caller's own
     * accounts counts as two (debit and credit) and {@code totalElements} is the number of rows, not payments.
     */
    public record SearchResult(List<TransactionView> rows, long totalElements, int page, int size) {}

    @Transactional(readOnly = true)
    public SearchResult search(UserPrincipal actor, Criteria criteria, int page, int size) {
        var pageable = PageRequests.of(page, size, Sort.unsorted());
        var sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        long offset = pageable.getOffset();
        int limit = (int) Math.min(offset + pageable.getPageSize(), Integer.MAX_VALUE);
        Specification<PaymentTransaction> matching = specification(actor, criteria);
        // Every payment yields at least one row, so the first offset + size payments cover the requested rows.
        List<PaymentTransaction> payments = repository.findBy(matching, query -> query.sortBy(sort).limit(limit).all());
        // A transfer between the caller's own accounts is two entries (debit, then credit).
        List<Entry> entries = payments.stream()
                .flatMap(tx -> isInternal(actor, tx)
                        ? Stream.of(new Entry(tx, EntrySide.DEBIT), new Entry(tx, EntrySide.CREDIT))
                        : Stream.of(new Entry(tx, null)))
                .skip(offset)
                .limit(pageable.getPageSize())
                .toList();
        long total = repository.count(matching) + repository.count(matching.and(internal(actor)));
        return new SearchResult(view(actor, entries), total, pageable.getPageNumber(), pageable.getPageSize());
    }

    private static Specification<PaymentTransaction> internal(UserPrincipal actor) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("createdBy"), actor.id()), cb.equal(root.get("toOwnerId"), actor.id()));
    }

    private static Specification<PaymentTransaction> specification(UserPrincipal actor, Criteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(cb.equal(root.get("createdBy"), actor.id()), cb.equal(root.get("toOwnerId"), actor.id())));
            if (criteria.text() != null && !criteria.text().isBlank()) {
                String text = criteria.text().trim();
                String term = Like.contains(text);
                String ibanTerm = Like.contains(PaymentExecutionService.normalize(text));
                // The registered payee name is hidden from a payer whose name check failed, so it must not be searchable either.
                Predicate payeeNameVisible =
                        cb.or(cb.notEqual(root.get("nameCheck"), NameCheckResult.NO_MATCH), cb.equal(root.get("toOwnerId"), actor.id()));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("fromIban")), ibanTerm, Like.ESCAPE),
                        cb.like(cb.lower(root.<String>get("toIban")), ibanTerm, Like.ESCAPE),
                        cb.like(cb.lower(root.<String>get("reference")), term, Like.ESCAPE),
                        cb.like(cb.lower(root.<String>get("beneficiaryName")), term, Like.ESCAPE),
                        cb.like(cb.lower(root.<String>get("fromName")), term, Like.ESCAPE),
                        cb.and(cb.like(cb.lower(root.<String>get("toName")), term, Like.ESCAPE), payeeNameVisible)));
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
        return actor.id().equals(tx.getCreatedBy()) && actor.id().equals(tx.getToOwnerId());
    }

    private record Entry(PaymentTransaction transaction, EntrySide side) {}

    private List<TransactionView> view(UserPrincipal actor, List<Entry> entries) {
        Map<UUID, Map<UUID, BigDecimal>> balances = new HashMap<>();
        for (LedgerEntry entry : ledger.postingsOf(entries.stream().map(entry -> entry.transaction().getId()).distinct().toList())) {
            balances.computeIfAbsent(entry.getTransactionId(), id -> new HashMap<>())
                    .put(entry.getAccountId(), entry.getBalanceAfter());
        }
        return entries.stream().map(entry -> {
            PaymentTransaction tx = entry.transaction();
            boolean payer = actor.id().equals(tx.getCreatedBy());
            boolean payee = actor.id().equals(tx.getToOwnerId());
            boolean hideRegisteredName = tx.getNameCheck() == NameCheckResult.NO_MATCH && !payee;
            Map<UUID, BigDecimal> postings = balances.getOrDefault(tx.getId(), Map.of());
            return new TransactionView(tx, tx.getFromName(),
                    hideRegisteredName ? tx.getBeneficiaryName() : tx.getToName(),
                    payer ? postings.get(tx.getFromAccountId()) : null,
                    payee ? postings.get(tx.getToAccountId()) : null, entry.side());
        }).toList();
    }
}
