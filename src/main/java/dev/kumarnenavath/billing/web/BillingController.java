package dev.kumarnenavath.billing.web;

import dev.kumarnenavath.billing.domain.BillingAccount;
import dev.kumarnenavath.billing.domain.Invoice;
import dev.kumarnenavath.billing.domain.PaymentPlan;
import dev.kumarnenavath.billing.service.BillingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class BillingController {

    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    public record OpenAccountRequest(@NotBlank String accountName) {}
    public record BillPolicyRequest(@NotBlank String policyNumber, @Positive long premiumCents,
                                    @NotNull PaymentPlan plan, @NotNull LocalDate effectiveDate) {}
    public record PaymentRequest(@Positive long amountCents) {}

    public record AccountView(long id, String accountName, String status, long creditCents) {
        static AccountView of(BillingAccount a) {
            return new AccountView(a.getId(), a.getAccountName(), a.getStatus().name(), a.getCreditCents());
        }
    }

    public record InvoiceView(long id, String policyNumber, int sequence, LocalDate dueDate,
                              long premiumCents, long feeCents, long paidCents, long outstandingCents, String status) {
        static InvoiceView of(Invoice i) {
            return new InvoiceView(i.getId(), i.getPolicyNumber(), i.getSequence(), i.getDueDate(), i.getPremiumCents(),
                    i.getFeeCents(), i.getPaidCents(), i.outstandingCents(), i.getStatus().name());
        }
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView openAccount(@Valid @RequestBody OpenAccountRequest req) {
        return AccountView.of(billing.openAccount(req.accountName()));
    }

    @GetMapping("/accounts/{id}")
    public AccountView account(@PathVariable long id) {
        return AccountView.of(billing.getAccount(id));
    }

    @PostMapping("/accounts/{id}/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public List<InvoiceView> billPolicy(@PathVariable long id, @Valid @RequestBody BillPolicyRequest req) {
        return billing.billPolicy(id, req.policyNumber(), req.premiumCents(), req.plan(), req.effectiveDate())
                .stream().map(InvoiceView::of).toList();
    }

    @GetMapping("/accounts/{id}/invoices")
    public List<InvoiceView> invoices(@PathVariable long id) {
        return billing.invoicesFor(id).stream().map(InvoiceView::of).toList();
    }

    @PostMapping("/accounts/{id}/payments")
    public AccountView pay(@PathVariable long id, @Valid @RequestBody PaymentRequest req) {
        return AccountView.of(billing.receivePayment(id, req.amountCents()));
    }

    @PostMapping("/delinquency/run")
    public Set<Long> runDelinquency(@RequestParam(required = false) LocalDate asOf) {
        return billing.runDelinquency(asOf != null ? asOf : LocalDate.now());
    }
}
