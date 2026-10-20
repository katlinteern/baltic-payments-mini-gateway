package com.baltic.gateway.accounts.application;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.Account;
import com.baltic.gateway.platform.persistence.Like;

/**
 * Lets other features filter by an account's registered holder name without loading account ids: the match stays a
 * sub-select, so its size does not depend on how many accounts match.
 */
public final class HolderNameSearch {
    private HolderNameSearch() {}

    /** Ids of the accounts whose registered holder name contains the text, ignoring case. */
    public static Subquery<UUID> accountIdsMatching(AbstractQuery<?> query, CriteriaBuilder cb, String text) {
        Subquery<UUID> ids = query.subquery(UUID.class);
        Root<Account> account = ids.from(Account.class);
        return ids.select(account.<UUID>get("id"))
                .where(cb.like(cb.lower(account.<String>get("holderName")), Like.contains(text.trim()), Like.ESCAPE));
    }
}
