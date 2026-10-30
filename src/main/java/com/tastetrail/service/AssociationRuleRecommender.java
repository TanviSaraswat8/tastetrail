package com.tastetrail.service;

import java.util.*;

/**
 * "Frequently ordered together" using association rules (market basket analysis).
 * Each past order is a basket of menu-item ids.
 *   support(A,B)    = orders containing A and B / all orders
 *   confidence(A→B) = orders containing A and B / orders containing A
 *   lift(A→B)       = confidence(A→B) / support(B)      (> 1 means "more often than chance")
 */
public class AssociationRuleRecommender implements Recommender {

    /** One rule A → B. */
    public record Rule(int fromItem, int toItem, int count, double support, double confidence, double lift) {}

    private final int basketCount;
    private final Map<Integer, Integer> itemCount = new HashMap<>();
    private final Map<Integer, List<Rule>> rulesByItem = new HashMap<>();
    private final double minLift;

    /** Learns the rules from past baskets; pairs seen fewer than minPairCount times are ignored. */
    public AssociationRuleRecommender(Collection<? extends Collection<Integer>> baskets, int minPairCount, double minLift) {
        this.basketCount = baskets.size();
        this.minLift = minLift;
        Map<Long, Integer> pairCount = new HashMap<>();
        for (Collection<Integer> raw : baskets) {
            List<Integer> items = new ArrayList<>(new TreeSet<>(raw)); // unique and sorted
            for (int i : items) itemCount.merge(i, 1, Integer::sum);
            for (int x = 0; x < items.size(); x++)
                for (int y = x + 1; y < items.size(); y++)
                    pairCount.merge(((long) items.get(x) << 32) | items.get(y), 1, Integer::sum);
        }
        for (var e : pairCount.entrySet()) {
            int count = e.getValue();
            if (count < minPairCount) continue;
            int a = (int) (e.getKey() >> 32), b = (int) (long) e.getKey();
            addRule(a, b, count);
            addRule(b, a, count);
        }
    }

    private void addRule(int from, int to, int count) {
        double confidence = (double) count / itemCount.get(from);
        double supportTo = (double) itemCount.get(to) / basketCount;
        rulesByItem.computeIfAbsent(from, k -> new ArrayList<>())
                   .add(new Rule(from, to, count, (double) count / basketCount, confidence, confidence / supportTo));
    }

    public int getBasketCount() { return basketCount; }
    public Map<Integer, Integer> getItemCounts() { return Collections.unmodifiableMap(itemCount); }
    public List<Rule> getRulesFrom(int itemId) { return rulesByItem.getOrDefault(itemId, List.of()); }
    public List<Rule> getAllRules() { return rulesByItem.values().stream().flatMap(List::stream).toList(); }

    @Override
    public List<Suggestion> recommend(Collection<Integer> cartItemIds, int limit, Set<Integer> allowed) {
        Set<Integer> inCart = new HashSet<>(cartItemIds);
        Map<Integer, Suggestion> best = new HashMap<>();
        for (int id : inCart) {
            for (Rule r : getRulesFrom(id)) {
                if (inCart.contains(r.toItem()) || r.lift() < minLift) continue;
                if (allowed != null && !allowed.contains(r.toItem())) continue;
                double score = r.confidence() * r.lift();
                Suggestion cur = best.get(r.toItem());
                if (cur == null || score > cur.score())
                    best.put(r.toItem(), new Suggestion(r.toItem(), score, r.confidence(), r.lift(), id, "frequently ordered together"));
            }
        }
        return best.values().stream().sorted(Comparator.comparingDouble(Suggestion::score).reversed()).limit(limit).toList();
    }
}
