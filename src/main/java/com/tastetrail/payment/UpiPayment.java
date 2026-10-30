package com.tastetrail.payment;

/** UPI payment (simulated). Inherits the paid/refunded behaviour from OnlinePayment. */
public class UpiPayment extends OnlinePayment {
    @Override public String getCode() { return "UPI"; }
    @Override public String getDisplayName() { return "UPI"; }
}
