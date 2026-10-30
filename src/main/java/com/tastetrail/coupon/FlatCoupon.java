package com.tastetrail.coupon;

/** A fixed amount off, e.g. FLAT75 = ₹75 off on orders above ₹399. */
public class FlatCoupon extends Coupon {
    private final double amount;

    public FlatCoupon(String code, String description, double amount, double minOrder, boolean active) {
        super(code, description, minOrder, active);
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.amount = amount;
    }

    public double getAmount() { return amount; }

    @Override public String getType() { return "flat"; }

    @Override protected double calculateDiscount(double subtotal) { return amount; }
}
