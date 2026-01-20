//
// JmlPattern.kt
//
// This represents a juggling pattern in generalized form. All patterns that
// can be animated in Juggling Lab are expressed as JmlPatterns.
//
// The `layout` value creates an instance of LaidoutPattern that physically
// lays out the pattern, ready for animation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions", "EmptyRange")

package jugglinglab.jml

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.Constants
import jugglinglab.jml.JmlNode.Companion.xmlescape
import jugglinglab.layout.LaidoutPattern
import jugglinglab.notation.Pattern
import jugglinglab.prop.Prop
import jugglinglab.util.*
import jugglinglab.util.Permutation.Companion.lcm
import kotlin.math.max

data class JmlPattern(
    val title: String? = null,
    val info: String? = null,
    val tags: List<String> = emptyList(),
    val basePatternNotation: String? = null,
    val basePatternConfig: String? = null,
    val props: List<JmlProp> = emptyList(),
    val numberOfJugglers: Int,
    val numberOfPaths: Int,
    val propAssignment: List<Int> = listOf(0),
    val symmetries: List<JmlSymmetry> = emptyList(),
    val positions: List<JmlPosition> = emptyList(),
    val events: List<JmlEvent> = emptyList()
) {
    val loopStartTime: Double = 0.0

    val loopEndTime: Double by lazy {
        symmetries.find { it.type == JmlSymmetry.TYPE_DELAY }?.delay ?: -1.0
    }

    val pathPermutation: Permutation? by lazy {
        symmetries.find { it.type == JmlSymmetry.TYPE_DELAY }?.pathPerm
    }

    // sorted list of events that:
    // (a) includes all events inside the animation loop
    // (b) includes all events in the cycles immediately before and after the
    //     animation loop
    // (c) for every path number with events in the pattern, includes at least
    //     one event before the loop start, and one event after loop end

    val allEvents: List<EventImage> by lazy {
        val result = mutableListOf<EventImage>()
        val timeWindow = pathPermutation!!.order * (loopEndTime - loopStartTime)

        val pathDone = Array(numberOfPaths) { false }
        for (image in eventSequence(reverse = true)) {
            if (image.event.t < loopStartTime - timeWindow) break
            val addEvent =
                image.event.t >= (2 * loopStartTime - loopEndTime) ||
                    image.event.transitions.isEmpty() ||
                    !image.event.transitions.all { pathDone[it.path - 1] }
            if (addEvent) {
                result.add(image)
                image.event.transitions.forEach {
                    if (it.isThrowOrCatch) {
                        pathDone[it.path - 1] = true
                    }
                }
            }
        }

        pathDone.fill(false)
        for (image in eventSequence()) {
            if (image.event.t > loopEndTime + timeWindow) break
            val addEvent =
                image.event.t < (2 * loopEndTime - loopStartTime) ||
                    image.event.transitions.isEmpty() ||
                    !image.event.transitions.all { pathDone[it.path - 1] }
            if (addEvent) {
                result.add(image)
                if (image.event.t < loopEndTime) continue
                image.event.transitions.forEach {
                    if (it.isThrowOrCatch) {
                        pathDone[it.path - 1] = true
                    }
                }
            }
        }

        result.sortedBy { it.event }.toList()
    }

    // just the events inside the animation loop

    val loopEvents: List<EventImage> by lazy {
        allEvents.filter { it.event.t in loopStartTime..<loopEndTime }
    }

    val numberOfProps: Int = props.size

    fun getProp(propnum: Int): Prop {
        return props[propnum - 1].prop
    }

    fun getPropAssignment(path: Int): Int {
        return propAssignment[path - 1]
    }

    val initialPropForPath: List<Int> by lazy {
        (1..numberOfPaths).map { getPropAssignment(it) }
    }

    // number of loop iterations needed to bring props back into the same path
    // assignment; used for e.g. creating animated GIFs

    val periodWithProps: Int by lazy {
        val perm = pathPermutation!!
        var period = 1
        val size = perm.size
        val done = BooleanArray(size)

        for (i in 0..<size) {
            if (done[i]) continue

            val cycle = perm.cycleOf(i + 1).toMutableList()
            for (j in 0..<cycle.size) {
                done[cycle[j] - 1] = true
                cycle[j] = propAssignment[cycle[j] - 1]
            }
            // find the period `cperiod` of the current cycle
            for (cperiod in 1..cycle.size) {
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
        period
    }

    val hasBasePattern: Boolean =
        (basePatternNotation != null && basePatternConfig != null)

    val isBasePatternEdited: Boolean by lazy {
        var edited = false
        if (hasBasePattern) {
            try {
                edited = (fromBasePattern(
                    basePatternNotation!!,
                    basePatternConfig!!
                ).jlHashCode != jlHashCode)
            } catch (_: JuggleException) {
            }
        }
        edited
    }

    @get:Throws(JuggleExceptionUser::class)
    val isColorable: Boolean by lazy {
        props.all { it.prop.isColorable }
    }

    val isBouncePattern: Boolean by lazy {
        events.any {
            it.transitions.any { tr ->
                tr.type == JmlTransition.TRANS_THROW &&
                    tr.throwType.equals("bounce", ignoreCase = true)
            }
        }
    }

    val jlHashCode: Int by lazy {
        val sb = StringBuilder()
        // Omit <info> tag metadata for the purposes of evaluating hash code.
        // Two patterns that differ only by metadata are treated as identical.
        writeJml(sb, writeTitle = true, writeInfo = false)
        sb.toString().hashCode()
    }

    //--------------------------------------------------------------------------
    // Validity checking
    //--------------------------------------------------------------------------

    // Check whether the pattern is valid. Report any errors as user exceptions
    // with readable messages; the pattern may be from direct user input.

    @Throws(JuggleExceptionUser::class)
    fun assertValid() {
        /*
        TODO: finish this function. Check that:
        - all paths have at least one transition
        - the time sequence of transitions makes sense for each path (i.e.,
          don't have two throws in succession)
        - holding transitions are correct
        - pperm for switchdelay symmetry is consistent with pperm for delay symmetry
        - propnums don't refer to nonexistent props (already checked by parser?)
         */
    }

    //--------------------------------------------------------------------------
    // Physical pattern layout, for animation
    //--------------------------------------------------------------------------

    @get:Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    val layout: LaidoutPattern by lazy {
        LaidoutPattern(this)
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
            EventImages(this@JmlPattern, ev)
        }
        val eventQueue = eventImages.map { ei ->
            EventImage(ei.primaryEvent, ei.primaryEvent, Permutation(numberOfPaths))
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
            val nextEventImage = if (reverse xor starting) {
                eventImages[lastIndexUsed].previous
            } else {
                eventImages[lastIndexUsed].next
            }
            eventQueue[lastIndexUsed] = nextEventImage
        }
    }

    fun prevForHandFromEvent(ev: JmlEvent): EventImage {
        return eventSequence(startTime = ev.t, reverse = true).first {
            it.event.hand == ev.hand && it.event.juggler == ev.juggler
        }
    }

    fun nextForHandFromEvent(ev: JmlEvent): EventImage {
        return eventSequence(startTime = ev.t).first {
            it.event.t > ev.t && it.event.hand == ev.hand && it.event.juggler == ev.juggler
        }
    }

    fun prevForPathFromEvent(ev: JmlEvent, path: Int): EventImage {
        return eventSequence(startTime = ev.t, reverse = true).first { image ->
            image.event.transitions.any { it.path == path }
        }
    }

    fun nextForPathFromEvent(ev: JmlEvent, path: Int): EventImage {
        return eventSequence(startTime = ev.t).first { image ->
            image.event.t > ev.t && image.event.transitions.any { it.path == path }
        }
    }

    // Return true if the event has a transition for a path to or from a
    // different juggler.

    fun hasPassingTransitionInEvent(ev: JmlEvent): Boolean {
        val transitionPaths = ev.transitions.filter { it.isThrowOrCatch }.map { it.path }
        return transitionPaths.any {
            prevForPathFromEvent(ev, it).event.juggler != ev.juggler ||
                nextForPathFromEvent(ev, it).event.juggler != ev.juggler
        }
    }

    //--------------------------------------------------------------------------
    // Input/output
    //--------------------------------------------------------------------------

    fun writeJml(wr: Appendable, writeTitle: Boolean, writeInfo: Boolean) {
        JmlDefs.jmlPrefix.forEach { wr.append(it).append('\n') }
        wr.append("<jml version=\"${xmlescape(JmlDefs.CURRENT_JML_VERSION)}\">\n")
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
                    + xmlescape(basePatternNotation.lowercase())
                    + "\">\n")
            )
            wr.append(xmlescape(basePatternConfig.replace(";", ";\n"))).append('\n')
            wr.append("</basepattern>\n")
        }
        props.forEach { it.writeJml(wr) }

        var out = "<setup jugglers=\"$numberOfJugglers\" paths=\"$numberOfPaths\" props=\""
        if (numberOfPaths > 0) {
            out += getPropAssignment(1)
            for (i in 2..numberOfPaths) {
                out += "," + getPropAssignment(i)
            }
        }
        wr.append("$out\"/>\n")

        symmetries.forEach { it.writeJml(wr) }
        positions.forEach { it.writeJml(wr) }
        events.forEach { it.writeJml(wr) }
        wr.append("</pattern>\n")
        wr.append("</jml>\n")
        JmlDefs.jmlSuffix.forEach { wr.append(it).append('\n') }
    }

    @get:Throws(JuggleExceptionInternal::class)
    val rootNode: JmlNode?
        get() {
            try {
                val parser = JmlParser()
                parser.parse(toString())
                return parser.tree
            } catch (e: Exception) {
                throw JuggleExceptionInternal(e.message ?: "", this)
            }
        }

    private val cachedToString: String by lazy {
        val sb = StringBuilder()
        writeJml(sb, writeTitle = true, writeInfo = true)
        sb.toString()
    }

    override fun toString(): String = cachedToString

    //--------------------------------------------------------------------------
    // Pattern transformations
    //--------------------------------------------------------------------------

    // Multiply all times in the pattern by a common factor `scale`.

    fun withScaledTime(scale: Double): JmlPattern {
        val newSymmetries = symmetries.map { sym ->
            if (sym.delay > 0) {
                sym.copy(delay = sym.delay * scale)
            } else {
                sym
            }
        }
        val newPositions = positions.map { it.copy(t = it.t * scale) }
        val newEvents = events.map { it.copy(t = it.t * scale) }

        val record = PatternBuilder.fromJmlPattern(this)
        record.symmetries = newSymmetries.toMutableList()
        record.events = newEvents.toMutableList()
        record.positions = newPositions.toMutableList()
        return fromPatternBuilder(record)
    }

    // Rescale the pattern in time to ensure that all throws are allotted
    // more time than their minimum required.
    //
    // `multiplier` should typically be a little over 1.

    fun withScaledTimeToFitThrows(multiplier: Double): Pair<JmlPattern, Double> {
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
        return Pair(this, 1.0)
    }

    // Flip the x-axis in the local coordinates of each juggler.
    //
    // Makes a right<-->left hand switch for all events in the pattern.
    // Parameter `flipXCoordinate` determines whether the x coordinates are
    // also inverted.

    fun withInvertedXaxis(flipXCoordinate: Boolean = true): JmlPattern {
        val newEvents = events.map {
            val newHand = if (it.hand == JmlEvent.LEFT_HAND) {
                JmlEvent.RIGHT_HAND
            } else {
                JmlEvent.LEFT_HAND
            }
            if (flipXCoordinate) {
                it.copy(x = -it.x, hand = newHand)
            } else {
                it.copy(hand = newHand)
            }
        }

        val record = PatternBuilder.fromJmlPattern(this)
        record.events = newEvents.toMutableList()
        return fromPatternBuilder(record)
    }

    // Flip the time axis to create (as nearly as possible) what the pattern
    // looks like played in reverse.

    @Throws(JuggleExceptionInternal::class)
    fun withInvertedTime(): JmlPattern {
        // For each JmlEvent:
        //     - set t = looptime - t
        //     - set all throw transitions to catch transitions
        //     - set all catch transitions to throw transitions of the correct
        //       type
        val inverseEvents = events.map { ev ->
            val newT = loopEndTime - ev.t
            val newTransitions = ev.transitions.map { tr ->
                when (tr.type) {
                    JmlTransition.TRANS_THROW -> {
                        // throws become catches
                        JmlTransition(type = JmlTransition.TRANS_CATCH, path = tr.path)
                    }

                    JmlTransition.TRANS_CATCH,
                    JmlTransition.TRANS_SOFTCATCH,
                    JmlTransition.TRANS_GRABCATCH -> {
                        // and catches become the prior throw that landed at this
                        // catch
                        val sourceEvent =
                            eventSequence(startTime = ev.t, reverse = true)
                                .first { ei ->
                                    ei.event.transitions.any { it.path == tr.path }
                                }.event
                        val sourceTransition =
                            sourceEvent.transitions.first { it.path == tr.path }
                        if (sourceTransition.type != JmlTransition.TRANS_THROW) {
                            throw JuggleExceptionInternal("invertTime() problem 1", this)
                        }
                        sourceTransition
                    }

                    else -> tr
                }
            }
            ev.copy(t = newT, transitions = newTransitions)
        }

        // for each JmlPosition:
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
            if (sym.type == JmlSymmetry.TYPE_SWITCH) {
                sym
            } else {
                sym.copy(pathPerm = sym.pathPerm.inverse)
            }
        }

        val record = PatternBuilder.fromJmlPattern(this).apply {
            symmetries = newSymmetries.toMutableList()
            positions = newPositions.toMutableList()
            events = inverseEvents.toMutableList()
            selectPrimaryEvents()
        }
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

    @Suppress("unused", "KotlinConstantConditions")
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun withExtraEventsRemovedOverWindow(twindow: Double): JmlPattern {
        val record = PatternBuilder.fromJmlPattern(this)
        var result = fromPatternBuilder(record)

        val nEventsStart = record.events.size
        val nHoldsStart = record.events.count { ev ->
            ev.transitions.all { it.type == JmlTransition.TRANS_HOLDING }
        }

        newpattern@ while (true) {
            for (ev in events) {
                val holdingOnly = ev.transitions.all { it.type == JmlTransition.TRANS_HOLDING }
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

        if (Constants.DEBUG_PATTERN_CREATION) {
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
    fun withPropColors(colorString: String): JmlPattern {
        if (!isColorable) {
            throw JuggleExceptionInternal("setPropColors(): not colorable", this)
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
                    val cycle = delayPerm.cycleOf(i + 1)
                    for (j in cycle) {
                        colorsByOrbit[j - 1] =
                            Prop.colorMixed[colorIndex % Prop.colorMixed.size]
                    }
                    ++colorIndex
                }
                colorsByOrbit.toList()
            }

            "" -> {
                val message = jlGetStringResource(Res.string.error_color_empty)
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
                                val message = jlGetStringResource(Res.string.error_color_format)
                                throw JuggleExceptionUser(message)
                            }
                        }
                    }
            }
        }

        val newProps: MutableList<JmlProp> = mutableListOf()
        val newPropAssignment: MutableList<Int> = MutableList(numberOfPaths) { 1 }

        // apply colors to get a new list of JmlProps, deduping as we go
        for (i in 0..<numberOfPaths) {
            val oldProp: JmlProp = props[getPropAssignment(i + 1) - 1]
            val propParameters = ParameterList(oldProp.mod).apply {
                removeParameter("color")
                addParameter("color", colorList[i % colorList.size])
            }
            val newProp = JmlProp(oldProp.type, propParameters.toString())

            newPropAssignment[i] = when (val idx = newProps.indexOf(newProp)) {
                -1 -> {
                    newProps.add(newProp)
                    newProps.size
                }

                else -> idx + 1  // props are indexed from 1
            }
        }

        val record = PatternBuilder.fromJmlPattern(this)
        record.props = newProps
        record.propAssignment = newPropAssignment
        return fromPatternBuilder(record)
    }

    //--------------------------------------------------------------------------
    // Constructing JmlPatterns
    //--------------------------------------------------------------------------

    companion object {
        fun fromPatternBuilder(record: PatternBuilder): JmlPattern {
            return JmlPattern(
                title = record.title,
                info = record.info,
                tags = record.tags.toList(),
                basePatternNotation = record.basePatternNotation,
                basePatternConfig = record.basePatternConfig,
                props = record.props.toList(),
                numberOfJugglers = record.numberOfJugglers,
                numberOfPaths = record.numberOfPaths,
                propAssignment = record.propAssignment.toList(),
                symmetries = record.symmetries.toList(),
                positions = record.positions.sorted(),
                events = record.events.sorted()
            )
        }

        // Create a JmlPattern by parsing a JmlNode
        //
        // Include a JML version for when we're loading a JmlPattern that's
        // part of a JmlPatternList

        @Throws(JuggleExceptionUser::class)
        fun fromJmlNode(
            current: JmlNode,
            loadingJmlVersion: String = JmlDefs.CURRENT_JML_VERSION
        ): JmlPattern {
            val record = PatternBuilder()
            record.loadingJmlVersion = loadingJmlVersion
            readJml(current, record)
            return fromPatternBuilder(record)
        }

        // Helper for JML parsing

        @Throws(JuggleExceptionUser::class)
        private fun readJml(current: JmlNode, record: PatternBuilder) {
            // process current node, then treat subnodes recursively
            when (val type = current.nodeType?.lowercase()) {
                "#root" -> {
                    // skip over
                }

                "jml" -> {
                    val vers = current.attributes.getValueOf("version") ?: return
                    if (jlCompareVersions(vers, JmlDefs.CURRENT_JML_VERSION) > 0) {
                        val message = jlGetStringResource(Res.string.error_jml_version)
                        throw JuggleExceptionUser(message)
                    }
                    record.loadingJmlVersion = vers
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
                    record.basePatternNotation =
                        Pattern.canonicalNotation(current.attributes.getValueOf("notation"))
                    record.basePatternConfig = current.nodeValue!!.trim()
                }

                "prop" -> record.props.add(JmlProp.fromJmlNode(current, record.loadingJmlVersion))
                "setup" -> {
                    val at = current.attributes
                    val jugglerstring = at.getValueOf("jugglers")
                    val pathstring: String? = at.getValueOf("paths")
                    val propstring = at.getValueOf("props")

                    try {
                        record.numberOfJugglers = jugglerstring?.toInt() ?: 1
                        record.numberOfPaths = pathstring!!.toInt()
                    } catch (_: Exception) {
                        val message = jlGetStringResource(Res.string.error_setup_tag)
                        throw JuggleExceptionUser(message)
                    }

                    record.propAssignment = if (propstring != null) {
                        val tokens = propstring.split(',')
                        if (tokens.size != record.numberOfPaths) {
                            val message = jlGetStringResource(Res.string.error_prop_assignments)
                            throw JuggleExceptionUser(message)
                        }
                        try {
                            tokens.map {
                                val propNum = it.trim().toInt()
                                if (propNum < 1 || propNum > record.props.size) {
                                    val message = jlGetStringResource(Res.string.error_prop_number)
                                    throw JuggleExceptionUser(message)
                                }
                                propNum
                            }.toMutableList()
                        } catch (_: NumberFormatException) {
                            val message = jlGetStringResource(Res.string.error_prop_format)
                            throw JuggleExceptionUser(message)
                        }
                    } else {
                        MutableList(record.numberOfPaths) { 1 }
                    }
                }

                "symmetry" -> {
                    val sym = JmlSymmetry.fromJmlNode(
                        current,
                        record.numberOfJugglers,
                        record.numberOfPaths,
                        record.loadingJmlVersion
                    )
                    record.symmetries.add(sym)
                }

                "event" -> {
                    val ev =
                        JmlEvent.fromJmlNode(
                            current,
                            record.numberOfJugglers,
                            record.numberOfPaths,
                            record.loadingJmlVersion
                        )
                    record.events.add(ev)
                    return  // stop recursion
                }

                "position" -> {
                    val pos = JmlPosition.fromJmlNode(current, record.loadingJmlVersion)
                    record.positions.add(pos)
                    return
                }

                "comment" -> {
                    // TODO: figure out a way to retain comments
                }

                else -> {
                    val message = jlGetStringResource(Res.string.error_unknown_tag, type)
                    throw JuggleExceptionUser(message)
                }
            }

            for (child in current.children) {
                readJml(child, record)
            }
        }

        // Construct from a string of XML data.
        //
        // Treat any errors as internal errors since this is not how user-inputted
        // patterns are created. TODO: is this last statement correct now?

        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun fromJmlString(
            xmlString: String
        ): JmlPattern {
            return try {
                val parser = JmlParser()
                parser.parse(xmlString)
                fromJmlNode(parser.tree!!)
            } catch (e: Exception) {
                throw JuggleExceptionInternal(e.message ?: "")
            }
        }

        // Create a JmlPattern from another notation. Here `config` can be regular
        // (like `pattern=3`) or not (like `3`).

        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun fromBasePattern(notation: String, config: String): JmlPattern {
            val p = Pattern.newPattern(notation).fromString(config)
            return p.asJmlPattern()
        }
    }
}

