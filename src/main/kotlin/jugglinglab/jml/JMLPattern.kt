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

package jugglinglab.jml

import jugglinglab.JugglingLab
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
import java.util.*
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.BooleanArray
import kotlin.Double
import kotlin.DoubleArray
import kotlin.Exception
import kotlin.Int
import kotlin.IntArray
import kotlin.NumberFormatException
import kotlin.Suppress
import kotlin.Throws
import kotlin.arrayOf
import kotlin.arrayOfNulls
import kotlin.collections.ArrayList
import kotlin.collections.MutableCollection
import kotlin.collections.dropLastWhile
import kotlin.collections.indices
import kotlin.collections.toTypedArray
import kotlin.math.cos
import kotlin.math.sin
import kotlin.run
import kotlin.text.equals
import kotlin.text.isEmpty
import kotlin.text.replace
import kotlin.text.split
import kotlin.text.toInt
import kotlin.text.toRegex
import kotlin.text.trim

class JMLPattern() {
    protected var version: String = JMLDefs.CURRENT_JML_VERSION
    var info: String? = null
        set(t) {
            field = if (t != null && !t.trim().isBlank()) t.trim() else null
        }

    var tags: ArrayList<String>
        protected set
    protected var numjugglers: Int = 0
    protected var numpaths: Int = 0
    protected var props: ArrayList<PropDef>
    protected var propassignment: IntArray? = null

    //--------------------------------------------------------------------------
    // Methods related to the base pattern (if set)
    //--------------------------------------------------------------------------
    // for retaining the base pattern this pattern was created from
    var basePatternNotation: String? = null
        private set
    var basePatternConfig: String? = null
        private set
    protected var base_pattern_hashcode: Int = 0
    protected var base_pattern_hashcode_valid: Boolean = false

    // whether pattern has a velocity-defining transition
    private lateinit var hasVDPathJMLTransition: BooleanArray // for a given path
    private lateinit var hasVDHandJMLTransition: Array<BooleanArray> // for a given juggler/hand

    protected var symmetries: java.util.ArrayList<JMLSymmetry>
    var eventList: JMLEvent? = null
        protected set
    var positionList: JMLPosition? = null
        protected set

    // list of PathLink objects for each path
    protected var pathlinks: java.util.ArrayList<java.util.ArrayList<PathLink>?>? = null

    // list of HandLink objects for each juggler/hand combination
    protected var handlinks: java.util.ArrayList<java.util.ArrayList<java.util.ArrayList<HandLink>?>?>? =
        null

    protected lateinit var jugglercurve: Array<Curve?> // coordinates for each juggler
    protected lateinit var jugglerangle: Array<Curve?> // angles for each juggler

    protected var loadingversion: String? = JMLDefs.CURRENT_JML_VERSION
    protected var laidout: Boolean = false
    var isValid: Boolean = true
        protected set

    var title: String? = null
        set(title) {
            val t = title?.replace(";".toRegex(), "")  // semicolons not allowed
            field = if (t != null && !t.isBlank()) t.trim() else null

            // Check if there is a base pattern defined, and if so set the new title
            // in the base pattern as well
            if (this.basePatternNotation == null || this.basePatternConfig == null) {
                return
            }

            try {
                val pl = ParameterList(this.basePatternConfig)

                // Is the title equal to the default title? If so then remove the
                // title parameter
                if (pl.getParameter("pattern") == title) {
                    pl.removeParameter("title")
                } else {
                    pl.addParameter("title", if (title == null) "" else title)
                }

                basePatternConfig = pl.toString()
                base_pattern_hashcode_valid = false // recalculate hash code
            } catch (jeu: JuggleExceptionUser) {
                // can't be a user error since base pattern has already successfully
                // compiled
                handleFatalException(JuggleExceptionInternal(jeu.message, this))
            }
        }

    init {
        tags = ArrayList<String>()
        props = ArrayList<PropDef>()
        symmetries = ArrayList<JMLSymmetry>()
    }

    constructor(root: JMLNode) : this() {
        readJML(root)
        isValid = true
    }

    // Used to specify the jml version number, when pattern is part of a patternlist

    constructor(root: JMLNode, jmlvers: String) : this() {
        loadingversion = jmlvers
        readJML(root)
        isValid = true
    }

    constructor(read: Reader?) : this() {
        try {
            val parser = JMLParser()
            parser.parse(read)
            readJML(parser.tree!!)
            this.isValid = true
        } catch (se: SAXException) {
            throw JuggleExceptionUser(se.message)
        } catch (ioe: IOException) {
            throw JuggleExceptionInternal(ioe.message)
        }
    }

    constructor(pat: JMLPattern) : this(StringReader(pat.toString()))

    //--------------------------------------------------------------------------
    // Methods to define the pattern
    //--------------------------------------------------------------------------

    fun addTag(tag: String?) {
        if (tag != null && !tag.isEmpty() && !isTaggedWith(tag)) {
            tags.add(tag)
        }
    }

    fun removeTag(tag: String?): Boolean {
        if (tag == null || !isTaggedWith(tag)) {
            return false
        }

        for (i in tags.indices) {
            if (tags.get(i).equals(tag, ignoreCase = true)) {
                tags.removeAt(i)
                return true
            }
        }
        return false // shouldn't happen
    }

