package com.enterprise.payment.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    Optional<Expense> findByIdAndMerchantId(Long id, Long merchantId);

    List<Expense> findByMerchantIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(Long merchantId, LocalDate from,
                                                                                      LocalDate to);

    @Query("""
            select e.category, count(e), sum(e.amount)
            from Expense e
            where e.merchantId = :merchantId and e.expenseDate >= :from and e.expenseDate <= :to
            group by e.category
            order by sum(e.amount) desc
            """)
    List<Object[]> totalsByCategory(Long merchantId, LocalDate from, LocalDate to);

    @Query("""
            select year(e.expenseDate), month(e.expenseDate), sum(e.amount)
            from Expense e
            where e.merchantId = :merchantId and e.expenseDate >= :from and e.expenseDate <= :to
            group by year(e.expenseDate), month(e.expenseDate)
            """)
    List<Object[]> totalsByMonth(Long merchantId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(e.amount), 0)
            from Expense e
            where e.merchantId = :merchantId and e.category = :category
              and e.expenseDate >= :from and e.expenseDate <= :to
            """)
    BigDecimal total(Long merchantId, ExpenseCategory category, LocalDate from, LocalDate to);
}
