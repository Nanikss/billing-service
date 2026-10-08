package dev.kumarnenavath.billing.repository;

import dev.kumarnenavath.billing.domain.BillingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingAccountRepository extends JpaRepository<BillingAccount, Long> {
}
