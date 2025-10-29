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

import jugglinglab.JugglingLab
import jugglinglab.core.AnimationPanel.AnimationAttachment
import jugglinglab.jml.*
import jugglinglab.util.toStringRounded
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
    @JvmField
    protected val pat: JMLPattern
    @JvmField
    protected var width: Int = 0 // pixel dimensions of entire panel
    @JvmField
    protected var height: Int = 0
    @JvmField
    protected var right_x: Int = 0 // right/left hand pos. for juggler 1 (px)
    @JvmField
    protected var left_x: Int = 0
    @JvmField
    protected var juggler_delta_x: Int = 0 // horizontal offset between jugglers (px)
    @JvmField
    protected var gui_state: Int = STATE_INACTIVE // one of STATE_x values above
    @JvmField
    protected var sim_time: Double = 0.0

    @JvmField
    protected var tracker_y: Int = BORDER_TOP
    protected var has_switch_symmetry: Boolean = false
    protected var has_switchdelay_symmetry: Boolean = false

    @JvmField
    protected var laddereventitems: ArrayList<LadderEventItem>? = null
    protected var ladderpathitems: ArrayList<LadderPathItem>? = null

    @JvmField
    protected var ladderpositionitems: ArrayList<LadderPositionItem>? = null

    protected var im: BufferedImage? = null
    protected var image_valid: Boolean = false
    protected var frames_until_image_draw: Int = 0

    @JvmField
    protected var anim_paused: Boolean = false

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
            setPreferredSize(Dimension(mwidth, 1))
            setMinimumSize(Dimension(mwidth, 1))
        } else {
            var pref_width: Int = LADDER_WIDTH_PER_JUGGLER * jugglers
            val min_width: Int = LADDER_MIN_WIDTH_PER_JUGGLER * jugglers
            val width_mult =
                doubleArrayOf(
                    1.0, 1.0, 0.85, 0.72, 0.65, 0.55,
                )
            pref_width = (pref_width.toDouble() *
                (if (jugglers >= width_mult.size) 0.5 else width_mult[jugglers])).toInt()
            pref_width = max(pref_width, min_width)
            setPreferredSize(Dimension(pref_width, 1))
            setMinimumSize(Dimension(min_width, 1))

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
        my = min(max(my, BORDER_TOP), height - BORDER_TOP)

        gui_state = STATE_MOVING_TRACKER
        tracker_y = my
        if (ap != null) {
            val scale = ((pat.loopEndTime - pat.loopStartTime)
                / (height - 2 * BORDER_TOP).toDouble())
            val newtime = (my - BORDER_TOP).toDouble() * scale
            anim_paused = ap!!.isPaused
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

        gui_state = STATE_INACTIVE
        if (ap != null) {
            ap!!.isPaused = anim_paused
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
        my = min(max(my, BORDER_TOP), height - BORDER_TOP)
        tracker_y = my
        repaint()

        if (ap != null) {
            val scale = ((pat.loopEndTime - pat.loopStartTime)
                / (height - 2 * BORDER_TOP).toDouble())
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
        for (item in laddereventitems!!) {
            if (x >= item.xlow && x <= item.xhigh && y >= item.ylow && y <= item.yhigh) {
                return item
            }
        }
        return null
    }

    protected fun getSelectedLadderPosition(x: Int, y: Int): LadderPositionItem? {
        for (item in ladderpositionitems!!) {
            if (x >= item.xlow && x <= item.xhigh && y >= item.ylow && y <= item.yhigh) {
                return item
            }
        }
        return null
    }

    protected fun getSelectedLadderPath(x: Int, y: Int, slop: Int): LadderPathItem? {
        var result: LadderPathItem? = null
        var dmin = 0.0

        if (y < (BORDER_TOP - slop) || y > (height - BORDER_TOP + slop)) {
            return null
        }

        for (item in ladderpathitems!!) {
            var d: Double

            if (item.type == LadderItem.Companion.TYPE_SELF) {
                if (y < (item.ystart - slop) || y > (item.yend + slop)) {
                    continue
                }
                d =
                    ((x - item.xcenter) * (x - item.xcenter) + (y - item.ycenter) * (y - item.ycenter)).toDouble()
                d = abs(sqrt(d) - item.radius)
            } else {
                val xmin = min(item.xstart, item.xend)
                val xmax = max(item.xstart, item.xend)

                if (x < (xmin - slop) || x > (xmax + slop)) {
                    continue
                }
                if (y < (item.ystart - slop) || y > (item.yend + slop)) {
                    continue
                }
                d = ((item.xend - item.xstart) * (y - item.ystart)
                    - (x - item.xstart) * (item.yend - item.ystart)).toDouble()
                d = abs(d) / sqrt(
                    ((item.xend - item.xstart) * (item.xend - item.xstart)
                        + (item.yend - item.ystart) * (item.yend - item.ystart)).toDouble()
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
        val loop_start = pat.loopStartTime
        val loop_end = pat.loopEndTime
        tracker_y =
            (0.5 + (height - 2 * BORDER_TOP).toDouble() * (sim_time - loop_start) / (loop_end - loop_start)).toInt() + BORDER_TOP
    }

    //----------------------------------------------------------------------------
    // Methods to create and paint the ladder view
    //----------------------------------------------------------------------------
    // Create arrays of all the elements in the ladder diagram.
    protected fun createView() {
        has_switchdelay_symmetry = false
        has_switch_symmetry = has_switchdelay_symmetry

        for (sym in pat.symmetries) {
            when (sym.getType()) {
                JMLSymmetry.TYPE_SWITCH -> has_switch_symmetry = true
                JMLSymmetry.TYPE_SWITCHDELAY -> has_switchdelay_symmetry = true
            }
        }

        val loop_start = pat.loopStartTime
        val loop_end = pat.loopEndTime

        // first create events (black circles on the vertical lines representing hands)
        laddereventitems = ArrayList<LadderEventItem>()
        val eventlist = pat.eventList
        var ev = eventlist

        while (ev!!.t < loop_start) {
            ev = ev.next
        }

        while (ev!!.t < loop_end) {
            val item = LadderEventItem()
            item.type = LadderItem.Companion.TYPE_EVENT
            item.eventitem = item
            item.event = ev
            laddereventitems!!.add(item)

            for (i in 0..<ev.numberOfTransitions) {
                val item2 = LadderEventItem()
                item2.type = LadderItem.Companion.TYPE_TRANSITION
                item2.eventitem = item
                item2.event = ev
                item2.transnum = i
                laddereventitems!!.add(item2)
            }

            ev = ev.next
        }

        // create paths (lines and arcs)
        ladderpathitems = ArrayList<LadderPathItem>()
        ev = eventlist
        while (ev!!.t <= loop_end) {
            for (i in 0..<ev.numberOfTransitions) {
                val tr = ev.getTransition(i)
                val opl = tr.outgoingPathLink

                if (opl != null) {
                    val item = LadderPathItem()
                    item.transnum_start = i
                    item.startevent = opl.startEvent
                    item.endevent = opl.endEvent

                    if (opl.isInHand) {
                        item.type = LadderItem.Companion.TYPE_HOLD
                    } else if (item.startevent!!.juggler != item.endevent!!.juggler) {
                        item.type = LadderItem.Companion.TYPE_PASS
                    } else {
                        item.type = if (item.startevent!!.hand == item.endevent!!.hand)
                            LadderItem.Companion.TYPE_SELF
                        else
                            LadderItem.Companion.TYPE_CROSS
                    }

                    item.pathnum = opl.pathNum
                    item.color = Color.black
                    ladderpathitems!!.add(item)
                }
            }

            ev = ev.next
        }

        // create juggler positions
        ladderpositionitems = ArrayList<LadderPositionItem>()
        var pos = pat.positionList

        while (pos != null && pos.t < loop_start) {
            pos = pos.next
        }

        while (pos != null && pos.t < loop_end) {
            val item = LadderPositionItem()
            item.type = LadderItem.Companion.TYPE_POSITION
            item.position = pos
            ladderpositionitems!!.add(item)

            pos = pos.next
        }

        updateView()
    }

    // Assign physical locations to all the elements in the ladder diagram.
    protected fun updateView() {
        val dim = getSize()
        width = dim.width
        height = dim.height

        // calculate placements of hands and jugglers
        val scale: Double =
            width.toDouble() / (BORDER_SIDES * 2 + JUGGLER_SEPARATION * (pat.numberOfJugglers - 1) + pat.numberOfJugglers)
        left_x = (scale * BORDER_SIDES + 0.5).toInt()
        right_x = (scale * (BORDER_SIDES + 1.0) + 0.5).toInt()
        juggler_delta_x = (scale * (1.0 + JUGGLER_SEPARATION) + 0.5).toInt()

        // invalidate cached image of ladder diagram
        image_valid = false
        im = null
        frames_until_image_draw = IMAGE_DRAW_WAIT

        val loop_start = pat.loopStartTime
        val loop_end = pat.loopEndTime

        // set locations of events and transitions
        for (item in laddereventitems!!) {
            val ev = item.event

            var event_x: Int = ((if (ev!!.hand == HandLink.LEFT_HAND) left_x else right_x)
                + (ev.juggler - 1) * juggler_delta_x
                - TRANSITION_RADIUS)
            val event_y: Int =
                ((0.5 + (height - 2 * BORDER_TOP).toDouble() * (ev.t - loop_start) / (loop_end - loop_start)).toInt() + BORDER_TOP
                    - TRANSITION_RADIUS)

            if (item.type != LadderItem.Companion.TYPE_EVENT) {
                if (ev.hand == HandLink.LEFT_HAND) {
                    event_x += 2 * TRANSITION_RADIUS * (item.transnum + 1)
                } else {
                    event_x -= 2 * TRANSITION_RADIUS * (item.transnum + 1)
                }
            }
            item.xlow = event_x
            item.xhigh = event_x + 2 * TRANSITION_RADIUS
            item.ylow = event_y
            item.yhigh = event_y + 2 * TRANSITION_RADIUS
        }

        // set locations of paths (lines and arcs)
        for (item in ladderpathitems!!) {
            item.xstart =
                ((if (item.startevent!!.hand == HandLink.LEFT_HAND)
                    (left_x + (item.transnum_start + 1) * 2 * TRANSITION_RADIUS)
                else
                    (right_x - (item.transnum_start + 1) * 2 * TRANSITION_RADIUS))
                    + (item.startevent!!.juggler - 1) * juggler_delta_x)
            item.ystart =
                (0.5 + (height - 2 * BORDER_TOP).toDouble() * (item.startevent!!.t - loop_start) / (loop_end - loop_start)).toInt() + BORDER_TOP
            item.yend =
                (0.5 + (height - 2 * BORDER_TOP).toDouble() * (item.endevent!!.t - loop_start) / (loop_end - loop_start)).toInt() + BORDER_TOP

            var slot = 0
            for (j in 0..<item.endevent!!.numberOfTransitions) {
                val temp = item.endevent!!.getTransition(j)
                if (temp.path == item.pathnum) {
                    slot = j
                    break
                }
            }
            item.xend = ((if (item.endevent!!.hand == HandLink.LEFT_HAND)
                (left_x + (slot + 1) * 2 * TRANSITION_RADIUS)
            else
                (right_x - (slot + 1) * 2 * TRANSITION_RADIUS))
                + (item.endevent!!.juggler - 1) * juggler_delta_x)

            if (item.type == LadderItem.Companion.TYPE_SELF) {
                val a = 0.5 * sqrt(
                    ((item.xstart - item.xend) * (item.xstart - item.xend)).toDouble() + ((item.ystart - item.yend) * (item.ystart - item.yend)).toDouble()
                )
                val xt = 0.5 * (item.xstart + item.xend).toDouble()
                val yt = 0.5 * (item.ystart + item.yend).toDouble()
                val b: Double = SELFTHROW_WIDTH * (width.toDouble() / pat.numberOfJugglers)
                var d = 0.5 * (a * a / b - b)
                if (d < (0.5 * b)) {
                    d = 0.5 * b
                }
                val mult = if (item.endevent!!.hand == HandLink.LEFT_HAND) -1.0 else 1.0
                val xc = xt + mult * d * (yt - item.ystart.toDouble()) / a
                val yc = yt + mult * d * (item.xstart.toDouble() - xt) / a
                val rad = sqrt(
                    (item.xstart.toDouble() - xc) * (item.xstart.toDouble() - xc)
                        + (item.ystart.toDouble() - yc) * (item.ystart.toDouble() - yc)
                )
                item.xcenter = (0.5 + xc).toInt()
                item.ycenter = (0.5 + yc).toInt()
                item.radius = (0.5 + rad).toInt()
            }
        }

        // set locations of juggler positions
        for (item in ladderpositionitems!!) {
            val pos = item.position

            val position_x: Int =
                (left_x + right_x) / 2 + (pos!!.juggler - 1) * juggler_delta_x - POSITION_RADIUS
            val position_y: Int =
                ((0.5 + (height - 2 * BORDER_TOP).toDouble() * (pos.t - loop_start) / (loop_end - loop_start)).toInt() + BORDER_TOP
                    - POSITION_RADIUS)

            item.xlow = position_x
            item.xhigh = position_x + 2 * POSITION_RADIUS
            item.ylow = position_y
            item.yhigh = position_y + 2 * POSITION_RADIUS
        }

        // update position of tracker bar
        updateTrackerPosition()
    }

    // Return true if ladder was drawn successfully, false otherwise.
    protected fun paintLadder(gr: Graphics): Boolean {
        if (pat.numberOfJugglers > MAX_JUGGLERS) {
            val dim = getSize()
            gr.setFont(MSGFONT)
            val fm = gr.getFontMetrics()
            val template: String = guistrings.getString("Too_many_jugglers")
            val arguments = arrayOf<Any?>(MAX_JUGGLERS)
            val message = MessageFormat.format(template, *arguments)
            val mwidth = fm.stringWidth(message)
            val x = max((dim.width - mwidth) / 2, 0)
            val y = (dim.height + fm.getHeight()) / 2
            gr.setColor(COLOR_BACKGROUND)
            gr.fillRect(0, 0, dim.width, dim.height)
            gr.setColor(Color.black)
            gr.drawString(message, x, y)
            return false
        }

        var g = gr

        // check if ladder was resized
        val dim = getSize()
        if (dim.width != width || dim.height != height) {
            updateView()
        }

        val rebuild_ladder_image = (!image_valid && --frames_until_image_draw <= 0)

        if (rebuild_ladder_image) {
            im = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .createCompatibleImage(width, height, Transparency.OPAQUE)
            g = im!!.getGraphics()

            if (g is Graphics2D) {
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
            }
        }

        if (!image_valid) {
            // first erase the background
            g.setColor(COLOR_BACKGROUND)
            g.fillRect(0, 0, width, height)

            // draw the lines signifying symmetries
            g.setColor(COLOR_SYMMETRIES)
            g.drawLine(0, BORDER_TOP, width, BORDER_TOP)
            g.drawLine(0, height - BORDER_TOP, width, height - BORDER_TOP)
            if (has_switch_symmetry) {
                g.drawLine(left_x, height - BORDER_TOP / 2, width - left_x, height - BORDER_TOP / 2)
                g.drawLine(
                    left_x,
                    height - BORDER_TOP / 2,
                    left_x + left_x,
                    height - BORDER_TOP * 3 / 4
                )
                g.drawLine(
                    left_x,
                    height - BORDER_TOP / 2,
                    left_x + left_x,
                    height - BORDER_TOP / 4
                )
                g.drawLine(
                    width - left_x, height - BORDER_TOP / 2,
                    width - 2 * left_x, height - BORDER_TOP * 3 / 4
                )
                g.drawLine(
                    width - left_x, height - BORDER_TOP / 2,
                    width - 2 * left_x, height - BORDER_TOP / 4
                )
            }
            if (has_switchdelay_symmetry) {
                g.drawLine(0, height / 2, width, height / 2)
            }

            // draw the lines representing the hands
            g.setColor(COLOR_HANDS)
            for (j in 0..<pat.numberOfJugglers) {
                for (i in -1..1) {
                    g.drawLine(
                        left_x + i + j * juggler_delta_x,
                        BORDER_TOP,
                        left_x + i + j * juggler_delta_x,
                        height - BORDER_TOP
                    )
                    g.drawLine(
                        right_x + i + j * juggler_delta_x,
                        BORDER_TOP,
                        right_x + i + j * juggler_delta_x,
                        height - BORDER_TOP
                    )
                }
            }

            // draw paths
            val clip = g.getClip()

            for (item in ladderpathitems!!) {
                g.setColor(item.color)

                if (item.type != LadderItem.Companion.TYPE_PASS) {
                    g.clipRect(
                        left_x + (item.startevent!!.juggler - 1) * juggler_delta_x,
                        BORDER_TOP,
                        right_x - left_x + (item.startevent!!.juggler - 1) * juggler_delta_x,
                        height - 2 * BORDER_TOP
                    )
                }

                if (item.type == LadderItem.Companion.TYPE_CROSS) {
                    g.drawLine(item.xstart, item.ystart, item.xend, item.yend)
                } else if (item.type == LadderItem.Companion.TYPE_HOLD) {
                    g.drawLine(item.xstart, item.ystart, item.xend, item.yend)
                } else if (item.type == LadderItem.Companion.TYPE_PASS) {
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
                    gdash.setStroke(dashed)
                    gdash.clipRect(left_x, BORDER_TOP, width - left_x, height - 2 * BORDER_TOP)

                    gdash.drawLine(item.xstart, item.ystart, item.xend, item.yend)
                    gdash.dispose()
                } else if (item.type == LadderItem.Companion.TYPE_SELF) {
                    if (item.yend >= BORDER_TOP) {
                        g.clipRect(
                            left_x + (item.startevent!!.juggler - 1) * juggler_delta_x,
                            item.ystart,
                            right_x - left_x + (item.startevent!!.juggler - 1) * juggler_delta_x,
                            item.yend - item.ystart
                        )
                        g.drawOval(
                            item.xcenter - item.radius,
                            item.ycenter - item.radius,
                            2 * item.radius,
                            2 * item.radius
                        )
                    }
                }
                g.setClip(clip)
            }
        }

        if (rebuild_ladder_image) {
            image_valid = true
        }

        if (image_valid) {
            gr.drawImage(im, 0, 0, this)
        }

        // draw positions
        for (item in ladderpositionitems!!) {
            if (item.ylow >= BORDER_TOP || item.yhigh <= height + BORDER_TOP) {
                gr.setColor(COLOR_BACKGROUND)
                gr.fillRect(item.xlow, item.ylow, item.xhigh - item.xlow, item.yhigh - item.ylow)
                gr.setColor(COLOR_POSITIONS)
                gr.drawRect(item.xlow, item.ylow, item.xhigh - item.xlow, item.yhigh - item.ylow)
            }
        }

        // draw events
        var animpropnum: IntArray? = null
        if (ap != null && ap!!.animator != null) {
            animpropnum = ap!!.animator.animPropNum
        }

        for (item in laddereventitems!!) {
            if (item.type == LadderItem.Companion.TYPE_EVENT) {
                gr.setColor(COLOR_HANDS)
                gr.fillOval(item.xlow, item.ylow, item.xhigh - item.xlow, item.yhigh - item.ylow)
            } else {
                if (item.ylow >= BORDER_TOP || item.yhigh <= height + BORDER_TOP) {
                    if (animpropnum == null) {
                        gr.setColor(COLOR_BACKGROUND)
                    } else {
                        // color ball representation with the prop's color
                        val tr = item.event!!.getTransition(item.transnum)
                        val propnum = animpropnum[tr.path - 1]
                        gr.setColor(pat.getProp(propnum)!!.getEditorColor())
                    }
                    gr.fillOval(
                        item.xlow,
                        item.ylow,
                        item.xhigh - item.xlow,
                        item.yhigh - item.ylow
                    )

                    gr.setColor(COLOR_HANDS)
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
        gr.setColor(COLOR_TRACKER)
        gr.drawLine(0, tracker_y, width, tracker_y)

        return true
    }

    //----------------------------------------------------------------------------
    // AnimationPanel.AnimationAttachment methods
    //----------------------------------------------------------------------------
    override fun setAnimationPanel(a: AnimationPanel?) {
        ap = a
    }

    override fun setTime(time: Double) {
        if (sim_time == time) {
            return
        }

        sim_time = time
        updateTrackerPosition()
        repaint()
    }

    override fun repaintAttachment() {
        repaint()
    }

    //----------------------------------------------------------------------------
    // javax.swing.JComponent methods
    //----------------------------------------------------------------------------
    override fun paintComponent(gr: Graphics) {
        if (gr is Graphics2D) {
            gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        paintLadder(gr)

        // label the tracker line with the time
        if (gui_state == STATE_MOVING_TRACKER) {
            gr.setColor(COLOR_TRACKER)
            gr.drawString(toStringRounded(sim_time, 2) + " s", width / 2 - 18, tracker_y - 5)
        }
    }

    //----------------------------------------------------------------------------
    // Types for building the ladder diagram
    //----------------------------------------------------------------------------
    open class LadderItem {
        @JvmField
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
        @JvmField
        var xlow: Int = 0

        @JvmField
        var xhigh: Int = 0

        @JvmField
        var ylow: Int = 0

        @JvmField
        var yhigh: Int = 0

        // for transitions within an event, the next two point to the containing event:
        @JvmField
        var eventitem: LadderEventItem? = null

        @JvmField
        var event: JMLEvent? = null

        @JvmField
        var transnum: Int = 0

        val hashCode: Int
            get() = event!!.hashCode * 17 + type * 23 + transnum * 27
    }

    class LadderPathItem : LadderItem() {
        var xstart: Int = 0
        var ystart: Int = 0
        var xend: Int = 0
        var yend: Int = 0
        var xcenter: Int = 0
        var ycenter: Int = 0
        var radius: Int = 0 // for type SELF
        var color: Color? = null

        var startevent: JMLEvent? = null
        var endevent: JMLEvent? = null
        var transnum_start: Int = 0

        @JvmField
        var pathnum: Int = 0
    }

    class LadderPositionItem : LadderItem() {
        @JvmField
        var xlow: Int = 0

        @JvmField
        var xhigh: Int = 0

        @JvmField
        var ylow: Int = 0

        @JvmField
        var yhigh: Int = 0

        @JvmField
        var position: JMLPosition? = null

        val hashCode: Int
            get() = position!!.hashCode
    }

    companion object {
        @JvmField
        val guistrings: ResourceBundle = JugglingLab.guistrings

        @JvmField
        val errorstrings: ResourceBundle? = JugglingLab.errorstrings

        // overall sizing
        const val MAX_JUGGLERS: Int = 8
        protected const val LADDER_WIDTH_PER_JUGGLER: Int = 150 // pixels
        protected const val LADDER_MIN_WIDTH_PER_JUGGLER: Int = 60
        protected val MSGFONT: Font = Font("SansSerif", Font.PLAIN, 12)

        // geometric constants in pixels
        protected const val BORDER_TOP: Int = 25
        protected const val TRANSITION_RADIUS: Int = 5
        protected const val PATH_SLOP: Int = 5
        protected const val POSITION_RADIUS: Int = 5

        // geometric constants as fraction of hands separation for each juggler
        protected const val BORDER_SIDES: Double = 0.15
        protected const val JUGGLER_SEPARATION: Double = 0.45
        protected const val SELFTHROW_WIDTH: Double = 0.25

        protected val COLOR_BACKGROUND: Color? = Color.white
        protected val COLOR_HANDS: Color? = Color.black
        protected val COLOR_POSITIONS: Color? = Color.black
        protected val COLOR_SYMMETRIES: Color? = Color.lightGray

        @JvmField
        protected val COLOR_TRACKER: Color? = Color.red
        protected const val IMAGE_DRAW_WAIT: Int = 5 // frames

        // GUI states
        protected const val STATE_INACTIVE: Int = 0
        protected const val STATE_MOVING_TRACKER: Int = 1
    }
}
