//
// LadderDiagram.kt
//
// This class draws the vertical ladder diagram on the right side of Edit view.
// This version does not include any mouse interaction or editing functions;
// those are added in EditLadderDiagram.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.JugglingLab.guistrings
import jugglinglab.core.AnimationPanel.AnimationAttachment
import jugglinglab.jml.*
import jugglinglab.util.NumberFormatter.jlToStringRounded
import jugglinglab.util.toAwtColor
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.image.BufferedImage
import java.text.MessageFormat
import java.util.*
import javax.swing.JPanel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

open class LadderDiagram(p: JMLPattern) :
    JPanel(), AnimationAttachment, MouseListener, MouseMotionListener {
    protected var ap: AnimationPanel? = null
    protected val pat: JMLPattern
    protected var ladderWidth: Int = 0 // pixel dimensions of entire panel
    protected var ladderHeight: Int = 0
    protected var rightX: Int = 0 // right/left hand pos. for juggler 1 (px)
    protected var leftX: Int = 0
    protected var jugglerDeltaX: Int = 0 // horizontal offset between jugglers (px)
    protected var guiState: Int = STATE_INACTIVE // one of STATE_x values above
    protected var simTime: Double = 0.0
    protected var trackerY: Int = BORDER_TOP
    protected var hasSwitchSymmetry: Boolean = false
    protected var hasSwitchdelaySymmetry: Boolean = false

    protected var ladderEventItems: ArrayList<LadderEventItem>? = null
    protected var ladderPathItems: ArrayList<LadderPathItem>? = null
    protected var ladderPositionItems: ArrayList<LadderPositionItem>? = null

    protected var im: BufferedImage? = null
    protected var imageValid: Boolean = false
    protected var framesUntilImageDraw: Int = 0

    protected var animPaused: Boolean = false

    init {
        setBackground(COLOR_BACKGROUND)
        setOpaque(false)
        pat = p

        val jugglers = pat.numberOfJugglers
        if (jugglers > MAX_JUGGLERS) {
            // allocate enough space for a "too many jugglers" message; see paintLadder()
            val template: String = guistrings.getString("Too_many_jugglers")
            val arguments = arrayOf<Any?>(MAX_JUGGLERS)
            val message = MessageFormat.format(template, *arguments)
            val mwidth = 20 + getFontMetrics(MSGFONT).stringWidth(message)
            preferredSize = Dimension(mwidth, 1)
            minimumSize = Dimension(mwidth, 1)
        } else {
            var prefWidth: Int = LADDER_WIDTH_PER_JUGGLER * jugglers
            val minWidth: Int = LADDER_MIN_WIDTH_PER_JUGGLER * jugglers
            val widthMult =
                doubleArrayOf(
                    1.0, 1.0, 0.85, 0.72, 0.65, 0.55,
                )
            prefWidth = (prefWidth.toDouble() *
                (if (jugglers >= widthMult.size) 0.5 else widthMult[jugglers])).toInt()
            prefWidth = max(prefWidth, minWidth)
            preferredSize = Dimension(prefWidth, 1)
            minimumSize = Dimension(minWidth, 1)

            pat.layoutPattern() // ensures we have event list
            createView()

            addMouseListener(this)
            addMouseMotionListener(this)
        }
    }

    //----------------------------------------------------------------------------
    // java.awt.event.MouseListener methods
    //----------------------------------------------------------------------------
    override fun mousePressed(me: MouseEvent) {
        if (ap != null && (ap!!.writingGIF || !ap!!.engineAnimating)) {
            return
        }

        var my = me.getY()
        my = min(max(my, BORDER_TOP), ladderHeight - BORDER_TOP)

        guiState = STATE_MOVING_TRACKER
        trackerY = my
        if (ap != null) {
            val scale = ((pat.loopEndTime - pat.loopStartTime)
                / (ladderHeight - 2 * BORDER_TOP).toDouble())
            val newtime = (my - BORDER_TOP).toDouble() * scale
            animPaused = ap!!.isPaused
            ap!!.isPaused = true
            ap!!.time = newtime
        }

        repaint()
        if (ap != null) {
            ap!!.repaint()
        }
    }

    override fun mouseReleased(me: MouseEvent?) {
        if (ap != null && (ap!!.writingGIF || !ap!!.engineAnimating)) {
            return
        }

        guiState = STATE_INACTIVE
        if (ap != null) {
            ap!!.isPaused = animPaused
        }
        repaint()
    }

    override fun mouseClicked(e: MouseEvent?) {}

    override fun mouseEntered(e: MouseEvent?) {}

    override fun mouseExited(e: MouseEvent?) {}

    //----------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //----------------------------------------------------------------------------
    override fun mouseDragged(me: MouseEvent) {
        if (ap != null && (ap!!.writingGIF || !ap!!.engineAnimating)) {
            return
        }

        var my = me.getY()
        my = min(max(my, BORDER_TOP), ladderHeight - BORDER_TOP)
        trackerY = my
        repaint()

        if (ap != null) {
            val scale = ((pat.loopEndTime - pat.loopStartTime)
                / (ladderHeight - 2 * BORDER_TOP).toDouble())
            val newtime = (my - BORDER_TOP).toDouble() * scale
            ap!!.time = newtime
            ap!!.repaint()
        }
    }

    override fun mouseMoved(e: MouseEvent?) {}

    //----------------------------------------------------------------------------
    // Methods to interact with ladder items
    //----------------------------------------------------------------------------
    protected fun getSelectedLadderEvent(x: Int, y: Int): LadderEventItem? {
        for (item in ladderEventItems!!) {
            if (x >= item.xlow && x <= item.xhigh && y >= item.ylow && y <= item.yhigh) {
                return item
            }
        }
        return null
    }

    protected fun getSelectedLadderPosition(x: Int, y: Int): LadderPositionItem? {
        for (item in ladderPositionItems!!) {
            if (x >= item.xLow && x <= item.xHigh && y >= item.yLow && y <= item.yHigh) {
                return item
            }
        }
        return null
    }

    @Suppress("SameParameterValue")
    protected fun getSelectedLadderPath(x: Int, y: Int, slop: Int): LadderPathItem? {
        var result: LadderPathItem? = null
        var dmin = 0.0

        if (y < (BORDER_TOP - slop) || y > (ladderHeight - BORDER_TOP + slop)) {
            return null
        }

        for (item in ladderPathItems!!) {
            var d: Double

            if (item.type == LadderItem.TYPE_SELF) {
                if (y < (item.yStart - slop) || y > (item.yEnd + slop)) {
                    continue
                }
                d =
                    ((x - item.xCenter) * (x - item.xCenter) + (y - item.yCenter) * (y - item.yCenter)).toDouble()
                d = abs(sqrt(d) - item.radius)
            } else {
                val xmin = min(item.xStart, item.xEnd)
                val xmax = max(item.xStart, item.xEnd)

                if (x < (xmin - slop) || x > (xmax + slop)) {
                    continue
                }
                if (y < (item.yStart - slop) || y > (item.yEnd + slop)) {
                    continue
                }
                d = ((item.xEnd - item.xStart) * (y - item.yStart)
                    - (x - item.xStart) * (item.yEnd - item.yStart)).toDouble()
                d = abs(d) / sqrt(
                    ((item.xEnd - item.xStart) * (item.xEnd - item.xStart)
                        + (item.yEnd - item.yStart) * (item.yEnd - item.yStart)).toDouble()
                )
            }

            if (d.toInt() < slop) {
                if (result == null || d < dmin) {
                    result = item
                    dmin = d
                }
            }
        }
        return result
    }

    /*
  public void setPathColor(int path, Color color) {
    for (LadderPathItem item : ladderpathitems) {
      if (item.pathnum == path) {
        item.color = color;
      }
    }
  }
  */
    protected fun updateTrackerPosition() {
        val loopStart = pat.loopStartTime
        val loopEnd = pat.loopEndTime
        trackerY = (
            0.5 +
                (ladderHeight - 2 * BORDER_TOP).toDouble() *
                (simTime - loopStart) / (loopEnd - loopStart)
            ).toInt() + BORDER_TOP
    }

    //--------------------------------------------------------------------------
    // Methods to create and paint the ladder view
    //--------------------------------------------------------------------------

    // Create arrays of all the elements in the ladder diagram.
    protected fun createView() {
        hasSwitchdelaySymmetry = false
        hasSwitchSymmetry = false

        for (sym in pat.symmetries) {
            when (sym.getType()) {
                JMLSymmetry.TYPE_SWITCH -> hasSwitchSymmetry = true
                JMLSymmetry.TYPE_SWITCHDELAY -> hasSwitchdelaySymmetry = true
            }
        }

        val loopStart = pat.loopStartTime
        val loopEnd = pat.loopEndTime

        // first create events (black circles on the vertical lines representing hands)
        ladderEventItems = ArrayList()
        val eventlist = pat.eventList
        var ev = eventlist

        while (ev!!.t < loopStart) {
            ev = ev.next
        }

        while (ev!!.t < loopEnd) {
            val item = LadderEventItem()
            item.type = LadderItem.TYPE_EVENT
            item.eventitem = item
            item.event = ev
            ladderEventItems!!.add(item)

            for (i in 0..<ev.numberOfTransitions) {
                val item2 = LadderEventItem()
                item2.type = LadderItem.TYPE_TRANSITION
                item2.eventitem = item
                item2.event = ev
                item2.transnum = i
                ladderEventItems!!.add(item2)
            }

            ev = ev.next
        }

        // create paths (lines and arcs)
        ladderPathItems = ArrayList()
        ev = eventlist
        while (ev!!.t <= loopEnd) {
            for (i in 0..<ev.numberOfTransitions) {
                val tr = ev.getTransition(i)
                val opl = tr.outgoingPathLink

                if (opl != null) {
                    val item = LadderPathItem()
                    item.transnumStart = i
                    item.startEvent = opl.startEvent
                    item.endEvent = opl.endEvent

                    if (opl.isInHand) {
                        item.type = LadderItem.TYPE_HOLD
                    } else if (item.startEvent!!.juggler != item.endEvent!!.juggler) {
                        item.type = LadderItem.TYPE_PASS
                    } else {
                        item.type = if (item.startEvent!!.hand == item.endEvent!!.hand)
                            LadderItem.TYPE_SELF
                        else
                            LadderItem.TYPE_CROSS
                    }

                    item.pathNum = opl.pathNum
                    item.color = Color.black
                    ladderPathItems!!.add(item)
                }
            }

            ev = ev.next
        }

        // create juggler positions
        ladderPositionItems = ArrayList()
        var pos = pat.positionList

        while (pos != null && pos.t < loopStart) {
            pos = pos.next
        }

        while (pos != null && pos.t < loopEnd) {
            val item = LadderPositionItem()
            item.type = LadderItem.TYPE_POSITION
            item.position = pos
            ladderPositionItems!!.add(item)

            pos = pos.next
        }

        updateView()
    }

    // Assign physical locations to all the elements in the ladder diagram.
    protected fun updateView() {
        val dim = size
        ladderWidth = dim.width
        ladderHeight = dim.height

        // calculate placements of hands and jugglers
        val scale: Double =
            ladderWidth.toDouble() / (BORDER_SIDES * 2 + JUGGLER_SEPARATION * (pat.numberOfJugglers - 1) + pat.numberOfJugglers)
        leftX = (scale * BORDER_SIDES + 0.5).toInt()
        rightX = (scale * (BORDER_SIDES + 1.0) + 0.5).toInt()
        jugglerDeltaX = (scale * (1.0 + JUGGLER_SEPARATION) + 0.5).toInt()

        // invalidate cached image of ladder diagram
        imageValid = false
        im = null
        framesUntilImageDraw = IMAGE_DRAW_WAIT

        val loopStart = pat.loopStartTime
        val loopEnd = pat.loopEndTime

        // set locations of events and transitions
        for (item in ladderEventItems!!) {
            val ev = item.event

            var eventX: Int =
                ((if (ev!!.hand == HandLink.LEFT_HAND) leftX else rightX)
                + (ev.juggler - 1) * jugglerDeltaX
                - TRANSITION_RADIUS)
            val eventY: Int =
                ((0.5 + (ladderHeight - 2 * BORDER_TOP).toDouble() *
                    (ev.t - loopStart) / (loopEnd - loopStart)).toInt() +
                    BORDER_TOP - TRANSITION_RADIUS)

            if (item.type != LadderItem.TYPE_EVENT) {
                if (ev.hand == HandLink.LEFT_HAND) {
                    eventX += 2 * TRANSITION_RADIUS * (item.transnum + 1)
                } else {
                    eventX -= 2 * TRANSITION_RADIUS * (item.transnum + 1)
                }
            }
            item.xlow = eventX
            item.xhigh = eventX + 2 * TRANSITION_RADIUS
            item.ylow = eventY
            item.yhigh = eventY + 2 * TRANSITION_RADIUS
        }

        // set locations of paths (lines and arcs)
        for (item in ladderPathItems!!) {
            item.xStart =
                ((if (item.startEvent!!.hand == HandLink.LEFT_HAND)
                    (leftX + (item.transnumStart + 1) * 2 * TRANSITION_RADIUS)
                else
                    (rightX - (item.transnumStart + 1) * 2 * TRANSITION_RADIUS))
                    + (item.startEvent!!.juggler - 1) * jugglerDeltaX)
            item.yStart =
                (0.5 + (ladderHeight - 2 * BORDER_TOP).toDouble() * (item.startEvent!!.t - loopStart) / (loopEnd - loopStart)).toInt() + BORDER_TOP
            item.yEnd =
                (0.5 + (ladderHeight - 2 * BORDER_TOP).toDouble() * (item.endEvent!!.t - loopStart) / (loopEnd - loopStart)).toInt() + BORDER_TOP

            var slot = 0
            for (j in 0..<item.endEvent!!.numberOfTransitions) {
                val temp = item.endEvent!!.getTransition(j)
                if (temp.path == item.pathNum) {
                    slot = j
                    break
                }
            }
            item.xEnd = ((if (item.endEvent!!.hand == HandLink.LEFT_HAND)
                (leftX + (slot + 1) * 2 * TRANSITION_RADIUS)
            else
                (rightX - (slot + 1) * 2 * TRANSITION_RADIUS))
                + (item.endEvent!!.juggler - 1) * jugglerDeltaX)

            if (item.type == LadderItem.TYPE_SELF) {
                val a = 0.5 * sqrt(
                    ((item.xStart - item.xEnd) * (item.xStart - item.xEnd)).toDouble() + ((item.yStart - item.yEnd) * (item.yStart - item.yEnd)).toDouble()
                )
                val xt = 0.5 * (item.xStart + item.xEnd).toDouble()
                val yt = 0.5 * (item.yStart + item.yEnd).toDouble()
                val b: Double = SELFTHROW_WIDTH * (ladderWidth.toDouble() / pat.numberOfJugglers)
                var d = 0.5 * (a * a / b - b)
                if (d < (0.5 * b)) {
                    d = 0.5 * b
                }
                val mult = if (item.endEvent!!.hand == HandLink.LEFT_HAND) -1.0 else 1.0
                val xc = xt + mult * d * (yt - item.yStart.toDouble()) / a
                val yc = yt + mult * d * (item.xStart.toDouble() - xt) / a
                val rad = sqrt(
                    (item.xStart.toDouble() - xc) * (item.xStart.toDouble() - xc)
                        + (item.yStart.toDouble() - yc) * (item.yStart.toDouble() - yc)
                )
                item.xCenter = (0.5 + xc).toInt()
                item.yCenter = (0.5 + yc).toInt()
                item.radius = (0.5 + rad).toInt()
            }
        }

        // set locations of juggler positions
        for (item in ladderPositionItems!!) {
            val pos = item.position

            val positionX: Int =
                (leftX + rightX) / 2 + (pos!!.juggler - 1) * jugglerDeltaX - POSITION_RADIUS
            val positionY: Int =
                ((0.5 +
                    (ladderHeight - 2 * BORDER_TOP).toDouble() *
                    (pos.t - loopStart) / (loopEnd - loopStart)).toInt() +
                    BORDER_TOP - POSITION_RADIUS)

            item.xLow = positionX
            item.xHigh = positionX + 2 * POSITION_RADIUS
            item.yLow = positionY
            item.yHigh = positionY + 2 * POSITION_RADIUS
        }

        // update position of tracker bar
        updateTrackerPosition()
    }

    // Return true if ladder was drawn successfully, false otherwise.
    protected fun paintLadder(gr: Graphics): Boolean {
        if (pat.numberOfJugglers > MAX_JUGGLERS) {
            val dim = size
            gr.font = MSGFONT
            val fm = gr.fontMetrics
            val template: String = guistrings.getString("Too_many_jugglers")
            val arguments = arrayOf<Any?>(MAX_JUGGLERS)
            val message = MessageFormat.format(template, *arguments)
            val mwidth = fm.stringWidth(message)
            val x = max((dim.width - mwidth) / 2, 0)
            val y = (dim.height + fm.height) / 2
            gr.color = COLOR_BACKGROUND
            gr.fillRect(0, 0, dim.width, dim.height)
            gr.color = Color.black
            gr.drawString(message, x, y)
            return false
        }

        var g = gr

        // check if ladder was resized
        val dim = size
        if (dim.width != ladderWidth || dim.height != ladderHeight) {
            updateView()
        }

        val rebuildLadderImage = (!imageValid && --framesUntilImageDraw <= 0)

        if (rebuildLadderImage) {
            im = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice
                .defaultConfiguration
                .createCompatibleImage(ladderWidth, ladderHeight, Transparency.OPAQUE)
            g = im!!.graphics

            if (g is Graphics2D) {
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
            }
        }

        if (!imageValid) {
            // first erase the background
            g.color = COLOR_BACKGROUND
            g.fillRect(0, 0, ladderWidth, ladderHeight)

            // draw the lines signifying symmetries
            g.color = COLOR_SYMMETRIES
            g.drawLine(0, BORDER_TOP, ladderWidth, BORDER_TOP)
            g.drawLine(0, ladderHeight - BORDER_TOP, ladderWidth, ladderHeight - BORDER_TOP)
            if (hasSwitchSymmetry) {
                g.drawLine(leftX, ladderHeight - BORDER_TOP / 2, ladderWidth - leftX, ladderHeight - BORDER_TOP / 2)
                g.drawLine(
                    leftX,
                    ladderHeight - BORDER_TOP / 2,
                    leftX + leftX,
                    ladderHeight - BORDER_TOP * 3 / 4
                )
                g.drawLine(
                    leftX,
                    ladderHeight - BORDER_TOP / 2,
                    leftX + leftX,
                    ladderHeight - BORDER_TOP / 4
                )
                g.drawLine(
                    ladderWidth - leftX, ladderHeight - BORDER_TOP / 2,
                    ladderWidth - 2 * leftX, ladderHeight - BORDER_TOP * 3 / 4
                )
                g.drawLine(
                    ladderWidth - leftX, ladderHeight - BORDER_TOP / 2,
                    ladderWidth - 2 * leftX, ladderHeight - BORDER_TOP / 4
                )
            }
            if (hasSwitchdelaySymmetry) {
                g.drawLine(0, ladderHeight / 2, ladderWidth, ladderHeight / 2)
            }

            // draw the lines representing the hands
            g.color = COLOR_HANDS
            for (j in 0..<pat.numberOfJugglers) {
                for (i in -1..1) {
                    g.drawLine(
                        leftX + i + j * jugglerDeltaX,
                        BORDER_TOP,
                        leftX + i + j * jugglerDeltaX,
                        ladderHeight - BORDER_TOP
                    )
                    g.drawLine(
                        rightX + i + j * jugglerDeltaX,
                        BORDER_TOP,
                        rightX + i + j * jugglerDeltaX,
                        ladderHeight - BORDER_TOP
                    )
                }
            }

            // draw paths
            val clip = g.clip

            for (item in ladderPathItems!!) {
                g.color = item.color

                if (item.type != LadderItem.TYPE_PASS) {
                    g.clipRect(
                        leftX + (item.startEvent!!.juggler - 1) * jugglerDeltaX,
                        BORDER_TOP,
                        rightX - leftX + (item.startEvent!!.juggler - 1) * jugglerDeltaX,
                        ladderHeight - 2 * BORDER_TOP
                    )
                }

                if (item.type == LadderItem.TYPE_CROSS) {
                    g.drawLine(item.xStart, item.yStart, item.xEnd, item.yEnd)
                } else if (item.type == LadderItem.TYPE_HOLD) {
                    g.drawLine(item.xStart, item.yStart, item.xEnd, item.yEnd)
                } else if (item.type == LadderItem.TYPE_PASS) {
                    val gdash = g.create() as Graphics2D
                    val dashed: Stroke =
                        BasicStroke(
                            2f,
                            BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_BEVEL,
                            1f,
                            floatArrayOf(7f, 3f),
                            0f
                        )
                    gdash.stroke = dashed
                    gdash.clipRect(leftX, BORDER_TOP, ladderWidth - leftX, ladderHeight - 2 * BORDER_TOP)

                    gdash.drawLine(item.xStart, item.yStart, item.xEnd, item.yEnd)
                    gdash.dispose()
                } else if (item.type == LadderItem.TYPE_SELF) {
                    if (item.yEnd >= BORDER_TOP) {
                        g.clipRect(
                            leftX + (item.startEvent!!.juggler - 1) * jugglerDeltaX,
                            item.yStart,
                            rightX - leftX + (item.startEvent!!.juggler - 1) * jugglerDeltaX,
                            item.yEnd - item.yStart
                        )
                        g.drawOval(
                            item.xCenter - item.radius,
                            item.yCenter - item.radius,
                            2 * item.radius,
                            2 * item.radius
                        )
                    }
                }
                g.clip = clip
            }
        }

        if (rebuildLadderImage) {
            imageValid = true
        }

        if (imageValid) {
            gr.drawImage(im, 0, 0, this)
        }

        // draw positions
        for (item in ladderPositionItems!!) {
            if (item.yLow >= BORDER_TOP || item.yHigh <= ladderHeight + BORDER_TOP) {
                gr.color = COLOR_BACKGROUND
                gr.fillRect(item.xLow, item.yLow, item.xHigh - item.xLow, item.yHigh - item.yLow)
                gr.color = COLOR_POSITIONS
                gr.drawRect(item.xLow, item.yLow, item.xHigh - item.xLow, item.yHigh - item.yLow)
            }
        }

        // draw events
        val animpropnum: IntArray? = ap?.animator?.animPropNum

        for (item in ladderEventItems!!) {
            if (item.type == LadderItem.TYPE_EVENT) {
                gr.color = COLOR_HANDS
                gr.fillOval(item.xlow, item.ylow, item.xhigh - item.xlow, item.yhigh - item.ylow)
            } else {
                if (item.ylow >= BORDER_TOP || item.yhigh <= ladderHeight + BORDER_TOP) {
                    if (animpropnum == null) {
                        gr.color = COLOR_BACKGROUND
                    } else {
                        // color ball representation with the prop's color
                        val tr = item.event!!.getTransition(item.transnum)
                        val propnum = animpropnum[tr.path - 1]
                        gr.color = pat.getProp(propnum).getEditorColor().toAwtColor()
                    }
                    gr.fillOval(
                        item.xlow,
                        item.ylow,
                        item.xhigh - item.xlow,
                        item.yhigh - item.ylow
                    )

                    gr.color = COLOR_HANDS
                    gr.drawOval(
                        item.xlow,
                        item.ylow,
                        item.xhigh - item.xlow,
                        item.yhigh - item.ylow
                    )
                }
            }
        }

        // draw the tracker line showing the time
        gr.color = COLOR_TRACKER
        gr.drawLine(0, trackerY, ladderWidth, trackerY)

        return true
    }

    //--------------------------------------------------------------------------
    // AnimationPanel.AnimationAttachment methods
    //--------------------------------------------------------------------------

    override fun setAnimationPanel(animPanel: AnimationPanel?) {
        ap = animPanel
    }

    override fun setTime(t: Double) {
        if (simTime == t) return
        simTime = t
        updateTrackerPosition()
        repaint()
    }

    override fun repaintAttachment() {
        repaint()
    }

    //--------------------------------------------------------------------------
    // javax.swing.JComponent methods
    //--------------------------------------------------------------------------

    override fun paintComponent(gr: Graphics) {
        if (gr is Graphics2D) {
            gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        paintLadder(gr)

        // label the tracker line with the time
        if (guiState == STATE_MOVING_TRACKER) {
            gr.color = COLOR_TRACKER
            gr.drawString(jlToStringRounded(simTime, 2) + " s", ladderWidth / 2 - 18, trackerY - 5)
        }
    }

    //--------------------------------------------------------------------------
    // Types for building the ladder diagram
    //--------------------------------------------------------------------------

    open class LadderItem {
        var type: Int = 0

        companion object {
            const val TYPE_EVENT: Int = 1
            const val TYPE_TRANSITION: Int = 2
            const val TYPE_SELF: Int = 3
            const val TYPE_CROSS: Int = 4
            const val TYPE_HOLD: Int = 5
            const val TYPE_PASS: Int = 6
            const val TYPE_POSITION: Int = 7
        }
    }

    class LadderEventItem : LadderItem() {
        var xlow: Int = 0
        var xhigh: Int = 0
        var ylow: Int = 0
        var yhigh: Int = 0
        // for transitions within an event, the next two point to the containing event:
        var eventitem: LadderEventItem? = null
        var event: JMLEvent? = null
        var transnum: Int = 0
        val hashCode: Int
            get() = event!!.hashCode * 17 + type * 23 + transnum * 27
    }

    class LadderPathItem : LadderItem() {
        var xStart: Int = 0
        var yStart: Int = 0
        var xEnd: Int = 0
        var yEnd: Int = 0
        var xCenter: Int = 0
        var yCenter: Int = 0
        var radius: Int = 0 // for type SELF
        var color: Color? = null
        var startEvent: JMLEvent? = null
        var endEvent: JMLEvent? = null
        var transnumStart: Int = 0
        var pathNum: Int = 0
    }

    class LadderPositionItem : LadderItem() {
        var xLow: Int = 0
        var xHigh: Int = 0
        var yLow: Int = 0
        var yHigh: Int = 0
        var position: JMLPosition? = null
        val hashCode: Int
            get() = position!!.hashCode
    }

    companion object {
        // overall sizing
        const val MAX_JUGGLERS: Int = 8
        const val LADDER_WIDTH_PER_JUGGLER: Int = 150 // pixels
        const val LADDER_MIN_WIDTH_PER_JUGGLER: Int = 60
        val MSGFONT: Font = Font("SansSerif", Font.PLAIN, 12)

        // geometric constants in pixels
        const val BORDER_TOP: Int = 25
        const val TRANSITION_RADIUS: Int = 5
        const val PATH_SLOP: Int = 5
        const val POSITION_RADIUS: Int = 5

        // geometric constants as fraction of hands separation for each juggler
        const val BORDER_SIDES: Double = 0.15
        const val JUGGLER_SEPARATION: Double = 0.45
        const val SELFTHROW_WIDTH: Double = 0.25

        val COLOR_BACKGROUND: Color? = Color.white
        val COLOR_HANDS: Color? = Color.black
        val COLOR_POSITIONS: Color? = Color.black
        val COLOR_SYMMETRIES: Color? = Color.lightGray
        val COLOR_TRACKER: Color? = Color.red
        const val IMAGE_DRAW_WAIT: Int = 5 // frames

        // GUI states
        const val STATE_INACTIVE: Int = 0
        const val STATE_MOVING_TRACKER: Int = 1
    }
}
