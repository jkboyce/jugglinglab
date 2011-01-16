// PropDef.java
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
import java.io.*;

import jugglinglab.prop.*;
import jugglinglab.util.*;

public class PropDef {
    String	proptype, mod;
    Prop	prop;

    public PropDef() {
        proptype = mod = null;
        prop = null;
    }

    public PropDef(String proptype, String mod) {
        this();
        setType(proptype);
        setMod(mod);
    }

    public String getType()		{ return proptype; }
    protected void setType(String type)	{ this.proptype = type; }
    public String getMod()		{ return mod; }
    protected void setMod(String spec) 	{ this.mod = spec; }
    public Prop getProp()		{ return prop; }

    public void layoutProp() throws JuggleExceptionUser {
        this.prop = Prop.getProp(getType());
        prop.initProp(getMod());
    }

    public void readJML(JMLNode current, String version) {
        JMLAttributes at = current.getAttributes();

        setType(at.getAttribute("type"));
        setMod(at.getAttribute("mod"));
    }

    public void writeJML(PrintWriter wr) throws IOException {
        String out = "<prop type=\""+proptype+"\"";
        if (mod != null)
            out += " mod=\""+mod+"\"/>";
        else
            out += "/>";
        wr.println(out);
    }
}