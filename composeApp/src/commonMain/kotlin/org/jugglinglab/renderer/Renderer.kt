//
// Renderer.kt
//
// Class that draws the juggling into the frame using Compose DrawScope.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.util.Coordinate
import org.jugglinglab.core.Constants
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

class Renderer {
    var backgroundColor: Color = Color.White
    var lineColor: Color = Color.Black
    var showGround: Boolean = false

    // Internal fields
    private var cameraCenter: JlVector? = null
    private var zoomCenterV: JlVector? = null
    private var bbCenterY: Double = 0.0
    private var m: JlMatrix = JlMatrix()
    private var width: Int = 0
    private var height: Int = 0
    private var viewport: Rect? = null
    private lateinit var pattern: JmlPattern
    val currentPattern: JmlPattern?
        get() = if (::pattern.isInitialized) pattern else null
    private var zoom: Double = 0.0 // pixels/cm
    private var zoomOrig: Double = 0.0 // pixels/cm at zoomfactor=1
    private var originX: Int = 0
    private var originZ: Int = 0

    private lateinit var obj: MutableList<DrawObject2D>
    private lateinit var obj2: MutableList<DrawObject2D>
    private lateinit var jugglerVec: Array<Array<JlVector?>>
    private var tempc: Coordinate = Coordinate()
    private var tempv1: JlVector = JlVector()
    private var tempv2: JlVector = JlVector()

    // for switching antialiasing on/off
    var isAntiAlias: Boolean = true
    private val paint = Paint()

    // Which avatar draws each juggler (by juggler number, 1-based); jugglers
    // absent from the map use the default. Avatars are stateless (see Avatar),
    // so instances may be shared freely across jugglers and renderers.
    private val defaultAvatar = MaleAvatar()
    private var avatars: Map<Int, Avatar> = emptyMap()

    fun avatarFor(juggler: Int): Avatar = avatars[juggler] ?: defaultAvatar

    // Call together with setPattern() — the per-juggler point buffers are
    // sized from the avatars' point counts, whichever setter runs last.
    fun setAvatars(newAvatars: Map<Int, Avatar>) {
        avatars = newAvatars
        if (::pattern.isInitialized) {
            resizeJointBuffers()
        }
    }

    fun setPattern(pat: JmlPattern) {
        pattern = pat
        val maxobjects = 5 * pat.numberOfJugglers + pat.numberOfPaths + 18
        obj = MutableList(maxobjects) { DrawObject2D() }
        obj2 = MutableList(maxobjects) { DrawObject2D() }
        resizeJointBuffers()
    }

    // One 3D point buffer per juggler, sized for that juggler's avatar.
    private fun resizeJointBuffers() {
        jugglerVec = Array(pattern.numberOfJugglers) {
            arrayOfNulls(avatarFor(it + 1).pointCount)
        }
    }

    fun setGround(show: Boolean) {
        showGround = show
    }

