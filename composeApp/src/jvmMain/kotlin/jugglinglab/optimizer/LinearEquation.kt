//
// LinearEquation.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.optimizer

class LinearEquation {
    var numVars: Int  // number of independent variables
    var coefs: DoubleArray  // coefficients in linear equation
    var done: Boolean  // flag for use with optimizer

    constructor(vars: Int) {
        numVars = vars
        coefs = DoubleArray(vars + 1)
        done = false
    }

    fun setCoefficients(c: DoubleArray) {
        coefs = c
    }

    fun coef(col: Int): Double {
        return coefs[col]
    }

    fun constant(): Double {
        return coefs[numVars]
    }
}
