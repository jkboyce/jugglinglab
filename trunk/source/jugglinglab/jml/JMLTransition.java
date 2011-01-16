// JMLTransition.java
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

import java.io.*;


public class JMLTransition {
    protected int path;
    protected String type;
    protected String mod;
    protected PathLink ipl, opl;

    protected int transitiontype;
    public static final int TRANS_NONE = 0;
    public static final int TRANS_THROW = 1;
    public static final int TRANS_CATCH = 2;
    public static final int TRANS_SOFTCATCH = 3;
    public static final int TRANS_HOLDING = 4;
    public static final int TRANS_ANY = 5;


    public JMLTransition(int transtype, int path, String type, String mod) {
        this.transitiontype = transtype;
        this.type = type;
        this.mod = mod;
        this.ipl = this.opl = null;
        this.path = path;
    }

    public int getType()			{ return transitiontype; }
    public void setType(int t)			{ this.transitiontype = t; }
    public int getPath()			{ return path; }
    public void setPath(int p)			{ this.path = p; }
    public String getThrowType()		{ return type; }
    public void setThrowType(String type)	{ this.type = type; }
    public String getMod()			{ return mod; }
    public void setMod(String mod)		{ this.mod = mod; }

    public void setIncomingPathLink(PathLink ipl)	{ this.ipl = ipl; }
    public PathLink getIncomingPathLink()		{ return ipl; }
    public void setOutgoingPathLink(PathLink opl)	{ this.opl = opl; }
    public PathLink getOutgoingPathLink()		{ return opl; }

    public JMLTransition duplicate() {
        JMLTransition tr = new JMLTransition(transitiontype, path,
                                             type, mod);
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
                wr.println("<softcatch path=\""+Integer.toString(getPath())+"\"/>");
                break;
            case TRANS_HOLDING:
                wr.println("<holding path=\""+Integer.toString(getPath())+"\"/>");
                break;
        }
    }
}