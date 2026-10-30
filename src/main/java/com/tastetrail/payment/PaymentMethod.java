package com.tastetrail.payment;

/**
 * INTERFACE: what every payment method must be able to tell the order system.
 * The order code only talks to this interface, so a new method (for example a wallet)
 * can be added without changing it – POLYMORPHISM through an interface.
 */
public interface PaymentMethod {
    /** Short code stored in the orders table: UPI, CARD or COD. */
    String getCode();

    /** Name shown to people. */
    String getDisplayName();

    /** Payment status right after the order is placed. */
    String statusWhenPlaced();

    /** Payment status after the food is delivered. Every method is paid by then (default method). */
    default String statusWhenDelivered() { return "PAID"; }

    /** Payment status after the order is cancelled. */
    String statusWhenCancelled();

    /** STATIC FACTORY: returns the object for a code, or null if the code is unknown. */
    static PaymentMethod of(String code) {
        if (code == null) return null;
        return switch (code) {
            case "UPI" -> new UpiPayment();
            case "CARD" -> new CardPayment();
            case "COD" -> new CashOnDelivery();
            default -> null;
        };
    }
}
