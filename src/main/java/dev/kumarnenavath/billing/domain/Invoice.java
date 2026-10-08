package dev.kumarnenavath.billing.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(indexes = @Index(name = "idx_invoice_account_due", columnList = "account_id, dueDate"))
public class Invoice {

    public enum Status { OPEN, PARTIALLY_PAID, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private BillingAccount account;

    @Column(nullable = false)
    private String policyNumber;

    private int sequence;

    @Column(nullable = false)
    private LocalDate dueDate;

    private long premiumCents;
    private long feeCents;
    private long paidCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    protected Invoice() {}

    public Invoice(BillingAccount account, String policyNumber, int sequence, LocalDate dueDate, long premiumCents, long feeCents) {
        this.account = account;
        this.policyNumber = policyNumber;
        this.sequence = sequence;
        this.dueDate = dueDate;
        this.premiumCents = premiumCents;
        this.feeCents = feeCents;
    }

    public long amountCents() { return premiumCents + feeCents; }
    public long outstandingCents() { return amountCents() - paidCents; }

    /** Applies up to {@code cents} to this invoice and returns how much was used. */
    public long applyPayment(long cents) {
        long applied = Math.min(cents, outstandingCents());
        paidCents += applied;
        status = outstandingCents() == 0 ? Status.PAID : (paidCents > 0 ? Status.PARTIALLY_PAID : Status.OPEN);
        return applied;
    }

    public boolean isPastDue(LocalDate asOf, int graceDays) {
        return outstandingCents() > 0 && dueDate.plusDays(graceDays).isBefore(asOf);
    }

    public Long getId() { return id; }
    public BillingAccount getAccount() { return account; }
    public String getPolicyNumber() { return policyNumber; }
    public int getSequence() { return sequence; }
    public LocalDate getDueDate() { return dueDate; }
    public long getPremiumCents() { return premiumCents; }
    public long getFeeCents() { return feeCents; }
    public long getPaidCents() { return paidCents; }
    public Status getStatus() { return status; }
}
