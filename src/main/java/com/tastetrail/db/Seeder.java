package com.tastetrail.db;

import com.tastetrail.coupon.Coupon;
import com.tastetrail.model.Bill;
import com.tastetrail.model.Cart;
import com.tastetrail.model.CartItem;
import com.tastetrail.model.Restaurant;
import com.tastetrail.security.PasswordHasher;
import com.tastetrail.service.Analytics;
import com.tastetrail.service.BillCalculator;

import java.time.*;
import java.util.*;

import static com.tastetrail.db.Db.*;

/**
 * Fills the database with restaurants, menus, coupons, demo accounts and ~60 days of synthetic order
 * history. A fixed random seed (mulberry32) makes the data identical on every run.
 */
public final class Seeder {
    private Seeder() {}

    // ---------- deterministic random numbers (mulberry32) ----------
    private static int seed;
    static double rand() {
        seed += 0x6d2b79f5;
        int t = (seed ^ (seed >>> 15)) * (1 | seed);
        t = (t + (t ^ (t >>> 7)) * (61 | t)) ^ t;
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }
    static <T> T pick(List<T> items, double[] w) {
        double tot = 0; for (double x : w) tot += x;
        double r = rand() * tot;
        for (int i = 0; i < items.size(); i++) { r -= w[i]; if (r <= 0) return items.get(i); }
        return items.get(items.size() - 1);
    }

    record Item(String name, String category, double price, boolean veg, String emoji, double weight, String desc) {}
    record Pair(String a, String b, double p) {}
    static final class Rest {
        final String name, cuisine, area, theme; final double km; final int prep, costForTwo; final boolean veg;
        final List<Item> items; final List<Pair> pairs;
        int id; final Map<String, Integer> idOf = new HashMap<>(); final Map<Integer, Item> byId = new LinkedHashMap<>();
        Rest(String name, String cuisine, String area, double km, int prep, int cost, boolean veg, String theme, List<Item> items, List<Pair> pairs) {
            this.name = name; this.cuisine = cuisine; this.area = area; this.km = km; this.prep = prep; this.costForTwo = cost;
            this.veg = veg; this.theme = theme; this.items = items; this.pairs = pairs;
        }
    }
    static Item it(String n, String c, double p, boolean v, String e, double w, String d) { return new Item(n, c, p, v, e, w, d); }
    static Pair pr(String a, String b, double p) { return new Pair(a, b, p); }

