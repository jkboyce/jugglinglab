//
// LaidoutPattern.kt
//
// This class represent a JmlPattern that is physically laid out and ready to
// animate.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions", "EmptyRange")

package jugglinglab.layout

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.Constants
import jugglinglab.curve.Curve
import jugglinglab.curve.LineCurve
import jugglinglab.curve.SplineCurve
import jugglinglab.jml.EventImages
import jugglinglab.jml.JmlEvent
import jugglinglab.jml.JmlPattern
import jugglinglab.jml.JmlTransition
import jugglinglab.path.BouncePath
import jugglinglab.renderer.Juggler
import jugglinglab.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max

class LaidoutPattern(val pat: JmlPattern) {
    // events as a linked list
    private var eventList: LayoutEvent? = null

    // list of PathLink objects for each path
    private val pathlinks =
        List(pat.numberOfPaths) { mutableListOf<PathLink>() }

    // list of HandLink objects for each juggler/hand combination
    private val handlinks =
        Array(pat.numberOfJugglers) { Array(2) { mutableListOf<HandLink>() } }

    // coordinates and angles for each juggler
    private val jugglerCurve = mutableListOf<Curve>()
    private val jugglerAngle = mutableListOf<Curve>()

    // whether pattern has a velocity-defining transition, for path and juggler/hand
    private val hasVDPathJMLTransition =
        BooleanArray(pat.numberOfPaths)
    private val hasVDHandJMLTransition =
        Array(pat.numberOfJugglers) { BooleanArray(2) }

    init {
        if (pat.numberOfProps == 0 && pat.numberOfPaths > 0) {
            throw JuggleExceptionInternal("No props defined", pat)
        }
        for (i in 1..pat.numberOfProps) {
            // raise an exception if prop cannot be loaded
            pat.getProp(i)
        }

        pat.events.forEach {
            addEvent(
                LayoutEvent(
                    event = it,
                    primary = it,
                    pathPermFromPrimary = Permutation(pat.numberOfPaths)
                )
            )
        }

        try {
            buildEventList()
            findPositions()
            buildLinkLists()
            layoutHandPaths()

            if (Constants.DEBUG_LAYOUT) {
                println("Data from LaidoutPattern.init:")
                for (i in 0..<pat.numberOfPaths) {
                    println(pathlinks[i].size.toString() + " pathlinks for path " + (i + 1) + ":")
                    for (jtemp in pathlinks[i].indices) {
                        println("   " + pathlinks[i][jtemp])
                    }
                }
                for (i in 0..<pat.numberOfJugglers) {
                    for (j in 0..1) {
                        println(
                            (handlinks[i][j].size
                                .toString() + " handlinks for juggler "
                                    + "${i + 1}, hand ${j + 1}:")
                        )
                        for (k in handlinks[i][j].indices) {
                            println("   " + handlinks[i][j][k])
                        }
                    }
                }
            }
        } catch (jeu: JuggleExceptionUser) {
            throw jeu
        } catch (jei: JuggleExceptionInternal) {
            jei.pattern = pat
            throw jei
        }
    }

    //--------------------------------------------------------------------------
    // Managing the event list
    //--------------------------------------------------------------------------

    private fun addEvent(ev: LayoutEvent) {
        if (eventList == null || ev.t < eventList!!.t) {
            // set `ev` as new list head
            ev.previous = null
            ev.next = eventList
            if (eventList != null) {
                eventList!!.previous = ev
            }
            eventList = ev
            return
        }

        var current = eventList

        while (true) {
            /*
            val combineEvents =
                current!!.t == ev.t && current.hand == ev.hand && current.juggler == ev.juggler

            if (combineEvents) {
                var newJMLEvent = ev.event

                // move all the transitions from `current` to `event`, except those
                // for a path number that already has a transition in `event`.
                for (trCurrent in current.transitions) {
                    if (newJMLEvent.transitions.all { tr -> tr.path != trCurrent.path }) {
                        newJMLEvent = newJMLEvent.withTransition(trCurrent)
                    }
                }

                // then replace `current` with `event` in the list
                newJMLEvent.previous = current.previous
                newJMLEvent.next = current.next
                if (current.next != null) {
                    current.next!!.previous = newJMLEvent
                }
                if (current.previous == null) {
                    eventList = newJMLEvent // new head of the list
                } else {
                    current.previous!!.next = newJMLEvent
                }
                return
            }
*/
            if (ev.t < current!!.t) {
                // insert `ev` before `current`
                ev.next = current
                ev.previous = current.previous
                current.previous!!.next = ev
                current.previous = ev
                return
            }

            if (current.next == null) {
                // append `ev` at the list end, after current
                current.next = ev
                ev.next = null
                ev.previous = current
                return
            }

            current = current.next
        }
    }

    private fun removeEvent(ev: LayoutEvent) {
        if (eventList === ev) {
            eventList = ev.next
            if (eventList != null) {
                eventList!!.previous = null
            }
            return
        }

        ev.next?.previous = ev.previous
        ev.previous?.next = ev.next
    }

