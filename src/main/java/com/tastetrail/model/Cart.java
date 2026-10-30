package com.tastetrail.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ENCAPSULATION: a cart holds items from exactly one restaurant. The list of items can be
 * read but not changed from outside (an unmodifiable view is returned).
 */
public class Cart {
    private final Restaurant restaurant;
    private final List<CartItem> items = new ArrayList<>();

    public Cart(Restaurant restaurant) { this.restaurant = restaurant; }

    public Cart add(CartItem item) { items.add(item); return this; }

    public Restaurant getRestaurant() { return restaurant; }
    public List<CartItem> getItems() { return Collections.unmodifiableList(items); }
    public boolean isEmpty() { return items.isEmpty(); }

    public double getSubtotal() { return items.stream().mapToDouble(CartItem::getLineTotal).sum(); }
    public int getItemCount() { return items.stream().mapToInt(CartItem::getQuantity).sum(); }
    public List<Integer> getMenuItemIds() { return items.stream().map(CartItem::getMenuItemId).toList(); }
}
