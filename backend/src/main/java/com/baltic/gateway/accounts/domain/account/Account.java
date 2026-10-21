package com.baltic.gateway.accounts.domain.account;

import com.baltic.gateway.platform.money.Money;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Account {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, unique = true)
    private String iban;

    @Column(nullable = false)
    private String currency;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    public Money balance() {
        return Money.of(balance, currency);
    }

    public boolean canCover(Money amount) {
        return !balance().isLessThan(amount);
    }

    public void debit(Money amount) {
        requireOwnCurrency(amount);
        if (!canCover(amount)) throw new IllegalStateException("Insufficient funds on " + iban);
        balance = balance.subtract(amount.amount());
    }

    public void credit(Money amount) {
        requireOwnCurrency(amount);
        balance = balance.add(amount.amount());
    }

    private void requireOwnCurrency(Money amount) {
        if (!currency.equals(amount.currency())) {
            throw new IllegalArgumentException("Account " + iban + " is held in " + currency);
        }
    }

    public AccountParty party() {
        return new AccountParty(id, iban, holderName, ownerId);
    }
}