    fun initDisplay(w: Int, h: Int, border: Int, overallMax: Coordinate, overallMin: Coordinate) {
        width = w
        height = h
        viewport = Rect(border.toFloat(), border.toFloat(), (width - border).toFloat(), (height - border).toFloat())

        val adjustedMax = overallMax.copy()
        val adjustedMin = overallMin.copy()

        if (ORIGINAL_ZOOM) {
            // This is the zoom algorithm that has been in Juggling Lab for many
            // years. It's a bit too zoomed-in for some patterns.

            // We want to ensure everything stays visible as we rotate the camera
            // viewpoint. The following is simple and seems to work ok.

            if (pattern.numberOfJugglers == 1) {
                adjustedMin.z -= 0.3 * max(abs(adjustedMin.y), abs(adjustedMax.y))
                adjustedMax.z += 5.0  // keeps objects from rubbing against top of window
            } else {
                val tempx = max(abs(adjustedMin.x), abs(adjustedMax.x))
                val tempy = max(abs(adjustedMin.y), abs(adjustedMax.y))
                adjustedMin.z -= 0.4 * max(tempx, tempy)
                adjustedMax.z += 0.4 * max(tempx, tempy)
            }

            // make the x-coordinate origin at the center of the view
            val maxAbsX = max(abs(adjustedMin.x), abs(adjustedMax.x))
            adjustedMin.x = -maxAbsX
            adjustedMax.x = maxAbsX

            zoomOrig = min(
                viewport!!.width.toDouble() / (adjustedMax.x - adjustedMin.x),
                viewport!!.height.toDouble() / (adjustedMax.z - adjustedMin.z)
            )
        } else {
            // NEW ALGORITHM

            // make the x-coordinate origin at the center of the view
            val maxabsx = max(abs(adjustedMin.x), abs(adjustedMax.x))
            adjustedMin.x = -maxabsx
            adjustedMax.x = maxabsx

            val dx = adjustedMax.x - adjustedMin.x
            val dy = adjustedMax.y - adjustedMin.y
            val dz = adjustedMax.z - adjustedMin.z
            val dxy = max(dx, dy)

            // Find `zoom` value that keeps the adjusted bounding box visible in
            // the viewport
            zoomOrig = min(
                viewport!!.width.toDouble() / sqrt(dx * dx + dy * dy),
                viewport!!.height.toDouble() / sqrt(dxy * dxy + dz * dz)
            )
        }

        val zc = pattern.layout.zoomCenter
        zoomCenterV = JlVector(zc.x, zc.z, zc.y)
        // Pattern center vis-a-vis camera rotation
        cameraCenter = JlVector(zc.x, 0.0, zc.y)
        // Vertical midpoint of the pattern bounding box
        bbCenterY = 0.5 * (adjustedMax.z + adjustedMin.z)

        setZoom(zoomLevel)  // calculate camera matrix etc.

        if (Constants.DEBUG_LAYOUT) {
            println("Data from Renderer.initDisplay():")
            println("overallMax = $overallMax")
            println("overallMin = $overallMin")
            println("adjustedMax = $adjustedMax")
            println("adjustedMin = $adjustedMin")
            println("zoomOrig (px/cm) = $zoomOrig")
        }
    }

    var zoomLevel: Double = 1.0
        set(value) {
            field = value
            setZoom(value)
        }

    private fun setZoom(zoomFactor: Double) {
        zoom = zoomOrig * zoomFactor

        if (viewport != null && cameraCenter != null && zoomCenterV != null) {
            val limit = (viewport!!.height / 2.0) / zoom
            cameraCenter!!.y = bbCenterY.coerceIn(zoomCenterV!!.y - limit, zoomCenterV!!.y + limit)

            originX = (viewport!!.left + 0.5 * viewport!!.width - zoom * cameraCenter!!.x).roundToInt()
            originZ = (viewport!!.top + 0.5 * viewport!!.height + zoom * cameraCenter!!.y).roundToInt()
            calculateCameraMatrix()
        }
    }

    var cameraAngle: DoubleArray = doubleArrayOf(0.0, 0.0)
        set(value) {
            field = doubleArrayOf(value[0], value[1])
            if (cameraCenter != null) {
                calculateCameraMatrix()
            }
        }

    private fun calculateCameraMatrix() {
        m = JlMatrix.shiftMatrix(-cameraCenter!!.x, -cameraCenter!!.y, -cameraCenter!!.z)
        m.transform(JlMatrix.rotateMatrix(0.0, PI - cameraAngle[0], 0.0))
        m.transform(JlMatrix.rotateMatrix(0.5 * PI - cameraAngle[1], 0.0, 0.0))
        m.transform(JlMatrix.shiftMatrix(cameraCenter!!.x, cameraCenter!!.y, cameraCenter!!.z))

        m.transform(JlMatrix.scaleMatrix(1.0, -1.0, 1.0))
        m.transform(JlMatrix.scaleMatrix(zoom))
        m.transform(JlMatrix.shiftMatrix(originX.toDouble(), originZ.toDouble(), 0.0))
    }

    fun getXY(coord: Coordinate): IntOffset {
        val vecX = coord.x
        val vecY = coord.z
        val vecZ = coord.y
        val newX = vecX * m.m00 + vecY * m.m01 + vecZ * m.m02 + m.m03
        val newY = vecX * m.m10 + vecY * m.m11 + vecZ * m.m12 + m.m13
        return IntOffset(newX.roundToInt(), newY.roundToInt())
    }
    
    private fun getXYZ(vec: JlVector, result: JlVector): JlVector {
        result.x = vec.x * m.m00 + vec.y * m.m01 + vec.z * m.m02 + m.m03
        result.y = vec.x * m.m10 + vec.y * m.m11 + vec.z * m.m12 + m.m13
        result.z = vec.x * m.m20 + vec.y * m.m21 + vec.z * m.m22 + m.m23
        return result
    }

