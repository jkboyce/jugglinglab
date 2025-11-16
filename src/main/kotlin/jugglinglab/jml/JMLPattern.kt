//
// JMLPattern.kt
//
// This is one of the core classes, representing a juggling pattern in generalized
// form. It is used in three steps:
//
// 1) Define a pattern, in one of four ways:
//    a) Manually, by calling methods in this class.
//    b) Parsing from pre-existing JML stream (file, user input, etc.).
//       (JML = Juggling Markup Language, an XML document type)
//    c) Output from a Notation instance's asJMLPattern() method.
//    d) The fromBasePattern() method in this class.
//
// 2) Call layoutPattern() to calculate flight paths for all the props and hands.
//
// 3) Call various methods to get information about the pattern, e.g., prop/hand
//    coordinates at points in time.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")

package jugglinglab.jml

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.core.Constants
import jugglinglab.curve.Curve
import jugglinglab.curve.LineCurve
import jugglinglab.curve.SplineCurve
import jugglinglab.jml.HandLink.Companion.index
import jugglinglab.jml.JMLNode.Companion.xmlescape
import jugglinglab.notation.Pattern
import jugglinglab.path.BouncePath
import jugglinglab.prop.Prop
import jugglinglab.renderer.Juggler
import jugglinglab.util.*
import jugglinglab.util.Coordinate.Companion.max
import jugglinglab.util.Coordinate.Companion.min
import jugglinglab.util.Coordinate.Companion.sub
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.Permutation.Companion.lcm
import org.xml.sax.SAXException
import java.io.*
import java.text.MessageFormat
import java.util.Locale
import java.util.StringTokenizer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max

class JMLPattern() {
    private var version: String = JMLDefs.CURRENT_JML_VERSION
    private var loadingversion: String = JMLDefs.CURRENT_JML_VERSION
    private var tags: ArrayList<String> = ArrayList()
    private var numjugglers: Int = 0
    private var numpaths: Int = 0
    private var props: ArrayList<JMLProp> = ArrayList()
    private var propassignment: IntArray? = null

    // for retaining the base pattern this pattern was created from
    var basePatternNotation: String? = null
        private set
    var basePatternConfig: String? = null
        private set
    private var basePatternHashcode: Int = 0
    private var basePatternHashcodeValid: Boolean = false

    var symmetries: ArrayList<JMLSymmetry> = ArrayList()
        private set
    var eventList: JMLEvent? = null
        private set
    var positionList: JMLPosition? = null
        private set

    // list of PathLink objects for each path
    private var pathlinks: ArrayList<ArrayList<PathLink>>? = null

    // list of HandLink objects for each juggler/hand combination
    private var handlinks: ArrayList<ArrayList<ArrayList<HandLink>>>? = null

    // for layout
    private lateinit var jugglercurve: Array<Curve?> // coordinates for each juggler
    private lateinit var jugglerangle: Array<Curve?> // angles for each juggler
    // whether pattern has a velocity-defining transition
    private lateinit var hasVDPathJMLTransition: BooleanArray // for a given path
    private lateinit var hasVDHandJMLTransition: Array<BooleanArray> // for a given juggler/hand
    private var laidout: Boolean = false
    var isValid: Boolean = true
        private set

    var info: String? = null
        set(t) {
            field = if (t != null && !t.trim().isBlank()) t.trim() else null
        }

    var title: String? = null
        set(title) {
            val t = title?.replace(";".toRegex(), "")  // filter out semicolons
            field = if (t != null && !t.isBlank()) t.trim() else null

            if (!hasBasePattern) return
            try {
                // set the title in base pattern
                val pl = ParameterList(basePatternConfig)
                if (pl.getParameter("pattern") == title) {
                    // if title is the default then remove the title parameter
                    pl.removeParameter("title")
                } else {
                    pl.addParameter("title", title ?: "")
                }

                basePatternConfig = pl.toString()
                basePatternHashcodeValid = false  // recalculate hash code
            } catch (jeu: JuggleExceptionUser) {
                // can't be a user error since base pattern has already successfully
                // compiled
                handleFatalException(JuggleExceptionInternal(jeu.message, this))
            }
        }

