//
// FemaleAvatar.kt
//
// The female juggler avatar: a slimmer frame, a flared dress, and a side-swept
// ponytail. All of it is real 3D geometry projected like the rest of the
// skeleton, so it works at every camera angle, in stereo, and in GIF export.
// Every dimension lives in FemaleConfig so the figure can be tuned (dress
// length, ponytail, hips, ...) without code changes.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.util.Coordinate
import androidx.compose.ui.graphics.Path
import kotlin.math.sqrt

// Adjustable dimensions of the female avatar, in centimeters. Defaults are the
// reference design; a different look is just a different FemaleConfig.
data class FemaleConfig(
    val shoulderHW: Double = 18.0,      // shoulder half-width (narrower than default)
    val waistHW: Double = 13.0,         // cinched waist
    val hipHW: Double = 21.0,           // hips, wider than the waist
    val hipH: Double = -18.0,           // hip height, below the waist
    val hemHW: Double = 34.0,           // flared hem half-width
    val hemH: Double = -44.0,           // hem height (i.e. dress length)
    val ponytailSide: Double = 11.0,    // anchor: to the side of the head
    val ponytailBack: Double = 5.0,     // anchor: slightly behind the head
    val ponytailTipSide: Double = 16.0, // tip: further to the side
    val ponytailTipBack: Double = 9.0   // tip: further behind (ponytail length)
)

class FemaleAvatar(private val cfg: FemaleConfig = FemaleConfig()) : Avatar() {
    override val shoulderHW: Double get() = cfg.shoulderHW
    override val waistHW: Double get() = cfg.waistHW

    override val pointCount: Int get() = EXTENDED_POINT_COUNT

    // Hips and hem join the bounding box so arms and props sort correctly
    // against the skirt; the ponytail is deliberately excluded (it is small,
    // and leaving it out keeps the cheap overlap test closest to the classic
    // behavior).
    override val boundsPoints: List<Int> =
        TORSO_AND_HEAD_POINTS + listOf(LEFT_HIP, RIGHT_HIP, LEFT_HEM, RIGHT_HEM)

    //--------------------------------------------------------------------------
    // Extra 3D points: hips, flared hem, and the ponytail
    //--------------------------------------------------------------------------

    override fun computeExtraPoints(
        pos: Coordinate, s: Double, c: Double, out: Array<JlVector?>
    ) {
        out[LEFT_HIP] = bodyPoint(pos, -cfg.hipHW, cfg.hipH, s, c)
        out[RIGHT_HIP] = bodyPoint(pos, cfg.hipHW, cfg.hipH, s, c)
        out[LEFT_HEM] = bodyPoint(pos, -cfg.hemHW, cfg.hemH, s, c)
        out[RIGHT_HEM] = bodyPoint(pos, cfg.hemHW, cfg.hemH, s, c)

        // Ponytail anchor/tip: a side offset keeps it visible from the front,
        // a back offset keeps it visible from the side.
        val neckTop = Juggler.SHOULDER_H + Juggler.NECK_H
        out[PONYTAIL_ANCHOR] = ponytailPoint(
            pos, cfg.ponytailSide, cfg.ponytailBack, neckTop + 0.9 * Juggler.HEAD_H, s, c
        )
        out[PONYTAIL_TIP] = ponytailPoint(
            pos, cfg.ponytailTipSide, cfg.ponytailTipBack, neckTop + 0.2 * Juggler.HEAD_H, s, c
        )
    }

    // Like bodyPoint, but with an extra offset along the juggler's back axis.
    private fun ponytailPoint(
        pos: Coordinate, side: Double, back: Double, h: Double, s: Double, c: Double
    ) = JlVector(
        pos.x + side * c + back * s,
        pos.z + h,
        pos.y + side * s - back * c
    )

    //--------------------------------------------------------------------------
    // Drawing
    //--------------------------------------------------------------------------

