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

import androidx.compose.ui.graphics.toAwtImage
import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.AnimationPanel.AnimationAttachment
import jugglinglab.jml.*
import jugglinglab.path.Path
import jugglinglab.path.Path.Companion.newPath
import jugglinglab.prop.Prop
import jugglinglab.prop.Prop.Companion.newProp
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterDescriptor
import jugglinglab.util.jlConstraints
import jugglinglab.util.jlGetImageResource
import jugglinglab.util.jlGetStringResource
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.util.jlJfc
import jugglinglab.util.jlParseFiniteDouble
import jugglinglab.util.jlToStringRounded
import jugglinglab.util.toAwtColor
import jugglinglab.view.View
import org.jetbrains.compose.resources.StringResource
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.image.BufferedImage
import java.net.MalformedURLException
import java.util.Locale
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextField
import javax.swing.border.BevelBorder
import javax.swing.event.CaretEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class LadderDiagram(
     p: JMLPattern,
     val parentFrame: JFrame?,
     val parentView: View
) : JPanel(), AnimationAttachment, MouseListener, MouseMotionListener, ActionListener {
    private var ladderWidth: Int = 0 // pixel dimensions of entire panel
    private var ladderHeight: Int = 0
    private var rightX: Int = 0 // right/left hand pos. for juggler 1 (px)
    private var leftX: Int = 0
    private var jugglerDeltaX: Int = 0 // horizontal offset between jugglers (px)
    private var guiState: Int = STATE_INACTIVE // one of STATE_x values above
    private var simTime: Double = 0.0
    private var trackerY: Int = BORDER_TOP
    private var hasSwitchSymmetry: Boolean = false
    private var hasSwitchDelaySymmetry: Boolean = false

    private var ladderEventItems: List<LadderEventItem> = emptyList()
    private var ladderPathItems: List<LadderPathItem> = emptyList()
    private var ladderPositionItems: List<LadderPositionItem> = emptyList()

    private var im: BufferedImage? = null
    private var imageValid: Boolean = false
    private var framesUntilImageDraw: Int = 0

    private var animPaused: Boolean = false

    //---------------------

    private var aep: AnimationEditPanel? = null

    private var activeItemHashCode: Int = 0
    private var activeEventItem: LadderEventItem? = null
    private var activePositionItem: LadderPositionItem? = null
    private var itemWasSelected: Boolean = false // for detecting de-selecting clicks

    private var startY: Int = 0
    private var startYLow: Int = 0
    private var startYHigh: Int = 0 // initial box y-coordinates
    private var startT: Double = 0.0 // initial time
    private var deltaY: Int = 0
    private var deltaYMin: Int = 0
    private var deltaYMax: Int = 0 // limits for dragging up/down

    private var popupItem: LadderItem? = null
    private var popupX: Int = 0 // screen coordinates where popup was raised
    private var popupY: Int = 0

    private var dialogControls: MutableList<JComponent>? = null
    private var dialogPd: List<ParameterDescriptor> = emptyList()

    var pattern: JMLPattern = p
        private set(pat) {
            removeMouseListener(this)
            removeMouseMotionListener(this)
            field = pat

            val jugglers = pat.numberOfJugglers
            if (jugglers > MAX_JUGGLERS) {
                // allocate enough space for a "too many jugglers" message; see paintLadder()
                val message = jlGetStringResource(Res.string.gui_too_many_jugglers, MAX_JUGGLERS)
                val mwidth = 20 + getFontMetrics(MSGFONT).stringWidth(message)
                preferredSize = Dimension(mwidth, 1)
                minimumSize = Dimension(mwidth, 1)
            } else {
                var prefWidth: Int = LADDER_WIDTH_PER_JUGGLER * jugglers
                val minWidth: Int = LADDER_MIN_WIDTH_PER_JUGGLER * jugglers
                val widthMult = doubleArrayOf(1.0, 1.0, 0.85, 0.72, 0.65, 0.55)
                prefWidth = (prefWidth.toDouble() *
                    (if (jugglers >= widthMult.size) 0.5 else widthMult[jugglers])).toInt()
                prefWidth = max(prefWidth, minWidth)
                preferredSize = Dimension(prefWidth, 1)
                minimumSize = Dimension(minWidth, 1)

                createView()
            }
        }

    init {
        setBackground(COLOR_BACKGROUND)
        setOpaque(false)
        setJMLPattern(p)
    }

    //--------------------------------------------------------------------------
    // Methods to interact with ladder items
    //--------------------------------------------------------------------------

    private fun getSelectedLadderEvent(x: Int, y: Int): LadderEventItem? {
        for (item in ladderEventItems) {
            if (x >= item.xLow && x <= item.xHigh && y >= item.yLow && y <= item.yHigh) {
                return item
            }
        }
        return null
    }

    private fun getSelectedLadderPosition(x: Int, y: Int): LadderPositionItem? {
        for (item in ladderPositionItems) {
            if (x >= item.xLow && x <= item.xHigh && y >= item.yLow && y <= item.yHigh) {
                return item
            }
        }
        return null
    }

    @Suppress("SameParameterValue")
    private fun getSelectedLadderPath(x: Int, y: Int, slop: Int): LadderPathItem? {
        var result: LadderPathItem? = null
        var dmin = 0.0

        if (y < (BORDER_TOP - slop) || y > (ladderHeight - BORDER_TOP + slop)) {
            return null
        }

        for (item in ladderPathItems) {
            var d: Double

            if (item.type == LadderItem.TYPE_SELF) {
                if (y < (item.yStart - slop) || y > (item.yEnd + slop)) {
                    continue
                }
                d = ((x - item.xCenter) * (x - item.xCenter) +
                    (y - item.yCenter) * (y - item.yCenter)).toDouble()
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

    private fun updateTrackerPosition() {
        val loopStart = pattern.loopStartTime
        val loopEnd = pattern.loopEndTime
        trackerY = (
            0.5 +
                (ladderHeight - 2 * BORDER_TOP).toDouble() *
                (simTime - loopStart) / (loopEnd - loopStart)
            ).toInt() + BORDER_TOP
    }

    //--------------------------------------------------------------------------
    // Methods to create and paint the ladder view
    //--------------------------------------------------------------------------

    // Create lists of all the elements in the ladder diagram.

    private fun createView() {
        hasSwitchDelaySymmetry = pattern.symmetries.any { it.type == JMLSymmetry.TYPE_SWITCHDELAY }
        hasSwitchSymmetry = pattern.symmetries.any { it.type == JMLSymmetry.TYPE_SWITCH }

        val loopStart = pattern.loopStartTime
        val loopEnd = pattern.loopEndTime

        // create events (black circles on the vertical lines representing hands)
        ladderEventItems = buildList {
            for (ei in pattern.loopEvents) {
                val item = LadderEventItem(
                    event = ei.event,
                    primary = ei.primary
                )
                item.type = LadderItem.TYPE_EVENT
                item.transEventItem = item
                add(item)

                for (index in ei.event.transitions.indices) {
                    add(
                        LadderEventItem(
                            event = ei.event,
                            primary = ei.primary
                        ).apply {
                            type = LadderItem.TYPE_TRANSITION
                            transEventItem = item
                            transNum = index
                        })
                }
            }
        }

        // create paths (lines and arcs)
        ladderPathItems = buildList {
            for ((indexEv, ei) in pattern.allEvents.withIndex()) {
                for ((indexTr, tr) in ei.event.transitions.withIndex()) {
                    // add item that starts on event `ei.event`, transition `tr`

                    val endEi = pattern.allEvents
                        .asSequence()
                        .drop(indexEv + 1)
                        .firstOrNull { it.event.transitions.any { tr2 -> tr2.path == tr.path } }
                        ?: continue

                    add(
                        LadderPathItem(
                            startEvent = ei.event,
                            endEvent = endEi.event,
                        ).apply {
                            transnumStart = indexTr
                            type = if (tr.type != JMLTransition.TRANS_THROW) {
                                LadderItem.TYPE_HOLD
                            } else if (ei.event.juggler != endEvent.juggler) {
                                LadderItem.TYPE_PASS
                            } else if (ei.event.hand == endEvent.hand) {
                                LadderItem.TYPE_SELF
                            } else {
                                LadderItem.TYPE_CROSS
                            }
                            pathNum = tr.path
                            color = Color.black
                        })
                }
            }
        }

        // create juggler positions
        ladderPositionItems = buildList {
            for (pos in pattern.positions) {
                if (pos.t in loopStart..<loopEnd) {
                    val item = LadderPositionItem(pos)
                    item.type = LadderItem.TYPE_POSITION
                    add(item)
                }
            }
        }

        updateView()
    }

    // Assign screen locations to all the elements in the ladder diagram.

    private fun updateView() {
        ladderWidth = size.width
        ladderHeight = size.height

        // calculate placements of hands and jugglers
        val scale: Double =
            ladderWidth.toDouble() / (BORDER_SIDES * 2 +
                JUGGLER_SEPARATION * (pattern.numberOfJugglers - 1) +
                pattern.numberOfJugglers)
        leftX = (scale * BORDER_SIDES + 0.5).toInt()
        rightX = (scale * (BORDER_SIDES + 1.0) + 0.5).toInt()
        jugglerDeltaX = (scale * (1.0 + JUGGLER_SEPARATION) + 0.5).toInt()

        // invalidate cached image of ladder diagram
        imageValid = false
        im = null
        framesUntilImageDraw = IMAGE_DRAW_WAIT

        val loopStart = pattern.loopStartTime
        val loopEnd = pattern.loopEndTime

        // set locations of events and transitions
        for (item in ladderEventItems) {
            val ev = item.event

            var eventX: Int =
                ((if (ev.hand == HandLink.LEFT_HAND) leftX else rightX)
                    + (ev.juggler - 1) * jugglerDeltaX
                    - TRANSITION_RADIUS)
            val eventY: Int =
                ((0.5 + (ladderHeight - 2 * BORDER_TOP).toDouble() *
                    (ev.t - loopStart) / (loopEnd - loopStart)).toInt() +
                    BORDER_TOP - TRANSITION_RADIUS)

            if (item.type != LadderItem.TYPE_EVENT) {
                if (ev.hand == HandLink.LEFT_HAND) {
                    eventX += 2 * TRANSITION_RADIUS * (item.transNum + 1)
                } else {
                    eventX -= 2 * TRANSITION_RADIUS * (item.transNum + 1)
                }
            }
            item.xLow = eventX
            item.xHigh = eventX + 2 * TRANSITION_RADIUS
            item.yLow = eventY
            item.yHigh = eventY + 2 * TRANSITION_RADIUS
        }

        // set locations of paths (lines and arcs)
        for (item in ladderPathItems) {
            item.xStart =
                ((if (item.startEvent.hand == HandLink.LEFT_HAND)
                    (leftX + (item.transnumStart + 1) * 2 * TRANSITION_RADIUS)
                else
                    (rightX - (item.transnumStart + 1) * 2 * TRANSITION_RADIUS))
                    + (item.startEvent.juggler - 1) * jugglerDeltaX)
            item.yStart =
                (0.5 + (ladderHeight - 2 * BORDER_TOP).toDouble() * (item.startEvent.t - loopStart) /
                    (loopEnd - loopStart)).toInt() + BORDER_TOP
            item.yEnd =
                (0.5 + (ladderHeight - 2 * BORDER_TOP).toDouble() * (item.endEvent.t - loopStart) /
                    (loopEnd - loopStart)).toInt() + BORDER_TOP

            val slot = item.endEvent.transitions.indexOfFirst { it.path == item.pathNum }
            if (slot == -1) continue // Should not happen in a valid pattern

            item.xEnd = ((if (item.endEvent.hand == HandLink.LEFT_HAND)
                (leftX + (slot + 1) * 2 * TRANSITION_RADIUS)
            else
                (rightX - (slot + 1) * 2 * TRANSITION_RADIUS))
                + (item.endEvent.juggler - 1) * jugglerDeltaX)

            if (item.type == LadderItem.TYPE_SELF) {
                val a = 0.5 * sqrt(
                    ((item.xStart - item.xEnd) * (item.xStart - item.xEnd)).toDouble() +
                        ((item.yStart - item.yEnd) * (item.yStart - item.yEnd)).toDouble()
                )
                val xt = 0.5 * (item.xStart + item.xEnd).toDouble()
                val yt = 0.5 * (item.yStart + item.yEnd).toDouble()
                val b: Double =
                    SELFTHROW_WIDTH * (ladderWidth.toDouble() / pattern.numberOfJugglers)
                var d = 0.5 * (a * a / b - b)
                if (d < (0.5 * b)) {
                    d = 0.5 * b
                }
                val mult = if (item.endEvent.hand == HandLink.LEFT_HAND) -1.0 else 1.0
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
        for (item in ladderPositionItems) {
            val pos = item.position
            val positionX: Int =
                (leftX + rightX) / 2 + (pos.juggler - 1) * jugglerDeltaX - POSITION_RADIUS
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

    // Paint the latter on the screen.
    //
    // Return true if ladder was drawn successfully, false otherwise.

    private fun paintLadder(gr: Graphics): Boolean {
        if (pattern.numberOfJugglers > MAX_JUGGLERS) {
            val dim = size
            gr.font = MSGFONT
            val fm = gr.fontMetrics
            val message = jlGetStringResource(Res.string.gui_too_many_jugglers, MAX_JUGGLERS)
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

        // TODO: remove image caching?

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
                g.drawLine(
                    leftX, ladderHeight - BORDER_TOP / 2,
                    ladderWidth - leftX, ladderHeight - BORDER_TOP / 2
                )
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
            if (hasSwitchDelaySymmetry) {
                g.drawLine(0, ladderHeight / 2, ladderWidth, ladderHeight / 2)
            }

            // draw the lines representing the hands
            g.color = COLOR_HANDS
            for (j in 0..<pattern.numberOfJugglers) {
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

            for (item in ladderPathItems) {
                g.color = item.color

                if (item.type != LadderItem.TYPE_PASS) {
                    g.clipRect(
                        leftX + (item.startEvent.juggler - 1) * jugglerDeltaX,
                        BORDER_TOP,
                        rightX - leftX + (item.startEvent.juggler - 1) * jugglerDeltaX,
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
                    gdash.clipRect(
                        leftX,
                        BORDER_TOP,
                        ladderWidth - leftX,
                        ladderHeight - 2 * BORDER_TOP
                    )

                    gdash.drawLine(item.xStart, item.yStart, item.xEnd, item.yEnd)
                    gdash.dispose()
                } else if (item.type == LadderItem.TYPE_SELF) {
                    if (item.yEnd >= BORDER_TOP) {
                        g.clipRect(
                            leftX + (item.startEvent.juggler - 1) * jugglerDeltaX,
                            item.yStart,
                            rightX - leftX + (item.startEvent.juggler - 1) * jugglerDeltaX,
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
        for (item in ladderPositionItems) {
            if (item.yLow >= BORDER_TOP || item.yHigh <= ladderHeight + BORDER_TOP) {
                gr.color = COLOR_BACKGROUND
                gr.fillRect(item.xLow, item.yLow, item.xHigh - item.xLow, item.yHigh - item.yLow)
                gr.color = COLOR_POSITIONS
                gr.drawRect(item.xLow, item.yLow, item.xHigh - item.xLow, item.yHigh - item.yLow)
            }
        }

        // draw events
        val animpropnum: IntArray? = aep?.animator?.animPropNum

        for (item in ladderEventItems) {
            if (item.type == LadderItem.TYPE_EVENT) {
                gr.color = COLOR_HANDS
                gr.fillOval(item.xLow, item.yLow, item.xHigh - item.xLow, item.yHigh - item.yLow)
            } else {
                if (item.yLow >= BORDER_TOP || item.yHigh <= ladderHeight + BORDER_TOP) {
                    if (animpropnum == null) {
                        gr.color = COLOR_BACKGROUND
                    } else {
                        // color ball representation with the prop's color
                        val tr = item.event.transitions[item.transNum]
                        val propnum = animpropnum[tr.path - 1]
                        gr.color = pattern.getProp(propnum).getEditorColor().toAwtColor()
                    }
                    gr.fillOval(
                        item.xLow,
                        item.yLow,
                        item.xHigh - item.xLow,
                        item.yHigh - item.yLow
                    )

                    gr.color = COLOR_HANDS
                    gr.drawOval(
                        item.xLow,
                        item.yLow,
                        item.xHigh - item.xLow,
                        item.yHigh - item.yLow
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
    // Types for building the ladder diagram
    //--------------------------------------------------------------------------

    open class LadderItem {
        var type: Int = 0

        open val jlHashCode: Int = 0

        companion object {
            const val TYPE_EVENT: Int = 0
            const val TYPE_TRANSITION: Int = 1
            const val TYPE_SELF: Int = 2
            const val TYPE_CROSS: Int = 3
            const val TYPE_HOLD: Int = 4
            const val TYPE_PASS: Int = 5
            const val TYPE_POSITION: Int = 6
        }
    }

    class LadderEventItem(
        val event: JMLEvent,
        val primary: JMLEvent
    ) : LadderItem() {
        // screen bounding box of the event circle
        var xLow: Int = 0
        var xHigh: Int = 0
        var yLow: Int = 0
        var yHigh: Int = 0

        // for transitions within an event, the next two refer to the containing event:
        var transEventItem: LadderEventItem? = null
        var transNum: Int = 0

        // note the jlHashCode equals the jlHashCode of the event, for an event-type
        // LadderItem
        override val jlHashCode: Int
            get() = event.jlHashCode + type * 23 + transNum * 27
    }

    class LadderPathItem(
        val startEvent: JMLEvent,
        val endEvent: JMLEvent,
    ) : LadderItem() {
        // screen coordinates/dimensions of the path
        var xStart: Int = 0
        var yStart: Int = 0
        var xEnd: Int = 0
        var yEnd: Int = 0
        var xCenter: Int = 0
        var yCenter: Int = 0
        var radius: Int = 0 // for type SELF

        var color: Color? = null
        var transnumStart: Int = 0
        var pathNum: Int = 0
    }

    class LadderPositionItem(
        val position: JMLPosition
    ) : LadderItem() {
        // screen bounding box of the position square
        var xLow: Int = 0
        var xHigh: Int = 0
        var yLow: Int = 0
        var yHigh: Int = 0

        override val jlHashCode: Int
            get() = position.jlHashCode
    }

    //----------------------------------
    //--------------------------------------------------------------------------
    // Methods to handle changes made within this UI
    //--------------------------------------------------------------------------

    // Call this to initiate a change in the pattern. The AnimationEditPanel
    // notifies us of the new pattern through the AnimationAttachment interface
    // below.

    fun onPatternChange(
        newPattern: JMLPattern,
        undoable: Boolean = true
    ) {
        if (undoable) {
            addToUndoList(newPattern)
        }
        aep?.setJMLPattern(newPattern, activeItemHashCode, restart = false)
    }

    fun onNewActiveItem(hash: Int) {
        try {
            aep?.setActiveItem(hash)
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, pattern))
        }
    }

    fun addToUndoList(pat: JMLPattern) {
        parentView.addToUndoList(pat)
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseListener methods
    //--------------------------------------------------------------------------

    override fun mousePressed(me: MouseEvent) {
        val aep2 = aep
        if (aep2 != null && (aep2.writingGIF || !aep2.engineAnimating)) {
            return
        }

        try {
            var my = me.getY()
            my = min(max(my, BORDER_TOP), ladderHeight - BORDER_TOP)

            // on macOS the popup triggers here
            if (me.isPopupTrigger) {
                guiState = STATE_POPUP
                activeEventItem = getSelectedLadderEvent(me.getX(), me.getY())
                activePositionItem = getSelectedLadderPosition(me.getX(), me.getY())
                popupItem = if (activeEventItem != null) activeEventItem else activePositionItem
                if (popupItem == null) {
                    popupItem = getSelectedLadderPath(me.getX(), me.getY(), PATH_SLOP)
                }

                popupX = me.getX()
                popupY = me.getY()
                if (aep2 != null) {
                    val scale = (pattern.loopEndTime - pattern.loopStartTime) /
                        (ladderHeight - 2 * BORDER_TOP).toDouble()
                    val newT = (my - BORDER_TOP).toDouble() * scale
                    animPaused = aep2.isPaused
                    aep2.isPaused = true
                    aep2.time = newT
                    activeItemHashCode = if (activeEventItem != null) {
                        activeEventItem!!.event.jlHashCode
                    } else if (activePositionItem != null) {
                        activePositionItem!!.position.jlHashCode
                    } else {
                        0
                    }
                    onNewActiveItem(activeItemHashCode)
                }

                makePopupMenu(popupItem).show(this, me.getX(), me.getY())
            } else {
                when (guiState) {
                    STATE_INACTIVE -> {
                        var needsHandling = true
                        itemWasSelected = false

                        val oldEventitem = activeEventItem
                        activeEventItem = getSelectedLadderEvent(me.getX(), me.getY())

                        if (activeEventItem != null) {
                            if (oldEventitem == activeEventItem) {
                                itemWasSelected = true
                            }
                            activeItemHashCode = activeEventItem!!.jlHashCode
                            onNewActiveItem(activeItemHashCode)
                            if (activeEventItem!!.type == LadderItem.TYPE_TRANSITION) {
                                // only allow dragging of TYPE_EVENT
                                needsHandling = false
                            }

                            if (needsHandling) {
                                guiState = STATE_MOVING_EVENT
                                activePositionItem = null
                                startY = me.getY()
                                startYLow = activeEventItem!!.yLow
                                startYHigh = activeEventItem!!.yHigh
                                startT = activeEventItem!!.event.t
                                findEventLimits(activeEventItem!!)
                                needsHandling = false
                            }
                        }

                        if (needsHandling) {
                            val oldPositionitem = activePositionItem
                            activePositionItem = getSelectedLadderPosition(me.getX(), me.getY())

                            if (activePositionItem != null) {
                                if (oldPositionitem == activePositionItem) {
                                    itemWasSelected = true
                                }
                                activeItemHashCode = activePositionItem!!.jlHashCode
                                guiState = STATE_MOVING_POSITION
                                activeEventItem = null
                                startY = me.getY()
                                startYLow = activePositionItem!!.yLow
                                startYHigh = activePositionItem!!.yHigh
                                startT = activePositionItem!!.position.t
                                findPositionLimits(activePositionItem!!)
                                onNewActiveItem(activeItemHashCode)
                                needsHandling = false
                            }
                        }

                        if (needsHandling) {
                            guiState = STATE_MOVING_TRACKER
                            trackerY = my
                            if (aep2 != null) {
                                val scale =
                                    ((pattern.loopEndTime - pattern.loopStartTime)
                                        / (ladderHeight - 2 * BORDER_TOP).toDouble())
                                val newtime = (my - BORDER_TOP).toDouble() * scale
                                animPaused = aep2.isPaused
                                aep2.isPaused = true
                                aep2.time = newtime
                                onNewActiveItem(0)
                            }
                        }
                    }

                    STATE_MOVING_EVENT -> {}
                    STATE_MOVING_POSITION -> {}
                    STATE_MOVING_TRACKER -> {}
                    STATE_POPUP -> finishPopup()  // shouldn't ever get here
                }

                repaint()
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, pattern))
        }
    }

    override fun mouseReleased(me: MouseEvent?) {
        val aep2 = aep
        if (aep2 != null && (aep2.writingGIF || !aep2.engineAnimating)) {
            return
        }

        try {
            // on Windows the popup triggers here
            if (me!!.isPopupTrigger) {
                when (guiState) {
                    STATE_INACTIVE, STATE_MOVING_EVENT, STATE_MOVING_POSITION, STATE_MOVING_TRACKER -> {
                        // skip this code for MOVING_TRACKER state, since already executed in
                        // mousePressed() above
                        if (guiState != STATE_MOVING_TRACKER && aep2 != null) {
                            val my = min(max(me.getY(), BORDER_TOP), ladderHeight - BORDER_TOP)
                            val scale = ((pattern.loopEndTime - pattern.loopStartTime)
                                / (ladderHeight - 2 * BORDER_TOP).toDouble())
                            val newtime = (my - BORDER_TOP).toDouble() * scale
                            animPaused = aep2.isPaused
                            aep2.isPaused = true
                            aep2.time = newtime
                            activeItemHashCode = if (activeEventItem != null) {
                                activeEventItem!!.event.jlHashCode
                            } else if (activePositionItem != null) {
                                activePositionItem!!.position.jlHashCode
                            } else {
                                0
                            }
                            onNewActiveItem(activeItemHashCode)
                            aep2.repaint()
                        }

                        guiState = STATE_POPUP

                        if (deltaY != 0) {
                            deltaY = 0
                            repaint()
                        }
                        popupX = me.getX()
                        popupY = me.getY()
                        popupItem =
                            (if (activeEventItem != null) activeEventItem else activePositionItem)
                        if (popupItem == null) {
                            popupItem = getSelectedLadderPath(me.getX(), me.getY(), PATH_SLOP)
                        }

                        makePopupMenu(popupItem).show(this, me.getX(), me.getY())
                    }

                    STATE_POPUP -> throw JuggleExceptionInternal(
                        "tried to enter POPUP state while already in it"
                    )
                }
            } else {
                when (guiState) {
                    STATE_INACTIVE -> {}
                    STATE_MOVING_EVENT -> {
                        guiState = STATE_INACTIVE
                        if (deltaY != 0) {
                            deltaY = 0
                            addToUndoList(pattern)
                        } else if (itemWasSelected) {
                            // clicked without moving --> deselect
                            activeEventItem = null
                            activeItemHashCode = 0
                            onNewActiveItem(0)
                            repaint()
                        }
                    }

                    STATE_MOVING_POSITION -> {
                        guiState = STATE_INACTIVE
                        if (deltaY != 0) {
                            deltaY = 0
                            addToUndoList(pattern)
                        } else if (itemWasSelected) {
                            activePositionItem = null
                            activeItemHashCode = 0
                            onNewActiveItem(0)
                            repaint()
                        }
                    }

                    STATE_MOVING_TRACKER -> {
                        guiState = STATE_INACTIVE
                        aep2?.isPaused = animPaused
                        repaint()
                    }

                    STATE_POPUP -> {}
                }
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, pattern))
        }
    }

    override fun mouseClicked(e: MouseEvent?) {}

    override fun mouseEntered(e: MouseEvent?) {}

    override fun mouseExited(e: MouseEvent?) {}

    //--------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //--------------------------------------------------------------------------

    override fun mouseDragged(me: MouseEvent) {
        val aep2 = aep
        if (aep2 != null && (aep2.writingGIF || !aep2.engineAnimating)) {
            return
        }

        try {
            val my = min(max(me.getY(), BORDER_TOP), ladderHeight - BORDER_TOP)

            when (guiState) {
                STATE_INACTIVE, STATE_POPUP -> {}
                STATE_MOVING_EVENT -> {
                    val oldDeltaY = deltaY
                    deltaY = getClippedEventTime(activeEventItem!!, me)
                    if (deltaY != oldDeltaY) {
                        moveEventInPattern(activeEventItem!!.transEventItem!!)
                    }
                }

                STATE_MOVING_POSITION -> {
                    val oldDeltaY = deltaY
                    deltaY = getClippedPositionTime(me, activePositionItem!!.position)
                    if (deltaY != oldDeltaY) {
                        movePositionInPattern(activePositionItem!!)
                    }
                }

                STATE_MOVING_TRACKER -> {
                    trackerY = my
                    repaint()
                    if (aep2 != null) {
                        val scale = (pattern.loopEndTime - pattern.loopStartTime) /
                            (ladderHeight - 2 * BORDER_TOP).toDouble()
                        val newtime = (my - BORDER_TOP).toDouble() * scale
                        aep2.time = newtime
                        aep2.repaint()
                    }
                }
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, pattern))
        }
    }

    override fun mouseMoved(e: MouseEvent?) {}

    //--------------------------------------------------------------------------
    // Utility methods for mouse interactions
    //--------------------------------------------------------------------------

    // Set `deltaYMin` and `deltaYMax` for a selected event, determining the
    // number of pixels it is allowed to move up or down.

    private fun findEventLimits(item: LadderEventItem) {
        var tMin = pattern.loopStartTime
        var tMax = pattern.loopEndTime
        val evPaths = item.event.transitions.filter { it.isThrowOrCatch }.map { it.path }.toList()

        // other events with throws/catches using the same paths define the
        // limits of how far the item can move

        pattern.allEvents
            .filter { it.primary != item.primary }
            .filter { it.event.transitions.any { tr -> tr.isThrowOrCatch && tr.path in evPaths } }
            .forEach {
                if (it.event.t < item.event.t - MIN_EVENT_SEP_TIME) {
                    tMin = max(tMin, it.event.t + MIN_THROW_SEP_TIME)
                } else if (it.event.t > item.event.t + MIN_THROW_SEP_TIME) {
                    tMax = min(tMax, it.event.t - MIN_THROW_SEP_TIME)
                }
            }

        val scale = (pattern.loopEndTime - pattern.loopStartTime) /
            (ladderHeight - 2 * BORDER_TOP).toDouble()
        deltaYMin = ((tMin - item.event.t) / scale).toInt()
        deltaYMax = ((tMax - item.event.t) / scale).toInt()
    }

    // Return the value of `deltaY` during mouse drag of an event, clipping it to
    // enforce proximity limits between various event types, as well as hard
    // limits `deltaYMin` and `deltaYMax`.

    private fun getClippedEventTime(item: LadderEventItem, me: MouseEvent): Int {
        val dy = min(max(me.getY() - startY, deltaYMin), deltaYMax)
        val scale = (pattern.loopEndTime - pattern.loopStartTime) /
            (ladderHeight - 2 * BORDER_TOP).toDouble()
        val newT = startT + dy * scale  // unclipped new event time

        // Calculate a window (tExclMin, tExclMax) of excluded times based on
        // proximity to other events, where `newT` is contained within the window.
        var tExclMin = newT
        var tExclMax = newT

        while (true) {
            var changed = false
            pattern.allEvents
                .filter { it.primary != item.primary }
                .filter { it.event.juggler == item.event.juggler && it.event.hand == item.event.hand }
                .forEach {
                    val sep = if (it.event.hasThrow && item.event.hasThrowOrCatch
                        || it.event.hasThrowOrCatch && item.event.hasThrow
                    ) {
                        MIN_THROW_SEP_TIME
                    } else {
                        MIN_EVENT_SEP_TIME
                    }
                    val evExclMin = it.event.t - sep
                    val evExclMax = it.event.t + sep

                    if (tExclMin > evExclMin && tExclMin <= evExclMax) {
                        tExclMin = evExclMin
                        changed = true
                    }
                    if (tExclMax in evExclMin..<evExclMax) {
                        tExclMax = evExclMax
                        changed = true
                    }
                }
            if (!changed) break
        }

        // Clip the event time `newT` to whichever end of the exclusion window
        // is closest. First check if each end is feasible.
        val exclDyMin = floor((tExclMin - startT) / scale).toInt()
        val exclDyMax = ceil((tExclMax - startT) / scale).toInt()
        val feasibleMin = (exclDyMin in deltaYMin..deltaYMax)
        val feasibleMax = (exclDyMax in deltaYMin..deltaYMax)

        return if (feasibleMin && feasibleMax) {
            val tMidpoint = 0.5 * (tExclMin + tExclMax)
            if (newT <= tMidpoint) exclDyMin else exclDyMax
        } else if (feasibleMin) {
            exclDyMin
        } else if (feasibleMax) {
            exclDyMax
        } else {
            dy
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun moveEventInPattern(item: LadderEventItem) {
        val scale = (pattern.loopEndTime - pattern.loopStartTime) /
            (ladderHeight - 2 * BORDER_TOP).toDouble()
        val newT = run {
            val tempT = startT + deltaY * scale
            if (tempT < pattern.loopStartTime + scale) {
                pattern.loopStartTime  // within 1 pixel of top
            } else if (tempT >= pattern.loopEndTime) {
                pattern.loopEndTime - 0.0001
            } else {
                tempT
            }
        }

        // new event to swap in for `item.event`
        val newEvent = item.event.copy(t = newT)

        val record = PatternBuilder.fromJMLPattern(pattern)
        val index = record.events.indexOf(item.primary)
        if (index < 0) throw JuggleExceptionInternal("Error in ELD.moveEventInPattern()")
        if (item.event == item.primary) {
            record.events[index] = newEvent
        } else {
            // make the change in the primary event
            val newPrimaryT = newT + item.primary.t - item.event.t
            val newEventPrimary = item.primary.copy(t = newPrimaryT)
            record.events[index] = newEventPrimary
        }

        // did we just hop over another event in the same hand? if so then fix
        // the HOLDING transitions
        val needFixHolds = ladderEventItems.any {
            (it.event.t - item.event.t) * (it.event.t - newT) < 0.0 &&
                it.event.juggler == item.event.juggler &&
                it.event.hand == item.event.hand
        }
        if (needFixHolds) {
            record.fixHolds()
        }

        record.selectPrimaryEvents()
        val newPattern = JMLPattern.fromPatternBuilder(record)
        activeItemHashCode = newEvent.jlHashCode
        onPatternChange(newPattern, undoable = false)
    }

    private fun findPositionLimits(item: LadderPositionItem) {
        val tmin = pattern.loopStartTime
        val tmax = pattern.loopEndTime
        val scale =
            (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()

        deltaYMin = ((tmin - item.position.t) / scale).toInt()
        deltaYMax = ((tmax - item.position.t) / scale).toInt()
    }

    // Return value of `delta_y` during mouse drag of an event, clipping it to
    // enforce proximity limits between various event types, as well as hard
    // limits `delta_y_min` and `delta_y_max`.

    private fun getClippedPositionTime(me: MouseEvent, position: JMLPosition): Int {
        var dy = me.getY() - startY
        dy = min(max(dy, deltaYMin), deltaYMax)

        val scale =
            (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        val shift = dy * scale
        val newt = startT + shift // unclipped new event time

        // Calculate a window (t_excl_min, t_excl_max) of excluded times based on
        // proximity to other events, where `newt` is contained within the window.
        var tExclMin = newt
        var tExclMax = newt
        var changed: Boolean

        do {
            changed = false

            for (pos in pattern.positions) {
                if (pos != position && pos.juggler == position.juggler) {
                    val posExclMin: Double = pos.t - MIN_POSITION_SEP_TIME
                    val posExclMax: Double = pos.t + MIN_POSITION_SEP_TIME

                    if (tExclMax in posExclMin..<posExclMax) {
                        tExclMax = posExclMax
                        changed = true
                    }

                    if (posExclMin < tExclMin && posExclMax >= tExclMin) {
                        tExclMin = posExclMin
                        changed = true
                    }
                }
            }
        } while (changed)

        // Clip the position time `newt` to whichever end of the exclusion window
        // is closest. First check if each end is feasible.
        val exclDyMin = floor((tExclMin - startT) / scale).toInt()
        val exclDyMax = ceil((tExclMax - startT) / scale).toInt()
        val feasibleMin = (exclDyMin in deltaYMin..deltaYMax)
        val feasibleMax = (exclDyMax in deltaYMin..deltaYMax)

        var resultDy = dy

        if (feasibleMin && feasibleMax) {
            val tMidpoint = 0.5 * (tExclMin + tExclMax)
            resultDy = (if (newt <= tMidpoint) exclDyMin else exclDyMax)
        } else if (feasibleMin) {
            resultDy = exclDyMin
        } else if (feasibleMax) {
            resultDy = exclDyMax
        }

        return resultDy
    }

    private fun movePositionInPattern(item: LadderPositionItem) {
        val pos = item.position
        val scale = (pattern.loopEndTime - pattern.loopStartTime) /
            (ladderHeight - 2 * BORDER_TOP).toDouble()

        var newt = startT + deltaY * scale
        if (newt < pattern.loopStartTime + scale) {
            newt = pattern.loopStartTime // within 1 pixel of top
        } else if (newt >= pattern.loopEndTime) {
            newt = pattern.loopEndTime - 0.0001
        }

        val rec = PatternBuilder.fromJMLPattern(pattern)
        val index = rec.positions.indexOf(pos)
        if (index < 0) throw JuggleExceptionInternal("Error in ELD.movePositionInPattern()")
        val newPosition = pos.copy(t = newt)
        rec.positions[index] = newPosition
        activeItemHashCode = newPosition.jlHashCode
        onPatternChange(JMLPattern.fromPatternBuilder(rec), undoable = false)
    }

    private fun makePopupMenu(laditem: LadderItem?): JPopupMenu {
        val popup = JPopupMenu()

        for (i in popupItems.indices) {
            val name: String? = popupItems[i]
            if (name == null) {
                popup.addSeparator()
                continue
            }

            val item = JMenuItem(jlGetStringResource(popupItemsStringResources[i]!!))
            val command: String? = popupCommands[i]
            item.actionCommand = command
            item.addActionListener(this)
            item.setEnabled(isCommandEnabled(laditem, command))
            popup.add(item)
        }

        popup.setBorder(BevelBorder(BevelBorder.RAISED))

        popup.addPopupMenuListener(
            object : PopupMenuListener {
                override fun popupMenuCanceled(e: PopupMenuEvent?) {
                    finishPopup()
                }

                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {}
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {}
            })

        return popup
    }

    //--------------------------------------------------------------------------
    // java.awt.event.ActionListener methods
    //--------------------------------------------------------------------------

    override fun actionPerformed(event: ActionEvent) {
        try {
            val command = event.getActionCommand() ?: return
            when (command) {
                "addeventtoleft" -> addEventToHand(HandLink.LEFT_HAND)
                "addeventtoright" -> addEventToHand(HandLink.RIGHT_HAND)
                "removeevent" -> removeEvent()
                "addposition" -> addPositionToJuggler()
                "removeposition" -> removePosition()
                "defineprop" -> defineProp()
                "definethrow" -> defineThrow()
                "changetocatch" -> changeCatchStyleTo(JMLTransition.TRANS_CATCH)
                "changetosoftcatch" -> changeCatchStyleTo(JMLTransition.TRANS_SOFTCATCH)
                "changetograbcatch" -> changeCatchStyleTo(JMLTransition.TRANS_GRABCATCH)
                "makelast" -> makeLastInEvent()
                else -> throw JuggleExceptionInternal("unknown item in ELD popup")
            }
            finishPopup()
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, pattern))
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun addEventToHand(hand: Int) {
        val juggler = run {
            var jug = 1
            if (pattern.numberOfJugglers > 1) {
                var mouseX = popupX
                val jugglerRightPx = (leftX + rightX + jugglerDeltaX) / 2
                while (jug <= pattern.numberOfJugglers) {
                    if (mouseX < jugglerRightPx) {
                        break
                    }
                    mouseX -= jugglerDeltaX
                    ++jug
                }
                jug = min(jug, pattern.numberOfJugglers)
            }
            jug
        }

        val scale = (pattern.loopEndTime - pattern.loopStartTime) /
            (ladderHeight - 2 * BORDER_TOP).toDouble()
        val newTime = (popupY - BORDER_TOP).toDouble() * scale
        val newGlobalCoordinate = Coordinate()
        pattern.layout.getHandCoordinate(juggler, hand, newTime, newGlobalCoordinate)

        val newLocalCoordinate =
            pattern.layout.convertGlobalToLocal(newGlobalCoordinate, juggler, newTime)
        val newEvent = JMLEvent(
            x = newLocalCoordinate.x,
            y = newLocalCoordinate.y,
            z = newLocalCoordinate.z,
            t = newTime,
            juggler = juggler,
            hand = hand
        )

        val record = PatternBuilder.fromJMLPattern(pattern)
        record.events.add(newEvent)
        record.fixHolds()
        record.selectPrimaryEvents()
        activeItemHashCode = newEvent.jlHashCode
        onPatternChange(JMLPattern.fromPatternBuilder(record))
    }

    @Throws(JuggleExceptionInternal::class)
    private fun removeEvent() {
        // makePopupMenu() ensures that the event only has hold transitions
        if (popupItem !is LadderEventItem) {
            throw JuggleExceptionInternal("LadderDiagram illegal remove event")
        }
        val evRemove = (popupItem as LadderEventItem).primary
        val record = PatternBuilder.fromJMLPattern(pattern)
        record.events.remove(evRemove)
        activeItemHashCode = 0
        onPatternChange(JMLPattern.fromPatternBuilder(record))
    }

    private fun addPositionToJuggler() {
        val juggler = run {
            var jug = 1
            if (pattern.numberOfJugglers > 1) {
                var mouseX = popupX
                val jugglerRightPx = (leftX + rightX + jugglerDeltaX) / 2
                while (jug <= pattern.numberOfJugglers) {
                    if (mouseX < jugglerRightPx) {
                        break
                    }
                    mouseX -= jugglerDeltaX
                    ++jug
                }
                jug = min(jug, pattern.numberOfJugglers)
            }
            jug
        }

        val scale = (pattern.loopEndTime - pattern.loopStartTime) /
            (ladderHeight - 2 * BORDER_TOP).toDouble()
        val newTime = (popupY - BORDER_TOP).toDouble() * scale

        val newGlobalCoordinate = Coordinate()
        pattern.layout.getJugglerPosition(juggler, newTime, newGlobalCoordinate)
        val pos = JMLPosition(
            x = newGlobalCoordinate.x,
            y = newGlobalCoordinate.y,
            z = newGlobalCoordinate.z,
            t = newTime,
            angle = pattern.layout.getJugglerAngle(juggler, newTime),
            juggler = juggler
        )
        val rec = PatternBuilder.fromJMLPattern(pattern)
        rec.positions.add(pos)
        activeItemHashCode = pos.jlHashCode
        onPatternChange(JMLPattern.fromPatternBuilder(rec))
    }

    @Throws(JuggleExceptionInternal::class)
    private fun removePosition() {
        if (popupItem !is LadderPositionItem) {
            throw JuggleExceptionInternal("LadderDiagram illegal remove position")
        }
        val pos = (popupItem as LadderPositionItem).position
        val rec = PatternBuilder.fromJMLPattern(pattern)
        rec.positions.remove(pos)
        activeItemHashCode = 0
        onPatternChange(JMLPattern.fromPatternBuilder(rec))
    }

    @Throws(JuggleExceptionInternal::class)
    private fun defineProp() {
        if (popupItem == null) {
            throw JuggleExceptionInternal("defineProp() null popupitem")
        }

        // figure out which path number the user selected
        val pn: Int
        if (popupItem is LadderEventItem) {
            if (popupItem!!.type != LadderItem.TYPE_TRANSITION) {
                throw JuggleExceptionInternal("defineProp() bad LadderItem type")
            }
            val ev = (popupItem as LadderEventItem).event
            val transnum = (popupItem as LadderEventItem).transNum
            val tr = ev.transitions[transnum]
            pn = tr.path
        } else {
            pn = (popupItem as LadderPathItem).pathNum
        }

        val animpropnum = aep!!.animator.animPropNum
        val propnum = animpropnum!![pn - 1]
        val startprop = pattern.getProp(propnum)
        val prtypes: List<String> = Prop.builtinProps

        val jd = JDialog(parentFrame, jlGetStringResource(Res.string.gui_define_prop), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab = JLabel(jlGetStringResource(Res.string.gui_prop_type))
        p1.add(lab)
        gb.setConstraints(
            lab, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )

        val p2 = JPanel()
        p2.setLayout(gb)

        val cb1 = JComboBox(prtypes.toTypedArray())
        p1.add(cb1)
        gb.setConstraints(
            cb1, jlConstraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 10, 0, 0))
        )
        cb1.addActionListener { _: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            try {
                val pt = if (type.equals(startprop.type, ignoreCase = true)) {
                    startprop
                } else {
                    newProp(type)
                }
                makeParametersPanel(p2, pt.parameterDescriptors)
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(jd, jeu.message)
                return@addActionListener
            }
            jd.pack()
        }
        val bp: List<String> = Prop.builtinProps
        for (i in bp.indices) {
            if (bp[i].equals(startprop.type, ignoreCase = true)) {
                cb1.setSelectedIndex(i)
                break
            }
        }

        val p3 = JPanel()
        p3.setLayout(gb)
        val cancelbutton = JButton(jlGetStringResource(Res.string.gui_cancel))
        p3.add(cancelbutton)
        gb.setConstraints(
            cancelbutton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        cancelbutton.addActionListener { _: ActionEvent? -> jd.dispose() }
        val okbutton = JButton(jlGetStringResource(Res.string.gui_ok))
        p3.add(okbutton)
        gb.setConstraints(
            okbutton, jlConstraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )
        okbutton.addActionListener { _: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            val mod: String?

            try {
                // fail if prop definition is invalid, before we change the pattern
                mod = this.dialogParameterList
                JMLProp(type.lowercase(Locale.getDefault()), mod).prop.isColorable
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(parentFrame, jeu.message)
                return@addActionListener
            }

            val rec = PatternBuilder.fromJMLPattern(pattern)

            // sync paths with current prop list
            for (i in 0..<rec.numberOfPaths) {
                rec.propAssignment[i] = animpropnum[i]
            }

            // check to see if any other paths are using this prop definition
            var killprop = true
            for (i in 0..<rec.numberOfPaths) {
                if (i != pn - 1) {
                    if (animpropnum[i] == propnum) {
                        killprop = false
                        break
                    }
                }
            }

            if (killprop) {
                rec.props.removeAt(propnum - 1)
                for (i in 0..<rec.numberOfPaths) {
                    if (rec.propAssignment[i] > propnum) {
                        rec.propAssignment[i] = rec.propAssignment[i] - 1
                    }
                }
            }

            // check to see if a prop like this one has already been defined
            var gotmatch = false
            var matchingprop = 0
            for (i in 1..rec.props.size) {
                val pdef = rec.props[i - 1]
                if (type.equals(pdef.type, ignoreCase = true)) {
                    if ((mod == null && pdef.mod == null)
                        || (mod != null && mod.equals(pdef.mod, ignoreCase = true))
                    ) {
                        gotmatch = true
                        matchingprop = i
                        break
                    }
                }
            }

            if (gotmatch) {
                // new prop is identical to pre-existing one
                rec.propAssignment[pn - 1] = matchingprop
            } else {
                // new prop is different
                val newprop = JMLProp(type.lowercase(Locale.getDefault()), mod)
                rec.props.add(newprop)
                rec.propAssignment[pn - 1] = rec.props.size
            }

            onPatternChange(JMLPattern.fromPatternBuilder(rec))
            jd.dispose()
        }

        jd.contentPane.add(p1)
        gb.setConstraints(
            p1, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.contentPane.add(p2)
        gb.setConstraints(
            p2, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )
        jd.contentPane.add(p3)
        gb.setConstraints(
            p3, jlConstraints(GridBagConstraints.LINE_END, 0, 2, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default

        val loc = Locale.getDefault()
        jd.applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        jd.pack()
        jd.setResizable(false)
        jd.setLocationRelativeTo(this)
        jd.isVisible = true  // blocks until dispose() above
        dialogControls = null
    }

    @Throws(JuggleExceptionInternal::class)
    private fun defineThrow() {
        if (popupItem !is LadderEventItem) {
            throw JuggleExceptionInternal("defineThrow() class format")
        }
        val evPrimary = (popupItem as LadderEventItem).primary
        val tr = evPrimary.transitions[(popupItem as LadderEventItem).transNum]

        val pptypes: List<String> = Path.builtinPaths

        val jd = JDialog(parentFrame, jlGetStringResource(Res.string.gui_define_throw), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab = JLabel(jlGetStringResource(Res.string.gui_throw_type))
        p1.add(lab)
        gb.setConstraints(
            lab, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )

        val p2 = JPanel()
        p2.setLayout(gb)

        val cb1 = JComboBox(pptypes.toTypedArray())
        p1.add(cb1)
        gb.setConstraints(
            cb1, jlConstraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 10, 0, 0))
        )
        cb1.addActionListener { _: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            try {
                val ppt = newPath(type)
                if (type.equals(tr.throwType, ignoreCase = true)) {
                    // populate with current throw parameters
                    ppt.initPath(tr.throwMod)
                }
                makeParametersPanel(p2, ppt.parameterDescriptors)
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(jd, jeu.message)
                return@addActionListener
            }
            jd.pack()
        }
        val bpp: List<String> = Path.builtinPaths
        for (i in bpp.indices) {
            if (bpp[i].equals(tr.throwType, ignoreCase = true)) {
                cb1.setSelectedIndex(i)
                break
            }
        }

        val p3 = JPanel()
        p3.setLayout(gb)
        val cancelbutton = JButton(jlGetStringResource(Res.string.gui_cancel))
        p3.add(cancelbutton)
        gb.setConstraints(
            cancelbutton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        cancelbutton.addActionListener { _: ActionEvent? -> jd.dispose() }
        val okbutton = JButton(jlGetStringResource(Res.string.gui_ok))
        p3.add(okbutton)
        gb.setConstraints(
            okbutton, jlConstraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )
        okbutton.addActionListener { _: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            val mod = try {
                this.dialogParameterList
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(parentFrame, jeu.message)
                return@addActionListener
            }

            val newTransition = tr.copy(
                throwType = type.lowercase(),
                throwMod = mod
            )
            val newPrimary = evPrimary.copy(
                transitions = evPrimary.transitions.toMutableList().apply {
                    this[(popupItem as LadderEventItem).transNum] = newTransition
                }
            )
            val record = PatternBuilder.fromJMLPattern(pattern)
            val index = record.events.indexOf(evPrimary)
            record.events[index] = newPrimary
            onPatternChange(JMLPattern.fromPatternBuilder(record))
            jd.dispose()
        }

        jd.contentPane.add(p1)
        gb.setConstraints(
            p1, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.contentPane.add(p2)
        gb.setConstraints(
            p2, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )
        jd.contentPane.add(p3)
        gb.setConstraints(
            p3, jlConstraints(GridBagConstraints.LINE_END, 0, 2, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default

        jd.pack()
        jd.setResizable(false)
        jd.setLocationRelativeTo(this)
        jd.isVisible = true  // blocks until dispose() above
        dialogControls = null
    }

    @Throws(JuggleExceptionInternal::class)
    private fun changeCatchStyleTo(type: Int) {
        if (popupItem == null) {
            throw JuggleExceptionInternal("No popupitem in case 10")
        }
        if (popupItem !is LadderEventItem) {
            throw JuggleExceptionInternal("LadderDiagram change to catch class format")
        }
        val evPrimary = (popupItem as LadderEventItem).primary
        val tr = evPrimary.transitions[(popupItem as LadderEventItem).transNum]
        val newPrimary = evPrimary.copy(
            transitions = evPrimary.transitions.toMutableList().apply {
                this[(popupItem as LadderEventItem).transNum] = tr.copy(type = type)
            }
        )

        val record = PatternBuilder.fromJMLPattern(pattern)
        val index = record.events.indexOf(evPrimary)
        record.events[index] = newPrimary
        activeItemHashCode = (popupItem as LadderEventItem).event.jlHashCode + 23 +
            (popupItem as LadderEventItem).transNum * 27
        onPatternChange(JMLPattern.fromPatternBuilder(record))
    }

    @Throws(JuggleExceptionInternal::class)
    private fun makeLastInEvent() {
        if (popupItem == null) {
            throw JuggleExceptionInternal("No popupitem in case 8")
        }
        if (popupItem !is LadderEventItem) {
            throw JuggleExceptionInternal("LadderDiagram make last transition class format")
        }
        val evPrimary = (popupItem as LadderEventItem).primary
        val tr = evPrimary.transitions[(popupItem as LadderEventItem).transNum]
        val newPrimary = evPrimary.withoutTransition(tr).withTransition(tr)  // adds at end

        val record = PatternBuilder.fromJMLPattern(pattern)
        val index = record.events.indexOf(evPrimary)
        record.events[index] = newPrimary
        activeItemHashCode = (popupItem as LadderEventItem).event.jlHashCode + 23 +
            (newPrimary.transitions.size - 1) * 27
        onPatternChange(JMLPattern.fromPatternBuilder(record))
    }

    // Helper for defineProp() and defineThrow().

    private fun makeParametersPanel(jp: JPanel, pd: List<ParameterDescriptor>) {
        jp.removeAll()
        dialogControls = mutableListOf()
        dialogPd = pd

        if (pd.isEmpty())
            return

        val pdp = JPanel()
        val gb = GridBagLayout()
        jp.setLayout(gb)
        pdp.setLayout(gb)

        for (i in pd.indices) {
            val lab = JLabel(pd[i].name)
            pdp.add(lab)
            gb.setConstraints(
                lab, jlConstraints(GridBagConstraints.LINE_START, 0, i, Insets(0, 0, 0, 0))
            )
            if (pd[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
                // JComboBox jcb = new JComboBox(booleanList);
                val jcb = JCheckBox()
                pdp.add(jcb)
                gb.setConstraints(
                    jcb, jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(2, 5, 2, 0))
                )
                dialogControls!!.add(jcb)
                val def = (pd[i].value) as Boolean
                // jcb.setSelectedIndex(def ? 0 : 1);
                jcb.setSelected(def)
            } else if (pd[i].type == ParameterDescriptor.TYPE_FLOAT) {
                val tf = JTextField(7)
                pdp.add(tf)
                gb.setConstraints(
                    tf, jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                )
                dialogControls!!.add(tf)
                val def = (pd[i].value) as Double?
                tf.text = def.toString()
            } else if (pd[i].type == ParameterDescriptor.TYPE_CHOICE) {
                val choices = pd[i].range!!.toTypedArray()
                val jcb = JComboBox(choices)
                jcb.setMaximumRowCount(15)
                pdp.add(jcb)
                gb.setConstraints(
                    jcb, jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                )
                dialogControls!!.add(jcb)

                val `val` = (pd[i].value) as String?
                for (j in choices.indices) {
                    if (`val`.equals(choices[j], ignoreCase = true)) {
                        jcb.setSelectedIndex(j)
                        break
                    }
                }
            } else if (pd[i].type == ParameterDescriptor.TYPE_INT) {
                val tf = JTextField(4)
                pdp.add(tf)
                gb.setConstraints(
                    tf, jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                )
                dialogControls!!.add(tf)
                val def = (pd[i].value) as Int?
                tf.text = def.toString()

                tf.addCaretListener { _: CaretEvent? -> }
            } else if (pd[i].type == ParameterDescriptor.TYPE_ICON) {
                val fileSource = pd[i].value as String
                val composeImage = jlGetImageResource(fileSource)

                val icon = ImageIcon(composeImage.toAwtImage(), fileSource)
                val maxHeight = 100f
                if (icon.iconHeight > maxHeight) {
                    val scaleFactor = maxHeight / icon.iconHeight
                    val height = (scaleFactor * icon.iconHeight).toInt()
                    val width = (scaleFactor * icon.iconWidth).toInt()
                    icon.setImage(
                        icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH)
                    )
                }
                val label = JLabel(icon)

                // Clicking on the icon launches a file chooser for getting a new image
                label.addMouseListener(
                    object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent?) {
                            jlJfc.setFileFilter(
                                FileNameExtensionFilter(
                                    "Image file",
                                    "jpg",
                                    "jpeg",
                                    "gif",
                                    "png"
                                )
                            )
                            val result = jlJfc.showOpenDialog(this@LadderDiagram)
                            if (result != JFileChooser.APPROVE_OPTION) {
                                return
                            }

                            try {
                                // Rebuild the parameter panel
                                pd[0].value = jlJfc.selectedFile.toURI().toURL().toString()
                                makeParametersPanel(jp, pd)
                                ((jp.getTopLevelAncestor()) as JDialog).pack()
                            } catch (_: MalformedURLException) {
                                // this should never happen
                                jlHandleFatalException(
                                    JuggleExceptionUser(jlGetStringResource(Res.string.error_malformed_url))
                                )
                            }
                        }
                    })
                // Add the icon to the panel
                pdp.add(label)
                gb.setConstraints(
                    label,
                    jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 5, 0))
                )
                dialogControls!!.add(label)
            }
        }

        jp.add(pdp)
        gb.setConstraints(
            pdp, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(10, 10, 0, 10))
        )
    }

    @get:Throws(JuggleExceptionUser::class)
    private val dialogParameterList: String?
        get() {
            var result: String? = null
            val dialog = dialogPd
            for (i in dialog.indices) {
                var term: String? = null
                val control: Any = dialogControls!![i]
                if (dialog[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
                    // JComboBox jcb = (JComboBox)control;
                    // boolean val = ((jcb.getSelectedIndex() == 0) ? true : false);
                    val jcb = control as JCheckBox
                    val value = jcb.isSelected
                    val defValue = (dialog[i].defaultValue) as Boolean
                    if (value != defValue) {
                        term = value.toString()
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_FLOAT) {
                    val tf = control as JTextField
                    try {
                        val value = jlParseFiniteDouble(tf.getText())
                        val defValue = (dialog[i].defaultValue) as Double
                        if (value != defValue) {
                            term = tf.getText().trim { it <= ' ' }
                        }
                    } catch (_: NumberFormatException) {
                        val message =
                            jlGetStringResource(Res.string.error_number_format, dialog[i].name)
                        throw JuggleExceptionUser(message)
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_CHOICE) {
                    val jcb = control as JComboBox<*>
                    val ind = jcb.getSelectedIndex()
                    val value = dialog[i].range!![ind]
                    val defValue = (dialog[i].defaultValue) as String?
                    if (!value.equals(defValue, ignoreCase = true)) {
                        term = value
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_INT) {
                    val tf = control as JTextField
                    try {
                        val value = tf.getText().toInt()
                        val defValue = (dialog[i].defaultValue) as Int
                        if (value != defValue) {
                            term = tf.getText().trim { it <= ' ' }
                        }
                    } catch (_: NumberFormatException) {
                        val message =
                            jlGetStringResource(Res.string.error_number_format, dialog[i].name)
                        throw JuggleExceptionUser(message)
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_ICON) {
                    val label = control as JLabel
                    val icon = label.icon as ImageIcon
                    val def: String = dialog[i].defaultValue.toString()
                    if (icon.getDescription() != def) {
                        term = icon.getDescription() // This contains the URL string
                    }
                }

                if (term != null) {
                    term = "${dialog[i].name}=$term"
                    result = if (result == null) term else "$result;$term"
                }
            }
            return result
        }

    // Call this at the very end of every popup interaction.

    private fun finishPopup() {
        popupItem = null
        if (guiState == STATE_POPUP) {
            guiState = STATE_INACTIVE
            aep?.isPaused = animPaused
        }
    }

    //--------------------------------------------------------------------------
    // AnimationPanel.AnimationAttachment methods
    //--------------------------------------------------------------------------

    override fun setAnimationPanel(animPanel: AnimationPanel?) {
        if (animPanel is AnimationEditPanel) {
            aep = animPanel
        }
    }

    override fun setJMLPattern(pat: JMLPattern, activeHashCode: Int) {
        pattern = pat
        if (activeHashCode != 0) {
            // use updated jlHashCode from the animation panel
            setActiveItem(activeHashCode)
        } else {
            // otherwise try to reactivate previous active item
            setActiveItem(activeItemHashCode)
        }
        addMouseListener(this)
        addMouseMotionListener(this)
    }

    override fun setActiveItem(activeHashCode: Int) {
        activeItemHashCode = 0
        activeEventItem = null
        activePositionItem = null

        for (item in ladderEventItems) {
            if (item.jlHashCode == activeHashCode) {
                activeItemHashCode = activeHashCode
                activeEventItem = item
            }
        }
        for (item in ladderPositionItems) {
            if (item.jlHashCode == activeHashCode) {
                activeItemHashCode = activeHashCode
                activePositionItem = item
            }
        }
        repaint()
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

        if (!paintLadder(gr)) return

        // draw the box around the selected position
        val api = activePositionItem
        if (api != null) {
            gr.color = COLOR_SELECTION
            gr.drawLine(
                api.xLow - 1,
                api.yLow - 1,
                api.xHigh + 1,
                api.yLow - 1
            )
            gr.drawLine(
                api.xHigh + 1,
                api.yLow - 1,
                api.xHigh + 1,
                api.yHigh + 1
            )
            gr.drawLine(
                api.xHigh + 1,
                api.yHigh + 1,
                api.xLow,
                api.yHigh + 1
            )
            gr.drawLine(
                api.xLow - 1,
                api.yHigh + 1,
                api.xLow - 1,
                api.yLow - 1
            )
        }

        // draw the box around the selected event
        val aei = activeEventItem
        if (aei != null) {
            gr.color = COLOR_SELECTION
            gr.drawLine(
                aei.xLow - 1,
                aei.yLow - 1,
                aei.xHigh + 1,
                aei.yLow - 1
            )
            gr.drawLine(
                aei.xHigh + 1,
                aei.yLow - 1,
                aei.xHigh + 1,
                aei.yHigh + 1
            )
            gr.drawLine(
                aei.xHigh + 1,
                aei.yHigh + 1,
                aei.xLow,
                aei.yHigh + 1
            )
            gr.drawLine(
                aei.xLow - 1,
                aei.yHigh + 1,
                aei.xLow - 1,
                aei.yLow - 1
            )
        }

        // label the tracker line with the time
        if (guiState == STATE_MOVING_TRACKER) {
            gr.color = COLOR_TRACKER
            gr.drawString(jlToStringRounded(simTime, 2) + " s", ladderWidth / 2 - 18, trackerY - 5)
        }
    }

    // Determine which commands are enabled for a particular LadderItem
    //
    // Returns true for enabled, false for disabled

    private fun isCommandEnabled(laditem: LadderItem?, command: String?): Boolean {
        if (laditem == null) {
            return !listOf(
                "removeevent",
                "removeposition",
                "defineprop",
                "definethrow",
                "changetocatch",
                "changetosoftcatch",
                "changetograbcatch",
                "makelast"
            ).contains(command)
        } else if (laditem.type == LadderItem.TYPE_EVENT) {
            if (listOf(
                    "addeventtoleft",
                    "addeventtoright",
                    "addposition",
                    "removeposition",
                    "defineprop",
                    "definethrow",
                    "changetocatch",
                    "changetosoftcatch",
                    "changetograbcatch",
                    "makelast"
                ).contains(command)
            ) return false

            if (command == "removeevent") {
                // can't remove an event with throws or catches
                val evitem = laditem as LadderEventItem
                if (evitem.event.transitions.any { it.isThrowOrCatch }) {
                    return false
                }

                // can't delete an event if it's the last one for that hand
                val anotherEventForHand = ladderEventItems.any {
                    it.event.juggler == evitem.event.juggler &&
                        it.event.hand == evitem.event.hand &&
                        it.primary != evitem.primary
                }
                if (!anotherEventForHand) {
                    return false
                }
            }
        } else if (laditem.type == LadderItem.TYPE_TRANSITION) {
            if (mutableListOf(
                    "addeventtoleft",
                    "addeventtoright",
                    "addposition",
                    "removeposition",
                    "removeevent"
                ).contains(command)
            ) return false

            val evitem = laditem as LadderEventItem
            val tr = evitem.event.transitions[evitem.transNum]

            when (command) {
                "makelast" ->
                    return evitem.transNum != (evitem.event.transitions.size - 1)

                "definethrow" ->
                    return tr.type == JMLTransition.TRANS_THROW

                "changetocatch" ->
                    return tr.type == JMLTransition.TRANS_SOFTCATCH
                        || tr.type == JMLTransition.TRANS_GRABCATCH

                "changetosoftcatch" ->
                    return tr.type == JMLTransition.TRANS_CATCH
                        || tr.type == JMLTransition.TRANS_GRABCATCH

                "changetograbcatch" ->
                    return tr.type == JMLTransition.TRANS_CATCH
                        || tr.type == JMLTransition.TRANS_SOFTCATCH
            }
        } else if (laditem.type == LadderItem.TYPE_POSITION) {
            return !mutableListOf(
                "addeventtoleft",
                "addeventtoright",
                "removeevent",
                "addposition",
                "defineprop",
                "definethrow",
                "changetocatch",
                "changetosoftcatch",
                "changetograbcatch",
                "makelast"
            ).contains(command)
        } else {  // LadderPathItem
            return !mutableListOf(
                "removeevent",
                "removeposition",
                "definethrow",
                "changetocatch",
                "changetosoftcatch",
                "changetograbcatch",
                "makelast"
            ).contains(command)
        }
        return true
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

        // minimum time (seconds) between a throw and another event with transitions
        private const val MIN_THROW_SEP_TIME: Double = 0.05

        // minimum time (seconds) between all events for a hand
        private const val MIN_EVENT_SEP_TIME: Double = 0.01

        // minimum time (seconds) between positions for a juggler
        private const val MIN_POSITION_SEP_TIME: Double = 0.02

        private val COLOR_SELECTION: Color? = Color.green

        // additional GUI states
        private const val STATE_MOVING_EVENT: Int = 2
        private const val STATE_MOVING_POSITION: Int = 3
        private const val STATE_POPUP: Int = 4

        //----------------------------------------------------------------------
        // Popup menu and related handlers
        //----------------------------------------------------------------------

        private val popupItems: List<String?> = listOf(
            "Add event to L hand",
            "Add event to R hand",
            "Remove event",
            "Add position to juggler",
            "Remove position",
            null,
            "Define prop...",
            "Define throw...",
            "Change to normal catch",
            "Change to soft catch",
            "Change to grab catch",
            "Make last in event",
        )
        private val popupItemsStringResources: List<StringResource?> = listOf(
            Res.string.gui_add_event_to_l_hand,
            Res.string.gui_add_event_to_r_hand,
            Res.string.gui_remove_event,
            Res.string.gui_add_position_to_juggler,
            Res.string.gui_remove_position,
            null,
            Res.string.gui_define_prop,
            Res.string.gui_define_throw,
            Res.string.gui_change_to_normal_catch,
            Res.string.gui_change_to_soft_catch,
            Res.string.gui_change_to_grab_catch,
            Res.string.gui_make_last_in_event,
        )

        private val popupCommands: List<String?> = listOf(
            "addeventtoleft",
            "addeventtoright",
            "removeevent",
            "addposition",
            "removeposition",
            null,
            "defineprop",
            "definethrow",
            "changetocatch",
            "changetosoftcatch",
            "changetograbcatch",
            "makelast",
        )
    }
}
