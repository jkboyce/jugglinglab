// Prop.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.prop;

import java.awt.*;
import java.util.*;
import jugglinglab.util.*;
import jugglinglab.renderer.*;

// import idx3d.*;

public abstract class Prop {
    // static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        // guistrings = Utf8ResourceBundle.getBundle("GUIStrings");
        errorstrings = Utf8ResourceBundle.getBundle("ErrorStrings");
    }
    
    protected String initString;

    public static final String[] builtinProps = { "Ball", "Image", "Ring" };

    public static Prop getProp(String name) throws JuggleExceptionUser {
        try {
            Object obj = Class.forName("jugglinglab.prop."+name.toLowerCase()+"Prop").newInstance();
            if (!(obj instanceof Prop))
                throw new JuggleExceptionUser("Prop type '"+name+"' doesn't work");
            return (Prop)obj;
        }
        catch (ClassNotFoundException cnfe) {
            throw new JuggleExceptionUser("Prop type '"+name+"' not found");
        }
        catch (IllegalAccessException iae) {
            throw new JuggleExceptionUser("Cannot access '"+name+"' prop file (security)");
        }
        catch (InstantiationException ie) {
            throw new JuggleExceptionUser("Couldn't create '"+name+"' prop");
        }
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
    public abstract Image getProp2DImage(Component comp, double zoom, double[] camangle);
    public abstract Dimension getProp2DSize(Component comp, double zoom);
    public abstract Dimension getProp2DCenter(Component comp, double zoom);
    public abstract Dimension getProp2DGrip(Component comp, double zoom);
    public abstract Object getPropIDX3D();
    public abstract Coordinate getPropIDX3DGrip();
}
