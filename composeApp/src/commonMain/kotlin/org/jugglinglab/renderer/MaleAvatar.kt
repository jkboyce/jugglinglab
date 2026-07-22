//
// MaleAvatar.kt
//
// The classic Juggling Lab stick figure, and the default avatar. It inherits
// the shared skeleton, head and arms unchanged, and only defines its torso
// outline — so its rendered output is identical to the historical renderer.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import androidx.compose.ui.graphics.Path

class MaleAvatar : Avatar() {
    // Torso = the shoulders -> waist trapezoid.
    override fun buildTorsoPath(body: DrawObject2D, path: Path) {
        val c = body.coord
        path.moveTo(c[LEFT_SHOULDER].x.toFloat(), c[LEFT_SHOULDER].y.toFloat())
        path.lineTo(c[RIGHT_SHOULDER].x.toFloat(), c[RIGHT_SHOULDER].y.toFloat())
        path.lineTo(c[RIGHT_WAIST].x.toFloat(), c[RIGHT_WAIST].y.toFloat())
        path.lineTo(c[LEFT_WAIST].x.toFloat(), c[LEFT_WAIST].y.toFloat())
    }
}
