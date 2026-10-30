package com.tastetrail.dao;

import com.tastetrail.db.Db;
import com.tastetrail.model.MenuItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads and writes the menu_items table. */
public class MenuItemDao extends BaseDao<MenuItem> {

    @Override protected MenuItem map(Map<String, Object> r) {
        return new MenuItem(intOf(r.get("id")), intOf(r.get("restaurant_id")), strOf(r.get("name")), strOf(r.get("description")),
            strOf(r.get("category")), doubleOf(r.get("price")), boolOf(r.get("is_veg")), boolOf(r.get("is_available")), strOf(r.get("emoji")));
    }

    public Optional<MenuItem> findById(int id) { return findOne("SELECT * FROM menu_items WHERE id = ?", id); }

    /** Only returns the dish if it belongs to the given restaurant. */
    public Optional<MenuItem> findInRestaurant(int itemId, int restaurantId) {
        return findOne("SELECT * FROM menu_items WHERE id = ? AND restaurant_id = ?", itemId, restaurantId);
    }

    public List<MenuItem> findAvailable(int restaurantId) {
        return findAll("SELECT * FROM menu_items WHERE restaurant_id = ? AND is_available = TRUE ORDER BY id", restaurantId);
    }

    public int add(int restaurantId, String name, String description, String category, double price, boolean veg, String emoji) {
        return Db.insert("INSERT INTO menu_items (restaurant_id, name, description, category, price, is_veg, emoji) VALUES (?,?,?,?,?,?,?)",
            restaurantId, name, description, category, price, veg, emoji);
    }

    public void updatePrice(int id, double price) { Db.update("UPDATE menu_items SET price = ? WHERE id = ?", price, id); }

    public void setAvailable(int id, boolean available) { Db.update("UPDATE menu_items SET is_available = ? WHERE id = ?", available, id); }
}
