// JMLAttributes.java
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

package jugglinglab.jml;

import java.util.*;


public class JMLAttributes {
    protected JMLNode parentTag;
    protected int size;
    protected Vector names;
    protected Vector values;

    public JMLAttributes(JMLNode parent) {
        parentTag = parent;
        size = 0;
    }

    public int getNumberOfAttributes() {
        return size;
    }

    public void addAttribute(String name, String value) {
        if (size == 0) {
            names = new Vector();
            values = new Vector();
        }
        names.addElement(name);
        values.addElement(value);
        size++;
    }

    public String getAttributeName(int index) {
        return (String)names.elementAt(index);
    }

    public String getAttributeValue(int index) {
        return (String)values.elementAt(index);
    }

    public String getAttribute(String name) {
        for (int i = 0; i < size; i++)
            if (name.equalsIgnoreCase(getAttributeName(i)))
                return getAttributeValue(i);
        return null;
    }
}