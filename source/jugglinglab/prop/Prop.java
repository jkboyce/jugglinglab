// Prop.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.prop;

import java.awt.*;
import java.util.*;
import java.text.MessageFormat;

import jugglinglab.util.*;
import jugglinglab.renderer.*;


public abstract class Prop {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected String initString;

    public static final String[] builtinProps = { "Ball", "Image", "Ring" };

    public static Prop getProp(String name) throws JuggleExceptionUser {
        try {
            Object obj = Class.forName("jugglinglab.prop."+name.toLowerCase()+"Prop").newInstance();
            if (obj instanceof Prop)
                return (Prop)obj;
        }
        catch (ClassNotFoundException cnfe) {
        }
        catch (IllegalAccessException iae) {
        }
        catch (InstantiationException ie) {
        }

        String template = errorstrings.getString("Error_prop_type");
        Object[] arguments = { name };
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
    }

    public abstract String getName();

    public abstract Color getEditorColor();

    public abstract ParameterDescriptor[] getParameterDescriptors();

    public void initProp(String st) throws JuggleExceptionUser {
        initString = st;
        this.init(st);
    }

    protected abstract void init(String st) throws JuggleExceptionUser;
    public abstract Coordinate getMax();
    public abstract Coordinate getMin();
    public abstract Image getProp2DImage(double zoom, double[] camangle);
    public abstract Dimension getProp2DSize(double zoom);
    public abstract Dimension getProp2DCenter(double zoom);
    public abstract Dimension getProp2DGrip(double zoom);

    /*
    public abstract Object getPropIDX3D();
    public abstract Coordinate getPropIDX3DGrip();
    */
}
