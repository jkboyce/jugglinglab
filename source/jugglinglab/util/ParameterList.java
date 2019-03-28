// ParameterList.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.util;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.StringTokenizer;


public class ParameterList {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected int size;
    protected ArrayList<String> names;
    protected ArrayList<String> values;

    public ParameterList() {
        size = 0;
    }

    public ParameterList(String source) {
        this();
        this.readParameters(source);
    }

    public void addParameter(String name, String value) {
        if (size == 0) {
            names = new ArrayList<String>();
            values = new ArrayList<String>();
        }
        names.add(name);
        values.add(value);
        size++;
    }

    public String getParameter(String name) {
        for (int i = size - 1; i >= 0; i--)
            if (name.equalsIgnoreCase(getParameterName(i)))
                return getParameterValue(i);
        return null;
    }

    public String removeParameter(String name) {
        for (int i = size - 1; i >= 0; i--) {
            if (name.equalsIgnoreCase(getParameterName(i))) {
                size--;
                names.remove(i);
                return values.remove(i);
            }
        }
        return null;
    }

    public String getParameterName(int index) {
        return names.get(index);
    }

    public String getParameterValue(int index) {
        return values.get(index);
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
                if (name.length() != 0 && value.length() != 0)
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

    // Utility function to throw an appropriate error if there are parameters
    // left over after parsing.
    public void errorIfParametersLeft() throws JuggleExceptionUser {
        int count = getNumberOfParameters();

        if (count == 0)
            return;
        else if (count == 1) {
            String template = errorstrings.getString("Error_unrecognized_param");
            Object[] arguments = { getParameterName(0) };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        } else {
            String template = errorstrings.getString("Error_unrecognized_params");
            ArrayList<String> names = new ArrayList<String>();
            for (int i = 0; i < count; i++)
                names.add(getParameterName(i));
            Object[] arguments = { String.join(", ", names) };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
    }
}
