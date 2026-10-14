package com.enterprise.inventory.numbering;

/** Business documents that get a gap-free number per seller (PO-00001, GRN-00001, ...). */
public enum DocumentType {
    PURCHASE_ORDER("PO"),
    GOODS_RECEIPT("GRN"),
    STOCK_TRANSFER("TRF"),
    STOCK_COUNT("CNT");

    private final String prefix;

    DocumentType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
