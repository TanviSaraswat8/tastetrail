package com.tastetrail.web;

import com.tastetrail.coupon.Coupon;
import com.tastetrail.dao.*;
import com.tastetrail.db.Db;
import com.tastetrail.model.Cart;
import com.tastetrail.model.CartItem;
import com.tastetrail.model.MenuItem;
import com.tastetrail.model.Restaurant;
import com.tastetrail.security.PasswordHasher;
import com.tastetrail.security.TokenService;
import com.tastetrail.service.*;

import java.util.*;

/**
 * Holds the objects every controller needs (DAOs, services, security) so they are created once
 * and shared – the controllers receive this object in their constructor (dependency injection by hand).
 */
public class AppContext {
    public final UserDao users = new UserDao();
    public final RestaurantDao restaurants = new RestaurantDao();
    public final MenuItemDao menuItems = new MenuItemDao();
    public final CouponDao coupons = new CouponDao();
    public final OrderDao orders = new OrderDao();
    public final BillCalculator billing = BillCalculator.STANDARD;
    public final PasswordHasher passwords = new PasswordHasher(120_000);
    public final TokenService tokens;

    // the recommendation models are rebuilt lazily after orders change (thread-safe)
    private volatile AssociationRuleRecommender rules;

    public AppContext(TokenService tokens) { this.tokens = tokens; }

    public synchronized AssociationRuleRecommender rules() {
        if (rules == null) {
            Map<Integer, List<Integer>> baskets = new LinkedHashMap<>();
            for (var r : Db.query("SELECT oi.order_id, oi.menu_item_id FROM order_items oi JOIN orders o ON o.id = oi.order_id WHERE o.status <> 'CANCELLED'"))
                baskets.computeIfAbsent(i(r.get("order_id")), k -> new ArrayList<>()).add(i(r.get("menu_item_id")));
            rules = new AssociationRuleRecommender(baskets.values(), 5, 1.2);
        }
        return rules;
    }
    public PopularityRecommender popularity() { return new PopularityRecommender(rules().getItemCounts()); }
    /** Association rules first, popular dishes to fill the gaps – both behind the Recommender interface. */
    public Recommender cartRecommender() { return new HybridRecommender(rules(), popularity()); }
    public void ordersChanged() { rules = null; }

    // ---------- shared helpers ----------
    public static int i(Object o) { return ((Number) o).intValue(); }
    public static double d(Object o) { return ((Number) o).doubleValue(); }
    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int k = 0; k < kv.length; k += 2) m.put((String) kv[k], kv[k + 1]);
        return m;
    }

    /** Builds a Cart from the request. Prices always come from the database, never from the browser. */
    public Cart loadCart(Http.Req q) {
        Double rid = q.num("restaurantId");
        Restaurant r = rid == null ? null : restaurants.findById(rid.intValue()).orElse(null);
        if (r == null) throw new ApiException.NotFound("Restaurant not found");
        if (!r.isOpen()) throw new ApiException.BadRequest("This restaurant is closed right now");
        if (!(q.body.get("items") instanceof List<?> list) || list.isEmpty()) throw new ApiException.BadRequest("Your cart is empty");
        Cart cart = new Cart(r);
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> it) || !(it.get("id") instanceof Number idNum)) throw new ApiException.BadRequest("An item in your cart is not on this menu");
            MenuItem m = menuItems.findInRestaurant(idNum.intValue(), r.getId())
                .orElseThrow(() -> new ApiException.BadRequest("An item in your cart is not on this menu"));
            if (!m.isAvailable()) throw new ApiException.BadRequest(m.getName() + " is currently unavailable");
            double qty = it.get("quantity") instanceof Number n ? n.doubleValue() : 0;
            if (qty != Math.rint(qty)) throw new ApiException.BadRequest("Invalid quantity for " + m.getName());
            cart.add(CartItem.of(m, (int) qty));
        }
        return cart;
    }

    public Coupon findCoupon(String code) { return coupons.findByCode(code).orElse(null); }

    /** Full order with items, review and customer, for the tracking page. */
    public Map<String, Object> orderDetail(int id) {
        Map<String, Object> o = Db.one("""
            SELECT o.*, r.name AS restaurant, r.area AS restaurant_area, r.theme FROM orders o
            JOIN restaurants r ON r.id = o.restaurant_id WHERE o.id = ?""", id);
        if (o == null) return null;
        o.put("items", Db.query("SELECT menu_item_id, name, price, quantity FROM order_items WHERE order_id = ? ORDER BY id", id));
        o.put("review", Db.one("SELECT rating, comment FROM reviews WHERE order_id = ?", id));
        o.put("customer", Db.one("SELECT name, phone FROM users WHERE id = ?", o.get("user_id")));
        return o;
    }

    /** Adds "2 × Butter Chicken, 1 × Dal Makhani" summaries to a list of orders. */
    public void addSummaries(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        Object[] ids = rows.stream().map(r -> r.get("id")).toArray();
        Map<Integer, List<String>> parts = new HashMap<>();
        for (var l : Db.query("SELECT order_id, quantity, name FROM order_items WHERE order_id IN (" + Db.placeholders(ids.length) + ") ORDER BY id", ids))
            parts.computeIfAbsent(i(l.get("order_id")), k -> new ArrayList<>()).add(l.get("quantity") + " × " + l.get("name"));
        for (var r : rows) r.put("summary", String.join(", ", parts.getOrDefault(i(r.get("id")), List.of())));
    }
}