    static final List<Rest> RESTAURANTS = List.of(
        new Rest("Biryani Bazaar", "Biryani · Mughlai", "Sector 18", 2.4, 25, 550, false, "amber", List.of(
            it("Chicken Dum Biryani", "Mains", 299, false, "🍗", 10, "Slow-cooked basmati rice layered with marinated chicken"),
            it("Veg Hyderabadi Biryani", "Mains", 229, true, "🍚", 5, "Fragrant rice with seasonal vegetables and saffron"),
            it("Mutton Biryani", "Mains", 379, false, "🍖", 4, "Tender mutton pieces in dum-cooked rice"),
            it("Chicken 65", "Starters", 249, false, "🍢", 4, "Spicy deep-fried chicken bites"),
            it("Burani Raita", "Sides", 59, true, "🥣", 1, "Garlic-tempered yoghurt"),
            it("Mirchi ka Salan", "Sides", 79, true, "🌶️", 1, "Chilli and peanut curry"),
            it("Double ka Meetha", "Desserts", 99, true, "🍮", 1, "Hyderabadi bread pudding"),
            it("Masala Chaas", "Beverages", 49, true, "🥛", 1, "Spiced buttermilk")),
            List.of(pr("Chicken Dum Biryani", "Burani Raita", 0.62), pr("Mutton Biryani", "Burani Raita", 0.55), pr("Veg Hyderabadi Biryani", "Mirchi ka Salan", 0.5),
                pr("Chicken Dum Biryani", "Chicken 65", 0.22), pr("Mutton Biryani", "Double ka Meetha", 0.35), pr("Chicken Dum Biryani", "Masala Chaas", 0.2))),
        new Rest("Pizza Planet", "Pizza · Italian", "Sector 62", 4.8, 20, 600, false, "red", List.of(
            it("Margherita Pizza", "Mains", 249, true, "🍕", 7, "Classic tomato, mozzarella and basil"),
            it("Farmhouse Pizza", "Mains", 349, true, "🍕", 6, "Capsicum, onion, mushroom and tomato"),
            it("Chicken Tikka Pizza", "Mains", 399, false, "🍕", 5, "Tandoori chicken tikka with onions"),
            it("Penne Arrabbiata", "Mains", 279, true, "🍝", 3, "Penne in spicy tomato sauce"),
            it("Garlic Breadsticks", "Sides", 129, true, "🥖", 1, "Buttery breadsticks with herbs"),
            it("Cheesy Dip", "Sides", 39, true, "🧀", 1, "Creamy cheese dip"),
            it("Coke (500 ml)", "Beverages", 60, true, "🥤", 1, "Chilled soft drink"),
            it("Choco Lava Cake", "Desserts", 109, true, "🍫", 1, "Warm cake with a molten centre")),
            List.of(pr("Margherita Pizza", "Garlic Breadsticks", 0.48), pr("Farmhouse Pizza", "Garlic Breadsticks", 0.45), pr("Chicken Tikka Pizza", "Coke (500 ml)", 0.5),
                pr("Margherita Pizza", "Coke (500 ml)", 0.35), pr("Farmhouse Pizza", "Choco Lava Cake", 0.3), pr("Garlic Breadsticks", "Cheesy Dip", 0.7), pr("Penne Arrabbiata", "Garlic Breadsticks", 0.4))),
        new Rest("Punjabi Tadka", "North Indian", "Sector 15", 3.1, 22, 500, false, "orange", List.of(
            it("Butter Chicken", "Mains", 329, false, "🍛", 8, "Chicken in a rich tomato-butter gravy"),
            it("Paneer Butter Masala", "Mains", 279, true, "🧈", 7, "Cottage cheese in creamy makhani gravy"),
            it("Dal Makhani", "Mains", 229, true, "🫘", 6, "Black lentils simmered overnight"),
            it("Chole Bhature", "Mains", 169, true, "🫓", 5, "Spiced chickpeas with fried bread"),
            it("Butter Naan", "Breads", 49, true, "🫓", 1, "Tandoor-baked naan with butter"),
            it("Jeera Rice", "Sides", 129, true, "🍚", 1, "Basmati rice tempered with cumin"),
            it("Sweet Lassi", "Beverages", 79, true, "🥛", 1, "Thick Punjabi lassi"),
            it("Gulab Jamun (2 pcs)", "Desserts", 69, true, "🟤", 1, "Warm milk dumplings in syrup")),
            List.of(pr("Butter Chicken", "Butter Naan", 0.75), pr("Paneer Butter Masala", "Butter Naan", 0.72), pr("Dal Makhani", "Jeera Rice", 0.55), pr("Dal Makhani", "Butter Naan", 0.4),
                pr("Chole Bhature", "Sweet Lassi", 0.45), pr("Butter Chicken", "Gulab Jamun (2 pcs)", 0.2))),
        new Rest("Dosa Junction", "South Indian", "Sector 50", 3.6, 15, 350, true, "lime", List.of(
            it("Masala Dosa", "Mains", 139, true, "🥞", 9, "Crisp dosa with potato masala"),
            it("Mysore Masala Dosa", "Mains", 159, true, "🥞", 5, "Dosa with spicy red chutney"),
            it("Idli Sambar (3 pcs)", "Mains", 99, true, "⚪", 6, "Steamed rice cakes with sambar"),
            it("Medu Vada (2 pcs)", "Starters", 89, true, "🍩", 4, "Crispy lentil doughnuts"),
            it("Uttapam", "Mains", 149, true, "🥘", 3, "Thick dosa topped with onion and tomato"),
            it("Filter Coffee", "Beverages", 49, true, "☕", 2, "South Indian decoction coffee"),
            it("Extra Sambar", "Sides", 39, true, "🥣", 1, "A bowl of lentil sambar"),
            it("Rava Kesari", "Desserts", 69, true, "🟧", 1, "Semolina sweet with ghee")),
            List.of(pr("Masala Dosa", "Filter Coffee", 0.55), pr("Idli Sambar (3 pcs)", "Medu Vada (2 pcs)", 0.5), pr("Mysore Masala Dosa", "Filter Coffee", 0.45),
                pr("Idli Sambar (3 pcs)", "Filter Coffee", 0.35), pr("Masala Dosa", "Extra Sambar", 0.25))),
        new Rest("Wok This Way", "Chinese · Asian", "Sector 44", 5.2, 18, 450, false, "rose", List.of(
            it("Veg Hakka Noodles", "Mains", 189, true, "🍜", 7, "Wok-tossed noodles with vegetables"),
            it("Chicken Fried Rice", "Mains", 219, false, "🍚", 6, "Egg and chicken fried rice"),
            it("Veg Manchurian", "Starters", 199, true, "🥟", 5, "Vegetable balls in Manchurian sauce"),
            it("Chilli Chicken", "Starters", 259, false, "🌶️", 6, "Indo-Chinese chilli chicken, dry"),
            it("Veg Momos (8 pcs)", "Starters", 129, true, "🥟", 6, "Steamed dumplings with red chutney"),
            it("Hot & Sour Soup", "Sides", 119, true, "🍲", 2, "Tangy, peppery soup"),
            it("Iced Lemon Tea", "Beverages", 79, true, "🧋", 1, "Chilled lemon tea")),
            List.of(pr("Veg Hakka Noodles", "Veg Manchurian", 0.6), pr("Chicken Fried Rice", "Chilli Chicken", 0.55), pr("Veg Momos (8 pcs)", "Iced Lemon Tea", 0.3),
                pr("Veg Hakka Noodles", "Hot & Sour Soup", 0.25), pr("Chilli Chicken", "Veg Hakka Noodles", 0.3))),
        new Rest("Burger Barn", "Burgers · Fast Food", "Sector 125", 1.2, 12, 400, false, "yellow", List.of(
            it("Classic Veg Burger", "Mains", 129, true, "🍔", 7, "Crispy veg patty, lettuce and mayo"),
            it("Crispy Chicken Burger", "Mains", 179, false, "🍔", 8, "Fried chicken fillet with slaw"),
            it("Paneer Tikka Burger", "Mains", 169, true, "🍔", 5, "Grilled paneer with mint mayo"),
            it("Peri Peri Fries", "Sides", 109, true, "🍟", 3, "Fries tossed in peri peri spice"),
            it("Chicken Nuggets (6 pcs)", "Starters", 149, false, "🍗", 3, "Golden fried nuggets"),
            it("Cold Coffee", "Beverages", 119, true, "🥤", 2, "Blended coffee with ice cream"),
            it("Oreo Shake", "Beverages", 139, true, "🥤", 1, "Thick shake with Oreo crumbs")),
            List.of(pr("Classic Veg Burger", "Peri Peri Fries", 0.58), pr("Crispy Chicken Burger", "Peri Peri Fries", 0.6), pr("Crispy Chicken Burger", "Cold Coffee", 0.3),
                pr("Paneer Tikka Burger", "Oreo Shake", 0.35), pr("Classic Veg Burger", "Cold Coffee", 0.25))),
        new Rest("Chaat Corner", "Street Food", "Sector 27", 2.9, 10, 250, true, "emerald", List.of(
            it("Pani Puri (8 pcs)", "Starters", 69, true, "🫧", 7, "Crisp puris with tangy pani"),
            it("Aloo Tikki Chaat", "Starters", 89, true, "🥔", 6, "Potato patties with chutneys and curd"),
            it("Pav Bhaji", "Mains", 139, true, "🍞", 7, "Spiced vegetable mash with buttered pav"),
            it("Dahi Bhalla", "Starters", 99, true, "🥣", 4, "Lentil dumplings in sweet curd"),
            it("Samosa (2 pcs)", "Starters", 49, true, "🥟", 5, "Potato-filled pastry"),
            it("Masala Chai", "Beverages", 29, true, "🍵", 2, "Ginger cardamom tea"),
            it("Extra Pav (2 pcs)", "Breads", 30, true, "🍞", 1, "Buttered pav")),
            List.of(pr("Pav Bhaji", "Extra Pav (2 pcs)", 0.55), pr("Samosa (2 pcs)", "Masala Chai", 0.65), pr("Aloo Tikki Chaat", "Pani Puri (8 pcs)", 0.35), pr("Dahi Bhalla", "Pani Puri (8 pcs)", 0.3))),
        new Rest("Sweet Tooth", "Desserts · Beverages", "Sector 104", 4.1, 8, 300, true, "pink", List.of(
            it("Belgian Waffle", "Desserts", 179, true, "🧇", 6, "Waffle with chocolate sauce"),
            it("Brownie with Ice Cream", "Desserts", 159, true, "🍨", 7, "Warm walnut brownie and vanilla scoop"),
            it("Red Velvet Pastry", "Desserts", 119, true, "🍰", 4, "Cream cheese frosted pastry"),
            it("Mango Smoothie", "Beverages", 149, true, "🥭", 4, "Alphonso mango smoothie"),
            it("Hot Chocolate", "Beverages", 139, true, "☕", 3, "Rich hot chocolate"),
            it("Vanilla Scoop", "Desserts", 59, true, "🍦", 1, "Single scoop ice cream")),
            List.of(pr("Belgian Waffle", "Vanilla Scoop", 0.45), pr("Belgian Waffle", "Hot Chocolate", 0.3), pr("Red Velvet Pastry", "Hot Chocolate", 0.35))));

