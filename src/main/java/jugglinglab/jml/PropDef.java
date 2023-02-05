// PropDef.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.jml;

import java.io.*;

import jugglinglab.prop.*;
import jugglinglab.util.*;

public class PropDef {
    String proptype;
    String mod;
    Prop prop;


    public PropDef() {
        proptype = mod = null;
        prop = null;
    }

    public PropDef(String proptype, String mod) {
        this();
        setType(proptype);
        setMod(mod);
    }

    public String getType() {
        return proptype;
    }

    protected void setType(String type) {
        proptype = type;
    }

    public String getMod() {
        return mod;
    }

    protected void setMod(String spec) {
        mod = spec;
    }

    public Prop getProp() {
        return prop;
    }

    public void layoutProp() throws JuggleExceptionUser {
        prop = Prop.newProp(getType());
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