    fun getScreenTranslatedCoordinate(coord: Coordinate, dx: Int, dy: Int): Coordinate {
        val v = JlVector(coord.x, coord.z, coord.y)
        val s = v.transform(m)
        val news = JlVector.add(s, JlVector(dx.toDouble(), dy.toDouble(), 0.0))
        val newv = news.transform(m.inverse())
        return Coordinate(newv.x, newv.z, newv.y)
    }

    fun drawFrame(
        time: Double,
        pnum: List<Int>,
        hideJugglers: List<Int>,
        scope: DrawScope
    ): Unit = with(scope) {
        @Suppress("LocalVariableName")
        val strokeWidth0_5 = 0.5.dp.toPx()
        val strokeWidth1 = 1.dp.toPx()
        val stroke1 = Stroke(strokeWidth1)

        // Drawing primitives an avatar uses to paint itself, bound to this
        // frame's DrawScope and theme colors (see AvatarContext).
        val avatarCtx = AvatarContext(
            fill = { path -> drawAaPath(path, backgroundColor) },
            stroke = { path -> drawAaPath(path, lineColor, style = stroke1) },
            segment = { a, b -> drawAaLine(lineColor, a, b, strokeWidth = strokeWidth1) }
        )

        // Maximum object count in the scene:
        // - 1 for each juggler's body
        // - 4 for each juggler's arms
        // - 1 for each prop
        // - 18 for the lines constituting the ground
        var numObjects = 5 * pattern.numberOfJugglers + pattern.numberOfPaths + 18

        for (i in 0..<numObjects) {
            obj[i].covering.clear()
        }

        var index = 0

        // Props
        var propMinZ = 0.0
        for (i in 1..pattern.numberOfPaths) {
            obj[index].type = DrawObject2D.TYPE_PROP
            obj[index].number = i
            pattern.layout.getPathCoordinate(i, time, tempc)
            if (!tempc.isValid) {
                tempc.setCoordinate(0.0, 0.0, 0.0)
            }
            getXYZ(JlVector.fromCoordinate(tempc, tempv1), obj[index].coord[0])
            val x = obj[index].coord[0].x.roundToInt()
            val y = obj[index].coord[0].y.roundToInt()
            val pr = pattern.getProp(pnum[i - 1])
            val center = pr.getProp2DCenter(zoom, cameraAngle)
            val size = pr.getProp2DSize(zoom, cameraAngle)
            
            obj[index].bbLeft = (x - center.width).toFloat()
            obj[index].bbTop = (y - center.height).toFloat()
            obj[index].bbRight = (x - center.width + size.width).toFloat()
            obj[index].bbBottom = (y - center.height + size.height).toFloat()
            
            propMinZ = min(propMinZ, pr.getMinZ())
            index++
        }

        // Ground
        if (showGround) {
            for (i in 0..17) {
                obj[index].type = DrawObject2D.TYPE_LINE
                obj[index].number = 0

                if (i < 9) {
                    tempv1.x = -50.0 + 100.0 * i / 8.0
                    tempv1.z = -50.0
                    tempv2.x = tempv1.x
                    tempv2.z = 50.0
                } else {
                    tempv1.x = -50.0
                    tempv1.z = -50.0 + 100.0 * (i - 9) / 8.0
                    tempv2.x = 50.0
                    tempv2.z = tempv1.z
                }
                tempv2.y = propMinZ
                tempv1.y = propMinZ

                getXYZ(tempv1, obj[index].coord[0])
                getXYZ(tempv2, obj[index].coord[1])
                val x1 = obj[index].coord[0].x.roundToInt().toFloat()
                val y1 = obj[index].coord[0].y.roundToInt().toFloat()
                val x2 = obj[index].coord[1].x.roundToInt().toFloat()
                val y2 = obj[index].coord[1].y.roundToInt().toFloat()

                // Bounding box for line
                val left = min(x1, x2)
                val top = min(y1, y2)
                val right = max(x1, x2)
                val bottom = max(y1, y2)

                obj[index].bbLeft = left
                obj[index].bbTop = top
                obj[index].bbRight = max(left + 1f, right)
                obj[index].bbBottom = max(top + 1f, bottom)
                index++
            }
        }

        // Jugglers
        for (i in 1..pattern.numberOfJugglers) {
            if (i in hideJugglers) continue

            val avatar = avatarFor(i)
            val points = jugglerVec[i - 1]
            avatar.computePoints(pattern, i, time, points)

            val body = obj[index]
            body.type = DrawObject2D.TYPE_BODY
            body.number = i
            body.avatar = avatar
            body.ensureCapacity(avatar.pointCount)
            // Project every avatar point; coord[p] is always the projection of
            // points[p]. Elbows may be null (hand out of reach) — skipped here
            // and drawn as straight arms below.
            for (p in 0..<avatar.pointCount) {
                points[p]?.let { getXYZ(it, body.coord[p]) }
            }
            body.computeBounds(avatar.boundsPoints)
            index++

            // Arms: shoulder->elbow->hand as two lines, or shoulder->hand as
            // one straight line when the elbow is out of reach (null).
            for (j in 0..1) {
                val shoulder = points[Avatar.LEFT_SHOULDER + j]!!
                val elbow = points[Avatar.LEFT_ELBOW + j]
                val hand = points[Avatar.LEFT_HAND + j]!!

                if (elbow == null) {
                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(shoulder, obj[index].coord[0])
                    getXYZ(hand, obj[index].coord[1])
                    obj[index].computeLineBounds()
                    index++
                } else {
                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(shoulder, obj[index].coord[0])
                    getXYZ(elbow, obj[index].coord[1])
                    obj[index].computeLineBounds()
                    index++

                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(elbow, obj[index].coord[0])
                    getXYZ(hand, obj[index].coord[1])
                    obj[index].computeLineBounds()
                    index++
                }
            }
        }
        numObjects = index

        // figure out which display elements are covering which other elements
        for (i in 0..<numObjects) {
            for (j in 0..<numObjects) {
                if (j == i) continue
                if (obj[i].isCovering(obj[j])) {
                    obj[i].covering.add(obj[j])
                }
            }
            obj[i].drawn = false
        }

        // figure out a drawing order
        index = 0
        for (pass in 1..2) {
            // first assign a drawing order based on "covering" constraints
            var changed = true
            while (changed) {
                changed = false
                for (i in 0..<numObjects) {
                    if (obj[i].drawn) {
                        continue
                    }
                    
                    var allCoveringDrawn = true
                    for (k in obj[i].covering.indices) {
                        if (!obj[i].covering[k].drawn) {
                            allCoveringDrawn = false
                            break
                        }
                    }
                    if (allCoveringDrawn) {
                        obj2[index] = obj[i]
                        obj[i].drawn = true
                        index++
                        changed = true
                    }
                }
            }

            // We sometimes get situations where A > B > C > A from a covering
            // standpoint, and the objects aren't yet drawn. On pass 1 we draw
            // the lines next, then resume the above algorithm in pass 2. At the
            // end of pass 2 we draw everything remaining in arbitrary order.
            for (i in 0..<numObjects) {
                if (obj[i].drawn) {
                    continue
                }
                if (pass == 1 && obj[i].type != DrawObject2D.TYPE_LINE) {
                    continue
                }
                obj2[index] = obj[i]
                obj[i].drawn = true
                ++index
            }
        }

        // draw the objects in the sorted order
        for (i in 0..<numObjects) {
            val ob = obj2[i]

            when (ob.type) {
                DrawObject2D.TYPE_PROP -> {
                    val pr = pattern.getProp(pnum[ob.number - 1])
                    val x = ob.coord[0].x.roundToInt()
                    val y = ob.coord[0].y.roundToInt()

                    val image = pr.getProp2DImage(zoom, cameraAngle)
                    if (image != null) {
                        val grip = pr.getProp2DGrip(zoom, cameraAngle)
                        drawImage(
                            image = image,
                            topLeft = Offset((x - grip.width).toFloat(), (y - grip.height).toFloat())
                        )
                    }
                }

                DrawObject2D.TYPE_BODY -> {
                    // The avatar draws its own torso, head and adornments.
                    ob.avatar?.drawBody(ob, avatarCtx)
                }

                DrawObject2D.TYPE_LINE -> {
                    val x1 = ob.coord[0].x.toFloat()
                    val y1 = ob.coord[0].y.toFloat()
                    val x2 = ob.coord[1].x.toFloat()
                    val y2 = ob.coord[1].y.toFloat()
                    // Juggler parts have number > 0, ground uses 0
                    val strokeWidth = if (ob.number > 0) strokeWidth1 else strokeWidth0_5
                    drawAaLine(lineColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = strokeWidth)
                }
            }
        }
    }

