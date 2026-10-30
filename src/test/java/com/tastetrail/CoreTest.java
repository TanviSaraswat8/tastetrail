package com.tastetrail;

import com.tastetrail.coupon.Coupon;
import com.tastetrail.coupon.FlatCoupon;
import com.tastetrail.coupon.PercentCoupon;
import com.tastetrail.model.*;
import com.tastetrail.payment.PaymentMethod;
import com.tastetrail.security.PasswordHasher;
import com.tastetrail.security.TokenService;
import com.tastetrail.service.*;
import com.tastetrail.explore.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Unit tests for the pure business-logic classes. A tiny built-in runner is used so the project
 * needs no test framework:  java -cp "out:lib/*" com.tastetrail.CoreTest
 */
public final class CoreTest {
    private static int passed = 0, failed = 0;

    interface Body { void run() throws Exception; }
    static void test(String name, Body b) {
        try { b.run(); passed++; System.out.println("  PASS  " + name); }
        catch (Throwable t) { failed++; System.out.println("  FAIL  " + name + "\n        " + t.getMessage()); }
    }
    static void eq(Object expected, Object actual) {
        if (expected instanceof Number e && actual instanceof Number a) {
            if (Math.abs(e.doubleValue() - a.doubleValue()) > 1e-9) throw new AssertionError("expected " + expected + " but was " + actual);
        } else if (!Objects.equals(expected, actual)) throw new AssertionError("expected " + expected + " but was " + actual);
    }
    static void ok(boolean cond, String msg) { if (!cond) throw new AssertionError(msg); }
    static void throwsMsg(Body b, String part) {
        try { b.run(); } catch (Exception e) { if (String.valueOf(e.getMessage()).contains(part)) return; throw new AssertionError("wrong message: " + e.getMessage()); }
        throw new AssertionError("expected an exception containing \"" + part + "\"");
    }
    /** Builds a cart: cart(km, prep, "name", qty, price, ...) */
    static Cart cart(double km, int prep, Object... nameQtyPrice) {
        Cart c = new Cart(new Restaurant("Test", km, prep));
        for (int i = 0; i < nameQtyPrice.length; i += 3)
            c.add(new CartItem(i, (String) nameQtyPrice[i], ((Number) nameQtyPrice[i + 2]).doubleValue(), (Integer) nameQtyPrice[i + 1]));
        return c;
    }

    static final BillCalculator BILL = BillCalculator.STANDARD;
    static final Coupon FLAT75 = new FlatCoupon("FLAT75", "Flat 75 off", 75, 399, true);
    static final Coupon WELCOME50 = new PercentCoupon("WELCOME50", "50% off", 50, 100.0, 199, true);

