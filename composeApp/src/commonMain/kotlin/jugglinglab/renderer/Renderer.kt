//
// Renderer.kt
//
// Class that draws the juggling into the frame using Compose DrawScope.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.renderer

import jugglinglab.jml.JmlPattern
import jugglinglab.util.Coordinate
import jugglinglab.core.Constants
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
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

class Renderer {
    private var background: Color = Color.White
    var showGround: Boolean = false

    // Internal fields
    private var cameraCenter: JlVector? = null
    private var m: JlMatrix = JlMatrix()
    private var width: Int = 0
    private var height: Int = 0
    private var viewport: Rect? = null
    private lateinit var pattern: JmlPattern
    private var zoom: Double = 0.0 // pixels/cm
    private var zoomOrig: Double = 0.0 // pixels/cm at zoomfactor=1
    private var originX: Int = 0
    private var originZ: Int = 0
    private var polysides: Int = 40 // # sides in polygon for head
    private var headCos: DoubleArray = DoubleArray(polysides)
    private var headSin: DoubleArray = DoubleArray(polysides)
    private var headX: IntArray = IntArray(polysides)
    private var headY: IntArray = IntArray(polysides)

    private lateinit var obj: MutableList<DrawObject2D>
    private lateinit var obj2: MutableList<DrawObject2D>
    private lateinit var jugglerVec: Array<Array<JlVector?>>
    private var tempc: Coordinate = Coordinate()
    private var tempv1: JlVector = JlVector()
    private var tempv2: JlVector = JlVector()

    // for switching antialiasing on/off
    var isAntiAlias: Boolean = true
    private val paint = androidx.compose.ui.graphics.Paint()

    init {
        for (i in 0..<polysides) {
            headCos[i] = cos(i.toDouble() * 2.0 * Math.PI / polysides)
            headSin[i] = sin(i.toDouble() * 2.0 * Math.PI / polysides)
        }
    }

    fun setPattern(pat: JmlPattern) {
        pattern = pat
        val maxobjects = 5 * pat.numberOfJugglers + pat.numberOfPaths + 18
        obj = MutableList(maxobjects) { DrawObject2D() }
        obj2 = MutableList(maxobjects) { DrawObject2D() }
        jugglerVec = Array(pat.numberOfJugglers) { arrayOfNulls(12) }
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

        // Pattern center vis-a-vis camera rotation
        cameraCenter = JlVector(
            0.5 * (adjustedMax.x + adjustedMin.x),
            0.5 * (adjustedMax.z + adjustedMin.z),
            0.5 * (adjustedMax.y + adjustedMin.y)
        )

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

        if (viewport != null && cameraCenter != null) {
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
        m.transform(JlMatrix.rotateMatrix(0.0, Math.PI - cameraAngle[0], 0.0))
        m.transform(JlMatrix.rotateMatrix(0.5 * Math.PI - cameraAngle[1], 0.0, 0.0))
        m.transform(JlMatrix.shiftMatrix(cameraCenter!!.x, cameraCenter!!.y, cameraCenter!!.z))

        m.transform(JlMatrix.scaleMatrix(1.0, -1.0, 1.0))
        m.transform(JlMatrix.scaleMatrix(zoom))
        m.transform(JlMatrix.shiftMatrix(originX.toDouble(), originZ.toDouble(), 0.0))
    }

    fun getXY(coord: Coordinate): IntArray {
        return getXY(JlVector(coord.x, coord.z, coord.y))
    }

    private fun getXY(vec: JlVector): IntArray {
        val v = vec.transform(m)
        val `val` = IntArray(2)
        `val`[0] = v.x.roundToInt()
        `val`[1] = v.y.roundToInt()
        return `val`
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

        var numObjects = 5 * pattern.numberOfJugglers + pattern.numberOfPaths + 18

        for (i in 0..<numObjects) {
            obj[i].covering.clear()
        }

        var index = 0

        // Props
        var propmin = 0.0
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
            obj[index].boundingbox = Rect(
                (x - center!!.width).toFloat(),
                (y - center.height).toFloat(),
                (x - center.width + size!!.width).toFloat(),
                (y - center.height + size.height).toFloat()
            )
            propmin = min(propmin, pr.getMin()!!.z)
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
                tempv2.y = propmin
                tempv1.y = propmin

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

                obj[index].boundingbox = Rect(left, top, max(left + 1f, right), max(top + 1f, bottom))
                index++
            }
        }

        // Jugglers
        Juggler.findJugglerCoordinates(pattern, time, jugglerVec)

