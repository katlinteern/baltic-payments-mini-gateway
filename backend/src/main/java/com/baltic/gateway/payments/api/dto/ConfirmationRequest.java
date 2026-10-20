package com.baltic.gateway.payments.api.dto;

/** The optional body of the confirmation request. */
public record ConfirmationRequest(boolean acknowledgeNameMismatch) {}
