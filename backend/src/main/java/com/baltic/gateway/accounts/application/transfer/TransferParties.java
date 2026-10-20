package com.baltic.gateway.accounts.application.transfer;

import com.baltic.gateway.accounts.domain.account.AccountParty;

public record TransferParties(AccountParty source, AccountParty target) {}
