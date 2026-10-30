package com.tastetrail.db;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

/** Small JDBC helper around the embedded Apache Derby database. */
public final class Db {
    private static String url;
    private static final ThreadLocal<Connection> TX = new ThreadLocal<>();

    private Db() {}

    public static void init(String path) throws SQLException {
        url = "jdbc:derby:" + path + ";create=true";
        try (Connection c = DriverManager.getConnection(url)) {
            if (!tableExists(c, "USERS")) createSchema(c);
        }
    }

    public static final String[] SCHEMA = {
        """
        CREATE TABLE users (
          id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
          name          VARCHAR(100) NOT NULL,
          email         VARCHAR(150) NOT NULL UNIQUE,
          phone         VARCHAR(15),
          password_hash VARCHAR(200) NOT NULL,
          role          VARCHAR(10) DEFAULT 'customer' NOT NULL CHECK (role IN ('customer','admin')),
          created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
        )""",
        """
        CREATE TABLE restaurants (
          id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
          name          VARCHAR(100) NOT NULL,
          cuisine       VARCHAR(100) NOT NULL,
          area          VARCHAR(60) NOT NULL,
          distance_km   DOUBLE NOT NULL,
          prep_minutes  INT NOT NULL,
          cost_for_two  INT NOT NULL,
          is_pure_veg   BOOLEAN DEFAULT FALSE NOT NULL,
          is_open       BOOLEAN DEFAULT TRUE NOT NULL,
          theme         VARCHAR(20) NOT NULL
        )""",
        """
        CREATE TABLE menu_items (
          id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
          restaurant_id INT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
          name          VARCHAR(100) NOT NULL,
          description   VARCHAR(300),
          category      VARCHAR(30) NOT NULL,
          price         DOUBLE NOT NULL CHECK (price > 0),
          is_veg        BOOLEAN DEFAULT TRUE NOT NULL,
          is_available  BOOLEAN DEFAULT TRUE NOT NULL,
          emoji         VARCHAR(16)
        )""",
        """
        CREATE TABLE coupons (
          code          VARCHAR(20) PRIMARY KEY,
          description   VARCHAR(200),
          type          VARCHAR(10) NOT NULL CHECK (type IN ('percent','flat')),
          amount        DOUBLE NOT NULL,
          max_discount  DOUBLE,
          min_order     DOUBLE DEFAULT 0 NOT NULL,
          active        BOOLEAN DEFAULT TRUE NOT NULL
        )""",
        """
        CREATE TABLE orders (
          id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
          user_id        INT NOT NULL REFERENCES users(id),
          restaurant_id  INT NOT NULL REFERENCES restaurants(id),
          subtotal       DOUBLE NOT NULL,
          discount       DOUBLE DEFAULT 0 NOT NULL,
          delivery_fee   DOUBLE DEFAULT 0 NOT NULL,
          packaging_fee  DOUBLE DEFAULT 0 NOT NULL,
          tax            DOUBLE DEFAULT 0 NOT NULL,
          total          DOUBLE NOT NULL,
          coupon_code    VARCHAR(20) REFERENCES coupons(code),
          payment_method VARCHAR(5) NOT NULL CHECK (payment_method IN ('COD','UPI','CARD')),
          payment_status VARCHAR(10) NOT NULL CHECK (payment_status IN ('PENDING','PAID','REFUNDED')),
          status         VARCHAR(20) DEFAULT 'PLACED' NOT NULL,
          address        VARCHAR(300) NOT NULL,
          eta_minutes    INT,
          created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
          delivered_at   TIMESTAMP
        )""",
        """
        CREATE TABLE order_items (
          id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
          order_id      INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
          menu_item_id  INT NOT NULL REFERENCES menu_items(id),
          name          VARCHAR(100) NOT NULL,
          price         DOUBLE NOT NULL,
          quantity      INT NOT NULL CHECK (quantity > 0)
        )""",
        """
        CREATE TABLE reviews (
          id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
          order_id      INT NOT NULL UNIQUE REFERENCES orders(id),
          user_id       INT NOT NULL REFERENCES users(id),
          restaurant_id INT NOT NULL REFERENCES restaurants(id),
          rating        INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
          comment       VARCHAR(300),
          created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
        )""",
        "CREATE INDEX idx_orders_created ON orders(created_at)",
        "CREATE INDEX idx_orders_status ON orders(status)",
    };

    private static boolean tableExists(Connection c, String name) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM SYS.SYSTABLES WHERE TABLENAME = ?")) {
            p.setString(1, name);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    public static void createSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) { for (String sql : SCHEMA) s.execute(sql); }
    }

    /** Drops every table and recreates the schema (used by the seeder). */
    public static void reset() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            for (String t : new String[]{"REVIEWS", "ORDER_ITEMS", "ORDERS", "COUPONS", "MENU_ITEMS", "RESTAURANTS", "USERS"})
                if (tableExists(c, t)) s.execute("DROP TABLE " + t);
            createSchema(c);
        }
    }

    public static void shutdown() {
        try { DriverManager.getConnection("jdbc:derby:;shutdown=true"); } catch (SQLException ignored) { /* Derby signals shutdown with an exception */ }
    }

    // ---------- query helpers ----------
    private interface Work<T> { T run(Connection c) throws SQLException; }

    private static <T> T with(Work<T> w) {
        Connection tx = TX.get();
        try {
            if (tx != null) return w.run(tx);
            try (Connection c = DriverManager.getConnection(url)) { return w.run(c); }
        } catch (SQLException e) { throw new RuntimeException(e.getMessage(), e); }
    }

    private static void bind(PreparedStatement p, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            if (v instanceof Instant in) p.setTimestamp(i + 1, Timestamp.from(in));
            else p.setObject(i + 1, v);
        }
    }

    /** Rows as maps with lower-case column names; timestamps become ISO-8601 UTC strings. */
    public static List<Map<String, Object>> query(String sql, Object... params) {
        return with(c -> {
            try (PreparedStatement p = c.prepareStatement(sql)) {
                bind(p, params);
                try (ResultSet r = p.executeQuery()) {
                    ResultSetMetaData md = r.getMetaData();
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (r.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            Object v = r.getObject(i);
                            if (v instanceof Timestamp ts) v = ts.toInstant().toString();
                            row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), v);
                        }
                        rows.add(row);
                    }
                    return rows;
                }
            }
        });
    }

    public static Map<String, Object> one(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int update(String sql, Object... params) {
        return with(c -> { try (PreparedStatement p = c.prepareStatement(sql)) { bind(p, params); return p.executeUpdate(); } });
    }

    /** INSERT and return the generated identity key. */
    public static int insert(String sql, Object... params) {
        return with(c -> {
            try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(p, params);
                p.executeUpdate();
                try (ResultSet k = p.getGeneratedKeys()) { k.next(); return k.getInt(1); }
            }
        });
    }

    /** Runs the work in one transaction: everything is committed together or rolled back. */
    public static <T> T tx(Callable<T> work) {
        try (Connection c = DriverManager.getConnection(url)) {
            c.setAutoCommit(false);
            TX.set(c);
            try { T r = work.call(); c.commit(); return r; }
            catch (Exception e) { c.rollback(); throw e instanceof RuntimeException re ? re : new RuntimeException(e); }
            finally { TX.remove(); }
        } catch (SQLException e) { throw new RuntimeException(e.getMessage(), e); }
    }

    public static String placeholders(int n) { return String.join(",", Collections.nCopies(n, "?")); }
}
