package dev.kumarnenavath.billing.repository;

import dev.kumarnenavath.billing.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByAccountIdOrderByDueDateAscSequenceAsc(Long accountId);

    List<Invoice> findByAccountIdAndStatusNotOrderByDueDateAscSequenceAsc(Long accountId, Invoice.Status status);

    List<Invoice> findByStatusNot(Invoice.Status status);
}
