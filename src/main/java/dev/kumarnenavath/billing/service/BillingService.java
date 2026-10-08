package dev.kumarnenavath.billing.service;

import dev.kumarnenavath.billing.domain.BillingAccount;
import dev.kumarnenavath.billing.domain.InstallmentScheduler;
import dev.kumarnenavath.billing.domain.Invoice;
import dev.kumarnenavath.billing.domain.PaymentPlan;
import dev.kumarnenavath.billing.repository.BillingAccountRepository;
import dev.kumarnenavath.billing.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BillingService {

    private final BillingAccountRepository accounts;
    private final InvoiceRepository invoices;
    private final int graceDays;

    public BillingService(BillingAccountRepository accounts,
                          InvoiceRepository invoices,
                          @Value("${billing.delinquency.grace-days:10}") int graceDays) {
        this.accounts = accounts;
        this.invoices = invoices;
        this.graceDays = graceDays;
    }

    @Transactional
    public BillingAccount openAccount(String accountName) {
        return accounts.save(new BillingAccount(accountName));
    }

    @Transactional(readOnly = true)
    public BillingAccount getAccount(long accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new NotFoundException("Account " + accountId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Invoice> invoicesFor(long accountId) {
        getAccount(accountId);
        return invoices.findByAccountIdOrderByDueDateAscSequenceAsc(accountId);
    }

    /** Bills a new policy term: creates one invoice per installment of the chosen plan. */
    @Transactional
    public List<Invoice> billPolicy(long accountId, String policyNumber, long premiumCents, PaymentPlan plan, LocalDate effectiveDate) {
        BillingAccount account = getAccount(accountId);
        List<Invoice> created = InstallmentScheduler.schedule(premiumCents, plan, effectiveDate).stream()
                .map(i -> new Invoice(account, policyNumber, i.sequence(), i.dueDate(), i.premiumCents(), i.feeCents()))
                .toList();
        invoices.saveAll(created);
        // Use any unapplied cash already sitting on the account.
        if (account.getCreditCents() > 0) {
            long credit = account.getCreditCents();
            account.useCredit(credit);
            account.addCredit(allocate(accountId, credit));
        }
        return created;
    }

    /**
     * Applies a payment to open invoices, oldest due date first. Anything left over is
     * kept as account credit. Returns the account so callers can see the remaining credit.
     */
    @Transactional
    public BillingAccount receivePayment(long accountId, long amountCents) {
        if (amountCents <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        BillingAccount account = getAccount(accountId);
        long leftover = allocate(accountId, amountCents);
        account.addCredit(leftover);
        if (account.getStatus() == BillingAccount.Status.DELINQUENT && !hasPastDue(accountId, LocalDate.now())) {
            account.setStatus(BillingAccount.Status.ACTIVE);
        }
        return account;
    }

    /**
     * Marks every account with an invoice unpaid beyond the grace period as delinquent,
     * and clears delinquency on accounts that have caught up. Returns the delinquent account ids.
     */
    @Transactional
    public Set<Long> runDelinquency(LocalDate asOf) {
        Set<Long> delinquent = new HashSet<>();
        for (Invoice inv : invoices.findByStatusNot(Invoice.Status.PAID)) {
            if (inv.isPastDue(asOf, graceDays)) delinquent.add(inv.getAccount().getId());
        }
        for (BillingAccount account : accounts.findAll()) {
            account.setStatus(delinquent.contains(account.getId()) ? BillingAccount.Status.DELINQUENT : BillingAccount.Status.ACTIVE);
        }
        return delinquent;
    }

    private long allocate(long accountId, long amountCents) {
        long remaining = amountCents;
        for (Invoice inv : invoices.findByAccountIdAndStatusNotOrderByDueDateAscSequenceAsc(accountId, Invoice.Status.PAID)) {
            if (remaining == 0) break;
            remaining -= inv.applyPayment(remaining);
        }
        return remaining;
    }

    private boolean hasPastDue(long accountId, LocalDate asOf) {
        return invoices.findByAccountIdAndStatusNotOrderByDueDateAscSequenceAsc(accountId, Invoice.Status.PAID).stream()
                .anyMatch(i -> i.isPastDue(asOf, graceDays));
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }
}