    static final String[] FIRST = {"Aarav", "Vivaan", "Aditya", "Ishaan", "Kabir", "Riya", "Ananya", "Diya", "Saanvi", "Meera", "Rohan", "Arjun", "Kavya", "Pooja", "Neha", "Rahul", "Simran", "Karan", "Tanya", "Yash"};
    static final String[] LAST = {"Sharma", "Verma", "Gupta", "Singh", "Mehta", "Iyer", "Nair", "Kapoor", "Malhotra", "Bansal", "Chauhan", "Reddy"};
    static final String[] AREAS = {"Sector 125, Noida", "Sector 62, Noida", "Sector 18, Noida", "Sector 50, Noida", "Sector 137, Noida", "Sector 76, Noida", "Indirapuram, Ghaziabad"};
    static final Map<Integer, String[]> COMMENTS = Map.of(
        5, new String[]{"Loved it!", "Hot and fresh, quick delivery", "Perfect taste", "Will order again"},
        4, new String[]{"Good food", "Tasty, slightly late", "Nice portion size"},
        3, new String[]{"Average", "Food was okay"}, 2, new String[]{"Arrived cold", "Too spicy"}, 1, new String[]{"Very late delivery"});
    static final Coupon[] COUPONS = {
        Coupon.create("WELCOME50", "percent", 50, 100.0, 199, true, "50% off up to ₹100 on orders above ₹199"),
        Coupon.create("FLAT75", "flat", 75, null, 399, true, "Flat ₹75 off on orders above ₹399"),
        Coupon.create("FEAST20", "percent", 20, 150.0, 599, true, "20% off up to ₹150 on orders above ₹599"),
        Coupon.create("DIWALI100", "flat", 100, null, 499, false, "Festive offer (expired)")};