        for (i in 1..pattern.numberOfJugglers) {
            if (i in hideJugglers) continue

            obj[index].type = DrawObject2D.TYPE_BODY
            obj[index].number = i
            getXYZ(jugglerVec[i - 1][2]!!, obj[index].coord[0]) // left shoulder
            getXYZ(jugglerVec[i - 1][3]!!, obj[index].coord[1]) // right shoulder
            getXYZ(jugglerVec[i - 1][7]!!, obj[index].coord[2]) // right waist
            getXYZ(jugglerVec[i - 1][6]!!, obj[index].coord[3]) // left waist
            getXYZ(jugglerVec[i - 1][8]!!, obj[index].coord[4]) // left head bottom
            getXYZ(jugglerVec[i - 1][9]!!, obj[index].coord[5]) // left head top
            getXYZ(jugglerVec[i - 1][10]!!, obj[index].coord[6]) // right head bottom
            getXYZ(jugglerVec[i - 1][11]!!, obj[index].coord[7]) // right head top

            var xmin = obj[index].coord[0].x.roundToInt()
            var xmax = xmin
            var ymin = obj[index].coord[0].y.roundToInt()
            var ymax = ymin

            for (j in 1..7) {
                val x = obj[index].coord[j].x.roundToInt()
                val y = obj[index].coord[j].y.roundToInt()
                if (x < xmin) xmin = x
                if (x > xmax) xmax = x
                if (y < ymin) ymin = y
                if (y > ymax) ymax = y
            }
            obj[index].boundingbox = Rect((xmin + 1).toFloat(), (ymin + 1).toFloat(), xmax.toFloat(), ymax.toFloat())
            index++

            // Arms
            for (j in 0..1) {
                if (jugglerVec[i - 1][4 + j] == null) {
                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(jugglerVec[i - 1][2 + j]!!, obj[index].coord[0])
                    getXYZ(jugglerVec[i - 1][j]!!, obj[index].coord[1])
                    updateLineBoundingBox(obj[index])
                    index++
                } else {
                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(jugglerVec[i - 1][2 + j]!!, obj[index].coord[0])
                    getXYZ(jugglerVec[i - 1][4 + j]!!, obj[index].coord[1])
                    updateLineBoundingBox(obj[index])
                    index++

                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(jugglerVec[i - 1][4 + j]!!, obj[index].coord[0])
                    getXYZ(jugglerVec[i - 1][j]!!, obj[index].coord[1])
                    updateLineBoundingBox(obj[index])
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
                    if (obj[i].covering.all { it.drawn }) {
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
                index++
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
                        val center = pr.getProp2DCenter(zoom, cameraAngle)!!
                        drawImage(
                            image = image,
                            topLeft = Offset((x - center.width).toFloat(), (y - center.height).toFloat())
                        )
                    } else {
                        val size = pr.getProp2DSize(zoom, cameraAngle)
                        if (size != null) {
                            val center = pr.getProp2DCenter(zoom, cameraAngle)!!
                            drawAaOval(
                                color = pr.getEditorColor(),
                                topLeft = Offset((x - center.width).toFloat(), (y - center.height).toFloat()),
                                size = Size(size.width.toFloat(), size.height.toFloat())
                            )
                        }
                    }
                }

                DrawObject2D.TYPE_BODY -> {
                    val path = Path()
                    path.moveTo(ob.coord[0].x.toFloat(), ob.coord[0].y.toFloat())
                    for (j in 1..3) {
                        path.lineTo(ob.coord[j].x.toFloat(), ob.coord[j].y.toFloat())
                    }
                    path.close()
                    drawAaPath(path, background)
                    drawAaPath(path, Color.Black, style = Stroke(strokeWidth1))

                    val lHeadBx = ob.coord[4].x
                    val lHeadBy = ob.coord[4].y
                    val lHeadTy = ob.coord[5].y
                    val rHeadBx = ob.coord[6].x
                    val rHeadBy = ob.coord[6].y

                    if (abs(rHeadBx - lHeadBx) > 2.0) {
                        val headPath = Path()
                        for (j in 0..<polysides) {
                            headX[j] = (0.5 * (lHeadBx + rHeadBx + headCos[j] * (rHeadBx - lHeadBx))).roundToInt()
                            headY[j] = (0.5 * (lHeadBy + lHeadTy + headSin[j] * (lHeadBy - lHeadTy))
                                    + (headX[j] - lHeadBx) * (rHeadBy - lHeadBy) / (rHeadBx - lHeadBx)).roundToInt()

                            if (j == 0) headPath.moveTo(headX[j].toFloat(), headY[j].toFloat())
                            else headPath.lineTo(headX[j].toFloat(), headY[j].toFloat())
                        }
                        headPath.close()
                        drawAaPath(headPath, background)
                        drawAaPath(headPath, Color.Black, style = Stroke(strokeWidth1))
                    } else {
                        val h =
                            sqrt((lHeadBy - lHeadTy) * (lHeadBy - lHeadTy) + (rHeadBy - lHeadBy) * (rHeadBy - lHeadBy))
                        val hx = (0.5 * (lHeadBx + rHeadBx)).toFloat()
                        val hy1 = (0.5 * (lHeadTy + rHeadBy + h)).toFloat()
                        val hy2 = (0.5 * (lHeadTy + rHeadBy - h)).toFloat()
                        drawAaLine(Color.Black, Offset(hx, hy1), Offset(hx, hy2), strokeWidth = strokeWidth1)
                    }
                }

                DrawObject2D.TYPE_LINE -> {
                    val x1 = ob.coord[0].x.toFloat()
                    val y1 = ob.coord[0].y.toFloat()
                    val x2 = ob.coord[1].x.toFloat()
                    val y2 = ob.coord[1].y.toFloat()
                    // Juggler parts have number > 0, ground uses 0
                    val strokeWidth = if (ob.number > 0) strokeWidth1 else strokeWidth0_5
                    drawAaLine(Color.Black, Offset(x1, y1), Offset(x2, y2), strokeWidth = strokeWidth)
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

        val axesColor = Color.Green
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

    private fun updateLineBoundingBox(ob: DrawObject2D) {
        val x1 = ob.coord[0].x.roundToInt().toFloat()
        val y1 = ob.coord[0].y.roundToInt().toFloat()
        val x2 = ob.coord[1].x.roundToInt().toFloat()
        val y2 = ob.coord[1].y.roundToInt().toFloat()
        val left = min(x1, x2)
        val top = min(y1, y2)
        val right = max(x1, x2)
        val bottom = max(y1, y2)
        ob.boundingbox = Rect(left, top, max(left + 1f, right), max(top + 1f, bottom))
    }

    class DrawObject2D {
        var type: Int = 0
        var number: Int = 0
        var coord: MutableList<JlVector> = MutableList(8) { JlVector() }
        var boundingbox: Rect = Rect.Zero
        var covering: MutableList<DrawObject2D> = mutableListOf()
        var drawn: Boolean = false
        var tempv: JlVector = JlVector()

        fun isCovering(obj: DrawObject2D): Boolean {
            if (!boundingbox.overlaps(obj.boundingbox)) {
                return false
            }

            when (type) {
                TYPE_PROP -> when (obj.type) {
                    TYPE_PROP -> return (coord[0].z < obj.coord[0].z)
                    TYPE_BODY -> {
                        vectorProduct(obj.coord[0], obj.coord[1], obj.coord[2], tempv)
                        if (tempv.z == 0.0) return false
                        val z = obj.coord[0].z -
                                (tempv.x * (coord[0].x - obj.coord[0].x) + tempv.y * (coord[0].y - obj.coord[0].y)) / tempv.z
                        return (coord[0].z < z)
                    }

                    TYPE_LINE -> return (isBoxCoveringLine(this, obj) == 1)
                }

                TYPE_BODY -> when (obj.type) {
                    TYPE_PROP -> {
                        vectorProduct(coord[0], coord[1], coord[2], tempv)
                        if (tempv.z == 0.0) return false
                        val z = coord[0].z -
                                (tempv.x * (obj.coord[0].x - coord[0].x) + tempv.y * (obj.coord[0].y - coord[0].y)) / tempv.z
                        return (z < obj.coord[0].z)
                    }

                    TYPE_BODY -> {
                        var d = 0.0
                        for (i in 0..3) d += (coord[i].z - obj.coord[i].z)
                        return (d < 0.0)
                    }

                    TYPE_LINE -> return (isBoxCoveringLine(this, obj) == 1)
                }

                TYPE_LINE -> when (obj.type) {
                    TYPE_PROP, TYPE_BODY -> return (isBoxCoveringLine(obj, this) == -1)
                    TYPE_LINE -> return false
                }
            }
            return false
        }

        private fun isBoxCoveringLine(box: DrawObject2D, line: DrawObject2D): Int {
            if (box.type == TYPE_BODY) {
                vectorProduct(box.coord[0], box.coord[1], box.coord[2], tempv)
            } else {
                tempv.x = 0.0
                tempv.y = 0.0
                tempv.z = 1.0
            }

            if (tempv.z == 0.0) return 0

            var endinbb = false
            for (i in 0..1) {
                val x = line.coord[i].x
                val y = line.coord[i].y
                if (box.boundingbox.contains(Offset((x + 0.5).toFloat(), (y + 0.5).toFloat()))) {
                    val zb =
                        (box.coord[0].z - (tempv.x * (x - box.coord[0].x) + tempv.y * (y - box.coord[0].y)) / tempv.z)
                    if (line.coord[i].z < (zb - SLOP)) return -1
                    endinbb = true
                }
            }
            if (endinbb) return 1

            var intersection = false
            // Check top/bottom edges of bbox
            for (i in 0..1) {
                val x = (if (i == 0) box.boundingbox.left else box.boundingbox.right).toDouble()
                if (x < min(line.coord[0].x, line.coord[1].x) || x > max(line.coord[0].x, line.coord[1].x)) continue
                if (line.coord[1].x == line.coord[0].x) continue

                val y =
                    line.coord[0].y + (line.coord[1].y - line.coord[0].y) * (x - line.coord[0].x) / (line.coord[1].x - line.coord[0].x)
                if (y < box.boundingbox.top || y > box.boundingbox.bottom) continue

                intersection = true
                val zb = (box.coord[0].z - (tempv.x * (x - box.coord[0].x) + tempv.y * (y - box.coord[0].y)) / tempv.z)
                val zl =
                    (line.coord[0].z + (line.coord[1].z - line.coord[0].z) * (x - line.coord[0].x) / (line.coord[1].x - line.coord[0].x))
                if (zl < (zb - SLOP)) return -1
            }

            // Check left/right edges of bbox
            for (i in 0..1) {
                val y = (if (i == 0) box.boundingbox.top else box.boundingbox.bottom).toDouble()
                if (y < min(line.coord[0].y, line.coord[1].y) || y > max(line.coord[0].y, line.coord[1].y)) continue
                if (line.coord[1].y == line.coord[0].y) continue

                val x =
                    line.coord[0].x + (line.coord[1].x - line.coord[0].x) * (y - line.coord[0].y) / (line.coord[1].y - line.coord[0].y)
                if (x < box.boundingbox.left || x > box.boundingbox.right) continue

                intersection = true
                val zb = (box.coord[0].z - (tempv.x * (x - box.coord[0].x) + tempv.y * (y - box.coord[0].y)) / tempv.z)
                val zl =
                    (line.coord[0].z + (line.coord[1].z - line.coord[0].z) * (x - line.coord[0].x) / (line.coord[1].x - line.coord[0].x))
                if (zl < (zb - SLOP)) return -1
            }

            return if (intersection) 1 else 0
        }

        fun vectorProduct(v1: JlVector, v2: JlVector, v3: JlVector, result: JlVector): JlVector {
            val ax = v2.x - v1.x
            val ay = v2.y - v1.y
            val az = v2.z - v1.z
            val bx = v3.x - v1.x
            val by = v3.y - v1.y
            val bz = v3.z - v1.z
            result.x = ay * bz - by * az
            result.y = az * bx - bz * ax
            result.z = ax * by - bx * ay
            return result
        }

        companion object {
            const val TYPE_PROP: Int = 1
            const val TYPE_BODY: Int = 2
            const val TYPE_LINE: Int = 3

            private const val SLOP: Double = 3.0
        }
    }

    //--------------------------------------------------------------------------
    // Extension functions to handle aliased or anti-aliased drawing
    //--------------------------------------------------------------------------

    private fun DrawScope.drawAaPath(
        path: Path,
        color: Color,
        style: androidx.compose.ui.graphics.drawscope.DrawStyle = Fill
    ) {
        if (isAntiAlias) {
            drawPath(path, color, style = style)
        } else {
            paint.color = color
            paint.isAntiAlias = false
            paint.pathEffect = null
            when (style) {
                is Stroke -> {
                    paint.style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                    paint.strokeWidth = style.width
                    paint.strokeCap = style.cap
                    paint.strokeJoin = style.join
                    paint.strokeMiterLimit = style.miter
                    paint.pathEffect = style.pathEffect
                }

                is Fill -> {
                    paint.style = androidx.compose.ui.graphics.PaintingStyle.Fill
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
            paint.style = androidx.compose.ui.graphics.PaintingStyle.Stroke
            paint.strokeWidth = strokeWidth
            paint.strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt
            drawContext.canvas.drawLine(start, end, paint)
        }
    }

    private fun DrawScope.drawAaOval(
        color: Color,
        topLeft: Offset,
        size: Size,
        style: androidx.compose.ui.graphics.drawscope.DrawStyle = Fill
    ) {
        if (isAntiAlias) {
            drawOval(color, topLeft, size, style = style)
        } else {
            paint.color = color
            paint.isAntiAlias = false
            paint.pathEffect = null
            when (style) {
                is Stroke -> {
                    paint.style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                    paint.strokeWidth = style.width
                }

                is Fill -> {
                    paint.style = androidx.compose.ui.graphics.PaintingStyle.Fill
                }
            }
            drawContext.canvas.drawOval(
                left = topLeft.x,
                top = topLeft.y,
                right = topLeft.x + size.width,
                bottom = topLeft.y + size.height,
                paint = paint
            )
        }
    }

    companion object {
        const val ORIGINAL_ZOOM = true
    }
}
