package com.tastetrail.model;

/** INHERITANCE: an Admin is a User who runs the restaurants (orders, menu, analytics). */
public class Admin extends User {
    public Admin(int id, String name, String email, String phone) { super(id, name, email, phone); }

    @Override public String getRole() { return "admin"; }
    @Override public boolean canManageRestaurants() { return true; }
    @Override public boolean canPlaceOrders() { return false; }
}