    public static void main(String[] args) {
        System.out.println("TasteTrail unit tests");

        test("bill: subtotal, GST, packaging and delivery fee add up", () -> {
            Bill b = BILL.calculate(cart(2.4, 25, "Biryani", 1, 299));
            eq(299.0, b.getSubtotal()); eq(14.95, b.getTax()); eq(30.0, b.getDeliveryFee());
            eq(15.0, b.getPackaging()); eq(358.95, b.getTotal()); eq(200.0, b.getFreeDeliveryGap());
        });

        test("delivery: free from Rs 499, Rs 8 per started km beyond 3 km", () -> {
            eq(0.0, BILL.deliveryFee(2.4, 499)); eq(30.0, BILL.deliveryFee(2.4, 498.99));
            eq(54.0, BILL.deliveryFee(5.2, 200)); eq(30.0, BILL.deliveryFee(3.0, 100));
        });

        test("coupons: minimum order, cap and inactive codes", () -> {
            eq(0.0, FLAT75.apply(398).discount());
            ok(FLAT75.apply(398).error().contains("more to use FLAT75"), "minimum-order message");
            eq(75.0, FLAT75.apply(400).discount());
            eq(0.0, WELCOME50.apply(150).discount());
            eq(100.0, WELCOME50.apply(320).discount()); // 50% of 320 = 160, capped at 100
            eq(0.0, new FlatCoupon("X", "", 75, 0, false).apply(900).discount());
        });

        test("coupon discount is applied before GST and the free-delivery check", () -> {
            Bill b = BILL.calculate(cart(3.1, 22, "Butter Chicken", 2, 329, "Naan", 3, 49), FLAT75);
            eq(805.0, b.getSubtotal()); eq(75.0, b.getDiscount()); eq(36.5, b.getTax());
            eq(0.0, b.getDeliveryFee()); eq(781.5, b.getTotal()); eq("FLAT75", b.getCouponApplied());
        });

        test("order #1507 from the report: Rs 1,021.95 and 42 min", () -> {
            Bill b = BILL.calculate(cart(3.1, 22, "Butter Chicken", 2, 329, "Dal Makhani", 1, 229, "Butter Naan", 3, 49), FLAT75);
            eq(1034.0, b.getSubtotal()); eq(47.95, b.getTax()); eq(1021.95, b.getTotal()); eq(42, b.getEtaMinutes());
        });

        test("invalid carts are rejected", () -> {
            throwsMsg(() -> BILL.calculate(cart(1, 10)), "empty");
            throwsMsg(() -> BILL.calculate(cart(1, 10, "X", 0, 10)), "Invalid quantity");
            throwsMsg(() -> BILL.calculate(cart(1, 10, "X", 21, 10)), "Invalid quantity");
            throwsMsg(() -> new MenuItem(1, 1, "Free", "", "Mains", 0, true, true, ""), "positive");
        });

        test("ETA = preparation + travel at 20 km/h + 5 min, longer for big orders", () -> {
            eq(37, BILL.estimateEtaMinutes(25, 2.4, 1));   // 25 + 7.2 + 5
            eq(45, BILL.estimateEtaMinutes(18, 5.2, 6));   // 18 + 6 + 15.6 + 5
        });

        // baskets: 1 = biryani, 2 = raita, 3 = coke, 4 = dessert
        List<List<Integer>> baskets = List.of(List.of(1, 2), List.of(1, 2), List.of(1, 2), List.of(1, 2, 3), List.of(1), List.of(3), List.of(3, 4), List.of(3), List.of(2, 1), List.of(4));

        test("association rules: support, confidence and lift", () -> {
            var m = new AssociationRuleRecommender(baskets, 1, 1.0);
            var r = m.getRulesFrom(1).stream().filter(x -> x.toItem() == 2).findFirst().orElseThrow();
            eq(5, r.count()); eq(0.5, r.support()); eq(5.0 / 6, r.confidence()); eq((5.0 / 6) / 0.5, r.lift());
        });

        test("recommend skips cart items and respects the allowed set and minimum lift", () -> {
            Recommender m = new AssociationRuleRecommender(baskets, 1, 1.0);
            var recs = m.recommend(List.of(1), 3, null);
            eq(2, recs.get(0).itemId());
            ok(recs.stream().noneMatch(s -> s.itemId() == 1), "cart item suggested");
            ok(m.recommend(List.of(1), 3, Set.of(4)).isEmpty(), "allowed set ignored");
            ok(new AssociationRuleRecommender(baskets, 1, 1.2).recommend(List.of(1), 3, null).stream().noneMatch(s -> s.itemId() == 3), "low-lift rule kept");
            var rules = new AssociationRuleRecommender(baskets, 1, 1.0);
            var pop = new PopularityRecommender(rules.getItemCounts()).recommend(List.of(), 2, null);
            eq(1, pop.get(0).itemId()); eq(2, pop.get(1).itemId());
        });

        test("duplicate items within an order count once", () -> {
            var m = new AssociationRuleRecommender(List.of(List.of(1, 1, 2), List.of(1, 2)), 1, 1.0);
            eq(2, m.getItemCounts().get(1)); eq(2, m.getRulesFrom(1).get(0).count());
        });

        List<Analytics.OrderRow> orders = List.of(
            new Analytics.OrderRow(1, 1, 1, 400, "UPI", "DELIVERED", Instant.parse("2026-10-01T07:30:00Z"), Instant.parse("2026-10-01T08:00:00Z")),
            new Analytics.OrderRow(2, 1, 1, 200, "COD", "DELIVERED", Instant.parse("2026-10-02T14:45:00Z"), Instant.parse("2026-10-02T15:25:00Z")),
            new Analytics.OrderRow(3, 2, 1, 300, "UPI", "CANCELLED", Instant.parse("2026-10-02T09:00:00Z"), null),
            new Analytics.OrderRow(4, 3, 1, 600, "CARD", "DELIVERED", Instant.parse("2026-10-03T08:00:00Z"), Instant.parse("2026-10-03T08:35:00Z")));

        test("KPIs exclude cancelled orders from revenue", () -> {
            var k = Analytics.kpis(orders);
            eq(3, k.get("orders")); eq(1200.0, k.get("revenue")); eq(400.0, k.get("avgOrderValue")); eq(25.0, k.get("cancellationRate"));
            eq(2, k.get("customers")); eq(50.0, k.get("repeatRate")); eq(35L, k.get("avgDeliveryMinutes"));
        });

        test("daily revenue uses IST calendar days and a 7-day moving average", () -> {
            var s = Analytics.dailyRevenue(orders, 3, LocalDate.parse("2026-10-03"));
            eq("2026-10-01", s.get(0).get("date")); eq(400.0, s.get(0).get("revenue"));
            eq(200.0, s.get(1).get("revenue")); // 14:45 UTC = 20:15 IST on 2 Oct
            eq(600.0, s.get(2).get("revenue")); eq(400.0, s.get(2).get("movingAvg"));
            var late = Analytics.dailyRevenue(List.of(new Analytics.OrderRow(9, 1, 1, 100, "UPI", "DELIVERED", Instant.parse("2026-10-01T20:00:00Z"), null)), 2, LocalDate.parse("2026-10-02"));
            eq(0.0, late.get(0).get("revenue")); eq(100.0, late.get(1).get("revenue")); // 20:00 UTC = 01:30 IST next day
        });

        test("linear forecast recovers a perfect trend", () -> {
            List<Map<String, Object>> series = new ArrayList<>();
            double[] ys = {100, 110, 120, 130};
            for (int i = 0; i < 4; i++) series.add(Map.of("date", "2026-10-0" + (i + 1), "revenue", ys[i]));
            var f = Analytics.linearForecast(series, 2);
            eq(10.0, f.get("slope")); eq(100.0, f.get("intercept")); eq(1.0, f.get("r2"));
            @SuppressWarnings("unchecked") var fc = (List<Map<String, Object>>) f.get("forecast");
            eq("2026-10-05", fc.get(0).get("date")); eq(140.0, fc.get(0).get("forecast")); eq(150.0, fc.get(1).get("forecast"));
        });

        test("hourly distribution, shares and rating average", () -> {
            int[] h = Analytics.hourlyDistribution(orders);
            eq(2, h[13]); eq(1, h[20]);
            var pay = Analytics.shareBy(orders.stream().filter(o -> !o.status().equals("CANCELLED")).toList(), Analytics.OrderRow::paymentMethod, o -> 1);
            eq(3.0, pay.stream().mapToDouble(p -> (double) p.get("value")).sum());
            var r = Analytics.ratingDistribution(List.of(5, 4, 4, 3));
            eq(4.0, r.get("average")); eq(2, ((int[]) r.get("dist"))[3]);
        });

        test("order status: valid transitions and cancellation window", () -> {
            ok(OrderStatus.PLACED.canMoveTo(OrderStatus.CONFIRMED), "PLACED→CONFIRMED");
            ok(OrderStatus.OUT_FOR_DELIVERY.canMoveTo(OrderStatus.DELIVERED), "OUT_FOR_DELIVERY→DELIVERED");
            ok(!OrderStatus.PLACED.canMoveTo(OrderStatus.DELIVERED), "PLACED→DELIVERED allowed");
            ok(!OrderStatus.DELIVERED.canMoveTo(OrderStatus.CANCELLED), "DELIVERED→CANCELLED allowed");
            ok(!OrderStatus.PREPARING.canMoveTo(OrderStatus.CANCELLED), "PREPARING→CANCELLED allowed");
            ok(OrderStatus.CONFIRMED.isCancellableByCustomer() && !OrderStatus.PREPARING.isCancellableByCustomer(), "cancel window");
            ok(OrderStatus.DELIVERED.isFinal() && OrderStatus.parse("nonsense") == null, "final / parse");
        });

        test("passwords are salted PBKDF2 hashes and verify correctly", () -> {
            PasswordHasher ph = new PasswordHasher(1000);
            String h1 = ph.hash("demo123"), h2 = ph.hash("demo123");
            ok(!h1.equals(h2), "same hash for two salts");
            ok(h1.startsWith("pbkdf2_sha256$1000$") && !h1.contains("demo123"), "hash format");
            ok(ph.verify("demo123", h1) && !ph.verify("demo124", h1), "verify");
        });

        test("tokens: valid signature accepted, tampered token rejected", () -> {
            TokenService ts = new TokenService("test-secret", 3600);
            String t = ts.issue(new Customer(7, "Tanvi", "t@x.in", null));
            User u = ts.verify(t);
            eq(7, u.getId()); eq("customer", u.getRole()); ok(u instanceof Customer, "token did not rebuild a Customer");
            String[] p = t.split("\\.");
            String forged = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"id\":1,\"role\":\"admin\",\"name\":\"x\",\"exp\":9999999999}".getBytes());
            ok(ts.verify(p[0] + "." + forged + "." + p[2]) == null, "forged role accepted");
            ok(ts.verify("garbage") == null, "garbage accepted");
            ok(new TokenService("other-secret", 3600).verify(t) == null, "token from another secret accepted");
        });