    @Suppress("UnnecessaryVariable")
    fun drawAxes(
        textMeasurer: TextMeasurer,
        scope: DrawScope
    ): Unit = with(scope) {
        val ca = cameraAngle
        val theta = ca[0]
        val phi = ca[1]

        val xya = 30.dp.toPx()
        val xyb = (xya * cos(phi)).toFloat()
        val zlen = (xya * sin(phi)).toFloat()
        val cx = 38.dp.toPx()
        val cy = 48.dp.toPx()
        val xx = cx - (xya * cos(theta)).toFloat()
        val xy = cy + (xyb * sin(theta)).toFloat()
        val yx = cx + (xya * sin(theta)).toFloat()
        val yy = cy + (xyb * cos(theta)).toFloat()
        val zx = cx
        val zy = cy - zlen

        val axesColor = Constants.HIGHLIGHT_COLOR
        val strokeWidth = 1.dp.toPx()
        val dotSize = 5.dp.toPx()
        val dotOffset = dotSize / 2

        drawLine(axesColor, Offset(cx, cy), Offset(xx, xy), strokeWidth = strokeWidth)
        drawLine(axesColor, Offset(cx, cy), Offset(yx, yy), strokeWidth = strokeWidth)
        drawLine(axesColor, Offset(cx, cy), Offset(zx, zy), strokeWidth = strokeWidth)
        drawOval(
            color = axesColor,
            topLeft = Offset(xx - dotOffset, xy - dotOffset),
            size = Size(dotSize, dotSize)
        )
        drawOval(
            color = axesColor,
            topLeft = Offset(yx - dotOffset, yy - dotOffset),
            size = Size(dotSize, dotSize)
        )
        drawOval(
            color = axesColor,
            topLeft = Offset(zx - dotOffset, zy - dotOffset),
            size = Size(dotSize, dotSize)
        )

        val textStyle = TextStyle(color = axesColor, fontSize = 13.sp)
        val padding = 3.dp.toPx()
        val textLayoutResultX = textMeasurer.measure(text = "x", style = textStyle)
        drawText(
            textLayoutResult = textLayoutResultX,
            topLeft = Offset(
                x = xx - textLayoutResultX.size.width / 2,
                y = xy - textLayoutResultX.size.height - padding
            )
        )
        val textLayoutResultY = textMeasurer.measure(text = "y", style = textStyle)
        drawText(
            textLayoutResult = textLayoutResultY,
            topLeft = Offset(
                x = yx - textLayoutResultY.size.width / 2,
                y = yy - textLayoutResultY.size.height - padding
            )
        )
        val textLayoutResultZ = textMeasurer.measure(text = "z", style = textStyle)
        drawText(
            textLayoutResult = textLayoutResultZ,
            topLeft = Offset(
                x = zx - textLayoutResultZ.size.width / 2,
                y = zy - textLayoutResultZ.size.height - padding
            )
        )
    }

