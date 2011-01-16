// ParameterDescriptor.java
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
import javax.swing.*;


public class ParameterDescriptor {
	public static final int TYPE_BOOLEAN = 1;
	public static final int TYPE_FLOAT = 2;
	public static final int TYPE_CHOICE = 3;
	public static final int TYPE_INT = 4;
	public static final int TYPE_ICON = 5;
        
	public String name;
	public int type;
	public Vector range;
	public Object default_value;
	public Object value;
	
	public ParameterDescriptor(String name, int type, Vector range,
				Object default_value, Object value) {
		this.name = name;
		this.type = type;
		this.range = range;
		this.default_value = default_value;
		this.value = value;
	}
}
