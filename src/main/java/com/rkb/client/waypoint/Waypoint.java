package com.rkb.client.waypoint;

/** One saved location. Plain fields so Gson can read and write it. */
public class Waypoint {
    public String name = "Waypoint";
    public int x;
    public int y;
    public int z;
    public String dim = "minecraft:overworld";
    /** Index into WaypointStore.COLORS. */
    public int color;
    public boolean visible = true;

    public Waypoint() {
    }

    public Waypoint(String name, int x, int y, int z, String dim, int color) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.color = color;
    }
}
