package com.tastetrail.explore;

import java.util.EnumSet;
import java.util.Set;

/**
 * A menu item together with the restaurant facts the explore features need (immutable record).
 * {@code orders} = how many past orders contained this dish (popularity).
 */
public record Dish(int id, String name, String category, double price, boolean veg, String emoji,
                   int restaurantId, String restaurant, String cuisine, double distanceKm, int prepMinutes, int orders) {

    /** Flavour / occasion tags, derived from the dish name and category. */
    public enum Tag { SPICY, COMFORT, LIGHT, SWEET, DRINK, FILLING, STREET, GLOBAL, GRILLED }

    private static boolean has(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    /** Simple keyword rules – easy to explain and to extend. */
    public Set<Tag> tags() {
        String n = name.toLowerCase(), c = cuisine.toLowerCase();
        Set<Tag> t = EnumSet.noneOf(Tag.class);
        if (has(n, "chilli", "peri", "65", "mirchi", "kimchi", "seekh", "tikka", "tandoori", "pani puri", "korean", "arrabbiata", "65", "hot & sour", "masala dosa", "mysore", "kathi", "kebab", "tacos"))
            t.add(Tag.SPICY);
        if (has(n, "biryani", "dal", "butter", "makhani", "chole", "pav bhaji", "ramen", "noodles", "fried rice", "pizza", "thali", "baati", "burrito", "lava", "brownie", "hot chocolate", "chai", "naan"))
            t.add(Tag.COMFORT);
        if (has(n, "salad", "idli", "dhokla", "khandvi", "sushi", "smoothie", "fruit", "soup", "uttapam", "avocado", "falafel", "raita", "chaas", "lemon", "nimbu", "panna", "filter coffee", "matcha"))
            t.add(Tag.LIGHT);
        if (category.equals("Desserts") || has(n, "shake", "lassi", "waffle", "kesari", "jamun", "meetha", "phirni", "churros", "smoothie", "hot chocolate"))
            t.add(Tag.SWEET);
        if (category.equals("Beverages")) t.add(Tag.DRINK);
        if (category.equals("Mains") && price >= 199) t.add(Tag.FILLING);
        if (c.contains("street") || has(n, "roll", "samosa", "chaat", "momos", "kachori", "vada", "fries", "burger")) t.add(Tag.STREET);
        if (has(c, "italian", "chinese", "japanese", "korean", "mexican", "healthy")) t.add(Tag.GLOBAL);
        if (has(n, "tikka", "tandoori", "seekh", "kebab", "grilled")) t.add(Tag.GRILLED);
        return t;
    }

    /** Main dishes count as one serving, starters as half a serving (stored as "half-servings"). */
    public int halfServings() {
        return switch (category) { case "Mains" -> 2; case "Starters" -> 1; default -> 0; };
    }
}