    record Customer(int id, double w, String area) {}

    public static void seed() {
        seed = 20261029;
        try { Db.reset(); } catch (Exception e) { throw new RuntimeException(e); }
        Db.tx(() -> { fill(); return null; });
    }

    static void fill() {
        for (Rest r : RESTAURANTS) {
            r.id = insert("INSERT INTO restaurants (name, cuisine, area, distance_km, prep_minutes, cost_for_two, is_pure_veg, theme) VALUES (?,?,?,?,?,?,?,?)",
                r.name, r.cuisine, r.area, r.km, r.prep, r.costForTwo, r.veg, r.theme);
            for (Item m : r.items) {
                int id = insert("INSERT INTO menu_items (restaurant_id, name, description, category, price, is_veg, emoji) VALUES (?,?,?,?,?,?,?)",
                    r.id, m.name(), m.desc(), m.category(), m.price(), m.veg(), m.emoji());
                r.idOf.put(m.name(), id); r.byId.put(id, m);
            }
        }
        for (Coupon c : COUPONS) {
            double amount = c instanceof com.tastetrail.coupon.PercentCoupon pc ? pc.getPercent() : ((com.tastetrail.coupon.FlatCoupon) c).getAmount();
            Double max = c instanceof com.tastetrail.coupon.PercentCoupon pc ? pc.getMaxDiscount() : null;
            insert0("INSERT INTO coupons (code, description, type, amount, max_discount, min_order, active) VALUES (?,?,?,?,?,?,?)",
                c.getCode(), c.getDescription(), c.getType(), amount, max, c.getMinOrder(), c.isActive());
        }
        Instant earliest = Instant.now().minus(Duration.ofDays(65));
        insert("INSERT INTO users (name, email, phone, password_hash, role, created_at) VALUES (?,?,?,?,?,?)",
            "Admin (Restaurant Ops)", "admin@tastetrail.in", "9000000001", new PasswordHasher(120_000).hash("admin123"), "admin", earliest);
        int demoId = insert("INSERT INTO users (name, email, phone, password_hash, role, created_at) VALUES (?,?,?,?,?,?)",
            "Tanvi", "demo@tastetrail.in", "9000000002", new PasswordHasher(120_000).hash("demo123"), "customer", earliest);
        String shared = new PasswordHasher(20_000).hash("password123"); // one hash reused for synthetic users keeps seeding fast
        List<Customer> users = new ArrayList<>(List.of(new Customer(demoId, 6, AREAS[0])));
        for (int i = 0; i < 260; i++) {
            String name = FIRST[i % FIRST.length] + " " + LAST[(i * 7) % LAST.length];
            String phone = ("98" + (10000000 + i * 7919)).substring(0, 10);
            int id = insert("INSERT INTO users (name, email, phone, password_hash, role, created_at) VALUES (?,?,?,?,?,?)",
                name, "user" + (i + 1) + "@example.com", phone, shared, "customer", earliest);
            users.add(new Customer(id, 1 / Math.pow(1 + i * 0.06, 1.4), AREAS[i % AREAS.length])); // a few loyal customers, many occasional
        }
        double[] userW = users.stream().mapToDouble(Customer::w).toArray();

        // day weights: gentle growth + weekend boost; history runs up to yesterday (IST)
        LocalDate today = LocalDate.now(Analytics.IST);
        int ORDER_DAYS = 60;
        List<LocalDate> days = new ArrayList<>(); List<Double> dw = new ArrayList<>();
        for (int d = ORDER_DAYS - 1; d >= 1; d--) {
            LocalDate date = today.minusDays(d);
            DayOfWeek dow = date.getDayOfWeek();
            double boost = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY ? 1.35 : dow == DayOfWeek.FRIDAY ? 1.15 : 1;
            days.add(date); dw.add((1 + (ORDER_DAYS - d) * 0.008) * boost);
        }
        double[] dayW = dw.stream().mapToDouble(Double::doubleValue).toArray();
        List<Integer> hours = new ArrayList<>(); for (int h = 0; h < 24; h++) hours.add(h);
        double[] hourW = {0.1, 0.05, 0, 0, 0, 0, 0.1, 0.4, 1.2, 1.4, 1, 1.3, 3.2, 4.2, 3, 1.6, 1.4, 1.8, 2.4, 3.6, 4.8, 4.6, 3, 1.2};
        double[] restW = {11, 9, 9, 7, 7, 8, 5, 4};

        for (int n = 0; n < 1500; n++) {
            LocalDate day = pick(days, dayW);
            int hour = pick(hours, hourW);
            Instant created = day.atStartOfDay(Analytics.IST).toInstant().plusSeconds((hour * 60L + (long) Math.floor(rand() * 60)) * 60);
            Customer u = pick(users, userW);
            Rest r = pick(RESTAURANTS, restW);
            placeOrder(u, r, created, rand() < 0.065 ? "CANCELLED" : "DELIVERED");
        }
        String[] live = {"PLACED", "PLACED", "CONFIRMED", "PREPARING", "PREPARING", "OUT_FOR_DELIVERY"};
        for (int k = 0; k < live.length; k++)
            placeOrder(users.get(1 + k * 3), RESTAURANTS.get(k % RESTAURANTS.size()), Instant.now().minus(Duration.ofMinutes(40 - k * 6L)), live[k]);
    }

