package com.tastetrail.service;

import java.util.*;

/** Suggests the most-ordered dishes. Used for "Popular dishes", bestseller tags and as a fallback. */
public class PopularityRecommender implements Recommender {
    private final Map<Integer, Integer> orderCounts;

    public PopularityRecommender(Map<Integer, Integer> orderCounts) { this.orderCounts = orderCounts; }

    public int countFor(int itemId) { return orderCounts.getOrDefault(itemId, 0); }

    @Override
    public List<Suggestion> recommend(Collection<Integer> exclude, int limit, Set<Integer> allowed) {
        Set<Integer> ex = exclude == null ? Set.of() : new HashSet<>(exclude);
        return orderCounts.entrySet().stream()
            .filter(e -> !ex.contains(e.getKey()) && (allowed == null || allowed.contains(e.getKey())))
            .sorted((x, y) -> y.getValue().equals(x.getValue()) ? Integer.compare(x.getKey(), y.getKey()) : Integer.compare(y.getValue(), x.getValue()))
            .limit(limit)
            .map(e -> new Suggestion(e.getKey(), e.getValue(), null, null, null, "popular at this restaurant"))
            .toList();
    }
}
