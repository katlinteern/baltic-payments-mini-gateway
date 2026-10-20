package com.baltic.gateway.accounts.domain.account;

import java.util.UUID;

/** Immutable view of an account for other features, so they never hold the {@link Account} entity itself. */
public record AccountParty(UUID accountId, String iban, String holderName, UUID ownerId) {}