        // ---------- OOP behaviour ----------
        test("inheritance + polymorphism: User subclasses answer differently", () -> {
            List<User> people = List.of(User.create(1, "customer", "Tanvi", "a@b.in", null), User.create(2, "admin", "Ops", "c@d.in", null));
            ok(people.get(0) instanceof Customer && people.get(1) instanceof Admin, "factory built wrong classes");
            ok(!people.get(0).canManageRestaurants() && people.get(1).canManageRestaurants(), "canManageRestaurants");
            ok(people.get(0).canPlaceOrders() && !people.get(1).canPlaceOrders(), "canPlaceOrders");
            eq("admin", people.get(1).toJson().get("role"));
        });

        test("polymorphism: each coupon subclass calculates its own discount", () -> {
            List<Coupon> coupons = List.of(Coupon.create("A", "percent", 20, 150.0, 0, true, ""), Coupon.create("B", "flat", 75, null, 0, true, ""));
            ok(coupons.get(0) instanceof PercentCoupon && coupons.get(1) instanceof FlatCoupon, "factory built wrong classes");
            eq(150.0, coupons.get(0).apply(1000).discount()); // 20% = 200, capped at 150
            eq(75.0, coupons.get(1).apply(1000).discount());
            eq(50.0, coupons.get(1).apply(50).discount());    // never more than the subtotal
        });

