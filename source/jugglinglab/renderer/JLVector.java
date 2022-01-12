// JLVector.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.renderer;


public class JLVector {
    public double x;
    public double y;
    public double z;


    public JLVector() {
    }

    public JLVector(double xpos, double ypos, double zpos) {
        x = xpos;
        y = ypos;
        z = zpos;
    }

    public double length() {
        return Math.sqrt(x*x + y*y + z*z);
    }

    public JLVector transform(JLMatrix m) {
        double newx = x * m.m00 + y * m.m01 + z * m.m02 + m.m03;
        double newy = x * m.m10 + y * m.m11 + z * m.m12 + m.m13;
        double newz = x * m.m20 + y * m.m21 + z * m.m22 + m.m23;
        return new JLVector(newx, newy, newz);
    }

    public static JLVector add(JLVector a, JLVector b) {
        return new JLVector(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    public static JLVector sub(JLVector a, JLVector b) {
        return new JLVector(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    public static JLVector scale(double f, JLVector a) {
        return new JLVector(f * a.x, f * a.y, f * a.z);
    }
}
