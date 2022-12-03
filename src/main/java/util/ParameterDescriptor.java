// ParameterDescriptor.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;

import java.util.*;


public class ParameterDescriptor {
    public static final int TYPE_BOOLEAN = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_CHOICE = 3;
    public static final int TYPE_INT = 4;
    public static final int TYPE_ICON = 5;

    public String name;
    public int type;
    public ArrayList<String> range;
    public Object default_value;
    public Object value;


    public ParameterDescriptor(String name, int type, ArrayList<String> range,
                Object default_value, Object value) {
        this.name = name;
        this.type = type;
        this.range = range;
        this.default_value = default_value;
        this.value = value;
    }
}
