// JMLAttributes.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.jml;

import java.util.*;


public class JMLAttributes {
    protected JMLNode parentTag;
    protected int size;
    protected ArrayList<String> names;
    protected ArrayList<String> values;

    public JMLAttributes(JMLNode parent) {
        parentTag = parent;
        size = 0;
    }

    public int getNumberOfAttributes() {
        return size;
    }

    public void addAttribute(String name, String value) {
        if (size == 0) {
            names = new ArrayList<String>();
            values = new ArrayList<String>();
        }
        names.add(name);
        values.add(value);
        size++;
    }

    public String getAttributeName(int index) {
        return names.get(index);
    }

    public String getAttributeValue(int index) {
        return values.get(index);
    }

    public String getAttribute(String name) {
        for (int i = 0; i < size; i++)
            if (name.equalsIgnoreCase(getAttributeName(i)))
                return getAttributeValue(i);
        return null;
    }
}
