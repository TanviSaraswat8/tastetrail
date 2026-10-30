package com.tastetrail.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ABSTRACTION + ENCAPSULATION: the common parent of every kind of user.
 * Fields are private and can only be read through getters. Each subclass decides
 * what it is allowed to do by overriding the abstract methods (POLYMORPHISM).
 */
public abstract class User {
    private final int id;
    private final String name;
    private final String email;
    private final String phone;

    protected User(int id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getFirstName() { return name.split(" ")[0]; }

    /** "customer" or "admin" – stored in the database and inside the login token. */
    public abstract String getRole();

    /** Can this user manage orders, menus and see analytics? */
    public abstract boolean canManageRestaurants();

    /** Can this user place food orders? */
    public abstract boolean canPlaceOrders();

    /** FACTORY METHOD: builds the right subclass from the role stored in the database. */
    public static User create(int id, String role, String name, String email, String phone) {
        return "admin".equals(role) ? new Admin(id, name, email, phone) : new Customer(id, name, email, phone);
    }

    /** The public view of a user sent to the browser (never includes the password hash). */
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("email", email);
        m.put("phone", phone);
        m.put("role", getRole());
        return m;
    }

    @Override public String toString() { return getClass().getSimpleName() + "#" + id + " (" + name + ")"; }
}
