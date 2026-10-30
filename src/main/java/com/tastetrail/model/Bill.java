package com.tastetrail.model;

/**
 * IMMUTABLE value object: the full bill for a cart. It is built once by BillCalculator and then
 * only read. Gson turns these private fields straight into the JSON the cart page shows.
 */
public class Bill {
    private final double subtotal;
    private final double discount;
    private final String couponError;
    private final String couponApplied;
    private final double deliveryFee;
    private final double packaging;
    private final double tax;
    private final double total;
    private final int itemCount;
    private final int etaMinutes;
    private final double freeDeliveryGap;

    public Bill(double subtotal, double discount, String couponError, String couponApplied, double deliveryFee,
                double packaging, double tax, double total, int itemCount, int etaMinutes, double freeDeliveryGap) {
        this.subtotal = subtotal;
        this.discount = discount;
        this.couponError = couponError;
        this.couponApplied = couponApplied;
        this.deliveryFee = deliveryFee;
        this.packaging = packaging;
        this.tax = tax;
        this.total = total;
        this.itemCount = itemCount;
        this.etaMinutes = etaMinutes;
        this.freeDeliveryGap = freeDeliveryGap;
    }

    /** Returns a copy with a different coupon message (used when the code does not exist at all). */
    public Bill withCouponError(String message) {
        return new Bill(subtotal, discount, message, couponApplied, deliveryFee, packaging, tax, total, itemCount, etaMinutes, freeDeliveryGap);
    }

    public double getSubtotal() { return subtotal; }
    public double getDiscount() { return discount; }
    public String getCouponError() { return couponError; }
    public String getCouponApplied() { return couponApplied; }
    public double getDeliveryFee() { return deliveryFee; }
    public double getPackaging() { return packaging; }
    public double getTax() { return tax; }
    public double getTotal() { return total; }
    public int getItemCount() { return itemCount; }
    public int getEtaMinutes() { return etaMinutes; }
    public double getFreeDeliveryGap() { return freeDeliveryGap; }
}
