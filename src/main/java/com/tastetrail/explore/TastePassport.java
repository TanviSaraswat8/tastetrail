package com.tastetrail.explore;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;

/**
 * TASTE PASSPORT – gamified food discovery.
 * Loyalty programmes on delivery apps reward how much you spend. The passport rewards how widely you
 * explore: one stamp per cuisine, XP for every order (more for a new cuisine), levels and badges, plus a
 * "next challenge" that points to a cuisine the customer has not tried yet.
 */
public class TastePassport {

    public record Place(int id, String name, String cuisine) {}
    public record PastOrder(int restaurantId, ZonedDateTime createdAt, List<Dish> items, boolean rated) {}

    public record Stamp(int restaurantId, String restaurant, String cuisine, boolean collected, int orders) {}
    public record Badge(String id, String emoji, String name, String description, int progress, int target, boolean earned, int tier, String tierName) {}
    public record Level(int number, String name, int fromXp, Integer nextXp) {}
    public record Result(int xp, Level level, int ordersCount, List<Stamp> stamps, int stampsCollected, List<Badge> badges,
                         Place challenge, String challengeText) {}

    static final int XP_PER_ORDER = 10, XP_NEW_CUISINE = 25, XP_PER_REVIEW = 5;
    static final String[] LEVELS = {"Taste Rookie", "Food Explorer", "Flavour Hunter", "Cuisine Connoisseur", "TasteTrail Legend"};
    static final int[] LEVEL_XP = {0, 150, 600, 1800, 4000};
    static final String[] TIERS = {"", "Bronze", "Silver", "Gold"};

    /** A badge is a rule over the order history: how many orders satisfy it, and how many are needed. */
    private record Rule(String id, String emoji, String name, String description, int[] tiers, Predicate<PastOrder> test) {}

    private static boolean anyTag(PastOrder o, Dish.Tag tag) { return o.items().stream().anyMatch(d -> d.tags().contains(tag)); }

    private static final List<Rule> RULES = List.of(
        new Rule("night-owl", "🦉", "Night Owl", "Orders after 10 pm", new int[]{3, 15, 40}, o -> o.createdAt().getHour() >= 22 || o.createdAt().getHour() < 4),
        new Rule("early-bird", "🌅", "Early Bird", "Orders before 11 am", new int[]{3, 10, 25}, o -> o.createdAt().getHour() >= 6 && o.createdAt().getHour() < 11),
        new Rule("spice-lord", "🌶️", "Spice Lord", "Orders with a spicy dish", new int[]{5, 25, 100}, o -> anyTag(o, Dish.Tag.SPICY)),
        new Rule("sweet-tooth", "🍰", "Sweet Tooth", "Orders with a dessert", new int[]{5, 20, 60}, o -> o.items().stream().anyMatch(d -> d.category().equals("Desserts"))),
        new Rule("weekend", "🎉", "Weekend Feaster", "Orders on Saturday or Sunday", new int[]{5, 25, 80},
            o -> o.createdAt().getDayOfWeek() == DayOfWeek.SATURDAY || o.createdAt().getDayOfWeek() == DayOfWeek.SUNDAY),
        new Rule("critic", "✍️", "Food Critic", "Orders you rated", new int[]{5, 25, 150}, PastOrder::rated),
        new Rule("healthy", "🥗", "Green Streak", "Orders with something light", new int[]{3, 15, 50}, o -> anyTag(o, Dish.Tag.LIGHT)));

    /** Bronze / Silver / Gold: progress is shown towards the next tier still to reach. */
    static Badge badge(String id, String emoji, String name, String description, int[] tiers, int count) {
        int tier = 0;
        while (tier < tiers.length && count >= tiers[tier]) tier++;
        int target = tiers[Math.min(tier, tiers.length - 1)];
        return new Badge(id, emoji, name, description, Math.min(count, target), target, tier > 0, tier, TIERS[tier]);
    }

    public static Level levelFor(int xp) {
        int k = 0;
        for (int i = 0; i < LEVEL_XP.length; i++) if (xp >= LEVEL_XP[i]) k = i;
        return new Level(k + 1, LEVELS[k], LEVEL_XP[k], k + 1 < LEVEL_XP.length ? LEVEL_XP[k + 1] : null);
    }

    /** @param orders the customer's delivered orders, oldest first */
    public Result build(List<Place> places, List<PastOrder> orders) {
        Map<Integer, Integer> perPlace = new HashMap<>();
        Set<String> cuisinesSeen = new HashSet<>();
        Map<Integer, String> cuisineOf = new HashMap<>();
        for (Place p : places) cuisineOf.put(p.id(), p.cuisine());
        int xp = 0;
        for (PastOrder o : orders) {
            xp += XP_PER_ORDER + (o.rated() ? XP_PER_REVIEW : 0);
            if (cuisinesSeen.add(cuisineOf.getOrDefault(o.restaurantId(), "?"))) xp += XP_NEW_CUISINE;
            perPlace.merge(o.restaurantId(), 1, Integer::sum);
        }
        List<Stamp> stamps = new ArrayList<>();
        for (Place p : places) stamps.add(new Stamp(p.id(), p.name(), p.cuisine(), perPlace.containsKey(p.id()), perPlace.getOrDefault(p.id(), 0)));
        int collected = (int) stamps.stream().filter(Stamp::collected).count();

        List<Badge> badges = new ArrayList<>();
        for (Rule r : RULES) badges.add(badge(r.id(), r.emoji(), r.name(), r.description(), r.tiers(), (int) orders.stream().filter(r.test()).count()));
        badges.add(badge("globetrotter", "🌍", "Globetrotter", "Cuisine stamps collected", new int[]{3, 8, places.size()}, collected));
        int loyal = perPlace.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        badges.add(badge("regular", "🏠", "Loyal Regular", "Orders from your favourite restaurant", new int[]{5, 20, 50}, loyal));

        // challenge: the nearest-to-hand cuisine not collected yet (first in the list = most popular / newest)
        Place challenge = places.stream().filter(p -> !perPlace.containsKey(p.id())).findFirst().orElse(null);
        String text = challenge == null ? "You've collected every stamp – legend status!"
            : "Order from " + challenge.name() + " to collect the " + challenge.cuisine() + " stamp and earn +" + (XP_PER_ORDER + XP_NEW_CUISINE) + " XP";
        return new Result(xp, levelFor(xp), orders.size(), stamps, collected, badges, challenge, text);
    }
}