    fun addProp(pd: PropDef?) {
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
            val combine_events =
                current!!.t == ev.t && current.hand == ev.hand && current.juggler == ev.juggler

            if (combine_events) {
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
                for (tr_current in current.transitions) {
                    var add_transition = true

                    for (tr in ev.transitions) {
                        if (tr.path == tr_current.path) {
                            add_transition = false
                            break
                        }
                    }

                    if (add_transition) {
                        ev.addTransition(tr_current)
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
        if (this.eventList == ev) {
            this.eventList = ev.next
            if (this.eventList != null) {
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
        var current = this.eventList
        while (current != null) {
            if (current.t >= this.loopStartTime && current.t < this.loopEndTime && current.juggler == ev.juggler && current.hand == ev.hand && current.hasSameMasterAs(
                    ev
                )
            ) {
                return current
            }
            current = current.next
        }
        return null
    }

    // used for debugging
    protected fun printEventList() {
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

    val pathLinks: ArrayList<ArrayList<PathLink>?>
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
                writeJML(sw, true, false)
            } catch (ioe: IOException) {
            }

            return sw.toString().hashCode()
        }

    fun hasBasePattern(): Boolean {
        return (this.basePatternNotation != null && this.basePatternConfig != null)
    }

    val isBasePatternEdited: Boolean
        get() {
            if (this.basePatternNotation == null || this.basePatternConfig == null) {
                return false
            }

            if (!base_pattern_hashcode_valid) {
                try {
                    base_pattern_hashcode =
                        fromBasePattern(this.basePatternNotation, this.basePatternConfig)
                            .layoutPattern().hashCode
                    base_pattern_hashcode_valid = true
                } catch (je: JuggleException) {
                    base_pattern_hashcode = 0
                    base_pattern_hashcode_valid = false
                    return false
                }
            }

            return (this.hashCode != base_pattern_hashcode)
        }

    //----------------------------------------------------------------------------
    // Some pattern transformations
    //----------------------------------------------------------------------------
    // Multiply all times in the pattern by a common factor `scale`.
    fun scaleTime(scale: Double) {
        var ev = this.eventList
        while (ev != null) {
            if (ev.isMaster) {
                ev.t = ev.t * scale
            }
            ev = ev.next
        }
        var pos = this.positionList
        while (pos != null) {
            pos.t = pos.t * scale
            pos = pos.next
        }

        for (sym in symmetries()) {
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
    // `multiplier` should typically be a little over 1
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun scaleTimeToFitThrows(multiplier: Double): Double {
        layoutPattern() // to ensure we have PathLinks
        var scale_factor = 1.0

        for (path in 1..this.numberOfPaths) {
            for (pl in this.pathLinks.get(path - 1)!!) {
                val p = pl.path
                if (p != null) {
                    val d = p.duration
                    val dmin = p.minDuration

                    if (d < dmin && d > 0) {
                        scale_factor = kotlin.math.max(scale_factor, dmin / d)
                    }
                }
            }
        }

        if (scale_factor > 1) {
            scale_factor *= multiplier // so things aren't just barely feasible
            scaleTime(scale_factor)
        }

        return scale_factor
    }

    // Swap the assignment of hands, leaving events in the same locations.
    fun swapHands() {
        var ev = this.eventList
        while (ev != null) {
            var hand = ev.hand

            // flip hand assignment, invert x coordinate
            if (hand == HandLink.LEFT_HAND) {
                hand = HandLink.RIGHT_HAND
            } else {
                hand = HandLink.LEFT_HAND
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
            if (hand == HandLink.LEFT_HAND) {
                hand = HandLink.RIGHT_HAND
            } else {
                hand = HandLink.LEFT_HAND
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
            val looptime = this.loopEndTime

            var ev = this.eventList
            while (ev != null) {
                ev.t = looptime - ev.t

                val prev = ev.previous
                val next = ev.next
                ev.previous = next
                ev.next = prev

                if (next == null) {
                    this.eventList = ev // new list head
                }

                ev = next
            }

            // For each JMLPosition:
            //     - set t = looptime - t
            //     - sort the position list in time
            var pos = this.positionList
            this.positionList = null
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
            for (sym in symmetries()) {
                if (sym.getType() == JMLSymmetry.TYPE_SWITCH) {
                    continue
                }

                val newpathperm = sym.pathPerm!!.inverse
                sym.setPathPerm(sym.numberOfPaths, newpathperm.toString())
            }

            // for each PathLink:
            //     - find corresponding throw-type JMLTransition in startevent
            //     - find corresponding catch-type JMLTransition in endevent
            //     - swap {type, throw type, throw mod} for the two transitions
            for (path in 1..this.numberOfPaths) {
                for (pl in this.pathLinks.get(path - 1)!!) {
                    if (pl.isInHand) {
                        continue
                    }

                    val start = pl.startEvent
                    val end = pl.endEvent

                    var start_tr: JMLTransition? = null
                    for (tr in start.transitions) {
                        if (tr.path == path) {
                            start_tr = tr
                            break
                        }
                    }

                    var end_tr: JMLTransition? = null
                    for (tr in end.transitions) {
                        if (tr.path == path) {
                            end_tr = tr
                            break
                        }
                    }

                    if (start_tr == null || end_tr == null) {
                        throw JuggleExceptionInternal("invertTime() error 1", this)
                    }
                    if (start_tr.outgoingPathLink != pl) {
                        throw JuggleExceptionInternal("invertTime() error 2", this)
                    }
                    if (end_tr.incomingPathLink != pl) {
                        throw JuggleExceptionInternal("invertTime() error 3", this)
                    }

                    val start_tr_type = start_tr.getType()
                    val start_tr_throw_type = start_tr.throwType
                    val start_tr_throw_mod = start_tr.mod

                    start_tr.setType(end_tr.getType())
                    start_tr.throwType = end_tr.throwType
                    start_tr.mod = end_tr.mod
                    end_tr.setType(start_tr_type)
                    end_tr.throwType = start_tr_throw_type
                    end_tr.mod = start_tr_throw_mod

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
        layoutPattern() // to ensure we have PathLinks

        var n_events = 0 // for reporting stats
        var n_holds = 0
        var n_removed = 0

        var ev = this.eventList

        while (ev != null) {
            val prev = ev.previousForHand
            val next = ev.nextForHand

            var holding_only = true
            for (tr in ev.transitions) {
                if (tr.getType() != JMLTransition.TRANS_HOLDING) {
                    holding_only = false
                    break
                }
            }
            val different_masters = (prev == null || !ev.hasSameMasterAs(prev))
            val inside_window = (prev != null && (ev.t - prev.t) < twindow)
            val not_pass_adjacent =
                (prev != null && next != null && !prev.hasPassingTransition() && !next.hasPassingTransition())

            val remove = holding_only && different_masters && inside_window && not_pass_adjacent

            if (remove) {
                removeEvent(ev)
                n_removed++
            }

            n_events++
            if (holding_only) {
                n_holds++
            }

            ev = ev.next
        }

        if (Constants.DEBUG_LAYOUT) {
            println("Streamlined with time window " + twindow + " secs:")
            println(
                ("    Removed "
                    + n_removed
                    + " of "
                    + n_holds
                    + " holding events ("
                    + n_events
                    + " events total)")
            )
        }
    }

    //----------------------------------------------------------------------------
    // Lay out the spatial paths in the pattern
    //
    // Note that this can change the pattern's toString() representation,
    // and therefore its hash code.
    //----------------------------------------------------------------------------
    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    fun layoutPattern(): JMLPattern {
        if (laidout) {
            return this
        }

        if (!this.isValid) {
            throw JuggleExceptionInternal("Cannot do layout of invalid pattern", this)
        }

        try {
            if (this.numberOfProps == 0 && this.numberOfPaths > 0) {
                addProp(PropDef("ball", null))
            }
            for (i in 0..<this.numberOfProps) {
                props.get(i).layoutProp()
            }

            buildEventList()
            findMasterEvents()
            findPositions()
            gotoGlobalCoordinates()
            buildLinkLists()
            layoutHandPaths()

            if (Constants.DEBUG_LAYOUT) {
                for (i in 0..<this.numberOfPaths) {
                    println(pathlinks!!.get(i)!!.size.toString() + " pathlinks for path " + (i + 1) + ":")
                    for (jtemp in pathlinks!!.get(i)!!.indices) {
                        println("   " + pathlinks!!.get(i)!!.get(jtemp))
                    }
                }
                for (i in 0..<this.numberOfJugglers) {
                    for (j in 0..1) {
                        println(
                            (handlinks!!.get(i)!!.get(j)!!.size
                                .toString() + " handlinks for juggler "
                                + (i + 1)
                                + ", hand "
                                + (j + 1)
                                + ":")
                        )
                        for (k in handlinks!!.get(i)!!.get(j)!!.indices) {
                            println("   " + handlinks!!.get(i)!!.get(j)!!.get(k))
                        }
                    }
                }
            }
            laidout = true
        } catch (jeu: JuggleExceptionUser) {
            this.isValid = false
            throw jeu
        } catch (jei: JuggleExceptionInternal) {
            this.isValid = false
            jei.attachPattern(this)
            throw jei
        }

        return this
    }

    fun setNeedsLayout() {
        laidout = false
    }

    //----------------------------------------------------------------------------
    // Step 1: construct the list of events
    // Extend events in list using known symmetries
    //----------------------------------------------------------------------------
    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    fun buildEventList() {
        // figure out how many events there are
        var numevents = 0
        var current = this.eventList
        while (current != null) {
            if (current.juggler < 1 || current.juggler > numjugglers) {
                throw JuggleExceptionUser(errorstrings.getString("Error_juggler_outofrange"))
            }
            if (current.isMaster) {
                numevents++
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
        val needHandEvent = Array<BooleanArray?>(numjugglers) { BooleanArray(2) }
        val needVDHandEvent = Array<BooleanArray?>(numjugglers) { BooleanArray(2) }
        val needPathEvent = BooleanArray(numpaths)
        val needSpecialPathEvent = BooleanArray(numpaths)
        hasVDHandJMLTransition = Array<BooleanArray>(numjugglers) { BooleanArray(2) }
        hasVDPathJMLTransition = BooleanArray(numpaths)

        // make sure each hand and path are hit at least once
        for (i in 0..<numjugglers) {
            var hasJMLTransitionForLeft = false
            var hasJMLTransitionForRight = false
            hasVDHandJMLTransition[i]!![1] = false
            hasVDHandJMLTransition[i]!![0] = hasVDHandJMLTransition[i]!![1]

            for (j in 0..<numevents) {
                if (!hasJMLTransitionForLeft) {
                    hasJMLTransitionForLeft =
                        ei[j]!!.hasJMLTransitionForHand(i + 1, HandLink.LEFT_HAND)
                }
                if (!hasJMLTransitionForRight) {
                    hasJMLTransitionForRight =
                        ei[j]!!.hasJMLTransitionForHand(i + 1, HandLink.RIGHT_HAND)
                }
                if (!hasVDHandJMLTransition[i]!![0]) {
                    hasVDHandJMLTransition[i]!![0] =
                        ei[j]!!.hasVDJMLTransitionForHand(i + 1, HandLink.LEFT_HAND)
                }
                if (!hasVDHandJMLTransition[i]!![1]) {
                    hasVDHandJMLTransition[i]!![1] =
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
            needVDHandEvent[i]!![0] = hasVDHandJMLTransition[i]!![0] // set up for later
            needVDHandEvent[i]!![1] = hasVDHandJMLTransition[i]!![1]
            needHandEvent[i]!![1] = true
            needHandEvent[i]!![0] = needHandEvent[i]!![1]
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
        val eventqueue: Array<JMLEvent?> = arrayOfNulls<JMLEvent>(numevents)
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

                if (!hasVDHandJMLTransition[jug]!![han]) {
                    needHandEvent[jug]!![han] = false
                }

                for (tr in maxevent.transitions) {
                    val path = tr.path - 1

                    when (tr.getType()) {
                        JMLTransition.TRANS_THROW -> {
                            needPathEvent[path] = false
                            run {
                                needHandEvent[jug]!![han] = false
                                needVDHandEvent[jug]!![han] = needHandEvent[jug]!![han]
                            }
                            needSpecialPathEvent[path] = false
                        }

                        JMLTransition.TRANS_CATCH, JMLTransition.TRANS_GRABCATCH -> {}
                        JMLTransition.TRANS_SOFTCATCH -> {
                            if (needVDHandEvent[jug]!![han]) {
                                needSpecialPathEvent[path] =
                                    true // need corresponding throw to get velocity
                            }
                            run {
                                needHandEvent[jug]!![han] = false
                                needVDHandEvent[jug]!![han] = needHandEvent[jug]!![han]
                            }
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
                contin = contin or needHandEvent[i]!![0]
                contin = contin or needHandEvent[i]!![1]
                contin = contin or needVDHandEvent[i]!![0]
                contin = contin or needVDHandEvent[i]!![1]
            }
            for (i in 0..<numpaths) {
                contin = contin or needPathEvent[i]
                contin = contin or needSpecialPathEvent[i]
            }
        } while (contin)

        // reset things to go forward in time
        for (i in 0..<numjugglers) {
            needVDHandEvent[i]!![0] = hasVDHandJMLTransition[i]!![0]
            needVDHandEvent[i]!![1] = hasVDHandJMLTransition[i]!![1]
            needHandEvent[i]!![1] = true
            needHandEvent[i]!![0] = needHandEvent[i]!![1]
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
            if (mintime > this.loopEndTime) {
                val jug = minevent.juggler - 1
                val han = index(minevent.hand)

                // if this hand has no throws/catches, then need to build out event list
                // past a certain time, due to how the hand layout is done in this case
                // (see layoutHandPaths() below)
                if ((!hasVDHandJMLTransition[jug]!![han])
                    && (mintime > (2 * this.loopEndTime - this.loopStartTime))
                ) {
                    needHandEvent[jug]!![han] = false
                }

                for (tr in minevent.transitions) {
                    val path = tr.path - 1

                    when (tr.getType()) {
                        JMLTransition.TRANS_THROW -> {
                            needPathEvent[path] = false
                            if (needVDHandEvent[jug]!![han]) {
                                // need corresponding catch to get velocity
                                needSpecialPathEvent[path] = true
                            }
                            run {
                                needHandEvent[jug]!![han] = false
                                needVDHandEvent[jug]!![han] = needHandEvent[jug]!![han]
                            }
                        }

                        JMLTransition.TRANS_CATCH, JMLTransition.TRANS_GRABCATCH -> {
                            needPathEvent[path] = false
                            needSpecialPathEvent[path] = false
                        }

                        JMLTransition.TRANS_SOFTCATCH -> {
                            needPathEvent[path] = false
                            run {
                                needHandEvent[jug]!![han] = false
                                needVDHandEvent[jug]!![han] = needHandEvent[jug]!![han]
                            }
                            needSpecialPathEvent[path] = false
                        }

                        JMLTransition.TRANS_HOLDING -> if (!hasVDPathJMLTransition[path]) {
                            needPathEvent[path] = false // no throws for this path, done
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
                contin = contin or needHandEvent[i]!![0]
                contin = contin or needHandEvent[i]!![1]
                contin = contin or needVDHandEvent[i]!![0]
                contin = contin or needVDHandEvent[i]!![1]
            }
            for (i in 0..<numpaths) {
                contin = contin or needPathEvent[i]
                contin = contin or needSpecialPathEvent[i]
            }
        } while (contin)
    }

    //----------------------------------------------------------------------------
    // Step 2: figure out which events should be considered master events
    //----------------------------------------------------------------------------
    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    fun findMasterEvents() {
        var rebuildList = false
        var ev = this.eventList

        while (ev != null) {
            if (ev.isMaster) {
                var newmaster: JMLEvent? = ev
                var tmaster = this.loopEndTime
                if (ev.t >= this.loopStartTime && ev.t < tmaster) {
                    tmaster = ev.t
                }

                var ev2 = this.eventList
                while (ev2 != null) {
                    if (ev2.master == ev) {
                        if (ev2.t >= this.loopStartTime && ev2.t < tmaster) {
                            newmaster = ev2
                            tmaster = ev2.t
                        }
                    }
                    ev2 = ev2.next
                }

                if (newmaster != ev) {
                    rebuildList = true
                    ev2 = this.eventList
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
        jugglercurve = arrayOfNulls(this.numberOfJugglers)
        jugglerangle =
            (if (Constants.ANGLE_LAYOUT_METHOD == Curve.CURVE_LINE)
                (arrayOfNulls<Curve>(this.numberOfJugglers)) as Array<Curve?>
            else
                (arrayOfNulls<Curve>(this.numberOfJugglers)) as Array<Curve?>)

        for (i in 1..this.numberOfJugglers) {
            var num = 0
            var current = this.positionList

            while (current != null) {
                if (current.juggler == i) {
                    num++
                }
                current = current.next
            }

            if (num == 0) {
                jugglercurve[i - 1] = SplineCurve()
                jugglerangle[i - 1] =
                    ((if (jugglinglab.core.Constants.ANGLE_LAYOUT_METHOD == jugglinglab.curve.Curve.CURVE_LINE)
                        (LineCurve()) as jugglinglab.curve.Curve
                    else
                        (SplineCurve()) as jugglinglab.curve.Curve?)!!)
                val times = DoubleArray(2)
                times[0] = this.loopStartTime
                times[1] = this.loopEndTime
                val positions = Array(2) { Coordinate() }
                val angles = Array(2) { Coordinate() }
                positions[0] = Coordinate()
                angles[0] = Coordinate()

                // default juggler body positions
                if (this.numberOfJugglers == 1) {
                    positions[0]!!.setCoordinate(0.0, 0.0, 100.0)
                    angles[0]!!.setCoordinate(0.0, 0.0, 0.0)
                } else {
                    var r = 70.0
                    val theta = 360 / this.numberOfJugglers.toDouble()
                    if (r * sin(Math.toRadians(0.5 * theta)) < 65) {
                        r = 65 / sin(Math.toRadians(0.5 * theta))
                    }
                    positions[0]!!.setCoordinate(
                        r * cos(Math.toRadians(theta * (i - 1).toDouble())),
                        r * sin(Math.toRadians(theta * (i - 1).toDouble())),
                        100.0
                    )
                    angles[0]!!.setCoordinate(90 + theta * (i - 1).toDouble(), 0.0, 0.0)
                }

                positions[1] = positions[0]
                angles[1] = angles[0]
                jugglercurve[i - 1]!!.setCurve(times, positions, arrayOfNulls<Coordinate>(2))
                jugglercurve[i - 1]!!.calcCurve()
                jugglerangle[i - 1]!!.setCurve(times, angles, arrayOfNulls<Coordinate>(2))
                jugglerangle[i - 1]!!.calcCurve()
            } else {
                jugglercurve[i - 1] = SplineCurve()
                jugglerangle[i - 1] =
                    ((if (jugglinglab.core.Constants.ANGLE_LAYOUT_METHOD == jugglinglab.curve.Curve.CURVE_LINE)
                        (LineCurve()) as jugglinglab.curve.Curve
                    else
                        (SplineCurve()) as jugglinglab.curve.Curve?)!!)
                val times = DoubleArray(num + 1)
                val positions = Array(num + 1) { Coordinate() }
                val angles = Array(num + 1) { Coordinate() }

                current = this.positionList
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
                times[num] = times[0] + this.loopEndTime - this.loopStartTime
                positions[num] = positions[0]
                angles[num] = Coordinate(angles[0]!!.x, angles[0]!!.y, angles[0]!!.z)

                j = 1
                while (j <= num) {
                    while ((angles[j]!!.x - angles[j - 1]!!.x) > 180) {
                        angles[j]!!.x -= 360.0
                    }
                    while ((angles[j]!!.x - angles[j - 1]!!.x) < -180) {
                        angles[j]!!.x += 360.0
                    }
                    j++
                }

                jugglercurve[i - 1]!!.setCurve(times, positions, arrayOfNulls<Coordinate>(num + 1))
                jugglercurve[i - 1]!!.calcCurve()
                jugglerangle[i - 1]!!.setCurve(times, angles, arrayOfNulls<Coordinate>(num + 1))
                jugglerangle[i - 1]!!.calcCurve()
            }
        }
    }

    //----------------------------------------------------------------------------
    // Step 4: transform event coordinates from local to global reference frame
    //----------------------------------------------------------------------------
    fun gotoGlobalCoordinates() {
        var ev = this.eventList

        while (ev != null) {
            val lc = ev.localCoordinate
            val gc = convertLocalToGlobal(lc, ev.juggler, ev.t)
            ev.globalCoordinate = gc
            ev = ev.next
        }
    }

    //----------------------------------------------------------------------------
    // Step 5: construct the links connecting events; build PathLink and
    // HandLink lists
    //----------------------------------------------------------------------------
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    protected fun buildLinkLists() {
        pathlinks = java.util.ArrayList<java.util.ArrayList<PathLink>?>(this.numberOfPaths)

        for (i in 0..<this.numberOfPaths) {
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

                    when (tr.getType()) {
                        JMLTransition.TRANS_THROW, JMLTransition.TRANS_HOLDING -> {
                            if (lasttr!!.getType() == JMLTransition.TRANS_THROW) {
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
                            if (lasttr!!.getType() != JMLTransition.TRANS_THROW) {
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

                    pathlinks!!.get(i)!!.add(pl)
                    if (lasttr != null) {
                        lasttr.outgoingPathLink = pl
                    }
                    tr.incomingPathLink = pl
                }

                lastev = ev
                lasttr = tr
                ev = ev.next
                if (ev == null) {
                    break
                }
            }

            if (pathlinks!!.get(i)!!.isEmpty()) {
                throw JuggleExceptionInternal("No event found for path " + (i + 1), this)
            }
        }

        // now build the HandLink lists
        handlinks = java.util.ArrayList<java.util.ArrayList<java.util.ArrayList<HandLink>?>?>()

        for (i in 0..<this.numberOfJugglers) {
            // build the HandLink list for the ith juggler

            handlinks!!.add(ArrayList())

            for (j in 0..1) {
                val handnum = (if (j == 0) HandLink.LEFT_HAND else HandLink.RIGHT_HAND)

                handlinks!!.get(i)!!.add(ArrayList())

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
                            if (tr.getType() == JMLTransition.TRANS_THROW) {
                                val pl = tr.outgoingPathLink
                                if (pl != null) {
                                    vr = VelocityRef(pl.path!!, VelocityRef.VR_THROW)
                                }
                            } else if (tr.getType() == JMLTransition.TRANS_SOFTCATCH) {
                                val pl = tr.incomingPathLink
                                if (pl != null) {
                                    vr = VelocityRef(pl.path!!, VelocityRef.VR_SOFTCATCH)
                                }
                            } else if (tr.getType() == JMLTransition.TRANS_CATCH) {
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
                        handlinks!!.get(i)!!.get(j)!!.add(hl)
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

    //----------------------------------------------------------------------------
    // Step 6: do a physical layout of the handlink paths
    // (Props were physically laid out in PathLink.setThrow() in Step 5 above)
    //----------------------------------------------------------------------------
    @Throws(JuggleExceptionInternal::class)
    protected fun layoutHandPaths() {
        // go through HandLink lists, creating Path objects and calculating paths

        for (j in 0..<this.numberOfJugglers) {
            for (h in 0..1) {
                // There are two cases: a hand has throw or softcatch events (which define
                // hand velocities at points in time), or it does not (no velocities known).
                // To determine the spline paths, we need to solve for hand velocity at each
                // of its events, but this is done differently in the two cases.

                if (hasVDHandJMLTransition[j]!![h]) {
                    var startlink: HandLink? = null
                    var num = 0

                    for (k in handlinks!!.get(j)!!.get(h)!!.indices) {
                        val hl = handlinks!!.get(j)!!.get(h)!!.get(k)

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
                                val hl2 = handlinks!!.get(j)!!.get(h)!!.get(k - num + 1 + l)
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
                    while (k < handlinks!!.get(j)!!.get(h)!!.size) {
                        hl = handlinks!!.get(j)!!.get(h)!!.get(k)
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
                            hl = handlinks!!.get(j)!!.get(h)!!.get(++k)
                            ++num
                        }
                        val times = DoubleArray(num + 1)
                        val pos = Array(num + 1) { Coordinate() }
                        val hp: Curve = SplineCurve()

                        for (l in 0..<num) {
                            val hl2 = handlinks!!.get(j)!!.get(h)!!.get(k - num + 1 + l)
                            pos[l] = hl2.startEvent.globalCoordinate!!
                            times[l] = hl2.startEvent.t
                            hl2.handCurve = hp
                        }
                        pos[num] = hl.endEvent.globalCoordinate!!
                        times[num] = hl.endEvent.t
                        // all velocities are null (unknown) -> signal to calculate
                        hp.setCurve(times, pos, arrayOfNulls<Coordinate>(num + 1))
                        hp.calcCurve()

                        if (chain == 0) {
                            hl = handlinks!!.get(j)!!.get(h)!!.get(++k)
                        }
                    }
                }
            }
        }
    }

    //----------------------------------------------------------------------------
    // Methods used by the animator to animate the pattern
    //----------------------------------------------------------------------------

    fun isTaggedWith(tag: String?): Boolean {
        if (tag == null) {
            return false
        }

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
        return getPropDef(propnum)!!.prop
    }

    fun getPropDef(propnum: Int): PropDef? {
        return props.get(propnum - 1)
    }

    fun getPropAssignment(path: Int): Int {
        return propassignment!!.get(path - 1)
    }

    fun symmetries(): MutableCollection<JMLSymmetry> {
        return symmetries
    }

    val loopStartTime: Double
        get() = 0.0

    val loopEndTime: Double
        get() {
            for (sym in symmetries()) {
                if (sym.getType() == JMLSymmetry.TYPE_DELAY) {
                    return sym.delay
                }
            }
            return -1.0
        }

    // returns path coordinate in global frame
    @Throws(JuggleExceptionInternal::class)
    fun getPathCoordinate(path: Int, time: Double, newPosition: Coordinate) {
        for (pl in pathlinks!!.get(path - 1)!!) {
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
        throw JuggleExceptionInternal("time t=" + time + " is out of path range", this)
    }

    // Check if a given hand is holding the path at a given time.
    fun isHandHoldingPath(juggler: Int, hand: Int, time: Double, path: Int): Boolean {
        for (pl in pathlinks!!.get(path - 1)!!) {
            if (!pl.isInHand) {
                continue
            }
            if (pl.holdingJuggler != juggler) {
                continue
            }
            if (pl.holdingHand != hand) {
                continue
            }
            if (time >= pl.startEvent.t && time <= pl.endEvent.t) {
                return true
            }
        }
        return false
    }

    // Check if a given hand is holding any path at the given time.
    fun isHandHolding(juggler: Int, hand: Int, time: Double): Boolean {
        for (path in 1..this.numberOfPaths) {
            if (isHandHoldingPath(juggler, hand, time, path)) return true
        }
        return false
    }

    // Return orientation of prop on given path, in global frame
    // result is {pitch, yaw, roll}.
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
            time += this.loopEndTime - this.loopStartTime
        }
        while (time > p.endTime) {
            time -= this.loopEndTime - this.loopStartTime
        }

        p.getCoordinate(time, newPosition)
    }

    // Return angle (in degrees) between local x axis and global x axis
    // (rotation around vertical z axis).
    fun getJugglerAngle(juggler: Int, time: Double): Double {
        var time = time
        val p = jugglerangle[juggler - 1]

        while (time < p!!.startTime) {
            time += this.loopEndTime - this.loopStartTime
        }
        while (time > p.endTime) {
            time -= this.loopEndTime - this.loopStartTime
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

        while (time < this.loopStartTime) time += this.loopEndTime - this.loopStartTime
        while (time >= this.loopEndTime) time -= this.loopEndTime - this.loopStartTime

        for (hl in handlinks!!.get(juggler - 1)!!.get(handindex)!!) {
            if (time >= hl.startEvent.t && time < hl.endEvent.t) {
                val hp = hl.handCurve
                if (hp == null) throw JuggleExceptionInternal(
                    "getHandCoordinate() null pointer at t=" + time,
                    this
                )
                hp.getCoordinate(time, newPosition)
                return
            }
        }
        throw JuggleExceptionInternal(
            "time t=" + time + " (j=" + juggler + ",h=" + handindex + ") is out of handpath range",
            this
        )
    }

    // Get volume of any catch made between time1 and time2.
    //
    // If no catch then return 0.
    fun getPathCatchVolume(path: Int, time1: Double, time2: Double): Double {
        var i: Int
        var pl1: PathLink
        var pl2: PathLink
        var wasinair = false
        var gotcatch = false

        i = 0
        while (i < pathlinks!!.get(path - 1)!!.size) {
            pl1 = pathlinks!!.get(path - 1)!!.get(i)
            if (time1 >= pl1.startEvent.t && time1 <= pl1.endEvent.t) break
            i++
        }
        if (i == pathlinks!!.get(path - 1)!!.size) return 0.0
        while (true) {
            pl2 = pathlinks!!.get(path - 1)!!.get(i)
            if (!pl2.isInHand) wasinair = true
            if (pl2.isInHand && wasinair) {
                gotcatch = true
                break
            }
            if (time2 >= pl2.startEvent.t && time2 <= pl2.endEvent.t) break

            i++
            if (i == pathlinks!!.get(path - 1)!!.size) i = 0
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
        var i: Int
        var pl: PathLink

        i = 0
        while (i < pathlinks!!.get(path - 1)!!.size) {
            pl = pathlinks!!.get(path - 1)!!.get(i)
            if (time1 >= pl.startEvent.t && time1 <= pl.endEvent.t) {
                break
            }
            i++
        }
        if (i == pathlinks!!.get(path - 1)!!.size) {
            return 0.0
        }
        while (true) {
            pl = pathlinks!!.get(path - 1)!!.get(i)
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
            if (i == pathlinks!!.get(path - 1)!!.size) {
                i = 0
            }
        }

        return 0.0
    }

    fun getPathMax(path: Int): Coordinate? {  // maximum of each coordinate
        var result: Coordinate? = null
        val t1 = this.loopStartTime
        val t2 = this.loopEndTime

        for (i in pathlinks!!.get(path - 1)!!.indices) {
            val pl = pathlinks!!.get(path - 1)!!.get(i)
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

        for (i in pathlinks!!.get(path - 1)!!.indices) {
            val pl = pathlinks!!.get(path - 1)!!.get(i)
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
        val t1 = this.loopStartTime
        val t2 = this.loopEndTime
        val handnum = if (hand == HandLink.LEFT_HAND) 0 else 1

        for (i in handlinks!!.get(juggler - 1)!!.get(handnum)!!.indices) {
            val hl = handlinks!!.get(juggler - 1)!!.get(handnum)!!.get(i)
            val hp = hl.handCurve
            if (hp != null) {
                if (Constants.DEBUG_LAYOUT) {
                    println("getHandMax(" + juggler + "," + hand + ") = " + hp.getMin(t1, t2))
                }
                result = max(result, hp.getMax(t1, t2))
            }
        }
        return result
    }

    fun getHandMin(juggler: Int, hand: Int): Coordinate? {
        var result: Coordinate? = null
        val t1 = this.loopStartTime
        val t2 = this.loopEndTime
        val handnum = if (hand == HandLink.LEFT_HAND) 0 else 1

        for (i in handlinks!!.get(juggler - 1)!!.get(handnum)!!.indices) {
            val hl = handlinks!!.get(juggler - 1)!!.get(handnum)!!.get(i)
            val hp = hl.handCurve
            if (hp != null) {
                if (Constants.DEBUG_LAYOUT) {
                    println("getHandMin(" + juggler + "," + hand + ") = " + hp.getMin(t1, t2))
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
        get() {
            for (sym in symmetries()) {
                if (sym.getType() == JMLSymmetry.TYPE_DELAY) {
                    return sym.pathPerm
                }
            }
            return null
        }

    val period: Int
        get() = getPeriod(pathPermutation!!, propassignment!!)

    val isBouncePattern: Boolean
        get() {
            for (path in 1..numberOfPaths) {
                for (pl in pathlinks!!.get(path - 1)!!) {
                    if (pl.path is BouncePath) {
                        return true
                    }
                }
            }
            return false
        }

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
                if (compareVersions(vers, JMLDefs.CURRENT_JML_VERSION) > 0) {
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
            val pd = PropDef()
            pd.readJML(current, loadingversion)
            addProp(pd)
        } else if (type.equals("setup", ignoreCase = true)) {
            val at = current.attributes
            val jugglerstring: String?
            val pathstring: String?
            val propstring: String?
            jugglerstring = at.getAttribute("jugglers")
            pathstring = at.getAttribute("paths")
            propstring = at.getAttribute("props")

            try {
                if (jugglerstring != null) {
                    this.numberOfJugglers = jugglerstring.toInt()
                } else {
                    this.numberOfJugglers = 1
                }
                this.numberOfPaths = pathstring!!.toInt()
            } catch (ex: Exception) {
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
                } catch (nfe: NumberFormatException) {
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
                current, loadingversion!!, this.numberOfJugglers, this.numberOfPaths
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
    fun writeJML(wr: Writer, write_title: Boolean, write_info: Boolean) {
        val write = PrintWriter(wr)

        for (i in JMLDefs.jmlprefix.indices) {
            write.println(JMLDefs.jmlprefix[i])
        }
        write.println("<jml version=\"" + xmlescape(version) + "\">")
        write.println("<pattern>")
        if (write_title && title != null) {
            write.println("<title>" + JMLNode.xmlescape(title!!) + "</title>")
        }
        if (write_info && (info != null || !tags.isEmpty())) {
            val tagstr = tags.joinToString(",")
            if (info != null) {
                if (tagstr.isEmpty()) {
                    write.println("<info>" + JMLNode.xmlescape(info!!) + "</info>")
                } else {
                    write.println(
                        ("<info tags=\""
                            + xmlescape(tagstr)
                            + "\">"
                            + JMLNode.xmlescape(info!!)
                            + "</info>")
                    )
                }
            } else {
                write.println("<info tags=\"" + xmlescape(tagstr) + "\"/>")
            }
        }
        if (this.basePatternNotation != null && this.basePatternConfig != null) {
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

        if (this.numberOfPaths > 0) {
            out += getPropAssignment(1)
            for (i in 2..this.numberOfPaths) {
                out += "," + getPropAssignment(i)
            }
        }
        write.println(out + "\"/>")

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

    //----------------------------------------------------------------------------
    // java.lang.Object methods
    //----------------------------------------------------------------------------
    override fun toString(): kotlin.String {
        val sw = StringWriter()
        try {
            writeJML(sw, true, true)
        } catch (ioe: IOException) {
        }

        return sw.toString()
    }

    companion object {
        val errorstrings: ResourceBundle = JugglingLab.errorstrings

        // Here `config` can be regular (like `pattern=3`) or not (like `3`)
        @JvmStatic
        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun fromBasePattern(notation: kotlin.String?, config: kotlin.String?): JMLPattern {
            val p = Pattern.newPattern(notation).fromString(config)

            val pat = p.asJMLPattern()

            // regularize the notation name and config string
            pat.basePatternNotation = p.getNotationName()
            pat.basePatternConfig = p.toString()

            return pat
        }

        fun getPeriod(perm: Permutation, propassign: IntArray): Int {
            var i: Int
            var j: Int
            var k: Int
            var cperiod: Int
            var period = 1
            var matches: Boolean
            val size = perm.size
            val notdone = BooleanArray(size)

            i = 0
            while (i < size) {
                notdone[i] = true
                i++
            }
            i = 0
            while (i < size) {
                if (notdone[i]) {
                    val cycle = perm.getCycle(i + 1)
                    j = 0
                    while (j < cycle.size) {
                        notdone[cycle[j] - 1] = false
                        cycle[j] = propassign[cycle[j] - 1]
                        j++
                    }
                    // now find the period of the current cycle
                    cperiod = 1
                    while (cperiod < cycle.size) {
                        if ((cycle.size % cperiod) == 0) {
                            matches = true
                            k = 0
                            while (k < cycle.size) {
                                if (cycle[k] != cycle[(k + cperiod) % cycle.size]) {
                                    matches = false
                                    break
                                }
                                k++
                            }
                            if (matches) {
                                break
                            }
                        }
                        cperiod++
                    }

                    period = lcm(period, cperiod)
                }
                i++
            }
            return period
        }
    }
}
