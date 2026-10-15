package com.enterprise.inventory.catalog;

/** Unit of measure a product is stocked and sold in. */
public enum Unit {
    PIECE("pc"),
    PACK("pack"),
    BOX("box"),
    CARTON("ctn"),
    PAIR("pair"),
    SET("set"),
    KG("kg"),
    GRAM("g"),
    LITRE("L"),
    METRE("m");

    private final String symbol;

    Unit(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
