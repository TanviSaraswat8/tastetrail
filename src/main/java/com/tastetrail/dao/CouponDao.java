package com.tastetrail.dao;

import com.tastetrail.coupon.Coupon;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reads the coupons table and builds PercentCoupon or FlatCoupon objects. */
public class CouponDao extends BaseDao<Coupon> {

    @Override protected Coupon map(Map<String, Object> r) {
        Object max = r.get("max_discount");
        return Coupon.create(strOf(r.get("code")), strOf(r.get("type")), doubleOf(r.get("amount")),
            max == null ? null : doubleOf(max), doubleOf(r.get("min_order")), boolOf(r.get("active")), strOf(r.get("description")));
    }

    public Optional<Coupon> findByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return findOne("SELECT * FROM coupons WHERE code = ?", code.trim().toUpperCase(Locale.ROOT));
    }
}
