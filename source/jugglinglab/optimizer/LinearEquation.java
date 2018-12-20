// LinearEquation.java
//
// Copyright 2011 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.optimizer;


public class LinearEquation {
	// static ResourceBundle guistrings;
    // static ResourceBundle errorstrings;
    static {
        // guistrings = JLLocale.getBundle("GUIStrings");
        // errorstrings = JLLocale.getBundle("ErrorStrings");
    }
	
	protected int			numVars;	// number of variables
	protected double[]		coefs;		// coefficients in linear equation
	protected boolean		done;		// flag for use with optimizer
	
	
    public LinearEquation(int vars) {
		this.numVars = vars;
		this.coefs = new double[vars + 1];
		this.done = false;
	}
	
	public void setCoefficients(double[] c) {
		this.coefs = c;
	}
	
	public double coef(int col) {
		return coefs[col];
	}
	
	public double constant() {
		return coefs[numVars];
	}
	
	public boolean done() {
		return done;
	}
	
	public void setDone(boolean d) {
		this.done = d;
	}
}
