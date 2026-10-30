package com.tastetrail.web;

/**
 * INTERFACE implemented by every controller. Main.java registers all controllers in a loop
 * without knowing what each one does – POLYMORPHISM.
 */
public interface Controller {
    /** Adds this controller's URL routes to the router. */
    void register(Http.Router router);
}
