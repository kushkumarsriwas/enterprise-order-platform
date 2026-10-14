package com.enterprise.payment.finance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByMerchantIdAndBudgetMonth(Long merchantId, String budgetMonth);

    Optional<Budget> findByMerchantIdAndBudgetMonthAndCategory(Long merchantId, String budgetMonth,
                                                               ExpenseCategory category);

    Optional<Budget> findByIdAndMerchantId(Long id, Long merchantId);
}