    //--------------------------------------------------------------------------
    // Step 1: construct the list of events
    // Extend events in list using known symmetries
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    private fun buildEventList() {
        // figure out how many events there are
        var numevents = 0
        var current = eventList
        while (current != null) {
            if (current.juggler !in 1..pat.numberOfJugglers) {
                val message = jlGetStringResource(Res.string.error_juggler_outofrange)
                throw JuggleExceptionUser(message)
            }
            if (current.isPrimary) {
                ++numevents
            } else {
                removeEvent(current)
            }
            current = current.next
        }
        // construct event images for extending event list
        val ei = arrayOfNulls<EventImages>(numevents)
        current = eventList
        for (i in 0..<numevents) {
            ei[i] = EventImages(pat, current!!.primary)
            current = current.next
        }

        // arrays used for creating the event list
        val needHandEvent = Array(pat.numberOfJugglers) { BooleanArray(2) }
        val needVDHandEvent = Array(pat.numberOfJugglers) { BooleanArray(2) }
        val needPathEvent = BooleanArray(pat.numberOfPaths)
        val needSpecialPathEvent = BooleanArray(pat.numberOfPaths)

        // make sure each hand and path are hit at least once
        for (i in 0..<pat.numberOfJugglers) {
            var hasJMLTransitionForLeft = false
            var hasJMLTransitionForRight = false
            hasVDHandJMLTransition[i][1] = false
            hasVDHandJMLTransition[i][0] = false

            for (j in 0..<numevents) {
                if (!hasJMLTransitionForLeft) {
                    hasJMLTransitionForLeft =
                        ei[j]!!.hasJmlTransitionForHand(i + 1, JmlEvent.LEFT_HAND)
                }
                if (!hasJMLTransitionForRight) {
                    hasJMLTransitionForRight =
                        ei[j]!!.hasJmlTransitionForHand(i + 1, JmlEvent.RIGHT_HAND)
                }
                if (!hasVDHandJMLTransition[i][0]) {
                    hasVDHandJMLTransition[i][0] =
                        ei[j]!!.hasVdJmlTransitionForHand(i + 1, JmlEvent.LEFT_HAND)
                }
                if (!hasVDHandJMLTransition[i][1]) {
                    hasVDHandJMLTransition[i][1] =
                        ei[j]!!.hasVdJmlTransitionForHand(i + 1, JmlEvent.RIGHT_HAND)
                }
            }
            if (!hasJMLTransitionForLeft) {
                val message = jlGetStringResource(Res.string.error_no_left_events, i + 1)
                throw JuggleExceptionUser(message)
            }
            if (!hasJMLTransitionForRight) {
                val message = jlGetStringResource(Res.string.error_no_right_events, i + 1)
                throw JuggleExceptionUser(message)
            }
            needVDHandEvent[i][0] = hasVDHandJMLTransition[i][0] // set up for later
            needVDHandEvent[i][1] = hasVDHandJMLTransition[i][1]
            needHandEvent[i][1] = true
            needHandEvent[i][0] = true
        }
        for (i in 0..<pat.numberOfPaths) {
            var hasPathJMLTransition = false
            hasVDPathJMLTransition[i] = false

            for (j in 0..<numevents) {
                if (!hasPathJMLTransition) {
                    hasPathJMLTransition = ei[j]!!.hasJmlTransitionForPath(i + 1)
                }
                if (!hasVDPathJMLTransition[i]) {
                    hasVDPathJMLTransition[i] = ei[j]!!.hasVdJmlTransitionForPath(i + 1)
                }
            }
            if (!hasPathJMLTransition) {
                val message = jlGetStringResource(Res.string.error_no_path_events, i + 1)
                throw JuggleExceptionUser(message)
            }
            needPathEvent[i] = true // set up for later
            needSpecialPathEvent[i] = false
        }

        // queue used to store events while building event list
        val eventQueue = Array(numevents) { i -> ei[i]!!.previous }

        // start by extending each primary event backward in time
        var contin: Boolean
        do {
            // find latest event in queue
            var maxEventImage = eventQueue[0]
            var maxtime = maxEventImage.event.t
            var maxnum = 0
            for (i in 1..<numevents) {
                if (eventQueue[i].event.t > maxtime) {
                    maxEventImage = eventQueue[i]
                    maxtime = maxEventImage.event.t
                    maxnum = i
                }
            }

            addEvent(
                LayoutEvent(
                    event = maxEventImage.event,
                    primary = maxEventImage.primary,
                    pathPermFromPrimary = maxEventImage.pathPermFromPrimary
                )
            )
            eventQueue[maxnum] = ei[maxnum]!!.previous // restock queue

            // now update the needs arrays, so we know when to stop
            if (maxtime < pat.loopStartTime) {
                val jug = maxEventImage.event.juggler - 1
                val han = JmlEvent.handIndex(maxEventImage.event.hand)

                if (!hasVDHandJMLTransition[jug][han]) {
                    needHandEvent[jug][han] = false
                }

                for (tr in maxEventImage.event.transitions) {
                    val path = tr.path - 1

                    when (tr.type) {
                        JmlTransition.TRANS_THROW -> {
                            needPathEvent[path] = false
                            needHandEvent[jug][han] = false
                            needVDHandEvent[jug][han] = false
                            needSpecialPathEvent[path] = false
                        }

                        JmlTransition.TRANS_CATCH, JmlTransition.TRANS_GRABCATCH -> {}
                        JmlTransition.TRANS_SOFTCATCH -> {
                            if (needVDHandEvent[jug][han]) {
                                // need corresponding throw to get velocity
                                needSpecialPathEvent[path] = true
                            }
                            needHandEvent[jug][han] = false
                            needVDHandEvent[jug][han] = false
                        }

                        JmlTransition.TRANS_HOLDING -> if (!hasVDPathJMLTransition[path]) {
                            // if no throws for this path, then done
                            needPathEvent[path] = false
                        }

                        else -> throw JuggleExceptionInternal(
                            "Unrecognized transition type in buildEventList()",
                            pat
                        )
                    }
                }
            }
            // do we need to continue adding earlier events?
            contin = false
            for (i in 0..<pat.numberOfJugglers) {
                contin = contin or needHandEvent[i][0]
                contin = contin or needHandEvent[i][1]
                contin = contin or needVDHandEvent[i][0]
                contin = contin or needVDHandEvent[i][1]
            }
            for (i in 0..<pat.numberOfPaths) {
                contin = contin or needPathEvent[i]
                contin = contin or needSpecialPathEvent[i]
            }
        } while (contin)

        // reset things to go forward in time
        for (i in 0..<pat.numberOfJugglers) {
            needVDHandEvent[i][0] = hasVDHandJMLTransition[i][0]
            needVDHandEvent[i][1] = hasVDHandJMLTransition[i][1]
            needHandEvent[i][1] = true
            needHandEvent[i][0] = true
        }
        for (i in 0..<pat.numberOfPaths) {
            needPathEvent[i] = true
            needSpecialPathEvent[i] = false
        }
        for (i in 0..<numevents) {
            ei[i]!!.resetPosition()
            eventQueue[i] = ei[i]!!.next
        }

        do {
            // find earliest event in queue
            var minEventImage = eventQueue[0]
            var mintime = minEventImage.event.t
            var minnum = 0
            for (i in 1..<numevents) {
                if (eventQueue[i].event.t < mintime) {
                    minEventImage = eventQueue[i]
                    mintime = minEventImage.event.t
                    minnum = i
                }
            }

            addEvent(
                LayoutEvent(
                    event = minEventImage.event,
                    primary = minEventImage.primary,
                    pathPermFromPrimary = minEventImage.pathPermFromPrimary
                )
            )
            eventQueue[minnum] = ei[minnum]!!.next // restock queue

            // now update the needs arrays, so we know when to stop
            if (mintime > pat.loopEndTime) {
                val jug = minEventImage.event.juggler - 1
                val han = JmlEvent.handIndex(minEventImage.event.hand)

                // if this hand has no throws/catches, then need to build out event list
                // past a certain time, due to how the hand layout is done in this case
                // (see layoutHandPaths() below)
                if (!hasVDHandJMLTransition[jug][han]
                    && mintime > (2 * pat.loopEndTime - pat.loopStartTime)
                ) {
                    needHandEvent[jug][han] = false
                }

                for (tr in minEventImage.event.transitions) {
                    val path = tr.path - 1

                    when (tr.type) {
                        JmlTransition.TRANS_THROW -> {
                            needPathEvent[path] = false
                            if (needVDHandEvent[jug][han]) {
                                // need corresponding catch to get velocity
                                needSpecialPathEvent[path] = true
                            }
                            needHandEvent[jug][han] = false
                            needVDHandEvent[jug][han] = false
                        }

                        JmlTransition.TRANS_CATCH, JmlTransition.TRANS_GRABCATCH -> {
                            needPathEvent[path] = false
                            needSpecialPathEvent[path] = false
                        }

                        JmlTransition.TRANS_SOFTCATCH -> {
                            needPathEvent[path] = false
                            needHandEvent[jug][han] = false
                            needVDHandEvent[jug][han] = false
                            needSpecialPathEvent[path] = false
                        }

                        JmlTransition.TRANS_HOLDING -> if (!hasVDPathJMLTransition[path]) {
                            // no throws for this path, done
                            needPathEvent[path] = false
                        }

                        else -> throw JuggleExceptionInternal(
                            "Unrecognized transition type in buildEventList()",
                            pat
                        )
                    }
                }
            }
            // do we need to continue adding later events?
            contin = false
            for (i in 0..<pat.numberOfJugglers) {
                contin = contin or needHandEvent[i][0]
                contin = contin or needHandEvent[i][1]
                contin = contin or needVDHandEvent[i][0]
                contin = contin or needVDHandEvent[i][1]
            }
            for (i in 0..<pat.numberOfPaths) {
                contin = contin or needPathEvent[i]
                contin = contin or needSpecialPathEvent[i]
            }
        } while (contin)
    }

