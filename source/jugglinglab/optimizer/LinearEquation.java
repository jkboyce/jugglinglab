// LinearEquation.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.optimizer;


public class LinearEquation {
    protected int numVars;  // number of variables
    protected double[] coefs;  // coefficients in linear equation
    protected boolean done;  // flag for use with optimizer


    public LinearEquation(int vars) {
        numVars = vars;
        coefs = new double[vars + 1];
        done = false;
    }

    public void setCoefficients(double[] c) {
        coefs = c;
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
        done = d;
    }
}
