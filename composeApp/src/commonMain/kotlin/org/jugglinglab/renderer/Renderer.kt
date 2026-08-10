//
// Renderer.kt
//
// Draws the juggling into the frame using Compose DrawScope.
//
// The renderer's first job is translating from the global coordinate system the
// pattern is defined in, to an appropriately scaled screen-space coordinate
// system.
//
// Its second job is rendering frames of juggling. The scene is decomposed into
// DrawObject2D objects, which are painted one at a time, in their entirety,
// from back to front within the scene. This entails working out a drawing order
// and handling coverage cycles when they occur.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.core.Constants
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.renderer.DrawObject2D.Type
import org.jugglinglab.util.Coordinate
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
    // absent from the map use the default.
    private val defaultAvatar = ClassicAvatar()
    private var avatars: Map<Int, Avatar> = emptyMap()
    fun avatarFor(juggler: Int): Avatar = avatars[juggler] ?: defaultAvatar

    //--------------------------------------------------------------------------
    // Methods to configure renderer
    //--------------------------------------------------------------------------

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
        viewport = Rect(
            border.toFloat(),
            border.toFloat(),
            (width - border).toFloat(),
            (height - border).toFloat()
        )

        val adjustedMax = overallMax.copy()
        val adjustedMin = overallMin.copy()

        if (USE_ORIGINAL_ZOOM) {
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

            originX =
                (viewport!!.left + 0.5 * viewport!!.width - zoom * cameraCenter!!.x).roundToInt()
            originZ =
                (viewport!!.top + 0.5 * viewport!!.height + zoom * cameraCenter!!.y).roundToInt()
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

    //--------------------------------------------------------------------------
    // Translating from global to screen coordinates
    //--------------------------------------------------------------------------

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

    //--------------------------------------------------------------------------
    // Public drawing methods
    //--------------------------------------------------------------------------

    fun drawFrame(
        time: Double,
        propNumForPath: List<Int>,
        hideJugglers: List<Int>,
        scope: DrawScope,
        isPaused: Boolean = false
    ) {
        // 1. Add all visible objects to pool
        populateObjectPool(time, propNumForPath, hideJugglers, isPaused)

        // 2. Determine which objects are covering which other objects
        determineCoverage(isPaused)

        // 3. Determine drawing order using Kahn-FAS topological sort
        determineDrawingOrder(isPaused)

        // 4. Render objects in sorted order
        scope.drawObjects(propNumForPath)
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
    // Adding objects to the scene for rendering
    //--------------------------------------------------------------------------

    private fun populateObjectPool(
        time: Double,
        propNumForPath: List<Int>,
        hideJugglers: List<Int>,
        isPaused: Boolean
    ) {
        objectPool.reset()

        // Props
        var propMinZ = 0.0
        for (i in 1..pattern.numberOfPaths) {
            val propObj = objectPool.next()
            pattern.layout.getPathCoordinate(i, time, tempc)
            if (!tempc.isValid) {
                tempc.setCoordinate(0.0, 0.0, 0.0)
            }
            propObj.set3DCoordinates(Type.PROP, i, tempc)
            debugDrawing(isPaused) {
                propObj.label = "Prop $i"
            }

            val pr = pattern.getProp(propNumForPath[i - 1])
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
                lineObj.set3DCoordinates(Type.LINE, 0, tempv1, tempv2)
                debugDrawing(isPaused) {
                    lineObj.label = "Ground line ${i + 1}"
                }
            }
        }

        // Jugglers
        for (i in 1..pattern.numberOfJugglers) {
            if (i in hideJugglers) continue
            avatarFor(i).addObjectsToPool(i, pattern, time, objectPool)
        }

        // Project 3D to 2D screen coordinates and compute bounds
        for ((idx, ob) in objectPool.withIndex()) {
            ob.sequenceNumber = idx + 1

            for (p in 0..<ob.numPoints) {
                getXYZ(ob.coords3D[p], ob.coords2D[p])
            }

            if (ob.type == Type.PROP) {
                val x = ob.coords2D[0].x.roundToInt()
                val y = ob.coords2D[0].y.roundToInt()
                val pr = pattern.getProp(propNumForPath[ob.number - 1])
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

        debugDrawing(isPaused) {
            fun fmt(d: Double): String = org.jugglinglab.util.jlToStringRounded(d, 1)
            fun fmtF(f: Float): String = org.jugglinglab.util.jlToStringRounded(f.toDouble(), 1)

            fun DrawObject2D.coords3DToString(): String {
                return (0..<numPoints).joinToString(", ") { i ->
                    "(${fmt(coords3D[i].x)}, ${fmt(coords3D[i].y)}, ${fmt(coords3D[i].z)})"
                }
            }

            fun DrawObject2D.coords2DToString(): String {
                return (0..<numPoints).joinToString(", ") { i ->
                    "(${fmt(coords2D[i].x)}, ${fmt(coords2D[i].y)}, z=${fmt(coords2D[i].z)})"
                }
            }

            fun DrawObject2D.bounds2DToString(): String {
                return "[left=${fmtF(bbLeft)}, right=${fmtF(bbRight)}, top=${fmtF(bbTop)}, bottom=${
                    fmtF(
                        bbBottom
                    )
                }]"
            }

            val roundedTime = (kotlin.math.round(time * 100.0) / 100.0)
            println("==================== DRAWING DEBUG (time = ${roundedTime}s) ====================")
            println("--- Active DrawObjects (total: ${objectPool.activeCount}) ---")
            for (ob in objectPool) {
                println("[#${ob.sequenceNumber}] \"${ob.label}\" (${ob.type}, ${ob.numPoints} pts)")
                println("  3D: ${ob.coords3DToString()}")
                println("  2D: ${ob.coords2DToString()}")
                println("  Bounds: ${ob.bounds2DToString()}")
            }
        }
    }

    //--------------------------------------------------------------------------
    // Helpers for determining drawing order
    //--------------------------------------------------------------------------

    private fun determineCoverage(isPaused: Boolean) {
        for (i in 0..<objectPool.activeCount) {
            val objI = objectPool.objects[i]
            for (j in (i + 1)..<objectPool.activeCount) {
                val objJ = objectPool.objects[j]
                val cmp = objI.compareCovering(objJ)
                if (cmp > 0) {
                    objI.covering.add(objJ)
                } else if (cmp < 0) {
                    objJ.covering.add(objI)
                }
            }
        }

        debugDrawing(isPaused) {
            println("\n--- Covering Summary ---")
            for (ob in objectPool) {
                if (ob.covering.isEmpty()) {
                    println("[#${ob.sequenceNumber} \"${ob.label}\"] covers: NONE")
                } else {
                    val cov =
                        ob.covering.joinToString(", ") { "[#${it.sequenceNumber} \"${it.label}\"]" }
                    println("[#${ob.sequenceNumber} \"${ob.label}\"] covers: $cov")
                }
            }

            // find and print coverage cycles
            val cycles = mutableListOf<List<DrawObject2D>>()
            val maxCycles = 50
            val maxDepth = 10
            var stepCount = 0
            val maxSteps = 10000

            for (startIdx in 0..<objectPool.activeCount) {
                if (cycles.size >= maxCycles || stepCount >= maxSteps) break
                val startNode = objectPool.objects[startIdx]
                val path = mutableListOf(startNode)
                val visitedInPath = mutableSetOf(startNode)

                fun dfs(current: DrawObject2D) {
                    stepCount++
                    if (stepCount >= maxSteps || cycles.size >= maxCycles) return

                    for (neighbor in current.covering) {
                        if (neighbor === startNode) {
                            if (path.size >= 2) {
                                val minSeq = path.minOf { it.sequenceNumber }
                                if (startNode.sequenceNumber == minSeq) {
                                    cycles.add(path.toList() + startNode)
                                }
                            }
                        } else if (neighbor !in visitedInPath && path.size < maxDepth) {
                            path.add(neighbor)
                            visitedInPath.add(neighbor)
                            dfs(neighbor)
                            visitedInPath.remove(neighbor)
                            path.removeAt(path.size - 1)
                        }
                    }
                }

                dfs(startNode)
            }

            println("\n--- Coverage Cycles ---")
            if (cycles.isEmpty()) {
                println("No coverage cycles detected.")
            } else {
                val limitStr =
                    if (cycles.size >= maxCycles || stepCount >= maxSteps) " (search limit reached)" else ""
                println("WARNING: Detected ${cycles.size} coverage cycle(s)$limitStr:")
                for (i in cycles.indices) {
                    val cycleStr =
                        cycles[i].joinToString(" -> ") { "[#${it.sequenceNumber} \"${it.label}\"]" }
                    println("  Cycle #${i + 1}: $cycleStr")
                }
            }
        }
    }

    private fun determineDrawingOrder(isPaused: Boolean) {
        while (sortedObjects.size < objectPool.activeCount) {
            sortedObjects.add(objectPool.objects[0])
        }

        for (ob in objectPool) {
            ob.drawn = false
        }

        debugDrawing(isPaused) {
            println("\n--- Kahn-FAS Topological Sort ---")
        }

        var index = 0
        while (index < objectPool.activeCount) {
            var progress = false

            // 1. Topological sort: draw any object whose coverage dependencies
            // are all drawn
            for (ob in objectPool) {
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
                    progress = true
                    debugDrawing(isPaused) {
                        println("Order $index: [#${ob.sequenceNumber} \"${ob.label}\"]")
                    }
                }
            }

            // 2. If stalled then there must be a cycle in the coverage graph;
            // delete the highest-preference active coverage link and continue
            if (!progress && index < objectPool.activeCount) {
                var bestCoverer: DrawObject2D? = null
                var bestCovered: DrawObject2D? = null
                var bestPriority = Int.MAX_VALUE
                var bestCovererZ = -Double.MAX_VALUE

                for (coverer in objectPool) {
                    if (coverer.drawn) continue
                    for (covered in coverer.covering) {
                        if (covered.drawn) continue

                        val prio = getCoverageLinkPriority(coverer, covered)
                        val covererZ = coverer.coords2D.firstOrNull()?.z ?: 0.0

                        if (prio < bestPriority || (prio == bestPriority && covererZ > bestCovererZ)) {
                            bestPriority = prio
                            bestCoverer = coverer
                            bestCovered = covered
                            bestCovererZ = covererZ
                        }
                    }
                }

                if (bestCoverer != null && bestCovered != null) {
                    bestCoverer.covering.remove(bestCovered)
                    debugDrawing(isPaused) {
                        val prioName = when (bestPriority) {
                            1 -> "LINE-PROP"
                            2 -> "LINE-POLY"
                            3 -> "POLY-POLY"
                            4 -> "PROP-POLY"
                            5 -> "PROP-PROP"
                            else -> "OTHER"
                        }
                        println("Cycle detected. Deleted $prioName coverage link: [#${bestCoverer.sequenceNumber} \"${bestCoverer.label}\"] COVERS [#${bestCovered.sequenceNumber} \"${bestCovered.label}\"]")
                    }
                } else {
                    // fallback if no candidate edge found
                    for (ob in objectPool) {
                        if (!ob.drawn) {
                            sortedObjects[index] = ob
                            ob.drawn = true
                            index++
                            debugDrawing(isPaused) {
                                println("Fallback force-draw order $index: [#${ob.sequenceNumber} \"${ob.label}\"]")
                            }
                            break
                        }
                    }
                }
            }
        }

        debugDrawing(isPaused) {
            println("======================================================================\n")
        }
    }

    private fun getCoverageLinkPriority(coverer: DrawObject2D, covered: DrawObject2D): Int {
        return when (coverer.type) {
            Type.PROP -> when (covered.type) {
                Type.PROP -> 5 // (least preferred to delete)
                Type.POLY -> 4
                Type.LINE -> 1 // (most preferred to delete)
            }

            Type.POLY -> when (covered.type) {
                Type.PROP -> 4
                Type.POLY -> 3
                Type.LINE -> 2
            }

            Type.LINE -> when (covered.type) {
                Type.PROP -> 1
                Type.POLY -> 2
                Type.LINE -> 6 // shouldn't occur
            }
        }
    }

    //--------------------------------------------------------------------------
    // Helpers for drawing
    //--------------------------------------------------------------------------

    private val fillPath: Path = Path()
    private val strokePath: Path = Path()

    private fun DrawScope.drawObjects(
        propNumForPath: List<Int>
    ) {
        val activeStrokeWidth05 = 0.5.dp.toPx()
        val activeStrokeWidth1 = 1.dp.toPx()
        val activeStroke1 = Stroke(activeStrokeWidth1)

        for (i in 0..<objectPool.activeCount) {
            val ob = sortedObjects[i]
            when (ob.type) {
                Type.PROP -> {
                    val pr = pattern.getProp(propNumForPath[ob.number - 1])
                    // rounding prop location to int values makes animated GIFs
                    // considerably smaller, with no visible impact
                    val x = ob.coords2D[0].x.roundToInt()
                    val y = ob.coords2D[0].y.roundToInt()

                    val image = pr.getProp2DImage(zoom, cameraAngle)
                    if (image != null) {
                        val grip = pr.getProp2DGrip(zoom, cameraAngle)
                        drawImage(
                            image = image,
                            topLeft = Offset(
                                (x - grip.width).toFloat(),
                                (y - grip.height).toFloat()
                            )
                        )
                    }
                }

                Type.POLY -> {
                    if (ob.numPoints < 3) return

                    fillPath.rewind()
                    fillPath.moveTo(ob.coords2D[0].x.toFloat(), ob.coords2D[0].y.toFloat())
                    for (i in 1..<ob.numPoints) {
                        fillPath.lineTo(ob.coords2D[i].x.toFloat(), ob.coords2D[i].y.toFloat())
                    }
                    fillPath.close()
                    drawAaPath(fillPath, backgroundColor)

                    if (ob.isClosed) {
                        drawAaPath(fillPath, lineColor, style = activeStroke1)
                    } else {
                        strokePath.rewind()
                        strokePath.moveTo(ob.coords2D[0].x.toFloat(), ob.coords2D[0].y.toFloat())
                        for (i in 1..<ob.numPoints) {
                            strokePath.lineTo(
                                ob.coords2D[i].x.toFloat(),
                                ob.coords2D[i].y.toFloat()
                            )
                        }
                        drawAaPath(strokePath, lineColor, style = activeStroke1)
                    }
                }

                Type.LINE -> {
                    if (ob.numPoints < 2) return

                    val p1 = Offset(ob.coords2D[0].x.toFloat(), ob.coords2D[0].y.toFloat())
                    val p2 = Offset(ob.coords2D[1].x.toFloat(), ob.coords2D[1].y.toFloat())
                    if (ob.number > 0) {
                        // juggler lines
                        drawAaLine(lineColor, p1, p2, strokeWidth = activeStrokeWidth1)
                    } else {
                        // ground lines
                        drawAaLine(lineColor, p1, p2, strokeWidth = activeStrokeWidth05)
                    }
                }
            }
        }
    }

    // Our own versions of drawPath and drawLine let us switch antialiased
    // rendering off when making animated GIFs.

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

    //--------------------------------------------------------------------------
    // Helper for debug logging
    //--------------------------------------------------------------------------

    // inline fun makes compiler prune logging code when DEBUG_DRAWING is false

    private inline fun debugDrawing(condition: Boolean = true, block: () -> Unit) {
        @Suppress("SimplifyBooleanWithConstants")
        if (Constants.DEBUG_DRAWING && condition) {
            block()
        }
    }

    companion object {
        const val USE_ORIGINAL_ZOOM = true
    }
}
