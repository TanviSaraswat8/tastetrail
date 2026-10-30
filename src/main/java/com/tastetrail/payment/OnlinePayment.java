package com.tastetrail.payment;

/**
 * ABSTRACT CLASS implementing the interface: behaviour shared by all online payments.
 * Online payments are simulated in this prototype – they succeed immediately and are refunded on cancellation.
 */
public abstract class OnlinePayment implements PaymentMethod {
    @Override public String statusWhenPlaced() { return "PAID"; }
    @Override public String statusWhenCancelled() { return "REFUNDED"; }
}
