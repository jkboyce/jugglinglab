// LinearEquation.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.optimizer;


public class LinearEquation {
    protected int           numVars;    // number of variables
    protected double[]      coefs;      // coefficients in linear equation
    protected boolean       done;       // flag for use with optimizer


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
