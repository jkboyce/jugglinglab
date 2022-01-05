// Coordinate.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;


// Simple container class

public class Coordinate {
    public double x, y, z;


    public Coordinate() {
        this(0.0, 0.0, 0.0);
    }

    public Coordinate(double x, double y) {
        this(x, y, 0.0);
    }

    public Coordinate(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Coordinate(Coordinate c) {
        x = c.x;
        y = c.y;
        z = c.z;
    }

    public void setCoordinate(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getIndex(int index) {
        if (index == 0)
            return x;
        else if (index == 1)
            return y;
        else
            return z;
    }

    public static Coordinate max(Coordinate coord1, Coordinate coord2) {
        if (coord1 == null)
            return coord2;
        if (coord2 == null)
            return coord1;
        return new Coordinate(Math.max(coord1.x, coord2.x),
                              Math.max(coord1.y, coord2.y),
                              Math.max(coord1.z, coord2.z));
    }

    public static Coordinate min(Coordinate coord1, Coordinate coord2) {
        if (coord1 == null)
            return coord2;
        if (coord2 == null)
            return coord1;
        return new Coordinate(Math.min(coord1.x, coord2.x),
                              Math.min(coord1.y, coord2.y),
                              Math.min(coord1.z, coord2.z));
    }

    public static Coordinate add(Coordinate coord1, Coordinate coord2) {
        if (coord1 == null)
            return coord2;
        if (coord2 == null)
            return coord1;
        return new Coordinate(coord1.x + coord2.x,
                              coord1.y + coord2.y,
                              coord1.z + coord2.z);
    }

    public static Coordinate sub(Coordinate coord1, Coordinate coord2) {
        if (coord1 == null)
            return coord2;
        if (coord2 == null)
            return coord1;
        return new Coordinate(coord1.x - coord2.x,
                              coord1.y - coord2.y,
                              coord1.z - coord2.z);
    }

    public boolean isValid() {
        if (Double.isNaN(this.x) || Double.isInfinite(this.x) ||
                Double.isNaN(this.y) || Double.isInfinite(this.y) ||
                Double.isNaN(this.z) || Double.isInfinite(this.z))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return ("("+x+","+y+","+z+")");
    }
}
