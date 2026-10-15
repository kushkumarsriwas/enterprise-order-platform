package com.enterprise.payment.finance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findBySellerIdAndBudgetMonth(Long sellerId, String budgetMonth);

    Optional<Budget> findBySellerIdAndBudgetMonthAndCategory(Long sellerId, String budgetMonth,
                                                               ExpenseCategory category);

    Optional<Budget> findByIdAndSellerId(Long id, Long sellerId);
}
