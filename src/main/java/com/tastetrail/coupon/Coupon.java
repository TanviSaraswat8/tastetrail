package com.tastetrail.coupon;

/**
 * ABSTRACT CLASS + TEMPLATE METHOD: every coupon shares the same checks (is it active? is the
 * minimum order reached? never more than the subtotal), but each kind of coupon calculates
 * its discount differently. Subclasses only override calculateDiscount() – POLYMORPHISM.
 */
public abstract class Coupon {
    private final String code;
    private final String description;
    private final double minOrder;
    private final boolean active;

    /** Result of applying a coupon: the discount, or the reason it could not be used. */
    public record Result(double discount, String error) {
        public boolean isApplied() { return error == null; }
    }

    protected Coupon(String code, String description, double minOrder, boolean active) {
        this.code = code;
        this.description = description;
        this.minOrder = minOrder;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
    public double getMinOrder() { return minOrder; }
    public boolean isActive() { return active; }

    /** "percent" or "flat" – stored in the coupons table. */
    public abstract String getType();

    /** The raw discount for a subtotal, before the shared rules are applied. */
    protected abstract double calculateDiscount(double subtotal);

    /** TEMPLATE METHOD: the same steps for every coupon; final so subclasses cannot skip the checks. */
    public final Result apply(double subtotal) {
        if (!active) return new Result(0, "This coupon is no longer active");
        if (subtotal < minOrder) return new Result(0, "Add items worth ₹" + money(round2(minOrder - subtotal)) + " more to use " + code);
        double d = Math.min(calculateDiscount(subtotal), subtotal);
        return new Result(round2(Math.max(0, d)), null);
    }

    /** FACTORY METHOD: creates the right subclass from a row of the coupons table. */
    public static Coupon create(String code, String type, double amount, Double maxDiscount, double minOrder, boolean active, String description) {
        return switch (type) {
            case "percent" -> new PercentCoupon(code, description, amount, maxDiscount, minOrder, active);
            case "flat" -> new FlatCoupon(code, description, amount, minOrder, active);
            default -> throw new IllegalArgumentException("Unknown coupon type: " + type);
        };
    }

    static double round2(double n) { return Math.round((n + 1e-9) * 100) / 100.0; }
    static String money(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }
}