    //--------------------------------------------------------------------------
    // Step 2: find positions/angles for all jugglers at all points in time,
    // using <position> tags. This is done by finding spline functions passing
    // through the specified locations and angles.
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    private fun findPositions() {
        for (i in 1..pat.numberOfJugglers) {
            val num = pat.positions.count { it.juggler == i }

            // curves for body position and angle, respectively
            val jcurve = SplineCurve()
            val jangle = when (Constants.ANGLE_LAYOUT_METHOD) {
                Curve.CURVE_SPLINE -> SplineCurve()
                else -> LineCurve()
            }

            if (num == 0) {
                // no positions for this juggler
                val times = doubleArrayOf(pat.loopStartTime, pat.loopEndTime)
                val positions = Array(2) { Coordinate() }
                val angles = Array(2) { Coordinate() }

                // apply some defaults
                if (pat.numberOfJugglers == 1) {
                    positions[0].setCoordinate(0.0, 0.0, 100.0)
                    angles[0].setCoordinate(0.0, 0.0, 0.0)
                } else {
                    var r = 70.0
                    val theta = 360 / pat.numberOfJugglers.toDouble()
                    if (r * sin(Math.toRadians(0.5 * theta)) < 65) {
                        r = 65 / sin(Math.toRadians(0.5 * theta))
                    }
                    positions[0].setCoordinate(
                        r * cos(Math.toRadians(theta * (i - 1).toDouble())),
                        r * sin(Math.toRadians(theta * (i - 1).toDouble())),
                        100.0
                    )
                    angles[0].setCoordinate(90 + theta * (i - 1).toDouble(), 0.0, 0.0)
                }
                positions[1] = positions[0]
                angles[1] = angles[0]

                jcurve.setCurve(times, positions, arrayOfNulls(2))
                jangle.setCurve(times, angles, arrayOfNulls(2))
            } else {
                val times = DoubleArray(num + 1)
                val positions = Array(num + 1) { Coordinate() }
                val angles = Array(num + 1) { Coordinate() }

                var j = 0
                for (pos in pat.positions) {
                    if (pos.juggler == i) {
                        times[j] = pos.t
                        positions[j] = pos.coordinate
                        angles[j] = Coordinate(pos.angle, 0.0, 0.0)
                        ++j
                    }
                }
                times[num] = times[0] + pat.loopEndTime - pat.loopStartTime
                positions[num] = positions[0]
                // copy below in case we have nonzero winding number
                angles[num] = angles[0].copy()

                // ensure consecutive angles not more than 180 degrees apart
                j = 1
                while (j <= num) {
                    while ((angles[j].x - angles[j - 1].x) > 180) {
                        angles[j].x -= 360.0
                    }
                    while ((angles[j].x - angles[j - 1].x) < -180) {
                        angles[j].x += 360.0
                    }
                    ++j
                }

                jcurve.setCurve(times, positions, arrayOfNulls(num + 1))
                jangle.setCurve(times, angles, arrayOfNulls(num + 1))
            }
            jcurve.calcCurve()
            jangle.calcCurve()
            jugglerCurve.add(jcurve)
            jugglerAngle.add(jangle)
        }
    }

