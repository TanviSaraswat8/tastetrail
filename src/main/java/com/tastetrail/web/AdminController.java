package com.tastetrail.web;

import com.tastetrail.db.Db;
import com.tastetrail.model.OrderStatus;
import com.tastetrail.payment.PaymentMethod;
import com.tastetrail.service.Analytics;
import com.tastetrail.service.AssociationRuleRecommender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.tastetrail.web.AppContext.*;

/** Restaurant operations: live order board, menu management and sales analytics. Admins only. */
public class AdminController implements Controller {
    private final AppContext ctx;

    public AdminController(AppContext ctx) { this.ctx = ctx; }

    @Override public void register(Http.Router r) {
        r.get("/api/admin/orders", this::activeOrders);
        r.patch("/api/admin/orders/:id/status", this::changeStatus);
        r.get("/api/admin/menu", this::menu);
        r.post("/api/admin/menu", this::addDish);
        r.patch("/api/admin/menu/:id", this::editDish);
        r.patch("/api/admin/restaurants/:id", this::openClose);
        r.get("/api/admin/analytics", this::analytics);
    }

    private Object activeOrders(Http.Req q) {
        q.requireAdmin();
        String filter = q.q("status", "ACTIVE");
        OrderStatus only = OrderStatus.parse(filter);
        String where = "ACTIVE".equals(filter) ? "o.status NOT IN ('DELIVERED','CANCELLED')" : only != null ? "o.status = ?" : "1=1";
        Object[] params = only != null ? new Object[]{only.name()} : new Object[]{};
        List<Map<String, Object>> rows = Db.query("""
            SELECT o.id, o.total, o.status, o.created_at, o.payment_method, o.payment_status, o.address, o.eta_minutes,
                   r.name AS restaurant, u.name AS customer
            FROM orders o JOIN restaurants r ON r.id = o.restaurant_id JOIN users u ON u.id = o.user_id
            """ + " WHERE " + where + " ORDER BY o.created_at DESC FETCH FIRST 60 ROWS ONLY", params);
        ctx.addSummaries(rows);
        return rows;
    }

    private Object changeStatus(Http.Req q) {
        q.requireAdmin();
        OrderStatus next = OrderStatus.parse(q.str("status"));
        Map<String, Object> o = ctx.orders.findRow(q.param("id"));
        if (o == null) throw new ApiException.NotFound("Order not found");
        OrderStatus current = OrderStatus.parse((String) o.get("status"));
        if (!current.canMoveTo(next)) throw new ApiException.BadRequest("Cannot move an order from " + current + " to " + q.str("status"));
        ctx.orders.changeStatus(i(o.get("id")), next, PaymentMethod.of((String) o.get("payment_method")));
        ctx.ordersChanged();
        return ctx.orderDetail(i(o.get("id")));
    }

    private Object menu(Http.Req q) {
        q.requireAdmin();
        int rid = Integer.parseInt(q.q("restaurantId", "1"));
        Map<Integer, Object> sold = new HashMap<>();
        for (var s : Db.query("""
            SELECT oi.menu_item_id AS id, SUM(oi.quantity) AS q FROM order_items oi JOIN orders o ON o.id = oi.order_id
            WHERE o.status <> 'CANCELLED' AND o.restaurant_id = ? GROUP BY oi.menu_item_id""", rid)) sold.put(i(s.get("id")), s.get("q"));
        List<Map<String, Object>> items = Db.query("SELECT * FROM menu_items WHERE restaurant_id = ? ORDER BY category, id", rid);
        for (var it : items) it.put("sold", sold.getOrDefault(i(it.get("id")), 0));
        return map("restaurants", Db.query("SELECT id, name, is_open FROM restaurants ORDER BY id"), "items", items);
    }

    private Object addDish(Http.Req q) {
        q.requireAdmin();
        String name = q.str("name"), category = q.str("category");
        Double price = q.num("price"), rid = q.num("restaurantId");
        if (name == null || name.isEmpty() || category == null || price == null || price <= 0 || rid == null)
            throw new ApiException.BadRequest("Name, category and a positive price are required");
        if (ctx.restaurants.findById(rid.intValue()).isEmpty()) throw new ApiException.NotFound("Restaurant not found");
        Boolean veg = q.bool("isVeg");
        String emoji = q.str("emoji");
        int id = ctx.menuItems.add(rid.intValue(), name, q.str("description"), category, price, veg == null || veg, emoji == null || emoji.isEmpty() ? "🍽️" : emoji);
        q.status(201);
        return Db.one("SELECT * FROM menu_items WHERE id = ?", id);
    }

