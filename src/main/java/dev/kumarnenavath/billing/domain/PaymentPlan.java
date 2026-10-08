package dev.kumarnenavath.billing.domain;

/**
 * How a policy's premium is split into invoices.
 * Down payment is a percentage of premium billed on the first invoice;
 * the rest is spread evenly over the remaining installments, each carrying a flat installment fee.
 */
public enum PaymentPlan {
    FULL_PAY(1, 100, 0, 12),
    QUARTERLY(4, 25, 500, 3),
    MONTHLY(10, 20, 300, 1);

    private final int installments;
    private final int downPaymentPercent;
    private final long installmentFeeCents;
    private final int monthsBetweenInstallments;

    PaymentPlan(int installments, int downPaymentPercent, long installmentFeeCents, int monthsBetweenInstallments) {
        this.installments = installments;
        this.downPaymentPercent = downPaymentPercent;
        this.installmentFeeCents = installmentFeeCents;
        this.monthsBetweenInstallments = monthsBetweenInstallments;
    }

    public int installments() { return installments; }
    public int downPaymentPercent() { return downPaymentPercent; }
    public long installmentFeeCents() { return installmentFeeCents; }
    public int monthsBetweenInstallments() { return monthsBetweenInstallments; }
}
