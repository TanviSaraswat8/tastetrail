package com.tastetrail.payment;

/** Cash on delivery: nothing is collected until the rider hands over the food. */
public class CashOnDelivery implements PaymentMethod {
    @Override public String getCode() { return "COD"; }
    @Override public String getDisplayName() { return "Cash on delivery"; }
    @Override public String statusWhenPlaced() { return "PENDING"; }
    @Override public String statusWhenCancelled() { return "PENDING"; }
}