    private Object editDish(Http.Req q) {
        q.requireAdmin();
        int id = q.param("id");
        if (ctx.menuItems.findById(id).isEmpty()) throw new ApiException.NotFound("Item not found");
        Double price = q.num("price");
        if (q.body.containsKey("price") && (price == null || price <= 0)) throw new ApiException.BadRequest("Price must be positive");
        if (price != null) ctx.menuItems.updatePrice(id, price);
        Boolean available = q.bool("isAvailable");
        if (available != null) ctx.menuItems.setAvailable(id, available);
        return Db.one("SELECT * FROM menu_items WHERE id = ?", id);
    }

    private Object openClose(Http.Req q) {
        q.requireAdmin();
        ctx.restaurants.setOpen(q.param("id"), Boolean.TRUE.equals(q.bool("isOpen")));
        return map("ok", true);
    }

    private Object analytics(Http.Req q) {
        q.requireAdmin();
        int days = Math.min(90, Math.max(7, Integer.parseInt(q.q("days", "30"))));
        LocalDate today = LocalDate.now(Analytics.IST);
        Instant since = today.minusDays(days - 1).atStartOfDay(Analytics.IST).toInstant(); // full IST days, plus today so far
        List<Analytics.OrderRow> orders = Db.query("SELECT id, user_id, restaurant_id, total, payment_method, status, created_at, delivered_at FROM orders WHERE created_at >= ?", since)
            .stream().map(r -> new Analytics.OrderRow(i(r.get("id")), i(r.get("user_id")), i(r.get("restaurant_id")), d(r.get("total")),
                (String) r.get("payment_method"), (String) r.get("status"), Instant.parse((String) r.get("created_at")),
                r.get("delivered_at") == null ? null : Instant.parse((String) r.get("delivered_at")))).toList();
        List<Analytics.LineRow> lines = Db.query("""
            SELECT oi.order_id, oi.name, oi.quantity, oi.price, mi.category FROM order_items oi JOIN orders o ON o.id = oi.order_id
            JOIN menu_items mi ON mi.id = oi.menu_item_id WHERE o.created_at >= ? AND o.status <> 'CANCELLED'""", since)
            .stream().map(r -> new Analytics.LineRow(i(r.get("order_id")), (String) r.get("name"), (String) r.get("category"), i(r.get("quantity")), d(r.get("price")))).toList();
        Map<Integer, String> restNames = new HashMap<>();
        ctx.restaurants.findAll().forEach(r -> restNames.put(r.getId(), r.getName()));
        List<Integer> ratings = Db.query("SELECT rating FROM reviews WHERE created_at >= ?", since).stream().map(x -> i(x.get("rating"))).toList();
        List<Map<String, Object>> daily = Analytics.dailyRevenue(orders, days, today);
        List<Map<String, Object>> history = daily.subList(0, daily.size() - 1); // today is incomplete: keep it out of the fit
        List<Analytics.OrderRow> valid = orders.stream().filter(o -> !"CANCELLED".equals(o.status())).toList();

        AssociationRuleRecommender model = ctx.rules();
        Map<Integer, String> itemNames = new HashMap<>();
        for (var x : Db.query("SELECT id, name FROM menu_items")) itemNames.put(i(x.get("id")), (String) x.get("name"));
        List<Map<String, Object>> rules = new ArrayList<>(model.getAllRules().stream().filter(rule -> rule.count() >= 20)
            .map(rule -> map("from", itemNames.get(rule.fromItem()), "to", itemNames.get(rule.toItem()), "count", rule.count(),
                "support", Math.round(rule.support() * 10000) / 100.0, "confidence", Math.round(rule.confidence() * 1000) / 10.0,
                "lift", Math.round(rule.lift() * 100) / 100.0)).toList());
        rules.sort((a, b) -> Double.compare(d(b.get("lift")), d(a.get("lift"))));
        int[] hourly = Analytics.hourlyDistribution(orders);
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) hours.add(map("hour", h, "orders", hourly[h]));

        return map("days", days, "kpis", Analytics.kpis(orders), "daily", daily, "forecast", Analytics.linearForecast(history, 7),
            "hourly", hours, "topItems", Analytics.topItems(lines, 8),
            "categories", Analytics.shareBy(lines, Analytics.LineRow::category, l -> l.quantity() * l.price()),
            "payments", Analytics.shareBy(valid, Analytics.OrderRow::paymentMethod, o -> 1),
            "restaurants", Analytics.shareBy(valid, o -> restNames.get(o.restaurantId()), Analytics.OrderRow::total),
            "ratings", Analytics.ratingDistribution(ratings), "rules", rules.subList(0, Math.min(8, rules.size())), "baskets", model.getBasketCount());
    }
}
