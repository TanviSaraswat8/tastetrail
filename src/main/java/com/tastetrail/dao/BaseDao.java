package com.tastetrail.dao;

import com.tastetrail.db.Db;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GENERICS + ABSTRACT CLASS: a Data Access Object turns database rows into Java objects of type T.
 * Each subclass only says how one row becomes one object (map); finding one or many rows is shared.
 */
public abstract class BaseDao<T> {

    /** Converts one database row into an object. */
    protected abstract T map(Map<String, Object> row);

    protected Optional<T> findOne(String sql, Object... params) {
        Map<String, Object> row = Db.one(sql, params);
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    protected List<T> findAll(String sql, Object... params) {
        return Db.query(sql, params).stream().map(this::map).toList();
    }

    // small conversion helpers for JDBC values
    protected static int intOf(Object o) { return ((Number) o).intValue(); }
    protected static double doubleOf(Object o) { return ((Number) o).doubleValue(); }
    protected static boolean boolOf(Object o) { return Boolean.TRUE.equals(o); }
    protected static String strOf(Object o) { return o == null ? null : o.toString(); }
}
