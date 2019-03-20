// Notation.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.awt.*;
import java.util.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public abstract class Notation {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    static Hashtable<String, Notation> hash;

    // The built-in notations
    public static final String[] builtinNotations = { "Siteswap" };

    // these should be in the same order as in the builtinNotations array
    public static final int NOTATION_NONE = 0;
    public static final int NOTATION_SITESWAP = 1;

    // This is a factory to create Notations from names.  Note the
    // naming convention.
    public static Notation getNotation(String name) throws JuggleExceptionUser,
                                                JuggleExceptionInternal {
        if (hash == null)
            hash = new Hashtable<String, Notation>();

        Notation not = hash.get(name.toLowerCase());
        if (not == null) {
            Notation newnot = null;
            try {
                Object obj = Class.forName("jugglinglab.notation."+
                                           name.toLowerCase()+"Notation").newInstance();
                if (!(obj instanceof Notation))
                    throw new JuggleExceptionInternal(errorstrings.getString(
                                        "Error_notation_bad")+": '"+name+"'");
                newnot = (Notation)obj;
            }
            catch (ClassNotFoundException cnfe) {
                throw new JuggleExceptionUser(errorstrings.getString(
                                        "Error_notation_notfound")+": '"+name+"'");
            }
            catch (IllegalAccessException iae) {
                throw new JuggleExceptionUser(errorstrings.getString(
                                        "Error_notation_cantaccess")+": '"+name+"'");
            }
            catch (InstantiationException ie) {
                throw new JuggleExceptionInternal(errorstrings.getString(
                                        "Error_notation_cantcreate")+": '"+name+"'");
            }

            hash.put(name.toLowerCase(), newnot);
            return newnot;
        }
        return not;
    }

    public abstract String getName();

    // This is the important method: Convert a pattern in the notation to JML
    public abstract JMLPattern getJMLPattern(String pattern) throws
                        JuggleExceptionUser, JuggleExceptionInternal;
}
