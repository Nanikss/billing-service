package dev.kumarnenavath.billing.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure function that turns a premium + payment plan into a list of installments.
 * Amounts are in cents. Any rounding remainder goes on the down payment so the
 * installments always add up to exactly the premium.
 */
public final class InstallmentScheduler {

    public record Installment(int sequence, LocalDate dueDate, long premiumCents, long feeCents) {
        public long totalCents() { return premiumCents + feeCents; }
    }

    private InstallmentScheduler() {}

    public static List<Installment> schedule(long premiumCents, PaymentPlan plan, LocalDate effectiveDate) {
        if (premiumCents <= 0) {
            throw new IllegalArgumentException("Premium must be positive");
        }
        int n = plan.installments();
        long downPayment = premiumCents * plan.downPaymentPercent() / 100;
        long remaining = premiumCents - downPayment;
        long perInstallment = n > 1 ? remaining / (n - 1) : 0;
        long remainder = n > 1 ? remaining - perInstallment * (n - 1) : remaining;
        downPayment += remainder;

        List<Installment> result = new ArrayList<>(n);
        result.add(new Installment(1, effectiveDate, downPayment, 0));
        for (int i = 2; i <= n; i++) {
            LocalDate due = effectiveDate.plusMonths((long) plan.monthsBetweenInstallments() * (i - 1));
            result.add(new Installment(i, due, perInstallment, plan.installmentFeeCents()));
        }
        return result;
    }
}
