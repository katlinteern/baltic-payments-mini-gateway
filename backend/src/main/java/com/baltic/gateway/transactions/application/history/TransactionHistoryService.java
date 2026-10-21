package com.baltic.gateway.transactions.application.history;

import com.baltic.gateway.accounts.application.ledger.LedgerEntryView;
import com.baltic.gateway.accounts.application.ledger.LedgerService;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.platform.paging.PageRequests;
import com.baltic.gateway.platform.persistence.Like;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.validation.Iban;
import com.baltic.gateway.transactions.domain.transaction.NameCheckResult;
import com.baltic.gateway.transactions.domain.transaction.Transaction;
import com.baltic.gateway.transactions.infrastructure.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {
    private final TransactionRepository repository;
    private final LedgerService ledger;

    public record Criteria(String text, Instant from, Instant to) {}

    public record SearchResult(List<TransactionView> rows, long totalElements, int page, int size) {}

    @Transactional(readOnly = true)
    public SearchResult search(UserPrincipal actor, Criteria criteria, int page, int size) {
        var pageable = PageRequests.of(page, size, Sort.unsorted());
        var sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        long offset = pageable.getOffset();
        int limit = (int) Math.min(offset + pageable.getPageSize(), Integer.MAX_VALUE);
        Specification<Transaction> matching = specification(actor, criteria);
        List<Transaction> payments = repository.findBy(
                matching, query -> query.sortBy(sort).limit(limit).all());
        List<HistoryRow> rows = payments.stream()
                .flatMap(tx -> isInternal(actor, tx)
                        ? Stream.of(new HistoryRow(tx, EntryDirection.DEBIT), new HistoryRow(tx, EntryDirection.CREDIT))
                        : Stream.of(new HistoryRow(tx, null)))
                .skip(offset)
                .limit(pageable.getPageSize())
                .toList();
        long total = repository.count(matching) + repository.count(matching.and(internal(actor)));
        return new SearchResult(view(actor, rows), total, pageable.getPageNumber(), pageable.getPageSize());
    }

    private static Specification<Transaction> internal(UserPrincipal actor) {
        return (root, query, cb) ->
                cb.and(cb.equal(root.get("createdBy"), actor.id()), cb.equal(root.get("toOwnerId"), actor.id()));
    }

    private static Specification<Transaction> specification(UserPrincipal actor, Criteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(
                    cb.or(cb.equal(root.get("createdBy"), actor.id()), cb.equal(root.get("toOwnerId"), actor.id())));
            if (criteria.text() != null && !criteria.text().isBlank()) {
                String text = criteria.text().trim();
                String term = Like.contains(text);
                String ibanTerm = Like.contains(Iban.normalize(text));
                Predicate payeeNameVisible = cb.or(
                        cb.notEqual(root.get("nameCheck"), NameCheckResult.NO_MATCH),
                        cb.equal(root.get("toOwnerId"), actor.id()));
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

    private static boolean isInternal(UserPrincipal actor, Transaction tx) {
        return actor.id().equals(tx.getCreatedBy()) && actor.id().equals(tx.getToOwnerId());
    }

    private record HistoryRow(Transaction transaction, EntryDirection side) {}

    private List<TransactionView> view(UserPrincipal actor, List<HistoryRow> rows) {
        Map<UUID, Map<UUID, BigDecimal>> balances = new HashMap<>();
        for (LedgerEntryView entry : ledger.entriesOf(
                rows.stream().map(row -> row.transaction().getId()).distinct().toList())) {
            balances.computeIfAbsent(entry.transactionId(), id -> new HashMap<>())
                    .put(entry.accountId(), entry.balanceAfter());
        }
        return rows.stream()
                .map(row -> {
                    Transaction tx = row.transaction();
                    boolean payer = actor.id().equals(tx.getCreatedBy());
                    boolean payee = actor.id().equals(tx.getToOwnerId());
                    boolean hideRegisteredName = tx.getNameCheck() == NameCheckResult.NO_MATCH && !payee;
                    Map<UUID, BigDecimal> balancesByAccount = balances.getOrDefault(tx.getId(), Map.of());
                    return new TransactionView(
                            tx,
                            tx.getFromName(),
                            hideRegisteredName ? tx.getBeneficiaryName() : tx.getToName(),
                            payer ? balancesByAccount.get(tx.getFromAccountId()) : null,
                            payee ? balancesByAccount.get(tx.getToAccountId()) : null,
                            row.side());
                })
                .toList();
    }
}