    //--------------------------------------------------------------------------
    // Step 3: construct the links connecting events: PathLinks and HandLinks
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun buildLinkLists() {
        val incomingPathLink: HashMap<IdentityKey<JmlTransition>, PathLink> = HashMap()
        val outgoingPathLink: HashMap<IdentityKey<JmlTransition>, PathLink> = HashMap()

        for (path in 1..pat.numberOfPaths) {
            var ev = eventList
            var lastev: LayoutEvent? = null
            var lasttr: JmlTransition? = null

            done1@ while (true) {
                // find the next transition for this path
                var tr: JmlTransition?
                while (true) {
                    tr = ev!!.event.getPathTransition(path, JmlTransition.TRANS_ANY)
                    if (tr != null) {
                        break
                    }
                    ev = ev.next
                    if (ev == null) {
                        break@done1
                    }
                }

                if (lastev != null) {
                    val pl = PathLink(
                        path + 1,
                        getGlobalCoordinate(lastev.event),
                        lastev,
                        getGlobalCoordinate(ev.event),
                        ev
                    )

                    when (tr.type) {
                        JmlTransition.TRANS_THROW, JmlTransition.TRANS_HOLDING -> {
                            if (lasttr!!.type == JmlTransition.TRANS_THROW) {
                                val message =
                                    jlGetStringResource(Res.string.error_successive_throws, path)
                                throw JuggleExceptionUser(message)
                            }
                            if (lastev.juggler != ev.juggler) {
                                val message =
                                    jlGetStringResource(Res.string.error_juggler_changed, path)
                                throw JuggleExceptionUser(message)
                            }
                            if (lastev.hand != ev.hand) {
                                val message =
                                    jlGetStringResource(Res.string.error_hand_changed, path)
                                throw JuggleExceptionUser(message)
                            }
                            pl.setInHand(ev.juggler, ev.hand)
                        }

                        JmlTransition.TRANS_CATCH, JmlTransition.TRANS_SOFTCATCH, JmlTransition.TRANS_GRABCATCH -> {
                            if (lasttr!!.type != JmlTransition.TRANS_THROW) {
                                val message =
                                    jlGetStringResource(Res.string.error_successive_catches, path)
                                throw JuggleExceptionUser(message)
                            }
                            pl.setThrow(lasttr.throwType!!, lasttr.throwMod)
                        }

                        else -> throw JuggleExceptionInternal(
                            "unrecognized transition type in buildLinkLists()",
                            pat
                        )
                    }

                    pathlinks[path - 1].add(pl)
                    outgoingPathLink[IdentityKey(lasttr)] = pl
                    incomingPathLink[IdentityKey(tr)] = pl
                }

                lastev = ev
                lasttr = tr
                ev = ev.next
                if (ev == null) {
                    break
                }
            }

            if (pathlinks[path - 1].isEmpty()) {
                throw JuggleExceptionInternal("No event found for path $path", pat)
            }
        }

        // build the HandLink lists

        for (juggler in 1..pat.numberOfJugglers) {
            for (h in 0..1) {
                val hand = if (h == 0) JmlEvent.LEFT_HAND else JmlEvent.RIGHT_HAND

                var ev = eventList
                var lastev: LayoutEvent? = null
                var vr: VelocityRef?
                var lastvr: VelocityRef? = null

                done2@ while (true) {
                    // find the next event touching hand
                    while (ev!!.juggler != juggler || ev.hand != hand) {
                        ev = ev.next ?: break@done2
                    }

                    // find velocity of hand path ending
                    vr = null
                    if (ev.juggler == juggler && ev.hand == hand) {
                        for (tr in ev.event.transitions) {
                            if (tr.type == JmlTransition.TRANS_THROW) {
                                val pl = outgoingPathLink[IdentityKey(tr)]
                                if (pl != null) {
                                    vr = VelocityRef(pl.path!!, VelocityRef.VR_THROW)
                                }
                            } else if (tr.type == JmlTransition.TRANS_SOFTCATCH) {
                                val pl = incomingPathLink[IdentityKey(tr)]
                                if (pl != null) {
                                    vr = VelocityRef(pl.path!!, VelocityRef.VR_SOFTCATCH)
                                }
                            } else if (tr.type == JmlTransition.TRANS_CATCH) {
                                val pl = incomingPathLink[IdentityKey(tr)]
                                if (pl != null) {
                                    vr = VelocityRef(pl.path!!, VelocityRef.VR_CATCH)
                                }
                            }
                            // can skip adding VelocityRef for GRABCATCH because it's
                            // never used by hand layout
                        }
                    }

                    if (lastev != null) {
                        // add HandLink from lastev to ev
                        val hl = HandLink(juggler - 1, hand, lastev, ev)
                        hl.startVelocityRef = lastvr  // may be null, which is ok
                        hl.endVelocityRef = vr
                        handlinks[juggler - 1][h].add(hl)
                    }
                    lastev = ev
                    lastvr = vr
                    ev = ev.next ?: break
                }
            }
        }
    }

