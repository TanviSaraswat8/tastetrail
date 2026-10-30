package com.tastetrail.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * ENUM with behaviour: the order life cycle. Each constant knows its label and which
 * statuses it may move to, so the rules live in one place.
 *   PLACED → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
 *   PLACED / CONFIRMED → CANCELLED
 */
public enum OrderStatus {
    PLACED("Order placed"),
    CONFIRMED("Confirmed by restaurant"),
    PREPARING("Being prepared"),
    OUT_FOR_DELIVERY("Out for delivery"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    private final String label;

    OrderStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** The statuses this one is allowed to change to. */
    public Set<OrderStatus> nextStatuses() {
        return switch (this) {
            case PLACED -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(OUT_FOR_DELIVERY);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus next) { return next != null && nextStatuses().contains(next); }

    /** Customers may cancel only before the kitchen starts preparing. */
    public boolean isCancellableByCustomer() { return this == PLACED || this == CONFIRMED; }

    /** Delivered and cancelled orders are finished. */
    public boolean isFinal() { return nextStatuses().isEmpty(); }

    /** Safe conversion from text; returns null for unknown values instead of throwing. */
    public static OrderStatus parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
