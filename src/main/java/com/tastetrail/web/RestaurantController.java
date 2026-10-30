package com.tastetrail.web;

import com.tastetrail.coupon.Coupon;
import com.tastetrail.db.Db;
import com.tastetrail.model.Bill;
import com.tastetrail.model.Cart;
import com.tastetrail.model.MenuItem;
import com.tastetrail.service.Recommender;

import java.util.*;
import java.util.stream.Collectors;

import static com.tastetrail.web.AppContext.*;

/** Browsing: restaurants, menus, coupons, popular dishes, cart bill and "ordered together" suggestions. */
public class RestaurantController implements Controller {
    private static final String RESTAURANT_SELECT = """
        SELECT r.id, r.name, r.cuisine, r.area, r.distance_km, r.prep_minutes, r.cost_for_two, r.is_pure_veg, r.is_open, r.theme,
               AVG(CAST(rv.rating AS DOUBLE)) AS rating, COUNT(rv.id) AS rating_count
        FROM restaurants r LEFT JOIN reviews rv ON rv.restaurant_id = r.id""";
    private static final String RESTAURANT_GROUP = " GROUP BY r.id, r.name, r.cuisine, r.area, r.distance_km, r.prep_minutes, r.cost_for_two, r.is_pure_veg, r.is_open, r.theme";

    private final AppContext ctx;

    public RestaurantController(AppContext ctx) { this.ctx = ctx; }

    @Override public void register(Http.Router r) {
        r.get("/api/health", q -> map("ok", true, "orders", Db.one("SELECT COUNT(*) AS n FROM orders").get("n")));
        r.get("/api/restaurants", this::list);
        r.get("/api/restaurants/:id", this::detail);
        r.get("/api/coupons", q -> Db.query("SELECT code, description, min_order FROM coupons WHERE active = TRUE ORDER BY min_order"));
        r.get("/api/popular-dishes", this::popularDishes);
        r.post("/api/cart/quote", this::quote);
        r.get("/api/recommendations", this::recommendations);
    }

    private static void roundRating(Map<String, Object> r) {
        Object v = r.get("rating");
        r.put("rating", v == null ? null : Math.round(d(v) * 10) / 10.0);
    }

