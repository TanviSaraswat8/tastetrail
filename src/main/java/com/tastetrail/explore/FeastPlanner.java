package com.tastetrail.explore;

import com.tastetrail.service.BillCalculator;

import java.util.*;

/**
 * BUDGET FEAST PLANNER – "feed 4 people for ₹1,000".
 *
 * For one restaurant it chooses how many of each dish to order so that
 *   (1) everyone gets a full serving (main = 1 serving, starter = ½),
 *   (2) the FINAL bill (GST + packaging + delivery included) stays within the budget, and
 *   (3) the total "satisfaction value" is as high as possible.
 *
 * This is a bounded 0/1 knapsack solved with dynamic programming:
 *   best[s][c] = highest value reachable with s half-servings (capped at the target) and exactly ₹c of food.
 * Every copy of a dish is a separate knapsack item whose value decays (0.7×, 0.49× …) so the plan prefers variety.
 * Time: O(copies × target × foodBudget) – a few million steps, well under a second.
 */
public class FeastPlanner {
    /** How much a person enjoys one portion from each section of the menu. */
    private static final Map<String, Double> BASE_VALUE = Map.of(
        "Mains", 10.0, "Starters", 6.0, "Desserts", 5.0, "Beverages", 4.0, "Sides", 3.0, "Breads", 2.5);
    private static final double REPEAT_DECAY = 0.7;

    private final BillCalculator billing;

    public FeastPlanner(BillCalculator billing) { this.billing = billing; }

    public record Line(Dish dish, int quantity) {}
    public record Plan(int restaurantId, String restaurant, List<Line> lines, double food, double total, double spare,
                       int servingsHalf, double value) {}

    /** Value of one portion: section value, boosted by up to 50% for the restaurant's most popular dishes. */
    static double valueOf(Dish d, int maxOrders) {
        double base = BASE_VALUE.getOrDefault(d.category(), 3.0);
        return base * (1 + 0.5 * (maxOrders == 0 ? 0 : (double) d.orders() / maxOrders));
    }

    /** Returns the best plan for one restaurant's menu, or empty if nothing fits the budget. */
    public Optional<Plan> plan(List<Dish> menu, int people, double budget, boolean vegOnly) {
        if (people < 1 || people > 12) throw new IllegalArgumentException("People must be between 1 and 12");
        List<Dish> dishes = menu.stream().filter(d -> !vegOnly || d.veg()).toList();
        if (dishes.isEmpty()) return Optional.empty();
        double km = dishes.get(0).distanceKm();
        int maxOrders = dishes.stream().mapToInt(Dish::orders).max().orElse(0);

        // largest food value whose final bill is still within budget
        int foodBudget = (int) Math.floor(budget / 1.05);
        while (foodBudget > 0 && billing.totalFor(foodBudget, km) > budget) foodBudget--;
        if (foodBudget <= 0) return Optional.empty();

        // expand every dish into copies with decaying value
        List<Dish> copyDish = new ArrayList<>();
        List<Double> copyValue = new ArrayList<>();
        for (Dish d : dishes) {
            int maxCopies = d.halfServings() > 0 ? people : Math.min(people, 3);
            double v = valueOf(d, maxOrders);
            for (int k = 0; k < maxCopies && d.price() * (k + 1) <= foodBudget; k++) {
                copyDish.add(d); copyValue.add(v); v *= REPEAT_DECAY;
            }
        }
        int target = 2 * people, n = copyDish.size(), C = foodBudget;
        double[][] best = new double[target + 1][C + 1];
        for (double[] row : best) Arrays.fill(row, Double.NEGATIVE_INFINITY);
        best[0][0] = 0;
        short[][][] from = new short[n][][]; // previous serving state for each decision, -1 = copy not taken

        for (int i = 0; i < n; i++) {
            Dish d = copyDish.get(i);
            int cost = (int) Math.round(d.price()), hs = d.halfServings();
            double val = copyValue.get(i);
            from[i] = new short[target + 1][C + 1];
            for (short[] row : from[i]) Arrays.fill(row, (short) -1);
            for (int s = target; s >= 0; s--) {            // rows and columns backwards: each copy is used at most once
                int lo = s == target ? Math.max(0, target - hs) : s - hs, hi = s == target ? target : s - hs;
                if (hi < 0) continue;
                for (int c = C; c >= cost; c--)
                    for (int ps = lo; ps <= hi; ps++) {    // extra servings beyond the target are allowed (capped)
                        double cand = best[ps][c - cost];
                        if (cand == Double.NEGATIVE_INFINITY) continue;
                        if (cand + val > best[s][c] + 1e-9) { best[s][c] = cand + val; from[i][s][c] = (short) ps; }
                    }
            }
        }

        // the best state where everyone has a full serving
        int bestC = -1;
        for (int c = 0; c <= C; c++)
            if (best[target][c] > Double.NEGATIVE_INFINITY && (bestC < 0 || best[target][c] > best[target][bestC] + 1e-9)) bestC = c;
        if (bestC < 0) return Optional.empty();

        // walk the decisions backwards to rebuild the basket
        Map<Dish, Integer> qty = new LinkedHashMap<>();
        int s = target, c = bestC;
        for (int i = n - 1; i >= 0; i--) {
            short ps = from[i][s][c];
            if (ps < 0) continue;
            Dish d = copyDish.get(i);
            qty.merge(d, 1, Integer::sum);
            s = ps; c -= (int) Math.round(d.price());
        }
        List<Line> lines = new ArrayList<>();
        qty.forEach((d, q) -> lines.add(new Line(d, q)));
        lines.sort(Comparator.comparingInt((Line l) -> -l.dish().halfServings()).thenComparing(l -> -l.dish().price()));
        double food = lines.stream().mapToDouble(l -> l.dish().price() * l.quantity()).sum();
        double total = billing.totalFor(food, km);
        int servings = lines.stream().mapToInt(l -> l.dish().halfServings() * l.quantity()).sum();
        Dish first = dishes.get(0);
        return Optional.of(new Plan(first.restaurantId(), first.restaurant(), lines, BillCalculator.round2(food), total,
            BillCalculator.round2(budget - total), servings, Math.round(best[target][bestC] * 10) / 10.0));
    }

    /** OVERLOADED: plans every restaurant and returns the best few, highest value first. */
    public List<Plan> plan(Map<Integer, List<Dish>> menus, int people, double budget, boolean vegOnly, int limit) {
        List<Plan> out = new ArrayList<>();
        for (List<Dish> menu : menus.values()) plan(menu, people, budget, vegOnly).ifPresent(out::add);
        out.sort(Comparator.comparingDouble(Plan::value).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }
}
