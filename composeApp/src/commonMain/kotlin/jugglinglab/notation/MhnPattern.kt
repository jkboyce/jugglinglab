//
// MhnPattern.kt
//
// "Multi-Hand Notation" (name due to Ed Carstens) is a general notation that
// defines a matrix of hands and discrete event times, and describes patterns as
// transitions between elements in this matrix.
//
// MHN has no standardized string (textual) representation. Hence this class is
// abstract because it lacks a fromString() method. It is however useful as a
// building block for other notations. For example we model siteswap notation
// as a type of MHN, with a parser to interpret siteswap notation into the
// internal MHN matrix representation.
//
// The main business of this class is to go from the matrix-based internal
// representation of an MHN pattern to JML. Any notation that builds on MHN can
// avoid duplicating this functionality.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions", "EmptyRange")

package jugglinglab.notation

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.Constants
import jugglinglab.jml.*
import jugglinglab.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class MhnPattern : Pattern() {
    // original config string:
    protected var config: String? = null

    // input parameters:
    protected var pattern: String? = null
    protected var bpsSet: Double = BPS_DEFAULT
    protected var dwell: Double = DWELL_DEFAULT
    protected var gravity: Double = GRAVITY_DEFAULT
    protected var propdiam: Double = PROPDIAM_DEFAULT
    protected var bouncefrac: Double = BOUNCEFRAC_DEFAULT
    protected var squeezebeats: Double = SQUEEZEBEATS_DEFAULT
    var propName: String = PROP_DEFAULT
        protected set
    protected var colors: String? = null
    protected var title: String? = null

    // hss parameters:
    protected var hss: String? = null
    protected var hold: Boolean = HOLD_DEFAULT
    protected var dwellmax: Boolean = DWELLMAX_DEFAULT
    protected var handspec: String? = null
    protected var dwellarray: DoubleArray? = null

    // internal variables:
    var numberOfJugglers: Int = 1
        protected set
    var numberOfPaths: Int = 0
        protected set
    var period: Int = 0
        protected set
    var maxOccupancy: Int = 0
        protected set
    lateinit var th: Array<Array<Array<Array<MhnThrow?>>>>

    protected var hands: MhnHands? = null
    protected var bodies: MhnBody? = null
    var maxThrow: Int = 0
        protected set
    var indexes: Int = 0
    protected lateinit var symmetries: MutableList<MhnSymmetry>
    protected var bps: Double = 0.0

    fun addSymmetry(ss: MhnSymmetry) {
        symmetries.add(ss)
    }

    // Pull out the MHN-related parameters from the given list, leaving any
    // other parameters alone.
    //
    // Note this doesn't create a valid pattern as-is, since MhnNotation doesn't
    // know how to interpret `pattern`. Subclasses like SiteswapPattern should
    // override this to add that functionality.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun fromParameters(pl: ParameterList): MhnPattern {
        config = pl.toString()
        // `pattern` is the only required parameter
        pattern = pl.removeParameter("pattern")
            ?: throw JuggleExceptionUser(jlGetStringResource(Res.string.error_no_pattern))

        var temp: String?
        if ((pl.removeParameter("bps").also { temp = it }) != null) {
            try {
                bpsSet = temp!!.toDouble()
                bps = bpsSet
            } catch (_: NumberFormatException) {
                val message = jlGetStringResource(Res.string.error_bps_value)
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("dwell").also { temp = it }) != null) {
            try {
                dwell = temp!!.toDouble()
                if (dwell <= 0 || dwell >= 2) {
                    val message = jlGetStringResource(Res.string.error_dwell_range)
                    throw JuggleExceptionUser(message)
                }
            } catch (_: NumberFormatException) {
                val message = jlGetStringResource(Res.string.error_dwell_value)
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("hands").also { temp = it }) != null) {
            hands = MhnHands(temp!!)
        }
        if ((pl.removeParameter("body").also { temp = it }) != null) {
            bodies = MhnBody(temp!!)
        }
        if ((pl.removeParameter("gravity").also { temp = it }) != null) {
            try {
                gravity = temp!!.toDouble()
            } catch (_: NumberFormatException) {
            }
        }
        if ((pl.removeParameter("propdiam").also { temp = it }) != null) {
            try {
                propdiam = temp!!.toDouble()
            } catch (_: NumberFormatException) {
            }
        }
        if ((pl.removeParameter("bouncefrac").also { temp = it }) != null) {
            try {
                bouncefrac = temp!!.toDouble()
            } catch (_: NumberFormatException) {
            }
        }
        if ((pl.removeParameter("squeezebeats").also { temp = it }) != null) {
            try {
                squeezebeats = temp!!.toDouble()
            } catch (_: NumberFormatException) {
            }
        }
        if ((pl.removeParameter("prop").also { temp = it }) != null) {
            propName = temp!!
        }
        if ((pl.removeParameter("colors").also { temp = it }) != null) {
            colors = temp
        }
        if ((pl.removeParameter("hss").also { temp = it }) != null) {
            hss = temp
        }
        if (hss != null) {
            if ((pl.removeParameter("hold").also { temp = it }) != null) {
                hold = when (temp?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> {
                        val message = jlGetStringResource(Res.string.error_hss_hold_value_error)
                        throw JuggleExceptionUser(message)
                    }
                }
            }

            if ((pl.removeParameter("dwellmax").also { temp = it }) != null) {
                dwellmax = when (temp?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> {
                        val message = jlGetStringResource(Res.string.error_hss_dwellmax_value_error)
                        throw JuggleExceptionUser(message)
                    }
                }
            }

            if ((pl.removeParameter("handspec").also { temp = it }) != null) {
                handspec = temp
            }
        }
        if ((pl.removeParameter("title").also { temp = it }) != null) {
            title = temp!!.trim()
        }
        return this
    }

    // Write out configuration parameters in a standard order.

    override fun toString(): String {
        if (config == null) return ""
        var result = ""
        try {
            val pl = ParameterList(config)
            val keys = listOf(
                "pattern",
                "bps",
                "dwell",
                "hands",
                "body",
                "gravity",
                "propdiam",
                "bouncefrac",
                "squeezebeats",
                "prop",
                "colors",
                "hss",
                "hold",
                "dwellmax",
                "handspec",
                "title",
            )
            for (key in keys) {
                val value = pl.getParameter(key)
                if (value != null) {
                    result += "$key=$value;"
                }
            }
            if (result.isNotEmpty()) {
                result = result.dropLast(1)
            }
        } catch (jeu: JuggleExceptionUser) {
            // can't be a user error since config has already been successfully read
            throw JuggleExceptionInternal(jeu.message ?: "")
        }
        return result
    }

    // Fill in details of the juggling matrix th[], to prepare for animation
    //
    // Note that th[] is assumed to be pre-populated with MhnThrows from the
    // parsing step, prior to this. This function fills in missing data elements
    // in the MhnThrows, connecting them up into a pattern. See MhnThrow.java
    // for more details.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    protected fun buildJugglingMatrix() {
        // build out the juggling matrix in steps
        //
        // this will find and raise many types of errors in the pattern
        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("-----------------------------------------------------")
            println("Building internal MhnPattern representation...\n")
            println("findPrimaryThrows()")
        }
        findPrimaryThrows()

        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("assignPaths()")
        }
        assignPaths()

        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("addThrowSources()")
        }
        addThrowSources()

        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("setCatchOrder()")
        }
        setCatchOrder()

        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("findDwellWindows()")
        }
        findDwellWindows()

        if (Constants.DEBUG_SITESWAP_PARSING) {
            val s = internalRepresentation
            println("\nInternal MhnPattern representation:\n")
            println(s)
            println("-----------------------------------------------------")
        }
    }

    // Describes an entry in the `th` array, for iteration.

    protected data class MhnThrowEntry(
        val i: Int,
        val j: Int,
        val h: Int,
        val slot: Int,
        val throwInstance: MhnThrow?
    )

    // Custom iterator (as a Sequence) that traverses the 4D `th` array in the
    // specific order required by the MHN logic: time index, then juggler, then
    // hand, then multiplex slot.

    protected fun Array<Array<Array<Array<MhnThrow?>>>>.mhnIterator():
        Sequence<MhnThrowEntry> = sequence {
        for (i in 0..<indexes) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    for (slot in 0..<maxOccupancy) {
                        yield(MhnThrowEntry(i, j, h, slot, this@mhnIterator[j][h][i][slot]))
                    }
                }
            }
        }
    }

    // Determine which throws are "primary" throws. Because of symmetries defined
    // for the pattern, some throws are shifted or reflected copies of others.
    // For each such chain of related throws, appoint one as the primary.

    @Throws(JuggleExceptionInternal::class)
    protected fun findPrimaryThrows() {
        // start by making every throw a primary throw
        th.mhnIterator().forEach { (_, _, _, _, mhnt) ->
            mhnt?.primary = mhnt // Every throw is its own primary initially
            mhnt?.source = null   // No source is known yet
        }

        var changed = true
        while (changed) {
            changed = false

            for (sym in symmetries) {
                val jperm = sym.jugglerPerm
                val delay = sym.delay

                th.mhnIterator().forEach { (i, j, h, slot, mhnt) ->
                    val imagei = i + delay
                    if (imagei >= indexes) {
                        return@forEach
                    }
                    if (mhnt == null) {
                        return@forEach
                    }

                    var imagej = jperm.map(j + 1)
                    val imageh = (if (imagej > 0) h else 1 - h)
                    imagej = abs(imagej) - 1

                    val imaget = th[imagej][imageh][imagei][slot]
                        ?: throw JuggleExceptionInternal("Problem finding primary throws")

                    val m = mhnt.primary!!
                    val im = imaget.primary!!
                    if (m === im) {
                        return@forEach
                    }

                    // we have a disagreement about which is the primary;
                    // choose one and set them equal
                    val newm = minOf(m, im)
                    imaget.primary = newm
                    mhnt.primary = newm
                    changed = true
                }
            }
        }

        if (Constants.DEBUG_SITESWAP_PARSING) {
            th.mhnIterator().forEach { (i, j, h, slot, mhnt) ->
                if (mhnt != null && mhnt.primary === mhnt) {
                    println("primary throw at j=$j,h=$h,i=$i,slot=$slot")
                }
            }
        }
    }

    // Figure out which throws are filling other throws, and assigns path
    // numbers to all throws.
    //
    // This process is complicated by the fact that we allow multiplexing.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    protected fun assignPaths() {
        th.mhnIterator().forEach { (_, _, _, _, sst) ->
            if (sst == null || sst.primary !== sst) {
                return@forEach  // skip non-primary throws
            }

            // Figure out which slot number we're filling with this
            // primary throw. We need to find a value of `targetslot`
            // that is empty for the primary throw's target, as well as
            // the targets of its images.
            var targetslot = 0
            while (targetslot < maxOccupancy) {  // find value of targetslot that works
                var itworks = true

                // loop over all throws that have sst as primary
                th.mhnIterator().forEach { (_, _, _, _, sst2) ->
                    if (sst2 == null || sst2.primary !== sst || sst2.targetIndex >= indexes) {
                        return@forEach
                    }

                    val target =
                        th[sst2.targetJuggler - 1][sst2.targetHand][sst2.targetIndex][targetslot]
                    itworks = if (target == null) {
                        false
                    } else {
                        itworks and (target.source == null) // target also unfilled?
                    }
                }

                if (itworks) {
                    break
                }
                ++targetslot
            }

            if (targetslot == maxOccupancy) {
                if (Constants.DEBUG_SITESWAP_PARSING) {
                    println(
                        ("Error: Too many objects landing on beat "
                            + (sst.targetIndex + 1)
                            + " for juggler "
                            + sst.targetJuggler
                            + ", "
                            + (if (sst.targetHand == 0) "right hand" else "left hand"))
                    )
                }
                val hand = if (sst.targetHand == 0)
                    jlGetStringResource(Res.string.error_right_hand)
                else
                    jlGetStringResource(Res.string.error_left_hand)
                val message = jlGetStringResource(
                    Res.string.error_badpattern_landings,
                    sst.targetIndex + 1,
                    sst.targetJuggler,
                    hand
                )
                throw JuggleExceptionUser(message)
            }

            // loop again over all throws that have sst as primary,
            // wiring up sources and targets using the value of `targetslot`
            th.mhnIterator().forEach { (_, _, _, _, sst2) ->
                if (sst2 == null || sst2.primary !== sst || sst2.targetIndex >= indexes) {
                    return@forEach
                }
                val target2 =
                    th[sst2.targetJuggler - 1][sst2.targetHand][sst2.targetIndex][targetslot]
                        ?: throw JuggleExceptionInternal("Got null target in assignPaths()")
                sst2.target = target2 // hook source and target together
                target2.source = sst2
            }
        }

        // assign path numbers to all of the throws
        var currentpath = 1
        th.mhnIterator().forEach { (i, j, h, slot, sst) ->
            if (sst == null) {
                return@forEach
            }
            if (sst.source != null) {
                sst.pathNum = sst.source!!.pathNum
                return@forEach
            }

            if (currentpath > numberOfPaths) {
                if (Constants.DEBUG_SITESWAP_PARSING) {
                    println("j=$j, h=$h, index=$i, slot=$slot\n")
                    println("---------------------------")

                    th.mhnIterator().forEach { (tempi, tempj, temph, tempslot, tempsst) ->
                        if (tempj != 0) {
                            return@forEach
                        }
                        println("index=$tempi, hand=$temph, slot=$tempslot:")
                        if (tempsst == null) {
                            println("   null entry")
                        } else {
                            println(
                                "   targetindex="
                                    + tempsst.targetIndex
                                    + ", targethand="
                                    + tempsst.targetHand
                                    + ", targetslot="
                                    + tempsst.targetSlot
                                    + ", pathnum="
                                    + tempsst.pathNum
                            )
                        }
                        println("---------------------------")
                    }
                }
                val message = jlGetStringResource(Res.string.error_badpattern)
                throw JuggleExceptionUser(message)
            }
            sst.pathNum = currentpath
            ++currentpath
        }
        if (currentpath <= numberOfPaths) {
            throw JuggleExceptionInternal("Problem assigning path numbers 2")
        }
    }

    // Set the `source` field for all throws that don't already have it set.
    //
    // In doing this we create new MhnThrows that are not part of the juggling
    // matrix th[] because they occur before index 0.

    @Throws(JuggleExceptionInternal::class)
    protected fun addThrowSources() {
        for (i in indexes - 1 downTo 0) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    for (slot in 0..<maxOccupancy) {
                        val sst = th[j][h][i][slot]
                        if (sst == null || sst.source != null) {
                            continue
                        }
                        if ((i + period) >= indexes) {
                            throw JuggleExceptionInternal("Could not get throw source 2")
                        }
                        val sst2 = th[j][h][i + period][slot]!!.source
                            ?: throw JuggleExceptionInternal("Could not get throw source 1")

                        val sst3 = MhnThrow(
                            juggler = sst2.juggler,
                            hand = sst2.hand,
                            index = sst2.index - period,
                            slot = sst2.slot,
                            targetJuggler = j,
                            targetHand = h,
                            targetIndex = i,
                            targetSlot = slot,
                            mod = sst2.mod
                        ).apply {
                            handsBeat = -1 // undefined
                            pathNum = sst.pathNum
                            primary = sst2.primary
                            source = null
                            target = sst
                        }
                        sst.source = sst3
                    }
                }
            }
        }
    }

    // Set the MhnThrow.catching and MhnThrow.catchnum fields.

    protected fun setCatchOrder() {
        // Figure out the correct catch order for primary throws
        for (k in 0..<indexes) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    var slotcatches = 0

                    for (slot in 0..<maxOccupancy) {
                        val sst = th[j][h][k][slot] ?: break

                        sst.catching = (sst.source!!.mod!![0] != 'H')
                        if (sst.catching) {
                            sst.catchNum = slotcatches
                            ++slotcatches
                        }
                    }

                    // Arrange the order of the catches, if more than one
                    if (slotcatches < 2) {
                        continue
                    }

                    for (slot1 in 0..<maxOccupancy) {
                        val sst1 = th[j][h][k][slot1]
                        if (sst1 == null || sst1.primary !== sst1) {
                            break // only primary throws
                        }

                        if (!sst1.catching) {
                            continue
                        }

                        for (slot2 in (slot1 + 1)..<maxOccupancy) {
                            val sst2 = th[j][h][k][slot2]
                            if (sst2 == null || sst2.primary !== sst2) {
                                break
                            }
                            if (!sst2.catching) {
                                continue
                            }
                            val switchcatches: Boolean =
                                if (sst1.catchNum < sst2.catchNum) {
                                    isCatchOrderIncorrect(sst1, sst2)
                                } else {
                                    isCatchOrderIncorrect(sst2, sst1)
                                }
                            if (switchcatches) {
                                val temp = sst1.catchNum
                                sst1.catchNum = sst2.catchNum
                                sst2.catchNum = temp
                            }
                        }
                    }
                }
            }
        }

        // Copy that over to the non-primary throws
        th.mhnIterator().forEach { (_, _, _, _, sst) ->
            if (sst == null || sst.primary === sst) {
                return@forEach  // skip primary throws
            }
            sst.catchNum = sst.primary!!.catchNum
        }
    }

    // Determine, for each throw in the juggling matrix, how many beats prior to
    // that throw was the last throw from that hand. This determines the
    // earliest we can catch (i.e., the maximum dwell time).

    protected fun findDwellWindows() {
        th.mhnIterator().forEach { (k, j, h, _, sst) ->
            if (sst == null)
                return@forEach

            // see if we made a throw on the beat immediately prior
            var index = k - 1
            if (index < 0) {
                index += period
            }

            var prevBeatThrow = false
            for (slot2 in 0..<maxOccupancy) {
                val sst2 = th[j][h][index][slot2]
                if (sst2 != null && !sst2.isZero) {
                    prevBeatThrow = true
                }
            }

            // don't bother with dwellwindow > 2 since in practice
            // we never want to dwell for more than two beats
            sst.dwellWindow = if (prevBeatThrow) 1 else 2
        }
    }

    // Dump the internal state of the pattern to a string; intended to be used
    // for debugging.

    protected val internalRepresentation: String
        get() {
            val sb = StringBuilder()
            sb.append("numjugglers = ").append(numberOfJugglers).append("\n")
            sb.append("numpaths = ").append(numberOfPaths).append("\n")
            sb.append("period = ").append(period).append("\n")
            sb.append("max_occupancy = ").append(maxOccupancy).append("\n")
            sb.append("max_throw = ").append(maxThrow).append("\n")
            sb.append("indexes = ").append(indexes).append("\n")
            sb.append("throws:\n")

            th.mhnIterator().forEach { (i, j, h, s, mhnt) ->
                sb.append("  th[")
                    .append(j).append("][")
                    .append(h).append("][")
                    .append(i).append("][")
                    .append(s).append("] = ")
                if (mhnt == null) {
                    sb.append("null\n")
                } else {
                    sb.append(mhnt).append("\n")
                }
            }
            sb.append("symmetries: (to do)\n") // not finished
            sb.append("hands: ${hands?.toString() ?: "none"}\n")
            sb.append("bodies: (to do)\n")
            return sb.toString()
        }

    // Return the pattern's starting state.
    //
    // Result is a matrix of dimension (jugglers) x 2 x (indexes), with values
    // between 0 and (max_occupancy) inclusive.

    fun getStartingState(beats: Int): Array<Array<IntArray>> {
        val result = Array(numberOfJugglers) { Array(2) { IntArray(beats) } }
        for (i in period..<indexes) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    for (s in 0..<maxOccupancy) {
                        val mhnt = th[j][h][i][s] ?: continue
                        if (mhnt.source!!.index < period) {
                            if ((i - period) < beats) {
                                result[j][h][i - period] += 1
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    //--------------------------------------------------------------------------
    // Convert from juggling matrix representation to JML
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionInternal::class)
    override fun asJmlPattern(): JmlPattern {
        if (bpsSet <= 0) {
            // signal that we should calculate bps
            bps = calcBps()
        }

        //  code to modify bps according to number of jugglers
        //  and potentially the specific pattern being juggled
        //  can be inserted here for example:
        //        if (hss != null) {
        //          bps *= getNumberOfJugglers();
        //        }

        // The following ensures a uniform catching rhythm in patterns with 1
        // throws, so long as dwell <= (2 - BEATS_THROW_CATCH_MIN)
        beats_one_throw_early = max(0.0, dwell + BEATS_AIRTIME_MIN - 1)

        val record = PatternBuilder()
        record.numberOfJugglers = numberOfJugglers
        record.numberOfPaths = numberOfPaths
        record.basePatternNotation = notationName
        record.basePatternConfig = toString()
        record.setTitleString(if (title == null) pattern else title)

        // Step 1: Add basic information about the pattern
        addPropsToJml(record)
        addSymmetriesToJml(record)

        // Step 2: Assign catch and throw times to each MhnThrow in the
        // juggling matrix
        findCatchThrowTimes()

        // Step 3: Add the primary events to the pattern
        //
        // We keep track of which hands/paths don't get any events, so we can
        // add positioning events later. Also keep track of which events need a
        // position calculation later on.
        val handtouched = Array(numberOfJugglers) { BooleanArray(2) }
        val pathtouched = BooleanArray(numberOfPaths)
        val calcpos: MutableMap<JmlEvent, Boolean> = mutableMapOf()
        addPrimaryEventsToJml(record, handtouched, pathtouched, calcpos)
        if (Constants.DEBUG_PATTERN_CREATION) {
            println("After step 3:")
            println(JmlPattern.fromPatternBuilder(record))
        }

        // Step 4: Define a body position for this juggler and beat, if specified
        addJugglerPositionsToJml(record)
        if (Constants.DEBUG_PATTERN_CREATION) {
            println("After step 4:")
            println(JmlPattern.fromPatternBuilder(record))
        }

        // Step 5: Add simple positioning events for hands that got no events
        addEventsForUntouchedHandsToJml(record, handtouched, calcpos)
        if (Constants.DEBUG_PATTERN_CREATION) {
            println("After step 5:")
            println(JmlPattern.fromPatternBuilder(record))
        }

        // Step 6: Add <holding> transitions for paths that got no events
        addEventsForUntouchedPathsToJml(record, pathtouched, calcpos)
        if (Constants.DEBUG_PATTERN_CREATION) {
            println("After step 6:")
            println(JmlPattern.fromPatternBuilder(record))
        }

        // save a snapshot in case we need to redo steps 7-9 below
        val recordSnapshot = record.copy(
            events = record.events.toMutableList()
        )

        // Step 7: Add events where there are long gaps for a hand
        if (hands == null) {
            addEventsForGapsToJml(record, calcpos)
            if (Constants.DEBUG_PATTERN_CREATION) {
                println("After step 7:")
                println(JmlPattern.fromPatternBuilder(record))
            }
        }

        // Step 8: Specify positions for events that don't have them defined yet
        addLocationsForIncompleteEventsToJml(record, calcpos)
        if (Constants.DEBUG_PATTERN_CREATION) {
            println("After step 8:")
            println(JmlPattern.fromPatternBuilder(record))
        }

        // Step 9: Add additional <holding> transitions where needed (i.e., a
        // ball is in a hand)
        record.fixHolds()
        if (Constants.DEBUG_PATTERN_CREATION) {
            println("After step 9:")
            println(JmlPattern.fromPatternBuilder(record))
        }

        // Step 10: Select the primary events, and build the pattern.
        record.selectPrimaryEvents()
        var result = JmlPattern.fromPatternBuilder(record)
        if (Constants.DEBUG_PATTERN_CREATION) {
            println("After step 10:")
            println(result)
        }

        // Step 11: We can now construct a JmlPattern that can be laid out.
        // If `bps` wasn't manually set then we do a final check: Do a layout and
        // confirm that each throw in the pattern has enough time to satisfy its
        // minimum duration requirement. If not then rescale time (bps) to make
        // everything feasible.
        if (bpsSet <= 0) {
            val (newResult, scaleFactor) = result.withScaledTimeToFitThrows(1.01)

            if (scaleFactor > 1.0) {
                if (Constants.DEBUG_PATTERN_CREATION) {
                    println("Rescaled time; scale factor = $scaleFactor")
                    println("After scaleTimeToFitThrows():")
                    println(newResult)
                }
                bps /= scaleFactor
                if (hands == null) {
                    // go back to before step 7, when we added extra events
                    // - finish the pattern without the extra events
                    // - re-run the scaling operation
                    // - get a PatternBuilder from the rescaled pattern
                    // - redo steps 7-10 to get the final pattern
                    addLocationsForIncompleteEventsToJml(recordSnapshot, calcpos)
                    if (Constants.DEBUG_PATTERN_CREATION) {
                        println("After completion step 8:")
                        println(JmlPattern.fromPatternBuilder(recordSnapshot))
                    }
                    recordSnapshot.fixHolds()
                    if (Constants.DEBUG_PATTERN_CREATION) {
                        println("After completion step 9:")
                        println(JmlPattern.fromPatternBuilder(recordSnapshot))
                    }
                    val (newResult, _) =
                        JmlPattern.fromPatternBuilder(recordSnapshot)
                            .withScaledTimeToFitThrows(1.01)
                    if (Constants.DEBUG_PATTERN_CREATION) {
                        println("After second scaleTimeToFitThrows():")
                        println(newResult)
                    }
                    val newRecord = PatternBuilder.fromJmlPattern(newResult)
                    if (Constants.DEBUG_PATTERN_CREATION) {
                        println("After PatternBuilder.fromJmlPattern():")
                        println(JmlPattern.fromPatternBuilder(newRecord))
                    }

                    // redo steps 7-10
                    addEventsForGapsToJml(newRecord, calcpos)
                    if (Constants.DEBUG_PATTERN_CREATION) {
                        println("After redone step 7:")
                        println(JmlPattern.fromPatternBuilder(newRecord))
                    }
                    addLocationsForIncompleteEventsToJml(newRecord, calcpos)
                    if (Constants.DEBUG_PATTERN_CREATION) {
                        println("After redone step 8:")
                        println(JmlPattern.fromPatternBuilder(newRecord))
                    }
                    newRecord.fixHolds()
                    if (Constants.DEBUG_PATTERN_CREATION) {
                        println("After redone step 9:")
                        println(JmlPattern.fromPatternBuilder(newRecord))
                    }
                    newRecord.selectPrimaryEvents()
                    result = JmlPattern.fromPatternBuilder(newRecord)
                    if (Constants.DEBUG_PATTERN_CREATION) {
                        println("After redone step 10:")
                        println(result)
                    }
                } else {
                    // if `hands` was set then accept the rescaled pattern as-is
                    result = newResult
                }
            }
        }

        if (colors != null) {
            result = result.withPropColors(colors!!)
        }
        if (Constants.DEBUG_PATTERN_CREATION) {
            println("Final result from MhnPattern.asJmlPattern():")
            println(result)
        }

        try {
            result.assertValid()
        } catch (e: JuggleExceptionUser) {
            // treat as internal error since user input errors should have
            // been caught upstream
            throw JuggleExceptionInternal(
                "Error in asJmlPattern(): ${e.message}", result
            )
        } catch (jei: JuggleExceptionInternal) {
            jei.pattern = result
            throw jei
        }

        return result
    }

    //--------------------------------------------------------------------------
    // Helpers for converting to JML
    //--------------------------------------------------------------------------

    protected fun calcBps(): Double {
        // Calculate a default beats per second (bps) for the pattern
        var result = 0.0
        var numberaveraged = 0

        th.mhnIterator().forEach { (k, _, _, _, sst) ->
            if (sst != null) {
                val throwval = sst.targetIndex - k
                if (throwval > 2) {
                    result += throwspersec[min(throwval, 9)]
                    ++numberaveraged
                }
            }
        }
        if (numberaveraged > 0) {
            result /= numberaveraged.toDouble()
        } else {
            result = 2.0
        }
        return result
    }

    protected fun addPropsToJml(rec: PatternBuilder) {
        val mod = if (propdiam != PROPDIAM_DEFAULT) "diam=$propdiam" else null
        rec.props.add(JmlProp(propName, mod))
        val pa = MutableList(numberOfPaths) { 1 }
        rec.propAssignment = pa
    }

    @Throws(JuggleExceptionUser::class)
    protected fun addSymmetriesToJml(rec: PatternBuilder) {
        val balls = numberOfPaths

        for (sss in symmetries) {
            val symtype: Int
            val pathmap = IntArray(balls + 1)

            when (sss.type) {
                MhnSymmetry.TYPE_DELAY -> {
                    symtype = JmlSymmetry.TYPE_DELAY
                    for (k in 0..<(indexes - sss.delay)) {
                        for (j in 0..<numberOfJugglers) {
                            for (h in 0..1) {
                                for (slot in 0..<maxOccupancy) {
                                    val sst: MhnThrow? = th[j][h][k][slot]
                                    if (sst != null && sst.pathNum != -1) {
                                        val sst2 = th[j][h][k + sss.delay][slot]
                                            ?: throw JuggleExceptionUser(
                                                jlGetStringResource(Res.string.error_badpattern_paths)
                                            )
                                        if ((sst.pathNum == 0) || (sst2.pathNum == 0)) {
                                            throw JuggleExceptionUser(
                                                jlGetStringResource(Res.string.error_badpattern_paths)
                                            )
                                        }
                                        if (pathmap[sst.pathNum] == 0) {
                                            pathmap[sst.pathNum] = sst2.pathNum
                                        } else if (pathmap[sst.pathNum] != sst2.pathNum) {
                                            throw JuggleExceptionUser(
                                                jlGetStringResource(Res.string.error_badpattern_delay)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                MhnSymmetry.TYPE_SWITCH -> symtype = JmlSymmetry.TYPE_SWITCH
                MhnSymmetry.TYPE_SWITCHDELAY -> {
                    symtype = JmlSymmetry.TYPE_SWITCHDELAY

                    val jugperm = sss.jugglerPerm
                    for (k in 0..<(indexes - sss.delay)) {
                        for (j in 0..<numberOfJugglers) {
                            for (h in 0..1) {
                                var slot = 0
                                while (slot < maxOccupancy) {
                                    val sst: MhnThrow? = th[j][h][k][slot]
                                    if (sst != null && sst.pathNum != -1) {
                                        val map = jugperm.map(j + 1)
                                        val newj = abs(map) - 1
                                        val newh = (if (map > 0) h else 1 - h)
                                        val sst2 = th[newj][newh][k + sss.delay][slot]
                                            ?: throw JuggleExceptionUser(
                                                jlGetStringResource(Res.string.error_badpattern_paths)
                                            )
                                        if (sst.pathNum == 0 || sst2.pathNum == 0) {
                                            throw JuggleExceptionUser(
                                                jlGetStringResource(Res.string.error_badpattern_paths)
                                            )
                                        }
                                        if (pathmap[sst.pathNum] == 0) {
                                            pathmap[sst.pathNum] = sst2.pathNum
                                        } else if (pathmap[sst.pathNum] != sst2.pathNum) {
                                            throw JuggleExceptionUser(
                                                jlGetStringResource(Res.string.error_badpattern_switchdelay)
                                            )
                                        }
                                    }
                                    ++slot
                                }
                            }
                        }
                    }
                }

                else -> throw JuggleExceptionUser(
                    jlGetStringResource(Res.string.error_unknown_symmetry)
                )
            }
            // convert path mapping to a string
            var pathmapstring = ""
            for (j in 1..<balls) {
                pathmapstring += pathmap[j].toString() + ","
            }
            if (balls > 0) {
                pathmapstring += pathmap[balls]
            }

            val sym = JmlSymmetry(
                type = symtype,
                numberOfJugglers = sss.numberOfJugglers,
                numberOfPaths = numberOfPaths,
                jugglerPerm = sss.jugglerPerm,
                pathPerm = Permutation(numberOfPaths, pathmapstring, false),
                delay = sss.delay.toDouble() / bps
            )

            rec.symmetries.add(sym)
        }
    }

    // Assign throw and catch times to each element in the juggling matrix.
    //
    // The catch time here refers to that prop's catch immediately prior to the
    // throw represented by MhnThrow.

    fun findCatchThrowTimes() {
        for (k in 0..<indexes) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    val sst = th[j][h][k][0] ?: continue

                    // Are we throwing a 1 on this beat?
                    var onethrown = false
                    for (slot in 0..<maxOccupancy) {
                        val sst2 = th[j][h][k][slot]
                        if (sst2 != null && sst2.isThrownOne) {
                            onethrown = true
                        }
                    }

                    // Set throw times
                    for (slot in 0..<maxOccupancy) {
                        val sst2 = th[j][h][k][slot] ?: break

                        if (onethrown) sst2.throwTime =
                            (k.toDouble() - beats_one_throw_early) / bps
                        else sst2.throwTime = k.toDouble() / bps

                        if (hss != null) {
                            // if (onethrown)
                            //    throwtime = ((double)k - 0.25*(double)dwellarray[k]) / bps;
                            // else
                            sst2.throwTime = k.toDouble() / bps
                        }
                    }

                    var numCatches = 0
                    var onecaught = false

                    for (slot in 0..<maxOccupancy) {
                        val sst2 = th[j][h][k][slot] ?: break

                        if (sst2.catching) {
                            numCatches++

                            if (sst2.source!!.isThrownOne) {
                                onecaught = true
                            }
                        }
                    }

                    // Figure out when the object we just threw was caught, prior
                    // to the throw.
                    //
                    // We call this `firstcatchtime` because if there are multiple
                    // catches spread out over time on this beat (i.e., a squeeze
                    // catch), this is the earliest one.

                    // Did the previous throw out of this same hand contain
                    // a 1 throw?
                    var prevOnethrown = false
                    var tempindex = k - sst.dwellWindow
                    while (tempindex < 0) {
                        tempindex += period
                    }

                    for (slot in 0..<maxOccupancy) {
                        val sst2 = th[j][h][tempindex][slot]
                        if (sst2 != null && sst2.isThrownOne) {
                            prevOnethrown = true
                        }
                    }

                    // Start by giving the requested number of dwell beats
                    //
                    // Note we assume here all throws are on-beat, so we get
                    // a uniform catching rhythm. Thus for 1 throws when
                    // BEATS_ONE_THROW_EARLY > 0, the assigned dwell before the
                    // 1 will actually be (dwell - BEATS_ONE_THROW_EARLY) beats.
                    // Note that in all cases BEATS_ONE_THROW_EARLY < dwell.
                    var firstcatchtime = (k.toDouble() - dwell) / bps

                    // Constraint #1: Don't allow catch to move before the
                    // previous throw from the same hand (plus margin)
                    firstcatchtime = max(
                        firstcatchtime,
                        ((k - sst.dwellWindow).toDouble() - (if (prevOnethrown) beats_one_throw_early else 0.0)
                            + BEATS_THROW_CATCH_MIN) / bps
                    )

                    // Constraint #2: If catching a 1 throw, allocate enough air
                    // time to it
                    if (onecaught) {
                        firstcatchtime = max(
                            firstcatchtime,
                            ((k - 1).toDouble() - beats_one_throw_early + BEATS_AIRTIME_MIN) / bps
                        )
                    }

                    // Constraint #3: Ensure we have enough time between catch
                    // and subsequent throw
                    firstcatchtime =
                        min(firstcatchtime, sst.throwTime - BEATS_CATCH_THROW_MIN / bps)

                    // Set catch times
                    for (slot in 0..<maxOccupancy) {
                        val sst2 = th[j][h][k][slot] ?: break
                        var catchtime = firstcatchtime

                        if (numCatches > 1) {
                            catchtime +=
                                (sst2.catchNum.toDouble() / (numCatches - 1).toDouble()) * (squeezebeats / bps)
                        }

                        if (hss != null) {
                            // if getPeriod() > size of dwellarray due to repeats
                            // to account for hand/body positions, then reuse
                            // dwellarray timings from prior array elements
                            val newk = k % dwellarray!!.size

                            catchtime = (k.toDouble() - dwellarray!![newk]) / bps

                            if (numCatches > 1) {
                                catchtime +=
                                    sst2.catchNum.toDouble() / (numCatches - 1).toDouble() * squeezebeats / bps
                            }
                        }

                        catchtime = min(catchtime, sst.throwTime - BEATS_CATCH_THROW_MIN / bps)

                        sst2.catchTime = catchtime
                        if (Constants.DEBUG_PATTERN_CREATION) {
                            println("catch time for $sst2 = $catchtime")
                        }
                    }
                }
            }
        }
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    protected fun addPrimaryEventsToJml(
        rec: PatternBuilder,
        handtouched: Array<BooleanArray>,
        pathtouched: BooleanArray,
        calcpos: MutableMap<JmlEvent, Boolean>
    ) {
        for (j in 0..<numberOfJugglers) {
            for (h in 0..1) {
                handtouched[j][h] = false
            }
        }
        for (j in 0..<numberOfPaths) {
            pathtouched[j] = false
        }

        for (k in 0..<period) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    val sst = th[j][h][k][0]
                    if (sst == null || sst.primary !== sst) {
                        continue
                    }

                    // Event that we will be constructing:
                    var ev = JmlEvent()

                    // Step 3a: Add transitions to the on-beat event (throw or holding transitions)
                    var throwxsum = 0.0
                    var numThrows = 0

                    for (slot in 0..<maxOccupancy) {
                        val sst2 = th[j][h][k][slot] ?: break
                        val type: String?
                        var mod: String?

                        when (sst2.mod!![0]) {
                            'B' -> {
                                type = "bounce"
                                mod = null
                                if (sst2.mod.contains("F")) {
                                    mod = "forced=true"
                                }
                                if (sst2.mod.contains("H")) {
                                    mod = if (mod == null) {
                                        "hyper=true"
                                    } else {
                                        "$mod;hyper=true"
                                    }
                                }
                                var bounces = 1
                                var i = 1
                                while (i < sst2.mod.length) {
                                    if (sst2.mod[i] == 'B') {
                                        ++bounces
                                    }
                                    i++
                                }
                                if (bounces > 1) {
                                    mod = if (mod == null) {
                                        "bounces=$bounces"
                                    } else {
                                        "$mod;bounces=$bounces"
                                    }
                                }
                                if (bouncefrac != BOUNCEFRAC_DEFAULT) {
                                    mod = if (mod == null) {
                                        "bouncefrac=$bouncefrac"
                                    } else {
                                        "$mod;bouncefrac=$bouncefrac"
                                    }
                                }
                                if (gravity != GRAVITY_DEFAULT) {
                                    mod = if (mod == null) {
                                        "g=$gravity"
                                    } else {
                                        "$mod;g=$gravity"
                                    }
                                }
                            }

                            'F' -> {
                                type = "bounce"
                                mod = "forced=true"
                                if (bouncefrac != BOUNCEFRAC_DEFAULT) {
                                    mod += ";bouncefrac=$bouncefrac"
                                }
                                if (gravity != GRAVITY_DEFAULT) {
                                    mod = "$mod;g=$gravity"
                                }
                            }

                            'H' -> {
                                type = "hold"
                                mod = null
                            }

                            'T' -> {
                                type = "toss"
                                mod = null
                                if (gravity != GRAVITY_DEFAULT) {
                                    mod = "g=$gravity"
                                }
                            }

                            else -> {
                                type = "toss"
                                mod = null
                                if (gravity != GRAVITY_DEFAULT) {
                                    mod = "g=$gravity"
                                }
                            }
                        }

                        if (sst2.mod[0] != 'H') {
                            if (sst2.isZero) {
                                val message = jlGetStringResource(
                                    Res.string.error_modifier_on_0,
                                    sst2.mod,
                                    k + 1
                                )
                                throw JuggleExceptionUser(message)
                            }
                            ev = ev.withTransition(
                                JmlTransition(
                                    type = JmlTransition.TRANS_THROW,
                                    path = sst2.pathNum,
                                    throwType = type,
                                    throwMod = mod
                                )
                            )

                            val throwval = sst2.targetIndex - k

                            throwxsum += if (sst2.targetHand == h) {
                                if (throwval > 8) samethrowx[8] else samethrowx[throwval]
                            } else {
                                if (throwval > 8) crossingthrowx[8] else crossingthrowx[throwval]
                            }
                            ++numThrows
                        } else if (hands != null) {
                            if (!sst2.isZero) {
                                // add holding transition if there's a ball in
                                // hand and "hands" is specified
                                ev = ev.withTransition(
                                    JmlTransition(
                                        type = JmlTransition.TRANS_HOLDING,
                                        path = sst2.pathNum,
                                        throwType = type,
                                        throwMod = mod
                                    )
                                )
                                pathtouched[sst2.pathNum - 1] = true
                            }
                        }
                    }

                    // Step 3b: Finish off the on-beat event based on the transitions we've added
                    //
                    // don't add on-beat event if there are no throws -- unless a hand layout is
                    // specified
                    if (hands != null || numThrows != 0) {
                        // set the event position
                        var newLocalCoordinate = Coordinate()
                        var newCalcpos: Boolean

                        if (hands == null) {
                            if (numThrows > 0) {
                                var throwxav = throwxsum / numThrows.toDouble()
                                if (h == LEFT_HAND) {
                                    throwxav = -throwxav
                                }
                                newLocalCoordinate = Coordinate(throwxav, 0.0, 0.0)
                                newCalcpos = false
                            } else {
                                // mark event to calculate coordinate later
                                newCalcpos = true
                            }
                        } else {
                            val c = hands!!.getCoordinate(sst.juggler, sst.handsBeat, 0)!!
                            if (h == LEFT_HAND) {
                                c.x = -c.x
                            }
                            newLocalCoordinate = c
                            newCalcpos = false
                        }

                        ev = ev.copy(
                            x = newLocalCoordinate.x,
                            y = newLocalCoordinate.y,
                            z = newLocalCoordinate.z,
                            t = sst.throwTime,
                            juggler = j + 1,
                            hand = if (h == RIGHT_HAND) JmlEvent.RIGHT_HAND else JmlEvent.LEFT_HAND
                        )
                        calcpos[ev] = newCalcpos
                        rec.events.add(ev)
                        if (Constants.DEBUG_PATTERN_CREATION) {
                            println("aPETJ 1: added event $ev")
                        }

                        // record which hands are touched by this event, for later reference
                        th.mhnIterator().forEach { (_, j2, h2, _, sst2) ->
                            if (sst2 != null && sst2.primary === sst) {
                                handtouched[j2][h2] = true
                            }
                        }
                    }

                    // Step 3c: Add any catching (or holding) events immediately prior to the
                    // on-beat event added in Step 3b above:
                    var catchxsum = 0.0
                    var numCatches = 0

                    for (slot in 0..<maxOccupancy) {
                        val sst2 = th[j][h][k][slot]
                        if (sst2 == null || !sst2.catching) {
                            continue
                        }

                        val catchpath = sst2.pathNum
                        val catchval = k - sst2.source!!.index
                        pathtouched[catchpath - 1] = true
                        catchxsum += (if (catchval > 8) catchx[8] else catchx[catchval])
                        ++numCatches
                    }

                    // Don't put an event at the catch time if there are no catches on
                    // this beat -- unless a hand layout is specified
                    if (hands == null && numCatches == 0) {
                        continue
                    }

                    // Now add the catch event(s). Two cases to consider: (1) all catches
                    // happen at the same event, or (2) multiple catch events are made in
                    // succession.

                    // keep track of the time of last catch, for Step 3d below
                    var lastcatchtime = 0.0

                    if (squeezebeats == 0.0 || numCatches < 2) {
                        // Case 1: everything happens at a single event
                        var newLocalCoordinate = Coordinate()
                        var newCalcpos: Boolean

                        // first set the event position
                        if (hands == null) {
                            if (numCatches > 0) {
                                val cx = catchxsum / numCatches.toDouble()
                                newLocalCoordinate =
                                    Coordinate(if (h == RIGHT_HAND) cx else -cx, 0.0, 0.0)
                                newCalcpos = false
                            } else {
                                // mark event to calculate coordinate later
                                newCalcpos = true
                            }
                        } else {
                            var pos = sst.handsBeat - 2
                            while (pos < 0) {
                                pos += hands!!.getPeriod(sst.juggler)
                            }
                            val index = hands!!.getCatchIndex(sst.juggler, pos)
                            val c = hands!!.getCoordinate(sst.juggler, pos, index)!!
                            if (h == LEFT_HAND) {
                                c.x = -c.x
                            }
                            newLocalCoordinate = c
                            newCalcpos = false
                        }

                        lastcatchtime = sst.catchTime

                        var ev = JmlEvent(
                            x = newLocalCoordinate.x,
                            y = newLocalCoordinate.y,
                            z = newLocalCoordinate.z,
                            t = sst.catchTime,
                            juggler = j + 1,
                            hand = if (h == RIGHT_HAND) JmlEvent.RIGHT_HAND else JmlEvent.LEFT_HAND
                        )

                        // add all the transitions
                        for (slot in 0..<maxOccupancy) {
                            val sst2 = th[j][h][k][slot] ?: break
                            if (sst2.catching) {
                                ev = ev.withTransition(
                                    JmlTransition(
                                        type = JmlTransition.TRANS_CATCH,
                                        path = sst2.pathNum
                                    )
                                )
                            } else if (hands != null) {
                                if (sst2.pathNum != -1) {  // -1 signals a 0 throw
                                    // add holding transition if there's a ball in
                                    // hand and "hands" is specified
                                    ev = ev.withTransition(
                                        JmlTransition(
                                            type = JmlTransition.TRANS_HOLDING,
                                            path = sst2.pathNum
                                        )
                                    )
                                    pathtouched[sst2.pathNum - 1] = true
                                }
                            }
                        }

                        calcpos[ev] = newCalcpos
                        rec.events.add(ev)
                        if (Constants.DEBUG_PATTERN_CREATION) {
                            println("aPETJ 2: added event $ev")
                        }
                    } else {
                        // Case 2: separate event for each catch; we know that numcatches > 1 here
                        for (slot in 0..<maxOccupancy) {
                            val sst2 = th[j][h][k][slot]
                            if (sst2 == null || !sst2.catching) {
                                continue
                            }

                            // we only need to add catch transitions here; holding transitions will be
                            // added in Step 9
                            var newLocalCoordinate: Coordinate

                            // first set the event position
                            if (hands == null) {
                                val cx = catchxsum / numCatches.toDouble()
                                newLocalCoordinate =
                                    Coordinate((if (h == RIGHT_HAND) cx else -cx), 0.0, 0.0)
                            } else {
                                var pos = sst.handsBeat - 2
                                while (pos < 0) {
                                    pos += hands!!.getPeriod(sst.juggler)
                                }
                                val index = hands!!.getCatchIndex(sst.juggler, pos)
                                val c = hands!!.getCoordinate(sst.juggler, pos, index)!!
                                if (h == LEFT_HAND) {
                                    c.x = -c.x
                                }
                                newLocalCoordinate = c
                            }

                            if (sst2.catchNum == (numCatches - 1)) {
                                lastcatchtime = sst2.catchTime
                            }
                            val ev = JmlEvent(
                                x = newLocalCoordinate.x,
                                y = newLocalCoordinate.y,
                                z = newLocalCoordinate.z,
                                t = sst2.catchTime,
                                juggler = j + 1,
                                hand = if (h == RIGHT_HAND) JmlEvent.RIGHT_HAND else JmlEvent.LEFT_HAND,
                                transitions = listOf(
                                    JmlTransition(
                                        type = JmlTransition.TRANS_CATCH,
                                        path = sst2.pathNum
                                    )
                                )
                            )
                            calcpos[ev] = false
                            rec.events.add(ev)
                            if (Constants.DEBUG_PATTERN_CREATION) {
                                println("aPETJ 3: added event $ev")
                            }
                        }
                    }

                    // Step 3d: If hand positions are specified, add any extra hand positioning
                    // events after the catch above, until the next catch for the hand
                    if (hands == null) {
                        continue
                    }

                    // add other events between the previous catch and the current throw
                    var pos = sst.handsBeat - 2
                    while (pos < 0) {
                        pos += hands!!.getPeriod(sst.juggler)
                    }
                    val catchindex = hands!!.getCatchIndex(sst.juggler, pos)
                    var numcoords =
                        hands!!.getNumberOfCoordinates(sst.juggler, pos) - catchindex

                    for (di in 1..<numcoords) {
                        val c = hands!!.getCoordinate(sst.juggler, pos, catchindex + di) ?: continue
                        if (h == LEFT_HAND) {
                            c.x = -c.x
                        }
                        val ev = JmlEvent(
                            x = c.x,
                            y = c.y,
                            z = c.z,
                            t = lastcatchtime + di.toDouble() *
                                (sst.throwTime - lastcatchtime) / numcoords,
                            juggler = sst.juggler,
                            hand = if (h == RIGHT_HAND) JmlEvent.RIGHT_HAND else JmlEvent.LEFT_HAND
                        )
                        calcpos[ev] = false
                        rec.events.add(ev)
                        if (Constants.DEBUG_PATTERN_CREATION) {
                            println("aPETJ 4: added event $ev")
                        }
                    }

                    // figure out when the next catch or hold is
                    var nextcatchtime = lastcatchtime
                    var k2 = k + 1

                    while (nextcatchtime == lastcatchtime) {
                        var tempk = k2
                        var wrap = 0
                        while (tempk >= indexes) {
                            tempk -= indexes
                            ++wrap
                        }

                        if (wrap > 1) {
                            throw JuggleExceptionInternal(
                                "Couldn't find next catch/hold past t=$lastcatchtime"
                            )
                        }

                        for (tempslot in 0..<maxOccupancy) {
                            val tempsst = th[j][h][tempk][tempslot] ?: break
                            val catcht = tempsst.catchTime + (wrap * indexes).toDouble() / bps
                            nextcatchtime =
                                (if (tempslot == 0) catcht else min(nextcatchtime, catcht))
                        }

                        k2++
                    }

                    // add other events between the current throw and the next catch
                    pos = sst.handsBeat
                    numcoords = hands!!.getCatchIndex(sst.juggler, pos)

                    for (di in 1..<numcoords) {
                        val c = hands!!.getCoordinate(sst.juggler, pos, di) ?: continue
                        if (h == LEFT_HAND) {
                            c.x = -c.x
                        }
                        val ev = JmlEvent(
                            x = c.x,
                            y = c.y,
                            z = c.z,
                            t = sst.throwTime + di.toDouble() *
                                (nextcatchtime - sst.throwTime) / numcoords,
                            juggler = sst.juggler,
                            hand = if (h == RIGHT_HAND) JmlEvent.RIGHT_HAND else JmlEvent.LEFT_HAND
                        )
                        calcpos[ev] = false
                        rec.events.add(ev)
                        if (Constants.DEBUG_PATTERN_CREATION) {
                            println("aPETJ 5: added event $ev")
                        }
                    }
                }
            }
        }
    }

    protected fun addJugglerPositionsToJml(
        rec: PatternBuilder
    ) {
        if (bodies == null) return
        for (k in 0..<period) {
            for (j in 0..<numberOfJugglers) {
                val index = k % bodies!!.getPeriod(j + 1)
                val coords = bodies!!.getNumberOfPositions(j + 1, index)
                for (z in 0..<coords) {
                    val jmlp = bodies!!.getPosition(j + 1, index, z)
                    if (jmlp != null) {
                        val newTime = (k.toDouble() + z.toDouble() / coords.toDouble()) / bps
                        rec.positions.add(jmlp.copy(t = newTime))
                    }
                }
            }
        }
    }

    protected fun addEventsForUntouchedHandsToJml(
        rec: PatternBuilder,
        handtouched: Array<BooleanArray>,
        calcpos: MutableMap<JmlEvent, Boolean>
    ) {
        for (j in 0..<numberOfJugglers) {
            for (h in 0..1) {
                if (!handtouched[j][h]) {
                    val ev = JmlEvent(
                        x = if (h == RIGHT_HAND) RESTINGX else -RESTINGX,
                        y = 0.0,
                        z = 0.0,
                        t = -1.0,
                        juggler = j + 1,
                        hand = if (h == 0) JmlEvent.RIGHT_HAND else JmlEvent.LEFT_HAND
                    )
                    calcpos[ev] = false
                    rec.events.add(ev)
                }
            }
        }
    }

    protected fun addEventsForUntouchedPathsToJml(
        rec: PatternBuilder,
        pathtouched: BooleanArray,
        calcpos: MutableMap<JmlEvent, Boolean>
    ) {
        // first, apply all pattern symmetries to figure out which paths don't get touched
        for (sym in rec.symmetries) {
            val perm = sym.pathPerm
            for (k in 0..<numberOfPaths) {
                if (pathtouched[k]) {
                    for (l in 1..<perm.orderOf(k + 1)) {
                        pathtouched[perm.map(k + 1, l) - 1] = true
                    }
                }
            }
        }

        // next, add <holding> transitions for the untouched paths
        for (k in 0..<numberOfPaths) {
            if (pathtouched[k]) {
                continue
            }

            // figure out which hand it should belong in
            var hand = JmlEvent.LEFT_HAND
            var juggler = 0

            top@ for (tempk in 0..<indexes) {
                for (tempj in 0..<numberOfJugglers) {
                    for (temph in 0..1) {
                        for (slot in 0..<maxOccupancy) {
                            val sst = th[tempj][temph][tempk][slot]
                            if (sst != null && sst.pathNum == (k + 1)) {
                                hand = (if (temph == RIGHT_HAND) JmlEvent.RIGHT_HAND
                                else JmlEvent.LEFT_HAND)
                                juggler = tempj
                                break@top
                            }
                        }
                    }
                }
            }

            // add <holding> transitions to each of that hand's events
            for ((index, ev) in rec.events.withIndex()) {
                if (ev.hand == hand && ev.juggler == (juggler + 1)) {
                    val ev2 = ev.withTransition(
                        JmlTransition(
                            type = JmlTransition.TRANS_HOLDING,
                            path = (k + 1)
                        )
                    )
                    calcpos[ev2] = calcpos[ev]!!
                    rec.events[index] = ev2
                    // mark related paths as touched
                    pathtouched[k] = true
                    for (sym in rec.symmetries) {
                        val perm = sym.pathPerm
                        for (l in 1..<perm.orderOf(k + 1)) {
                            pathtouched[perm.map(k + 1, l) - 1] = true
                        }
                    }
                }
            }
        }
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    protected fun addEventsForGapsToJml(
        rec: PatternBuilder,
        calcpos: MutableMap<JmlEvent, Boolean>
    ) {
        scanstart@ while (true) {
            // build the pattern so we can use `allEvents`
            val result = JmlPattern.fromPatternBuilder(rec)

            for (h in 0..1) {
                val hand = if (h == 0) JmlEvent.RIGHT_HAND else JmlEvent.LEFT_HAND
                val startEvent: MutableList<JmlEvent?> = MutableList(numberOfJugglers) { null }

                for (image in result.allEvents) {
                    if (image.event.hand != hand) {
                        continue
                    }
                    if (image.event.t < 2 * result.loopStartTime - result.loopEndTime) {
                        continue
                    }
                    if (image.event.t > 2 * result.loopEndTime - result.loopStartTime) {
                        break
                    }
                    val start = startEvent[image.event.juggler - 1]

                    if (start != null) {
                        val gap = image.event.t - start.t

                        if (gap > SECS_EVENT_GAP_MAX) {
                            val numAdd = (gap / SECS_EVENT_GAP_MAX).toInt()
                            val deltaT = gap / (numAdd + 1).toDouble()

                            for (i in 1..numAdd) {
                                val ev =
                                    JmlEvent(
                                        t = start.t + i * deltaT,
                                        juggler = image.event.juggler,
                                        hand = hand
                                    )
                                // no transitions yet; added later if needed
                                calcpos[ev] = true
                                rec.events.add(ev)
                            }
                            continue@scanstart
                        }
                    }
                    startEvent[image.event.juggler - 1] = image.event
                }
            }
            break
        }
    }

    protected fun addLocationsForIncompleteEventsToJml(
        rec: PatternBuilder,
        calcpos: MutableMap<JmlEvent, Boolean>
    ) {
        for ((index, ev) in rec.events.withIndex()) {
            if (!(calcpos[ev] ?: false)) {
                continue
            }

            // rebuild the pattern to get the event sequence
            val pat = JmlPattern.fromPatternBuilder(rec)
            val loopTime = pat.loopEndTime - pat.loopStartTime

            val startEvent: JmlEvent? =
                pat.eventSequence(startTime = ev.t, reverse = true)
                    .takeWhile { it.event.t > ev.t - loopTime }
                    .filter { it.event.juggler == ev.juggler && it.event.hand == ev.hand }
                    .firstOrNull { !(calcpos[it.primary] ?: false) }?.event
            if (startEvent == null) {
                // simple positioning event
                rec.events[index] = ev.copy(
                    x = if (ev.hand == RIGHT_HAND) RESTINGX else -RESTINGX,
                    y = 0.0,
                    z = 0.0,
                )
                continue
            }

            val endEvent: JmlEvent? =
                pat.eventSequence(startTime = ev.t)
                    .takeWhile { it.event.t < ev.t + loopTime }
                    .filter { it.event.juggler == ev.juggler && it.event.hand == ev.hand }
                    .firstOrNull { !(calcpos[it.primary] ?: false) }?.event
            if (endEvent == null) {
                // if we found an event going backward, we should find one forward
                throw JuggleExceptionInternal(
                    "Error in addLocationsForIncompleteEventsToJml()", pat
                )
            }

            // linear interpolation between start and end positions
            val startT = startEvent.t
            val startPos = startEvent.localCoordinate
            val endT = endEvent.t
            val endPos = endEvent.localCoordinate

            val t = ev.t
            val x = (startPos.x + (t - startT) * (endPos.x - startPos.x) / (endT - startT))
            val y = (startPos.y + (t - startT) * (endPos.y - startPos.y) / (endT - startT))
            val z = (startPos.z + (t - startT) * (endPos.z - startPos.z) / (endT - startT))
            rec.events[index] = ev.copy(x = x, y = y, z = z)
        }
    }

    companion object {
        const val BPS_DEFAULT: Double = -1.0 // calculate bps
        const val DWELL_DEFAULT: Double = 1.3
        const val GRAVITY_DEFAULT: Double = 980.0
        const val PROPDIAM_DEFAULT: Double = 10.0
        const val BOUNCEFRAC_DEFAULT: Double = 0.9
        const val SQUEEZEBEATS_DEFAULT: Double = 0.4
        const val PROP_DEFAULT: String = "ball"

        // for hss config
        const val HOLD_DEFAULT: Boolean = false
        const val DWELLMAX_DEFAULT: Boolean = true

        const val RIGHT_HAND: Int = 0
        const val LEFT_HAND: Int = 1

        // Default spatial coordinates, by number of objects
        protected val samethrowx: DoubleArray =
            doubleArrayOf(0.0, 20.0, 25.0, 12.0, 10.0, 7.5, 5.0, 5.0, 5.0)
        protected val crossingthrowx: DoubleArray =
            doubleArrayOf(0.0, 17.0, 17.0, 12.0, 10.0, 18.0, 25.0, 25.0, 30.0)
        protected val catchx: DoubleArray =
            doubleArrayOf(0.0, 17.0, 25.0, 30.0, 40.0, 45.0, 45.0, 50.0, 50.0)
        protected const val RESTINGX: Double = 25.0

        // Default beats per second, by number of objects
        protected val throwspersec: DoubleArray = doubleArrayOf(
            2.00,
            2.00,
            2.00,
            2.90,
            3.40,
            4.10,
            4.25,
            5.00,
            5.00,
            5.50,
        )

        // How many beats early to throw a '1' (all other throws are on-beat)
        // This value is calculated; see asJmlPattern()
        protected var beats_one_throw_early: Double = 0.0

        // Minimum airtime for a throw, in beats
        protected const val BEATS_AIRTIME_MIN: Double = 0.3

        // Minimum time from a throw to a subsequent catch for that hand, in beats
        protected const val BEATS_THROW_CATCH_MIN: Double = 0.3

        // Minimum time from a catch to a subsequent throw for that hand, in beats
        protected const val BEATS_CATCH_THROW_MIN: Double = 0.02

        // Maximum allowed time without events for a given hand, in seconds
        protected const val SECS_EVENT_GAP_MAX: Double = 0.5

        // Helper to decide whether the catches immediately prior to the two given
        // throws should be made in the order given, or whether they should be switched.
        //
        // JKB: The following implementation isn't ideal; we would like a function that
        // is invariant with respect to the various pattern symmetries we can apply,
        // but I don't think this is possible with respect to the jugglers.

        protected fun isCatchOrderIncorrect(t1: MhnThrow, t2: MhnThrow): Boolean {
            // first look at the time spent in the air; catch higher throws first
            if (t1.source!!.index > t2.source!!.index) {
                return true
            }
            if (t1.source!!.index < t2.source!!.index) {
                return false
            }

            // look at which juggler it's from; catch from "faraway" jugglers first
            val jdiff1 = abs(t1.juggler - t1.source!!.juggler)
            val jdiff2 = abs(t2.juggler - t2.source!!.juggler)
            if (jdiff1 < jdiff2) {
                return true
            }
            if (jdiff1 > jdiff2) {
                return false
            }

            // look at which hand it's from; catch from same hand first
            val hdiff1 = abs(t1.hand - t1.source!!.hand)
            val hdiff2 = abs(t2.hand - t2.source!!.hand)
            return hdiff1 > hdiff2
        }
    }
}