    //--------------------------------------------------------------------------
    // Step 4: do a physical layout of the handlink paths
    // (Props were physically laid out in PathLink.setThrow() in Step 3 above)
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    private fun layoutHandPaths() {
        // go through HandLink lists, creating Path objects and calculating paths

        for (j in 0..<pat.numberOfJugglers) {
            for (h in 0..1) {
                // There are two cases: a hand has throw or softcatch events (which define
                // hand velocities at points in time), or it does not (no velocities known).
                // To determine the spline paths, we need to solve for hand velocity at each
                // of its events, but this is done differently in the two cases.

                if (hasVDHandJMLTransition[j][h]) {
                    var startlink: HandLink? = null
                    var num = 0

                    for (k in handlinks[j][h].indices) {
                        val hl = handlinks[j][h][k]

                        var vr = hl.startVelocityRef
                        if (vr != null
                            && (vr.source == VelocityRef.VR_THROW
                                    || vr.source == VelocityRef.VR_SOFTCATCH)
                        ) {
                            // this is guaranteed to happen before the loop start time,
                            // given the way we built the event list above
                            startlink = hl
                            num = 1
                        }

                        vr = hl.endVelocityRef
                        if (startlink != null && vr != null && (vr.source == VelocityRef.VR_THROW
                                    || vr.source == VelocityRef.VR_SOFTCATCH)
                        ) {
                            val times = DoubleArray(num + 1)
                            val pos = Array(num + 1) { Coordinate() }
                            val velocities = arrayOfNulls<Coordinate>(num + 1)
                            val hp: Curve = SplineCurve()

                            for (l in 0..<num) {
                                val hl2 = handlinks[j][h][k - num + 1 + l]
                                times[l] = hl2.startEvent.t
                                pos[l] = getGlobalCoordinate(hl2.startEvent.event)
                                val vr2 = hl2.startVelocityRef
                                if (l > 0 && vr2 != null && vr2.source == VelocityRef.VR_CATCH) {
                                    velocities[l] = vr2.velocity
                                }
                                hl2.handCurve = hp
                            }
                            times[num] = hl.endEvent.t
                            pos[num] = getGlobalCoordinate(hl.endEvent.event)
                            velocities[0] = startlink.startVelocityRef!!.velocity
                            velocities[num] = hl.endVelocityRef!!.velocity

                            hp.setCurve(times, pos, velocities)
                            hp.calcCurve()
                            startlink = null
                        }
                        ++num
                    }
                } else {
                    // Build chain and solve for velocities. This implementation is a little
                    // inefficient since it builds the second chain by a duplicate calculation rather
                    // than a copy. Sketch of algorithm:
                    //    find first handlink that straddles loopStartTime -- call it startlink
                    //    startevent = first event in startlink
                    //    delayedstartevent = corresponding event 1 delay period after startevent
                    //    find handlink that ends with delayedstartevent -- call it endlink
                    //    build spline hand path from startlink to endlink, and calculate (chain 1)
                    //    startlink = next link after endlink
                    //    delayed2startevent = corresponding event 1 delay period after delayedstartevent
                    //    find handlink that ends with delayed2startevent -- call it endlink
                    //    build spline hand path from startlink to endlink, and calculate (chain 2)
                    var k = 0
                    var hl: HandLink? = null
                    while (k < handlinks[j][h].size) {
                        hl = handlinks[j][h][k]
                        if (hl.endEvent.t > pat.loopStartTime) {
                            break
                        }
                        ++k
                    }

                    for (chain in 0..1) {
                        val startlink = hl
                        val startevent = startlink!!.startEvent
                        var num = 1 // number of links in chain
                        while (!hl!!.endEvent.isDelayOf(startevent)) {
                            hl = handlinks[j][h][++k]
                            ++num
                        }
                        val times = DoubleArray(num + 1)
                        val pos = Array(num + 1) { Coordinate() }
                        val hp: Curve = SplineCurve()

                        for (l in 0..<num) {
                            val hl2 = handlinks[j][h][k - num + 1 + l]
                            pos[l] = getGlobalCoordinate(hl2.startEvent.event)
                            times[l] = hl2.startEvent.t
                            hl2.handCurve = hp
                        }
                        pos[num] = getGlobalCoordinate(hl.endEvent.event)
                        times[num] = hl.endEvent.t
                        // all velocities are null (unknown) -> signal to calculate
                        hp.setCurve(times, pos, arrayOfNulls(num + 1))
                        hp.calcCurve()

                        if (chain == 0) {
                            hl = handlinks[j][h][++k]
                        }
                    }
                }
            }
        }
    }

