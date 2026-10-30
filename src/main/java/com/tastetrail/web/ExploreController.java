package com.tastetrail.web;

import com.tastetrail.db.Db;
import com.tastetrail.explore.*;
import com.tastetrail.model.Bill;
import com.tastetrail.model.Cart;
import com.tastetrail.model.User;
import com.tastetrail.service.Analytics;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

import static com.tastetrail.web.AppContext.*;

/** The TasteTrail-only features: Feast Planner, Mood Match, Taste Passport and Fair Bill Split. */
public class ExploreController implements Controller {
    private final AppContext ctx;
    private final FeastPlanner planner;
    private final MoodMatcher matcher;
    private final TastePassport passport = new TastePassport();
    private final BillSplitter splitter = new BillSplitter();

    public ExploreController(AppContext ctx) {
        this.ctx = ctx;
        this.planner = new FeastPlanner(ctx.billing);
        this.matcher = new MoodMatcher(d -> ctx.billing.estimateEtaMinutes(d.prepMinutes(), d.distanceKm(), 1));
    }

    @Override public void register(Http.Router r) {
        r.get("/api/explore/plan", this::plan);
        r.get("/api/explore/mood", this::mood);
        r.get("/api/me/passport", this::passport);
        r.post("/api/cart/split", this::split);
    }