        test("interface: payment methods decide payment status", () -> {
            PaymentMethod upi = PaymentMethod.of("UPI"), cod = PaymentMethod.of("COD");
            eq("PAID", upi.statusWhenPlaced()); eq("REFUNDED", upi.statusWhenCancelled());
            eq("PENDING", cod.statusWhenPlaced()); eq("PAID", cod.statusWhenDelivered()); eq("PENDING", cod.statusWhenCancelled());
            ok(PaymentMethod.of("BITCOIN") == null, "unknown method accepted");
        });

        test("composition: hybrid recommender fills gaps with popular dishes", () -> {
            var rules = new AssociationRuleRecommender(baskets, 1, 1.2);
            Recommender hybrid = new HybridRecommender(rules, new PopularityRecommender(rules.getItemCounts()));
            var recs = hybrid.recommend(List.of(1), 3, null);
            eq(3, recs.size());
            eq("frequently ordered together", recs.get(0).reason()); eq("popular at this restaurant", recs.get(2).reason());
            ok(recs.stream().map(Recommender.Suggestion::itemId).distinct().count() == 3 && recs.stream().noneMatch(s -> s.itemId() == 1), "duplicates or cart item");
        });

        test("encapsulation: cart items cannot be changed from outside", () -> {
            Cart c = cart(1, 10, "A", 2, 100);
            throwsMsg(() -> c.getItems().clear(), "");
            eq(200.0, c.getSubtotal()); eq(2, c.getItemCount());
        });


        // ---------- TasteTrail-only features ----------
        java.util.function.BiFunction<String, Object[], Dish> dish = (name, a) -> new Dish((Integer) a[0], name, (String) a[1], ((Number) a[2]).doubleValue(),
            true, "x", 1, "R", "North Indian", 2.0, 15, (Integer) a[3]);
        List<Dish> menu = List.of(
            dish.apply("Dal", new Object[]{1, "Mains", 200, 10}), dish.apply("Paneer", new Object[]{2, "Mains", 280, 20}),
            dish.apply("Samosa", new Object[]{3, "Starters", 50, 5}), dish.apply("Lassi", new Object[]{4, "Beverages", 60, 1}));
        FeastPlanner planner = new FeastPlanner(BillCalculator.STANDARD);

