package com.baltic.gateway.accounts.application.transfer;

import com.baltic.gateway.accounts.domain.account.AccountParty;

/** The two accounts of a transfer, locked for the rest of the current transaction. */
public record TransferParties(AccountParty source, AccountParty target) {}
