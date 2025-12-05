//
// Renderer2D.kt
//
// Class that draws the juggling into the frame.
//
// It is designed so that no object allocation happens in drawFrame().
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.renderer

import androidx.compose.ui.graphics.toAwtImage
import jugglinglab.core.Constants
import jugglinglab.jml.JMLPattern
import jugglinglab.util.Coordinate
import jugglinglab.util.Coordinate.Companion.max
import jugglinglab.util.Coordinate.Companion.min
import jugglinglab.util.JuggleExceptionInternal
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class Renderer2D : Renderer() {
    // private var renderType: Int = RENDER_FLAT_SOLID
    private var background: Color = Color.white
    private var cameracenter: JLVector? = null
    private var cameraangle: DoubleArray = DoubleArray(2)
    private var m: JLMatrix = JLMatrix()
    private var width: Int = 0
    private var height: Int = 0
    private var viewport: Rectangle? = null
    private lateinit var pat: JMLPattern
    private var zoom: Double = 0.0 // pixels/cm
    private var zoomOrig: Double = 0.0 // pixels/cm at zoomfactor=1
    private var zoomfactor: Double = 1.0 // multiplier of `zoom`
    private var originx: Int = 0
    private var originz: Int = 0
    private var polysides: Int = 40 // # sides in polygon for head
    private var headcos: DoubleArray = DoubleArray(polysides)
    private var headsin: DoubleArray = DoubleArray(polysides)
    private var headx: IntArray = IntArray(polysides)
    private var heady: IntArray = IntArray(polysides)

    private lateinit var obj: MutableList<DrawObject2D>
    private lateinit var obj2: MutableList<DrawObject2D>
    private lateinit var jugglervec: Array<Array<JLVector?>>
    // private var propmin: Double = 0.0 // for drawing floor
    private var tempc: Coordinate = Coordinate()
    private var tempv1: JLVector = JLVector()
    private var tempv2: JLVector = JLVector()

    init {
        for (i in 0..<polysides) {
            headcos[i] = cos(i.toDouble() * 2.0 * Math.PI / polysides)
            headsin[i] = sin(i.toDouble() * 2.0 * Math.PI / polysides)
        }
    }

    override fun setPattern(pat: JMLPattern) {
        this.pat = pat
        val maxobjects = 5 * pat.numberOfJugglers + pat.numberOfPaths + 18
        obj = MutableList(maxobjects) { DrawObject2D(maxobjects) }
        obj2 = MutableList(maxobjects) { DrawObject2D(maxobjects) }
        jugglervec = Array(pat.numberOfJugglers) { arrayOfNulls(12) }
    }

    override fun getBackground(): Color {
        return background
    }

    override fun initDisplay(dim: Dimension, border: Int, overallmax: Coordinate, overallmin: Coordinate) {
        width = dim.width
        height = dim.height
        viewport = Rectangle(border, border, width - 2 * border, height - 2 * border)

        // Make some adjustments to the bounding box.
        val adjustedMax = Coordinate(overallmax.x, overallmax.y, overallmax.z)
        val adjustedMin = Coordinate(overallmin.x, overallmin.y, overallmin.z)

        if (ORIGINAL_ZOOM) {
            // This is the zoom algorithm that has been in Juggling Lab for many
            // years. It's a bit too zoomed-in for some patterns.

            // We want to ensure everything stays visible as we rotate the camera
            // viewpoint. The following is simple and seems to work ok.

            if (pat.numberOfJugglers == 1) {
                adjustedMin.z -= 0.3 * max(abs(adjustedMin.y), abs(adjustedMax.y))
                adjustedMax.z += 5.0 // keeps objects from rubbing against top of window
            } else {
                val tempx = max(abs(adjustedMin.x), abs(adjustedMax.x))
                val tempy = max(abs(adjustedMin.y), abs(adjustedMax.y))
                adjustedMin.z -= 0.4 * max(tempx, tempy)
                adjustedMax.z += 0.4 * max(tempx, tempy)
            }

            // make the x-coordinate origin at the center of the view
            val maxabsx = max(abs(adjustedMin.x), abs(adjustedMax.x))
            adjustedMin.x = -maxabsx
            adjustedMax.x = maxabsx

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
        cameracenter = JLVector(
            0.5 * (adjustedMax.x + adjustedMin.x),
            0.5 * (adjustedMax.z + adjustedMin.z),
            0.5 * (adjustedMax.y + adjustedMin.y)
        )

        setZoomLevel(getZoomLevel()) // calculate camera matrix etc.

        if (Constants.DEBUG_LAYOUT) {
            println("Data from Renderer2D:")
            println("overallmax = $overallmax")
            println("overallmin = $overallmin")
            println("adjusted_max = $adjustedMax")
            println("adjusted_min = $adjustedMin")
            println("zoom_orig (px/cm) = $zoomOrig")
        }
    }

    override fun getZoomLevel(): Double {
        return zoomfactor
    }

    override fun setZoomLevel(zoomfactor: Double) {
        this.zoomfactor = zoomfactor
        this.zoom = zoomOrig * zoomfactor

        originx = viewport!!.x + (0.5 * viewport!!.width - zoom * cameracenter!!.x).roundToInt()
        originz = viewport!!.y + (0.5 * viewport!!.height + zoom * cameracenter!!.y).roundToInt()

        calculateCameraMatrix()
    }

    private fun calculateCameraMatrix() {
        m = JLMatrix.shiftMatrix(-cameracenter!!.x, -cameracenter!!.y, -cameracenter!!.z)
        m.transform(JLMatrix.rotateMatrix(0.0, Math.PI - cameraangle[0], 0.0))
        m.transform(JLMatrix.rotateMatrix(0.5 * Math.PI - cameraangle[1], 0.0, 0.0))
        m.transform(JLMatrix.shiftMatrix(cameracenter!!.x, cameracenter!!.y, cameracenter!!.z))

        m.transform(JLMatrix.scaleMatrix(1.0, -1.0, 1.0)) // larger y values -> smaller y pixel coord
        m.transform(JLMatrix.scaleMatrix(zoom))
        m.transform(JLMatrix.shiftMatrix(originx.toDouble(), originz.toDouble(), 0.0))
    }

    override var cameraAngle: DoubleArray
        get() {
            val ca = DoubleArray(2)
            ca[0] = cameraangle[0]
            ca[1] = cameraangle[1]
            return ca
        }
        set(camangle) {
            cameraangle[0] = camangle[0]
            cameraangle[1] = camangle[1]
            if (cameracenter == null) {
                return
            }
            calculateCameraMatrix()
        }

    override fun getXY(coord: Coordinate): IntArray {
        return getXY(JLVector(coord.x, coord.z, coord.y))
    }

    private fun getXY(vec: JLVector): IntArray {
        val v = vec.transform(m) // apply camera rotation
        val `val` = IntArray(2)
        `val`[0] = v.x.roundToInt()
        `val`[1] = v.y.roundToInt()
        return `val`
    }

    private fun getXYZ(vec: JLVector, result: JLVector): JLVector {
        result.x = vec.x * m.m00 + vec.y * m.m01 + vec.z * m.m02 + m.m03
        result.y = vec.x * m.m10 + vec.y * m.m11 + vec.z * m.m12 + m.m13
        result.z = vec.x * m.m20 + vec.y * m.m21 + vec.z * m.m22 + m.m23
        return result
    }

    override fun getScreenTranslatedCoordinate(coord: Coordinate, dx: Int, dy: Int): Coordinate {
        val v = JLVector(coord.x, coord.z, coord.y)
        val s = v.transform(m)
        val news = JLVector.add(s, JLVector(dx.toDouble(), dy.toDouble(), 0.0))
        val newv = news.transform(m.inverse())
        return Coordinate(newv.x, newv.z, newv.y)
    }

    @Suppress("LocalVariableName")
    @Throws(JuggleExceptionInternal::class)
    override fun drawFrame(time: Double, pnum: IntArray, hideJugglers: IntArray?, g: Graphics) {
        var numobjects = 5 * pat.numberOfJugglers + pat.numberOfPaths + 18

        // first reset the objects in the object pool
        for (i in 0..<numobjects) {
            obj[i].covering.clear()
        }

        // first create a list of objects in the display
        var index = 0

        // props
        var propmin = 0.0
        for (i in 1..pat.numberOfPaths) {
            obj[index].type = DrawObject2D.TYPE_PROP
            obj[index].number = i
            pat.getPathCoordinate(i, time, tempc)
            if (!tempc.isValid) {
                tempc.setCoordinate(0.0, 0.0, 0.0)
            }
            getXYZ(JLVector.fromCoordinate(tempc, tempv1), obj[index].coord[0])
            val x = obj[index].coord[0].x.roundToInt()
            val y = obj[index].coord[0].y.roundToInt()
            val pr = pat.getProp(pnum[i - 1])
            if (pr.getProp2DImage(zoom, cameraangle) != null) {
                val center = pr.getProp2DCenter(zoom, cameraangle)
                val size = pr.getProp2DSize(zoom, cameraangle)
                obj[index].boundingbox.x = x - center!!.width
                obj[index].boundingbox.y = y - center.height
                obj[index].boundingbox.width = size!!.width
                obj[index].boundingbox.height = size.height
            }
            propmin = min(propmin, pr.getMin()!!.z)
            index++
        }

        // ground (set of lines)
        if (showground) {
            for (i in 0..17) {
                obj[index].type = DrawObject2D.TYPE_LINE
                obj[index].number = 0 // unused

                // first 9 lines for ground:
                // (x, y, z): (-50 + 100 * i / 8, 0, -50) to
                //            (-50 + 100 * i / 8, 0,  50)
                // next 9 lines:
                // (x, y, z): (-50, 0, -50 + 100 * (i - 9) / 8) to
                //            ( 50, 0, -50 + 100 * (i - 9) / 8)
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
                tempv1.y = tempv2.y

                getXYZ(tempv1, obj[index].coord[0])
                getXYZ(tempv2, obj[index].coord[1])
                val x = min(
                    obj[index].coord[0].x.roundToInt(),
                    obj[index].coord[1].x.roundToInt()
                )
                val y = min(
                    obj[index].coord[0].y.roundToInt(),
                    obj[index].coord[1].y.roundToInt()
                )
                val width = abs(
                    obj[index].coord[0].x.roundToInt() - obj[index].coord[1].x.roundToInt()
                ) + 1
                val height = abs(
                    obj[index].coord[0].y.roundToInt() - obj[index].coord[1].y.roundToInt()
                ) + 1
                obj[index].boundingbox.x = x
                obj[index].boundingbox.y = y
                obj[index].boundingbox.width = width
                obj[index].boundingbox.height = height
                index++
            }
        }

        // jugglers
        Juggler.findJugglerCoordinates(pat, time, jugglervec)

        for (i in 1..pat.numberOfJugglers) {
            if (hideJugglers != null) {
                var hide = false
                for (hideJuggler in hideJugglers) {
                    if (hideJuggler == i) {
                        hide = true
                        break
                    }
                }
                if (hide) {
                    continue
                }
            }

            obj[index].type = DrawObject2D.TYPE_BODY
            obj[index].number = i
            getXYZ(jugglervec[i - 1][2]!!, obj[index].coord[0]) // left shoulder
            getXYZ(jugglervec[i - 1][3]!!, obj[index].coord[1]) // right shoulder
            getXYZ(jugglervec[i - 1][7]!!, obj[index].coord[2]) // right waist
            getXYZ(jugglervec[i - 1][6]!!, obj[index].coord[3]) // left waist
            getXYZ(jugglervec[i - 1][8]!!, obj[index].coord[4]) // left head bottom
            getXYZ(jugglervec[i - 1][9]!!, obj[index].coord[5]) // left head top
            getXYZ(jugglervec[i - 1][10]!!, obj[index].coord[6]) // right head bottom
            getXYZ(jugglervec[i - 1][11]!!, obj[index].coord[7]) // right head top
            var xmin: Int
            var xmax: Int
            var ymin: Int
            var ymax: Int
            xmax = obj[index].coord[0].x.roundToInt()
            xmin = xmax
            ymax = obj[index].coord[0].y.roundToInt()
            ymin = ymax
            for (j in 1..7) {
                val x = obj[index].coord[j].x.roundToInt()
                val y = obj[index].coord[j].y.roundToInt()
                if (x < xmin) {
                    xmin = x
                }
                if (x > xmax) {
                    xmax = x
                }
                if (y < ymin) {
                    ymin = y
                }
                if (y > ymax) {
                    ymax = y
                }
            }
            // inset bb by one pixel to avoid intersection at shoulder:
            obj[index].boundingbox.x = xmin + 1
            obj[index].boundingbox.y = ymin + 1
            obj[index].boundingbox.width = xmax - xmin - 1
            obj[index].boundingbox.height = ymax - ymin - 1
            index++

            // the lines for each arm, starting with the left:
            for (j in 0..1) {
                if (jugglervec[i - 1][4 + j] == null) {
                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(jugglervec[i - 1][2 + j]!!, obj[index].coord[0]) // entire arm
                    getXYZ(jugglervec[i - 1][j]!!, obj[index].coord[1])
                    val x = min(
                        obj[index].coord[0].x.roundToInt(),
                        obj[index].coord[1].x.roundToInt()
                    )
                    val y = min(
                        obj[index].coord[0].y.roundToInt(),
                        obj[index].coord[1].y.roundToInt()
                    )
                    val width = abs(
                        obj[index].coord[0].x.roundToInt() - obj[index].coord[1].x.roundToInt()
                    ) + 1
                    val height = abs(
                        obj[index].coord[0].y.roundToInt() - obj[index].coord[1].y.roundToInt()
                    ) + 1
                    obj[index].boundingbox.x = x
                    obj[index].boundingbox.y = y
                    obj[index].boundingbox.width = width
                    obj[index].boundingbox.height = height
                    index++
                } else {
                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(jugglervec[i - 1][2 + j]!!, obj[index].coord[0]) // upper arm
                    getXYZ(jugglervec[i - 1][4 + j]!!, obj[index].coord[1])
                    var x = min(
                        obj[index].coord[0].x.roundToInt(),
                        obj[index].coord[1].x.roundToInt()
                    )
                    var y = min(
                        obj[index].coord[0].y.roundToInt(),
                        obj[index].coord[1].y.roundToInt()
                    )
                    var width = abs(
                        obj[index].coord[0].x.roundToInt() - obj[index].coord[1].x.roundToInt()
                    ) + 1
                    var height = abs(
                        obj[index].coord[0].y.roundToInt() - obj[index].coord[1].y.roundToInt()
                    ) + 1
                    obj[index].boundingbox.x = x
                    obj[index].boundingbox.y = y
                    obj[index].boundingbox.width = width
                    obj[index].boundingbox.height = height
                    index++

                    obj[index].type = DrawObject2D.TYPE_LINE
                    obj[index].number = i
                    getXYZ(jugglervec[i - 1][4 + j]!!, obj[index].coord[0]) // lower arm
                    getXYZ(jugglervec[i - 1][j]!!, obj[index].coord[1])
                    x = min(
                        obj[index].coord[0].x.roundToInt(),
                        obj[index].coord[1].x.roundToInt()
                    )
                    y = min(
                        obj[index].coord[0].y.roundToInt(),
                        obj[index].coord[1].y.roundToInt()
                    )
                    width = abs(
                        obj[index].coord[0].x.roundToInt() - obj[index].coord[1].x.roundToInt()
                    ) + 1
                    height = abs(
                        obj[index].coord[0].y.roundToInt() - obj[index].coord[1].y.roundToInt()
                    ) + 1
                    obj[index].boundingbox.x = x
                    obj[index].boundingbox.y = y
                    obj[index].boundingbox.width = width
                    obj[index].boundingbox.height = height
                    index++
                }
            }
        }
        numobjects = index

        // figure out which display elements are covering which other elements
        for (i in 0..<numobjects) {
            for (j in 0..<numobjects) {
                if (j == i) {
                    continue
                }
                if (obj[i].isCovering(obj[j])) {
                    obj[i].covering.add(obj[j])
                }
            }
            obj[i].drawn = false
        }

        // figure out a drawing order
        index = 0
        var changed = true
        while (changed) {
            changed = false

            for (i in 0..<numobjects) {
                if (obj[i].drawn) {
                    continue
                }

                var candraw = true
                for (j in obj[i].covering.indices) {
                    val temp = obj[i].covering[j]
                    if (!temp.drawn) {
                        candraw = false
                        break
                    }
                }
                if (candraw) {
                    obj2[index] = obj[i]
                    obj[i].drawn = true
                    index++
                    changed = true
                }
            }
        }
        // just in case there were some that couldn't be drawn:
        for (i in 0..<numobjects) {
            if (obj[i].drawn) {
                continue
            }
            obj2[index] = obj[i]
            obj[i].drawn = true
            index++
            // System.out.println("got undrawable item, type "+obj[i].type);
        }

        // draw the objects in the sorted order
        for (i in 0..<numobjects) {
            val ob = obj2[i]

            when (ob.type) {
                DrawObject2D.TYPE_PROP -> {
                    val pr = pat.getProp(pnum[ob.number - 1])
                    val x = ob.coord[0].x.roundToInt()
                    val y = ob.coord[0].y.roundToInt()
                    val propimage = pr.getProp2DImage(zoom, cameraangle)
                    if (propimage != null) {
                        val grip = pr.getProp2DGrip(zoom, cameraangle)!!
                        // TODO: It's inefficient to call toAwtImage() on every draw
                        g.drawImage(propimage.toAwtImage(), x - grip.width, y - grip.height, null)
                    }
                }

                DrawObject2D.TYPE_BODY -> {
                    val bodyx = IntArray(4)
                    val bodyy = IntArray(4)
                    for (j in 0..3) {
                        bodyx[j] = ob.coord[j].x.roundToInt()
                        bodyy[j] = ob.coord[j].y.roundToInt()
                    }
                    g.color = background
                    g.fillPolygon(bodyx, bodyy, 4)
                    g.color = Color.black
                    g.drawPolygon(bodyx, bodyy, 4)

                    val LheadBx = ob.coord[4].x
                    val LheadBy = ob.coord[4].y
                    // double LheadTx = ob.coord[5].x;
                    val LheadTy = ob.coord[5].y
                    val RheadBx = ob.coord[6].x
                    val RheadBy = ob.coord[6].y

                    // double RheadTx = ob.coord[7].x;
                    // double RheadTy = ob.coord[7].y;
                    if (abs(RheadBx - LheadBx) > 2.0) {
                        // head is at least 2 pixels wide; draw it as a polygon
                        for (j in 0..<polysides) {
                            headx[j] = (0.5 * (LheadBx + RheadBx + headcos[j] * (RheadBx - LheadBx))).roundToInt()
                            heady[j] = (0.5 * (LheadBy + LheadTy + headsin[j] * (LheadBy - LheadTy))
                                + (headx[j] - LheadBx) * (RheadBy - LheadBy) / (RheadBx - LheadBx)).roundToInt()
                        }

                        g.color = background
                        g.fillPolygon(headx, heady, polysides)
                        g.color = Color.black
                        g.drawPolygon(headx, heady, polysides)
                    } else {
                        // head is edge-on; draw it as a line
                        val h = sqrt(
                            (LheadBy - LheadTy) * (LheadBy - LheadTy)
                                    + (RheadBy - LheadBy) * (RheadBy - LheadBy)
                        )
                        val headx = (0.5 * (LheadBx + RheadBx)).roundToInt()
                        val heady1 = (0.5 * (LheadTy + RheadBy + h)).roundToInt()
                        val heady2 = (0.5 * (LheadTy + RheadBy - h)).roundToInt()

                        g.color = Color.black
                        g.drawLine(headx, heady1, headx, heady2)
                    }
                }

                DrawObject2D.TYPE_LINE -> {
                    g.color = Color.black
                    val x1 = ob.coord[0].x.roundToInt()
                    val y1 = ob.coord[0].y.roundToInt()
                    val x2 = ob.coord[1].x.roundToInt()
                    val y2 = ob.coord[1].y.roundToInt()
                    g.drawLine(x1, y1, x2, y2)
                }
            }

            /*
              g.setColor(Color.black);
              g.drawLine(ob.boundingbox.x, ob.boundingbox.y,
                         ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y);
              g.drawLine(ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y,
                         ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y+ob.boundingbox.height-1);
              g.drawLine(ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y+ob.boundingbox.height-1,
                         ob.boundingbox.x, ob.boundingbox.y+ob.boundingbox.height-1);
              g.drawLine(ob.boundingbox.x, ob.boundingbox.y+ob.boundingbox.height-1,
                         ob.boundingbox.x, ob.boundingbox.y);
              */
        }
    }

    override val handWindowMax: Coordinate
        get() = Coordinate(Juggler.HAND_OUT, 0.0, 1.0)

    override val handWindowMin: Coordinate
        get() = Coordinate(-Juggler.HAND_IN, 0.0, -1.0)

    override val jugglerWindowMax: Coordinate
        get() {
            var max = pat.getJugglerMax(1)
            for (i in 2..pat.numberOfJugglers) {
                max = max(max, pat.getJugglerMax(i))
            }

            max = Coordinate.add(
                max,
                Coordinate(
                    Juggler.SHOULDER_HW,
                    Juggler.SHOULDER_HW,  // Juggler.HEAD_HW,
                    Juggler.SHOULDER_H + Juggler.NECK_H + Juggler.HEAD_H
                )
            )
            return max!!
        }

     override val jugglerWindowMin: Coordinate
        get() {
            var min = pat.getJugglerMin(1)
            for (i in 2..pat.numberOfJugglers) {
                min = min(min, pat.getJugglerMin(i))
            }

            min = Coordinate.add(
                min,
                Coordinate(-Juggler.SHOULDER_HW, -Juggler.SHOULDER_HW, 0.0)
            )
            return min!!
        }

    //--------------------------------------------------------------------------
    // Class for defining the objects to draw
    //--------------------------------------------------------------------------
    
    class DrawObject2D(numobjects: Int) {
        var type: Int = 0
        var number: Int = 0  // path number or juggler number
        var coord: MutableList<JLVector> = MutableList(8) { JLVector() }
        var boundingbox: Rectangle = Rectangle()
        var covering: MutableList<DrawObject2D> = ArrayList(numobjects)
        var drawn: Boolean = false
        var tempv: JLVector = JLVector()

        fun isCovering(obj: DrawObject2D): Boolean {
            if (!boundingbox.intersects(obj.boundingbox)) {
                return false
            }

            when (type) {
                TYPE_PROP -> when (obj.type) {
                    TYPE_PROP -> return (coord[0].z < obj.coord[0].z)
                    TYPE_BODY -> {
                        vectorProduct(obj.coord[0], obj.coord[1], obj.coord[2], tempv)
                        if (tempv.z == 0.0) {
                            return false
                        }
                        val z = obj.coord[0].z - (tempv.x * (coord[0].x - obj.coord[0].x)
                                + tempv.y * (coord[0].y - obj.coord[0].y)) / tempv.z
                        return (coord[0].z < z)
                    }

                    TYPE_LINE -> return (isBoxCoveringLine(this, obj) == 1)
                }

                TYPE_BODY -> when (obj.type) {
                    TYPE_PROP -> {
                        vectorProduct(coord[0], coord[1], coord[2], tempv)
                        if (tempv.z == 0.0) {
                            return false
                        }
                        val z = coord[0].z - (tempv.x * (obj.coord[0].x - coord[0].x)
                                + tempv.y * (obj.coord[0].y - coord[0].y)) / tempv.z
                        return (z < obj.coord[0].z)
                    }

                    TYPE_BODY -> {
                        var d = 0.0
                        var i = 0
                        while (i < 4) {
                            d += (coord[i].z - obj.coord[i].z)
                            i++
                        }
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

        // Returns 1 if box covers line, -1 if line covers box, 0 otherwise.
        private fun isBoxCoveringLine(box: DrawObject2D, line: DrawObject2D): Int {
            // If at least one end of the line is inside the box's boundingbox, then return
            // 1 if all such ends are behind the box, and -1 otherwise.
            // If neither end is inside the boundingbox, then find intersections between the
            // line and the boundingbox.  If no points of intersection, return 0.  If the
            // line is behind the bb at all points of intersection, return 1.  Otherwise
            // return -1;

            if (box.type == TYPE_BODY) {
                vectorProduct(box.coord[0], box.coord[1], box.coord[2], tempv)
            } else {
                tempv.x = 0.0
                tempv.y = 0.0
                tempv.z = 1.0
            }

            if (tempv.z == 0.0) {
                return 0 // box is exactly sideways
            }

            var endinbb = false
            for (i in 0..1) {
                val x = line.coord[i].x
                val y = line.coord[i].y

                if (box.boundingbox.contains((x + 0.5).toInt(), (y + 0.5).toInt())) {
                    val zb = (box.coord[0].z
                            - (tempv.x * (x - box.coord[0].x) + tempv.y * (y - box.coord[0].y)) / tempv.z)
                    if (line.coord[i].z < (zb - SLOP)) {
                        return -1
                    }
                    endinbb = true
                }
            }
            if (endinbb) {
                return 1 // know that end wasn't in front of body
            }

            var intersection = false
            for (i in 0..1) {
                val x = (if (i == 0) box.boundingbox.x else (box.boundingbox.x + box.boundingbox.width - 1))
                if (x < min(line.coord[0].x, line.coord[1].x)
                    || x > max(line.coord[0].x, line.coord[1].x)
                ) {
                    continue
                }
                if (line.coord[1].x == line.coord[0].x) {
                    continue
                }
                val y = (line.coord[0].y + (line.coord[1].y - line.coord[0].y)
                        * (x.toDouble() - line.coord[0].x) / (line.coord[1].x - line.coord[0].x))
                if (y < box.boundingbox.y || y > (box.boundingbox.y + box.boundingbox.height - 1)) {
                    continue
                }
                intersection = true
                val zb = (box.coord[0].z
                        - (tempv.x * (x - box.coord[0].x) + tempv.y * (y - box.coord[0].y)) / tempv.z)
                val zl = (line.coord[0].z
                        + (line.coord[1].z - line.coord[0].z)
                        * (x.toDouble() - line.coord[0].x)
                        / (line.coord[1].x - line.coord[0].x))
                if (zl < (zb - SLOP)) {
                    return -1
                }
            }
            for (i in 0..1) {
                val y = (if (i == 0) box.boundingbox.y else (box.boundingbox.y + box.boundingbox.height - 1))
                if (y < min(line.coord[0].y, line.coord[1].y)
                    || y > max(line.coord[0].y, line.coord[1].y)
                ) {
                    continue
                }
                if (line.coord[1].y == line.coord[0].y) {
                    continue
                }
                val x = (line.coord[0].x + (line.coord[1].x - line.coord[0].x)
                        * (y.toDouble() - line.coord[0].y) / (line.coord[1].y - line.coord[0].y))
                if (x < box.boundingbox.x || x > (box.boundingbox.x + box.boundingbox.width - 1)) {
                    continue
                }
                intersection = true
                val zb = (box.coord[0].z
                        - (tempv.x * (x - box.coord[0].x) + tempv.y * (y.toDouble() - box.coord[0].y))
                        / tempv.z)
                val zl = (line.coord[0].z
                        + (line.coord[1].z - line.coord[0].z) * (x - line.coord[0].x)
                        / (line.coord[1].x - line.coord[0].x))
                if (zl < (zb - SLOP)) {
                    return -1
                }
            }

            return (if (intersection) 1 else 0)
        }

        fun vectorProduct(v1: JLVector, v2: JLVector, v3: JLVector, result: JLVector): JLVector {
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

    companion object {
        const val ORIGINAL_ZOOM = true
    }
}
