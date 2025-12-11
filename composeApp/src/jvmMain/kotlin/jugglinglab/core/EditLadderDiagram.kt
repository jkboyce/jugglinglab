//
// EditLadderDiagram.kt
//
// This class draws the vertical ladder diagram on the right side of Edit view.
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
import jugglinglab.util.*
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.view.View
import androidx.compose.ui.graphics.toAwtImage
import jugglinglab.jml.JMLEvent.Companion.addTransition
import jugglinglab.jml.JMLEvent.Companion.removeTransition
import org.jetbrains.compose.resources.StringResource
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.MalformedURLException
import java.util.*
import javax.swing.*
import javax.swing.border.BevelBorder
import javax.swing.event.CaretEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class EditLadderDiagram(
    p: JMLPattern,
    val parentFrame: JFrame?,
    val parentView: View
) : LadderDiagram(p), ActionListener {
    private var aep: AnimationEditPanel? = null

    private var activeEventitem: LadderEventItem? = null
    private var activePositionitem: LadderPositionItem? = null
    private var itemWasSelected: Boolean = false // for detecting de-selecting clicks
    private var startY: Int = 0
    private var startYlow: Int = 0
    private var startYhigh: Int = 0 // initial box y-coordinates
    private var startT: Double = 0.0 // initial time
    private var deltaY: Int = 0
    private var deltaYMin: Int = 0
    private var deltaYMax: Int = 0 // limits for dragging up/down

    private var popupitem: LadderItem? = null
    private var popupX: Int = 0 // screen coordinates where popup was raised
    private var popupY: Int = 0

    private var dialogControls: ArrayList<JComponent>? = null
    private var dialogPd: List<ParameterDescriptor>? = null

    //--------------------------------------------------------------------------
    // Methods to respond to changes made in this object's UI
    //--------------------------------------------------------------------------

    // Called whenever the active event in the ladder diagram is changed in
    // some way within this ladder diagram's UI.

    fun activeEventChanged() {
        if (activeEventitem == null) return

        val hash = activeEventitem!!.hashCode
        layoutPattern(false)  // rebuild pattern event list
        createView()  // rebuild ladder diagram (LadderItem arrays)

        // locate the event we're editing, in the updated pattern
        activeEventitem = null
        for (item in ladderEventItems!!) {
            if (item.hashCode == hash) {
                activeEventitem = item
                break
            }
        }

        try {
            if (activeEventitem == null) {
                throw JuggleExceptionInternalWithPattern("activeEventChanged(): event not found", pattern)
            } else if (aep != null) {
                aep!!.activateEvent(activeEventitem!!.event)
            }
        } catch (jei: JuggleExceptionInternal) {
            jlHandleFatalException(jei)
        }
    }

    // Called whenever the active position in the ladder diagram is changed in
    // some way, within this ladder diagram's UI.

    fun activePositionChanged() {
        if (activePositionitem == null) return

        val hash = activePositionitem!!.hashCode
        layoutPattern(false)
        createView()

        activePositionitem = null
        for (item in ladderPositionItems!!) {
            // System.out.println(item.event.toString());
            if (item.hashCode == hash) {
                activePositionitem = item
                break
            }
        }

        if (activePositionitem == null) {
            jlHandleFatalException(JuggleExceptionInternalWithPattern("ELD: position not found", pattern))
        } else if (aep != null) {
            aep!!.activatePosition(activePositionitem!!.position)
        }
    }

    private fun layoutPattern(undo: Boolean) {
        try {
            // use synchronized here to avoid data consistency problems with
            // animation thread in AnimationPanel's run() method
            synchronized(pattern) {
                pattern.setNeedsLayout()
                pattern.layout
                aep?.animator?.initAnimator()
                aep?.repaint()
            }

            if (undo) {
                addToUndoList()
            }
        } catch (je: JuggleException) {
            // The various editing functions below (e.g., from the popup menu)
            // should never put the pattern into an invalid state -- it is their
            // responsibility to validate input and handle errors. So we
            // shouldn't ever get here.
            jlHandleFatalException(je)
            parentFrame?.dispose()
        }
    }

    fun addToUndoList() {
        parentView.addToUndoList(pattern)
    }

    //--------------------------------------------------------------------------
    // Methods to respond to changes made elsewhere
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    fun activateEvent(ev: JMLEvent) {
        createView()  // rebuild ladder diagram (LadderItem arrays)
        activeEventitem = null
        val evInloop = pattern.getEventImageInLoop(ev)
            ?: throw JuggleExceptionInternalWithPattern("activateEvent(): null event", pattern)
        for (item in ladderEventItems!!) {
            if (item.event == evInloop) {
                activeEventitem = item
                break
            }
        }
        if (activeEventitem == null) {
            throw JuggleExceptionInternalWithPattern("activateEvent(): event not found", pattern)
        }
    }

    @Throws(JuggleExceptionInternal::class)
    fun reactivateEvent(): JMLEvent? {
        if (activeEventitem == null) {
            throw JuggleExceptionInternalWithPattern("reactivateEvent(): null eventitem", pattern)
        }
        val hash = activeEventitem!!.hashCode
        createView()  // rebuild ladder diagram (LadderItem arrays)

        // re-locate the event we're editing in the newly laid out pattern
        activeEventitem = null
        for (item in ladderEventItems!!) {
            if (item.hashCode == hash) {
                activeEventitem = item
                break
            }
        }
        if (activeEventitem == null) {
            throw JuggleExceptionInternalWithPattern("reactivateEvent(): event not found", pattern)
        }
        return activeEventitem!!.event
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseListener methods
    //--------------------------------------------------------------------------

    override fun mousePressed(me: MouseEvent) {
        val aep2 = aep
        if (aep2 != null && (aep2.writingGIF || !aep2.engineAnimating)) {
            return
        }

        var my = me.getY()
        my = min(max(my, BORDER_TOP), ladderHeight - BORDER_TOP)

        // on macOS the popup triggers here
        if (me.isPopupTrigger) {
            guiState = STATE_POPUP
            activeEventitem = getSelectedLadderEvent(me.getX(), me.getY())
            activePositionitem = getSelectedLadderPosition(me.getX(), me.getY())
            popupitem = if (activeEventitem != null) activeEventitem else activePositionitem
            if (popupitem == null) {
                popupitem = getSelectedLadderPath(me.getX(), me.getY(), PATH_SLOP)
            }

            popupX = me.getX()
            popupY = me.getY()
            if (aep2 != null) {
                val scale =
                    (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
                val newtime = (my - BORDER_TOP).toDouble() * scale
                animPaused = aep2.isPaused
                aep2.isPaused = true
                aep2.time = newtime
                aep2.deactivateEvent()
                aep2.deactivatePosition()
                if (activeEventitem != null) {
                    try {
                        aep2.activateEvent(activeEventitem!!.event)
                    } catch (jei: JuggleExceptionInternal) {
                        jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
                    }
                }
                if (activePositionitem != null) {
                    aep2.activatePosition(activePositionitem!!.position)
                }
                aep2.repaint()
            }

            makePopupMenu(popupitem).show(this@EditLadderDiagram, me.getX(), me.getY())
        } else {
            when (guiState) {
                STATE_INACTIVE -> {
                    var needsHandling = true
                    itemWasSelected = false

                    val oldEventitem = activeEventitem
                    activeEventitem = getSelectedLadderEvent(me.getX(), me.getY())
                    if (oldEventitem != null && oldEventitem == activeEventitem) {
                        itemWasSelected = true
                    }

                    if (activeEventitem != null) {
                        if (aep2 != null) {
                            try {
                                aep2.activateEvent(activeEventitem!!.event)
                            } catch (jei: JuggleExceptionInternal) {
                                jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
                            }
                        }
                        if (activeEventitem!!.type == LadderItem.TYPE_TRANSITION) {
                            // only allow dragging of TYPE_EVENT
                            needsHandling = false
                        }

                        if (needsHandling) {
                            guiState = STATE_MOVING_EVENT
                            activePositionitem = null
                            startY = me.getY()
                            startYlow = activeEventitem!!.ylow
                            startYhigh = activeEventitem!!.yhigh
                            startT = activeEventitem!!.event!!.t
                            findEventLimits(activeEventitem!!)
                            needsHandling = false
                        }
                    }

                    if (needsHandling) {
                        val oldPositionitem = activePositionitem
                        activePositionitem = getSelectedLadderPosition(me.getX(), me.getY())
                        if (oldPositionitem != null && oldPositionitem == activePositionitem) {
                            itemWasSelected = true
                        }

                        if (activePositionitem != null) {
                            guiState = STATE_MOVING_POSITION
                            activeEventitem = null
                            startY = me.getY()
                            startYlow = activePositionitem!!.yLow
                            startYhigh = activePositionitem!!.yHigh
                            startT = activePositionitem!!.position!!.t
                            findPositionLimits(activePositionitem!!)
                            aep2?.activatePosition(activePositionitem!!.position)
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
                            aep2.deactivateEvent()
                            aep2.deactivatePosition()
                        }
                    }
                }

                STATE_MOVING_EVENT -> {}
                STATE_MOVING_POSITION -> {}
                STATE_MOVING_TRACKER -> {}
                STATE_POPUP -> finishPopup()  // shouldn't ever get here
            }

            repaint()
            aep2?.repaint()
        }
    }

    override fun mouseReleased(me: MouseEvent?) {
        val aep2 = aep
        if (aep2 != null && (aep2.writingGIF || !aep2.engineAnimating)) {
            return
        }

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
                        aep2.deactivateEvent()
                        aep2.deactivatePosition()
                        if (activeEventitem != null) {
                            try {
                                aep2.activateEvent(activeEventitem!!.event)
                            } catch (jei: JuggleExceptionInternal) {
                                jlHandleFatalException(JuggleExceptionInternalWithPattern(jei, pattern))
                            }
                        }
                        if (activePositionitem != null) {
                            aep2.activatePosition(activePositionitem!!.position)
                        }
                        aep2.repaint()
                    }

                    guiState = STATE_POPUP

                    if (deltaY != 0) {
                        deltaY = 0
                        repaint()
                    }
                    popupX = me.getX()
                    popupY = me.getY()
                    popupitem =
                        (if (activeEventitem != null) activeEventitem else activePositionitem)
                    if (popupitem == null) {
                        popupitem = getSelectedLadderPath(me.getX(), me.getY(), PATH_SLOP)
                    }

                    makePopupMenu(popupitem).show(this@EditLadderDiagram, me.getX(), me.getY())
                }

                STATE_POPUP -> jlHandleFatalException(
                    JuggleExceptionInternalWithPattern("tried to enter POPUP state while already in it", pattern)
                )
            }
        } else {
            when (guiState) {
                STATE_INACTIVE -> {}
                STATE_MOVING_EVENT -> {
                    guiState = STATE_INACTIVE
                    if (deltaY != 0) {
                        deltaY = 0
                        addToUndoList()
                    } else if (itemWasSelected) {
                        // clicked without moving --> deselect
                        activeEventitem = null
                        aep2?.deactivateEvent()
                        aep2?.repaint()
                        repaint()
                    }
                }

                STATE_MOVING_POSITION -> {
                    guiState = STATE_INACTIVE
                    if (deltaY != 0) {
                        deltaY = 0
                        addToUndoList()
                    } else if (itemWasSelected) {
                        activePositionitem = null
                        aep2?.deactivatePosition()
                        aep2?.repaint()
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
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //--------------------------------------------------------------------------

    override fun mouseDragged(me: MouseEvent) {
        val aep2 = aep
        if (aep2 != null && (aep2.writingGIF || !aep2.engineAnimating)) {
            return
        }

        var my = me.getY()
        my = min(max(my, BORDER_TOP), ladderHeight - BORDER_TOP)

        when (guiState) {
            STATE_INACTIVE, STATE_POPUP -> {}
            STATE_MOVING_EVENT -> {
                val oldDeltaY = deltaY
                deltaY = getClippedEventTime(me, activeEventitem!!.event!!)

                if (deltaY != oldDeltaY) {
                    moveEventInPattern(activeEventitem!!.eventitem!!)
                    activeEventChanged()
                    repaint()
                }
            }

            STATE_MOVING_POSITION -> {
                val oldDeltaY = deltaY
                deltaY = getClippedPositionTime(me, activePositionitem!!.position!!)

                if (deltaY != oldDeltaY) {
                    movePositionInPattern(activePositionitem!!)
                    activePositionChanged()
                    repaint()
                }
            }

            STATE_MOVING_TRACKER -> {
                trackerY = my
                this@EditLadderDiagram.repaint()
                if (aep2 != null) {
                    val scale =
                        (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
                    val newtime = (my - BORDER_TOP).toDouble() * scale
                    aep2.time = newtime
                    aep2.repaint()
                }
            }
        }
    }

    //--------------------------------------------------------------------------
    // Utility methods for mouse interactions
    //--------------------------------------------------------------------------

    private fun findEventLimits(item: LadderEventItem) {
        var tmin = pattern.loopStartTime
        var tmax = pattern.loopEndTime
        val scale =
            (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()

        for (tr in item.event!!.transitions) {
            when (tr.type) {
                JMLTransition.TRANS_THROW -> {
                    // find out when the ball being thrown was last caught
                    var ev = item.event!!.previous
                    while (ev != null) {
                        if (ev.getPathTransition(
                                tr.path,
                                JMLTransition.TRANS_CATCH
                            ) != null || ev.getPathTransition(
                                tr.path,
                                JMLTransition.TRANS_SOFTCATCH
                            ) != null || ev.getPathTransition(
                                tr.path,
                                JMLTransition.TRANS_GRABCATCH
                            ) != null
                        ) {
                            break
                        }
                        ev = ev.previous
                    }
                    if (ev == null) {
                        jlHandleFatalException(
                            JuggleExceptionInternalWithPattern("Null event 1 in mousePressed()", pattern)
                        )
                        parentFrame?.dispose()
                        return
                    }
                    tmin = max(tmin, ev.t + MIN_THROW_SEP_TIME)

                    // next catch is easy to find
                    ev = tr.outgoingPathLink!!.endEvent
                    if (!ev.hasSamePrimaryAs(item.event!!)) {
                        tmax = min(tmax, ev.t - MIN_THROW_SEP_TIME)
                    }
                }

                JMLTransition.TRANS_CATCH,
                JMLTransition.TRANS_SOFTCATCH,
                JMLTransition.TRANS_GRABCATCH -> {
                    // previous throw is easy to find
                    var ev: JMLEvent? = tr.incomingPathLink!!.startEvent
                    if (!ev!!.hasSamePrimaryAs(item.event!!)) {
                        tmin = max(tmin, ev.t + MIN_THROW_SEP_TIME)
                    }

                    // find out when the ball being caught is next thrown
                    ev = item.event!!.next
                    while (ev != null) {
                        if (ev.getPathTransition(tr.path, JMLTransition.TRANS_THROW) != null) {
                            break
                        }
                        ev = ev.next
                    }
                    if (ev == null) {
                        jlHandleFatalException(
                            JuggleExceptionInternalWithPattern("Null event 2 in mousePressed()", pattern)
                        )
                        parentFrame?.dispose()
                        return
                    }
                    tmax = min(tmax, ev.t - MIN_THROW_SEP_TIME)
                }
            }
        }
        deltaYMin = ((tmin - item.event!!.t) / scale).toInt()
        deltaYMax = ((tmax - item.event!!.t) / scale).toInt()
    }

    // Return value of `delta_y` during mouse drag of an event, clipping it to
    // enforce proximity limits between various event types, as well as hard
    // limits `delta_y_min` and `delta_y_max`.

    private fun getClippedEventTime(me: MouseEvent, event: JMLEvent): Int {
        val dy = min(max(me.getY() - startY, deltaYMin), deltaYMax)

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
            var ev = pattern.eventList
            var sep: Double

            while (ev != null) {
                if (ev != event && ev.juggler == event.juggler && ev.hand == event.hand) {
                    sep = if (ev.hasThrow && event.hasThrowOrCatch
                        || ev.hasThrowOrCatch && event.hasThrow
                    ) {
                        MIN_THROW_SEP_TIME
                    } else {
                        MIN_EVENT_SEP_TIME
                    }

                    val evExclMin = ev.t - sep
                    val evExclMax = ev.t + sep

                    if (tExclMax in evExclMin..<evExclMax) {
                        tExclMax = evExclMax
                        changed = true
                    }

                    if (evExclMin < tExclMin && evExclMax >= tExclMin) {
                        tExclMin = evExclMin
                        changed = true
                    }
                }
                ev = ev.next
            }
        } while (changed)

        // System.out.println("t_excl_min = " + t_excl_min + ", t_excl_max = " + t_excl_max);

        // Clip the event time `newt` to whichever end of the exclusion window
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

    private fun moveEventInPattern(item: LadderEventItem) {
        var ev = item.event

        val scale =
            (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        var shift = deltaY * scale
        var newT = startT + shift
        if (newT < pattern.loopStartTime + scale) {
            // within 1 pixel of top
            shift = pattern.loopStartTime - startT
            newT = pattern.loopStartTime
        } else if (newT >= pattern.loopEndTime) {
            shift = pattern.loopEndTime - 0.0001 - startT
            newT = pattern.loopEndTime - 0.0001
        }

        val throwpath = BooleanArray(pattern.numberOfPaths)
        val catchpath = BooleanArray(pattern.numberOfPaths)
        val holdpathorig = BooleanArray(pattern.numberOfPaths)
        val holdpathnew = BooleanArray(pattern.numberOfPaths)
        for (tr in ev!!.transitions) {
            when (tr.type) {
                JMLTransition.TRANS_THROW -> throwpath[tr.path - 1] = true

                JMLTransition.TRANS_CATCH,
                JMLTransition.TRANS_SOFTCATCH,
                JMLTransition.TRANS_GRABCATCH -> catchpath[tr.path - 1] = true

                JMLTransition.TRANS_HOLDING -> {
                    holdpathorig[tr.path - 1] = true
                    holdpathnew[tr.path - 1] = true
                }
            }
        }

        if (newT < ev.t) {  // moving to earlier time
            ev = ev.previous
            while (ev != null && ev.t > newT) {
                if (!ev.hasSamePrimaryAs(item.event!!) && ev.juggler == item.event!!.juggler && ev.hand == item.event!!.hand) {
                    var j = 0
                    while (j < ev.transitions.size) {
                        val tr = ev.transitions[j]
                        when (tr.type) {
                            JMLTransition.TRANS_THROW -> holdpathnew[tr.path - 1] = true

                            JMLTransition.TRANS_CATCH,
                            JMLTransition.TRANS_SOFTCATCH,
                            JMLTransition.TRANS_GRABCATCH -> holdpathnew[tr.path - 1] = false

                            JMLTransition.TRANS_HOLDING -> if (throwpath[tr.path - 1]) {
                                ev.removeTransition(j)
                                if (!ev.isPrimary) {
                                    ev.primary.removeTransition(j)
                                }
                                j--  // next trans moved into slot
                            }
                        }
                        j++
                    }

                    for (j in 0..<pattern.numberOfPaths) {
                        if (catchpath[j]) {
                            var tr =
                                JMLTransition(JMLTransition.TRANS_HOLDING, (j + 1), null, null)
                            ev.addTransition(tr)
                            if (!ev.isPrimary) {
                                val pp = ev.pathPermFromPrimary!!.inverse
                                tr = JMLTransition(
                                    JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null
                                )
                                ev.primary.addTransition(tr)
                            }
                        }
                    }
                }
                ev = ev.previous
            }
        } else if (newT > ev.t) {  // moving to later time
            ev = ev.next
            while (ev != null && ev.t < newT) {
                if (!ev.hasSamePrimaryAs(item.event!!) && ev.juggler == item.event!!.juggler && ev.hand == item.event!!.hand) {
                    var j = 0
                    while (j < ev.transitions.size) {
                        val tr = ev.transitions[j]
                        when (tr.type) {
                            JMLTransition.TRANS_THROW -> holdpathnew[tr.path - 1] = false

                            JMLTransition.TRANS_CATCH,
                            JMLTransition.TRANS_SOFTCATCH,
                            JMLTransition.TRANS_GRABCATCH -> holdpathnew[tr.path - 1] = true

                            JMLTransition.TRANS_HOLDING -> if (catchpath[tr.path - 1]) {
                                ev.removeTransition(j)
                                if (!ev.isPrimary) {
                                    ev.primary.removeTransition(j)
                                }
                                j--
                            }
                        }
                        j++
                    }

                    for (j in 0..<pattern.numberOfPaths) {
                        if (throwpath[j]) {
                            var tr =
                                JMLTransition(JMLTransition.TRANS_HOLDING, (j + 1), null, null)
                            ev.addTransition(tr)
                            if (!ev.isPrimary) {
                                val pp = ev.pathPermFromPrimary!!.inverse
                                tr = JMLTransition(
                                    JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null
                                )
                                ev.primary.addTransition(tr)
                            }
                        }
                    }
                }
                ev = ev.next
            }
        }

        ev = item.event
        val pp = ev!!.pathPermFromPrimary!!.inverse
        var newPrimaryT = startT + shift

        /*
        if (!ev.isPrimary) {
            val newEventT = newPrimaryT
            newPrimaryT += ev.primary.t - ev.t
            ev.t = newEventT // update event time so getHashCode() works
            ev = ev.primary
        }*/

        for (j in 0..<pattern.numberOfPaths) {
            if (holdpathnew[j] != holdpathorig[j]) {
                if (holdpathnew[j]) {
                    val tr =
                        JMLTransition(JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null)
                    ev.addTransition(tr)
                } else {
                    val tr =
                        ev.getPathTransition(pp.getMapping(j + 1), JMLTransition.TRANS_HOLDING)
                    if (tr == null) {
                        jlHandleFatalException(
                            JuggleExceptionInternalWithPattern("Null transition in removing hold", pattern)
                        )
                        parentFrame?.dispose()
                        return
                    }
                    ev.removeTransition(tr)
                }
            }
        }

        pattern.removeEvent(ev)
        // change time of primary
        pattern.addEvent(ev.copy(t = newPrimaryT)) // remove/add cycle keeps events sorted
    }

    private fun findPositionLimits(item: LadderPositionItem) {
        val tmin = pattern.loopStartTime
        val tmax = pattern.loopEndTime
        val scale =
            (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()

        deltaYMin = ((tmin - item.position!!.t) / scale).toInt()
        deltaYMax = ((tmax - item.position!!.t) / scale).toInt()
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
        val pos = item.position!!
        val scale =
            (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()

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
        item.position = newPosition
        pattern = JMLPattern.fromPatternBuilder(rec)
    }

    private fun makePopupMenu(laditem: LadderItem?): JPopupMenu {
        val popup = JPopupMenu()

        for (i in popupItems.indices) {
            val name: String? = popupItems[i]
            if (name == null) {
                popup.addSeparator()
                continue
            }

            val item = JMenuItem(getStringResource(popupItemsStringResources[i]!!))
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
            else -> jlHandleFatalException(
                JuggleExceptionInternalWithPattern("unknown item in ELD popup", pattern)
            )
        }

        finishPopup()
    }

    private fun addEventToHand(hand: Int): JMLEvent? {
        var juggler = 1
        if (pattern.numberOfJugglers > 1) {
            var mouseX = popupX
            val jugglerRightPx = (leftX + rightX + jugglerDeltaX) / 2

            while (juggler <= pattern.numberOfJugglers) {
                if (mouseX < jugglerRightPx) {
                    break
                }

                mouseX -= jugglerDeltaX
                juggler++
            }
            juggler = min(juggler, pattern.numberOfJugglers)
        }

        val scale =
            (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        val evtime = (popupY - BORDER_TOP).toDouble() * scale

        val evpos = Coordinate()
        try {
            pattern.layout.getHandCoordinate(juggler, hand, evtime, evpos)
        } catch (jei: JuggleExceptionInternal) {
            jlHandleFatalException(jei)
            parentFrame?.dispose()
            return null
        }

        val newLocalCoordinate = pattern.layout.convertGlobalToLocal(evpos, juggler, evtime)
        val ev = JMLEvent(
            x = newLocalCoordinate.x,
            y = newLocalCoordinate.y,
            z = newLocalCoordinate.z,
            t = evtime,
            juggler = juggler,
            hand = hand
        )
        pattern.addEvent(ev)

        // add holding transitions to the new event, if hand is filled
        for (i in 0..<pattern.numberOfPaths) {
            var holding = false
            var evt = ev.previous
            while (evt != null) {
                val tr = evt.getPathTransition(i + 1, JMLTransition.TRANS_ANY)
                if (tr != null) {
                    if (evt.juggler != ev.juggler || evt.hand != ev.hand) {
                        break
                    }
                    if (tr.type == JMLTransition.TRANS_THROW) {
                        break
                    }
                    holding = true
                    break
                }
                evt = evt.previous
            }

            if (holding) {
                val tr = JMLTransition(JMLTransition.TRANS_HOLDING, (i + 1), null, null)
                ev.addTransition(tr)
            }
        }

        activeEventitem = null
        aep?.deactivateEvent()
        layoutPattern(true)
        createView()
        repaint()
        return ev
    }

    private fun removeEvent() {
        // makePopupMenu() ensures that the event only has hold transitions
        if (popupitem !is LadderEventItem) {
            jlHandleFatalException(
                JuggleExceptionInternalWithPattern("LadderDiagram illegal remove event", pattern)
            )
            return
        }
        var ev = (popupitem as LadderEventItem).event
        if (!ev!!.isPrimary) {
            ev = ev.primary
        }
        pattern.removeEvent(ev)
        activeEventitem = null
        aep?.deactivateEvent()
        layoutPattern(true)
        createView()
        repaint()
    }

    private fun addPositionToJuggler(): JMLPosition {
        var juggler = 1
        if (pattern.numberOfJugglers > 1) {
            var mouseX = popupX
            val jugglerRightPx = (leftX + rightX + jugglerDeltaX) / 2

            while (juggler <= pattern.numberOfJugglers) {
                if (mouseX < jugglerRightPx) {
                    break
                }

                mouseX -= jugglerDeltaX
                juggler++
            }
            if (juggler > pattern.numberOfJugglers) {
                juggler = pattern.numberOfJugglers
            }
        }

        val scale =
            (pattern.loopEndTime - pattern.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        val postime = (popupY - BORDER_TOP).toDouble() * scale

        val loc = Coordinate()
        pattern.layout.getJugglerPosition(juggler, postime, loc)
        val pos = JMLPosition(
            x = loc.x,
            y = loc.y,
            z = loc.z,
            t = postime,
            angle = pattern.layout.getJugglerAngle(juggler, postime),
            juggler = juggler
        )
        val rec = PatternBuilder.fromJMLPattern(pattern)
        rec.positions.add(pos)  // TODO: sort the positions
        pattern = JMLPattern.fromPatternBuilder(rec)

        activeEventitem = null
        aep?.deactivateEvent()
        layoutPattern(true)
        createView()
        repaint()
        return pos
    }

    private fun removePosition() {
        if (popupitem !is LadderPositionItem) {
            jlHandleFatalException(
                JuggleExceptionInternalWithPattern("LadderDiagram illegal remove position", pattern)
            )
            return
        }
        val pos = (popupitem as LadderPositionItem).position
        val rec = PatternBuilder.fromJMLPattern(pattern)
        rec.positions.remove(pos)
        pattern = JMLPattern.fromPatternBuilder(rec)
        activePositionitem = null
        aep?.deactivatePosition()
        layoutPattern(true)
        createView()
        repaint()
    }

    private fun defineProp() {
        if (popupitem == null) {
            jlHandleFatalException(JuggleExceptionInternalWithPattern("defineProp() null popupitem", pattern))
            return
        }

        // figure out which path number the user selected
        val pn: Int
        if (popupitem is LadderEventItem) {
            if (popupitem!!.type != LadderItem.TYPE_TRANSITION) {
                jlHandleFatalException(
                    JuggleExceptionInternalWithPattern("defineProp() bad LadderItem type", pattern)
                )
                return
            }

            val ev = (popupitem as LadderEventItem).event
            val transnum = (popupitem as LadderEventItem).transnum
            val tr = ev!!.transitions[transnum]
            pn = tr.path
        } else {
            pn = (popupitem as LadderPathItem).pathNum
        }

        val animpropnum = aep!!.animator.animPropNum
        val propnum = animpropnum!![pn - 1]
        // final int propnum = pattern.getPropAssignment(pathnum);
        // System.out.println("pathnum = " + pathnum + ", propnum = " + propnum);
        val startprop = pattern.getProp(propnum)
        val prtypes: List<String> = Prop.builtinProps

        val jd = JDialog(parentFrame, getStringResource(Res.string.gui_define_prop), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab = JLabel(getStringResource(Res.string.gui_prop_type))
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
        val cancelbutton = JButton(getStringResource(Res.string.gui_cancel))
        p3.add(cancelbutton)
        gb.setConstraints(
            cancelbutton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        cancelbutton.addActionListener { _: ActionEvent? -> jd.dispose() }
        val okbutton = JButton(getStringResource(Res.string.gui_ok))
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

            // sync paths with current prop list
            for (i in 0..<pattern.numberOfPaths) {
                pattern.setPropAssignment(i + 1, animpropnum[i])
            }

            // check to see if any other paths are using this prop definition
            var killprop = true
            for (i in 0..<pattern.numberOfPaths) {
                if (i != pn - 1) {
                    if (animpropnum[i] == propnum) {
                        killprop = false
                        break
                    }
                }
            }

            if (killprop) {
                pattern.removeProp(propnum)
            }

            // check to see if a prop like this one has already been defined
            var gotmatch = false
            var matchingprop = 0
            for (i in 1..pattern.numberOfProps) {
                val pdef = pattern.props[i - 1]
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
                pattern.setPropAssignment(pn, matchingprop)
            } else {
                // new prop is different
                val newprop = JMLProp(type.lowercase(Locale.getDefault()), mod)
                pattern.addProp(newprop)
                pattern.setPropAssignment(pn, pattern.numberOfProps)
            }

            if (activeEventitem != null) {
                activeEventChanged()
                addToUndoList()
            } else {
                layoutPattern(true)
            }
            jd.dispose()
            repaint()
            parentView.patternWindow?.updateColorsMenu()
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

    private fun defineThrow() {
        if (popupitem !is LadderEventItem) {
            jlHandleFatalException(JuggleExceptionInternalWithPattern("defineThrow() class format", pattern))
            return
        }
        val evPrimary = (popupitem as LadderEventItem).event!!.primary
        val tr = evPrimary.transitions[(popupitem as LadderEventItem).transnum]

        val pptypes: List<String> = Path.builtinPaths

        val jd = JDialog(parentFrame, getStringResource(Res.string.gui_define_throw), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab = JLabel(getStringResource(Res.string.gui_throw_type))
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
                val ppt = if (type.equals(tr.throwType, ignoreCase = true)) {
                    tr.outgoingPathLink!!.path
                } else {
                    newPath(type)
                }
                makeParametersPanel(p2, ppt!!.parameterDescriptors)
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
        val cancelbutton = JButton(getStringResource(Res.string.gui_cancel))
        p3.add(cancelbutton)
        gb.setConstraints(
            cancelbutton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        cancelbutton.addActionListener { _: ActionEvent? -> jd.dispose() }
        val okbutton = JButton(getStringResource(Res.string.gui_ok))
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
                    this[(popupitem as LadderEventItem).transnum] = newTransition
                }
            )
            pattern.removeEvent(evPrimary)
            pattern.addEvent(newPrimary)
            activeEventChanged()
            addToUndoList()
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

    private fun changeCatchStyleTo(type: Int) {
        if (popupitem == null) {
            jlHandleFatalException(JuggleExceptionInternalWithPattern("No popupitem in case 10", pattern))
            return
        }
        if (popupitem !is LadderEventItem) {
            jlHandleFatalException(
                JuggleExceptionInternalWithPattern("LadderDiagram change to catch class format", pattern)
            )
            return
        }
        val evPrimary = (popupitem as LadderEventItem).event!!.primary
        val tr = evPrimary.transitions[(popupitem as LadderEventItem).transnum]

        val newPrimary = evPrimary.copy(
            transitions = evPrimary.transitions.toMutableList().apply {
                this[(popupitem as LadderEventItem).transnum] = tr.copy(type = type)
            }
        )

        pattern.removeEvent(evPrimary)
        pattern.addEvent(newPrimary)
        activeEventChanged()
        addToUndoList()
        repaint()
    }

    private fun makeLastInEvent() {
        if (popupitem == null) {
            jlHandleFatalException(JuggleExceptionInternalWithPattern("No popupitem in case 8", pattern))
            return
        }
        if (popupitem !is LadderEventItem) {
            jlHandleFatalException(
                JuggleExceptionInternalWithPattern("LadderDiagram make last transition class format", pattern)
            )
            return
        }
        var ev = (popupitem as LadderEventItem).event
        if (!ev!!.isPrimary) {
            ev = ev.primary
        }
        val tr = ev.transitions[(popupitem as LadderEventItem).transnum]
        ev.removeTransition(tr)
        ev.addTransition(tr) // will add at end
        activeEventitem = null // deselect event since it's moving
        aep?.deactivateEvent()
        layoutPattern(true)
        createView()
        repaint()
    }

    // Helper for defineProp() and defineThrow().

    private fun makeParametersPanel(jp: JPanel, pd: List<ParameterDescriptor>) {
        jp.removeAll()
        dialogControls = ArrayList()
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
                val composeImage = getImageResource(fileSource)

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
                            val result = jlJfc.showOpenDialog(this@EditLadderDiagram)
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
                                    JuggleExceptionUser(getStringResource(Res.string.error_malformed_url))
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
            val dialog = dialogPd!!
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
                            getStringResource(Res.string.error_number_format, dialog[i].name)
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
                            getStringResource(Res.string.error_number_format, dialog[i].name)
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
        popupitem = null
        if (guiState == STATE_POPUP) {
            guiState = STATE_INACTIVE
            aep?.isPaused = animPaused
        }
    }

    //--------------------------------------------------------------------------
    // AnimationPanel.AnimationAttachment methods
    //--------------------------------------------------------------------------

    override fun setAnimationPanel(animPanel: AnimationPanel?) {
        super.setAnimationPanel(animPanel)
        if (animPanel is AnimationEditPanel) {
            aep = animPanel
        }
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
        val api = activePositionitem
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
        val aei = activeEventitem
        if (aei != null) {
            gr.color = COLOR_SELECTION
            gr.drawLine(
                aei.xlow - 1,
                aei.ylow - 1,
                aei.xhigh + 1,
                aei.ylow - 1
            )
            gr.drawLine(
                aei.xhigh + 1,
                aei.ylow - 1,
                aei.xhigh + 1,
                aei.yhigh + 1
            )
            gr.drawLine(
                aei.xhigh + 1,
                aei.yhigh + 1,
                aei.xlow,
                aei.yhigh + 1
            )
            gr.drawLine(
                aei.xlow - 1,
                aei.yhigh + 1,
                aei.xlow - 1,
                aei.ylow - 1
            )
        }

        // label the tracker line with the time
        if (guiState == STATE_MOVING_TRACKER) {
            gr.color = COLOR_TRACKER
            gr.drawString(jlToStringRounded(simTime, 2) + " s", ladderWidth / 2 - 18, trackerY - 5)
        }
    }

    companion object {
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

                    for (tr in evitem.event!!.transitions) {
                        if (tr.type != JMLTransition.TRANS_HOLDING) {
                            return false
                        }
                    }

                    // check to make sure we're not allowing the user to delete
                    // an event if it's the last one in that hand.
                    // do this by finding the next event in the same hand; if it
                    // has the same primary, it's the only one
                    val hand = evitem.event!!.hand
                    val juggler = evitem.event!!.juggler
                    val evm1 = if (evitem.event!!.isPrimary) evitem.event else evitem.event!!.primary
                    var ev = evitem.event!!.next
                    while (ev != null) {
                        if (ev.hand == hand && ev.juggler == juggler) {
                            val evm2 = if (ev.isPrimary) ev else ev.primary
                            if (evm1 == evm2) {
                                return false
                            }
                            break
                        }
                        ev = ev.next
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
                val tr = evitem.event!!.transitions[evitem.transnum]

                when (command) {
                    "makelast" ->
                        return evitem.transnum != (evitem.event!!.transitions.size - 1)

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
    }
}
