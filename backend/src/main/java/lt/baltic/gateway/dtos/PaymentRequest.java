package lt.baltic.gateway.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lt.baltic.gateway.validation.ValidIban;

public record PaymentRequest(
        @NotBlank @ValidIban String fromIban,
        @NotBlank @ValidIban String toIban,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 140) String reference) {}