    // Useful for debugging.

    @Suppress("unused")
    private fun printEventList() {
        val sb = StringBuilder()
        var current = eventList
        while (current != null) {
            if (current.isPrimary) {
                sb.append("  Primary event:\n")
            } else {
                sb.append("  Image event; primary at t=" + current.primary.t + "\n")
            }
            current.event.writeJml(sb)
            current = current.next
        }
        println(sb.toString())
    }

    //--------------------------------------------------------------------------
    // Public methods to optimize and animate the pattern
    //--------------------------------------------------------------------------

    val pathLinks: List<List<PathLink>>
        get() = pathlinks

    // Return path coordinate in global frame.

    @Throws(JuggleExceptionInternal::class)
    fun getPathCoordinate(path: Int, time: Double, newPosition: Coordinate) {
        for (pl in pathlinks[path - 1]) {
            if (time >= pl.startEvent.t && time <= pl.endEvent.t) {
                if (pl.isInHand) {
                    val jug = pl.holdingJuggler
                    val hand = pl.holdingHand
                    getHandCoordinate(jug, hand, time, newPosition)
                } else {
                    pl.path!!.getCoordinate(time, newPosition)
                }
                return
            }
        }
        throw JuggleExceptionInternal("time t=$time is out of path range", pat)
    }

    // Check if a given hand is holding the path at a given time.

    fun isHandHoldingPath(juggler: Int, hand: Int, time: Double, path: Int): Boolean {
        for (pl in pathlinks[path - 1]) {
            if (!pl.isInHand) continue
            if (pl.holdingJuggler != juggler) continue
            if (pl.holdingHand != hand) continue
            if (time >= pl.startEvent.t && time <= pl.endEvent.t) {
                return true
            }
        }
        return false
    }

    // Check if a given hand is holding any path at the given time.

    fun isHandHolding(juggler: Int, hand: Int, time: Double): Boolean {
        for (path in 1..pat.numberOfPaths) {
            if (isHandHoldingPath(juggler, hand, time, path)) return true
        }
        return false
    }

    // Return orientation of prop on given path, in global frame
    // result is {pitch, yaw, roll}.

    @Suppress("unused")
    fun getPathOrientation(path: Int, time: Double, axis: Coordinate): Double {
        axis.x = 0.0 // components of unit vector to rotate around
        axis.y = 0.0
        axis.z = 1.0
        return (3 * time)
    }

    // Return juggler coordinate in global frame.

    fun getJugglerPosition(juggler: Int, time: Double, newPosition: Coordinate) {
        var time = time
        val p = jugglerCurve[juggler - 1]
        while (time < p.startTime) {
            time += pat.loopEndTime - pat.loopStartTime
        }
        while (time > p.endTime) {
            time -= pat.loopEndTime - pat.loopStartTime
        }
        p.getCoordinate(time, newPosition)
    }

    // Return angle (in degrees) between local x axis and global x axis
    // (rotation around vertical z axis).

    fun getJugglerAngle(juggler: Int, time: Double): Double {
        var time = time
        val p = jugglerAngle[juggler - 1]

        while (time < p.startTime) {
            time += pat.loopEndTime - pat.loopStartTime
        }
        while (time > p.endTime) {
            time -= pat.loopEndTime - pat.loopStartTime
        }

        val coord = Coordinate()
        p.getCoordinate(time, coord)

        return coord.x
    }

    // Return the global coordinate of an event.

    fun getGlobalCoordinate(ev: JmlEvent): Coordinate {
        val lc = ev.localCoordinate
        return convertLocalToGlobal(lc, ev.juggler, ev.t)
    }

    // Convert from local juggler frame to global frame.

    fun convertLocalToGlobal(lc: Coordinate, juggler: Int, time: Double): Coordinate {
        val origin = Coordinate()
        getJugglerPosition(juggler, time, origin)
        val angle = Math.toRadians(getJugglerAngle(juggler, time))
        lc.y += Juggler.PATTERN_Y

        return Coordinate(
            origin.x + lc.x * cos(angle) - lc.y * sin(angle),
            origin.y + lc.x * sin(angle) + lc.y * cos(angle),
            origin.z + lc.z
        )
    }

    // Convert from global to local frame for a juggler.

    fun convertGlobalToLocal(gc: Coordinate?, juggler: Int, t: Double): Coordinate {
        val origin = Coordinate()
        getJugglerPosition(juggler, t, origin)
        val angle = Math.toRadians(getJugglerAngle(juggler, t))
        val c2 = Coordinate.sub(gc, origin)

        val lc =
            Coordinate(
                c2!!.x * cos(angle) + c2.y * sin(angle),
                -c2.x * sin(angle) + c2.y * cos(angle),
                c2.z
            )
        lc.y -= Juggler.PATTERN_Y
        return lc
    }

    // Return hand coordinate in global frame.

