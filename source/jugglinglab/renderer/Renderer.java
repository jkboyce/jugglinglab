// Renderer.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

import jugglinglab.jml.JMLPattern;
import jugglinglab.util.Coordinate;
import jugglinglab.util.JuggleExceptionInternal;


public abstract class Renderer {
    protected boolean showground;

    public void setGround(boolean showground) {
        this.showground = showground;
    }

    protected static JLVector toVector(Coordinate c, JLVector result) {
        result.x = c.x;
        result.y = c.z;
        result.z = c.y;
        return result;
    }

    public abstract void setPattern(JMLPattern pat);
    public abstract void initDisplay(Dimension dim, int border,
                Coordinate overallmax, Coordinate overallmin);

    public abstract void setCameraAngle(double[] angle);
    public abstract double[] getCameraAngle();

    // the following return results in local coordinates
    public abstract Coordinate getHandWindowMax();
    public abstract Coordinate getHandWindowMin();

    // the following return results in global coordinates
    public abstract Coordinate getJugglerWindowMax();
    public abstract Coordinate getJugglerWindowMin();

    public abstract int[] getXY(Coordinate coord);  // pixel coordinates
    public abstract Coordinate getScreenTranslatedCoordinate(Coordinate coord,
                int dx, int dy);

    public abstract void drawFrame(double time, int[] pnum, int[] hideJugglers, Graphics g)
                throws JuggleExceptionInternal;

    public abstract Color getBackground();
}
