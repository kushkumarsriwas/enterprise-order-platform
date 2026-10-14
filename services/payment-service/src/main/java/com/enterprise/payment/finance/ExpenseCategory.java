package com.enterprise.payment.finance;

/**
 * Operating expense categories. Stock purchases are not here on purpose: they are recorded in the inventory
 * (restock at cost) and reach the profit &amp; loss statement as cost of goods sold when the items are sold.
 */
public enum ExpenseCategory {
    RENT("Rent"),
    SALARIES("Salaries and wages"),
    UTILITIES("Utilities"),
    MARKETING("Marketing and advertising"),
    SHIPPING("Shipping and delivery"),
    PACKAGING("Packaging"),
    SOFTWARE("Software and subscriptions"),
    GOVERNMENT_FEES("Licences and government fees"),
    BANK_CHARGES("Bank charges"),
    MAINTENANCE("Repairs and maintenance"),
    TRAVEL("Travel and transport"),
    OTHER("Other");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
