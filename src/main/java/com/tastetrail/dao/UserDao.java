package com.tastetrail.dao;

import com.tastetrail.db.Db;
import com.tastetrail.model.User;

import java.util.Map;
import java.util.Optional;

/** Reads and writes the users table. Returns Customer or Admin objects through User.create(). */
public class UserDao extends BaseDao<User> {

    /** A user together with the stored password hash – only needed while signing in. */
    public record Credentials(User user, String passwordHash) {}

    @Override protected User map(Map<String, Object> r) {
        return User.create(intOf(r.get("id")), strOf(r.get("role")), strOf(r.get("name")), strOf(r.get("email")), strOf(r.get("phone")));
    }

    public Optional<User> findById(int id) {
        return findOne("SELECT id, name, email, phone, role FROM users WHERE id = ?", id);
    }

    public Optional<Credentials> findCredentials(String email) {
        Map<String, Object> r = Db.one("SELECT * FROM users WHERE email = ?", email);
        return r == null ? Optional.empty() : Optional.of(new Credentials(map(r), strOf(r.get("password_hash"))));
    }

    public boolean emailExists(String email) { return Db.one("SELECT id FROM users WHERE email = ?", email) != null; }

    /** Registers a new customer and returns it. */
    public User createCustomer(String name, String email, String phone, String passwordHash) {
        int id = Db.insert("INSERT INTO users (name, email, phone, password_hash) VALUES (?,?,?,?)", name, email, phone, passwordHash);
        return findById(id).orElseThrow();
    }
}
