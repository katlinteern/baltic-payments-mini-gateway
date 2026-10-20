package com.baltic.gateway.payments.domain.payee;

/** Outcome of comparing the beneficiary name typed by the payer with the name held on the destination account. */
public enum NameCheckResult { MATCH, CLOSE_MATCH, NO_MATCH, NOT_AVAILABLE }
