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
import jugglinglab.jml.JMLEvent.Companion.addTransition
import jugglinglab.jml.JMLNode.Companion.xmlescape
import jugglinglab.notation.Pattern
import jugglinglab.path.BouncePath
import jugglinglab.prop.Prop
import jugglinglab.util.*
import jugglinglab.util.Permutation.Companion.lcm
import kotlin.math.max

data class JMLPattern(
    val numberOfJugglers: Int,
    val numberOfPaths: Int
) {
    var version: String = JMLDefs.CURRENT_JML_VERSION
    var symmetries: MutableList<JMLSymmetry> = mutableListOf()
    var eventList: JMLEvent? = null
    var positionList: JMLPosition? = null
    var props: MutableList<JMLProp> = mutableListOf()
    var propassignment: IntArray? = null
    var tags: MutableList<String> = mutableListOf()

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

    var info: String? = null
        set(t) {
            field = if (t != null && !t.trim().isBlank()) t.trim() else null
        }

    var title: String? = null
        set(title) {
            val t = title?.replace(";", "")  // filter out semicolons
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
                throw JuggleExceptionInternalWithPattern(jeu.message, this)
            }
        }

    //--------------------------------------------------------------------------
    // Methods to define the pattern
    //--------------------------------------------------------------------------

    val numberOfProps: Int
        get() = props.size

    fun getProp(propnum: Int): Prop {
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
                if (sym.type == JMLSymmetry.TYPE_DELAY) {
                    return sym.delay
                }
            }
            return -1.0
        }

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

    fun isTaggedWith(tag: String?): Boolean {
        if (tag == null) return false
        for (t in tags) {
            if (t.equals(tag, ignoreCase = true)) {
                return true
            }
        }
        return false
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
            val combineEvents =
                current!!.t == ev.t && current.hand == ev.hand && current.juggler == ev.juggler

            if (combineEvents) {
                var event = ev

                // move all the transitions from `current` to `event`, except those
                // for a path number that already has a transition in `event`.
                for (trCurrent in current.transitions) {
                    if (event.transitions.all { tr -> tr.path != trCurrent.path }) {
                        event = event.addTransition(trCurrent)
                    }
                }

                // then replace `current` with `event` in the list
                event.previous = current.previous
                event.next = current.next
                if (current.next != null) {
                    current.next!!.previous = event
                }
                if (current.previous == null) {
                    eventList = event // new head of the list
                } else {
                    current.previous!!.next = event
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
                current.hasSamePrimaryAs(ev)
            ) {
                return current
            }
            current = current.next
        }
        return null
    }

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
            val sb = StringBuilder()

            // Omit <info> tag metadata for the purposes of evaluating hash code.
            // Two patterns that differ only by metadata are treated as identical.
            writeJML(sb, writeTitle = true, writeInfo = false)

            return sb.toString().hashCode()
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
        val primaryEvents = buildList {
            var ev = eventList
            while (ev != null) {
                if (ev.isPrimary) {
                    add(ev)
                }
                ev = ev.next
            }
        }
        val eventImages = primaryEvents.map { ev ->
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
    fun streamlinePatternWithWindow(twindow: Double) {
        //layoutPattern()  // to ensure we have PathLinks

        var nEvents = 0 // for reporting stats
        var nHolds = 0
        var nRemoved = 0

        var ev = eventList

        while (ev != null) {
            val prev = ev.previousForHand
            val next = ev.nextForHand

            var holdingOnly = true
            for (tr in ev.transitions) {
                if (tr.type != JMLTransition.TRANS_HOLDING) {
                    holdingOnly = false
                    break
                }
            }
            val differentPrimaries = (prev == null || !ev.hasSamePrimaryAs(prev))
            val insideWindow = (prev != null && (ev.t - prev.t) < twindow)
            val notPassAdjacent =
                (prev != null && next != null && !prev.hasPassingTransition && !next.hasPassingTransition)

            val remove = holdingOnly && differentPrimaries && insideWindow && notPassAdjacent
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


    val pathPermutation: Permutation?
        get() = symmetries.find { it.type == JMLSymmetry.TYPE_DELAY }?.pathPerm

    val periodWithProps: Int
        get() = getPeriod(pathPermutation!!, propassignment!!)

    @get:Throws(JuggleExceptionUser::class)
    val isColorable: Boolean
        get() = props.all { it.isColorable }

    // Set the colors of props in the pattern, using the information provided
    // in `colorString`.

    @Throws(JuggleExceptionInternal::class, JuggleExceptionUser::class)
    fun setPropColors(colorString: String) {
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
                        colorsByOrbit[j - 1] = Prop.colorMixed[colorIndex % Prop.colorMixed.size]
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
        val newPropAssignments = IntArray(numberOfPaths)

        // apply colors to get a new list of JMLProps, deduping as we go
        for (i in 0..<numberOfPaths) {
            val oldProp: JMLProp = props[getPropAssignment(i + 1) - 1]
            val propParameters = ParameterList(oldProp.mod).apply {
                removeParameter("color")
                addParameter("color", colorList[i % colorList.size])
            }
            val newProp = JMLProp(oldProp.type, propParameters.toString())

            newPropAssignments[i] = when (val idx = newProps.indexOf(newProp)) {
                -1 -> {
                    newProps.add(newProp)
                    newProps.size
                }

                else -> idx + 1  // props are indexed from 1
            }
        }

        props = newProps
        setPropAssignments(newPropAssignments)
        setNeedsLayout()
    }

    fun setNeedsLayout() {
        laidout = false
    }

    val isBouncePattern: Boolean
        get() = layout.pathlinks!!.any { it.any { it1 -> it1.path is BouncePath } }

    //--------------------------------------------------------------------------
    // Reader/writer methods
    //--------------------------------------------------------------------------

    fun writeJML(wr: Appendable, writeTitle: Boolean, writeInfo: Boolean) {
        for (i in JMLDefs.jmlPrefix.indices) {
            wr.append(JMLDefs.jmlPrefix[i]).append('\n')
        }
        wr.append("<jml version=\"${xmlescape(version)}\">\n")
        wr.append("<pattern>\n")
        if (writeTitle && title != null) {
            wr.append("<title>${xmlescape(title!!)}</title>\n")
        }
        if (writeInfo && (info != null || !tags.isEmpty())) {
            val tagstr = tags.joinToString(",")
            if (info != null) {
                if (tagstr.isEmpty()) {
                    wr.append("<info>${xmlescape(info!!)}</info>\n")
                } else {
                    wr.append(
                        ("<info tags=\"${xmlescape(tagstr)}\">${xmlescape(info!!)}</info>\n")
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
        for (prop in props) {
            prop.writeJML(wr)
        }

        var out =
            ("<setup jugglers=\"$numberOfJugglers\" paths=\"$numberOfPaths\" props=\"")

        if (numberOfPaths > 0) {
            out += getPropAssignment(1)
            for (i in 2..numberOfPaths) {
                out += "," + getPropAssignment(i)
            }
        }
        wr.append("$out\"/>\n")

        for (symmetry in symmetries) {
            symmetry.writeJML(wr)
        }

        var pos = this.positionList
        while (pos != null) {
            pos.writeJML(wr)
            pos = pos.next
        }

        var ev = this.eventList
        while (ev != null) {
            if (ev.isPrimary) {
                ev.writeJML(wr)
            }
            ev = ev.next
        }
        wr.append("</pattern>\n")

        wr.append("</jml>\n")
        for (i in JMLDefs.jmlSuffix.indices) {
            wr.append(JMLDefs.jmlSuffix[i]).append('\n')
        }
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
                numberOfJugglers = record.numberOfJugglers,
                numberOfPaths = record.numberOfPaths
            )

            result.version = record.loadingversion
            record.symmetries.forEach { result.addSymmetry(it) }
            record.events.forEach { result.addEvent(it) }
            record.positions.forEach { result.addPosition(it) }
            record.props.forEach { result.addProp(it) }
            result.setPropAssignments(record.propAssignment)
            if (record.basePatternNotationString != null) {
                result.basePatternNotation = record.basePatternNotationString
                result.basePatternConfig = record.basePatternConfigString
            }
            if (record.infoString != null) {
                result.info = record.infoString
                record.tags.forEach { result.addTag(it) }
            }
            result.title = record.titleString
            return result
        }

        // Create a JMLPattern by parsing a JMLNode

        @Throws(JuggleExceptionUser::class)
        fun fromJMLNode(
            current: JMLNode,
            version: String = JMLDefs.CURRENT_JML_VERSION
        ): JMLPattern {
            val record = PatternBuilder()
            record.loadingversion = version
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
                    record.loadingversion = vers
                }

                "pattern" -> {
                    // do nothing
                }

                "title" -> record.titleString = current.nodeValue
                "info" -> {
                    record.infoString = current.nodeValue
                    current.attributes.getValueOf("tags")
                        ?.split(',')
                        ?.forEach { record.tags.add(it.trim()) }
                }

                "basepattern" -> {
                    record.basePatternNotationString =
                        Pattern.canonicalNotation(current.attributes.getValueOf("notation"))
                    record.basePatternConfigString = current.nodeValue!!.trim()
                }

                "prop" -> record.props.add(JMLProp.fromJMLNode(current, record.loadingversion))
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
                            }.toIntArray()
                        } catch (_: NumberFormatException) {
                            val message = getStringResource(Res.string.error_prop_format)
                            throw JuggleExceptionUser(message)
                        }
                    } else {
                        IntArray(record.numberOfPaths) { 1 }
                    }
                }

                "symmetry" -> {
                    val sym = JMLSymmetry.fromJMLNode(current, record.numberOfJugglers, record.numberOfPaths)
                    record.symmetries.add(sym)
                }

                "event" -> {
                    val ev =
                        JMLEvent.fromJMLNode(current, record.loadingversion, record.numberOfJugglers, record.numberOfPaths)
                    record.events.add(ev)
                    return  // stop recursion
                }

                "position" -> {
                    val pos = JMLPosition.fromJMLNode(current, record.loadingversion)
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

            val newEvents: MutableList<JMLEvent> = mutableListOf()
            var ev = eventList
            while (ev != null) {
                if (ev.isPrimary) {
                    newEvents.add(ev.copy(t = ev.t * scale))
                }
                ev = ev.next
            }

            val newPositions: MutableList<JMLPosition> = mutableListOf()
            var pos = positionList
            while (pos != null) {
                newPositions.add(pos.copy(t = pos.t * scale))
                pos = pos.next
            }

            val result = fromJMLPattern(this)
            result.symmetries = newSymmetries.toMutableList()
            result.eventList = null
            newEvents.forEach { result.addEvent(it) }
            result.positionList = null
            newPositions.forEach { result.addPosition(it) }
            return result
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
            val result = fromJMLPattern(this)
            result.eventList = null

            var ev = eventList
            while (ev != null) {
                if (ev.isPrimary) {
                    val newHand = if (ev.hand == HandLink.LEFT_HAND) {
                        HandLink.RIGHT_HAND
                    } else {
                        HandLink.LEFT_HAND
                    }
                    if (flipXCoordinate) {
                        result.addEvent(ev.copy(x = -ev.x, hand = newHand))
                    } else {
                        result.addEvent(ev.copy(hand = newHand))
                    }
                }
                ev = ev.next
            }
            return result
        }

        // Flip the time axis to create (as nearly as possible) what the pattern
        // looks like played in reverse.

        @Throws(JuggleExceptionInternal::class)
        fun JMLPattern.withInvertedTime(): JMLPattern {
            val primaryEvents = buildList {
                var ev = eventList
                while (ev != null) {
                    if (ev.isPrimary) {
                        add(ev)
                    }
                    ev = ev.next
                }
            }

            // For each JMLEvent:
            //     - set t = looptime - t
            //     - set all throw transitions to catch transitions
            //     - set all catch transitions to throw transitions of the correct
            //       type
            val inverseEvents = primaryEvents.map { ev ->
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
                                throw JuggleExceptionInternalWithPattern("invertTime() problem 1", this)
                            }
                            sourceTransition
                        }
                        else -> tr
                    }
                }
                ev.copy(t = newT, transitions = newTransitions)
            }

            val result = fromJMLPattern(this)
            result.eventList = null
            inverseEvents.forEach { result.addEvent(it) }

            // for each JMLPosition:
            //     - set t = looptime - t
            result.positionList = null
            var pos = positionList
            while (pos != null) {
                // no notion analagous to primary events, so have to keep position
                // time within [loopStartTime, loopEndTime).
                val newTime = if (pos.t != loopStartTime) {
                    loopEndTime - pos.t
                } else loopStartTime
                result.addPosition(pos.copy(t = newTime))
                pos = pos.next
            }

            // for each symmetry (besides type SWITCH):
            //     - invert pperm
            result.symmetries = symmetries.map { sym ->
                if (sym.type == JMLSymmetry.TYPE_SWITCH) {
                    sym
                } else {
                    sym.copy(pathPerm = sym.pathPerm!!.inverse)
                }
            }.toMutableList()

            return result
        }

    }
}

// Helper for building JMLPatterns

class PatternBuilder {
    var numberOfJugglers: Int = -1
    var numberOfPaths: Int = -1
    var loadingversion: String = JMLDefs.CURRENT_JML_VERSION
    var symmetries: MutableList<JMLSymmetry> = mutableListOf()
    var events: MutableList<JMLEvent> = mutableListOf()
    var positions: MutableList<JMLPosition> = mutableListOf()
    var props: MutableList<JMLProp> = mutableListOf()
    var propAssignment: IntArray = IntArray(1) { 1 }
    var tags: MutableList<String> = mutableListOf()
    var basePatternNotationString: String? = null
    var basePatternConfigString: String? = null
    var infoString: String? = null
    var titleString: String? = null
}