        test("feast planner: final bill (with GST, packaging, delivery) stays within budget", () -> {
            FeastPlanner.Plan p = planner.plan(menu, 2, 700, false).orElseThrow();
            ok(p.total() <= 700, "total " + p.total());
            eq(BillCalculator.STANDARD.totalFor(p.food(), 2.0), p.total());
        });
        test("feast planner: everybody gets at least one full serving", () -> {
            FeastPlanner.Plan p = planner.plan(menu, 3, 900, false).orElseThrow();
            ok(p.servingsHalf() >= 6, "servings " + p.servingsHalf());
        });
        test("feast planner: impossible budget gives no plan", () -> ok(planner.plan(menu, 4, 300, false).isEmpty(), "expected empty"));
        test("feast planner: DP matches brute force on a small menu", () -> {
            FeastPlanner.Plan p = planner.plan(menu, 2, 800, false).orElseThrow();
            double best = -1; int maxO = 20;
            for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) for (int c = 0; c <= 2; c++) for (int d = 0; d <= 2; d++) {
                double food = 200 * a + 280 * b + 50 * c + 60 * d;
                if (2 * a + 2 * b + c < 4 || BillCalculator.STANDARD.totalFor(food, 2.0) > 800) continue;
                double v = 0; int[] q = {a, b, c, d};
                for (int k = 0; k < 4; k++) { double one = 0, val = new double[]{10, 10, 6, 4}[k] * (1 + 0.5 * new int[]{10, 20, 5, 1}[k] / maxO);
                    for (int r = 0; r < q[k]; r++) { one += val; val *= 0.7; } v += one; }
                best = Math.max(best, v);
            }
            eq(Math.round(best * 10) / 10.0, p.value());
        });
        test("bill split by items adds up exactly to the bill", () -> {
            Cart c = cart(2, 15, "Biryani", 1, 379, "Chai", 1, 49, "Raita", 2, 59);
            Bill bill = BillCalculator.STANDARD.calculate(c);
            List<CartItem> lines = c.getItems();
            Map<Integer, List<Integer>> who = Map.of(lines.get(0).getMenuItemId(), List.of(0), lines.get(1).getMenuItemId(), List.of(1));
            List<BillSplitter.Share> shares = new BillSplitter().split(bill, lines, List.of("A", "B"), who);
            eq(bill.getTotal(), BillCalculator.round2(shares.get(0).pay() + shares.get(1).pay()));
            ok(shares.get(0).pay() > shares.get(1).pay(), "biryani eater pays more");
        });
        test("bill split equally: rounding never loses a paisa", () -> {
            Cart c = cart(2, 15, "A", 1, 100);
            Bill bill = BillCalculator.STANDARD.calculate(c);
            List<BillSplitter.Share> s3 = new BillSplitter().split(bill, List.of("A", "B", "C"));
            eq(bill.getTotal(), BillCalculator.round2(s3.stream().mapToDouble(BillSplitter.Share::pay).sum()));
        });
        test("mood match: mild mode never suggests spicy dishes first", () -> {
            List<Dish> ds = List.of(dish.apply("Chilli Chicken", new Object[]{1, "Starters", 200, 50}), dish.apply("Dal Makhani", new Object[]{2, "Mains", 200, 10}));
            List<MoodMatcher.Match> m = new MoodMatcher(d -> 30).match(ds, MoodMatcher.Mood.COMFORT, 0, 500, false, Set.of(), 5);
            eq("Dal Makhani", m.get(0).dish().name());
        });
        test("taste passport: new cuisine gives bonus XP and a stamp", () -> {
            var places = List.of(new TastePassport.Place(1, "R", "North Indian"), new TastePassport.Place(2, "S", "Mexican"));
            var at = java.time.ZonedDateTime.of(2026, 10, 30, 21, 0, 0, 0, Analytics.IST);
            var res = new TastePassport().build(places, List.of(new TastePassport.PastOrder(1, at, menu, true), new TastePassport.PastOrder(1, at, menu, false)));
            eq(10 + 5 + 25 + 10, res.xp()); eq(1, res.stampsCollected()); eq("S", res.challenge().name());
        });

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }
}