    @Throws(JuggleExceptionInternal::class)
    fun getHandCoordinate(juggler: Int, hand: Int, time: Double, newPosition: Coordinate) {
        var time = time
        val handindex = if (hand == JmlEvent.LEFT_HAND) 0 else 1

        while (time < pat.loopStartTime) {
            time += pat.loopEndTime - pat.loopStartTime
        }
        while (time >= pat.loopEndTime) {
            time -= pat.loopEndTime - pat.loopStartTime
        }

        for (hl in handlinks[juggler - 1][handindex]) {
            if (time >= hl.startEvent.t && time < hl.endEvent.t) {
                val hp = hl.handCurve ?: throw JuggleExceptionInternal(
                    "getHandCoordinate() null pointer at t=$time",
                    pat
                )
                hp.getCoordinate(time, newPosition)
                return
            }
        }
        throw JuggleExceptionInternal(
            "time t=$time (j=$juggler,h=$handindex) is out of handpath range",
            pat
        )
    }

    // Get volume of any catch made between time1 and time2.
    //
    // If no catch then return 0.

    fun getPathCatchVolume(path: Int, time1: Double, time2: Double): Double {
        var wasinair = false
        var gotcatch = false

        var i = 0
        while (i < pathlinks[path - 1].size) {
            val pl1 = pathlinks[path - 1][i]
            if (time1 >= pl1.startEvent.t && time1 <= pl1.endEvent.t) {
                break
            }
            ++i
        }
        if (i == pathlinks[path - 1].size) {
            return 0.0
        }
        while (true) {
            val pl2 = pathlinks[path - 1][i]
            if (!pl2.isInHand) {
                wasinair = true
            }
            if (pl2.isInHand && wasinair) {
                gotcatch = true
                break
            }
            if (time2 in pl2.startEvent.t..pl2.endEvent.t) {
                break
            }
            ++i
            if (i == pathlinks[path - 1].size) {
                i = 0
            }
        }

        // We don't adjust the playback volume of the audio clip, so this is just
        // yes/no for now
        return if (gotcatch) 1.0 else 0.0
    }

    // Get volume of any bounce between time1 and time2.
    //
    // If no bounce then return 0.

    fun getPathBounceVolume(path: Int, time1: Double, time2: Double): Double {
        var i = 0
        var pl: PathLink

        while (i < pathlinks[path - 1].size) {
            pl = pathlinks[path - 1][i]
            if (time1 >= pl.startEvent.t && time1 <= pl.endEvent.t) {
                break
            }
            i++
        }
        if (i == pathlinks[path - 1].size) {
            return 0.0
        }
        while (true) {
            pl = pathlinks[path - 1][i]
            val p = pl.path
            if (p is BouncePath) {
                val vol = p.getBounceVolume(time1, time2)
                if (vol > 0) {
                    return vol
                }
            }
            if (time2 >= pl.startEvent.t && time2 <= pl.endEvent.t) {
                break
            }

            ++i
            if (i == pathlinks[path - 1].size) {
                i = 0
            }
        }

        return 0.0
    }

    fun getPathMax(path: Int): Coordinate? {  // maximum of each coordinate
        var result: Coordinate? = null
        val t1 = pat.loopStartTime
        val t2 = pat.loopEndTime

        for (pl in pathlinks[path - 1]) {
            if (pl.isInHand) {
                val coord2 = getHandMax(pl.holdingJuggler, pl.holdingHand)
                result = Coordinate.max(result, coord2)
            } else {
                val coord2 = pl.path!!.getMax(t1, t2)
                result = Coordinate.max(result, coord2)
            }
        }
        return result
    }

    fun getPathMin(path: Int): Coordinate? {
        var result: Coordinate? = null
        val t1 = pat.loopStartTime
        val t2 = pat.loopEndTime

        for ((i, pl) in pathlinks[path - 1].withIndex()) {
            if (pl.isInHand) {
                if (Constants.DEBUG_LAYOUT) {
                    println(
                        "Path min $path link $i: HandMin = " +
                                getHandMin(pl.holdingJuggler, pl.holdingHand)
                    )
                }
                result = Coordinate.min(result, getHandMin(pl.holdingJuggler, pl.holdingHand))
            } else {
                if (Constants.DEBUG_LAYOUT) {
                    println("Path min $path link : PathMin = " + pl.path!!.getMin(t1, t2))
                }
                result = Coordinate.min(result, pl.path!!.getMin(t1, t2))
            }
        }
        return result
    }

    fun getHandMax(juggler: Int, hand: Int): Coordinate? {
        var result: Coordinate? = null
        val t1 = pat.loopStartTime
        val t2 = pat.loopEndTime
        val handnum = if (hand == JmlEvent.LEFT_HAND) 0 else 1

        for (hl in handlinks[juggler - 1][handnum]) {
            val hp = hl.handCurve
            if (hp != null) {
                result = Coordinate.max(result, hp.getMax(t1, t2))
            }
        }
        return result
    }

    fun getHandMin(juggler: Int, hand: Int): Coordinate? {
        var result: Coordinate? = null
        val t1 = pat.loopStartTime
        val t2 = pat.loopEndTime
        val handnum = if (hand == JmlEvent.LEFT_HAND) 0 else 1

        for (hl in handlinks[juggler - 1][handnum]) {
            val hp = hl.handCurve
            if (hp != null) {
                result = Coordinate.min(result, hp.getMin(t1, t2))
            }
        }
        return result
    }

    fun getJugglerMax(juggler: Int): Coordinate? {
        return jugglerCurve[juggler - 1].max
    }

    fun getJugglerMin(juggler: Int): Coordinate? {
        return jugglerCurve[juggler - 1].min
    }

