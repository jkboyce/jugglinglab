// ParameterList.java
//
// Copyright 2002 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.util;

import java.util.*;


public class ParameterList {
    protected int size;
    protected Vector names;
    protected Vector values;

    public ParameterList() {
        size = 0;
    }

    public ParameterList(String source) {
        this();
        this.readParameters(source);
    }

    public void addParameter(String name, String value) {
        if (size == 0) {
            names = new Vector();
            values = new Vector();
        }
        names.addElement(name);
        values.addElement(value);
        size++;
    }

    public String getParameter(String name) {
        for (int i = size-1; i >= 0; i--)
            if (name.equalsIgnoreCase(getParameterName(i)))
                return getParameterValue(i);
        return null;
    }

    public String getParameterName(int index) {
        return (String)names.elementAt(index);
    }

    public String getParameterValue(int index) {
        return (String)values.elementAt(index);
    }

    public int getNumberOfParameters() {
        return size;
    }

    public void readParameters(String source) {
        if (source == null)
            return;

        StringTokenizer st1 = new StringTokenizer(source, ";");

        while (st1.hasMoreTokens()) {
            String str = st1.nextToken();
            int index = str.indexOf("=");
            if (index > 0) {
                String name = str.substring(0, index).trim();
                String value = str.substring(index + 1).trim();
                if ((name.length() != 0) && (value.length() != 0))
                    addParameter(name, value);
            }
        }
    }

    public String toString() {
        String result = "";

        for (int i = 0; i < size; i++) {
            if (i != 0)
                result += ";";
            result += getParameterName(i) + "=" + getParameterValue(i);
        }

        return result;
    }
}