    private static void insert0(String sql, Object... p) { Db.update(sql, p); }

    static Cart basket(Rest r) {
        List<Item> anchors = r.items.stream().filter(m -> m.weight() > 1).toList();
        double[] aw = anchors.stream().mapToDouble(Item::weight).toArray();
        Map<Integer, Integer> b = new LinkedHashMap<>();
        Item anchor = pick(anchors, aw);
        b.put(r.idOf.get(anchor.name()), rand() < 0.18 ? 2 : 1);
        for (Pair p : r.pairs) if (p.a().equals(anchor.name()) && rand() < p.p()) b.put(r.idOf.get(p.b()), rand() < 0.2 ? 2 : 1);
        if (rand() < 0.22) {
            Item s = pick(anchors, aw);
            b.merge(r.idOf.get(s.name()), 1, Integer::sum);
            for (Pair p : r.pairs) if (p.a().equals(s.name()) && rand() < p.p() * 0.7) b.put(r.idOf.get(p.b()), 1);
        }
        for (Item m : r.items) if (!b.containsKey(r.idOf.get(m.name())) && rand() < 0.03) b.put(r.idOf.get(m.name()), 1); // noise
        for (Pair p : r.pairs) { // cascades such as breadsticks → dip
            Item a = r.byId.get(r.idOf.get(p.a()));
            if (b.containsKey(r.idOf.get(p.a())) && !b.containsKey(r.idOf.get(p.b())) && a.weight() <= 1 && rand() < p.p()) b.put(r.idOf.get(p.b()), 1);
        }
        Cart cart = new Cart(new Restaurant(r.id, r.name, r.cuisine, r.area, r.km, r.prep, r.costForTwo, r.veg, true, r.theme));
        b.forEach((id, q) -> { Item m = r.byId.get(id); cart.add(new CartItem(id, m.name(), m.price(), q)); });
        return cart;
    }

