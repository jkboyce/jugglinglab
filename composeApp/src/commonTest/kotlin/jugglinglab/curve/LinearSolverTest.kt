//
// LinearSolverTest.kt
//
// Unit tests for the linear solver in SplineCurve.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.curve

import jugglinglab.util.JuggleExceptionInternal
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.math.abs
import kotlin.test.assertFailsWith

class LinearSolverTest {
    @Test
    fun `test identity matrix`() {
        val A = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0)
        )
        val b = doubleArrayOf(1.0, 2.0, 3.0)
        val x = SplineCurve.solveLinearSystem(A, b)
        
        assertVectorEquals(doubleArrayOf(1.0, 2.0, 3.0), x)
    }

    @Test
    fun `test simple system`() {
        // 2x + y = 5
        // x + 3y = 5
        // Solution: x = 2, y = 1
        val A = arrayOf(
            doubleArrayOf(2.0, 1.0),
            doubleArrayOf(1.0, 3.0)
        )
        val b = doubleArrayOf(5.0, 5.0)
        val x = SplineCurve.solveLinearSystem(A, b)
        
        assertVectorEquals(doubleArrayOf(2.0, 1.0), x)
    }

    @Test
    fun `test zero pivot`() {
        // 0x + y = 1
        // x + y = 2
        // Solution: x = 1, y = 1
        // Requires swapping rows
        val A = arrayOf(
            doubleArrayOf(0.0, 1.0),
            doubleArrayOf(1.0, 1.0)
        )
        val b = doubleArrayOf(1.0, 2.0)
        val x = SplineCurve.solveLinearSystem(A, b)
        
        assertVectorEquals(doubleArrayOf(1.0, 1.0), x)
    }
    
    @Test
    fun `test 3x3`() {
        // x + 2y + 3z = 9
        // 2x - y + z = 8
        // 3x + 0y - z = 3
        // Solution: x = 2, y = -1, z = 3
        val A = arrayOf(
            doubleArrayOf(1.0, 2.0, 3.0),
            doubleArrayOf(2.0, -1.0, 1.0),
            doubleArrayOf(3.0, 0.0, -1.0)
        )
        val b = doubleArrayOf(9.0, 8.0, 3.0)
        val x = SplineCurve.solveLinearSystem(A, b)
        
        assertVectorEquals(doubleArrayOf(2.0, -1.0, 3.0), x)
    }

    @Test
    fun `test singular matrix`() {
        // Singular matrix
        // x + y = 1
        // 2x + 2y = 2
        val A = arrayOf(
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(2.0, 2.0)
        )
        val b = doubleArrayOf(1.0, 2.0)
        
        assertFailsWith<JuggleExceptionInternal> {
            SplineCurve.solveLinearSystem(A, b)
        }
    }

    private fun assertVectorEquals(expected: DoubleArray, actual: DoubleArray, epsilon: Double = 1e-10) {
        assertTrue(expected.size == actual.size, "Vector size mismatch")
        for (i in expected.indices) {
            assertTrue(abs(expected[i] - actual[i]) < epsilon, "Mismatch at index $i: expected ${expected[i]}, got ${actual[i]}")
        }
    }
}