    /** All available dishes of open restaurants, with how often each was ordered. */
    private List<Dish> dishes() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (var c : Db.query("SELECT menu_item_id, COUNT(*) AS n FROM order_items GROUP BY menu_item_id"))
            counts.put(i(c.get("menu_item_id")), i(c.get("n")));
        List<Dish> out = new ArrayList<>();
        for (var m : Db.query("""
            SELECT mi.id, mi.name, mi.category, mi.price, mi.is_veg, mi.emoji, r.id AS rid, r.name AS restaurant, r.cuisine,
                   r.distance_km, r.prep_minutes FROM menu_items mi JOIN restaurants r ON r.id = mi.restaurant_id
            WHERE mi.is_available = TRUE AND r.is_open = TRUE ORDER BY r.id, mi.id"""))
            out.add(new Dish(i(m.get("id")), (String) m.get("name"), (String) m.get("category"), d(m.get("price")),
                Boolean.TRUE.equals(m.get("is_veg")), (String) m.get("emoji"), i(m.get("rid")), (String) m.get("restaurant"),
                (String) m.get("cuisine"), d(m.get("distance_km")), i(m.get("prep_minutes")), counts.getOrDefault(i(m.get("id")), 0)));
        return out;
    }

    private static Map<String, Object> dishJson(Dish d) {
        return map("id", d.id(), "name", d.name(), "category", d.category(), "price", d.price(), "is_veg", d.veg(), "emoji", d.emoji(),
            "restaurant_id", d.restaurantId(), "restaurant", d.restaurant(), "cuisine", d.cuisine());
    }

    private Object plan(Http.Req q) {
        int people = Integer.parseInt(q.q("people", "2"));
        double budget = Double.parseDouble(q.q("budget", "600"));
        boolean veg = "1".equals(q.q("veg", ""));
        if (people < 1 || people > 12) throw new ApiException.BadRequest("Choose between 1 and 12 people");
        if (budget < 100 || budget > 20000) throw new ApiException.BadRequest("Budget must be between ₹100 and ₹20,000");
        Map<Integer, List<Dish>> menus = new LinkedHashMap<>();
        for (Dish d : dishes()) menus.computeIfAbsent(d.restaurantId(), k -> new ArrayList<>()).add(d);
        List<Map<String, Object>> out = new ArrayList<>();
        for (FeastPlanner.Plan p : planner.plan(menus, people, budget, veg, 3)) {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (FeastPlanner.Line l : p.lines()) { var m = dishJson(l.dish()); m.put("quantity", l.quantity()); lines.add(m); }
            out.add(map("restaurant_id", p.restaurantId(), "restaurant", p.restaurant(), "items", lines, "food", p.food(),
                "total", p.total(), "spare", p.spare(), "servings", p.servingsHalf() / 2.0, "value", p.value()));
        }
        return map("people", people, "budget", budget, "plans", out);
    }

    private Set<String> triedCuisines(User u) {
        Set<String> s = new HashSet<>();
        if (u == null) return s;
        for (var r : Db.query("SELECT DISTINCT r.cuisine FROM orders o JOIN restaurants r ON r.id = o.restaurant_id WHERE o.user_id = ? AND o.status <> 'CANCELLED'", u.getId()))
            s.add((String) r.get("cuisine"));
        return s;
    }

    private Object mood(Http.Req q) {
        MoodMatcher.Mood mood = MoodMatcher.Mood.of(q.q("mood", "comfort"));
        int spice = Math.max(0, Math.min(2, Integer.parseInt(q.q("spice", "1"))));
        double maxPrice = Double.parseDouble(q.q("budget", "400"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (MoodMatcher.Match m : matcher.match(dishes(), mood, spice, maxPrice, "1".equals(q.q("veg", "")), triedCuisines(q.user), 8)) {
            var j = dishJson(m.dish());
            j.put("score", m.score()); j.put("reasons", m.reasons()); j.put("eta_minutes", m.etaMinutes());
            out.add(j);
        }
        return map("mood", mood.label, "emoji", mood.emoji, "matches", out);
    }

    private Object passport(Http.Req q) {
        User u = q.requireUser();
        Map<Integer, Dish> byId = new HashMap<>();
        for (Dish d : dishes()) byId.put(d.id(), d);
        // newest brands first, so the "next challenge" points at something the customer has not seen
        List<TastePassport.Place> places = new ArrayList<>();
        for (var r : Db.query("SELECT id, name, cuisine FROM restaurants ORDER BY id DESC"))
            places.add(new TastePassport.Place(i(r.get("id")), (String) r.get("name"), (String) r.get("cuisine")));
        Map<Integer, List<Dish>> items = new HashMap<>();
        for (var l : Db.query("SELECT oi.order_id, oi.menu_item_id FROM order_items oi JOIN orders o ON o.id = oi.order_id WHERE o.user_id = ? AND o.status = 'DELIVERED'", u.getId())) {
            Dish d = byId.get(i(l.get("menu_item_id")));
            if (d != null) items.computeIfAbsent(i(l.get("order_id")), k -> new ArrayList<>()).add(d);
        }
        List<TastePassport.PastOrder> orders = new ArrayList<>();
        for (var o : Db.query("""
            SELECT o.id, o.restaurant_id, o.created_at, (SELECT COUNT(*) FROM reviews rv WHERE rv.order_id = o.id) AS rated
            FROM orders o WHERE o.user_id = ? AND o.status = 'DELIVERED' ORDER BY o.created_at""", u.getId())) {
            Object t = o.get("created_at");
            Instant at = t instanceof Timestamp ts ? ts.toInstant() : Instant.parse(String.valueOf(t));
            orders.add(new TastePassport.PastOrder(i(o.get("restaurant_id")), at.atZone(Analytics.IST), items.getOrDefault(i(o.get("id")), List.of()), i(o.get("rated")) > 0));
        }
        return passport.build(places, orders);
    }

    @SuppressWarnings("unchecked")
    private Object split(Http.Req q) {
        Cart cart = ctx.loadCart(q);
        Bill bill = ctx.billing.calculate(cart, ctx.findCoupon(q.str("coupon")));
        List<String> people = new ArrayList<>();
        if (q.body.get("people") instanceof List<?> list) for (Object o : list) if (o != null && !String.valueOf(o).isBlank()) people.add(String.valueOf(o).trim());
        if (people.size() < 2 || people.size() > 10) throw new ApiException.BadRequest("Split between 2 and 10 people");
        List<BillSplitter.Share> shares;
        if ("items".equals(q.str("mode")) && q.body.get("assign") instanceof Map<?, ?> raw) {
            Map<Integer, List<Integer>> assign = new HashMap<>();
            for (var e : ((Map<String, Object>) raw).entrySet())
                if (e.getValue() instanceof List<?> who)
                    assign.put(Integer.parseInt(e.getKey()), who.stream().filter(Number.class::isInstance).map(x -> ((Number) x).intValue()).toList());
            shares = splitter.split(bill, cart.getItems(), people, assign);
        } else shares = splitter.split(bill, people);
        return map("total", bill.getTotal(), "shares", shares);
    }
}
