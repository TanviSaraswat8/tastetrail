package com.tastetrail.model;

/** INHERITANCE: a Customer is a User who orders food but cannot manage restaurants. */
public class Customer extends User {
    public Customer(int id, String name, String email, String phone) { super(id, name, email, phone); }

    @Override public String getRole() { return "customer"; }
    @Override public boolean canManageRestaurants() { return false; }
    @Override public boolean canPlaceOrders() { return true; }
}
