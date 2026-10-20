package com.baltic.gateway.accounts.domain.account;

import java.util.UUID;

public record AccountParty(UUID accountId, String iban, String holderName, UUID ownerId) {}
