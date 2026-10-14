package lt.baltic.gateway.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, unique = true) private String iban;
    @Column(nullable = false) private String currency;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal balance;

    protected Account() {}
    public Account(UUID id, UUID ownerId, String iban, String currency, BigDecimal balance) {
        this.id = id;
        this.ownerId = ownerId;
        this.iban = iban;
        this.currency = currency;
        this.balance = balance;
    }
    public void debit(BigDecimal amount) { balance = balance.subtract(amount); }
    public void credit(BigDecimal amount) { balance = balance.add(amount); }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getIban() { return iban; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
}