    val handWindowMax: Coordinate
        get() = Coordinate(Juggler.HAND_OUT, 0.0, 1.0)

    val handWindowMin: Coordinate
        get() = Coordinate(-Juggler.HAND_IN, 0.0, -1.0)

    val jugglerWindowMax: Coordinate by lazy {
        var max = getJugglerMax(1)
        for (i in 2..pat.numberOfJugglers) {
            max = Coordinate.max(max, getJugglerMax(i))
        }

        max = Coordinate.add(
            max,
            Coordinate(
                Juggler.SHOULDER_HW,
                Juggler.SHOULDER_HW,  // Juggler.HEAD_HW,
                Juggler.SHOULDER_H + Juggler.NECK_H + Juggler.HEAD_H
            )
        )
        max!!
    }

    val jugglerWindowMin: Coordinate by lazy {
        var min = getJugglerMin(1)
        for (i in 2..pat.numberOfJugglers) {
            min = Coordinate.min(min, getJugglerMin(i))
        }

        min = Coordinate.add(
            min,
            Coordinate(-Juggler.SHOULDER_HW, -Juggler.SHOULDER_HW, 0.0)
        )
        min!!
    }

    val overallBoundingBox: Pair<Coordinate, Coordinate> by lazy {
        // Step 1: Work out a bounding box that contains all paths through space
        // for the pattern, including the props
        var patternMax: Coordinate? = null
        var patternMin: Coordinate? = null
        for (i in 1..pat.numberOfPaths) {
            patternMax = Coordinate.max(patternMax, getPathMax(i))
            patternMin = Coordinate.min(patternMin, getPathMin(i))

            if (Constants.DEBUG_LAYOUT) {
                println("Data from LaidoutPattern.overallBoundingBox:")
                println("Path max $i = " + getPathMax(i))
                println("Path min $i = " + getPathMin(i))
            }
        }

        var propMax: Coordinate? = null
        var propMin: Coordinate? = null
        for (i in 1..pat.numberOfProps) {
            propMax = Coordinate.max(propMax, pat.getProp(i).getMax())
            propMin = Coordinate.min(propMin, pat.getProp(i).getMin())
        }

        // Make sure props are entirely visible along all paths. In principle
        // not all props go on all paths so this could be done more carefully.
        if (patternMax != null && patternMin != null) {
            patternMax = Coordinate.add(patternMax, propMax)
            patternMin = Coordinate.add(patternMin, propMin)
        }

        // Step 2: Work out a bounding box that contains the hands at all times,
        // factoring in the physical extent of the hands.
        var handMax: Coordinate? = null
        var handMin: Coordinate? = null
        for (i in 1..pat.numberOfJugglers) {
            handMax = Coordinate.max(handMax, getHandMax(i, JmlEvent.LEFT_HAND))
            handMin = Coordinate.min(handMin, getHandMin(i, JmlEvent.LEFT_HAND))
            handMax = Coordinate.max(handMax, getHandMax(i, JmlEvent.RIGHT_HAND))
            handMin = Coordinate.min(handMin, getHandMin(i, JmlEvent.RIGHT_HAND))

            if (Constants.DEBUG_LAYOUT) {
                println("Data from LaidoutPattern.overallBoundingBox:")
                println("Hand max $i left = " + getHandMax(i, JmlEvent.LEFT_HAND))
                println("Hand min $i left = " + getHandMin(i, JmlEvent.LEFT_HAND))
                println("Hand max $i right = " + getHandMax(i, JmlEvent.RIGHT_HAND))
                println("Hand min $i right = " + getHandMin(i, JmlEvent.RIGHT_HAND))
            }
        }

        // The renderer's hand window is in local coordinates. We don't know
        // the juggler's rotation angle where `handMax` and `handMin` are
        // achieved. So we create a bounding box that contains the hand
        // regardless of rotation angle.
        val hwMax = handWindowMax.copy()
        val hwMin = handWindowMin.copy()
        hwMax.x = max(
            max(abs(hwMax.x), abs(hwMin.x)),
            max(abs(hwMax.y), abs(hwMin.y))
        )
        hwMin.x = -hwMax.x
        hwMax.y = hwMax.x
        hwMin.y = hwMin.x

        // make sure hands are entirely visible
        handMax = Coordinate.add(handMax, hwMax)
        handMin = Coordinate.add(handMin, hwMin)

        // Step 3: Combine the pattern, hand, and juggler bounding boxes into an
        // overall bounding box.
        val overallMax = Coordinate.max(patternMax, Coordinate.max(handMax, jugglerWindowMax))
        val overallMin = Coordinate.min(patternMin, Coordinate.min(handMin, jugglerWindowMin))

        if (Constants.DEBUG_LAYOUT) {
            println("Data from LaidoutPattern.overallBoundingBox:")
            println("Hand max = $handMax")
            println("Hand min = $handMin")
            println("Prop max = $propMax")
            println("Prop min = $propMin")
            println("Pattern max = $patternMax")
            println("Pattern min = $patternMin")
            println("Juggler max = $jugglerWindowMax")
            println("Juggler min = $jugglerWindowMin")
            println("Overall max = $overallMax")
            println("Overall min = $overallMin")
        }

        Pair(overallMin!!, overallMax!!)
    }
}

// Wrapper to enforce reference equality for map keys.

class IdentityKey<T>(val value: T) {
    override fun equals(other: Any?): Boolean = other is IdentityKey<*> && value === other.value
    override fun hashCode(): Int = value.hashCode()
}
