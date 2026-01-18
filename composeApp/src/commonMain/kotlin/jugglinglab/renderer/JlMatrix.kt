//
// JlMatrix.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.renderer

import kotlin.math.cos
import kotlin.math.sin

class JlMatrix {
    var m00: Double = 1.0
    var m01: Double = 0.0
    var m02: Double = 0.0
    var m03: Double = 0.0
    var m10: Double = 0.0
    var m11: Double = 1.0
    var m12: Double = 0.0
    var m13: Double = 0.0
    var m20: Double = 0.0
    var m21: Double = 0.0
    var m22: Double = 1.0
    var m23: Double = 0.0
    var m30: Double = 0.0
    var m31: Double = 0.0
    var m32: Double = 0.0
    var m33: Double = 1.0

    // Identity matrix
    constructor()

    @Suppress("unused")
    fun shift(dx: Double, dy: Double, dz: Double) {
        transform(shiftMatrix(dx, dy, dz))
    }

    fun scale(dx: Double, dy: Double, dz: Double) {
        transform(scaleMatrix(dx, dy, dz))
    }

    fun scale(d: Double) {
        transform(scaleMatrix(d))
    }

    @Suppress("unused")
    fun rotate(dx: Double, dy: Double, dz: Double) {
        transform(rotateMatrix(dx, dy, dz))
    }

    fun transform(n: JlMatrix) {
        val m = this.clone
        m00 = n.m00 * m.m00 + n.m01 * m.m10 + n.m02 * m.m20
        m01 = n.m00 * m.m01 + n.m01 * m.m11 + n.m02 * m.m21
        m02 = n.m00 * m.m02 + n.m01 * m.m12 + n.m02 * m.m22
        m03 = n.m00 * m.m03 + n.m01 * m.m13 + n.m02 * m.m23 + n.m03
        m10 = n.m10 * m.m00 + n.m11 * m.m10 + n.m12 * m.m20
        m11 = n.m10 * m.m01 + n.m11 * m.m11 + n.m12 * m.m21
        m12 = n.m10 * m.m02 + n.m11 * m.m12 + n.m12 * m.m22
        m13 = n.m10 * m.m03 + n.m11 * m.m13 + n.m12 * m.m23 + n.m13
        m20 = n.m20 * m.m00 + n.m21 * m.m10 + n.m22 * m.m20
        m21 = n.m20 * m.m01 + n.m21 * m.m11 + n.m22 * m.m21
        m22 = n.m20 * m.m02 + n.m21 * m.m12 + n.m22 * m.m22
        m23 = n.m20 * m.m03 + n.m21 * m.m13 + n.m22 * m.m23 + n.m23
    }

    val clone: JlMatrix
        get() {
            val m = JlMatrix()
            m.m00 = m00
            m.m01 = m01
            m.m02 = m02
            m.m03 = m03
            m.m10 = m10
            m.m11 = m11
            m.m12 = m12
            m.m13 = m13
            m.m20 = m20
            m.m21 = m21
            m.m22 = m22
            m.m23 = m23
            m.m30 = m30
            m.m31 = m31
            m.m32 = m32
            m.m33 = m33
            return m
        }

    fun inverse(): JlMatrix {
        val m = JlMatrix()
        val q1 = m12
        val q6 = m10 * m01
        val q7 = m10 * m21
        val q8 = m02
        val q13 = m20 * m01
        val q14 = m20 * m11
        val q21 = m02 * m21
        val q22 = m03 * m21
        val q25 = m01 * m12
        val q26 = m01 * m13
        val q27 = m02 * m11
        val q28 = m03 * m11
        val q29 = m10 * m22
        val q30 = m10 * m23
        val q31 = m20 * m12
        val q32 = m20 * m13
        val q35 = m00 * m22
        val q36 = m00 * m23
        val q37 = m20 * m02
        val q38 = m20 * m03
        val q41 = m00 * m12
        val q42 = m00 * m13
        val q43 = m10 * m02
        val q44 = m10 * m03
        val q45 = m00 * m11
        val q48 = m00 * m21
        val q49 = q45 * m22 - q48 * q1 - q6 * m22 + q7 * q8
        val q50 = q13 * q1 - q14 * q8
        val q51 = 1.0 / (q49 + q50)

        m.m00 = (m11 * m22 * m33 - m11 * m23 * m32 - m21 * m12 * m33 + m21 * m13 * m32 +
            m31 * m12 * m23 - m31 * m13 * m22) * q51
        m.m01 = -(m01 * m22 * m33 - m01 * m23 * m32 - q21 * m33 + q22 * m32) * q51
        m.m02 = (q25 * m33 - q26 * m32 - q27 * m33 + q28 * m32) * q51
        m.m03 = -(q25 * m23 - q26 * m22 - q27 * m23 + q28 * m22 + q21 * m13 - q22 * m12) * q51
        m.m10 = -(q29 * m33 - q30 * m32 - q31 * m33 + q32 * m32) * q51
        m.m11 = (q35 * m33 - q36 * m32 - q37 * m33 + q38 * m32) * q51
        m.m12 = -(q41 * m33 - q42 * m32 - q43 * m33 + q44 * m32) * q51
        m.m13 = (q41 * m23 - q42 * m22 - q43 * m23 + q44 * m22 + q37 * m13 - q38 * m12) * q51
        m.m20 = (q7 * m33 - q30 * m31 - q14 * m33 + q32 * m31) * q51
        m.m21 = -(q48 * m33 - q36 * m31 - q13 * m33 + q38 * m31) * q51
        m.m22 = (q45 * m33 - q42 * m31 - q6 * m33 + q44 * m31) * q51
        m.m23 = -(q45 * m23 - q42 * m21 - q6 * m23 + q44 * m21 + q13 * m13 - q38 * m11) * q51
        return m
    }

    companion object {
        fun shiftMatrix(dx: Double, dy: Double, dz: Double): JlMatrix {
            val m = JlMatrix()
            m.m03 = dx
            m.m13 = dy
            m.m23 = dz
            return m
        }

        fun scaleMatrix(dx: Double, dy: Double, dz: Double): JlMatrix {
            val m = JlMatrix()
            m.m00 = dx
            m.m11 = dy
            m.m22 = dz
            return m
        }

        fun scaleMatrix(d: Double): JlMatrix {
            return scaleMatrix(d, d, d)
        }

        fun rotateMatrix(dx: Double, dy: Double, dz: Double): JlMatrix {
            val out = JlMatrix()
            var sine: Double
            var cosine: Double

            if (dx != 0.0) {
                val m = JlMatrix()
                sine = sin(dx)
                cosine = cos(dx)
                m.m11 = cosine
                m.m12 = sine
                m.m21 = -sine
                m.m22 = cosine
                out.transform(m)
            }
            if (dy != 0.0) {
                val m = JlMatrix()
                sine = sin(dy)
                cosine = cos(dy)
                m.m00 = cosine
                m.m02 = sine
                m.m20 = -sine
                m.m22 = cosine
                out.transform(m)
            }
            if (dz != 0.0) {
                val m = JlMatrix()
                sine = sin(dz)
                cosine = cos(dz)
                m.m00 = cosine
                m.m01 = sine
                m.m10 = -sine
                m.m11 = cosine
                out.transform(m)
            }
            return out
        }
    }
}
