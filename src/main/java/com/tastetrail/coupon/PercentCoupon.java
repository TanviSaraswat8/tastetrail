package com.tastetrail.coupon;

/** A percentage discount with an optional upper limit, e.g. WELCOME50 = 50% off up to ₹100. */
public class PercentCoupon extends Coupon {
    private final double percent;
    private final Double maxDiscount;

    public PercentCoupon(String code, String description, double percent, Double maxDiscount, double minOrder, boolean active) {
        super(code, description, minOrder, active);
        if (percent <= 0 || percent > 100) throw new IllegalArgumentException("Percent must be between 0 and 100");
        this.percent = percent;
        this.maxDiscount = maxDiscount;
    }

    public double getPercent() { return percent; }
    public Double getMaxDiscount() { return maxDiscount; }

    @Override public String getType() { return "percent"; }

    @Override protected double calculateDiscount(double subtotal) {
        double d = subtotal * percent / 100;
        return maxDiscount == null ? d : Math.min(d, maxDiscount);
    }
}
