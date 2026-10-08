package dev.kumarnenavath.billing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallmentSchedulerTest {

    private static final LocalDate EFF = LocalDate.of(2026, 1, 31);

    @ParameterizedTest
    @EnumSource(PaymentPlan.class)
    void installmentsAlwaysAddUpToExactlyThePremium(PaymentPlan plan) {
        for (long premium : new long[]{1, 99, 100_001, 1_234_567, 999_999_999}) {
            List<InstallmentScheduler.Installment> s = InstallmentScheduler.schedule(premium, plan, EFF);
            assertThat(s).hasSize(plan.installments());
            assertThat(s.stream().mapToLong(InstallmentScheduler.Installment::premiumCents).sum()).isEqualTo(premium);
        }
    }

    @Test
    void monthlyPlanHasDownPaymentThenEqualInstallmentsWithFees() {
        List<InstallmentScheduler.Installment> s = InstallmentScheduler.schedule(1_000_000, PaymentPlan.MONTHLY, EFF);
        assertThat(s.get(1).premiumCents()).isEqualTo(88_888);    // 800,000 / 9 rounded down
        assertThat(s.get(1).feeCents()).isEqualTo(300);
        assertThat(s.get(0).premiumCents()).isEqualTo(200_008);   // 20% down + the 8-cent rounding remainder
        assertThat(s.get(0).feeCents()).isZero();                 // no fee on the down payment
    }

    @Test
    void dueDatesClampToMonthEnd() {
        List<InstallmentScheduler.Installment> s = InstallmentScheduler.schedule(1_000_000, PaymentPlan.MONTHLY, EFF);
        assertThat(s.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28)); // Jan 31 + 1 month
        assertThat(s.get(2).dueDate()).isEqualTo(LocalDate.of(2026, 3, 31)); // computed from the effective date, not chained
    }

    @Test
    void fullPayIsOneInvoiceWithNoFee() {
        List<InstallmentScheduler.Installment> s = InstallmentScheduler.schedule(50_000, PaymentPlan.FULL_PAY, EFF);
        assertThat(s).singleElement().satisfies(i -> {
            assertThat(i.premiumCents()).isEqualTo(50_000);
            assertThat(i.feeCents()).isZero();
            assertThat(i.dueDate()).isEqualTo(EFF);
        });
    }

    @Test
    void rejectsNonPositivePremium() {
        assertThatThrownBy(() -> InstallmentScheduler.schedule(0, PaymentPlan.MONTHLY, EFF))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