    //--------------------------------------------------------------------------
    // Alternate constructors
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class)
    constructor(root: JMLNode) : this() {
        readJML(root)
        isValid = true
    }

    // Used to specify jml version number, when pattern is part of a patternlist
    @Throws(JuggleExceptionUser::class)
    constructor(root: JMLNode, jmlvers: String) : this() {
        loadingversion = jmlvers
        readJML(root)
        isValid = true
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    constructor(read: Reader?) : this() {
        try {
            val parser = JMLParser()
            parser.parse(read)
            readJML(parser.tree!!)
            isValid = true
        } catch (se: SAXException) {
            throw JuggleExceptionUser(se.message)
        } catch (ioe: IOException) {
            throw JuggleExceptionInternal(ioe.message)
        }
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    constructor(pat: JMLPattern) : this(StringReader(pat.toString()))

    //--------------------------------------------------------------------------
    // Methods to define the pattern
    //--------------------------------------------------------------------------

    fun addTag(tag: String?) {
        if (tag != null && !tag.isEmpty() && !isTaggedWith(tag)) {
            tags.add(tag)
        }
    }

    @Suppress("unused")
    fun removeTag(tag: String?): Boolean {
        if (tag == null || !isTaggedWith(tag)) {
            return false
        }

        for (i in tags.indices) {
            if (tags[i].equals(tag, ignoreCase = true)) {
                tags.removeAt(i)
                return true
            }
        }
        return false  // shouldn't happen
    }

    fun addProp(pd: JMLProp?) {
        props.add(pd!!)
        setNeedsLayout()
    }

    fun removeProp(propnum: Int) {
        props.removeAt(propnum - 1)
        for (i in 1..this.numberOfPaths) {
            if (getPropAssignment(i) > propnum) {
                setPropAssignment(i, getPropAssignment(i) - 1)
            }
        }
        setNeedsLayout()
    }

    fun setPropAssignment(pathnum: Int, propnum: Int) {
        propassignment?.set(pathnum - 1, propnum)
        setNeedsLayout()
    }

    fun setPropAssignments(pa: IntArray) {
        propassignment = pa
        setNeedsLayout()
    }

    fun addSymmetry(sym: JMLSymmetry?) {
        symmetries.add(sym!!)
        setNeedsLayout()
    }

    fun addEvent(ev: JMLEvent) {
        setNeedsLayout()

        if (this.eventList == null || ev.t < eventList!!.t) {
            // set `ev` as new list head
            ev.previous = null
            ev.next = this.eventList
            if (this.eventList != null) {
                eventList!!.previous = ev
            }
            this.eventList = ev
            return
        }

        var current = this.eventList
        while (true) {
            val combineEvents =
                current!!.t == ev.t && current.hand == ev.hand && current.juggler == ev.juggler

            if (combineEvents) {
                // replace `current` with `ev` in the list...
                ev.previous = current.previous
                ev.next = current.next
                if (current.next != null) {
                    current.next!!.previous = ev
                }
                if (current.previous == null) {
                    this.eventList = ev // new head of the list
                } else {
                    current.previous!!.next = ev
                }

                // ...then move all the transitions from `current` to `ev`, except
                // those for a path number that already has a transition in `ev`.
                for (trCurrent in current.transitions) {
                    var addTransition = true

                    for (tr in ev.transitions) {
                        if (tr.path == trCurrent.path) {
                            addTransition = false
                            break
                        }
                    }

                    if (addTransition) {
                        ev.addTransition(trCurrent)
                    }
                }
                return
            }

            if (ev.t < current.t) {
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

    fun removeEvent(ev: JMLEvent) {
        setNeedsLayout()
        if (eventList == ev) {
            eventList = ev.next
            if (eventList != null) {
                eventList!!.previous = null
            }
            return
        }

        val next = ev.next
        val prev = ev.previous
        if (next != null) {
            next.previous = prev
        }
        if (prev != null) {
            prev.next = next
        }
    }

    fun getEventImageInLoop(ev: JMLEvent): JMLEvent? {
        var current = eventList
        while (current != null) {
            if ((current.t in loopStartTime..<loopEndTime) &&
                current.juggler == ev.juggler &&
                current.hand == ev.hand &&
                current.hasSameMasterAs(ev)
            ) {
                return current
            }
            current = current.next
        }
        return null
    }

    // Useful for debugging.

    @Suppress("unused")
    private fun printEventList() {
        var current = this.eventList
        val pw = PrintWriter(System.out)
        while (current != null) {
            if (current.isMaster) {
                println("  Master event:")
            } else {
                println("  Slave event; master at t=" + current.master.t)
            }

            try {
                current.writeJML(pw)
            } catch (ioe: IOException) {
            }
            pw.flush()
            current = current.next
        }
    }

    val pathLinks: ArrayList<ArrayList<PathLink>>
        get() = pathlinks!!

    fun addPosition(pos: JMLPosition) {
        if (pos.t < this.loopStartTime || pos.t > this.loopEndTime) {
            return  // throw new JuggleExceptionUser("<position> time out of range");
        }
        setNeedsLayout()

        if (this.positionList == null || positionList!!.t > pos.t) {
            pos.previous = null
            pos.next = this.positionList
            if (this.positionList != null) {
                positionList!!.previous = pos
            }
            this.positionList = pos
            return
        }

        var current = this.positionList

        while (current!!.next != null) {
            current = current.next

            if (current!!.t > pos.t) {
                pos.next = current
                pos.previous = current.previous
                current.previous!!.next = pos
                current.previous = pos
                return
            }
        }

        current.next = pos
        pos.next = null
        pos.previous = current
    }

    fun removePosition(pos: JMLPosition) {
        setNeedsLayout()
        if (this.positionList == pos) {
            this.positionList = pos.next
            if (this.positionList != null) {
                positionList!!.previous = null
            }
            return
        }

        val next = pos.next
        val prev = pos.previous
        if (next != null) {
            next.previous = prev
        }
        if (prev != null) {
            prev.next = next
        }
    }

    val hashCode: Int
        get() {
            val sw = StringWriter()
            try {
                // Omit <info> tag metadata for the purposes of evaluating hash code.
                // Two patterns that differ only by metadata are treated as identical.
                writeJML(sw, writeTitle = true, writeInfo = false)
            } catch (_: IOException) {
            }

            return sw.toString().hashCode()
        }

    //--------------------------------------------------------------------------
    // Methods related to the base pattern (if set)
    //--------------------------------------------------------------------------

    val hasBasePattern: Boolean
        get() = (basePatternNotation != null && basePatternConfig != null)

    val isBasePatternEdited: Boolean
        get() {
            if (!hasBasePattern) return false
            if (!basePatternHashcodeValid) {
                try {
                    basePatternHashcode =
                        fromBasePattern(basePatternNotation!!, basePatternConfig!!)
                            .layoutPattern().hashCode
                    basePatternHashcodeValid = true
                } catch (_: JuggleException) {
                    basePatternHashcode = 0
                    basePatternHashcodeValid = false
                    return false
                }
            }

            return (this.hashCode != basePatternHashcode)
        }

    //--------------------------------------------------------------------------
    // Some pattern transformations
    //--------------------------------------------------------------------------

    // Multiply all times in the pattern by a common factor `scale`.

    fun scaleTime(scale: Double) {
        var ev = this.eventList
        while (ev != null) {
            if (ev.isMaster) {
                ev.t = ev.t * scale
            }
            ev = ev.next
        }
        var pos = positionList
        while (pos != null) {
            pos.t = pos.t * scale
            pos = pos.next
        }

        for (sym in symmetries) {
            val delay = sym.delay
            if (delay > 0) {
                sym.delay = delay * scale
            }
        }

        setNeedsLayout()
    }

    // Rescale the pattern in time to ensure that all throws are allotted
    // more time than their minimum required.
    //
    // `multiplier` should typically be a little over 1.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun scaleTimeToFitThrows(multiplier: Double): Double {
        layoutPattern()  // to ensure we have PathLinks
        var scaleFactor = 1.0

        for (path in 1..numberOfPaths) {
            for (pl in pathLinks[path - 1]) {
                val p = pl.path
                if (p != null) {
                    val d = p.duration
                    val dmin = p.minDuration

                    if (d < dmin && d > 0) {
                        scaleFactor = max(scaleFactor, dmin / d)
                    }
                }
            }
        }

        if (scaleFactor > 1) {
            scaleFactor *= multiplier // so things aren't just barely feasible
            scaleTime(scaleFactor)
        }
        return scaleFactor
    }

    // Swap the assignment of hands, leaving events in the same locations.

    fun swapHands() {
        var ev = this.eventList
        while (ev != null) {
            var hand = ev.hand

            // flip hand assignment, invert x coordinate
            hand = if (hand == HandLink.LEFT_HAND) {
                HandLink.RIGHT_HAND
            } else {
                HandLink.LEFT_HAND
            }

            ev.setHand(ev.juggler, hand)
            ev = ev.next
        }
        setNeedsLayout()
    }

    // Flip the x-axis in the local coordinates of each juggler.

    fun invertXAxis() {
        var ev = this.eventList
        while (ev != null) {
            var hand = ev.hand
            val c = ev.localCoordinate

            // flip hand assignment, invert x coordinate
            hand = if (hand == HandLink.LEFT_HAND) {
                HandLink.RIGHT_HAND
            } else {
                HandLink.LEFT_HAND
            }

            c.x = -c.x

            ev.setHand(ev.juggler, hand)
            ev.localCoordinate = c
            ev = ev.next
        }
        setNeedsLayout()
    }

    // Flip the time axis to create (as nearly as possible) what the pattern
    // looks like played in reverse.

    @Throws(JuggleExceptionInternal::class)
    fun invertTime() {
        try {
            layoutPattern() // to ensure we have PathLinks

            // For each JMLEvent:
            //     - set t = looptime - t
            //     - reverse the doubly-linked event list
            val looptime = loopEndTime
            var ev = eventList
            while (ev != null) {
                ev.t = looptime - ev.t

                val prev = ev.previous
                val next = ev.next
                ev.previous = next
                ev.next = prev

                if (next == null) {
                    eventList = ev  // new list head
                }

                ev = next
            }

            // For each JMLPosition:
            //     - set t = looptime - t
            //     - sort the position list in time
            var pos = positionList
            positionList = null
            while (pos != null) {
                // no notion analagous to master events, so have to keep
                // position time within [0, looptime).
                if (pos.t != 0.0) {
                    pos.t = looptime - pos.t
                }
                val next = pos.next
                addPosition(pos)
                pos = next
            }

            // for each symmetry (besides type SWITCH):
            //     - invert pperm
            for (sym in symmetries) {
                if (sym.getType() == JMLSymmetry.TYPE_SWITCH) continue
                val newpathperm = sym.pathPerm!!.inverse
                sym.setPathPerm(sym.numberOfPaths, newpathperm.toString())
            }

            // for each PathLink:
            //     - find corresponding throw-type JMLTransition in startevent
            //     - find corresponding catch-type JMLTransition in endevent
            //     - swap {type, throw type, throw mod} for the two transitions
            for (path in 1..numberOfPaths) {
                for (pl in pathLinks[path - 1]) {
                    if (pl.isInHand) continue
                    val start = pl.startEvent
                    val end = pl.endEvent

                    var startTr: JMLTransition? = null
                    for (tr in start.transitions) {
                        if (tr.path == path) {
                            startTr = tr
                            break
                        }
                    }

                    var endTr: JMLTransition? = null
                    for (tr in end.transitions) {
                        if (tr.path == path) {
                            endTr = tr
                            break
                        }
                    }

                    if (startTr == null || endTr == null) {
                        throw JuggleExceptionInternal("invertTime() error 1", this)
                    }
                    if (startTr.outgoingPathLink != pl) {
                        throw JuggleExceptionInternal("invertTime() error 2", this)
                    }
                    if (endTr.incomingPathLink != pl) {
                        throw JuggleExceptionInternal("invertTime() error 3", this)
                    }

                    val startTrType = startTr.transType
                    val startTrThrowType = startTr.throwType
                    val startTrThrowMod = startTr.mod

                    startTr.transType = endTr.transType
                    startTr.throwType = endTr.throwType
                    startTr.mod = endTr.mod
                    endTr.transType = startTrType
                    endTr.throwType = startTrThrowType
                    endTr.mod = startTrThrowMod

                    // don't need to do surgery on PathLinks or Paths since those
                    // will be recalculated during pattern layout
                }
            }
        } catch (jeu: JuggleExceptionUser) {
            // No user errors here because the pattern has already been animated
            throw JuggleExceptionInternal("invertTime() error 4: " + jeu.message, this)
        } finally {
            setNeedsLayout()
        }
    }

    // Streamline the pattern to remove excess empty and holding events.
    //
    // Scan forward in time through the pattern and remove any event for which
    // all of the following are true:
    //
    // (a) event is empty or contains only <holding> transitions
    // (b) event has a different master event than the previous (surviving)
    //     event for that hand
    // (c) event is within `twindow` seconds of the previous (surviving) event
    //     for that hand
    // (d) event is not immediately adjacent to a throw or catch event for that
    //     hand that involves a pass to/from a different juggler

    @Suppress("unused")
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun streamlinePatternWithWindow(twindow: Double) {
        layoutPattern()  // to ensure we have PathLinks

        var nEvents = 0 // for reporting stats
        var nHolds = 0
        var nRemoved = 0

        var ev = eventList

        while (ev != null) {
            val prev = ev.previousForHand
            val next = ev.nextForHand

            var holdingOnly = true
            for (tr in ev.transitions) {
                if (tr.transType != JMLTransition.TRANS_HOLDING) {
                    holdingOnly = false
                    break
                }
            }
            val differentMasters = (prev == null || !ev.hasSameMasterAs(prev))
            val insideWindow = (prev != null && (ev.t - prev.t) < twindow)
            val notPassAdjacent =
                (prev != null && next != null && !prev.hasPassingTransition && !next.hasPassingTransition)

            val remove = holdingOnly && differentMasters && insideWindow && notPassAdjacent
            if (remove) {
                removeEvent(ev)
                ++nRemoved
            }

            ++nEvents
            if (holdingOnly) {
                ++nHolds
            }

            ev = ev.next
        }

        if (Constants.DEBUG_LAYOUT) {
            println("Streamlined with time window $twindow secs:")
            println(
                "    Removed $nRemoved of $nHolds holding events ($nEvents events total)"
            )
        }
    }

    //--------------------------------------------------------------------------
    // Lay out the spatial paths in the pattern
    //
    // Note that this can change the pattern's toString() representation,
    // and therefore its hash code.
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    fun layoutPattern(): JMLPattern {
        if (laidout) {
            return this
        }

        if (!isValid) {
            throw JuggleExceptionInternal("Cannot do layout of invalid pattern", this)
        }

        try {
            if (numberOfProps == 0 && numberOfPaths > 0) {
                addProp(JMLProp("ball", null))
            }
            for (i in 0..<numberOfProps) {
                props[i].layoutProp()
            }

            buildEventList()
            findMasterEvents()
            findPositions()
            gotoGlobalCoordinates()
            buildLinkLists()
            layoutHandPaths()

            if (Constants.DEBUG_LAYOUT) {
                for (i in 0..<numberOfPaths) {
                    println(pathlinks!![i].size.toString() + " pathlinks for path " + (i + 1) + ":")
                    for (jtemp in pathlinks!![i].indices) {
                        println("   " + pathlinks!![i][jtemp])
                    }
                }
                for (i in 0..<numberOfJugglers) {
                    for (j in 0..1) {
                        println(
                            (handlinks!![i][j].size
                                .toString() + " handlinks for juggler "
                                + (i + 1)
                                + ", hand "
                                + (j + 1)
                                + ":")
                        )
                        for (k in handlinks!![i][j].indices) {
                            println("   " + handlinks!![i][j][k])
                        }
                    }
                }
            }
            laidout = true
        } catch (jeu: JuggleExceptionUser) {
            isValid = false
            throw jeu
        } catch (jei: JuggleExceptionInternal) {
            isValid = false
            jei.attachPattern(this)
            throw jei
        }
        return this
    }

    fun setNeedsLayout() {
        laidout = false
    }

    //--------------------------------------------------------------------------
    // Step 1: construct the list of events
    // Extend events in list using known symmetries
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    fun buildEventList() {
        // figure out how many events there are
        var numevents = 0
        var current = this.eventList
        while (current != null) {
            if (current.juggler !in 1..numjugglers) {
                throw JuggleExceptionUser(errorstrings.getString("Error_juggler_outofrange"))
            }
            if (current.isMaster) {
                ++numevents
            } else {
                removeEvent(current)
            }
            current = current.next
        }
        // construct event images for extending event list
        val ei = arrayOfNulls<EventImages>(numevents)
        current = this.eventList
        for (i in 0..<numevents) {
            ei[i] = EventImages(this, current!!)
            current = current.next
        }

        // arrays used for creating the event list
        val needHandEvent = Array(numjugglers) { BooleanArray(2) }
        val needVDHandEvent = Array(numjugglers) { BooleanArray(2) }
        val needPathEvent = BooleanArray(numpaths)
        val needSpecialPathEvent = BooleanArray(numpaths)
        hasVDHandJMLTransition = Array(numjugglers) { BooleanArray(2) }
        hasVDPathJMLTransition = BooleanArray(numpaths)

        // make sure each hand and path are hit at least once
        for (i in 0..<numjugglers) {
            var hasJMLTransitionForLeft = false
            var hasJMLTransitionForRight = false
            hasVDHandJMLTransition[i][1] = false
            hasVDHandJMLTransition[i][0] = false

            for (j in 0..<numevents) {
                if (!hasJMLTransitionForLeft) {
                    hasJMLTransitionForLeft =
                        ei[j]!!.hasJMLTransitionForHand(i + 1, HandLink.LEFT_HAND)
                }
                if (!hasJMLTransitionForRight) {
                    hasJMLTransitionForRight =
                        ei[j]!!.hasJMLTransitionForHand(i + 1, HandLink.RIGHT_HAND)
                }
                if (!hasVDHandJMLTransition[i][0]) {
                    hasVDHandJMLTransition[i][0] =
                        ei[j]!!.hasVDJMLTransitionForHand(i + 1, HandLink.LEFT_HAND)
                }
                if (!hasVDHandJMLTransition[i][1]) {
                    hasVDHandJMLTransition[i][1] =
                        ei[j]!!.hasVDJMLTransitionForHand(i + 1, HandLink.RIGHT_HAND)
                }
            }
            if (!hasJMLTransitionForLeft) {
                val template: String = errorstrings.getString("Error_no_left_events")
                val arguments = arrayOf<Any?>(i + 1)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
            if (!hasJMLTransitionForRight) {
                val template: String = errorstrings.getString("Error_no_right_events")
                val arguments = arrayOf<Any?>(i + 1)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
            needVDHandEvent[i][0] = hasVDHandJMLTransition[i][0] // set up for later
            needVDHandEvent[i][1] = hasVDHandJMLTransition[i][1]
            needHandEvent[i][1] = true
            needHandEvent[i][0] = true
        }
        for (i in 0..<numpaths) {
            var hasPathJMLTransition = false
            hasVDPathJMLTransition[i] = false

            for (j in 0..<numevents) {
                if (!hasPathJMLTransition) {
                    hasPathJMLTransition = ei[j]!!.hasJMLTransitionForPath(i + 1)
                }
                if (!hasVDPathJMLTransition[i]) {
                    hasVDPathJMLTransition[i] = ei[j]!!.hasVDJMLTransitionForPath(i + 1)
                }
            }
            if (!hasPathJMLTransition) {
                val template: String = errorstrings.getString("Error_no_path_events")
                val arguments = arrayOf<Any?>(i + 1)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
            needPathEvent[i] = true // set up for later
            needSpecialPathEvent[i] = false
        }

        // queue used to store events while building event list
        val eventqueue: Array<JMLEvent?> = arrayOfNulls(numevents)
        for (i in 0..<numevents) {
            eventqueue[i] = ei[i]!!.previous // seed the queue
        }

        // start by extending each master event backward in time
        var contin: Boolean
        do {
            // find latest event in queue
            var maxevent = eventqueue[0]
            var maxtime = maxevent!!.t
            var maxnum = 0
            for (i in 1..<numevents) {
                if (eventqueue[i]!!.t > maxtime) {
                    maxevent = eventqueue[i]
                    maxtime = maxevent!!.t
                    maxnum = i
                }
            }

            addEvent(maxevent!!)
            eventqueue[maxnum] = ei[maxnum]!!.previous // restock queue

            // now update the needs arrays, so we know when to stop
            if (maxtime < this.loopStartTime) {
                val jug = maxevent.juggler - 1
                val han = index(maxevent.hand)

                if (!hasVDHandJMLTransition[jug][han]) {
                    needHandEvent[jug][han] = false
                }

                for (tr in maxevent.transitions) {
                    val path = tr.path - 1

                    when (tr.transType) {
                        JMLTransition.TRANS_THROW -> {
                            needPathEvent[path] = false
                            needHandEvent[jug][han] = false
                            needVDHandEvent[jug][han] = false
                            needSpecialPathEvent[path] = false
                        }

                        JMLTransition.TRANS_CATCH, JMLTransition.TRANS_GRABCATCH -> {}
                        JMLTransition.TRANS_SOFTCATCH -> {
                            if (needVDHandEvent[jug][han]) {
                                // need corresponding throw to get velocity
                                needSpecialPathEvent[path] = true
                            }
                            needHandEvent[jug][han] = false
                            needVDHandEvent[jug][han] = false
                        }

                        JMLTransition.TRANS_HOLDING -> if (!hasVDPathJMLTransition[path]) {
                            // if no throws for this path, then done
                            needPathEvent[path] = false
                        }

                        else -> throw JuggleExceptionInternal(
                            "Unrecognized transition type in buildEventList()",
                            this
                        )
                    }
                }
            }
            // do we need to continue adding earlier events?
            contin = false
            for (i in 0..<numjugglers) {
                contin = contin or needHandEvent[i][0]
                contin = contin or needHandEvent[i][1]
                contin = contin or needVDHandEvent[i][0]
                contin = contin or needVDHandEvent[i][1]
            }
            for (i in 0..<numpaths) {
                contin = contin or needPathEvent[i]
                contin = contin or needSpecialPathEvent[i]
            }
        } while (contin)

        // reset things to go forward in time
        for (i in 0..<numjugglers) {
            needVDHandEvent[i][0] = hasVDHandJMLTransition[i][0]
            needVDHandEvent[i][1] = hasVDHandJMLTransition[i][1]
            needHandEvent[i][1] = true
            needHandEvent[i][0] = true
        }
        for (i in 0..<numpaths) {
            needPathEvent[i] = true
            needSpecialPathEvent[i] = false
        }
        for (i in 0..<numevents) {
            ei[i]!!.resetPosition()
            eventqueue[i] = ei[i]!!.next
        }

        do {
            // find earliest event in queue
            var minevent = eventqueue[0]
            var mintime = minevent!!.t
            var minnum = 0
            for (i in 1..<numevents) {
                if (eventqueue[i]!!.t < mintime) {
                    minevent = eventqueue[i]
                    mintime = minevent!!.t
                    minnum = i
                }
            }

            addEvent(minevent!!)
            eventqueue[minnum] = ei[minnum]!!.next // restock queue

            // now update the needs arrays, so we know when to stop
            if (mintime > loopEndTime) {
                val jug = minevent.juggler - 1
                val han = index(minevent.hand)

                // if this hand has no throws/catches, then need to build out event list
                // past a certain time, due to how the hand layout is done in this case
                // (see layoutHandPaths() below)
                if (!hasVDHandJMLTransition[jug][han]
                    && mintime > (2 * loopEndTime - loopStartTime)
                ) {
                    needHandEvent[jug][han] = false
                }

                for (tr in minevent.transitions) {
                    val path = tr.path - 1

                    when (tr.transType) {
                        JMLTransition.TRANS_THROW -> {
                            needPathEvent[path] = false
                            if (needVDHandEvent[jug][han]) {
                                // need corresponding catch to get velocity
                                needSpecialPathEvent[path] = true
                            }
                            needHandEvent[jug][han] = false
                            needVDHandEvent[jug][han] = false
                        }

                        JMLTransition.TRANS_CATCH, JMLTransition.TRANS_GRABCATCH -> {
                            needPathEvent[path] = false
                            needSpecialPathEvent[path] = false
                        }

                        JMLTransition.TRANS_SOFTCATCH -> {
                            needPathEvent[path] = false
                            needHandEvent[jug][han] = false
                            needVDHandEvent[jug][han] = false
                            needSpecialPathEvent[path] = false
                        }

                        JMLTransition.TRANS_HOLDING -> if (!hasVDPathJMLTransition[path]) {
                            // no throws for this path, done
                            needPathEvent[path] = false
                        }

                        else -> throw JuggleExceptionInternal(
                            "Unrecognized transition type in buildEventList()",
                            this
                        )
                    }
                }
            }
            // do we need to continue adding later events?
            contin = false
            for (i in 0..<numjugglers) {
                contin = contin or needHandEvent[i][0]
                contin = contin or needHandEvent[i][1]
                contin = contin or needVDHandEvent[i][0]
                contin = contin or needVDHandEvent[i][1]
            }
            for (i in 0..<numpaths) {
                contin = contin or needPathEvent[i]
                contin = contin or needSpecialPathEvent[i]
            }
        } while (contin)
    }

    //--------------------------------------------------------------------------
    // Step 2: figure out which events should be considered master events
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    fun findMasterEvents() {
        var rebuildList = false
        var ev = this.eventList

        while (ev != null) {
            if (ev.isMaster) {
                var newmaster: JMLEvent? = ev
                var tmaster = loopEndTime
                if (ev.t in loopStartTime..<tmaster) {
                    tmaster = ev.t
                }

                var ev2 = eventList
                while (ev2 != null) {
                    if (ev2.master == ev) {
                        if (ev2.t in loopStartTime..<tmaster) {
                            newmaster = ev2
                            tmaster = ev2.t
                        }
                    }
                    ev2 = ev2.next
                }

                if (newmaster != ev) {
                    rebuildList = true
                    ev2 = eventList
                    while (ev2 != null) {
                        if (ev2.master == ev) {
                            ev2.masterEvent = newmaster
                        }
                        ev2 = ev2.next
                    }
                    newmaster!!.masterEvent = null
                    ev.masterEvent = newmaster
                }
            }
            ev = ev.next
        }

        if (rebuildList) {
            buildEventList()
        }
    }

    //--------------------------------------------------------------------------
    // Step 3: find positions/angles for all jugglers at all points in time,
    // using <position> tags. This is done by finding spline functions passing
    // through the specified locations and angles.
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    fun findPositions() {
        jugglercurve = arrayOfNulls(numberOfJugglers)
        jugglerangle = arrayOfNulls(numberOfJugglers)

        for (i in 1..numberOfJugglers) {
            var num = 0
            var current = positionList
            while (current != null) {
                if (current.juggler == i) {
                    ++num
                }
                current = current.next
            }

            // curves for body position and angle, respectively
            val jcurve = SplineCurve()
            val jangle = when (Constants.ANGLE_LAYOUT_METHOD) {
                Curve.CURVE_SPLINE -> SplineCurve()
                else -> LineCurve()
            }

            if (num == 0) {
                // no positions for this juggler
                val times = doubleArrayOf(loopStartTime, loopEndTime)
                val positions = Array(2) { Coordinate() }
                val angles = Array(2) { Coordinate() }

                // apply some defaults
                if (numberOfJugglers == 1) {
                    positions[0].setCoordinate(0.0, 0.0, 100.0)
                    angles[0].setCoordinate(0.0, 0.0, 0.0)
                } else {
                    var r = 70.0
                    val theta = 360 / numberOfJugglers.toDouble()
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

                current = positionList
                var j = 0
                while (current != null) {
                    if (current.juggler == i) {
                        times[j] = current.t
                        positions[j] = current.coordinate
                        angles[j] = Coordinate(current.angle, 0.0, 0.0)
                        ++j
                    }
                    current = current.next
                }
                times[num] = times[0] + loopEndTime - loopStartTime
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
            jugglercurve[i - 1] = jcurve
            jugglerangle[i - 1] = jangle
        }
    }

    //--------------------------------------------------------------------------
    // Step 4: transform event coordinates from local to global reference frame
    //--------------------------------------------------------------------------

    fun gotoGlobalCoordinates() {
        var ev = eventList
        while (ev != null) {
            val lc = ev.localCoordinate
            val gc = convertLocalToGlobal(lc, ev.juggler, ev.t)
            ev.globalCoordinate = gc
            ev = ev.next
        }
    }

    //--------------------------------------------------------------------------
    // Step 5: construct the links connecting events; build PathLink and
    // HandLink lists
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun buildLinkLists() {
        pathlinks = ArrayList(numberOfPaths)

        for (i in 0..<numberOfPaths) {
            // build the PathLink list for the ith path
            pathlinks!!.add(ArrayList())
            var ev = this.eventList
            var lastev: JMLEvent? = null
            var lasttr: JMLTransition? = null

            done1@ while (true) {
                // find the next transition for this path
                var tr: JMLTransition?
                while (true) {
                    tr = ev!!.getPathTransition(i + 1, JMLTransition.TRANS_ANY)
                    if (tr != null) {
                        break
                    }
                    ev = ev.next
                    if (ev == null) {
                        break@done1
                    }
                }

                if (lastev != null) {
                    val pl = PathLink(i + 1, lastev, ev)

                    when (tr.transType) {
                        JMLTransition.TRANS_THROW, JMLTransition.TRANS_HOLDING -> {
                            if (lasttr!!.transType == JMLTransition.TRANS_THROW) {
                                val template: String =
                                    errorstrings.getString("Error_successive_throws")
                                val arguments = arrayOf<Any?>(i + 1)
                                throw JuggleExceptionUser(
                                    MessageFormat.format(
                                        template,
                                        *arguments
                                    )
                                )
                            }
                            if (lastev.juggler != ev.juggler) {
                                val template: String =
                                    errorstrings.getString("Error_juggler_changed")
                                val arguments = arrayOf<Any?>(i + 1)
                                throw JuggleExceptionUser(
                                    MessageFormat.format(
                                        template,
                                        *arguments
                                    )
                                )
                            }
                            if (lastev.hand != ev.hand) {
                                val template: String = errorstrings.getString("Error_hand_changed")
                                val arguments = arrayOf<Any?>(i + 1)
                                throw JuggleExceptionUser(
                                    MessageFormat.format(
                                        template,
                                        *arguments
                                    )
                                )
                            }
                            pl.setInHand(ev.juggler, ev.hand)
                        }

                        JMLTransition.TRANS_CATCH, JMLTransition.TRANS_SOFTCATCH, JMLTransition.TRANS_GRABCATCH -> {
                            if (lasttr!!.transType != JMLTransition.TRANS_THROW) {
                                val template: String =
                                    errorstrings.getString("Error_successive_catches")
                                val arguments = arrayOf<Any?>(i + 1)
                                throw JuggleExceptionUser(
                                    MessageFormat.format(
                                        template,
                                        *arguments
                                    )
                                )
                            }
                            pl.setThrow(lasttr.throwType!!, lasttr.mod)
                        }

                        else -> throw JuggleExceptionInternal(
                            "unrecognized transition type in buildLinkLists()",
                            this
                        )
                    }

                    pathlinks!![i].add(pl)
                    lasttr.outgoingPathLink = pl
                    tr.incomingPathLink = pl
                }

                lastev = ev
                lasttr = tr
                ev = ev.next
                if (ev == null) {
                    break
                }
            }

            if (pathlinks!![i].isEmpty()) {
                throw JuggleExceptionInternal("No event found for path " + (i + 1), this)
            }
        }

        // now build the HandLink lists
        handlinks = ArrayList()

        for (i in 0..<numberOfJugglers) {
            // build the HandLink list for the ith juggler

            handlinks!!.add(ArrayList())

            for (j in 0..1) {
                val handnum = if (j == 0) HandLink.LEFT_HAND else HandLink.RIGHT_HAND

                handlinks!![i].add(ArrayList())

                var ev = this.eventList
                var lastev: JMLEvent? = null
                var vr: VelocityRef?
                var lastvr: VelocityRef? = null

                done2@ while (true) {
                    // find the next event touching hand
                    while (ev!!.juggler != (i + 1) || ev.hand != handnum) {
                        ev = ev.next
                        if (ev == null) {
                            break@done2
                        }
                    }

                    // find velocity of hand path ending
                    vr = null
                    if (ev.juggler == (i + 1) && ev.hand == handnum) {
                        for (tr in ev.transitions) {
                            if (tr.transType == JMLTransition.TRANS_THROW) {
                                val pl = tr.outgoingPathLink
                                if (pl != null) {
                                    vr = VelocityRef(pl.path!!, VelocityRef.VR_THROW)
                                }
                            } else if (tr.transType == JMLTransition.TRANS_SOFTCATCH) {
                                val pl = tr.incomingPathLink
                                if (pl != null) {
                                    vr = VelocityRef(pl.path!!, VelocityRef.VR_SOFTCATCH)
                                }
                            } else if (tr.transType == JMLTransition.TRANS_CATCH) {
                                val pl = tr.incomingPathLink
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
                        val hl = HandLink(i, handnum, lastev, ev)
                        hl.startVelocityRef = lastvr // may be null, which is ok
                        hl.endVelocityRef = vr
                        handlinks!![i][j].add(hl)
                    }
                    lastev = ev
                    lastvr = vr
                    ev = ev.next
                    if (ev == null) {
                        break
                    }
                }
            }
        }
    }

    //--------------------------------------------------------------------------
    // Step 6: do a physical layout of the handlink paths
    // (Props were physically laid out in PathLink.setThrow() in Step 5 above)
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    private fun layoutHandPaths() {
        // go through HandLink lists, creating Path objects and calculating paths

        for (j in 0..<this.numberOfJugglers) {
            for (h in 0..1) {
                // There are two cases: a hand has throw or softcatch events (which define
                // hand velocities at points in time), or it does not (no velocities known).
                // To determine the spline paths, we need to solve for hand velocity at each
                // of its events, but this is done differently in the two cases.

                if (hasVDHandJMLTransition[j][h]) {
                    var startlink: HandLink? = null
                    var num = 0

                    for (k in handlinks!![j][h].indices) {
                        val hl = handlinks!![j][h][k]

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
                                val hl2 = handlinks!![j][h][k - num + 1 + l]
                                times[l] = hl2.startEvent.t
                                pos[l] = hl2.startEvent.globalCoordinate!!
                                val vr2 = hl2.startVelocityRef
                                if (l > 0 && vr2 != null && vr2.source == VelocityRef.VR_CATCH) {
                                    velocities[l] = vr2.velocity
                                }
                                hl2.handCurve = hp
                            }
                            times[num] = hl.endEvent.t
                            pos[num] = hl.endEvent.globalCoordinate!!
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
                    while (k < handlinks!![j][h].size) {
                        hl = handlinks!![j][h][k]
                        if (hl.endEvent.t > this.loopStartTime) {
                            break
                        }
                        ++k
                    }

                    for (chain in 0..1) {
                        val startlink = hl
                        val startevent = startlink!!.startEvent
                        var num = 1 // number of links in chain
                        while (!hl!!.endEvent.isDelayOf(startevent)) {
                            hl = handlinks!![j][h][++k]
                            ++num
                        }
                        val times = DoubleArray(num + 1)
                        val pos = Array(num + 1) { Coordinate() }
                        val hp: Curve = SplineCurve()

                        for (l in 0..<num) {
                            val hl2 = handlinks!![j][h][k - num + 1 + l]
                            pos[l] = hl2.startEvent.globalCoordinate!!
                            times[l] = hl2.startEvent.t
                            hl2.handCurve = hp
                        }
                        pos[num] = hl.endEvent.globalCoordinate!!
                        times[num] = hl.endEvent.t
                        // all velocities are null (unknown) -> signal to calculate
                        hp.setCurve(times, pos, arrayOfNulls(num + 1))
                        hp.calcCurve()

                        if (chain == 0) {
                            hl = handlinks!![j][h][++k]
                        }
                    }
                }
            }
        }
    }

    //--------------------------------------------------------------------------
    // Methods used by the animator to animate the pattern
    //--------------------------------------------------------------------------

    fun isTaggedWith(tag: String?): Boolean {
        if (tag == null) return false
        for (t in tags) {
            if (t.equals(tag, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    var numberOfJugglers: Int
        get() = numjugglers
        set(n) {
            numjugglers = n
            setNeedsLayout()
        }

    var numberOfPaths: Int
        get() = numpaths
        set(n) {
            numpaths = n
            setNeedsLayout()
        }

    val numberOfProps: Int
        get() = props.size

    fun getProp(propnum: Int): Prop? {
        return getPropDef(propnum).prop
    }

    fun getPropDef(propnum: Int): JMLProp {
        return props[propnum - 1]
    }

    fun getPropAssignment(path: Int): Int {
        return propassignment!![path - 1]
    }

    val loopStartTime: Double
        get() = 0.0

    val loopEndTime: Double
        get() {
            for (sym in symmetries) {
                if (sym.getType() == JMLSymmetry.TYPE_DELAY) {
                    return sym.delay
                }
            }
            return -1.0
        }

    // Return path coordinate in global frame.

    @Throws(JuggleExceptionInternal::class)
    fun getPathCoordinate(path: Int, time: Double, newPosition: Coordinate) {
        for (pl in pathlinks!![path - 1]) {
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
        throw JuggleExceptionInternal("time t=$time is out of path range", this)
    }

    // Check if a given hand is holding the path at a given time.

    fun isHandHoldingPath(juggler: Int, hand: Int, time: Double, path: Int): Boolean {
        for (pl in pathlinks!![path - 1]) {
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
        for (path in 1..numberOfPaths) {
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
        val p = jugglercurve[juggler - 1]
        while (time < p!!.startTime) {
            time += loopEndTime - loopStartTime
        }
        while (time > p.endTime) {
            time -= loopEndTime - loopStartTime
        }
        p.getCoordinate(time, newPosition)
    }

    // Return angle (in degrees) between local x axis and global x axis
    // (rotation around vertical z axis).

    fun getJugglerAngle(juggler: Int, time: Double): Double {
        var time = time
        val p = jugglerangle[juggler - 1]

        while (time < p!!.startTime) {
            time += loopEndTime - loopStartTime
        }
        while (time > p.endTime) {
            time -= loopEndTime - loopStartTime
        }

        val coord = Coordinate()
        p.getCoordinate(time, coord)

        return coord.x
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
        val c2 = sub(gc, origin)

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
        val handindex = if (hand == HandLink.LEFT_HAND) 0 else 1

        while (time < loopStartTime) {
            time += loopEndTime - loopStartTime
        }
        while (time >= loopEndTime) {
            time -= loopEndTime - loopStartTime
        }

        for (hl in handlinks!![juggler - 1][handindex]) {
            if (time >= hl.startEvent.t && time < hl.endEvent.t) {
                val hp = hl.handCurve ?: throw JuggleExceptionInternal(
                    "getHandCoordinate() null pointer at t=$time",
                    this
                )
                hp.getCoordinate(time, newPosition)
                return
            }
        }
        throw JuggleExceptionInternal(
            "time t=$time (j=$juggler,h=$handindex) is out of handpath range",
            this
        )
    }

    // Get volume of any catch made between time1 and time2.
    //
    // If no catch then return 0.

    fun getPathCatchVolume(path: Int, time1: Double, time2: Double): Double {
        var wasinair = false
        var gotcatch = false

        var i = 0
        while (i < pathlinks!![path - 1].size) {
            val pl1 = pathlinks!![path - 1][i]
            if (time1 >= pl1.startEvent.t && time1 <= pl1.endEvent.t) {
                break
            }
            ++i
        }
        if (i == pathlinks!![path - 1].size) {
            return 0.0
        }
        while (true) {
            val pl2 = pathlinks!![path - 1][i]
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
            if (i == pathlinks!![path - 1].size) {
                i = 0
            }
        }

        // We don't adjust the playback volume of the audio clip, so this is just
        // yes/no for now
        if (gotcatch) return 1.0

        return 0.0
    }

    // Get volume of any bounce between time1 and time2.
    //
    // If no bounce then return 0.

    fun getPathBounceVolume(path: Int, time1: Double, time2: Double): Double {
        var i = 0
        var pl: PathLink

        while (i < pathlinks!![path - 1].size) {
            pl = pathlinks!![path - 1][i]
            if (time1 >= pl.startEvent.t && time1 <= pl.endEvent.t) {
                break
            }
            i++
        }
        if (i == pathlinks!![path - 1].size) {
            return 0.0
        }
        while (true) {
            pl = pathlinks!![path - 1][i]
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
            if (i == pathlinks!![path - 1].size) {
                i = 0
            }
        }

        return 0.0
    }

    fun getPathMax(path: Int): Coordinate? {  // maximum of each coordinate
        var result: Coordinate? = null
        val t1 = this.loopStartTime
        val t2 = this.loopEndTime

        for (i in pathlinks!![path - 1].indices) {
            val pl = pathlinks!![path - 1][i]
            if (pl.isInHand) {
                val coord2 = getHandMax(pl.holdingJuggler, pl.holdingHand)
                result = max(result, coord2)
            } else {
                val coord2 = pl.path!!.getMax(t1, t2)
                result = max(result, coord2)
            }
        }
        return result
    }

    fun getPathMin(path: Int): Coordinate? {
        var result: Coordinate? = null
        val t1 = this.loopStartTime
        val t2 = this.loopEndTime

        for (i in pathlinks!![path - 1].indices) {
            val pl = pathlinks!![path - 1][i]
            if (pl.isInHand) {
                if (Constants.DEBUG_LAYOUT) {
                    println(
                        "Path min " + path + " link " + i + ": HandMin = " +
                            getHandMin(pl.holdingJuggler, pl.holdingHand)
                    )
                }
                result = min(result, getHandMin(pl.holdingJuggler, pl.holdingHand))
            } else {
                if (Constants.DEBUG_LAYOUT) {
                    println(
                        "Path min " + path + " link " + ": PathMin = " +
                            pl.path!!.getMin(t1, t2)
                    )
                }
                result = min(result, pl.path!!.getMin(t1, t2))
            }
        }
        return result
    }

    fun getHandMax(juggler: Int, hand: Int): Coordinate? {
        var result: Coordinate? = null
        val t1 = loopStartTime
        val t2 = loopEndTime
        val handnum = if (hand == HandLink.LEFT_HAND) 0 else 1

        for (i in handlinks!![juggler - 1][handnum].indices) {
            val hl = handlinks!![juggler - 1][handnum][i]
            val hp = hl.handCurve
            if (hp != null) {
                if (Constants.DEBUG_LAYOUT) {
                    println("getHandMax($juggler,$hand) = ${hp.getMin(t1, t2)}")
                }
                result = max(result, hp.getMax(t1, t2))
            }
        }
        return result
    }

    fun getHandMin(juggler: Int, hand: Int): Coordinate? {
        var result: Coordinate? = null
        val t1 = loopStartTime
        val t2 = loopEndTime
        val handnum = if (hand == HandLink.LEFT_HAND) 0 else 1

        for (i in handlinks!![juggler - 1][handnum].indices) {
            val hl = handlinks!![juggler - 1][handnum][i]
            val hp = hl.handCurve
            if (hp != null) {
                if (Constants.DEBUG_LAYOUT) {
                    println("getHandMin($juggler,$hand) = ${hp.getMin(t1, t2)}")
                }
                result = min(result, hp.getMin(t1, t2))
            }
        }
        return result
    }

    fun getJugglerMax(juggler: Int): Coordinate? {
        return jugglercurve[juggler - 1]!!.max
    }

    fun getJugglerMin(juggler: Int): Coordinate? {
        return jugglercurve[juggler - 1]!!.min
    }

    val pathPermutation: Permutation?
        get() = symmetries.find { it.getType() == JMLSymmetry.TYPE_DELAY }?.pathPerm

    val periodWithProps: Int
        get() = getPeriod(pathPermutation!!, propassignment!!)

    val isBouncePattern: Boolean
        get() = pathlinks!!.any { it.any { it1 -> it1.path is BouncePath } }

    //--------------------------------------------------------------------------
    // Reader/writer methods
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class)
    private fun readJML(current: JMLNode) {
        // process current node, then treat subnodes recursively

        val type = current.nodeType

        if (type.equals("jml", ignoreCase = true)) {
            val vers = current.attributes.getAttribute("version")
            if (vers != null) {
                if (jlCompareVersions(vers, JMLDefs.CURRENT_JML_VERSION) > 0) {
                    throw JuggleExceptionUser(errorstrings.getString("Error_JML_version"))
                }
                loadingversion = vers
            }
        } else if (type.equals("pattern", ignoreCase = true)) {
            // do nothing
        } else if (type.equals("title", ignoreCase = true)) {
            title = current.nodeValue
        } else if (type.equals("info", ignoreCase = true)) {
            info = current.nodeValue
            val tagstr = current.attributes.getAttribute("tags")
            if (tagstr != null) {
                for (t in tagstr.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()) {
                    addTag(t.trim())
                }
            }
        } else if (type.equals("basepattern", ignoreCase = true)) {
            this.basePatternNotation =
                Pattern.canonicalNotation(current.attributes.getAttribute("notation"))
            this.basePatternConfig = current.nodeValue!!.trim()
        } else if (type.equals("prop", ignoreCase = true)) {
            val pd = JMLProp()
            pd.readJML(current, loadingversion)
            addProp(pd)
        } else if (type.equals("setup", ignoreCase = true)) {
            val at = current.attributes
            val jugglerstring = at.getAttribute("jugglers")
            val pathstring: String? = at.getAttribute("paths")
            val propstring = at.getAttribute("props")

            try {
                numberOfJugglers = jugglerstring?.toInt() ?: 1
                numberOfPaths = pathstring!!.toInt()
            } catch (_: Exception) {
                throw JuggleExceptionUser(errorstrings.getString("Error_setup_tag"))
            }

            val pa = IntArray(numpaths)
            if (propstring != null) {
                val st = StringTokenizer(propstring, ",")
                if (st.countTokens() != numpaths) {
                    throw JuggleExceptionUser(errorstrings.getString("Error_prop_assignments"))
                }
                try {
                    for (i in 0..<numpaths) {
                        pa[i] = st.nextToken().toInt()
                        if (pa[i] < 1 || pa[i] > this.numberOfProps) {
                            throw JuggleExceptionUser(errorstrings.getString("Error_prop_number"))
                        }
                    }
                } catch (_: NumberFormatException) {
                    throw JuggleExceptionUser(errorstrings.getString("Error_prop_format"))
                }
            } else {
                for (i in 0..<numpaths) {
                    pa[i] = 1
                }
            }
            setPropAssignments(pa)
        } else if (type.equals("symmetry", ignoreCase = true)) {
            val sym = JMLSymmetry()
            sym.readJML(current, numjugglers, numpaths, loadingversion)
            addSymmetry(sym)
        } else if (type.equals("event", ignoreCase = true)) {
            val ev = JMLEvent()
            ev.readJML(
                current, loadingversion, numberOfJugglers, numberOfPaths
            ) // look at subnodes
            addEvent(ev)
            return  // stop recursion
        } else if (type.equals("position", ignoreCase = true)) {
            val pos = JMLPosition()
            pos.readJML(current, loadingversion)
            addPosition(pos)
            return
        } else {
            val template: String = errorstrings.getString("Error_unknown_tag")
            val arguments = arrayOf<Any?>(type)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        }

        for (i in 0..<current.numberOfChildren) {
            readJML(current.getChildNode(i))
        }

        // Set title in base pattern, if any. Do this after reading the <basepattern>
        // tag so that we don't overwrite these changes.
        if (current.nodeType.equals("jml", ignoreCase = true)) {
            title = title
        }
    }

    @Throws(IOException::class)
    fun writeJML(wr: Writer, writeTitle: Boolean, writeInfo: Boolean) {
        val write = PrintWriter(wr)

        for (i in JMLDefs.jmlprefix.indices) {
            write.println(JMLDefs.jmlprefix[i])
        }
        write.println("<jml version=\"${xmlescape(version)}\">")
        write.println("<pattern>")
        if (writeTitle && title != null) {
            write.println("<title>${xmlescape(title!!)}</title>")
        }
        if (writeInfo && (info != null || !tags.isEmpty())) {
            val tagstr = tags.joinToString(",")
            if (info != null) {
                if (tagstr.isEmpty()) {
                    write.println("<info>${xmlescape(info!!)}</info>")
                } else {
                    write.println(
                        ("<info tags=\"${xmlescape(tagstr)}\">${xmlescape(info!!)}</info>")
                    )
                }
            } else {
                write.println("<info tags=\"${xmlescape(tagstr)}\"/>")
            }
        }
        if (basePatternNotation != null && basePatternConfig != null) {
            write.println(
                ("<basepattern notation=\""
                    + xmlescape(basePatternNotation!!.lowercase(Locale.getDefault()))
                    + "\">")
            )
            write.println(xmlescape(basePatternConfig!!.replace(";".toRegex(), ";\n")))
            write.println("</basepattern>")
        }
        for (prop in props) {
            prop.writeJML(write)
        }

        var out =
            ("<setup jugglers=\""
                + this.numberOfJugglers
                + "\" paths=\""
                + this.numberOfPaths
                + "\" props=\"")

        if (numberOfPaths > 0) {
            out += getPropAssignment(1)
            for (i in 2..numberOfPaths) {
                out += "," + getPropAssignment(i)
            }
        }
        write.println("$out\"/>")

        for (symmetry in symmetries) {
            symmetry.writeJML(write)
        }

        var pos = this.positionList
        while (pos != null) {
            pos.writeJML(write)
            pos = pos.next
        }

        var ev = this.eventList
        while (ev != null) {
            if (ev.isMaster) {
                ev.writeJML(write)
            }
            ev = ev.next
        }
        write.println("</pattern>")

        write.println("</jml>")
        for (i in JMLDefs.jmlsuffix.indices) {
            write.println(JMLDefs.jmlsuffix[i])
        }
        write.flush()
    }

    @get:Throws(JuggleExceptionInternal::class)
    val rootNode: JMLNode?
        get() {
            try {
                val parser = JMLParser()
                parser.parse(StringReader(toString()))
                return parser.tree
            } catch (se: SAXException) {
                throw JuggleExceptionInternal(se.message, this)
            } catch (se: IOException) {
                throw JuggleExceptionInternal(se.message, this)
            }
        }

    override fun toString(): String {
        val sw = StringWriter()
        try {
            writeJML(sw, writeTitle = true, writeInfo = true)
        } catch (_: IOException) {
        }
        return sw.toString()
    }

    companion object {
        // Create a JMLPattern from another notation. Here `config` can be regular
        // (like `pattern=3`) or not (like `3`).

        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun fromBasePattern(notation: String, config: String): JMLPattern {
            val p = Pattern.newPattern(notation).fromString(config)
            val pat = p.asJMLPattern()

            // regularize the notation name and config string
            pat.basePatternNotation = p.notationName
            pat.basePatternConfig = p.toString()
            return pat
        }

        fun getPeriod(perm: Permutation, propassign: IntArray): Int {
            var period = 1
            val size = perm.size
            val done = BooleanArray(size)

            for (i in 0..<size) {
                if (done[i]) continue

                val cycle = perm.getCycle(i + 1)
                for (j in 0..<cycle.size) {
                    done[cycle[j] - 1] = true
                    cycle[j] = propassign[cycle[j] - 1]
                }
                // find the period of the current cycle
                for (cperiod in 1..<cycle.size) {
                    if (cycle.size % cperiod != 0) continue

                    var matches = true
                    for (k in 0..<cycle.size) {
                        if (cycle[k] != cycle[(k + cperiod) % cycle.size]) {
                            matches = false
                            break
                        }
                    }
                    if (matches) {
                        period = lcm(period, cperiod)
                        break
                    }
                }
            }
            return period
        }
    }
}
