package com.tastetrail.service;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import static com.tastetrail.service.BillCalculator.round2;

/** UTILITY CLASS (final, private constructor, static methods): descriptive analytics over order data. */
public final class Analytics {
    private Analytics() {}

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public record OrderRow(int id, int userId, int restaurantId, double total, String paymentMethod, String status, Instant createdAt, Instant deliveredAt) {}
    public record LineRow(int orderId, String name, String category, int quantity, double price) {}

    static boolean valid(OrderRow o) { return !"CANCELLED".equals(o.status()); }
    public static LocalDate dayOf(Instant t) { return t.atZone(IST).toLocalDate(); }

    public static Map<String, Object> kpis(List<OrderRow> orders) {
        List<OrderRow> ok = orders.stream().filter(Analytics::valid).toList();
        double revenue = ok.stream().mapToDouble(OrderRow::total).sum();
        Map<Integer, Integer> perUser = new HashMap<>();
        for (OrderRow o : ok) perUser.merge(o.userId(), 1, Integer::sum);
        long repeat = perUser.values().stream().filter(c -> c > 1).count();
        List<OrderRow> delivered = ok.stream().filter(o -> "DELIVERED".equals(o.status()) && o.deliveredAt() != null).toList();
        double avgDelivery = delivered.stream().mapToDouble(o -> Duration.between(o.createdAt(), o.deliveredAt()).toMillis() / 60000.0).average().orElse(0);
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("orders", ok.size());
        k.put("cancelled", orders.size() - ok.size());
        k.put("cancellationRate", orders.isEmpty() ? 0 : round2(100.0 * (orders.size() - ok.size()) / orders.size()));
        k.put("revenue", round2(revenue));
        k.put("avgOrderValue", ok.isEmpty() ? 0 : round2(revenue / ok.size()));
        k.put("customers", perUser.size());
        k.put("repeatRate", perUser.isEmpty() ? 0 : round2(100.0 * repeat / perUser.size()));
        k.put("avgDeliveryMinutes", Math.round(avgDelivery));
        return k;
    }

    /** Daily revenue (IST calendar days) for the `days` days ending on `endDay`, with a 7-day moving average. */
    public static List<Map<String, Object>> dailyRevenue(List<OrderRow> orders, int days, LocalDate endDay) {
        LinkedHashMap<LocalDate, double[]> buckets = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) buckets.put(endDay.minusDays(i), new double[2]);
        for (OrderRow o : orders) {
            if (!valid(o)) continue;
            double[] b = buckets.get(dayOf(o.createdAt()));
            if (b != null) { b[0] += o.total(); b[1]++; }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        List<Double> revs = new ArrayList<>();
        for (var e : buckets.entrySet()) {
            double rev = round2(e.getValue()[0]);
            revs.add(rev);
            int from = Math.max(0, revs.size() - 7);
            double ma = revs.subList(from, revs.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey().toString());
            m.put("revenue", rev);
            m.put("orders", (int) e.getValue()[1]);
            m.put("movingAvg", round2(ma));
            out.add(m);
        }
        return out;
    }

    /** Ordinary least squares y = a + b·x over the series (x = day index), projected `ahead` days. */
    public static Map<String, Object> linearForecast(List<Map<String, Object>> series, int ahead) {
        int n = series.size();
        Map<String, Object> res = new LinkedHashMap<>();
        if (n < 2) { res.put("slope", 0.0); res.put("intercept", 0.0); res.put("r2", 0.0); res.put("forecast", List.of()); return res; }
        double mx = (n - 1) / 2.0, my = series.stream().mapToDouble(s -> ((Number) s.get("revenue")).doubleValue()).average().orElse(0);
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double y = ((Number) series.get(i).get("revenue")).doubleValue();
            sxy += (i - mx) * (y - my); sxx += (i - mx) * (i - mx); syy += (y - my) * (y - my);
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;
        double intercept = my - slope * mx;
        double r2 = sxx == 0 || syy == 0 ? 0 : (sxy * sxy) / (sxx * syy);
        LocalDate last = LocalDate.parse((String) series.get(n - 1).get("date"));
        List<Map<String, Object>> fc = new ArrayList<>();
        for (int k = 0; k < ahead; k++) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("date", last.plusDays(k + 1).toString());
            f.put("forecast", round2(Math.max(0, intercept + slope * (n + k))));
            fc.add(f);
        }
        res.put("slope", round2(slope)); res.put("intercept", round2(intercept));
        res.put("r2", Math.round(r2 * 1000) / 1000.0); res.put("forecast", fc);
        return res;
    }

    /** Orders by hour of day in IST. */
    public static int[] hourlyDistribution(List<OrderRow> orders) {
        int[] h = new int[24];
        for (OrderRow o : orders) if (valid(o)) h[o.createdAt().atZone(IST).getHour()]++;
        return h;
    }

    public static List<Map<String, Object>> topItems(List<LineRow> lines, int k) {
        Map<String, double[]> m = new LinkedHashMap<>();
        Map<String, String> cat = new HashMap<>();
        for (LineRow l : lines) {
            double[] e = m.computeIfAbsent(l.name(), x -> new double[2]);
            e[0] += l.quantity(); e[1] += l.quantity() * l.price(); cat.put(l.name(), l.category());
        }
        return m.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0])).limit(k).map(e -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", e.getKey()); r.put("category", cat.get(e.getKey()));
            r.put("quantity", (int) e.getValue()[0]); r.put("revenue", round2(e.getValue()[1]));
            return r;
        }).toList();
    }

    public static <T> List<Map<String, Object>> shareBy(List<T> rows, Function<T, String> key, ToDoubleFunction<T> value) {
        Map<String, Double> m = new LinkedHashMap<>();
        double total = 0;
        for (T r : rows) { double v = value.applyAsDouble(r); m.merge(key.apply(r), v, Double::sum); total += v; }
        final double t = total;
        return m.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).map(e -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", e.getKey()); r.put("value", round2(e.getValue())); r.put("pct", t == 0 ? 0 : round2(100 * e.getValue() / t));
            return r;
        }).toList();
    }

    public static Map<String, Object> ratingDistribution(List<Integer> ratings) {
        int[] dist = new int[5];
        for (int r : ratings) if (r >= 1 && r <= 5) dist[r - 1]++;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("dist", dist);
        res.put("average", ratings.isEmpty() ? 0 : round2(ratings.stream().mapToInt(Integer::intValue).average().orElse(0)));
        res.put("count", ratings.size());
        return res;
    }
}
