package com.baltic.gateway.payments.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import com.baltic.gateway.platform.validation.ValidCurrency;
import com.baltic.gateway.platform.validation.ValidIban;

public record PaymentRequest(
        @NotBlank(message = "Source account is required") @ValidIban String fromIban,
        @NotBlank(message = "Beneficiary account is required") @ValidIban String toIban,
        @NotBlank(message = "Beneficiary name is required") @Size(max = 120, message = "Beneficiary name must be at most 120 characters") String beneficiaryName,
        @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount can have up to 15 digits and 2 decimals") BigDecimal amount,
        @NotBlank(message = "Currency is required") @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter code") @ValidCurrency String currency,
        @Size(max = 140, message = "Reference must be at most 140 characters") String reference) {}
