package com.tastetrail.service;

import com.tastetrail.coupon.Coupon;
import com.tastetrail.model.Bill;
import com.tastetrail.model.Cart;
import com.tastetrail.model.CartItem;

/**
 * Calculates the bill for a cart. The pricing rules are fields of the object, so a different
 * calculator (for example with a festival GST rate or free delivery) can be created without
 * changing any code. No database access – easy to unit-test.
 */
public class BillCalculator {
    private final double gstRate;           // 5% GST on food, after the discount
    private final double packagingFee;      // flat per order
    private final double baseDeliveryFee;   // covers the first freeDeliveryKm km
    private final double freeDeliveryKm;
    private final double perKmFee;          // for every started km beyond freeDeliveryKm
    private final double freeDeliveryAbove; // free delivery when the food value reaches this

    /** The rules TasteTrail uses. */
    public static final BillCalculator STANDARD = new BillCalculator(0.05, 15, 30, 3, 8, 499);

    public BillCalculator(double gstRate, double packagingFee, double baseDeliveryFee, double freeDeliveryKm, double perKmFee, double freeDeliveryAbove) {
        this.gstRate = gstRate;
        this.packagingFee = packagingFee;
        this.baseDeliveryFee = baseDeliveryFee;
        this.freeDeliveryKm = freeDeliveryKm;
        this.perKmFee = perKmFee;
        this.freeDeliveryAbove = freeDeliveryAbove;
    }

    public static double round2(double n) { return Math.round((n + 1e-9) * 100) / 100.0; }

    public double deliveryFee(double distanceKm, double foodValue) {
        if (foodValue >= freeDeliveryAbove) return 0;
        double extraKm = Math.max(0, Math.ceil(distanceKm - freeDeliveryKm - 1e-9));
        return baseDeliveryFee + extraKm * perKmFee;
    }

    /** Amount payable for a given food value (no coupon) – used by the Feast Planner to stay inside a budget. */
    public double totalFor(double foodValue, double distanceKm) {
        return round2(foodValue + round2(foodValue * gstRate) + deliveryFee(distanceKm, foodValue) + packagingFee);
    }

    /** Preparation + 2 min per item above three + travel at 20 km/h + 5 min hand-over. */
    public int estimateEtaMinutes(int prepMinutes, double distanceKm, int itemCount) {
        int extraPrep = Math.max(0, itemCount - 3) * 2;
        return (int) Math.round(prepMinutes + extraPrep + (distanceKm / 20.0) * 60 + 5);
    }

    /** OVERLOADED method: bill without a coupon. */
    public Bill calculate(Cart cart) { return calculate(cart, null); }

    /** OVERLOADED method: bill with an optional coupon. */
    public Bill calculate(Cart cart, Coupon coupon) {
        if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("Your cart is empty");
        for (CartItem item : cart.getItems())
            if (!item.hasValidQuantity()) throw new IllegalArgumentException("Invalid quantity for " + item.getName());

        double subtotal = round2(cart.getSubtotal());
        Coupon.Result c = coupon == null ? new Coupon.Result(0, null) : coupon.apply(subtotal); // polymorphic call
        double foodValue = round2(subtotal - c.discount());
        double delivery = deliveryFee(cart.getRestaurant().getDistanceKm(), foodValue);
        double tax = round2(foodValue * gstRate);
        double total = round2(foodValue + tax + delivery + packagingFee);
        int itemCount = cart.getItemCount();
        int eta = estimateEtaMinutes(cart.getRestaurant().getPrepMinutes(), cart.getRestaurant().getDistanceKm(), itemCount);
        double gap = delivery > 0 ? round2(freeDeliveryAbove - foodValue) : 0.0;
        String applied = coupon != null && c.isApplied() ? coupon.getCode() : null;
        return new Bill(subtotal, c.discount(), c.error(), applied, delivery, packagingFee, tax, total, itemCount, eta, gap);
    }
}
