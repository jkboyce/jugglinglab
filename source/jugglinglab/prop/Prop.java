// Prop.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.prop;

import java.awt.*;
import java.text.MessageFormat;
import java.util.ResourceBundle;

import jugglinglab.util.*;


public abstract class Prop {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected String initString;

    public static final String[] builtinProps = { "Ball", "Image", "Ring" };

    // Creates a new prop of the given type
    public static Prop newProp(String type) throws JuggleExceptionUser {
        if (type == null)
            throw new JuggleExceptionUser("Prop type not specified");

        if (type.equalsIgnoreCase("ball"))
            return new BallProp();
        else if (type.equalsIgnoreCase("image"))
            return new ImageProp();
        else if (type.equalsIgnoreCase("ring"))
            return new RingProp();

        String template = errorstrings.getString("Error_prop_type");
        Object[] arguments = { type };
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
    }

    public void initProp(String st) throws JuggleExceptionUser {
        initString = st;
        this.init(st);
    }

    public abstract String getType();

    public abstract Color getEditorColor();

    public abstract ParameterDescriptor[] getParameterDescriptors();

    protected abstract void init(String st) throws JuggleExceptionUser;
    public abstract Coordinate getMax();    // in cm
    public abstract Coordinate getMin();    // in cm
    public abstract double getWidth();      // prop width in cm
    public abstract Image getProp2DImage(double zoom, double[] camangle);
    public abstract Dimension getProp2DSize(double zoom);
    public abstract Dimension getProp2DCenter(double zoom);
    public abstract Dimension getProp2DGrip(double zoom);

    /*
    public abstract Object getPropIDX3D();
    public abstract Coordinate getPropIDX3DGrip();
    */
}
