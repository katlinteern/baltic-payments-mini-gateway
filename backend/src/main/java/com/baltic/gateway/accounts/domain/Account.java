package lt.baltic.gateway.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lt.baltic.gateway.platform.money.Money;

@Entity
@Table(name = "accounts")
public class Account {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, unique = true) private String iban;
    @Column(nullable = false) private String currency;
    @Column(name = "holder_name") private String holderName;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal balance;

    protected Account() {}

    public Account(UUID id, UUID ownerId, String iban, String currency, String holderName, BigDecimal balance) {
        this.id = id;
        this.ownerId = ownerId;
        this.iban = iban;
        this.currency = currency;
        this.holderName = holderName;
        this.balance = balance;
    }

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

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getIban() { return iban; }
    public String getCurrency() { return currency; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
}
