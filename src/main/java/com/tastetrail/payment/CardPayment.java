package com.tastetrail.payment;

/** Debit/credit card payment (simulated). Inherits the paid/refunded behaviour from OnlinePayment. */
public class CardPayment extends OnlinePayment {
    @Override public String getCode() { return "CARD"; }
    @Override public String getDisplayName() { return "Card"; }
}
