package dev.kumarnenavath.billing.domain;

import jakarta.persistence.*;

@Entity
public class BillingAccount {

    public enum Status { ACTIVE, DELINQUENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    /** Unapplied cash (overpayments) held on the account, in cents. */
    @Column(nullable = false)
    private long creditCents;

    @Version
    private long version;

    protected BillingAccount() {}

    public BillingAccount(String accountName) {
        this.accountName = accountName;
    }

    public Long getId() { return id; }
    public String getAccountName() { return accountName; }
    public Status getStatus() { return status; }
    public long getCreditCents() { return creditCents; }

    public void setStatus(Status status) { this.status = status; }
    public void addCredit(long cents) { this.creditCents += cents; }
    public void useCredit(long cents) {
        if (cents > creditCents) throw new IllegalStateException("Not enough credit");
        this.creditCents -= cents;
    }
}