    private Object list(Http.Req q) {
        String term = q.q("q", "").toLowerCase(Locale.ROOT).trim();
        String cuisine = q.q("cuisine", "").toLowerCase(Locale.ROOT);
        boolean veg = "1".equals(q.q("veg", ""));
        Map<Integer, Integer> orderCount = new HashMap<>();
        for (var c : Db.query("SELECT restaurant_id, COUNT(*) AS n FROM orders WHERE status <> 'CANCELLED' GROUP BY restaurant_id"))
            orderCount.put(i(c.get("restaurant_id")), i(c.get("n")));
        Set<Integer> dishHits = new HashSet<>();
        if (!term.isEmpty())
            for (var h : Db.query("SELECT DISTINCT restaurant_id FROM menu_items WHERE LOWER(name) LIKE ?", "%" + term + "%")) dishHits.add(i(h.get("restaurant_id")));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : Db.query(RESTAURANT_SELECT + RESTAURANT_GROUP)) {
            String name = ((String) r.get("name")).toLowerCase(Locale.ROOT), cu = ((String) r.get("cuisine")).toLowerCase(Locale.ROOT);
            if (!term.isEmpty() && !name.contains(term) && !cu.contains(term) && !dishHits.contains(i(r.get("id")))) continue;
            if (!cuisine.isEmpty() && !cu.contains(cuisine)) continue;
            if (veg && !Boolean.TRUE.equals(r.get("is_pure_veg"))) continue;
            roundRating(r);
            r.put("orders", orderCount.getOrDefault(i(r.get("id")), 0));
            r.put("eta_minutes", ctx.billing.estimateEtaMinutes(i(r.get("prep_minutes")), d(r.get("distance_km")), 1));
            out.add(r);
        }
        Comparator<Map<String, Object>> cmp = switch (q.q("sort", "popular")) {
            case "rating" -> Comparator.comparingDouble((Map<String, Object> m) -> m.get("rating") == null ? 0 : d(m.get("rating"))).reversed();
            case "fastest" -> Comparator.comparingInt(m -> i(m.get("eta_minutes")));
            case "cost" -> Comparator.comparingInt(m -> i(m.get("cost_for_two")));
            default -> Comparator.comparingInt((Map<String, Object> m) -> i(m.get("orders"))).reversed();
        };
        out.sort(cmp);
        return out;
    }

    private Object detail(Http.Req q) {
        Map<String, Object> r = Db.one(RESTAURANT_SELECT + " WHERE r.id = ?" + RESTAURANT_GROUP, q.param("id"));
        if (r == null) throw new ApiException.NotFound("Restaurant not found");
        roundRating(r);
        List<Map<String, Object>> items = Db.query("SELECT * FROM menu_items WHERE restaurant_id = ? ORDER BY category, id", r.get("id"));
        Set<Integer> ids = items.stream().map(m -> i(m.get("id"))).collect(Collectors.toSet());
        Set<Integer> best = ctx.popularity().recommend(List.of(), 3, ids).stream().map(Recommender.Suggestion::itemId).collect(Collectors.toSet());
        for (var it : items) it.put("bestseller", best.contains(i(it.get("id"))));
        r.put("items", items);
        r.put("reviews", Db.query("""
            SELECT rv.rating, rv.comment, rv.created_at, u.name FROM reviews rv JOIN users u ON u.id = rv.user_id
            WHERE rv.restaurant_id = ? AND rv.comment IS NOT NULL ORDER BY rv.created_at DESC FETCH FIRST 4 ROWS ONLY""", r.get("id")));
        return r;
    }

    private Object popularDishes(Http.Req q) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var s : ctx.popularity().recommend(List.of(), 8, null)) {
            Map<String, Object> m = Db.one("SELECT mi.*, r.name AS restaurant FROM menu_items mi JOIN restaurants r ON r.id = mi.restaurant_id WHERE mi.id = ?", s.itemId());
            m.put("orders", (int) s.score());
            out.add(m);
        }
        return out;
    }

    private Object quote(Http.Req q) {
        Cart cart = ctx.loadCart(q);
        String code = q.str("coupon");
        Coupon coupon = ctx.findCoupon(code);
        Bill bill = ctx.billing.calculate(cart, coupon);
        if (code != null && !code.isEmpty() && coupon == null) bill = bill.withCouponError("Invalid coupon code");
        return bill; // Gson turns the Bill object into JSON
    }

    private Object recommendations(Http.Req q) {
        int rid = Integer.parseInt(q.q("restaurantId", "0"));
        List<Integer> ids = new ArrayList<>();
        for (String s : q.q("items", "").split(",")) if (!s.isBlank()) try { ids.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) { }
        Map<Integer, MenuItem> available = new LinkedHashMap<>();
        for (MenuItem m : ctx.menuItems.findAvailable(rid)) available.put(m.getId(), m);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Recommender.Suggestion s : ctx.cartRecommender().recommend(ids, 3, available.keySet())) {
            MenuItem m = available.get(s.itemId());
            String because = s.becauseItemId() == null ? null : ctx.menuItems.findById(s.becauseItemId()).map(MenuItem::getName).orElse(null);
            out.add(map("id", m.getId(), "restaurant_id", m.getRestaurantId(), "name", m.getName(), "description", m.getDescription(),
                "category", m.getCategory(), "price", m.getPrice(), "is_veg", m.isVeg(), "is_available", m.isAvailable(), "emoji", m.getEmoji(),
                "confidence", s.confidence(), "lift", s.lift(), "reason", s.reason(), "because", because));
        }
        return out;
    }
}