    //--------------------------------------------------------------------------
    // Extension functions to handle aliased or anti-aliased drawing
    //--------------------------------------------------------------------------

    private fun DrawScope.drawAaPath(
        path: Path,
        color: Color,
        style: DrawStyle = Fill
    ) {
        if (isAntiAlias) {
            drawPath(path, color, style = style)
        } else {
            paint.color = color
            paint.isAntiAlias = false
            paint.pathEffect = null
            when (style) {
                is Stroke -> {
                    paint.style = PaintingStyle.Stroke
                    paint.strokeWidth = style.width
                    paint.strokeCap = style.cap
                    paint.strokeJoin = style.join
                    paint.strokeMiterLimit = style.miter
                    paint.pathEffect = style.pathEffect
                }

                is Fill -> {
                    paint.style = PaintingStyle.Fill
                }
            }
            drawContext.canvas.drawPath(path, paint)
        }
    }

    private fun DrawScope.drawAaLine(
        color: Color,
        start: Offset,
        end: Offset,
        strokeWidth: Float
    ) {
        if (isAntiAlias) {
            drawLine(color, start, end, strokeWidth)
        } else {
            paint.color = color
            paint.isAntiAlias = false
            paint.pathEffect = null
            paint.style = PaintingStyle.Stroke
            paint.strokeWidth = strokeWidth
            paint.strokeCap = StrokeCap.Butt
            drawContext.canvas.drawLine(start, end, paint)
        }
    }

    companion object {
        const val ORIGINAL_ZOOM = true
    }
}
