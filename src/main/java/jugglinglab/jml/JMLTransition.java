// JMLTransition.java
//
// Copyright 2002-2023 Jack Boyce and the Juggling Lab contributors

package jugglinglab.jml;

import java.io.*;


public class JMLTransition {
    protected int path;
    protected String type;
    protected String mod;
    protected PathLink ipl;
    protected PathLink opl;

    protected int transitiontype;
    public static final int TRANS_NONE = 0;
    public static final int TRANS_THROW = 1;
    public static final int TRANS_CATCH = 2;
    public static final int TRANS_SOFTCATCH = 3;
    public static final int TRANS_GRABCATCH = 4;
    public static final int TRANS_HOLDING = 5;
    public static final int TRANS_ANY = 6;


    public JMLTransition(int transtype, int path, String type, String mod) {
        this.transitiontype = transtype;
        this.type = type;
        this.mod = mod;
        this.path = path;
    }

    public int getType() {
        return transitiontype;
    }

    public void setType(int t) {
        transitiontype = t;
    }

    public int getPath() {
        return path;
    }

    public void setPath(int p) {
        path = p;
    }

    public String getThrowType() {
        return type;
    }

    public void setThrowType(String type) {
        this.type = type;
    }

    public String getMod() {
        return mod;
    }

    public void setMod(String mod) {
        this.mod = mod;
    }

    public void setIncomingPathLink(PathLink ipl) {
        this.ipl = ipl;
    }

    public PathLink getIncomingPathLink() {
        return ipl;
    }

    public void setOutgoingPathLink(PathLink opl) {
        this.opl = opl;
    }

    public PathLink getOutgoingPathLink() {
        return opl;
    }

    public JMLTransition duplicate() {
        JMLTransition tr = new JMLTransition(transitiontype, path, type, mod);
        return tr;
    }

    public void writeJML(PrintWriter wr) throws IOException {
        switch (getType()) {
            case TRANS_THROW:
                String out = "<throw path=\""+Integer.toString(getPath())+"\"";
                if (getThrowType() != null)
                    out += " type=\""+getThrowType()+"\"";
                if (getMod() != null)
                    out += " mod=\""+getMod()+"\"";
                wr.println(out+"/>");
                break;
            case TRANS_CATCH:
                wr.println("<catch path=\""+Integer.toString(getPath())+"\"/>");
                break;
            case TRANS_SOFTCATCH:
                wr.println("<catch path=\""+Integer.toString(getPath())+"\" type=\"soft\"/>");
                break;
            case TRANS_GRABCATCH:
                wr.println("<catch path=\""+Integer.toString(getPath())+"\" type=\"grab\"/>");
                break;
            case TRANS_HOLDING:
                wr.println("<holding path=\""+Integer.toString(getPath())+"\"/>");
                break;
        }
    }
}
