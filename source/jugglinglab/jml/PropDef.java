// PropDef.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.jml;

import java.util.*;
import java.io.*;

import jugglinglab.prop.*;
import jugglinglab.util.*;

public class PropDef {
    String  proptype, mod;
    Prop    prop;

    public PropDef() {
        proptype = mod = null;
        prop = null;
    }

    public PropDef(String proptype, String mod) {
        this();
        setType(proptype);
        setMod(mod);
    }

    public String getType()             { return proptype; }
    protected void setType(String type) { this.proptype = type; }
    public String getMod()              { return mod; }
    protected void setMod(String spec)  { this.mod = spec; }
    public Prop getProp()               { return prop; }

    public void layoutProp() throws JuggleExceptionUser {
        this.prop = Prop.newProp(getType());
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
