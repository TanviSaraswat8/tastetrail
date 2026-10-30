package com.tastetrail.model;

/** ENCAPSULATION: one dish on a restaurant's menu. The price is validated when the object is created. */
public class MenuItem {
    private final int id;
    private final int restaurantId;
    private final String name;
    private final String description;
    private final String category;
    private final double price;
    private final boolean veg;
    private final boolean available;
    private final String emoji;

    public MenuItem(int id, int restaurantId, String name, String description, String category,
                    double price, boolean veg, boolean available, String emoji) {
        if (price <= 0) throw new IllegalArgumentException("Price must be positive");
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.veg = veg;
        this.available = available;
        this.emoji = emoji;
    }

    public int getId() { return id; }
    public int getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public boolean isVeg() { return veg; }
    public boolean isAvailable() { return available; }
    public String getEmoji() { return emoji; }
}
