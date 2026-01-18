//
// Renderer.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.renderer

import jugglinglab.jml.JmlPattern
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics

abstract class Renderer {
    protected var showground: Boolean = false

    fun setGround(showground: Boolean) {
        this.showground = showground
    }

    abstract fun setPattern(pat: JmlPattern)

    abstract fun initDisplay(
        dim: Dimension, border: Int, overallmax: Coordinate, overallmin: Coordinate
    )

    abstract var cameraAngle: DoubleArray

    // the following two methods return results in local coordinates
    abstract val handWindowMax: Coordinate
    abstract val handWindowMin: Coordinate

    // the following two methods return results in global coordinates,
    // including any body movement during the pattern
    abstract val jugglerWindowMax: Coordinate
    abstract val jugglerWindowMin: Coordinate

    // Translate from global coordinate `coord` to (x, y) pixel coordinates
    abstract fun getXY(coord: Coordinate): IntArray

    // Translate a global coordinate `coord` by `dx` and `dy` pixels, and return
    // the result as a global coordinate
    abstract fun getScreenTranslatedCoordinate(coord: Coordinate, dx: Int, dy: Int): Coordinate

    @Throws(JuggleExceptionInternal::class)
    abstract fun drawFrame(time: Double, pnum: List<Int>, hideJugglers: List<Int>, g: Graphics)

    abstract fun getBackground(): Color

    abstract fun getZoomLevel(): Double

    abstract fun setZoomLevel(zoomfactor: Double)
}
