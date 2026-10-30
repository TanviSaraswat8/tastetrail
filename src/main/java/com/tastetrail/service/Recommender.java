package com.tastetrail.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * INTERFACE for anything that can suggest dishes for a cart. The cart page does not know
 * which algorithm is behind it – association rules, popularity, or both combined.
 */
public interface Recommender {

    /** One suggestion. confidence, lift and becauseItemId are null when the reason is popularity. */
    record Suggestion(int itemId, double score, Double confidence, Double lift, Integer becauseItemId, String reason) {}

    /**
     * @param cartItemIds dishes already in the cart (never suggested again)
     * @param limit       maximum number of suggestions
     * @param allowed     dishes that may be suggested (same restaurant, in stock); null = any
     */
    List<Suggestion> recommend(Collection<Integer> cartItemIds, int limit, Set<Integer> allowed);
}
