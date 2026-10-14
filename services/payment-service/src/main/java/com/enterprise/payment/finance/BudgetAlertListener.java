package com.enterprise.payment.finance;

import com.enterprise.payment.client.NotificationPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the alert only after the expense is committed, so a rolled-back expense never notifies anyone. */
@Component
class BudgetAlertListener {

    private final NotificationPublisher notifications;

    BudgetAlertListener(NotificationPublisher notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onBudgetAlert(BudgetAlertEvent event) {
        notifications.toUser(event.bearerToken(), event.merchantId(), "BUDGET_ALERT", event.title(), event.message());
    }
}
