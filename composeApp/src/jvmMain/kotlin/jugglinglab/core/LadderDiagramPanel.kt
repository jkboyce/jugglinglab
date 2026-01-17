//
// LadderDiagramPanel.kt
//
// This class draws the vertical ladder diagram on the right side of Edit view.
// This includes mouse interaction and editing functions.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.jml.*
import jugglinglab.path.Path
import jugglinglab.path.Path.Companion.newPath
import jugglinglab.prop.Prop
import jugglinglab.prop.Prop.Companion.newProp
import jugglinglab.ui.*
import jugglinglab.util.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import org.jetbrains.compose.resources.StringResource
import java.awt.*
import java.awt.event.*
import java.net.MalformedURLException
import java.util.Locale
import javax.swing.*
import javax.swing.border.BevelBorder
import javax.swing.event.CaretEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.*

class LadderDiagramPanel(
    val state: PatternAnimationState,
    val parentFrame: JFrame?
) : JPanel(), ActionListener {
    private val composePanel = ComposePanel()
    private var currentLayout: LadderDiagramLayout? = null
    private var currentDensity: Float = 1.0f

    private var guiState: Int = STATE_INACTIVE
    private var trackerY: Int = LadderDiagramLayout.BORDER_TOP_DP

    private var animWasPaused: Boolean = false

    // mouse interaction items below

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

    init {
        layout = BorderLayout()
        add(composePanel, BorderLayout.CENTER)

        composePanel.setContent {
            BoxWithConstraints {
                val widthPx = constraints.maxWidth
                val heightPx = constraints.maxHeight
                val density = LocalDensity.current.density

                val cachedLayout = currentLayout
                val layout = if (cachedLayout != null &&
                    cachedLayout.pattern === state.pattern &&
                    cachedLayout.width == widthPx &&
                    cachedLayout.height == heightPx
                ) {
                    cachedLayout
                } else {
                    // just in case the onPatternChange listener hasn't
                    // fired yet (can this happen?)
                    LadderDiagramLayout(state.pattern, widthPx, heightPx, density)
                }

                SideEffect {
                    currentDensity = density
                    currentLayout = layout
                }

                LadderDiagramView(
                    layout = layout,
                    state = state,
                    onPress = { offset, isPopup -> handlePress(offset, isPopup) },
                    onDrag = { offset -> handleDrag(offset) },
                    onRelease = { handleRelease() }
                )
            }
        }

        changeLadderPattern()

        state.addListener(onPatternChange = {
            changeLadderPattern()
        })
    }

    //--------------------------------------------------------------------------
    // Methods to respond to state changes
    //--------------------------------------------------------------------------

    // Respond to a change in the pattern.

    private fun changeLadderPattern() {
        val jugglers = state.pattern.numberOfJugglers
        if (jugglers > MAX_JUGGLERS) {
            // allocate enough space for a "too many jugglers" message
            val message = jlGetStringResource(Res.string.gui_too_many_jugglers, MAX_JUGGLERS)
            val mwidth = 20 + getFontMetrics(MSGFONT).stringWidth(message)
            preferredSize = Dimension(mwidth, 1)
            minimumSize = Dimension(mwidth, 1)
            currentLayout = null
        } else {
            var prefWidth: Int = LADDER_WIDTH_PER_JUGGLER * jugglers
            val minWidth: Int = LADDER_MIN_WIDTH_PER_JUGGLER * jugglers
            val widthMult = doubleArrayOf(1.0, 1.0, 0.85, 0.72, 0.65, 0.55)
            prefWidth = (prefWidth.toDouble() *
                (if (jugglers >= widthMult.size) 0.5 else widthMult[jugglers])).toInt()
            prefWidth = max(prefWidth, minWidth)
            preferredSize = Dimension(prefWidth, 1)
            minimumSize = Dimension(minWidth, 1)

            if (width > 0 && height > 0) {
                currentLayout = LadderDiagramLayout(
                    state.pattern,
                    (width * currentDensity).toInt(),
                    (height * currentDensity).toInt(),
                    currentDensity
                )
            }
        }
    }

    //--------------------------------------------------------------------------
    // Mouse event handlers
    //--------------------------------------------------------------------------

    private fun handlePress(offset: Offset, isPopup: Boolean) {
        val mx = offset.x.toInt()
        val my = offset.y.toInt()
        mousePressedLogic(mx, my, isPopup)
    }

    private fun handleDrag(offset: Offset) {
        val mx = offset.x.toInt()
        val my = offset.y.toInt()
        mouseDraggedLogic(mx, my)
    }

    private fun handleRelease() {
        mouseReleasedLogic()
    }

    //--------------------------------------------------------------------------
    // Mouse internal logic
    //--------------------------------------------------------------------------

    private fun mousePressedLogic(mx: Int, my: Int, isPopup: Boolean) {
        try {
            val layout = currentLayout ?: return

            if (isPopup) {
                guiState = STATE_POPUP
                popupItem = getSelectedLadderEvent(mx, my) ?: getSelectedLadderPosition(mx, my)
                    ?: getSelectedLadderPath(mx, my, (PATH_SLOP_DP * currentDensity).toInt())
                popupX = mx
                popupY = my

                animWasPaused = state.isPaused
                val newTime = layout.yToTime(my)
                val code = popupItem?.jlHashCode ?: 0
                state.update(time = newTime, isPaused = true, selectedItemHashCode = code)

                makePopupMenu(popupItem).show(composePanel, (mx / currentDensity).toInt(), (my / currentDensity).toInt())
                return
            }

            when (guiState) {
                STATE_INACTIVE -> {
                    var needsHandling = true
                    itemWasSelected = false
                    val oldActiveLadderItem = activeLadderItem()

                    val newActiveEventItem = getSelectedLadderEvent(mx, my)
                    if (newActiveEventItem != null) {
                        if (oldActiveLadderItem === newActiveEventItem) {
                            itemWasSelected = true
                        }
                        state.update(selectedItemHashCode = newActiveEventItem.jlHashCode)
                        if (newActiveEventItem.type == LadderItem.TYPE_TRANSITION) {
                            // only allow dragging of TYPE_EVENT
                            needsHandling = false
                        }

                        if (needsHandling) {
                            guiState = STATE_MOVING_EVENT
                            startY = my
                            startYLow = newActiveEventItem.yLow
                            startYHigh = newActiveEventItem.yHigh
                            startT = newActiveEventItem.event.t
                            findEventLimits(newActiveEventItem)
                            needsHandling = false
                        }
                    }

                    if (needsHandling) {
                        val newActivePositionItem = getSelectedLadderPosition(mx, my)
                        if (newActivePositionItem != null) {
                            if (oldActiveLadderItem === newActivePositionItem) {
                                itemWasSelected = true
                            }
                            guiState = STATE_MOVING_POSITION
                            startY = my
                            startYLow = newActivePositionItem.yLow
                            startYHigh = newActivePositionItem.yHigh
                            startT = newActivePositionItem.position.t
                            findPositionLimits(newActivePositionItem)
                            state.update(selectedItemHashCode = newActivePositionItem.jlHashCode)
                            needsHandling = false
                        }
                    }

                    if (needsHandling) {
                        guiState = STATE_MOVING_TRACKER
                        trackerY = my
                        val newTime = layout.yToTime(my)
                        animWasPaused = state.isPaused
                        state.update(isPaused = true, time = newTime, selectedItemHashCode = 0)
                    }
                }

                STATE_MOVING_EVENT -> {}
                STATE_MOVING_POSITION -> {}
                STATE_MOVING_TRACKER -> {}
                STATE_POPUP -> finishPopup()
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    @Suppress("unused")
    private fun mouseDraggedLogic(mx: Int, my: Int) {
        try {
            val layout = currentLayout ?: return
            val ladderHeight = layout.height
            val myClamped = min(max(my, layout.borderTop), ladderHeight - layout.borderTop)

            when (guiState) {
                STATE_INACTIVE, STATE_POPUP -> {}
                STATE_MOVING_EVENT -> {
                    val activeEventItem = activeLadderItem() as LadderEventItem
                    val oldDeltaY = deltaY
                    deltaY = getClippedEventTime(activeEventItem, myClamped)
                    if (deltaY != oldDeltaY) {
                        moveEventInPattern(activeEventItem.transEventItem!!)
                    }
                }

                STATE_MOVING_POSITION -> {
                    val activePositionItem = activeLadderItem() as LadderPositionItem
                    val oldDeltaY = deltaY
                    deltaY = getClippedPositionTime(myClamped, activePositionItem.position)
                    if (deltaY != oldDeltaY) {
                        movePositionInPattern(activePositionItem)
                    }
                }

                STATE_MOVING_TRACKER -> {
                    trackerY = myClamped
                    val newTime = layout.yToTime(myClamped)
                    state.update(time = newTime)
                }
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    private fun mouseReleasedLogic() {
        try {
            when (guiState) {
                STATE_INACTIVE -> {}
                STATE_MOVING_EVENT -> {
                    guiState = STATE_INACTIVE
                    if (deltaY != 0) {
                        deltaY = 0
                        state.addCurrentToUndoList()
                    } else if (itemWasSelected) {
                        state.update(selectedItemHashCode = 0)
                    }
                }

                STATE_MOVING_POSITION -> {
                    guiState = STATE_INACTIVE
                    if (deltaY != 0) {
                        deltaY = 0
                        state.addCurrentToUndoList()
                    } else if (itemWasSelected) {
                        state.update(selectedItemHashCode = 0)
                    }
                }

                STATE_MOVING_TRACKER -> {
                    guiState = STATE_INACTIVE
                    state.update(isPaused = animWasPaused)
                }

                STATE_POPUP -> {}
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    //--------------------------------------------------------------------------
    // Methods to find ladder items
    //--------------------------------------------------------------------------

    // Return the currently selected item.

    private fun activeLadderItem(): LadderItem? {
        val layout = currentLayout ?: return null
        val activeItemHash = state.selectedItemHashCode

        for (item in layout.eventItems) {
            if (item.jlHashCode == activeItemHash) {
                return item
            }
        }
        for (item in layout.positionItems) {
            if (item.jlHashCode == activeItemHash) {
                return item
            }
        }
        return null
    }

    private fun getSelectedLadderEvent(x: Int, y: Int): LadderEventItem? {
        val layout = currentLayout ?: return null
        for (item in layout.eventItems) {
            if (x >= item.xLow && x <= item.xHigh && y >= item.yLow && y <= item.yHigh) {
                return item
            }
        }
        return null
    }

    private fun getSelectedLadderPosition(x: Int, y: Int): LadderPositionItem? {
        val layout = currentLayout ?: return null
        for (item in layout.positionItems) {
            if (x >= item.xLow && x <= item.xHigh && y >= item.yLow && y <= item.yHigh) {
                return item
            }
        }
        return null
    }

    @Suppress("SameParameterValue")
    private fun getSelectedLadderPath(x: Int, y: Int, slop: Int): LadderPathItem? {
        val layout = currentLayout ?: return null
        var result: LadderPathItem? = null
        var dmin = 0.0

        if (y < (layout.borderTop - slop) || y > (layout.height - layout.borderTop + slop)) {
            return null
        }

        for (item in layout.pathItems) {
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

    //--------------------------------------------------------------------------
    // Utility methods for mouse interactions
    //--------------------------------------------------------------------------

    // Set `deltaYMin` and `deltaYMax` for a selected event, determining the
    // number of pixels it is allowed to move up or down.

    private fun findEventLimits(item: LadderEventItem) {
        val layout = currentLayout ?: return
        var tMin = state.pattern.loopStartTime
        var tMax = state.pattern.loopEndTime
        val evPaths = item.event.transitions.filter { it.isThrowOrCatch }.map { it.path }.toList()

        // other events with throws/catches using the same paths define the
        // limits of how far the item can move

        state.pattern.allEvents
            .filter { it.primary != item.primary }
            .filter { it.event.transitions.any { tr -> tr.isThrowOrCatch && tr.path in evPaths } }
            .forEach {
                if (it.event.t < item.event.t - MIN_EVENT_SEP_TIME) {
                    tMin = max(tMin, it.event.t + MIN_THROW_SEP_TIME)
                } else if (it.event.t > item.event.t + MIN_THROW_SEP_TIME) {
                    tMax = min(tMax, it.event.t - MIN_THROW_SEP_TIME)
                }
            }

        val scale = (state.pattern.loopEndTime - state.pattern.loopStartTime) /
            (layout.height - 2 * layout.borderTop).toDouble()
        deltaYMin = ((tMin - item.event.t) / scale).toInt()
        deltaYMax = ((tMax - item.event.t) / scale).toInt()
    }

    // Return the value of `deltaY` during mouse drag of an event, clipping it to
    // enforce proximity limits between various event types, as well as hard
    // limits `deltaYMin` and `deltaYMax`.

    private fun getClippedEventTime(item: LadderEventItem, my: Int): Int {
        val layout = currentLayout ?: return 0
        val dy = min(max(my - startY, deltaYMin), deltaYMax)
        val scale = (state.pattern.loopEndTime - state.pattern.loopStartTime) /
            (layout.height - 2 * layout.borderTop).toDouble()
        val newT = startT + dy * scale  // unclipped new event time

        // Calculate a window (tExclMin, tExclMax) of excluded times based on
        // proximity to other events, where `newT` is contained within the window.
        var tExclMin = newT
        var tExclMax = newT

        while (true) {
            var changed = false
            state.pattern.allEvents
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
        val layout = currentLayout ?: return
        val scale = (state.pattern.loopEndTime - state.pattern.loopStartTime) /
            (layout.height - 2 * layout.borderTop).toDouble()
        val newT = run {
            val tempT = startT + deltaY * scale
            if (tempT < state.pattern.loopStartTime + scale) {
                state.pattern.loopStartTime  // within 1 pixel of top
            } else if (tempT >= state.pattern.loopEndTime) {
                state.pattern.loopEndTime - 0.0001
            } else {
                tempT
            }
        }

        // new event to swap in for `item.event`
        val newEvent = item.event.copy(t = newT)

        val record = PatternBuilder.fromJMLPattern(state.pattern)
        val index = record.events.indexOf(item.primary)
        if (index < 0) throw JuggleExceptionInternal("Error in LadderDiagram.moveEventInPattern()")
        if (item.event === item.primary) {
            record.events[index] = newEvent
        } else {
            // make the change in the primary event
            val newPrimaryT = newT + item.primary.t - item.event.t
            val newEventPrimary = item.primary.copy(t = newPrimaryT)
            record.events[index] = newEventPrimary
        }

        // did we just hop over another event in the same hand? if so then fix
        // the HOLDING transitions
        val needFixHolds = layout.eventItems.any {
            (it.event.t - item.event.t) * (it.event.t - newT) < 0.0 &&
                it.event.juggler == item.event.juggler &&
                it.event.hand == item.event.hand
        }
        if (needFixHolds) {
            record.fixHolds()
        }

        record.selectPrimaryEvents()
        state.update(
            pattern = JMLPattern.fromPatternBuilder(record),
            selectedItemHashCode = newEvent.jlHashCode
        )
    }

    private fun findPositionLimits(item: LadderPositionItem) {
        val layout = currentLayout ?: return
        val tmin = state.pattern.loopStartTime
        val tmax = state.pattern.loopEndTime
        val scale = (state.pattern.loopEndTime - state.pattern.loopStartTime) /
            (layout.height - 2 * layout.borderTop).toDouble()

        deltaYMin = ((tmin - item.position.t) / scale).toInt()
        deltaYMax = ((tmax - item.position.t) / scale).toInt()
    }

    // Return value of `delta_y` during mouse drag of an event, clipping it to
    // enforce proximity limits between various event types, as well as hard
    // limits `delta_y_min` and `delta_y_max`.

    private fun getClippedPositionTime(my: Int, position: JMLPosition): Int {
        val layout = currentLayout ?: return 0
        var dy = my - startY
        dy = min(max(dy, deltaYMin), deltaYMax)

        val scale = (state.pattern.loopEndTime - state.pattern.loopStartTime) /
            (layout.height - 2 * layout.borderTop).toDouble()
        val shift = dy * scale
        val newt = startT + shift // unclipped new event time

        // Calculate a window (t_excl_min, t_excl_max) of excluded times based on
        // proximity to other events, where `newt` is contained within the window.
        var tExclMin = newt
        var tExclMax = newt
        var changed: Boolean

        do {
            changed = false

            for (pos in state.pattern.positions) {
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
        val layout = currentLayout ?: return
        val pos = item.position
        val scale = (state.pattern.loopEndTime - state.pattern.loopStartTime) /
            (layout.height - 2 * layout.borderTop).toDouble()

        var newT = startT + deltaY * scale
        if (newT < state.pattern.loopStartTime + scale) {
            newT = state.pattern.loopStartTime // within 1 pixel of top
        } else if (newT >= state.pattern.loopEndTime) {
            newT = state.pattern.loopEndTime - 0.0001
        }

        val rec = PatternBuilder.fromJMLPattern(state.pattern)
        val index = rec.positions.indexOf(pos)
        if (index < 0) throw JuggleExceptionInternal("Error in ELD.movePositionInPattern()")
        val newPosition = pos.copy(t = newT)
        rec.positions[index] = newPosition
        state.update(
            pattern = JMLPattern.fromPatternBuilder(rec),
            selectedItemHashCode = newPosition.jlHashCode
        )
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
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun addEventToHand(hand: Int) {
        val layout = currentLayout ?: return
        val juggler = run {
            var jug = 1
            if (state.pattern.numberOfJugglers > 1) {
                var mouseX = popupX
                val jugglerRightPx = (layout.leftX + layout.rightX + layout.jugglerDeltaX) / 2
                while (jug <= state.pattern.numberOfJugglers) {
                    if (mouseX < jugglerRightPx) {
                        break
                    }
                    mouseX -= layout.jugglerDeltaX
                    ++jug
                }
                jug = min(jug, state.pattern.numberOfJugglers)
            }
            jug
        }

        val newTime = layout.yToTime(popupY)
        val newGlobalCoordinate = Coordinate()
        state.pattern.layout.getHandCoordinate(juggler, hand, newTime, newGlobalCoordinate)

        val newLocalCoordinate =
            state.pattern.layout.convertGlobalToLocal(newGlobalCoordinate, juggler, newTime)
        val newEvent = JMLEvent(
            x = newLocalCoordinate.x,
            y = newLocalCoordinate.y,
            z = newLocalCoordinate.z,
            t = newTime,
            juggler = juggler,
            hand = hand
        )

        val record = PatternBuilder.fromJMLPattern(state.pattern)
        record.events.add(newEvent)
        record.fixHolds()
        record.selectPrimaryEvents()
        val newPattern = JMLPattern.fromPatternBuilder(record)
        state.update(
            pattern = newPattern,
            selectedItemHashCode = newEvent.jlHashCode
        )
        state.addCurrentToUndoList()
    }

    @Throws(JuggleExceptionInternal::class)
    private fun removeEvent() {
        // makePopupMenu() ensures that the event only has hold transitions
        if (popupItem !is LadderEventItem) {
            throw JuggleExceptionInternal("LadderDiagram illegal remove event")
        }
        val evRemove = (popupItem as LadderEventItem).primary
        val record = PatternBuilder.fromJMLPattern(state.pattern)
        record.events.remove(evRemove)
        val newPattern = JMLPattern.fromPatternBuilder(record)
        state.update(
            pattern = newPattern,
            selectedItemHashCode = 0
        )
        state.addCurrentToUndoList()
    }

    private fun addPositionToJuggler() {
        val layout = currentLayout ?: return
        val juggler = run {
            var jug = 1
            if (state.pattern.numberOfJugglers > 1) {
                var mouseX = popupX
                val jugglerRightPx = (layout.leftX + layout.rightX + layout.jugglerDeltaX) / 2
                while (jug <= state.pattern.numberOfJugglers) {
                    if (mouseX < jugglerRightPx) {
                        break
                    }
                    mouseX -= layout.jugglerDeltaX
                    ++jug
                }
                jug = min(jug, state.pattern.numberOfJugglers)
            }
            jug
        }

        val newTime = layout.yToTime(popupY)

        val newGlobalCoordinate = Coordinate()
        state.pattern.layout.getJugglerPosition(juggler, newTime, newGlobalCoordinate)
        val pos = JMLPosition(
            x = newGlobalCoordinate.x,
            y = newGlobalCoordinate.y,
            z = newGlobalCoordinate.z,
            t = newTime,
            angle = state.pattern.layout.getJugglerAngle(juggler, newTime),
            juggler = juggler
        )
        val rec = PatternBuilder.fromJMLPattern(state.pattern)
        rec.positions.add(pos)
        val newPattern = JMLPattern.fromPatternBuilder(rec)
        state.update(
            pattern = newPattern,
            selectedItemHashCode = pos.jlHashCode
        )
        state.addCurrentToUndoList()
    }

    @Throws(JuggleExceptionInternal::class)
    private fun removePosition() {
        if (popupItem !is LadderPositionItem) {
            throw JuggleExceptionInternal("LadderDiagram illegal remove position")
        }
        val pos = (popupItem as LadderPositionItem).position
        val rec = PatternBuilder.fromJMLPattern(state.pattern)
        rec.positions.remove(pos)
        val newPattern = JMLPattern.fromPatternBuilder(rec)
        state.update(
            pattern = newPattern,
            selectedItemHashCode = 0
        )
        state.addCurrentToUndoList()
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

        val animpropnum = state.propForPath
        val propnum = animpropnum[pn - 1]
        val startprop = state.pattern.getProp(propnum)
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

            val rec = PatternBuilder.fromJMLPattern(state.pattern)

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

            val newPattern = JMLPattern.fromPatternBuilder(rec)
            state.update(pattern = newPattern)
            state.addCurrentToUndoList()
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
            val record = PatternBuilder.fromJMLPattern(state.pattern)
            val index = record.events.indexOf(evPrimary)
            record.events[index] = newPrimary
            val newPattern = JMLPattern.fromPatternBuilder(record)
            state.update(pattern = newPattern)
            state.addCurrentToUndoList()
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

        val record = PatternBuilder.fromJMLPattern(state.pattern)
        val index = record.events.indexOf(evPrimary)
        record.events[index] = newPrimary
        val code = (popupItem as LadderEventItem).event.jlHashCode + 23 +
            (popupItem as LadderEventItem).transNum * 27
        val newPattern = JMLPattern.fromPatternBuilder(record)
        state.update(
            pattern = newPattern,
            selectedItemHashCode = code
        )
        state.addCurrentToUndoList()
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

        val record = PatternBuilder.fromJMLPattern(state.pattern)
        val index = record.events.indexOf(evPrimary)
        record.events[index] = newPrimary
        val code = (popupItem as LadderEventItem).event.jlHashCode + 23 +
            (newPrimary.transitions.size - 1) * 27
        val newPattern = JMLPattern.fromPatternBuilder(record)
        state.update(
            pattern = newPattern,
            selectedItemHashCode = code
        )
        state.addCurrentToUndoList()
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
                            val result = jlJfc.showOpenDialog(this@LadderDiagramPanel)
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
            state.update(isPaused = animWasPaused)
        }
    }

    // Determine which commands are enabled for a particular LadderItem
    //
    // Returns true for enabled, false for disabled

    private fun isCommandEnabled(laditem: LadderItem?, command: String?): Boolean {
        val layout = currentLayout ?: return false
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
                val anotherEventForHand = layout.eventItems.any {
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
        const val PATH_SLOP_DP: Int = 5

        // minimum time (seconds) between a throw and another event with transitions
        private const val MIN_THROW_SEP_TIME: Double = 0.05

        // minimum time (seconds) between all events for a hand
        private const val MIN_EVENT_SEP_TIME: Double = 0.01

        // minimum time (seconds) between positions for a juggler
        private const val MIN_POSITION_SEP_TIME: Double = 0.02

        // GUI states
        private const val STATE_INACTIVE: Int = 0
        private const val STATE_MOVING_TRACKER: Int = 1
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
