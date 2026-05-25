//
// LadderDiagramController.kt
//
// Controller for the ladder diagram. Handles mouse events and updates the
// pattern as needed. The callback `onMakePopup` is invoked to create a popup
// menu for certain kinds of user interactions.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.jml.*
import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlHandleFatalException
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.*
import org.jetbrains.compose.resources.StringResource
import kotlin.math.*

class LadderDiagramController(
    val state: PatternAnimationState,
    val onMakePopup: ((LadderItem?, Int, Int) -> Unit)? = null
) {
    // UI state for Compose
    var uiState by mutableStateOf(LadderDiagramUiState())
        private set

    // layout is for interpreting mouse events
    var currentLayout: LadderDiagramLayout? = null
        private set
    var currentDensity: Float = 1.0f
        private set

    // ui state variables
    private var guiState: Int = STATE_INACTIVE
    private var animWasPaused: Boolean? = null  // retained pause state
    private var itemWasSelected: Boolean = false  // for detecting de-selecting clicks

    // for event dragging
    private var startY: Int = 0  // initial box y-coordinates
    private var startYLow: Int = 0
    private var startYHigh: Int = 0
    private var startT: Double = 0.0  // initial time
    private var deltaY: Int = 0  // amount of drag in progress, in pixels
    private var deltaYMin: Int = 0  // limits for dragging up/down
    private var deltaYMax: Int = 0

    // for popup menu
    var popupItem: LadderItem? = null
        private set
    private var popupX: Int = 0 // screen coordinates where popup was raised
    private var popupY: Int = 0

    fun onLayoutUpdate(layout: LadderDiagramLayout?) {
        currentLayout = layout
        currentDensity = layout?.density ?: 1.0f
    }

    //--------------------------------------------------------------------------
    // Mouse event handlers
    //--------------------------------------------------------------------------

    fun handlePress(
        offset: Offset,
        isPopup: Boolean,
        ignoreEmptySpace: Boolean = false,
        screenOffset: Offset = offset
    ): Boolean {
        val mx = offset.x.toInt()
        val my = offset.y.toInt()
        val sx = screenOffset.x.toInt()
        val sy = screenOffset.y.toInt()
        return mousePressedLogic(mx, my, sx, sy, isPopup, ignoreEmptySpace)
    }

    fun handleDrag(offset: Offset) {
        val mx = offset.x.toInt()
        val my = offset.y.toInt()
        mouseDraggedLogic(mx, my)
    }

    fun handleRelease() {
        mouseReleasedLogic()
    }

    //--------------------------------------------------------------------------
    // Mouse internal logic
    //--------------------------------------------------------------------------

    private fun mousePressedLogic(
        mx: Int,
        my: Int,
        sx: Int,
        sy: Int,
        isPopup: Boolean,
        ignoreEmptySpace: Boolean
    ): Boolean {
        try {
            val layout = currentLayout ?: return false
            if (layout.pattern !== state.pattern) return false
            val myClamped = min(max(my, layout.borderTop), layout.height - layout.borderTop)

            if (isPopup) {
                guiState = STATE_POPUP
                popupItem = getSelectedLadderEvent(mx, my) ?: getSelectedLadderPosition(mx, my)
                    ?: getSelectedLadderPath(mx, my, (PATH_SLOP_DP * currentDensity).toInt())
                popupX = mx
                popupY = myClamped

                animWasPaused = animWasPaused ?: state.isPaused
                val newTime = layout.yToTime(myClamped)
                val code = popupItem?.jlHashCode ?: 0
                state.update(time = newTime, isPaused = true, selectedItemHashCode = code)

                onMakePopup?.invoke(popupItem, sx, sy)
                return true
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
                            startY = myClamped
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
                            startY = myClamped
                            startYLow = newActivePositionItem.yLow
                            startYHigh = newActivePositionItem.yHigh
                            startT = newActivePositionItem.position.t
                            findPositionLimits(newActivePositionItem)
                            state.update(selectedItemHashCode = newActivePositionItem.jlHashCode)
                            needsHandling = false
                        }
                    }

                    if (needsHandling) {
                        if (ignoreEmptySpace) {
                            return false
                        }
                        guiState = STATE_MOVING_TRACKER
                        val newTime = layout.yToTime(myClamped)
                        animWasPaused = animWasPaused ?: state.isPaused
                        state.update(isPaused = true, selectedItemHashCode = 0)
                        state.update(time = newTime)
                        return true
                    }
                    return true
                }

                STATE_MOVING_EVENT -> {}
                STATE_MOVING_POSITION -> {}
                STATE_MOVING_TRACKER -> {}
                STATE_POPUP -> finishPopup()
            }
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
        return false
    }

    @Suppress("unused")
    private fun mouseDraggedLogic(mx: Int, my: Int) {
        try {
            val layout = currentLayout ?: return
            if (layout.pattern !== state.pattern) return
            val myClamped = min(max(my, layout.borderTop), layout.height - layout.borderTop)

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
                    animWasPaused = null
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
    // Utility methods for pointer interactions
    //--------------------------------------------------------------------------

    private fun findEventLimits(item: LadderEventItem) {
        val layout = currentLayout ?: return
        var tMin = state.pattern.loopStartTime
        var tMax = state.pattern.loopEndTime
        val evPaths = item.event.transitions.filter { it.isThrowOrCatch }.map { it.path }.toList()

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

    private fun getClippedEventTime(item: LadderEventItem, my: Int): Int {
        val layout = currentLayout ?: return 0
        val dy = min(max(my - startY, deltaYMin), deltaYMax)
        val scale = (state.pattern.loopEndTime - state.pattern.loopStartTime) /
            (layout.height - 2 * layout.borderTop).toDouble()
        val newT = startT + dy * scale  // unclipped new event time

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

        val newEvent = item.event.copy(t = newT)

        val record = PatternBuilder.fromJmlPattern(state.pattern)
        val index = record.events.indexOf(item.primary)
        if (index < 0) throw JuggleExceptionInternal("Error in LadderDiagram.moveEventInPattern()")
        if (item.event === item.primary) {
            record.events[index] = newEvent
        } else {
            val newPrimaryT = newT + item.primary.t - item.event.t
            val newEventPrimary = item.primary.copy(t = newPrimaryT)
            record.events[index] = newEventPrimary
        }

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
            pattern = JmlPattern.fromPatternBuilder(record),
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

    private fun getClippedPositionTime(my: Int, position: JmlPosition): Int {
        val layout = currentLayout ?: return 0
        var dy = my - startY
        dy = min(max(dy, deltaYMin), deltaYMax)

        val scale = (state.pattern.loopEndTime - state.pattern.loopStartTime) /
            (layout.height - 2 * layout.borderTop).toDouble()
        val shift = dy * scale
        val newT = startT + shift  // unclipped new event time

        var tExclMin = newT
        var tExclMax = newT
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

        val exclDyMin = floor((tExclMin - startT) / scale).toInt()
        val exclDyMax = ceil((tExclMax - startT) / scale).toInt()
        val feasibleMin = (exclDyMin in deltaYMin..deltaYMax)
        val feasibleMax = (exclDyMax in deltaYMin..deltaYMax)

        var resultDy = dy

        if (feasibleMin && feasibleMax) {
            val tMidpoint = 0.5 * (tExclMin + tExclMax)
            resultDy = (if (newT <= tMidpoint) exclDyMin else exclDyMax)
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

        val rec = PatternBuilder.fromJmlPattern(state.pattern)
        val index = rec.positions.indexOf(pos)
        if (index < 0) throw JuggleExceptionInternal("Error in LDC.movePositionInPattern()")
        val newPosition = pos.copy(t = newT)
        rec.positions[index] = newPosition
        state.update(
            pattern = JmlPattern.fromPatternBuilder(rec),
            selectedItemHashCode = newPosition.jlHashCode
        )
    }

    // Call this at the very end of every popup interaction.

    fun finishPopup() {
        popupItem = null
        if (guiState == STATE_POPUP) {
            guiState = STATE_INACTIVE
            state.update(isPaused = animWasPaused)
            animWasPaused = null
        }
    }

    //--------------------------------------------------------------------------
    // Compose popup menu and follow-up dialogs
    //--------------------------------------------------------------------------

    fun onShowMenu() {
        uiState = uiState.copy(activeDialog = LadderDiagramDialog.Menu)
    }

    fun onDismissDialog() {
        uiState = uiState.copy(activeDialog = null)
        finishPopup()
    }

    fun onMenuAction(command: String) {
        when (command) {
            "defineprop" -> showDefinePropDialog()
            "definethrow" -> showDefineThrowDialog()
            else -> {
                // commands that don't need dialog interactions
                performCommand(command)
                onDismissDialog()
            }
        }
    }

    private fun showDefinePropDialog() {
        val item = popupItem ?: return
        val pathNum = when (item) {
            is LadderEventItem -> {
                val ev = item.event
                val transnum = item.transNum
                val tr = ev.transitions[transnum]
                tr.path
            }

            is LadderPathItem -> {
                item.pathNum
            }

            else -> {
                return
            }
        }

        val animpropnum = state.propForPath
        val propnum = animpropnum[pathNum - 1]
        val startprop = state.pattern.props[propnum - 1]

        uiState = uiState.copy(
            activeDialog = LadderDiagramDialog.DefineProp(
                currentType = startprop.type,
                currentMod = startprop.mod,
                pathNum = pathNum
            )
        )
    }

    private fun showDefineThrowDialog() {
        val item = popupItem as? LadderEventItem ?: return
        val evPrimary = item.primary
        val tr = evPrimary.transitions[item.transNum]

        uiState = uiState.copy(
            activeDialog = LadderDiagramDialog.DefineThrow(
                currentType = tr.throwType ?: "Toss",
                currentMod = tr.throwMod,
                eventItem = item
            )
        )
    }

    fun applyPropDefinition(pathNum: Int, propType: String, propMod: String?) {
        try {
            val animPropnum = state.propForPath
            val propNum = animPropnum[pathNum - 1]

            // this is to throw an exception if the requested prop is invalid:
            JmlProp(propType.lowercase(), propMod).prop.isColorable

            val rec = PatternBuilder.fromJmlPattern(state.pattern)

            // sync paths
            for (i in 0..<rec.numberOfPaths) {
                rec.propAssignment[i] = animPropnum[i]
            }

            // clean up old prop if unused
            var killprop = true
            for (i in 0..<rec.numberOfPaths) {
                if (i != pathNum - 1) {
                    if (animPropnum[i] == propNum) {
                        killprop = false
                        break
                    }
                }
            }

            if (killprop) {
                rec.props.removeAt(propNum - 1)
                for (i in 0..<rec.numberOfPaths) {
                    if (rec.propAssignment[i] > propNum) {
                        rec.propAssignment[i] = rec.propAssignment[i] - 1
                    }
                }
            }

            // check for duplicate
            var gotmatch = false
            var matchingprop = 0
            for (i in 1..rec.props.size) {
                val pdef = rec.props[i - 1]
                if (propType.equals(pdef.type, ignoreCase = true)) {
                    if ((propMod == null && pdef.mod == null)
                        || (propMod != null && propMod.equals(pdef.mod, ignoreCase = true))
                    ) {
                        gotmatch = true
                        matchingprop = i
                        break
                    }
                }
            }

            if (gotmatch) {
                rec.propAssignment[pathNum - 1] = matchingprop
            } else {
                val newprop = JmlProp(propType.lowercase(), propMod)
                rec.props.add(newprop)
                rec.propAssignment[pathNum - 1] = rec.props.size
            }

            val newPattern = JmlPattern.fromPatternBuilder(rec)
            state.update(
                pattern = newPattern,
                propForPath = newPattern.initialPropForPath
            )
            state.addCurrentToUndoList()
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
        onDismissDialog()
    }

    fun applyThrowDefinition(item: LadderEventItem, type: String, mod: String?) {
        try {
            val evPrimary = item.primary
            val tr = evPrimary.transitions[item.transNum]

            val newTransition = tr.copy(
                throwType = type.lowercase(),
                throwMod = mod
            )
            val newPrimary = evPrimary.copy(
                transitions = evPrimary.transitions.toMutableList().apply {
                    this[item.transNum] = newTransition
                }
            )
            val record = PatternBuilder.fromJmlPattern(state.pattern)
            val index = record.events.indexOf(evPrimary)
            record.events[index] = newPrimary
            val newPattern = JmlPattern.fromPatternBuilder(record)
            state.update(pattern = newPattern)
            state.addCurrentToUndoList()
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
        onDismissDialog()
    }

    //--------------------------------------------------------------------------
    // Handle popup commands that don't require dialogs
    //--------------------------------------------------------------------------

    fun performCommand(command: String) {
        try {
            when (command) {
                "addeventtoleft" -> addEventToHand(JmlEvent.LEFT_HAND)
                "addeventtoright" -> addEventToHand(JmlEvent.RIGHT_HAND)
                "removeevent" -> removeEvent()
                "addposition" -> addPositionToJuggler()
                "removeposition" -> removePosition()
                "changetocatch" -> changeCatchStyleTo(JmlTransition.TRANS_CATCH)
                "changetosoftcatch" -> changeCatchStyleTo(JmlTransition.TRANS_SOFTCATCH)
                "changetograbcatch" -> changeCatchStyleTo(JmlTransition.TRANS_GRABCATCH)
                "makelast" -> makeLastInEvent()
                else -> throw JuggleExceptionInternal("unknown item in LDC popup")
            }
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
        val newEvent = JmlEvent(
            x = newLocalCoordinate.x,
            y = newLocalCoordinate.y,
            z = newLocalCoordinate.z,
            t = newTime,
            juggler = juggler,
            hand = hand
        )

        val record = PatternBuilder.fromJmlPattern(state.pattern).apply {
            events.add(newEvent)
            fixHolds()
            selectPrimaryEvents()
        }
        try {
            val newPattern = JmlPattern.fromPatternBuilder(record).apply {
                assertValid()
            }
            state.update(
                pattern = newPattern,
                selectedItemHashCode = newEvent.jlHashCode
            )
            state.addCurrentToUndoList()
        } catch (_: JuggleExceptionUser) {
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun removeEvent() {
        if (popupItem !is LadderEventItem) {
            throw JuggleExceptionInternal("LadderDiagram illegal remove event")
        }
        val evRemove = (popupItem as LadderEventItem).primary
        val record = PatternBuilder.fromJmlPattern(state.pattern)
        record.events.remove(evRemove)
        val newPattern = JmlPattern.fromPatternBuilder(record)
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

        val newTime = run {
            val time = layout.yToTime(popupY)
            if (time == state.pattern.loopEndTime) state.pattern.loopStartTime else time
        }

        val newGlobalCoordinate = Coordinate()
        state.pattern.layout.getJugglerPosition(juggler, newTime, newGlobalCoordinate)
        val pos = JmlPosition(
            x = newGlobalCoordinate.x,
            y = newGlobalCoordinate.y,
            z = newGlobalCoordinate.z,
            t = newTime,
            angle = state.pattern.layout.getJugglerAngle(juggler, newTime),
            juggler = juggler
        )
        val rec = PatternBuilder.fromJmlPattern(state.pattern).apply {
            positions.add(pos)
        }
        try {
            val newPattern = JmlPattern.fromPatternBuilder(rec).apply {
                assertValid()
            }
            state.update(
                pattern = newPattern,
                selectedItemHashCode = pos.jlHashCode
            )
            state.addCurrentToUndoList()
        } catch (_: JuggleExceptionUser) {
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun removePosition() {
        if (popupItem !is LadderPositionItem) {
            throw JuggleExceptionInternal("LadderDiagram illegal remove position")
        }
        val pos = (popupItem as LadderPositionItem).position
        val rec = PatternBuilder.fromJmlPattern(state.pattern).apply {
            positions.remove(pos)
        }
        val newPattern = JmlPattern.fromPatternBuilder(rec)
        state.update(
            pattern = newPattern,
            selectedItemHashCode = 0
        )
        state.addCurrentToUndoList()
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

        val record = PatternBuilder.fromJmlPattern(state.pattern)
        val index = record.events.indexOf(evPrimary)
        record.events[index] = newPrimary
        val code = (popupItem as LadderEventItem).event.jlHashCode + 23 +
            (popupItem as LadderEventItem).transNum * 27
        val newPattern = JmlPattern.fromPatternBuilder(record)
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

        val record = PatternBuilder.fromJmlPattern(state.pattern)
        val index = record.events.indexOf(evPrimary)
        record.events[index] = newPrimary
        val code = (popupItem as LadderEventItem).event.jlHashCode + 23 +
            (newPrimary.transitions.size - 1) * 27
        val newPattern = JmlPattern.fromPatternBuilder(record)
        state.update(
            pattern = newPattern,
            selectedItemHashCode = code
        )
        state.addCurrentToUndoList()
    }

    // Determine whether a command is enabled for a particular LadderItem

    fun isCommandEnabled(ladderItem: LadderItem?, command: String?): Boolean {
        val layout = currentLayout ?: return false
        if (ladderItem == null) {
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
        } else if (ladderItem.type == LadderItem.TYPE_EVENT) {
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
                val evitem = ladderItem as LadderEventItem
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
        } else if (ladderItem.type == LadderItem.TYPE_TRANSITION) {
            if (listOf(
                    "addeventtoleft",
                    "addeventtoright",
                    "addposition",
                    "removeposition",
                    "removeevent"
                ).contains(command)
            ) return false

            val evitem = ladderItem as LadderEventItem
            val tr = evitem.event.transitions[evitem.transNum]

            when (command) {
                "makelast" ->
                    return evitem.transNum != (evitem.event.transitions.size - 1)

                "definethrow" ->
                    return tr.type == JmlTransition.TRANS_THROW

                "changetocatch" ->
                    return tr.type == JmlTransition.TRANS_SOFTCATCH
                        || tr.type == JmlTransition.TRANS_GRABCATCH

                "changetosoftcatch" ->
                    return tr.type == JmlTransition.TRANS_CATCH
                        || tr.type == JmlTransition.TRANS_GRABCATCH

                "changetograbcatch" ->
                    return tr.type == JmlTransition.TRANS_CATCH
                        || tr.type == JmlTransition.TRANS_SOFTCATCH
            }
        } else if (ladderItem.type == LadderItem.TYPE_POSITION) {
            return !listOf(
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
            return !listOf(
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
        // geometric constants in pixels
        const val PATH_SLOP_DP: Int = 5

        // minimum time (seconds) between a throw and another event with transitions
        private const val MIN_THROW_SEP_TIME: Double = 0.03

        // minimum time (seconds) between all events for a hand
        private const val MIN_EVENT_SEP_TIME: Double = 0.01

        // minimum time (seconds) between positions for a juggler
        private const val MIN_POSITION_SEP_TIME: Double = 0.02

        // popup items and commands
        val popupItemsStringResources: List<StringResource?> = listOf(
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
        val popupCommands: List<String?> = listOf(
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

        // GUI states
        private const val STATE_INACTIVE: Int = 0
        private const val STATE_MOVING_TRACKER: Int = 1
        private const val STATE_MOVING_EVENT: Int = 2
        private const val STATE_MOVING_POSITION: Int = 3
        private const val STATE_POPUP: Int = 4
    }
}

data class LadderDiagramUiState(
    val activeDialog: LadderDiagramDialog? = null,
)

sealed class LadderDiagramDialog {
    data object Menu : LadderDiagramDialog()

    data class DefineProp(
        val currentType: String,
        val currentMod: String?,
        val pathNum: Int
    ) : LadderDiagramDialog()

    data class DefineThrow(
        val currentType: String,
        val currentMod: String?,
        val eventItem: LadderEventItem
    ) : LadderDiagramDialog()
}

