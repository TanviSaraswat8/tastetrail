package com.tastetrail.model;

/** One line of a cart: a dish, the price charged for it and how many were ordered. */
public class CartItem {
    public static final int MAX_QUANTITY = 20;

    private final int menuItemId;
    private final String name;
    private final double price;
    private final int quantity;

    public CartItem(int menuItemId, String name, double price, int quantity) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    /** Builds a line from a menu item, so the price always comes from the menu (never from the browser). */
    public static CartItem of(MenuItem item, int quantity) {
        return new CartItem(item.getId(), item.getName(), item.getPrice(), quantity);
    }

    public int getMenuItemId() { return menuItemId; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public double getLineTotal() { return price * quantity; }
    public boolean hasValidQuantity() { return quantity >= 1 && quantity <= MAX_QUANTITY; }
}
