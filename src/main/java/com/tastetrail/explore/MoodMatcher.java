package com.tastetrail.explore;

import com.tastetrail.explore.Dish.Tag;

import java.util.*;

/**
 * MOOD MATCH – "I don't know what I want".
 * The customer answers three playful questions (mood, spice level, budget per person) and gets dishes
 * ranked by a transparent score, each with the reasons it was chosen. Cuisines the customer has never
 * ordered get a bonus in "adventurous" mode, so the app nudges discovery instead of repeating habits.
 */
public class MoodMatcher {

    /** Each mood is a weighting of dish tags (an enum with its own data, like OrderStatus). */
    public enum Mood {
        COMFORT("Comfort food", "🛋️", Map.of(Tag.COMFORT, 5.0, Tag.FILLING, 2.0, Tag.SWEET, 1.0)),
        HEALTHY("Light & fresh", "🥗", Map.of(Tag.LIGHT, 6.0, Tag.GRILLED, 2.0, Tag.FILLING, -1.0)),
        ADVENTUROUS("Adventurous", "🧭", Map.of(Tag.GLOBAL, 4.0, Tag.STREET, 1.5, Tag.SPICY, 1.0)),
        CELEBRATE("Celebration", "🎉", Map.of(Tag.FILLING, 3.0, Tag.SWEET, 3.0, Tag.GRILLED, 2.0)),
        QUICK("Quick bite", "⚡", Map.of(Tag.STREET, 4.0, Tag.LIGHT, 1.0, Tag.FILLING, -2.0));

        public final String label, emoji; final Map<Tag, Double> weights;
        Mood(String label, String emoji, Map<Tag, Double> weights) { this.label = label; this.emoji = emoji; this.weights = weights; }

        public static Mood of(String s) {
            try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (Exception e) { return COMFORT; }
        }
    }

    public record Match(Dish dish, double score, List<String> reasons, int etaMinutes) {}

    private final java.util.function.ToIntFunction<Dish> eta;

    /** @param eta how long a dish takes to arrive (injected, so the matcher does not depend on the bill code) */
    public MoodMatcher(java.util.function.ToIntFunction<Dish> eta) { this.eta = eta; }

    /**
     * @param spice 0 = mild, 1 = medium, 2 = hot
     * @param triedCuisines cuisines the customer has already ordered (may be empty)
     */
    public List<Match> match(List<Dish> dishes, Mood mood, int spice, double maxPrice, boolean vegOnly,
                             Set<String> triedCuisines, int limit) {
        int maxOrders = dishes.stream().mapToInt(Dish::orders).max().orElse(1);
        List<Match> all = new ArrayList<>();
        for (Dish d : dishes) {
            if (vegOnly && !d.veg()) continue;
            if (d.price() > maxPrice) continue;
            if (d.category().equals("Sides") || d.category().equals("Breads")) continue; // not a craving on its own
            Set<Tag> tags = d.tags();
            List<String> why = new ArrayList<>();
            double score = 0;
            for (var w : mood.weights.entrySet()) if (tags.contains(w.getKey())) score += w.getValue();
            if (score > 0) why.add(mood.emoji + " fits your " + mood.label.toLowerCase() + " mood");

            if (tags.contains(Tag.SPICY)) {
                if (spice == 2) { score += 3; why.add("🌶️ nice and spicy"); }
                else if (spice == 0) score -= 4;
            } else if (spice == 0) { score += 1; why.add("😌 mild"); }

            int minutes = eta.applyAsInt(d);
            if (mood == Mood.QUICK && minutes <= 30) { score += 3; why.add("⚡ arrives in ~" + minutes + " min"); }
            boolean newCuisine = !triedCuisines.isEmpty() && !triedCuisines.contains(d.cuisine());
            if (newCuisine && mood == Mood.ADVENTUROUS) { score += 4; why.add("🆕 a cuisine you haven't tried yet"); }
            else if (newCuisine) score += 0.5;

            double pop = (double) d.orders() / Math.max(1, maxOrders);
            score += 2 * pop;
            if (pop > 0.5) why.add("🔥 crowd favourite");
            if (d.price() <= maxPrice * 0.6) { score += 0.5; why.add("💰 well within budget"); }
            if (score > 0) all.add(new Match(d, Math.round(score * 10) / 10.0, why, minutes));
        }
        all.sort(Comparator.comparingDouble(Match::score).reversed().thenComparingDouble(m -> m.dish().price()));
        // keep the list varied: at most two dishes from the same restaurant
        Map<Integer, Integer> perRestaurant = new HashMap<>();
        List<Match> out = new ArrayList<>();
        for (Match m : all) {
            if (perRestaurant.merge(m.dish().restaurantId(), 1, Integer::sum) > 2) continue;
            out.add(m);
            if (out.size() == limit) break;
        }
        return out;
    }
}
