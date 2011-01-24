// Notation.java
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

package jugglinglab.notation;


import java.awt.*;
import java.util.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public abstract class Notation {
    // static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        // guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    static Hashtable hash = null;

    // The built-in notations
    public static final String[] builtinNotations = { "Siteswap" };

	// these should be in the same order as in the builtinNotations array
    public static final int NOTATION_NONE = 0;
    public static final int	NOTATION_SITESWAP = 1;

    // This is a factory to create Notations from names.  Note the
    // naming convention.
    public static Notation getNotation(String name) throws JuggleExceptionUser, JuggleExceptionInternal {
        if (hash == null)
            hash = new Hashtable();

        Notation not = (Notation)hash.get(name.toLowerCase());
        if (not == null) {
            Notation newnot = null;
            try {
                Object obj = Class.forName("jugglinglab.notation."+
                                           name.toLowerCase()+"Notation").newInstance();
                if (!(obj instanceof Notation))
                    throw new JuggleExceptionInternal(errorstrings.getString("Error_notation_bad")+": '"+name+"'");
                newnot = (Notation)obj;
            }
            catch (ClassNotFoundException cnfe) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_notation_notfound")+": '"+name+"'");
            }
            catch (IllegalAccessException iae) {
                throw new JuggleExceptionUser(errorstrings.getString("Error_notation_cantaccess")+": '"+name+"'");
            }
            catch (InstantiationException ie) {
                throw new JuggleExceptionInternal(errorstrings.getString("Error_notation_cantcreate")+": '"+name+"'");
            }

            hash.put(name.toLowerCase(), newnot);
            return newnot;
        }
        return not;
    }

    public abstract String getName();
    public abstract JMLPattern getJMLPattern(String pattern) throws JuggleExceptionUser, JuggleExceptionInternal;
}
