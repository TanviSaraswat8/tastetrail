package com.tastetrail.dao;

import com.tastetrail.db.Db;
import com.tastetrail.model.Restaurant;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads and writes the restaurants table. */
public class RestaurantDao extends BaseDao<Restaurant> {

    @Override protected Restaurant map(Map<String, Object> r) {
        return new Restaurant(intOf(r.get("id")), strOf(r.get("name")), strOf(r.get("cuisine")), strOf(r.get("area")),
            doubleOf(r.get("distance_km")), intOf(r.get("prep_minutes")), intOf(r.get("cost_for_two")),
            boolOf(r.get("is_pure_veg")), boolOf(r.get("is_open")), strOf(r.get("theme")));
    }

    public Optional<Restaurant> findById(int id) { return findOne("SELECT * FROM restaurants WHERE id = ?", id); }

    public List<Restaurant> findAll() { return findAll("SELECT * FROM restaurants ORDER BY id"); }

    public void setOpen(int id, boolean open) { Db.update("UPDATE restaurants SET is_open = ? WHERE id = ?", open, id); }
}
