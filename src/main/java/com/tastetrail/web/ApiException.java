package com.tastetrail.web;

/**
 * EXCEPTION HIERARCHY: the base class for every error the API reports to the browser.
 * Each subclass fixes its own HTTP status code, so handlers just throw the right type
 * and the router turns it into a JSON error message.
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) { super(message); this.status = status; }

    public int getStatus() { return status; }

    /** 400 – the request is wrong (missing field, invalid value). */
    public static class BadRequest extends ApiException { public BadRequest(String m) { super(400, m); } }

    /** 401 – the visitor is not signed in or the token is invalid. */
    public static class Unauthorized extends ApiException { public Unauthorized(String m) { super(401, m); } }

    /** 403 – signed in, but not allowed (for example a customer calling an admin endpoint). */
    public static class Forbidden extends ApiException { public Forbidden(String m) { super(403, m); } }

    /** 404 – the thing does not exist, or belongs to someone else. */
    public static class NotFound extends ApiException { public NotFound(String m) { super(404, m); } }

    /** 409 – conflicts with existing data (duplicate e-mail, second review). */
    public static class Conflict extends ApiException { public Conflict(String m) { super(409, m); } }
}