    static void placeOrder(Customer u, Rest r, Instant created, String status) {
        Cart cart = basket(r);
        Bill bill = BillCalculator.STANDARD.calculate(cart);
        String couponCode = null;
        if (rand() < 0.28) {
            double sub = bill.getSubtotal();
            List<Coupon> eligible = Arrays.stream(COUPONS).filter(c -> c.isActive() && sub >= c.getMinOrder()).toList();
            if (!eligible.isEmpty()) {
                Coupon c = eligible.get((int) Math.floor(rand() * eligible.size()));
                bill = BillCalculator.STANDARD.calculate(cart, c); couponCode = c.getCode();
            }
        }
        String pm = pick(List.of("UPI", "COD", "CARD"), new double[]{52, 28, 20});
        int eta = bill.getEtaMinutes();
        Instant delivered = "DELIVERED".equals(status) ? created.plusSeconds(60L * (eta + Math.round((rand() - 0.35) * 18))) : null;
        String pay = "CANCELLED".equals(status) ? ("COD".equals(pm) ? "PENDING" : "REFUNDED") : "COD".equals(pm) && !"DELIVERED".equals(status) ? "PENDING" : "PAID";
        int oid = insert("""
            INSERT INTO orders (user_id, restaurant_id, subtotal, discount, delivery_fee, packaging_fee, tax, total, coupon_code,
                                payment_method, payment_status, status, address, eta_minutes, created_at, delivered_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            u.id(), r.id, bill.getSubtotal(), bill.getDiscount(), bill.getDeliveryFee(), bill.getPackaging(), bill.getTax(), bill.getTotal(),
            couponCode, pm, pay, status, u.area(), eta, created, delivered);
        for (CartItem l : cart.getItems())
            insert("INSERT INTO order_items (order_id, menu_item_id, name, price, quantity) VALUES (?,?,?,?,?)", oid, l.getMenuItemId(), l.getName(), l.getPrice(), l.getQuantity());
        if (delivered != null && rand() < 0.55) {
            double late = Duration.between(created, delivered).toMinutes() - eta;
            int rating = pick(List.of(5, 4, 3, 2, 1), late > 6 ? new double[]{20, 35, 25, 12, 8} : new double[]{52, 33, 10, 3, 2});
            String[] cm = COMMENTS.get(rating);
            String comment = rand() < 0.6 ? cm[(int) Math.floor(rand() * cm.length)] : null;
            insert("INSERT INTO reviews (order_id, user_id, restaurant_id, rating, comment, created_at) VALUES (?,?,?,?,?,?)", oid, u.id(), r.id, rating, comment, delivered);
        }
    }
}
