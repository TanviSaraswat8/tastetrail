package com.tastetrail.service;

import java.util.*;

/**
 * COMPOSITION: combines two recommenders. It asks the primary one first and fills any empty
 * places from the fallback. Both are used only through the Recommender interface.
 */
public class HybridRecommender implements Recommender {
    private final Recommender primary;
    private final Recommender fallback;

    public HybridRecommender(Recommender primary, Recommender fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public List<Suggestion> recommend(Collection<Integer> cartItemIds, int limit, Set<Integer> allowed) {
        List<Suggestion> out = new ArrayList<>(primary.recommend(cartItemIds, limit, allowed));
        if (out.size() < limit) {
            List<Integer> exclude = new ArrayList<>(cartItemIds);
            out.forEach(s -> exclude.add(s.itemId()));
            out.addAll(fallback.recommend(exclude, limit - out.size(), allowed));
        }
        return out;
    }
}
