package com.tastetrail.model;

/** ENCAPSULATION: a restaurant and the data needed for delivery fees and delivery-time estimates. */
public class Restaurant {
    private final int id;
    private final String name;
    private final String cuisine;
    private final String area;
    private final double distanceKm;
    private final int prepMinutes;
    private final int costForTwo;
    private final boolean pureVeg;
    private final boolean open;
    private final String theme;

    public Restaurant(int id, String name, String cuisine, String area, double distanceKm, int prepMinutes,
                      int costForTwo, boolean pureVeg, boolean open, String theme) {
        if (distanceKm < 0 || prepMinutes < 0) throw new IllegalArgumentException("Distance and preparation time cannot be negative");
        this.id = id;
        this.name = name;
        this.cuisine = cuisine;
        this.area = area;
        this.distanceKm = distanceKm;
        this.prepMinutes = prepMinutes;
        this.costForTwo = costForTwo;
        this.pureVeg = pureVeg;
        this.open = open;
        this.theme = theme;
    }

    /** Convenience constructor used by tests: only the values that affect the bill. */
    public Restaurant(String name, double distanceKm, int prepMinutes) {
        this(0, name, "", "", distanceKm, prepMinutes, 0, false, true, "amber");
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCuisine() { return cuisine; }
    public String getArea() { return area; }
    public double getDistanceKm() { return distanceKm; }
    public int getPrepMinutes() { return prepMinutes; }
    public int getCostForTwo() { return costForTwo; }
    public boolean isPureVeg() { return pureVeg; }
    public boolean isOpen() { return open; }
    public String getTheme() { return theme; }
}
