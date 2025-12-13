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

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.Constants
import jugglinglab.jml.JMLNode.Companion.xmlescape
import jugglinglab.notation.Pattern
import jugglinglab.path.BouncePath
import jugglinglab.prop.Prop
import jugglinglab.util.*
import jugglinglab.util.Permutation.Companion.lcm
import kotlin.math.max

data class JMLPattern(
    val jmlVersion: String = JMLDefs.CURRENT_JML_VERSION,
    val title: String? = null,
    val info: String? = null,
    val tags: List<String> = emptyList(),
    val props: List<JMLProp> = emptyList(),
    val numberOfJugglers: Int,
    val numberOfPaths: Int,
    val propAssignment: List<Int> = listOf(0),
    val symmetries: List<JMLSymmetry> = emptyList(),
    val positions: List<JMLPosition> = emptyList(),
    val events: List<JMLEvent> = emptyList()
) {
    private var laidout: Boolean = false
    private var _layout: LaidoutPattern? = null

    val layout: LaidoutPattern
        get() {
            if (_layout == null || !laidout) {
                _layout = LaidoutPattern(this)
                laidout = true
            }
            return _layout!!
        }

    // for retaining the base pattern this pattern was created from
    var basePatternNotation: String? = null
        private set
    var basePatternConfig: String? = null
        private set
    private var basePatternHashcode: Int = 0
    private var basePatternHashcodeValid: Boolean = false

    var isValid: Boolean = true
        private set

    //--------------------------------------------------------------------------
    // Useful properties of JMLPatterns
    //--------------------------------------------------------------------------

    val pathPermutation: Permutation?
        get() = symmetries.find { it.type == JMLSymmetry.TYPE_DELAY }?.pathPerm

    val periodWithProps: Int
        get() = getPeriod(pathPermutation!!, propAssignment)

    @get:Throws(JuggleExceptionUser::class)
    val isColorable: Boolean
        get() = props.all { it.prop.isColorable }

    val numberOfProps: Int
        get() = props.size

    fun getProp(propnum: Int): Prop {
        return props[propnum - 1].prop
    }

    fun getPropAssignment(path: Int): Int {
        return propAssignment[path - 1]
    }

    val loopStartTime: Double
        get() = 0.0

    val loopEndTime: Double
        get() {
            for (sym in symmetries) {
                if (sym.type == JMLSymmetry.TYPE_DELAY) {
                    return sym.delay
                }
            }
            return -1.0
        }

    // TODO: make this not depend on `layout`
    val isBouncePattern: Boolean
        get() = layout.pathLinks.any { it.any { it1 -> it1.path is BouncePath } }

    val hashCode: Int
        get() {
            val sb = StringBuilder()
            // Omit <info> tag metadata for the purposes of evaluating hash code.
            // Two patterns that differ only by metadata are treated as identical.
            writeJML(sb, writeTitle = true, writeInfo = false)
            return sb.toString().hashCode()
        }

    //--------------------------------------------------------------------------
    // Target removal
    //--------------------------------------------------------------------------

    fun setNeedsLayout() {
        laidout = false
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
                            .hashCode
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
    // Event sequences
    //--------------------------------------------------------------------------

    // Return the (infinite) sequence of events formed by applying the pattern
    // symmetries to the primary events.
    //
    // This yields events in increasing order, the major dimension of which is
    // time. If `reverse` is true then they are yielded moving backward in time,
    // in decreasing order.
    //
    // When scanning forward we start at time `startTime` and advance forward.
    // When scanning in reverse we start at `startTime` and advance backward -
    // however we do not yield any events exactly at `startTime` when scanning
    // in reverse. In this way, scanning forward and backward generates all events
    // exactly once.

    fun eventSequence(
        startTime: Double = loopStartTime,
        reverse: Boolean = false
    ): Sequence<EventImage> = sequence {
        val eventImages = events.map { ev ->
            EventImages(this@JMLPattern, ev)
        }
        val eventQueue = eventImages.map { ei ->
            EventImage(ei.primaryEvent, ei.primaryEvent)
        }.toMutableList()

        // we may need to scan in the opposite direction for a while to get
        // to the correct time before we start yielding events
        var starting = true

        while (true) {
            val currentEventImage = if (reverse xor starting) {
                eventQueue.maxWith { a, b -> a.event.compareTo(b.event) }
            } else {
                eventQueue.minWith { a, b -> a.event.compareTo(b.event) }
            }

            if (starting) {
                if (reverse xor (currentEventImage.event.t < startTime)) {
                    starting = false
                }
            } else {
                if (reverse xor (currentEventImage.event.t >= startTime)) {
                    yield(currentEventImage)
                }
            }

            // restock the queue
            val lastIndexUsed = eventQueue.indexOf(currentEventImage)
            val nextEvent = if (reverse xor starting) {
                eventImages[lastIndexUsed].previous
            } else {
                eventImages[lastIndexUsed].next
            }
            eventQueue[lastIndexUsed] = EventImage(
                nextEvent,
                eventImages[lastIndexUsed].primaryEvent
            )
        }
    }

    data class EventImage(
        val event: JMLEvent,
        val primary: JMLEvent
    )

    fun prevForHandFromEvent(ev: JMLEvent): EventImage {
        return eventSequence(startTime = ev.t, reverse = true).first {
            it.event.hand == ev.hand && it.event.juggler == ev.juggler
        }
    }

    fun nextForHandFromEvent(ev: JMLEvent): EventImage {
        return eventSequence(startTime = ev.t).first {
            it.event.t > ev.t && it.event.hand == ev.hand && it.event.juggler == ev.juggler
        }
    }

    fun prevForPathFromEvent(ev: JMLEvent, path: Int): EventImage {
        return eventSequence(startTime = ev.t, reverse = true).first { image ->
            image.event.transitions.any { it.path == path }
        }
    }

    fun nextForPathFromEvent(ev: JMLEvent, path: Int): EventImage {
        return eventSequence(startTime = ev.t).first { image ->
            image.event.t > ev.t && image.event.transitions.any { it.path == path }
        }
    }

    // Return true if the event has a transition for a path to or from a
    // different juggler.

    fun hasPassingTransitionInEvent(ev: JMLEvent): Boolean {
        val transitionPaths = ev.transitions.filter { it.isThrowOrCatch }.map { it.path }
        return transitionPaths.any {
            prevForPathFromEvent(ev, it).event.juggler != ev.juggler ||
                nextForPathFromEvent(ev, it).event.juggler != ev.juggler
        }
    }

    // TODO: do we need this?

    fun getEventImageInLoop(ev: JMLEvent): JMLEvent? {
        var current = layout.eventList
        while (current != null) {
            if ((current.t in loopStartTime..<loopEndTime) &&
                current.juggler == ev.juggler &&
                current.hand == ev.hand &&
                current.hasSamePrimaryAs(ev)
            ) {
                return current
            }
            current = current.next
        }
        return null
    }

    //--------------------------------------------------------------------------
    // Input/output methods
    //--------------------------------------------------------------------------

    fun writeJML(wr: Appendable, writeTitle: Boolean, writeInfo: Boolean) {
        JMLDefs.jmlPrefix.forEach { wr.append(it).append('\n') }
        wr.append("<jml version=\"${xmlescape(jmlVersion)}\">\n")
        wr.append("<pattern>\n")
        if (writeTitle && title != null) {
            wr.append("<title>${xmlescape(title)}</title>\n")
        }
        if (writeInfo && (info != null || !tags.isEmpty())) {
            val tagstr = tags.joinToString(",")
            if (info != null) {
                if (tagstr.isEmpty()) {
                    wr.append("<info>${xmlescape(info)}</info>\n")
                } else {
                    wr.append(
                        ("<info tags=\"${xmlescape(tagstr)}\">${xmlescape(info)}</info>\n")
                    )
                }
            } else {
                wr.append("<info tags=\"${xmlescape(tagstr)}\"/>\n")
            }
        }
        if (basePatternNotation != null && basePatternConfig != null) {
            wr.append(
                ("<basepattern notation=\""
                    + xmlescape(basePatternNotation!!.lowercase())
                    + "\">\n")
            )
            wr.append(xmlescape(basePatternConfig!!.replace(";", ";\n"))).append('\n')
            wr.append("</basepattern>\n")
        }
        props.forEach { it.writeJML(wr) }

        var out = "<setup jugglers=\"$numberOfJugglers\" paths=\"$numberOfPaths\" props=\""
        if (numberOfPaths > 0) {
            out += getPropAssignment(1)
            for (i in 2..numberOfPaths) {
                out += "," + getPropAssignment(i)
            }
        }
        wr.append("$out\"/>\n")

        symmetries.forEach { it.writeJML(wr) }
        positions.forEach { it.writeJML(wr) }
        events.forEach { it.writeJML(wr) }
        wr.append("</pattern>\n")
        wr.append("</jml>\n")
        JMLDefs.jmlSuffix.forEach { wr.append(it).append('\n') }
    }

    @get:Throws(JuggleExceptionInternal::class)
    val rootNode: JMLNode?
        get() {
            try {
                val parser = JMLParser()
                parser.parse(toString())
                return parser.tree
            } catch (e: Exception) {
                throw JuggleExceptionInternalWithPattern(e.message, this)
            }
        }

    override fun toString(): String {
        val sb = StringBuilder()
        writeJML(sb, writeTitle = true, writeInfo = true)
        return sb.toString()
    }

    companion object {
        //----------------------------------------------------------------------
        // Constructing JMLPatterns
        //----------------------------------------------------------------------

        fun fromPatternBuilder(record: PatternBuilder): JMLPattern {
            val result = JMLPattern(
                jmlVersion = record.jmlVersion,
                title = record.title,
                info = record.info,
                tags = record.tags.toList(),
                props = record.props.toList(),
                numberOfJugglers = record.numberOfJugglers,
                numberOfPaths = record.numberOfPaths,
                propAssignment = record.propAssignment.toList(),
                symmetries = record.symmetries.toList(),
                positions = record.positions.toList(),  // TODO: sort the positions
                events = record.events.toList(),  // TODO: sort the events
            )
            if (record.basePatternNotationString != null) {
                result.basePatternNotation = record.basePatternNotationString
                result.basePatternConfig = record.basePatternConfigString
            }
            return result
        }

        // Create a JMLPattern by parsing a JMLNode

        @Throws(JuggleExceptionUser::class)
        fun fromJMLNode(
            current: JMLNode,
            version: String = JMLDefs.CURRENT_JML_VERSION
        ): JMLPattern {
            val record = PatternBuilder()
            record.jmlVersion = version
            readJML(current, record)
            return fromPatternBuilder(record)
        }

        // Helper for JML parsing

        @Throws(JuggleExceptionUser::class)
        private fun readJML(current: JMLNode, record: PatternBuilder) {
            // process current node, then treat subnodes recursively
            when (val type = current.nodeType?.lowercase()) {
                "#root" -> {
                    // skip over
                }

                "jml" -> {
                    val vers = current.attributes.getValueOf("version") ?: return
                    if (jlCompareVersions(vers, JMLDefs.CURRENT_JML_VERSION) > 0) {
                        val message = getStringResource(Res.string.error_jml_version)
                        throw JuggleExceptionUser(message)
                    }
                    record.jmlVersion = vers
                }

                "pattern" -> {
                    // do nothing
                }

                "title" -> record.setTitleString(current.nodeValue)
                "info" -> {
                    record.setInfoString(current.nodeValue)
                    current.attributes.getValueOf("tags")
                        ?.split(',')
                        ?.forEach { record.tags.add(it.trim()) }
                }

                "basepattern" -> {
                    record.basePatternNotationString =
                        Pattern.canonicalNotation(current.attributes.getValueOf("notation"))
                    record.basePatternConfigString = current.nodeValue!!.trim()
                }

                "prop" -> record.props.add(JMLProp.fromJMLNode(current, record.jmlVersion))
                "setup" -> {
                    val at = current.attributes
                    val jugglerstring = at.getValueOf("jugglers")
                    val pathstring: String? = at.getValueOf("paths")
                    val propstring = at.getValueOf("props")

                    try {
                        record.numberOfJugglers = jugglerstring?.toInt() ?: 1
                        record.numberOfPaths = pathstring!!.toInt()
                    } catch (_: Exception) {
                        val message = getStringResource(Res.string.error_setup_tag)
                        throw JuggleExceptionUser(message)
                    }

                    record.propAssignment = if (propstring != null) {
                        val tokens = propstring.split(',')
                        if (tokens.size != record.numberOfPaths) {
                            val message = getStringResource(Res.string.error_prop_assignments)
                            throw JuggleExceptionUser(message)
                        }
                        try {
                            tokens.map {
                                val propNum = it.trim().toInt()
                                if (propNum < 1 || propNum > record.props.size) {
                                    val message = getStringResource(Res.string.error_prop_number)
                                    throw JuggleExceptionUser(message)
                                }
                                propNum
                            }.toMutableList()
                        } catch (_: NumberFormatException) {
                            val message = getStringResource(Res.string.error_prop_format)
                            throw JuggleExceptionUser(message)
                        }
                    } else {
                        MutableList(record.numberOfPaths) { 1 }
                    }
                }

                "symmetry" -> {
                    val sym = JMLSymmetry.fromJMLNode(
                        current,
                        record.numberOfJugglers,
                        record.numberOfPaths
                    )
                    record.symmetries.add(sym)
                }

                "event" -> {
                    val ev =
                        JMLEvent.fromJMLNode(
                            current,
                            record.jmlVersion,
                            record.numberOfJugglers,
                            record.numberOfPaths
                        )
                    record.events.add(ev)
                    return  // stop recursion
                }

                "position" -> {
                    val pos = JMLPosition.fromJMLNode(current, record.jmlVersion)
                    record.positions.add(pos)
                    return
                }

                "comment" -> {
                    // TODO: figure out a way to retain comments
                }

                else -> {
                    val message = getStringResource(Res.string.error_unknown_tag, type)
                    throw JuggleExceptionUser(message)
                }
            }

            for (i in 0..<current.numberOfChildren) {
                readJML(current.getChildNode(i), record)
            }
        }

        // Construct from a string of XML data.
        //
        // Treat any errors as internal errors since this is not how user-inputted
        // patterns are created.

        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun fromJMLString(
            xmlString: String,
            version: String = JMLDefs.CURRENT_JML_VERSION
        ): JMLPattern {
            val result = try {
                val parser = JMLParser()
                parser.parse(xmlString)
                fromJMLNode(parser.tree!!, version)
            } catch (e: Exception) {
                throw JuggleExceptionInternal(e.message)
            }
            result.isValid = true
            return result
        }

        // TODO: Target removal

        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun fromJMLPattern(
            pat: JMLPattern,
            version: String = JMLDefs.CURRENT_JML_VERSION
        ): JMLPattern {
            return fromJMLString(pat.toString(), version)
        }

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

        //----------------------------------------------------------------------
        // Other helpers
        //----------------------------------------------------------------------

        fun getPeriod(perm: Permutation, propassign: List<Int>): Int {
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

        //----------------------------------------------------------------------
        // Some pattern transformations
        //----------------------------------------------------------------------

        // Multiply all times in the pattern by a common factor `scale`.

        fun JMLPattern.withScaledTime(scale: Double): JMLPattern {
            val newSymmetries = symmetries.map { sym ->
                if (sym.delay > 0) {
                    sym.copy(delay = sym.delay * scale)
                } else {
                    sym
                }
            }
            val newPositions = positions.map { it.copy(t = it.t * scale) }
            val newEvents = events.map { it.copy(t = it.t * scale) }

            val record = PatternBuilder.fromJMLPattern(this)
            record.symmetries = newSymmetries.toMutableList()
            record.events = newEvents.toMutableList()
            record.positions = newPositions.toMutableList()
            return fromPatternBuilder(record)
        }

        // Rescale the pattern in time to ensure that all throws are allotted
        // more time than their minimum required.
        //
        // `multiplier` should typically be a little over 1.

        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun JMLPattern.withScaledTimeToFitThrows(multiplier: Double): Pair<JMLPattern, Double> {
            var scaleFactor = 1.0

            for (path in 1..numberOfPaths) {
                for (pl in layout.pathLinks[path - 1]) {
                    val path = pl.path ?: continue
                    val duration = path.duration
                    val minDuration = path.minDuration

                    if (duration < minDuration && duration > 0) {
                        scaleFactor = max(scaleFactor, minDuration / duration)
                    }
                }
            }

            if (scaleFactor > 1) {
                scaleFactor *= multiplier  // so things aren't just barely feasible
                return Pair(withScaledTime(scaleFactor), scaleFactor)
            }
            return Pair(this, scaleFactor)
        }

        // Flip the x-axis in the local coordinates of each juggler.
        //
        // Makes a right<-->left hand switch for all events in the pattern.
        // Parameter `flipXCoordinate` determines whether the x coordinates are
        // also inverted.

        fun JMLPattern.withInvertedXAxis(flipXCoordinate: Boolean = true): JMLPattern {
            val newEvents = events.map {
                val newHand = if (it.hand == HandLink.LEFT_HAND) {
                    HandLink.RIGHT_HAND
                } else {
                    HandLink.LEFT_HAND
                }
                if (flipXCoordinate) {
                    it.copy(x = -it.x, hand = newHand)
                } else {
                    it.copy(hand = newHand)
                }
            }

            val record = PatternBuilder.fromJMLPattern(this)
            record.events = newEvents.toMutableList()
            return fromPatternBuilder(record)
        }

        // Flip the time axis to create (as nearly as possible) what the pattern
        // looks like played in reverse.

        @Throws(JuggleExceptionInternal::class)
        fun JMLPattern.withInvertedTime(): JMLPattern {
            // For each JMLEvent:
            //     - set t = looptime - t
            //     - set all throw transitions to catch transitions
            //     - set all catch transitions to throw transitions of the correct
            //       type
            val inverseEvents = events.map { ev ->
                val newT = loopEndTime - ev.t
                val newTransitions = ev.transitions.map { tr ->
                    when (tr.type) {
                        JMLTransition.TRANS_THROW -> {
                            // throws become catches
                            JMLTransition(type = JMLTransition.TRANS_CATCH, path = tr.path)
                        }

                        JMLTransition.TRANS_CATCH,
                        JMLTransition.TRANS_SOFTCATCH,
                        JMLTransition.TRANS_GRABCATCH -> {
                            // and catches become the prior throw that landed at this
                            // catch
                            val sourceEvent =
                                eventSequence(startTime = ev.t, reverse = true)
                                    .first { ei ->
                                        ei.event.transitions.any { it.path == tr.path }
                                    }.event
                            val sourceTransition =
                                sourceEvent.transitions.first { it.path == tr.path }
                            if (sourceTransition.type != JMLTransition.TRANS_THROW) {
                                throw JuggleExceptionInternalWithPattern(
                                    "invertTime() problem 1",
                                    this
                                )
                            }
                            sourceTransition
                        }

                        else -> tr
                    }
                }
                ev.copy(t = newT, transitions = newTransitions)
            }

            // for each JMLPosition:
            //     - set t = looptime - t
            val newPositions = positions.map {
                val newTime = if (it.t != loopStartTime) {
                    loopEndTime - it.t
                } else loopStartTime
                it.copy(t = newTime)
            }

            // for each symmetry (besides type SWITCH):
            //     - invert pperm
            val newSymmetries = symmetries.map { sym ->
                if (sym.type == JMLSymmetry.TYPE_SWITCH) {
                    sym
                } else {
                    sym.copy(pathPerm = sym.pathPerm!!.inverse)
                }
            }

            val record = PatternBuilder.fromJMLPattern(this)
            record.symmetries = newSymmetries.toMutableList()
            record.events = inverseEvents.toMutableList()
            record.positions = newPositions.toMutableList()
            return fromPatternBuilder(record)
        }

        // Streamline the pattern to remove excess empty and holding events.
        //
        // Scan forward in time through the pattern and remove any event for which
        // all of the following are true:
        //
        // (a) event is empty or contains only <holding> transitions
        // (b) event has a different primary event than the previous (surviving)
        //     event for that hand
        // (c) event is within `twindow` seconds of the previous (surviving) event
        //     for that hand
        // (d) event is not immediately adjacent to a throw or catch event for that
        //     hand that involves a pass to/from a different juggler

        @Suppress("unused")
        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun JMLPattern.withExtraEventsRemovedOverWindow(twindow: Double): JMLPattern {
            val record = PatternBuilder.fromJMLPattern(this)
            var result = fromPatternBuilder(record)

            val nEventsStart = record.events.size
            val nHoldsStart = record.events.count { ev ->
                ev.transitions.all { it.type == JMLTransition.TRANS_HOLDING }
            }

            newpattern@ while (true) {
                for (ev in events) {
                    val holdingOnly = ev.transitions.all { it.type == JMLTransition.TRANS_HOLDING }
                    if (!holdingOnly) {
                        continue
                    }

                    val prevForHand = result.prevForHandFromEvent(ev)
                    val nextForHand = result.nextForHandFromEvent(ev)

                    val differentPrimaries = (ev != prevForHand.primary)
                    //val differentPrimaries = !ev.hasSamePrimaryAs(prevForHand.event)
                    val insideWindow = ((ev.t - prevForHand.event.t) < twindow)
                    val notPassAdjacent = (
                        !result.hasPassingTransitionInEvent(prevForHand.event) &&
                            !result.hasPassingTransitionInEvent(nextForHand.event)
                        )

                    val remove = differentPrimaries && insideWindow && notPassAdjacent
                    if (remove) {
                        record.events.remove(ev)
                        result = fromPatternBuilder(record)
                        continue@newpattern
                    }
                }
                break
            }

            if (Constants.DEBUG_LAYOUT) {
                val nRemoved = nEventsStart - record.events.size
                println("Streamlined with time window $twindow secs:")
                println(
                    "    Removed $nRemoved of $nHoldsStart holding events ($nEventsStart events total)"
                )
            }
            return result
        }

        // Set the colors of props in the pattern, using the information provided
        // in `colorString`.

        @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
        fun JMLPattern.withPropColors(colorString: String): JMLPattern {
            if (!isColorable) {
                throw JuggleExceptionInternal("setPropColors(): not colorable")
            }

            // compile a list of colors to apply in round-robin fashion to paths
            val colorList: List<String> = when (val trimmedColorString = colorString.trim()) {
                "mixed" -> {
                    Prop.colorMixed
                }

                "orbits" -> {
                    // the path permutation on the DELAY symmetry determines orbits
                    val delayPerm: Permutation = pathPermutation!!
                    val colorsByOrbit = Array(numberOfPaths) { "" }
                    var colorIndex = 0
                    for (i in 0..<numberOfPaths) {
                        if (colorsByOrbit[i].isNotEmpty())
                            continue
                        val cycle = delayPerm.getCycle(i + 1)
                        for (j in cycle) {
                            colorsByOrbit[j - 1] =
                                Prop.colorMixed[colorIndex % Prop.colorMixed.size]
                        }
                        ++colorIndex
                    }
                    colorsByOrbit.toList()
                }

                "" -> {
                    val message = getStringResource(Res.string.error_color_empty)
                    throw JuggleExceptionUser(message)
                }

                else -> {
                    jlExpandRepeats(trimmedColorString)
                        .split('}')
                        .filter { it.isNotBlank() }
                        .map { it.replace("{", "").trim() }
                        .map { cs ->
                            val parts = cs.split(',')
                            when (parts.size) {
                                1 -> parts[0].trim()
                                3 -> "{$cs}"
                                else -> {
                                    val message = getStringResource(Res.string.error_color_format)
                                    throw JuggleExceptionUser(message)
                                }
                            }
                        }
                }
            }

            val newProps: MutableList<JMLProp> = mutableListOf()
            val newPropAssignment: MutableList<Int> = MutableList(numberOfPaths) { 1 }

            // apply colors to get a new list of JMLProps, deduping as we go
            for (i in 0..<numberOfPaths) {
                val oldProp: JMLProp = props[getPropAssignment(i + 1) - 1]
                val propParameters = ParameterList(oldProp.mod).apply {
                    removeParameter("color")
                    addParameter("color", colorList[i % colorList.size])
                }
                val newProp = JMLProp(oldProp.type, propParameters.toString())

                newPropAssignment[i] = when (val idx = newProps.indexOf(newProp)) {
                    -1 -> {
                        newProps.add(newProp)
                        newProps.size
                    }

                    else -> idx + 1  // props are indexed from 1
                }
            }

            val record = PatternBuilder.fromJMLPattern(this)
            record.props = newProps
            record.propAssignment = newPropAssignment
            return fromPatternBuilder(record)
        }

    }
}

// Helper for building JMLPatterns

data class PatternBuilder(
    var jmlVersion: String = JMLDefs.CURRENT_JML_VERSION,
    var title: String? = null,
    var info: String? = null,
    var tags: MutableList<String> = mutableListOf(),
    var basePatternNotationString: String? = null,
    var basePatternConfigString: String? = null,
    var props: MutableList<JMLProp> = mutableListOf(),
    var numberOfJugglers: Int = -1,
    var numberOfPaths: Int = -1,
    var propAssignment: MutableList<Int> = mutableListOf(),
    var symmetries: MutableList<JMLSymmetry> = mutableListOf(),
    var positions: MutableList<JMLPosition> = mutableListOf(),
    var events: MutableList<JMLEvent> = mutableListOf()
) {
    fun setTitleString(str: String?) {
        val t = str?.replace(";", "")  // filter out semicolons
        title = if (t != null && !t.isBlank()) t.trim() else null

        if (basePatternNotationString == null || basePatternConfigString == null) return
        try {
            // set the title in base pattern
            val pl = ParameterList(basePatternConfigString)
            if (pl.getParameter("pattern") == str) {
                // if title is the default then remove the title parameter
                pl.removeParameter("title")
            } else {
                pl.addParameter("title", str ?: "")
            }

            basePatternConfigString = pl.toString()
        } catch (jeu: JuggleExceptionUser) {
            // can't be a user error since base pattern has already successfully
            // compiled
            throw JuggleExceptionInternal(jeu.message)
        }
    }

    fun setInfoString(t: String?) {
        info = if (t != null && !t.trim().isBlank()) t.trim() else null
    }

    companion object {
        fun fromJMLPattern(pat: JMLPattern): PatternBuilder {
            val record = PatternBuilder()
            record.jmlVersion = pat.jmlVersion
            record.title = pat.title
            record.info = pat.info
            record.tags = pat.tags.toMutableList()
            record.basePatternNotationString = pat.basePatternNotation
            record.basePatternConfigString = pat.basePatternConfig
            record.props = pat.props.toMutableList()
            record.numberOfJugglers = pat.numberOfJugglers
            record.numberOfPaths = pat.numberOfPaths
            record.propAssignment = pat.propAssignment.toMutableList()
            record.symmetries = pat.symmetries.toMutableList()
            record.positions = pat.positions.toMutableList()
            record.events = pat.events.toMutableList()
            return record
        }
    }
}
