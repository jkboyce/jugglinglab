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

    private var objectPool: DrawObjectPool = DrawObjectPool()
    private var sortedObjects: MutableList<DrawObject2D> = mutableListOf()
    private var tempc: Coordinate = Coordinate()
    private var tempv1: JlVector = JlVector()
    private var tempv2: JlVector = JlVector()

    // for switching antialiasing on/off
    var isAntiAlias: Boolean = true
    private val paint = Paint()

    // Which avatar draws each juggler (by juggler number, 1-based); jugglers
    // absent from the map use the default. Avatars are stateless (see Avatar),
    // so instances may be shared freely across jugglers and renderers.
    private val defaultAvatar = ClassicAvatar()
    private var avatars: Map<Int, Avatar> = emptyMap()

    fun avatarFor(juggler: Int): Avatar = avatars[juggler] ?: defaultAvatar

    fun setAvatars(newAvatars: Map<Int, Avatar>) {
        avatars = newAvatars
    }

    fun setPattern(pat: JmlPattern) {
        pattern = pat
        objectPool = DrawObjectPool()
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

        // Drawing primitives a DrawObject2D uses to paint itself, bound to this
        // frame's DrawScope and theme colors.
        val drawObjectContext = DrawObjectContext(
            fill = { path -> drawAaPath(path, backgroundColor) },
            stroke = { path -> drawAaPath(path, lineColor, style = stroke1) },
            segment = { a, b -> drawAaLine(lineColor, a, b, strokeWidth = strokeWidth1) }
        )

        objectPool.reset()

        // Props
        var propMinZ = 0.0
        for (i in 1..pattern.numberOfPaths) {
            val propObj = objectPool.next()
            pattern.layout.getPathCoordinate(i, time, tempc)
            if (!tempc.isValid) {
                tempc.setCoordinate(0.0, 0.0, 0.0)
            }
            val vec = JlVector.fromCoordinate(tempc, tempv1)
            propObj.set3DCoordinates(DrawObject2D.TYPE_PROP, i, listOf(vec))

            val pr = pattern.getProp(pnum[i - 1])
            propMinZ = min(propMinZ, pr.getMinZ())
        }

        // Ground
        if (showGround) {
            for (i in 0..17) {
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

                val lineObj = objectPool.next()
                lineObj.set3DCoordinates(DrawObject2D.TYPE_LINE, 0, listOf(tempv1, tempv2))
            }
        }

        // Jugglers
        for (i in 1..pattern.numberOfJugglers) {
            if (i in hideJugglers) continue
            val avatar = avatarFor(i)
            avatar.computeObjects(pattern, i, time, objectPool)
        }

        val numObjects = objectPool.activeCount

        // Project 3D to 2D screen coordinates and compute bounds
        for (i in 0..<numObjects) {
            val ob = objectPool.objects[i]
            for (p in 0..<ob.numPoints) {
                getXYZ(ob.coords3D[p], ob.coords2D[p])
            }

            if (ob.type == DrawObject2D.TYPE_PROP) {
                val x = ob.coords2D[0].x.roundToInt()
                val y = ob.coords2D[0].y.roundToInt()
                val pr = pattern.getProp(pnum[ob.number - 1])
                val center = pr.getProp2DCenter(zoom, cameraAngle)
                val size = pr.getProp2DSize(zoom, cameraAngle)

                ob.bbLeft = (x - center.width).toFloat()
                ob.bbTop = (y - center.height).toFloat()
                ob.bbRight = (x - center.width + size.width).toFloat()
                ob.bbBottom = (y - center.height + size.height).toFloat()
            } else {
                ob.computeBounds()
            }

            ob.covering.clear()
            ob.drawn = false
        }

        // Figure out which display elements are covering which other elements
        for (i in 0..<numObjects) {
            val obI = objectPool.objects[i]
            for (j in 0..<numObjects) {
                if (j == i) continue
                val obJ = objectPool.objects[j]
                if (obI.isCovering(obJ)) {
                    obI.covering.add(obJ)
                }
            }
        }

        // Figure out a drawing order
        while (sortedObjects.size < numObjects) {
            sortedObjects.add(objectPool.objects[0])
        }

        var index = 0
        for (pass in 1..2) {
            var changed = true
            while (changed) {
                changed = false
                for (i in 0..<numObjects) {
                    val ob = objectPool.objects[i]
                    if (ob.drawn) continue

                    var allCoveringDrawn = true
                    for (k in ob.covering.indices) {
                        if (!ob.covering[k].drawn) {
                            allCoveringDrawn = false
                            break
                        }
                    }
                    if (allCoveringDrawn) {
                        sortedObjects[index] = ob
                        ob.drawn = true
                        index++
                        changed = true
                    }
                }
            }

            for (i in 0..<numObjects) {
                val ob = objectPool.objects[i]
                if (ob.drawn) continue
                if (pass == 1 && ob.type != DrawObject2D.TYPE_LINE) continue
                sortedObjects[index] = ob
                ob.drawn = true
                index++
            }
        }

        // Draw the objects in the sorted order
        for (i in 0..<numObjects) {
            val ob = sortedObjects[i]

            when (ob.type) {
                DrawObject2D.TYPE_PROP -> {
                    val pr = pattern.getProp(pnum[ob.number - 1])
                    val x = ob.coords2D[0].x.roundToInt()
                    val y = ob.coords2D[0].y.roundToInt()

                    val image = pr.getProp2DImage(zoom, cameraAngle)
                    if (image != null) {
                        val grip = pr.getProp2DGrip(zoom, cameraAngle)
                        drawImage(
                            image = image,
                            topLeft = Offset((x - grip.width).toFloat(), (y - grip.height).toFloat())
                        )
                    }
                }

                DrawObject2D.TYPE_POLY -> {
                    ob.draw(drawObjectContext)
                }

                DrawObject2D.TYPE_LINE -> {
                    if (ob.number > 0) {
                        ob.draw(drawObjectContext)
                    } else {
                        val x1 = ob.coords2D[0].x.toFloat()
                        val y1 = ob.coords2D[0].y.toFloat()
                        val x2 = ob.coords2D[1].x.toFloat()
                        val y2 = ob.coords2D[1].y.toFloat()
                        drawAaLine(lineColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = strokeWidth0_5)
                    }
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