    // Dress silhouette: shoulders -> waist -> hips -> flared hem, as one closed
    // outline. Fill-with-background + stroke happens in the shared drawTorso().
    override fun buildTorsoPath(body: DrawObject2D, path: Path) {
        val c = body.coord
        path.moveTo(c[LEFT_SHOULDER].x.toFloat(), c[LEFT_SHOULDER].y.toFloat())
        path.lineTo(c[RIGHT_SHOULDER].x.toFloat(), c[RIGHT_SHOULDER].y.toFloat())
        path.lineTo(c[RIGHT_WAIST].x.toFloat(), c[RIGHT_WAIST].y.toFloat())
        path.lineTo(c[RIGHT_HIP].x.toFloat(), c[RIGHT_HIP].y.toFloat())
        path.lineTo(c[RIGHT_HEM].x.toFloat(), c[RIGHT_HEM].y.toFloat())
        path.lineTo(c[LEFT_HEM].x.toFloat(), c[LEFT_HEM].y.toFloat())
        path.lineTo(c[LEFT_HIP].x.toFloat(), c[LEFT_HIP].y.toFloat())
        path.lineTo(c[LEFT_WAIST].x.toFloat(), c[LEFT_WAIST].y.toFloat())
    }

    // Torso, then the ponytail either behind or in front of the head depending
    // on its projected depth (smaller z = nearer the camera).
    override fun drawBody(body: DrawObject2D, ctx: AvatarContext) {
        drawTorso(body, ctx)
        val ponytailInFront = buildPonytail(body)
        if (!ponytailInFront) drawPonytail(body, ctx)
        drawHead(body, ctx)
        if (ponytailInFront) drawPonytail(body, ctx)
    }

    // Build the ponytail teardrop between the projected anchor and tip into a
    // scratch path; return whether it is nearer the camera than the head.
    private fun buildPonytail(body: DrawObject2D): Boolean {
        val c = body.coord
        val ax = c[PONYTAIL_ANCHOR].x
        val ay = c[PONYTAIL_ANCHOR].y
        val tx = c[PONYTAIL_TIP].x
        val ty = c[PONYTAIL_TIP].y
        val dx = tx - ax
        val dy = ty - ay
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
        val px = -dy / len * (len * 0.34)
        val py = dx / len * (len * 0.34)

        val hair = body.scratchPath(HAIR_PATH)
        hair.rewind()
        hair.moveTo(ax.toFloat(), ay.toFloat())
        hair.cubicTo(
            (ax + px + dx * 0.15).toFloat(), (ay + py + dy * 0.15).toFloat(),
            (tx + px * 0.4).toFloat(), (ty + py * 0.4).toFloat(),
            tx.toFloat(), ty.toFloat()
        )
        hair.cubicTo(
            (tx - px * 0.4).toFloat(), (ty - py * 0.4).toFloat(),
            (ax - px + dx * 0.15).toFloat(), (ay - py + dy * 0.15).toFloat(),
            ax.toFloat(), ay.toFloat()
        )
        hair.close()

        val headZ = 0.25 * (c[LEFT_HEAD_BOTTOM].z + c[LEFT_HEAD_TOP].z
                + c[RIGHT_HEAD_BOTTOM].z + c[RIGHT_HEAD_TOP].z)
        val ponyZ = 0.5 * (c[PONYTAIL_ANCHOR].z + c[PONYTAIL_TIP].z)
        return ponyZ < headZ
    }

    private fun drawPonytail(body: DrawObject2D, ctx: AvatarContext) {
        ctx.fill(body.scratchPath(HAIR_PATH))
        ctx.stroke(body.scratchPath(HAIR_PATH))
    }

    companion object {
        // This avatar's own points, continuing after the shared skeleton.
        const val LEFT_HIP = CORE_POINT_COUNT
        const val RIGHT_HIP = CORE_POINT_COUNT + 1
        const val LEFT_HEM = CORE_POINT_COUNT + 2
        const val RIGHT_HEM = CORE_POINT_COUNT + 3
        const val PONYTAIL_ANCHOR = CORE_POINT_COUNT + 4
        const val PONYTAIL_TIP = CORE_POINT_COUNT + 5
        const val EXTENDED_POINT_COUNT = CORE_POINT_COUNT + 6

        // This avatar's own scratch-path slot.
        private const val HAIR_PATH = FIRST_FREE_PATH
    }
}
