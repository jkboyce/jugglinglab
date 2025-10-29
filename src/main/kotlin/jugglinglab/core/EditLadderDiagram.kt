//
// EditLadderDiagram.kt
//
// This class draws the vertical ladder diagram on the right side of Edit view.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.JugglingLab
import jugglinglab.jml.*
import jugglinglab.path.Path
import jugglinglab.path.Path.Companion.newPath
import jugglinglab.prop.Prop
import jugglinglab.prop.Prop.Companion.newProp
import jugglinglab.util.*
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.ErrorDialog.handleUserException
import jugglinglab.view.View
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.MalformedURLException
import java.net.URL
import java.text.MessageFormat
import java.util.*
import javax.swing.*
import javax.swing.border.BevelBorder
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class EditLadderDiagram(pat: JMLPattern, private var parentframe: JFrame?, private var parentview: View) :
    LadderDiagram(pat), ActionListener {
    protected var aep: AnimationEditPanel? = null

    protected var active_eventitem: LadderEventItem? = null
    protected var active_positionitem: LadderPositionItem? = null
    protected var item_was_selected: Boolean = false // for detecting de-selecting clicks
    protected var start_y: Int = 0
    protected var start_ylow: Int = 0
    protected var start_yhigh: Int = 0 // initial box y-coordinates
    protected var start_t: Double = 0.0 // initial time
    protected var delta_y: Int = 0
    protected var delta_y_min: Int = 0
    protected var delta_y_max: Int = 0 // limits for dragging up/down

    protected var popupitem: LadderItem? = null
    protected var popup_x: Int = 0 // screen coordinates where popup was raised
    protected var popup_y: Int = 0

    protected var dialog_controls: ArrayList<JComponent>? = null
    protected var dialog_pd: Array<ParameterDescriptor>? = null

    //--------------------------------------------------------------------------
    // Methods to respond to changes made in this object's UI
    //--------------------------------------------------------------------------

    // Called whenever the active event in the ladder diagram is changed in
    // some way, within this ladder diagram's UI.

    fun activeEventChanged() {
        if (active_eventitem == null) {
            return
        }

        val hash = active_eventitem!!.hashCode

        layoutPattern(false) // rebuild pattern event list
        createView() // rebuild ladder diagram (LadderItem arrays)

        // locate the event we're editing, in the updated pattern
        active_eventitem = null
        for (item in ladderEventItems!!) {
            if (item.hashCode == hash) {
                active_eventitem = item
                break
            }
        }

        try {
            if (active_eventitem == null) {
                throw JuggleExceptionInternal("activeEventChanged(): event not found", pat)
            } else if (aep != null) {
                aep!!.activateEvent(active_eventitem!!.event)
            }
        } catch (jei: JuggleExceptionInternal) {
            handleFatalException(jei)
        }
    }

    // Called whenever the active position in the ladder diagram is changed in
    // some way, within this ladder diagram's UI.

    fun activePositionChanged() {
        if (active_positionitem == null) {
            return
        }

        val hash = active_positionitem!!.hashCode

        layoutPattern(false)
        createView()

        active_positionitem = null
        for (item in ladderPositionItems!!) {
            // System.out.println(item.event.toString());
            if (item.hashCode == hash) {
                active_positionitem = item
                break
            }
        }

        if (active_positionitem == null) {
            handleFatalException(JuggleExceptionInternal("ELD: position not found", pat))
        } else if (aep != null) {
            aep!!.activatePosition(active_positionitem!!.position)
        }
    }

    protected fun layoutPattern(undo: Boolean) {
        try {
            // use synchronized here to avoid data consistency problems with
            // animation thread in AnimationPanel's run() method
            synchronized(pat) {
                pat.setNeedsLayout()
                pat.layoutPattern()
                if (aep != null) {
                    aep!!.animator.initAnimator()
                    aep!!.repaint()
                }
            }

            if (undo) {
                addToUndoList()
            }
        } catch (je: JuggleException) {
            // The various editing functions below (e.g., from the popup menu)
            // should never put the pattern into an invalid state -- it is their
            // responsibility to validate input and handle errors. So we
            // shouldn't ever get here.
            handleFatalException(je)
            if (parentframe != null) {
                parentframe!!.dispose()
                parentframe = null
            }
        }
    }

    fun addToUndoList() {
        parentview.addToUndoList(pat)
    }

    //----------------------------------------------------------------------------
    // Methods to respond to changes made elsewhere
    //----------------------------------------------------------------------------
    @Throws(JuggleExceptionInternal::class)
    fun activateEvent(ev: JMLEvent) {
        createView() // rebuild ladder diagram (LadderItem arrays)

        active_eventitem = null
        val ev_inloop = pat.getEventImageInLoop(ev)
        if (ev_inloop == null) {
            throw JuggleExceptionInternal("activateEvent(): null event", pat)
        }

        for (item in ladderEventItems!!) {
            if (item.event == ev_inloop) {
                active_eventitem = item
                break
            }
        }

        if (active_eventitem == null) {
            throw JuggleExceptionInternal("activateEvent(): event not found", pat)
        }
    }

    @Throws(JuggleExceptionInternal::class)
    fun reactivateEvent(): JMLEvent? {
        if (active_eventitem == null) {
            throw JuggleExceptionInternal("reactivateEvent(): null eventitem", pat)
        }

        val hash = active_eventitem!!.hashCode

        createView()  // rebuild ladder diagram (LadderItem arrays)

        // re-locate the event we're editing in the newly laid out pattern
        active_eventitem = null
        for (item in ladderEventItems!!) {
            if (item.hashCode == hash) {
                active_eventitem = item
                break
            }
        }

        if (active_eventitem == null) {
            throw JuggleExceptionInternal("reactivateEvent(): event not found", pat)
        }

        return active_eventitem!!.event
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseListener methods
    //--------------------------------------------------------------------------

    public override fun mousePressed(me: MouseEvent) {
        if (aep != null && (aep!!.writingGIF || !aep!!.engineAnimating)) {
            return
        }

        var my = me.getY()
        my = min(max(my, BORDER_TOP), ladderHeight - BORDER_TOP)

        // on macOS the popup triggers here
        if (me.isPopupTrigger()) {
            guiState = STATE_POPUP
            active_eventitem = getSelectedLadderEvent(me.getX(), me.getY())
            active_positionitem = getSelectedLadderPosition(me.getX(), me.getY())
            popupitem = if (active_eventitem != null) active_eventitem else active_positionitem
            if (popupitem == null) {
                popupitem = getSelectedLadderPath(me.getX(), me.getY(), PATH_SLOP)
            }

            popup_x = me.getX()
            popup_y = me.getY()
            if (aep != null) {
                val scale =
                    (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
                val newtime = (my - BORDER_TOP).toDouble() * scale
                animPaused = aep!!.isPaused
                aep!!.isPaused = true
                aep!!.time = newtime
                aep!!.deactivateEvent()
                aep!!.deactivatePosition()
                if (active_eventitem != null) {
                    try {
                        aep!!.activateEvent(active_eventitem!!.event)
                    } catch (jei: JuggleExceptionInternal) {
                        jei.attachPattern(pat)
                        handleFatalException(jei)
                    }
                }
                if (active_positionitem != null) {
                    aep!!.activatePosition(active_positionitem!!.position)
                }
                aep!!.repaint()
            }

            makePopupMenu(popupitem).show(this@EditLadderDiagram, me.getX(), me.getY())
        } else {
            when (guiState) {
                STATE_INACTIVE -> {
                    var needsHandling = true
                    item_was_selected = false

                    val old_eventitem = active_eventitem
                    active_eventitem = getSelectedLadderEvent(me.getX(), me.getY())
                    if (old_eventitem != null && old_eventitem == active_eventitem) {
                        item_was_selected = true
                    }

                    if (active_eventitem != null) {
                        if (aep != null) {
                            try {
                                aep!!.activateEvent(active_eventitem!!.event)
                            } catch (jei: JuggleExceptionInternal) {
                                jei.attachPattern(pat)
                                handleFatalException(jei)
                            }
                        }
                        if (active_eventitem!!.type == LadderItem.TYPE_TRANSITION) {
                            // only allow dragging of TYPE_EVENT
                            needsHandling = false
                        }

                        if (needsHandling) {
                            guiState = STATE_MOVING_EVENT
                            active_positionitem = null
                            start_y = me.getY()
                            start_ylow = active_eventitem!!.ylow
                            start_yhigh = active_eventitem!!.yhigh
                            start_t = active_eventitem!!.event!!.t
                            findEventLimits(active_eventitem!!)
                            needsHandling = false
                        }
                    }

                    if (needsHandling) {
                        val old_positionitem = active_positionitem
                        active_positionitem = getSelectedLadderPosition(me.getX(), me.getY())
                        if (old_positionitem != null && old_positionitem == active_positionitem) {
                            item_was_selected = true
                        }

                        if (active_positionitem != null) {
                            guiState = STATE_MOVING_POSITION
                            active_eventitem = null
                            start_y = me.getY()
                            start_ylow = active_positionitem!!.yLow
                            start_yhigh = active_positionitem!!.yHigh
                            start_t = active_positionitem!!.position!!.t
                            findPositionLimits(active_positionitem!!)
                            if (aep != null) {
                                aep!!.activatePosition(active_positionitem!!.position)
                            }
                            needsHandling = false
                        }
                    }

                    if (needsHandling) {
                        guiState = STATE_MOVING_TRACKER
                        trackerY = my
                        if (aep != null) {
                            val scale =
                                ((pat.loopEndTime - pat.loopStartTime)
                                    / (ladderHeight - 2 * BORDER_TOP).toDouble())
                            val newtime = (my - BORDER_TOP).toDouble() * scale
                            animPaused = aep!!.isPaused
                            aep!!.isPaused = true
                            aep!!.time = newtime
                            aep!!.deactivateEvent()
                            aep!!.deactivatePosition()
                        }
                    }
                }

                STATE_MOVING_EVENT -> {}
                STATE_MOVING_POSITION -> {}
                STATE_MOVING_TRACKER -> {}
                STATE_POPUP ->           // shouldn't ever get here
                    finishPopup()
            }

            repaint()
            if (aep != null) {
                aep!!.repaint()
            }
        }
    }

    override fun mouseReleased(me: MouseEvent?) {
        if (aep != null && (aep!!.writingGIF || !aep!!.engineAnimating)) {
            return
        }

        // on Windows the popup triggers here
        if (me!!.isPopupTrigger()) {
            when (guiState) {
                STATE_INACTIVE, STATE_MOVING_EVENT, STATE_MOVING_POSITION, STATE_MOVING_TRACKER -> {
                    // skip this code for MOVING_TRACKER state, since already executed in
                    // mousePressed() above
                    if (guiState != STATE_MOVING_TRACKER && aep != null) {
                        var my = me.getY()
                        my = min(max(my, BORDER_TOP), ladderHeight - BORDER_TOP)

                        val scale = ((pat.loopEndTime - pat.loopStartTime)
                                / (ladderHeight - 2 * BORDER_TOP).toDouble())
                        val newtime = (my - BORDER_TOP).toDouble() * scale
                        animPaused = aep!!.isPaused
                        aep!!.isPaused = true
                        aep!!.time = newtime
                        aep!!.deactivateEvent()
                        aep!!.deactivatePosition()
                        if (active_eventitem != null) {
                            try {
                                aep!!.activateEvent(active_eventitem!!.event)
                            } catch (jei: JuggleExceptionInternal) {
                                jei.attachPattern(pat)
                                handleFatalException(jei)
                            }
                        }
                        if (active_positionitem != null) {
                            aep!!.activatePosition(active_positionitem!!.position)
                        }
                        aep!!.repaint()
                    }

                    guiState = STATE_POPUP

                    if (delta_y != 0) {
                        delta_y = 0
                        repaint()
                    }
                    popup_x = me.getX()
                    popup_y = me.getY()
                    popupitem = (if (active_eventitem != null) active_eventitem else active_positionitem)
                    if (popupitem == null) {
                        popupitem = getSelectedLadderPath(me.getX(), me.getY(), PATH_SLOP)
                    }

                    makePopupMenu(popupitem).show(this@EditLadderDiagram, me.getX(), me.getY())
                }

                STATE_POPUP -> handleFatalException(
                    JuggleExceptionInternal("tried to enter POPUP state while already in it", pat)
                )
            }
        } else {
            when (guiState) {
                STATE_INACTIVE -> {}
                STATE_MOVING_EVENT -> {
                    guiState = STATE_INACTIVE
                    if (delta_y != 0) {
                        delta_y = 0
                        addToUndoList()
                    } else if (item_was_selected) {
                        // clicked without moving --> deselect
                        active_eventitem = null
                        if (aep != null) {
                            aep!!.deactivateEvent()
                            aep!!.repaint()
                        }
                        repaint()
                    }
                }

                STATE_MOVING_POSITION -> {
                    guiState = STATE_INACTIVE
                    if (delta_y != 0) {
                        delta_y = 0
                        addToUndoList()
                    } else if (item_was_selected) {
                        active_positionitem = null
                        if (aep != null) {
                            aep!!.deactivatePosition()
                            aep!!.repaint()
                        }
                        repaint()
                    }
                }

                STATE_MOVING_TRACKER -> {
                    guiState = STATE_INACTIVE
                    if (aep != null) {
                        aep!!.isPaused = animPaused
                    }
                    repaint()
                }

                STATE_POPUP -> {}
            }
        }
    }

    //--------------------------------------------------------------------------
    // java.awt.event.MouseMotionListener methods
    //--------------------------------------------------------------------------
    
    public override fun mouseDragged(me: MouseEvent) {
        if (aep != null && (aep!!.writingGIF || !aep!!.engineAnimating)) {
            return
        }

        var my = me.getY()
        my = min(max(my, BORDER_TOP), ladderHeight - BORDER_TOP)

        when (guiState) {
            STATE_INACTIVE, STATE_POPUP -> {}
            STATE_MOVING_EVENT -> {
                val old_delta_y = delta_y
                delta_y = getClippedEventTime(me, active_eventitem!!.event!!)

                if (delta_y != old_delta_y) {
                    moveEventInPattern(active_eventitem!!.eventitem!!)
                    activeEventChanged()
                    repaint()
                }
            }

            STATE_MOVING_POSITION -> {
                val old_delta_y = delta_y
                delta_y = getClippedPositionTime(me, active_positionitem!!.position!!)

                if (delta_y != old_delta_y) {
                    movePositionInPattern(active_positionitem!!)
                    activePositionChanged()
                    repaint()
                }
            }

            STATE_MOVING_TRACKER -> {
                trackerY = my
                this@EditLadderDiagram.repaint()
                if (aep != null) {
                    val scale =
                        (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
                    val newtime = (my - BORDER_TOP).toDouble() * scale
                    aep!!.time = newtime
                    aep!!.repaint()
                }
            }
        }
    }

    //----------------------------------------------------------------------------
    // Utility methods for mouse interactions
    //----------------------------------------------------------------------------
    protected fun findEventLimits(item: LadderEventItem) {
        var tmin = pat.loopStartTime
        var tmax = pat.loopEndTime
        val scale =
            (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()

        for (tr in item.event!!.transitions) {
            when (tr.getType()) {
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
                            ) != null || ev.getPathTransition(tr.path, JMLTransition.TRANS_GRABCATCH) != null
                        ) {
                            break
                        }
                        ev = ev.previous
                    }
                    if (ev == null) {
                        handleFatalException(
                            JuggleExceptionInternal("Null event 1 in mousePressed()", pat)
                        )
                        if (parentframe != null) {
                            parentframe!!.dispose()
                            parentframe = null
                        }
                        return
                    }
                    tmin = max(tmin, ev.t + MIN_THROW_SEP_TIME)

                    // next catch is easy to find
                    ev = tr.outgoingPathLink!!.endEvent
                    if (!ev.hasSameMasterAs(item.event!!)) {
                        tmax = min(tmax, ev.t - MIN_THROW_SEP_TIME)
                    }
                }

                JMLTransition.TRANS_CATCH, JMLTransition.TRANS_SOFTCATCH, JMLTransition.TRANS_GRABCATCH -> {
                    // previous throw is easy to find
                    var ev: JMLEvent? = tr.incomingPathLink!!.startEvent
                    if (!ev!!.hasSameMasterAs(item.event!!)) {
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
                        handleFatalException(
                            JuggleExceptionInternal("Null event 2 in mousePressed()", pat)
                        )
                        if (parentframe != null) {
                            parentframe!!.dispose()
                            parentframe = null
                        }
                        return
                    }
                    tmax = min(tmax, ev.t - MIN_THROW_SEP_TIME)
                }
            }
        }
        delta_y_min = ((tmin - item.event!!.t) / scale).toInt()
        delta_y_max = ((tmax - item.event!!.t) / scale).toInt()
    }

    // Return value of `delta_y` during mouse drag of an event, clipping it to
    // enforce proximity limits between various event types, as well as hard
    // limits `delta_y_min` and `delta_y_max`.
    protected fun getClippedEventTime(me: MouseEvent, event: JMLEvent): Int {
        var dy = me.getY() - start_y

        dy = min(max(dy, delta_y_min), delta_y_max)

        val scale =
            (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        val shift = dy * scale
        val newt = start_t + shift // unclipped new event time

        // Calculate a window (t_excl_min, t_excl_max) of excluded times based on
        // proximity to other events, where `newt` is contained within the window.
        var t_excl_min = newt
        var t_excl_max = newt
        var changed: Boolean

        do {
            changed = false
            var ev = pat.eventList
            var sep: Double

            while (ev != null) {
                if (ev != event && ev.juggler == event.juggler && ev.hand == event.hand) {
                    if (ev.hasThrow && event.hasThrowOrCatch
                        || ev.hasThrowOrCatch && event.hasThrow
                    ) {
                        sep = MIN_THROW_SEP_TIME
                    } else {
                        sep = MIN_EVENT_SEP_TIME
                    }

                    val ev_excl_min = ev.t - sep
                    val ev_excl_max = ev.t + sep

                    if (ev_excl_max > t_excl_max && ev_excl_min <= t_excl_max) {
                        t_excl_max = ev_excl_max
                        changed = true
                    }

                    if (ev_excl_min < t_excl_min && ev_excl_max >= t_excl_min) {
                        t_excl_min = ev_excl_min
                        changed = true
                    }
                }
                ev = ev.next
            }
        } while (changed)

        // System.out.println("t_excl_min = " + t_excl_min + ", t_excl_max = " + t_excl_max);

        // Clip the event time `newt` to whichever end of the exclusion window
        // is closest. First check if each end is feasible.
        val excl_dy_min = floor((t_excl_min - start_t) / scale).toInt()
        val excl_dy_max = ceil((t_excl_max - start_t) / scale).toInt()
        val feasible_min = (excl_dy_min >= delta_y_min && excl_dy_min <= delta_y_max)
        val feasible_max = (excl_dy_max >= delta_y_min && excl_dy_max <= delta_y_max)

        var result_dy = dy

        if (feasible_min && feasible_max) {
            val t_midpoint = 0.5 * (t_excl_min + t_excl_max)
            result_dy = (if (newt <= t_midpoint) excl_dy_min else excl_dy_max)
        } else if (feasible_min && !feasible_max) {
            result_dy = excl_dy_min
        } else if (!feasible_min && feasible_max) {
            result_dy = excl_dy_max
        }

        return result_dy
    }

    protected fun moveEventInPattern(item: LadderEventItem) {
        var ev = item.event

        val scale =
            (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        var shift = delta_y * scale
        var newt = start_t + shift
        if (newt < pat.loopStartTime + scale) {
            // within 1 pixel of top
            shift = pat.loopStartTime - start_t
            newt = pat.loopStartTime
        } else if (newt >= pat.loopEndTime) {
            shift = pat.loopEndTime - 0.0001 - start_t
            newt = pat.loopEndTime - 0.0001
        }

        val throwpath = BooleanArray(pat.numberOfPaths)
        val catchpath = BooleanArray(pat.numberOfPaths)
        val holdpathorig = BooleanArray(pat.numberOfPaths)
        val holdpathnew = BooleanArray(pat.numberOfPaths)
        for (tr in ev!!.transitions) {
            when (tr.getType()) {
                JMLTransition.TRANS_THROW -> throwpath[tr.path - 1] = true
                JMLTransition.TRANS_CATCH, JMLTransition.TRANS_SOFTCATCH, JMLTransition.TRANS_GRABCATCH -> catchpath[tr.path - 1] =
                    true

                JMLTransition.TRANS_HOLDING -> {
                    holdpathorig[tr.path - 1] = true
                    holdpathnew[tr.path - 1] = holdpathorig[tr.path - 1]
                }
            }
        }

        if (newt < ev.t) {  // moving to earlier time
            ev = ev.previous
            while (ev != null && ev.t > newt) {
                if (!ev.hasSameMasterAs(item.event!!) && ev.juggler == item.event!!.juggler && ev.hand == item.event!!.hand) {
                    run {
                        var j = 0
                        while (j < ev.numberOfTransitions) {
                            val tr = ev.getTransition(j)
                            when (tr.getType()) {
                                JMLTransition.TRANS_THROW -> holdpathnew[tr.path - 1] = true
                                JMLTransition.TRANS_CATCH, JMLTransition.TRANS_SOFTCATCH, JMLTransition.TRANS_GRABCATCH -> holdpathnew[tr.path - 1] =
                                    false

                                JMLTransition.TRANS_HOLDING -> if (throwpath[tr.path - 1]) {
                                    ev.removeTransition(j)
                                    if (!ev.isMaster) {
                                        ev.master.removeTransition(j)
                                    }
                                    j-- // next trans moved into slot
                                }
                            }
                            j++
                        }
                    }

                    for (j in 0..<pat.numberOfPaths) {
                        if (catchpath[j]) {
                            var tr =
                                JMLTransition(JMLTransition.TRANS_HOLDING, (j + 1), null, null)
                            ev.addTransition(tr)
                            if (!ev.isMaster) {
                                val pp = ev.pathPermFromMaster!!.inverse
                                tr = JMLTransition(
                                    JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null
                                )
                                ev.master.addTransition(tr)
                            }
                        }
                    }
                }
                ev = ev.previous
            }
        } else if (newt > ev.t) {  // moving to later time
            ev = ev.next
            while (ev != null && ev.t < newt) {
                if (!ev.hasSameMasterAs(item.event!!) && ev.juggler == item.event!!.juggler && ev.hand == item.event!!.hand) {
                    run {
                        var j = 0
                        while (j < ev.numberOfTransitions) {
                            val tr = ev.getTransition(j)
                            when (tr.getType()) {
                                JMLTransition.TRANS_THROW -> holdpathnew[tr.path - 1] = false
                                JMLTransition.TRANS_CATCH, JMLTransition.TRANS_SOFTCATCH, JMLTransition.TRANS_GRABCATCH -> holdpathnew[tr.path - 1] =
                                    true

                                JMLTransition.TRANS_HOLDING -> if (catchpath[tr.path - 1]) {
                                    ev.removeTransition(j)
                                    if (!ev.isMaster) {
                                        ev.master.removeTransition(j)
                                    }
                                    j--
                                }
                            }
                            j++
                        }
                    }

                    for (j in 0..<pat.numberOfPaths) {
                        if (throwpath[j]) {
                            var tr =
                                JMLTransition(JMLTransition.TRANS_HOLDING, (j + 1), null, null)
                            ev.addTransition(tr)
                            if (!ev.isMaster) {
                                val pp = ev.pathPermFromMaster!!.inverse
                                tr = JMLTransition(
                                    JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null
                                )
                                ev.master.addTransition(tr)
                            }
                        }
                    }
                }
                ev = ev.next
            }
        }

        ev = item.event
        val pp = ev!!.pathPermFromMaster!!.inverse
        var new_master_t = start_t + shift

        if (!ev.isMaster) {
            val new_event_t = new_master_t
            new_master_t += ev.master.t - ev.t
            ev.t = new_event_t // update event time so getHashCode() works
            ev = ev.master
        }

        for (j in 0..<pat.numberOfPaths) {
            if (holdpathnew[j] != holdpathorig[j]) {
                if (holdpathnew[j]) {
                    val tr =
                        JMLTransition(JMLTransition.TRANS_HOLDING, pp.getMapping(j + 1), null, null)
                    ev.addTransition(tr)
                } else {
                    val tr =
                        ev.getPathTransition(pp.getMapping(j + 1), JMLTransition.TRANS_HOLDING)
                    if (tr == null) {
                        handleFatalException(
                            JuggleExceptionInternal("Null transition in removing hold", pat)
                        )
                        if (parentframe != null) {
                            parentframe!!.dispose()
                            parentframe = null
                        }
                        return
                    }
                    ev.removeTransition(tr)
                }
            }
        }

        pat.removeEvent(ev)
        ev.t = new_master_t // change time of master
        pat.addEvent(ev) // remove/add cycle keeps events sorted
    }

    protected fun findPositionLimits(item: LadderPositionItem) {
        val tmin = pat.loopStartTime
        val tmax = pat.loopEndTime
        val scale =
            (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()

        delta_y_min = ((tmin - item.position!!.t) / scale).toInt()
        delta_y_max = ((tmax - item.position!!.t) / scale).toInt()
    }

    // Return value of `delta_y` during mouse drag of an event, clipping it to
    // enforce proximity limits between various event types, as well as hard
    // limits `delta_y_min` and `delta_y_max`.
    protected fun getClippedPositionTime(me: MouseEvent, position: JMLPosition): Int {
        var dy = me.getY() - start_y

        dy = min(max(dy, delta_y_min), delta_y_max)

        val scale =
            (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        val shift = dy * scale
        val newt = start_t + shift // unclipped new event time

        // Calculate a window (t_excl_min, t_excl_max) of excluded times based on
        // proximity to other events, where `newt` is contained within the window.
        var t_excl_min = newt
        var t_excl_max = newt
        var changed: Boolean

        do {
            changed = false
            var pos = pat.positionList

            while (pos != null) {
                if (pos != position && pos.juggler == position.juggler) {
                    val pos_excl_min: Double = pos.t - MIN_POSITION_SEP_TIME
                    val pos_excl_max: Double = pos.t + MIN_POSITION_SEP_TIME

                    if (pos_excl_max > t_excl_max && pos_excl_min <= t_excl_max) {
                        t_excl_max = pos_excl_max
                        changed = true
                    }

                    if (pos_excl_min < t_excl_min && pos_excl_max >= t_excl_min) {
                        t_excl_min = pos_excl_min
                        changed = true
                    }
                }
                pos = pos.next
            }
        } while (changed)

        // Clip the position time `newt` to whichever end of the exclusion window
        // is closest. First check if each end is feasible.
        val excl_dy_min = floor((t_excl_min - start_t) / scale).toInt()
        val excl_dy_max = ceil((t_excl_max - start_t) / scale).toInt()
        val feasible_min = (excl_dy_min >= delta_y_min && excl_dy_min <= delta_y_max)
        val feasible_max = (excl_dy_max >= delta_y_min && excl_dy_max <= delta_y_max)

        var result_dy = dy

        if (feasible_min && feasible_max) {
            val t_midpoint = 0.5 * (t_excl_min + t_excl_max)
            result_dy = (if (newt <= t_midpoint) excl_dy_min else excl_dy_max)
        } else if (feasible_min && !feasible_max) {
            result_dy = excl_dy_min
        } else if (!feasible_min && feasible_max) {
            result_dy = excl_dy_max
        }

        return result_dy
    }

    protected fun movePositionInPattern(item: LadderPositionItem) {
        val pos = item.position

        val scale =
            (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()

        var newt = start_t + delta_y * scale
        if (newt < pat.loopStartTime + scale) {
            newt = pat.loopStartTime // within 1 pixel of top
        } else if (newt >= pat.loopEndTime) {
            newt = pat.loopEndTime - 0.0001
        }

        pat.removePosition(pos!!)
        pos.t = newt
        pat.addPosition(pos) // remove/add keeps positions sorted
    }

    protected fun makePopupMenu(laditem: LadderItem?): JPopupMenu {
        val popup = JPopupMenu()

        for (i in popupItems.indices) {
            val name: String? = popupItems[i]

            if (name == null) {
                popup.addSeparator()
                continue
            }

            val item: JMenuItem = JMenuItem(guistrings.getString(name.replace(' ', '_')))
            val command: String? = popupCommands[i]
            item.setActionCommand(command)
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

    //----------------------------------------------------------------------------
    // java.awt.event.ActionListener methods
    //----------------------------------------------------------------------------
    override fun actionPerformed(event: ActionEvent) {
        val command = event.getActionCommand()
        if (command == null) {
            return
        }

        when (command) {
            "changetitle" -> changeTitle()
            "changetiming" -> changeTiming()
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
            else -> handleFatalException(
                JuggleExceptionInternal("unknown item in ELD popup", pat)
            )
        }

        finishPopup()
    }

    protected fun changeTitle() {
        val jd = JDialog(parentframe, guistrings.getString("Change_title"), true)
        val gb = GridBagLayout()
        jd.getContentPane().setLayout(gb)

        val tf = JTextField(20)
        tf.setText(pat.title)

        val okbutton: JButton = JButton(guistrings.getString("OK"))
        okbutton.addActionListener(
            ActionListener { e: ActionEvent? ->
                val newtitle = tf.getText()
                pat.title = newtitle
                jd.dispose()
                addToUndoList()
            })

        jd.getContentPane().add(tf)
        gb.setConstraints(
            tf, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.getContentPane().add(okbutton)
        gb.setConstraints(
            okbutton,
            constraints(GridBagConstraints.LINE_END, 0, 1, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default
        jd.pack()
        jd.setResizable(false)
        jd.setLocationRelativeTo(this)
        jd.setVisible(true)
        parentframe!!.setTitle(pat.title)
    }

    protected fun changeTiming() {
        val jd = JDialog(parentframe, guistrings.getString("Change_timing"), true)
        val gb = GridBagLayout()
        jd.getContentPane().setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab: JLabel = JLabel(guistrings.getString("Rescale_percentage"))
        p1.add(lab)
        gb.setConstraints(
            lab, constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        val tf = JTextField(7)
        tf.setText("100")
        p1.add(tf)
        gb.setConstraints(
            tf, constraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 5, 0, 0))
        )

        val okbutton: JButton = JButton(guistrings.getString("OK"))
        okbutton.addActionListener { e: ActionEvent? ->
            val scale: Double
            try {
                scale = parseDouble(tf.getText()) / 100.0
            } catch (nfe: NumberFormatException) {
                handleUserException(
                    this@EditLadderDiagram,
                    "Number format error in rescale percentage"
                )
                return@addActionListener
            }
            if (scale > 0.0) {
                pat.scaleTime(scale)
                aep!!.time = 0.0
                layoutPattern(true)
                createView()
            }
            jd.dispose()
        }

        jd.getContentPane().add(p1)
        gb.setConstraints(
            p1, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.getContentPane().add(okbutton)
        gb.setConstraints(
            okbutton,
            constraints(GridBagConstraints.LINE_END, 0, 1, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default
        jd.pack()
        jd.setLocationRelativeTo(this)
        jd.setVisible(true)
    }

    protected fun addEventToHand(hand: Int): JMLEvent? {
        var juggler = 1
        if (pat.numberOfJugglers > 1) {
            var mouse_x = popup_x
            val juggler_right_px = (leftX + rightX + jugglerDeltaX) / 2

            while (juggler <= pat.numberOfJugglers) {
                if (mouse_x < juggler_right_px) {
                    break
                }

                mouse_x -= jugglerDeltaX
                juggler++
            }
            if (juggler > pat.numberOfJugglers) {
                juggler = pat.numberOfJugglers
            }
        }

        val scale =
            (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        val evtime = (popup_y - BORDER_TOP).toDouble() * scale

        val evpos = Coordinate()
        try {
            pat.getHandCoordinate(juggler, hand, evtime, evpos)
        } catch (jei: JuggleExceptionInternal) {
            handleFatalException(jei)
            if (parentframe != null) {
                parentframe!!.dispose()
                parentframe = null
            }
            return null
        }

        val ev = JMLEvent()
        ev.localCoordinate = pat.convertGlobalToLocal(evpos, juggler, evtime)
        ev.t = evtime
        ev.setHand(juggler, hand)
        pat.addEvent(ev)

        // add holding transitions to the new event, if hand is filled
        for (i in 0..<pat.numberOfPaths) {
            var holding = false

            var evt = ev.previous
            while (evt != null) {
                val tr = evt.getPathTransition(i + 1, JMLTransition.TRANS_ANY)
                if (tr != null) {
                    if (evt.juggler != ev.juggler || evt.hand != ev.hand) {
                        break
                    }
                    if (tr.getType() == JMLTransition.TRANS_THROW) {
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

        active_eventitem = null
        if (aep != null) {
            aep!!.deactivateEvent()
        }
        layoutPattern(true)
        createView()
        repaint()

        return ev
    }

    protected fun removeEvent() {
        // makePopupMenu() ensures that the event only has hold transitions
        if (popupitem !is LadderEventItem) {
            handleFatalException(
                JuggleExceptionInternal("LadderDiagram illegal remove event", pat)
            )
            return
        }
        var ev = (popupitem as LadderEventItem).event
        if (!ev!!.isMaster) {
            ev = ev.master
        }
        pat.removeEvent(ev)
        active_eventitem = null
        if (aep != null) {
            aep!!.deactivateEvent()
        }
        layoutPattern(true)
        createView()
        repaint()
    }

    protected fun addPositionToJuggler(): JMLPosition {
        var juggler = 1
        if (pat.numberOfJugglers > 1) {
            var mouse_x = popup_x
            val juggler_right_px = (leftX + rightX + jugglerDeltaX) / 2

            while (juggler <= pat.numberOfJugglers) {
                if (mouse_x < juggler_right_px) {
                    break
                }

                mouse_x -= jugglerDeltaX
                juggler++
            }
            if (juggler > pat.numberOfJugglers) {
                juggler = pat.numberOfJugglers
            }
        }

        val scale =
            (pat.loopEndTime - pat.loopStartTime) / (ladderHeight - 2 * BORDER_TOP).toDouble()
        val postime = (popup_y - BORDER_TOP).toDouble() * scale

        val pos = JMLPosition()
        val loc = Coordinate()
        pat.getJugglerPosition(juggler, postime, loc)
        pos.coordinate = loc
        pos.angle = pat.getJugglerAngle(juggler, postime)
        pos.t = postime
        pos.juggler = juggler
        pat.addPosition(pos)

        active_eventitem = null
        if (aep != null) {
            aep!!.deactivateEvent()
        }
        layoutPattern(true)
        createView()
        repaint()

        return pos
    }

    protected fun removePosition() {
        if (popupitem !is LadderPositionItem) {
            handleFatalException(
                JuggleExceptionInternal("LadderDiagram illegal remove position", pat)
            )
            return
        }
        val pos = (popupitem as LadderPositionItem).position
        pat.removePosition(pos!!)
        active_positionitem = null
        if (aep != null) {
            aep!!.deactivatePosition()
        }
        layoutPattern(true)
        createView()
        repaint()
    }

    protected fun defineProp() {
        if (popupitem == null) {
            handleFatalException(JuggleExceptionInternal("defineProp() null popupitem", pat))
            return
        }

        // figure out which path number the user selected
        val pn: Int
        if (popupitem is LadderEventItem) {
            if (popupitem!!.type != LadderItem.TYPE_TRANSITION) {
                handleFatalException(
                    JuggleExceptionInternal("defineProp() bad LadderItem type", pat)
                )
                return
            }

            val ev = (popupitem as LadderEventItem).event
            val transnum = (popupitem as LadderEventItem).transnum
            val tr = ev!!.getTransition(transnum)
            pn = tr.path
        } else {
            pn = (popupitem as LadderPathItem).pathNum
        }

        val pathnum = pn
        val animpropnum = aep!!.animator.animPropNum
        val propnum = animpropnum!![pathnum - 1]
        // final int propnum = pat.getPropAssignment(pathnum);
        // System.out.println("pathnum = " + pathnum + ", propnum = " + propnum);
        val startprop = pat.getProp(propnum)
        val prtypes: Array<String> = Prop.builtinProps

        val jd = JDialog(parentframe, guistrings.getString("Define_prop"), true)
        val gb = GridBagLayout()
        jd.getContentPane().setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab: JLabel = JLabel(guistrings.getString("Prop_type"))
        p1.add(lab)
        gb.setConstraints(
            lab, constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )

        val p2 = JPanel()
        p2.setLayout(gb)

        val cb1 = JComboBox<String>(prtypes)
        p1.add(cb1)
        gb.setConstraints(
            cb1, constraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 10, 0, 0))
        )
        cb1.addActionListener { ae: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            try {
                val pt: Prop?
                if (type.equals(startprop!!.type, ignoreCase = true)) {
                    pt = startprop
                } else {
                    pt = newProp(type)
                }
                makeParametersPanel(p2, pt.getParameterDescriptors()!!)
            } catch (jeu: JuggleExceptionUser) {
                handleUserException(jd, jeu.message)
                return@addActionListener
            }
            jd.pack()
        }
        val bp: Array<String> = Prop.builtinProps
        for (i in bp.indices) {
            if (bp[i].equals(startprop!!.type, ignoreCase = true)) {
                cb1.setSelectedIndex(i)
                break
            }
        }

        val p3 = JPanel()
        p3.setLayout(gb)
        val cancelbutton: JButton = JButton(guistrings.getString("Cancel"))
        p3.add(cancelbutton)
        gb.setConstraints(
            cancelbutton,
            constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        cancelbutton.addActionListener(ActionListener { e: ActionEvent? -> jd.dispose() })
        val okbutton: JButton = JButton(guistrings.getString("OK"))
        p3.add(okbutton)
        gb.setConstraints(
            okbutton, constraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )
        okbutton.addActionListener { e: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            val mod: String?

            try {
                mod = this.parameterList
                // System.out.println("type = " + type + ", mod = " + mod);
                // fail if prop definition is invalid, before we change the pattern
                (PropDef(type.lowercase(Locale.getDefault()), mod)).layoutProp()
            } catch (jeu: JuggleExceptionUser) {
                handleUserException(parentframe, jeu.message)
                return@addActionListener
            }

            // sync paths with current prop list
            for (i in 0..<pat.numberOfPaths) {
                pat.setPropAssignment(i + 1, animpropnum[i])
            }

            // check to see if any other paths are using this prop definition
            var killprop = true
            for (i in 0..<pat.numberOfPaths) {
                if (i != pathnum - 1) {
                    if (animpropnum[i] == propnum) {
                        killprop = false
                        break
                    }
                }
            }

            if (killprop) {
                pat.removeProp(propnum)
            }

            // check to see if a prop like this one has already been defined
            var gotmatch = false
            var matchingprop = 0
            for (i in 1..pat.numberOfProps) {
                val pdef = pat.getPropDef(i)
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
                pat.setPropAssignment(pathnum, matchingprop)
            } else {
                // new prop is different
                val newprop = PropDef(type.lowercase(Locale.getDefault()), mod)
                pat.addProp(newprop)
                pat.setPropAssignment(pathnum, pat.numberOfProps)
            }

            if (active_eventitem != null) {
                activeEventChanged()
            } else {
                layoutPattern(true)
            }
            jd.dispose()
            repaint()
        }

        jd.getContentPane().add(p1)
        gb.setConstraints(
            p1, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.getContentPane().add(p2)
        gb.setConstraints(
            p2, constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )
        jd.getContentPane().add(p3)
        gb.setConstraints(
            p3, constraints(GridBagConstraints.LINE_END, 0, 2, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default

        val loc = Locale.getDefault()
        jd.applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        jd.pack()
        jd.setResizable(false)
        jd.setLocationRelativeTo(this)
        jd.setVisible(true) // blocks until dispose() above
        dialog_controls = null
    }

    protected fun defineThrow() {
        if (popupitem !is LadderEventItem) {
            handleFatalException(JuggleExceptionInternal("defineThrow() class format", pat))
            return
        }
        var ev = (popupitem as LadderEventItem).event
        if (!ev!!.isMaster) {
            ev = ev.master
        }
        val tr = ev.getTransition((popupitem as LadderEventItem).transnum)

        val pptypes: Array<String> = Path.builtinPaths

        val jd = JDialog(parentframe, guistrings.getString("Define_throw"), true)
        val gb = GridBagLayout()
        jd.getContentPane().setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab: JLabel = JLabel(guistrings.getString("Throw_type"))
        p1.add(lab)
        gb.setConstraints(
            lab, constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )

        val p2 = JPanel()
        p2.setLayout(gb)

        val cb1 = JComboBox<String>(pptypes)
        p1.add(cb1)
        gb.setConstraints(
            cb1, constraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 10, 0, 0))
        )
        cb1.addActionListener { ae: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            try {
                val ppt: Path?
                if (type.equals(tr.throwType, ignoreCase = true)) {
                    ppt = tr.outgoingPathLink!!.path
                } else {
                    ppt = newPath(type)
                }
                makeParametersPanel(p2, ppt!!.parameterDescriptors)
            } catch (jeu: JuggleExceptionUser) {
                handleUserException(jd, jeu.message)
                return@addActionListener
            }
            jd.pack()
        }
        val bpp: Array<String> = Path.builtinPaths
        for (i in bpp.indices) {
            if (bpp[i].equals(tr.throwType, ignoreCase = true)) {
                cb1.setSelectedIndex(i)
                break
            }
        }

        val p3 = JPanel()
        p3.setLayout(gb)
        val cancelbutton: JButton = JButton(guistrings.getString("Cancel"))
        p3.add(cancelbutton)
        gb.setConstraints(
            cancelbutton,
            constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        cancelbutton.addActionListener(ActionListener { e: ActionEvent? -> jd.dispose() })
        val okbutton: JButton = JButton(guistrings.getString("OK"))
        p3.add(okbutton)
        gb.setConstraints(
            okbutton, constraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )
        okbutton.addActionListener { e: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            tr.throwType = type.lowercase(Locale.getDefault())

            val mod: String?
            try {
                mod = this.parameterList
            } catch (jeu: JuggleExceptionUser) {
                handleUserException(parentframe, jeu.message)
                return@addActionListener
            }

            tr.mod = mod

            activeEventChanged()
            jd.dispose()
        }

        jd.getContentPane().add(p1)
        gb.setConstraints(
            p1, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.getContentPane().add(p2)
        gb.setConstraints(
            p2, constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )
        jd.getContentPane().add(p3)
        gb.setConstraints(
            p3, constraints(GridBagConstraints.LINE_END, 0, 2, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default

        jd.pack()
        jd.setResizable(false)
        jd.setLocationRelativeTo(this)
        jd.setVisible(true) // blocks until dispose() above
        dialog_controls = null
    }

    protected fun changeCatchStyleTo(type: Int) {
        if (popupitem == null) {
            handleFatalException(JuggleExceptionInternal("No popupitem in case 10", pat))
            return
        }
        if (popupitem !is LadderEventItem) {
            handleFatalException(
                JuggleExceptionInternal("LadderDiagram change to catch class format", pat)
            )
            return
        }
        var ev = (popupitem as LadderEventItem).event
        if (!ev!!.isMaster) {
            ev = ev.master
        }
        // int transnum = ((LadderEventItem)popupitem).transnum;
        val tr = ev.getTransition((popupitem as LadderEventItem).transnum)
        tr.setType(type)
        activeEventChanged()
        repaint()
    }

    protected fun makeLastInEvent() {
        if (popupitem == null) {
            handleFatalException(JuggleExceptionInternal("No popupitem in case 8", pat))
            return
        }
        if (popupitem !is LadderEventItem) {
            handleFatalException(
                JuggleExceptionInternal("LadderDiagram make last transition class format", pat)
            )
            return
        }
        var ev = (popupitem as LadderEventItem).event
        if (!ev!!.isMaster) {
            ev = ev.master
        }
        val tr = ev.getTransition((popupitem as LadderEventItem).transnum)
        ev.removeTransition(tr)
        ev.addTransition(tr) // will add at end
        active_eventitem = null // deselect event since it's moving
        if (aep != null) {
            aep!!.deactivateEvent()
        }
        layoutPattern(true)
        createView()
        repaint()
    }

    // Helper for defineProp() and defineThrow()
    protected fun makeParametersPanel(jp: JPanel, pd: Array<ParameterDescriptor>) {
        jp.removeAll()
        val gb = GridBagLayout()
        jp.setLayout(gb)

        dialog_controls = ArrayList<JComponent>()
        dialog_pd = pd

        if (pd.size != 0) {
            val pdp = JPanel()
            pdp.setLayout(gb)

            for (i in pd.indices) {
                val lab = JLabel(pd[i].name)
                pdp.add(lab)
                gb.setConstraints(
                    lab, constraints(GridBagConstraints.LINE_START, 0, i, Insets(0, 0, 0, 0))
                )
                if (pd[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
                    // JComboBox jcb = new JComboBox(booleanList);
                    val jcb = JCheckBox()
                    pdp.add(jcb)
                    gb.setConstraints(
                        jcb, constraints(GridBagConstraints.LINE_START, 1, i, Insets(2, 5, 2, 0))
                    )
                    dialog_controls!!.add(jcb)
                    val def = (pd[i].value) as Boolean
                    // jcb.setSelectedIndex(def ? 0 : 1);
                    jcb.setSelected(def)
                } else if (pd[i].type == ParameterDescriptor.TYPE_FLOAT) {
                    val tf = JTextField(7)
                    pdp.add(tf)
                    gb.setConstraints(
                        tf, constraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                    )
                    dialog_controls!!.add(tf)
                    val def = (pd[i].value) as Double?
                    tf.setText(def.toString())
                } else if (pd[i].type == ParameterDescriptor.TYPE_CHOICE) {
                    val choices = arrayOfNulls<String>(pd[i].range!!.size)
                    pd[i].range!!.toArray<String?>(choices)

                    val jcb = JComboBox<String?>(choices)
                    jcb.setMaximumRowCount(15)
                    pdp.add(jcb)
                    gb.setConstraints(
                        jcb, constraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                    )
                    dialog_controls!!.add(jcb)

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
                        tf, constraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                    )
                    dialog_controls!!.add(tf)
                    val def = (pd[i].value) as Int?
                    tf.setText(def.toString())

                    tf.addCaretListener(CaretListener { e: CaretEvent? -> })
                } else if (pd[i].type == ParameterDescriptor.TYPE_ICON) {
                    val fpd = pd[i]
                    val fpds = pd
                    val fjp = jp
                    val filename = fpd.value as URL?

                    val icon = ImageIcon(filename, filename.toString())
                    // Scale the image down if it's too big
                    val MAX_HEIGHT = 100f
                    if (icon.getIconHeight() > MAX_HEIGHT) {
                        val scaleFactor = MAX_HEIGHT / icon.getIconHeight()
                        val height = (scaleFactor * icon.getIconHeight()).toInt()
                        val width = (scaleFactor * icon.getIconWidth()).toInt()
                        icon.setImage(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH))
                    }
                    val label = JLabel(icon)

                    // Clicking on the icon launches a file chooser for getting a new image
                    label.addMouseListener(
                        object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent?) {
                                jfc
                                    .setFileFilter(
                                        FileNameExtensionFilter("Image file", "jpg", "jpeg", "gif", "png")
                                    )
                                val result = jfc.showOpenDialog(this@EditLadderDiagram)
                                if (result != JFileChooser.APPROVE_OPTION) {
                                    return
                                }

                                try {
                                    // We have to load the image to get the correct dimensions
                                    // ImageIcon icon = new ImageIcon(source, source.toString());
                                    // Rebuild the paramter descriptions
                                    fpds[0].value = jfc.getSelectedFile().toURI().toURL()
                                    // fpds[1].value = new Integer(icon.getIconWidth());
                                    // fpds[2].value = new Integer(icon.getIconHeight());
                                    // fpds[1].default_value = fpds[1].value;
                                    // fpds[2].default_value = fpds[2].value;
                                    // Remake the parameter panal with new default values.
                                    makeParametersPanel(fjp, fpds)
                                    ((fjp.getTopLevelAncestor()) as JDialog).pack()
                                } catch (ex: MalformedURLException) {
                                    // This should never happen
                                    handleFatalException(
                                        JuggleExceptionUser(errorstrings.getString("Error_malformed_URL."))
                                    )
                                }
                            }
                        })
                    // Add the icon to the panel
                    pdp.add(label)
                    gb.setConstraints(
                        label,
                        constraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 5, 0))
                    )
                    dialog_controls!!.add(label)
                }
            }

            jp.add(pdp)
            gb.setConstraints(
                pdp, constraints(GridBagConstraints.LINE_START, 0, 1, Insets(10, 10, 0, 10))
            )
        }
    }

    @get:Throws(JuggleExceptionUser::class)
    protected val parameterList: String?
        get() {
            var result: String? = null
            val dialog = dialog_pd!!
            for (i in dialog.indices) {
                var term: String? = null
                val control: Any = dialog_controls!!.get(i)
                if (dialog[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
                    // JComboBox jcb = (JComboBox)control;
                    // boolean val = ((jcb.getSelectedIndex() == 0) ? true : false);
                    val jcb = control as JCheckBox
                    val `val` = jcb.isSelected()
                    val def_val = (dialog[i].defaultValue) as Boolean
                    if (`val` != def_val) {
                        term = (`val`).toString()
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_FLOAT) {
                    val tf = control as JTextField
                    try {
                        val `val` = parseDouble(tf.getText())
                        val def_val = (dialog[i].defaultValue) as Double
                        if (`val` != def_val) {
                            term = tf.getText().trim { it <= ' ' }
                        }
                    } catch (nfe: NumberFormatException) {
                        val template: String =
                            errorstrings.getString("Error_number_format")
                        val arguments = arrayOf<Any?>(dialog[i].name)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_CHOICE) {
                    val jcb = control as JComboBox<*>
                    val ind = jcb.getSelectedIndex()
                    val `val` = dialog[i].range!!.get(ind)
                    val def_val = (dialog[i].defaultValue) as String?
                    if (!`val`.equals(def_val, ignoreCase = true)) {
                        term = `val`
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_INT) {
                    val tf = control as JTextField
                    try {
                        val `val` = tf.getText().toInt()
                        val def_val = (dialog[i].defaultValue) as Int
                        if (`val` != def_val) {
                            term = tf.getText().trim { it <= ' ' }
                        }
                    } catch (nfe: NumberFormatException) {
                        val template: String =
                            errorstrings.getString("Error_number_format")
                        val arguments = arrayOf<Any?>(dialog[i].name)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_ICON) {
                    val label = control as JLabel
                    val icon = label.getIcon() as ImageIcon
                    val def: String = dialog[i].defaultValue.toString()
                    if (icon.getDescription() != def) {
                        term = icon.getDescription() // This contains the URL string
                    }
                }

                if (term != null) {
                    term = dialog[i].name + "=" + term

                    if (result == null) {
                        result = term
                    } else {
                        result = result + ";" + term
                    }
                }
            }
            return result
        }

    // Call this at the very end of every popup interaction.
    protected fun finishPopup() {
        popupitem = null

        if (guiState == STATE_POPUP) {
            guiState = STATE_INACTIVE
            if (aep != null) {
                aep!!.isPaused = animPaused
            }
        }
    }

    //----------------------------------------------------------------------------
    // AnimationPanel.AnimationAttachment methods
    //----------------------------------------------------------------------------
    public override fun setAnimationPanel(a: AnimationPanel?) {
        super.setAnimationPanel(a)

        if (a is AnimationEditPanel) {
            aep = a
        }
    }

    //----------------------------------------------------------------------------
    // javax.swing.JComponent methods
    //----------------------------------------------------------------------------
    protected override fun paintComponent(gr: Graphics) {
        if (gr is Graphics2D) {
            gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        if (!paintLadder(gr)) {
            return
        }

        // draw the box around the selected position
        if (active_positionitem != null) {
            gr.setColor(COLOR_SELECTION)
            gr.drawLine(
                active_positionitem!!.xLow - 1,
                active_positionitem!!.yLow - 1,
                active_positionitem!!.xHigh + 1,
                active_positionitem!!.yLow - 1
            )
            gr.drawLine(
                active_positionitem!!.xHigh + 1,
                active_positionitem!!.yLow - 1,
                active_positionitem!!.xHigh + 1,
                active_positionitem!!.yHigh + 1
            )
            gr.drawLine(
                active_positionitem!!.xHigh + 1,
                active_positionitem!!.yHigh + 1,
                active_positionitem!!.xLow,
                active_positionitem!!.yHigh + 1
            )
            gr.drawLine(
                active_positionitem!!.xLow - 1,
                active_positionitem!!.yHigh + 1,
                active_positionitem!!.xLow - 1,
                active_positionitem!!.yLow - 1
            )
        }

        // draw the box around the selected event
        if (active_eventitem != null) {
            gr.setColor(COLOR_SELECTION)
            gr.drawLine(
                active_eventitem!!.xlow - 1,
                active_eventitem!!.ylow - 1,
                active_eventitem!!.xhigh + 1,
                active_eventitem!!.ylow - 1
            )
            gr.drawLine(
                active_eventitem!!.xhigh + 1,
                active_eventitem!!.ylow - 1,
                active_eventitem!!.xhigh + 1,
                active_eventitem!!.yhigh + 1
            )
            gr.drawLine(
                active_eventitem!!.xhigh + 1,
                active_eventitem!!.yhigh + 1,
                active_eventitem!!.xlow,
                active_eventitem!!.yhigh + 1
            )
            gr.drawLine(
                active_eventitem!!.xlow - 1,
                active_eventitem!!.yhigh + 1,
                active_eventitem!!.xlow - 1,
                active_eventitem!!.ylow - 1
            )
        }

        // label the tracker line with the time
        if (guiState == STATE_MOVING_TRACKER) {
            gr.setColor(COLOR_TRACKER)
            gr.drawString(toStringRounded(simTime, 2) + " s", ladderWidth / 2 - 18, trackerY - 5)
        }
    }

    companion object {
        val guistrings: ResourceBundle = JugglingLab.guistrings
        val errorstrings: ResourceBundle = JugglingLab.errorstrings

        // minimum time (seconds) between a throw and another event with transitions
        protected const val MIN_THROW_SEP_TIME: Double = 0.05

        // minimum time (seconds) between all events for a hand
        protected const val MIN_EVENT_SEP_TIME: Double = 0.01

        // minimum time (seconds) between positions for a juggler
        protected const val MIN_POSITION_SEP_TIME: Double = 0.02

        protected val COLOR_SELECTION: Color? = Color.green

        // additional GUI states
        protected const val STATE_MOVING_EVENT: Int = 2
        protected const val STATE_MOVING_POSITION: Int = 3
        protected const val STATE_POPUP: Int = 4

        //----------------------------------------------------------------------------
        // Popup menu and related handlers
        //----------------------------------------------------------------------------
        protected val popupItems: Array<String?> = arrayOf<String?>(
            "Change title...",
            "Change overall timing...",
            null,
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

        protected val popupCommands: Array<String?> = arrayOf<String?>(
            "changetitle",
            "changetiming",
            null,
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
                return !mutableListOf<String?>(
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
                if (mutableListOf<String?>(
                        "changetitle",
                        "changetiming",
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
                        if (tr.getType() != JMLTransition.TRANS_HOLDING) {
                            return false
                        }
                    }

                    // check to make sure we're not allowing the user to delete
                    // an event if it's the last one in that hand.
                    // do this by finding the next event in the same hand; if it
                    // has the same master, it's the only one
                    val hand = evitem.event!!.hand
                    val juggler = evitem.event!!.juggler
                    val evm1 = if (evitem.event!!.isMaster) evitem.event else evitem.event!!.master
                    var ev = evitem.event!!.next
                    while (ev != null) {
                        if ((ev.hand == hand) && (ev.juggler == juggler)) {
                            val evm2 = if (ev.isMaster) ev else ev.master
                            if (evm1 == evm2) {
                                return false
                            }
                            break
                        }
                        ev = ev.next
                    }
                }
            } else if (laditem.type == LadderItem.TYPE_TRANSITION) {
                if (mutableListOf<String?>(
                        "changetitle",
                        "changetiming",
                        "addeventtoleft",
                        "addeventtoright",
                        "addposition",
                        "removeposition",
                        "removeevent"
                    ).contains(command)
                ) return false

                val evitem = laditem as LadderEventItem
                val tr = evitem.event!!.getTransition(evitem.transnum)

                when (command) {
                    "makelast" -> {
                        return evitem.transnum != (evitem.event!!.numberOfTransitions - 1)
                    }

                    "definethrow" -> {
                        return tr.getType() == JMLTransition.TRANS_THROW
                    }

                    "changetocatch" -> {
                        return tr.getType() == JMLTransition.TRANS_SOFTCATCH
                                || tr.getType() == JMLTransition.TRANS_GRABCATCH
                    }

                    "changetosoftcatch" -> {
                        return tr.getType() == JMLTransition.TRANS_CATCH
                                || tr.getType() == JMLTransition.TRANS_GRABCATCH
                    }

                    "changetograbcatch" -> {
                        return tr.getType() == JMLTransition.TRANS_CATCH
                                || tr.getType() == JMLTransition.TRANS_SOFTCATCH
                    }
                }
            } else if (laditem.type == LadderItem.TYPE_POSITION) {
                return !mutableListOf<String?>(
                    "changetitle",
                    "changetiming",
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
                return !mutableListOf<String?>(
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
