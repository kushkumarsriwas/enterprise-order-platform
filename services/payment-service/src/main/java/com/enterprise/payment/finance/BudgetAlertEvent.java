package com.enterprise.payment.finance;

/** Raised when an expense pushes a category to 80% or past 100% of its monthly budget. */
public record BudgetAlertEvent(Long sellerId, String title, String message, String bearerToken) {
}