//------------------------------------------------------------------------------
// Helper for building JmlPatterns
//------------------------------------------------------------------------------

data class PatternBuilder(
    var title: String? = null,
    var info: String? = null,
    var tags: MutableList<String> = mutableListOf(),
    var basePatternNotation: String? = null,
    var basePatternConfig: String? = null,
    var props: MutableList<JmlProp> = mutableListOf(),
    var numberOfJugglers: Int = -1,
    var numberOfPaths: Int = -1,
    var propAssignment: MutableList<Int> = mutableListOf(),
    var symmetries: MutableList<JmlSymmetry> = mutableListOf(),
    var positions: MutableList<JmlPosition> = mutableListOf(),
    var events: MutableList<JmlEvent> = mutableListOf()
) {
    var loadingJmlVersion: String = JmlDefs.CURRENT_JML_VERSION

    fun setTitleString(str: String?) {
        val t = str?.replace(";", "")  // filter out semicolons
        title = if (!t.isNullOrBlank()) t.trim() else null

        if (basePatternNotation == null || basePatternConfig == null) return
        try {
            // set the title in base pattern
            val pl = ParameterList(basePatternConfig)
            if (pl.getParameter("pattern") == str) {
                // if title is the default then remove the title parameter
                pl.removeParameter("title")
            } else {
                pl.addParameter("title", str ?: "")
            }
            basePatternConfig = pl.toString()
        } catch (jeu: JuggleExceptionUser) {
            // can't be a user error since base pattern has already successfully
            // compiled
            throw JuggleExceptionInternal(jeu.message ?: "")
        }
    }

    fun setInfoString(t: String?) {
        info = if (t != null && !t.trim().isBlank()) t.trim() else null
    }

    // For any primary events that have an image earlier in time but t>=0,
    // promote that earliest image as the replacement primary.

    @Throws(JuggleExceptionInternal::class)
    fun selectPrimaryEvents() {
        val pat = JmlPattern.fromPatternBuilder(this)

        for ((index, ev) in events.withIndex()) {
            // find the first event in pat.loopEvents with `ev` as its primary
            val newEvent = pat.loopEvents.firstOrNull { it.primary == ev }?.event
                ?: throw JuggleExceptionInternal("Error in selectPrimaryEvents()", pat)
            events[index] = newEvent
        }
    }

    // Scan through the list of events, looking for cases where we need to add
    // or remove <holding> transitions. Update the `event` records as needed.
    //
    // Any errors are considered internal errors because we assume user input
    // has been validated prior to this.

    @Throws(JuggleExceptionInternal::class)
    fun fixHolds() {
        val holdsOnly = Array(numberOfPaths) { false }
        val patternsSeen = mutableSetOf<Int>()
        var finishing = false  // mode at the end where we only add holds
        var iteration = 1

        scanstart@ while (true) {
            val pat = JmlPattern.fromPatternBuilder(this)
            if (Constants.DEBUG_PATTERN_CREATION) {
                println("fixHolds() pass $iteration")
                println(pat)
                ++iteration
            }
            if (!patternsSeen.add(pat.hashCode()) && !finishing) {
                // prevents an infinite loop if something goes wrong
                throw JuggleExceptionInternal("error 7 in fixHolds()", pat)
            }

            val timeWindow = pat.pathPermutation!!.order * (pat.loopEndTime - pat.loopStartTime) * 2

            // record of where balls are held (juggler, hand) as we scan forward,
            // for each path: value `null` means unknown, value (0, 0) means in the air
            val holdingLocation = arrayOfNulls<Pair<Int, Int>?>(numberOfPaths)

            for (image in pat.eventSequence()) {
                if (image.event.t > pat.loopStartTime + timeWindow) {
                    // last check: were there any paths that had ONLY holds? If
                    // so then re-scan and fix holds for those paths
                    for (i in 0..<numberOfPaths) {
                        holdsOnly[i] = (holdingLocation[i] == null)
                    }
                    if (holdsOnly.any { it }) {
                        finishing = true
                        continue@scanstart
                    }
                    return  // only exit from the function
                }

                val pathsToHold = (1..numberOfPaths).filter {
                    val loc = holdingLocation[it - 1]
                    (loc != null && loc.first == image.event.juggler && loc.second == image.event.hand)
                }.toMutableList()

                for (tr in image.event.transitions) {
                    pathsToHold.remove(tr.path)
                    val loc = holdingLocation[tr.path - 1]

                    when (tr.type) {
                        JmlTransition.TRANS_CATCH,
                        JmlTransition.TRANS_SOFTCATCH,
                        JmlTransition.TRANS_GRABCATCH -> {
                            if (loc != null && (loc.first != 0 || loc.second != 0)) {
                                throw JuggleExceptionInternal("error 1 in fixHolds()", pat)
                            }
                            holdingLocation[tr.path - 1] =
                                Pair(image.event.juggler, image.event.hand)
                        }

                        JmlTransition.TRANS_THROW -> {
                            if (loc != null && (loc.first != image.event.juggler || loc.second != image.event.hand)) {
                                throw JuggleExceptionInternal("error 2 in fixHolds()", pat)
                            }
                            holdingLocation[tr.path - 1] = Pair(0, 0)
                        }

                        JmlTransition.TRANS_HOLDING -> {
                            if (loc != null && (loc.first != image.event.juggler || loc.second != image.event.hand)) {
                                // Path `tr.path` is not being held in this hand – remove transition
                                // from the primary event and then restart the scan.

                                val pathPrimary =
                                    if (image.event === image.primary) tr.path else image.pathPermFromPrimary.mapInverse(
                                        tr.path
                                    )
                                val trPrimary =
                                    image.primary.getPathTransition(
                                        pathPrimary,
                                        JmlTransition.TRANS_ANY
                                    )
                                if (trPrimary == null || trPrimary.type != JmlTransition.TRANS_HOLDING) {
                                    throw JuggleExceptionInternal("error 3 in fixHolds()", pat)
                                }

                                val newPrimary = image.primary.withoutTransition(trPrimary)
                                val index = events.indexOf(image.primary)
                                if (index == -1) {
                                    throw JuggleExceptionInternal("error 4 in fixHolds()", pat)
                                }
                                events[index] = newPrimary
                                continue@scanstart
                            }
                            if (holdsOnly[tr.path - 1]) {
                                holdingLocation[tr.path - 1] =
                                    Pair(image.event.juggler, image.event.hand)
                            }
                        }
                    }
                }

                // We know which paths we want to add holds for, but we have to do it
                // in the primary event. Use `pathPermFromPrimary` generated by
                // EventImages to map the path number back to the primary. Restart
                // the scan after each added hold to maintain consistency.

                for (path in pathsToHold) {
                    val pathPrimary =
                        if (image.event === image.primary) path else image.pathPermFromPrimary.mapInverse(
                            path
                        )
                    val trPrimary =
                        image.primary.getPathTransition(pathPrimary, JmlTransition.TRANS_ANY)
                    if (trPrimary != null) {
                        if (trPrimary.type == JmlTransition.TRANS_HOLDING) {
                            continue
                        }
                        throw JuggleExceptionInternal("error 5 in fixHolds()", pat)
                    }

                    // hold is missing from primary – add it
                    val newPrimary = image.primary.withTransition(
                        JmlTransition(
                            type = JmlTransition.TRANS_HOLDING,
                            path = pathPrimary
                        )
                    )
                    val index = events.indexOf(image.primary)
                    if (index == -1) {
                        throw JuggleExceptionInternal("error 6 in fixHolds()", pat)
                    }
                    events[index] = newPrimary
                    continue@scanstart
                }
            }
        }
    }

    companion object {
        fun fromJmlPattern(pat: JmlPattern): PatternBuilder {
            return PatternBuilder(
                title = pat.title,
                info = pat.info,
                tags = pat.tags.toMutableList(),
                basePatternNotation = pat.basePatternNotation,
                basePatternConfig = pat.basePatternConfig,
                props = pat.props.toMutableList(),
                numberOfJugglers = pat.numberOfJugglers,
                numberOfPaths = pat.numberOfPaths,
                propAssignment = pat.propAssignment.toMutableList(),
                symmetries = pat.symmetries.toMutableList(),
                positions = pat.positions.toMutableList(),
                events = pat.events.toMutableList()
            )
        }
    }
}
