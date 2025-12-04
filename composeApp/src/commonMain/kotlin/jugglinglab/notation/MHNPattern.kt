//
// MHNPattern.kt
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
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.notation

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.Constants
import jugglinglab.jml.*
import jugglinglab.util.*
import jugglinglab.util.getStringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class MHNPattern : Pattern() {
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
    var numberOfJugglers: Int = 0
        protected set
    var numberOfPaths: Int = 0
        protected set
    var period: Int = 0
        protected set
    var maxOccupancy: Int = 0
        protected set
    lateinit var th: Array<Array<Array<Array<MHNThrow?>>>>

    protected var hands: MHNHands? = null
    protected var bodies: MHNBody? = null
    var maxThrow: Int = 0
        protected set
    var indexes: Int = 0
    protected lateinit var symmetries: ArrayList<MHNSymmetry>
    protected var bps: Double = 0.0

    fun addSymmetry(ss: MHNSymmetry) {
        symmetries.add(ss)
    }

    // Pull out the MHN-related parameters from the given list, leaving any
    // other parameters alone.
    //
    // Note this doesn't create a valid pattern as-is, since MHNNotation doesn't
    // know how to interpret `pattern`. Subclasses like SiteswapPattern should
    // override this to add that functionality.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun fromParameters(pl: ParameterList): MHNPattern {
        config = pl.toString()
        // `pattern` is the only required parameter
        pattern = pl.removeParameter("pattern") ?:
            throw JuggleExceptionUser(getStringResource(Res.string.error_no_pattern))

        var temp: String?
        if ((pl.removeParameter("bps").also { temp = it }) != null) {
            try {
                bpsSet = temp!!.toDouble()
                bps = bpsSet
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_bps_value)
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("dwell").also { temp = it }) != null) {
            try {
                dwell = temp!!.toDouble()
                if (dwell <= 0 || dwell >= 2) {
                    val message = getStringResource(Res.string.error_dwell_range)
                    throw JuggleExceptionUser(message)
                }
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_dwell_value)
                throw JuggleExceptionUser(message)
            }
        }
        if ((pl.removeParameter("hands").also { temp = it }) != null) {
            hands = MHNHands(temp!!)
        }
        if ((pl.removeParameter("body").also { temp = it }) != null) {
            bodies = MHNBody(temp!!)
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
                        val message = getStringResource(Res.string.error_hss_hold_value_error)
                        throw JuggleExceptionUser(message)
                    }
                }
            }

            if ((pl.removeParameter("dwellmax").also { temp = it }) != null) {
                dwellmax = when (temp?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> {
                        val message = getStringResource(Res.string.error_hss_dwellmax_value_error)
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
            throw JuggleExceptionInternal(jeu.message)
        }
        return result
    }

    // Fill in details of the juggling matrix th[], to prepare for animation
    //
    // Note that th[] is assumed to be pre-populated with MHNThrows from the
    // parsing step, prior to this. This function fills in missing data elements
    // in the MHNThrows, connecting them up into a pattern. See MHNThrow.java
    // for more details.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    protected fun buildJugglingMatrix() {
        // build out the juggling matrix in steps
        //
        // this will find and raise many types of errors in the pattern
        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("-----------------------------------------------------")
            println("Building internal MHNPattern representation...\n")
            println("findMasterThrows()")
        }
        findMasterThrows()

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
            println("\nInternal MHNPattern representation:\n")
            println(s)
            println("-----------------------------------------------------")
        }
    }

    // Describes an entry in the `th` array, for iteration.

    private data class MHNThrowEntry(
        val i: Int,
        val j: Int,
        val h: Int,
        val slot: Int,
        val throwInstance: MHNThrow?
    )

    // Custom iterator (as a Sequence) that traverses the 4D `th` array in the
    // specific order required by the MHN logic: time index, then juggler, then
    // hand, then multiplex slot.

    private fun Array<Array<Array<Array<MHNThrow?>>>>.mhnIterator():
        Sequence<MHNThrowEntry> = sequence {
        for (i in 0..<indexes) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    for (slot in 0..<maxOccupancy) {
                        yield(MHNThrowEntry(i, j, h, slot, this@mhnIterator[j][h][i][slot]))
                    }
                }
            }
        }
    }

    // Determine which throws are "master" throws. Because of symmetries defined
    // for the pattern, some throws are shifted or reflected copies of others.
    // For each such chain of related throws, appoint one as the master.

    @Throws(JuggleExceptionInternal::class)
    protected fun findMasterThrows() {
        // start by making every throw a master throw
        th.mhnIterator().forEach { (_, _, _, _, mhnt) ->
            mhnt?.master = mhnt // Every throw is its own master initially
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

                    var imagej = jperm.getMapping(j + 1)
                    val imageh = (if (imagej > 0) h else 1 - h)
                    imagej = abs(imagej) - 1

                    val imaget = th[imagej][imageh][imagei][slot]
                        ?: throw JuggleExceptionInternal("Problem finding master throws")

                    val m = mhnt.master!!
                    val im = imaget.master!!
                    if (m === im) {
                        return@forEach
                    }

                    // we have a disagreement about which is the master;
                    // choose one of them and set them equal
                    var newm: MHNThrow? = m
                    if (m.index > im.index) {
                        newm = im
                    } else if (m.index == im.index) {
                        if (m.juggler > im.juggler) {
                            newm = im
                        } else if (m.juggler == im.juggler) {
                            if (m.hand > im.hand) {
                                newm = im
                            }
                        }
                    }
                    imaget.master = newm
                    mhnt.master = imaget.master
                    changed = true
                }
            }
        }

        if (Constants.DEBUG_SITESWAP_PARSING) {
            th.mhnIterator().forEach { (i, j, h, slot, mhnt) ->
                if (mhnt?.master === mhnt) {
                    println("master throw at j=$j,h=$h,i=$i,slot=$slot")
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
            if (sst == null || sst.master !== sst) {
                return@forEach  // skip non-master throws
            }

            // Figure out which slot number we're filling with this
            // master throw. We need to find a value of `targetslot`
            // that is empty for the master throw's target, as well as
            // the targets of its images.
            var targetslot = 0
            while (targetslot < maxOccupancy) {  // find value of targetslot that works
                var itworks = true

                // loop over all throws that have sst as master
                th.mhnIterator().forEach { (_, _, _, _, sst2) ->
                    if (sst2 == null || sst2.master !== sst || sst2.targetindex >= indexes) {
                        return@forEach
                    }

                    val target =
                        th[sst2.targetjuggler - 1][sst2.targethand][sst2.targetindex][targetslot]
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
                            + (sst.targetindex + 1)
                            + " for juggler "
                            + sst.targetjuggler
                            + ", "
                            + (if (sst.targethand == 0) "right hand" else "left hand"))
                    )
                }
                val hand = if (sst.targethand == 0)
                    getStringResource(Res.string.error_right_hand)
                else
                    getStringResource(Res.string.error_left_hand)
                val message = getStringResource(
                    Res.string.error_badpattern_landings,
                    sst.targetindex + 1,
                    sst.targetjuggler,
                    hand
                )
                throw JuggleExceptionUser(message)
            }

            // loop again over all throws that have sst as master,
            // wiring up sources and targets using the value of `targetslot`
            th.mhnIterator().forEach { (_, _, _, _, sst2) ->
                if (sst2 == null || sst2.master !== sst || sst2.targetindex >= indexes) {
                    return@forEach
                }
                val target2 =
                    th[sst2.targetjuggler - 1][sst2.targethand][sst2.targetindex][targetslot]
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
                sst.pathnum = sst.source!!.pathnum
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
                                    + tempsst.targetindex
                                    + ", targethand="
                                    + tempsst.targethand
                                    + ", targetslot="
                                    + tempsst.targetslot
                                    + ", pathnum="
                                    + tempsst.pathnum
                            )
                        }
                        println("---------------------------")
                    }
                }
                val message = getStringResource(Res.string.error_badpattern)
                throw JuggleExceptionUser(message)
            }
            sst.pathnum = currentpath
            ++currentpath
        }
        if (currentpath <= numberOfPaths) {
            throw JuggleExceptionInternal("Problem assigning path numbers 2")
        }
    }

    // Set the `source` field for all throws that don't already have it set.
    //
    // In doing this we create new MHNThrows that are not part of the juggling
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

                        val sst3 = MHNThrow()
                        sst3.juggler = sst2.juggler
                        sst3.hand = sst2.hand
                        sst3.index = sst2.index - period
                        sst3.slot = sst2.slot
                        sst3.targetjuggler = j
                        sst3.targethand = h
                        sst3.targetindex = i
                        sst3.targetslot = slot
                        sst3.handsindex = -1 // undefined
                        sst3.pathnum = sst.pathnum
                        sst3.mod = sst2.mod
                        sst3.master = sst2.master
                        sst3.source = null
                        sst3.target = sst

                        sst.source = sst3
                    }
                }
            }
        }
    }

    // Set the MHNThrow.catching and MHNThrow.catchnum fields.

    protected fun setCatchOrder() {
        // Figure out the correct catch order for master throws
        for (k in 0..<indexes) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    var slotcatches = 0

                    for (slot in 0..<maxOccupancy) {
                        val sst = th[j][h][k][slot] ?: break

                        sst.catching = (sst.source!!.mod!![0] != 'H')
                        if (sst.catching) {
                            sst.catchnum = slotcatches
                            ++slotcatches
                        }
                    }

                    // Arrange the order of the catches, if more than one
                    if (slotcatches < 2) {
                        continue
                    }

                    for (slot1 in 0..<maxOccupancy) {
                        val sst1 = th[j][h][k][slot1]
                        if (sst1 == null || sst1.master !== sst1) {
                            break // only master throws
                        }

                        if (!sst1.catching) {
                            continue
                        }

                        for (slot2 in (slot1 + 1)..<maxOccupancy) {
                            val sst2 = th[j][h][k][slot2]
                            if (sst2 == null || sst2.master !== sst2) {
                                break
                            }
                            if (!sst2.catching) {
                                continue
                            }
                            val switchcatches: Boolean =
                                if (sst1.catchnum < sst2.catchnum) {
                                    isCatchOrderIncorrect(sst1, sst2)
                                } else {
                                    isCatchOrderIncorrect(sst2, sst1)
                                }
                            if (switchcatches) {
                                val temp = sst1.catchnum
                                sst1.catchnum = sst2.catchnum
                                sst2.catchnum = temp
                            }
                        }
                    }
                }
            }
        }

        // Copy that over to the non-master throws
        th.mhnIterator().forEach { (_, _, _, _, sst) ->
            if (sst == null || sst.master === sst) {
                return@forEach  // skip master throws
            }
            sst.catchnum = sst.master!!.catchnum
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
            sst.dwellwindow = if (prevBeatThrow) 1 else 2
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
                sb.append("  th[").append(j).append("][")
                    .append(h).append("][")
                    .append(i).append("][")
                    .append(s).append("] = ")
                if (mhnt == null) {
                    sb.append("null\n")
                } else {
                    sb.append(mhnt).append("\n")
                }
            }
            sb.append("symmetries:\n") // not finished
            sb.append("hands:\n")
            sb.append("bodies:\n")
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

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun asJMLPattern(): JMLPattern {
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

        val result = JMLPattern()

        // Step 1: Add basic information about the pattern
        result.numberOfJugglers = numberOfJugglers
        addPropsToJML(result)
        addSymmetriesToJML(result)

        // Step 2: Assign catch and throw times to each MHNThrow in the
        // juggling matrix
        findCatchThrowTimes()

        // Step 3: Add the primary events to the pattern
        //
        // We keep track of which hands/paths don't get any events, so we can
        // add positioning events later
        val handtouched = Array(numberOfJugglers) { BooleanArray(2) }
        val pathtouched = BooleanArray(numberOfPaths)
        addPrimaryEventsToJML(result, handtouched, pathtouched)

        // Step 4: Define a body position for this juggler and beat, if specified
        addJugglerPositionsToJML(result)

        // Step 5: Add simple positioning events for hands that got no events
        addEventsForUntouchedHandsToJML(result, handtouched)

        // Step 6: Add <holding> transitions for paths that got no events
        addEventsForUntouchedPathsToJML(result, pathtouched)

        // Step 7: Build the full event list so we can scan through it
        // chronologically in Steps 8-10
        result.buildEventList()

        // Step 8: Add events where there are long gaps for a hand
        if (hands == null) addEventsForGapsToJML(result)

        // Step 9: Specify positions for events that don't have them defined yet
        addLocationsForIncompleteEventsToJML(result)

        // Step 10: Add additional <holding> transitions where needed (i.e., a
        // ball is in a hand)
        addMissingHoldsToJML(result)

        // Step 11: Confirm that each throw in the JMLPattern has enough time to
        // satisfy its minimum duration requirement. If not then rescale time
        // (bps) to make everything feasible.
        //
        // This should only be done if the user has not manually set `bps`.
        if (bpsSet <= 0) {
            val scaleFactor = result.scaleTimeToFitThrows(1.01)
            if (scaleFactor > 1.0) {
                bps /= scaleFactor
                if (hands == null) {
                    // redo steps 8-10
                    addEventsForGapsToJML(result)
                    addLocationsForIncompleteEventsToJML(result)
                    addMissingHoldsToJML(result)
                }
                if (Constants.DEBUG_LAYOUT) {
                    println("Rescaled time; scale factor = $scaleFactor")
                }
            }
        }

        result.title = if (title == null) pattern else title
        if (colors != null) {
            result.setPropColors(colors!!)
        }
        if (Constants.DEBUG_LAYOUT) {
            println("Pattern in JML format:\n")
            println(result)
        }
        return result
    }

    protected fun calcBps(): Double {
        // Calculate a default beats per second (bps) for the pattern
        var result = 0.0
        var numberaveraged = 0

        th.mhnIterator().forEach { (k, _, _, _, sst) ->
            if (sst != null) {
                val throwval = sst.targetindex - k
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

    protected fun addPropsToJML(pat: JMLPattern) {
        val balls = numberOfPaths
        val mod = if (propdiam != PROPDIAM_DEFAULT) "diam=$propdiam" else null
        val pa = IntArray(balls) { 1 }
        pat.numberOfPaths = balls
        pat.addProp(JMLProp(propName, mod))
        pat.setPropAssignments(pa)
    }

    @Throws(JuggleExceptionUser::class)
    protected fun addSymmetriesToJML(pat: JMLPattern) {
        val balls = numberOfPaths

        for (sss in symmetries) {
            val symtype: Int
            val pathmap = IntArray(balls + 1)

            when (sss.type) {
                MHNSymmetry.TYPE_DELAY -> {
                    symtype = JMLSymmetry.TYPE_DELAY
                    for (k in 0..<(indexes - sss.delay)) {
                        for (j in 0..<numberOfJugglers) {
                            for (h in 0..1) {
                                for (slot in 0..<maxOccupancy) {
                                    val sst: MHNThrow? = th[j][h][k][slot]
                                    if (sst != null && sst.pathnum != -1) {
                                        val sst2 = th[j][h][k + sss.delay][slot]
                                            ?: throw JuggleExceptionUser(
                                                getStringResource(Res.string.error_badpattern_paths)
                                            )
                                        if ((sst.pathnum == 0) || (sst2.pathnum == 0)) {
                                            throw JuggleExceptionUser(
                                                getStringResource(Res.string.error_badpattern_paths)
                                            )
                                        }
                                        if (pathmap[sst.pathnum] == 0) {
                                            pathmap[sst.pathnum] = sst2.pathnum
                                        } else if (pathmap[sst.pathnum] != sst2.pathnum) {
                                            throw JuggleExceptionUser(
                                                getStringResource(Res.string.error_badpattern_delay)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                MHNSymmetry.TYPE_SWITCH -> symtype = JMLSymmetry.TYPE_SWITCH
                MHNSymmetry.TYPE_SWITCHDELAY -> {
                    symtype = JMLSymmetry.TYPE_SWITCHDELAY

                    val jugperm = sss.jugglerPerm
                    for (k in 0..<(indexes - sss.delay)) {
                        for (j in 0..<numberOfJugglers) {
                            for (h in 0..1) {
                                var slot = 0
                                while (slot < maxOccupancy) {
                                    val sst: MHNThrow? = th[j][h][k][slot]
                                    if (sst != null && sst.pathnum != -1) {
                                        val map = jugperm.getMapping(j + 1)
                                        val newj = abs(map) - 1
                                        val newh = (if (map > 0) h else 1 - h)
                                        val sst2 = th[newj][newh][k + sss.delay][slot]
                                            ?: throw JuggleExceptionUser(
                                                getStringResource(Res.string.error_badpattern_paths)
                                            )
                                        if (sst.pathnum == 0 || sst2.pathnum == 0) {
                                            throw JuggleExceptionUser(
                                                getStringResource(Res.string.error_badpattern_paths)
                                            )
                                        }
                                        if (pathmap[sst.pathnum] == 0) {
                                            pathmap[sst.pathnum] = sst2.pathnum
                                        } else if (pathmap[sst.pathnum] != sst2.pathnum) {
                                            throw JuggleExceptionUser(
                                                getStringResource(Res.string.error_badpattern_switchdelay)
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
                    getStringResource(Res.string.error_unknown_symmetry)
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

            val sym = JMLSymmetry(
                symType = symtype,
                numberOfJugglers = sss.numberOfJugglers,
                numberOfPaths = numberOfPaths,
                jugglerPerm = sss.jugglerPerm,
                pathPerm = Permutation(numberOfPaths, pathmapstring, false),
                delay = sss.delay.toDouble() / bps
            )

            pat.addSymmetry(sym)
        }
    }

    // Assign throw and catch times to each element in the juggling matrix.
    //
    // The catch time here refers to that prop's catch immediately prior to the
    // throw represented by MHNThrow.

    fun findCatchThrowTimes() {
        for (k in 0..<period) {
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

                        if (onethrown) sst2.throwtime =
                            (k.toDouble() - beats_one_throw_early) / bps
                        else sst2.throwtime = k.toDouble() / bps

                        if (hss != null) {
                            // if (onethrown)
                            //    throwtime = ((double)k - 0.25*(double)dwellarray[k]) / bps;
                            // else
                            sst2.throwtime = k.toDouble() / bps
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
                    var tempindex = k - sst.dwellwindow
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
                        ((k - sst.dwellwindow).toDouble() - (if (prevOnethrown) beats_one_throw_early else 0.0)
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
                        min(firstcatchtime, sst.throwtime - BEATS_CATCH_THROW_MIN / bps)

                    // Set catch times
                    for (slot in 0..<maxOccupancy) {
                        val sst2 = th[j][h][k][slot] ?: break
                        var catchtime = firstcatchtime

                        if (numCatches > 1) {
                            catchtime +=
                                (sst2.catchnum.toDouble() / (numCatches - 1).toDouble()) * (squeezebeats / bps)
                        }

                        if (hss != null) {
                            // if getPeriod() > size of dwellarray due to repeats
                            // to account for hand/body positions, then reuse
                            // dwellarray timings from prior array elements
                            val newk = k % dwellarray!!.size

                            catchtime = (k.toDouble() - dwellarray!![newk]) / bps

                            if (numCatches > 1) {
                                catchtime +=
                                    sst2.catchnum.toDouble() / (numCatches - 1).toDouble() * squeezebeats / bps
                            }
                        }

                        catchtime = min(catchtime, sst.throwtime - BEATS_CATCH_THROW_MIN / bps)

                        sst2.catchtime = catchtime
                    }
                }
            }
        }
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    protected fun addPrimaryEventsToJML(
        pat: JMLPattern, handtouched: Array<BooleanArray>, pathtouched: BooleanArray
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
                    if (sst == null || sst.master !== sst) {
                        continue
                    }

                    // Step 3a: Add transitions to the on-beat event (throw or holding transitions)
                    var ev = JMLEvent()
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
                                if (sst2.mod!!.contains("F")) {
                                    mod = "forced=true"
                                }
                                if (sst2.mod!!.contains("H")) {
                                    mod = if (mod == null) {
                                        "hyper=true"
                                    } else {
                                        "$mod;hyper=true"
                                    }
                                }
                                var bounces = 1
                                var i = 1
                                while (i < sst2.mod!!.length) {
                                    if (sst2.mod!![i] == 'B') {
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

                        if (sst2.mod!![0] != 'H') {
                            if (sst2.isZero) {
                                val message = getStringResource(
                                    Res.string.error_modifier_on_0,
                                    sst2.mod,
                                    k + 1
                                )
                                throw JuggleExceptionUser(message)
                            }
                            ev.addTransition(
                                JMLTransition(
                                    JMLTransition.TRANS_THROW,
                                    sst2.pathnum,
                                    type,
                                    mod
                                )
                            )

                            val throwval = sst2.targetindex - k

                            throwxsum += if (sst2.targethand == h) {
                                if (throwval > 8) samethrowx[8] else samethrowx[throwval]
                            } else {
                                if (throwval > 8) crossingthrowx[8] else crossingthrowx[throwval]
                            }
                            ++numThrows
                        } else if (hands != null) {
                            if (!sst2.isZero) {
                                // add holding transition if there's a ball in
                                // hand and "hands" is specified
                                ev.addTransition(
                                    JMLTransition(
                                        JMLTransition.TRANS_HOLDING,
                                        sst2.pathnum,
                                        type,
                                        mod
                                    )
                                )
                                pathtouched[sst2.pathnum - 1] = true
                            }
                        }
                    }

                    // Step 3b: Finish off the on-beat event based on the transitions we've added
                    if (hands == null && numThrows == 0) {
                        // don't add on-beat event if there are no throws -- unless a hand
                        // layout is specified
                    } else {
                        // set the event position
                        if (hands == null) {
                            if (numThrows > 0) {
                                var throwxav = throwxsum / numThrows.toDouble()
                                if (h == LEFT_HAND) {
                                    throwxav = -throwxav
                                }
                                ev.localCoordinate = Coordinate(throwxav, 0.0, 0.0)
                                ev.calcpos = false
                            } else {
                                // mark event to calculate coordinate later
                                ev.calcpos = true
                            }
                        } else {
                            val c = hands!!.getCoordinate(sst.juggler, sst.handsindex, 0)!!
                            if (h == LEFT_HAND) {
                                c.x = -c.x
                            }
                            ev.localCoordinate = c
                            ev.calcpos = false
                        }

                        ev.t = sst.throwtime
                        ev.setHand(
                            j + 1,
                            if (h == RIGHT_HAND) HandLink.RIGHT_HAND else HandLink.LEFT_HAND
                        )
                        pat.addEvent(ev)

                        // record which hands are touched by this event, for later reference
                        th.mhnIterator().forEach { (_, j2, h2, _, sst2) ->
                            if (sst2 != null && sst2.master === sst) {
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

                        val catchpath = sst2.pathnum
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
                        ev = JMLEvent()

                        // first set the event position
                        if (hands == null) {
                            if (numCatches > 0) {
                                val cx = catchxsum / numCatches.toDouble()
                                // System.out.println("average catch pos. = "+cx);
                                ev.localCoordinate =
                                    Coordinate(if (h == RIGHT_HAND) cx else -cx, 0.0, 0.0)
                                ev.calcpos = false
                            } else {
                                // mark event to calculate coordinate later
                                ev.calcpos = true
                            }
                        } else {
                            var pos = sst.handsindex - 2
                            while (pos < 0) {
                                pos += hands!!.getPeriod(sst.juggler)
                            }
                            val index = hands!!.getCatchIndex(sst.juggler, pos)
                            val c = hands!!.getCoordinate(sst.juggler, pos, index)!!
                            if (h == LEFT_HAND) {
                                c.x = -c.x
                            }
                            ev.localCoordinate = c
                            ev.calcpos = false
                        }

                        ev.t = sst.catchtime
                        lastcatchtime = sst.catchtime

                        ev.setHand(
                            j + 1,
                            if (h == RIGHT_HAND) HandLink.RIGHT_HAND else HandLink.LEFT_HAND
                        )

                        // add all the transitions
                        for (slot in 0..<maxOccupancy) {
                            val sst2 = th[j][h][k][slot] ?: break
                            if (sst2.catching) {
                                ev.addTransition(
                                    JMLTransition(
                                        JMLTransition.TRANS_CATCH,
                                        sst2.pathnum,
                                        null,
                                        null
                                    )
                                )
                            } else if (hands != null) {
                                if (sst2.pathnum != -1) {  // -1 signals a 0 throw
                                    // add holding transition if there's a ball in
                                    // hand and "hands" is specified
                                    ev.addTransition(
                                        JMLTransition(
                                            JMLTransition.TRANS_HOLDING,
                                            sst2.pathnum,
                                            null,
                                            null
                                        )
                                    )
                                    pathtouched[sst2.pathnum - 1] = true
                                }
                            }
                        }

                        pat.addEvent(ev)
                    } else {
                        // Case 2: separate event for each catch; we know that numcatches > 1 here
                        for (slot in 0..<maxOccupancy) {
                            val sst2 = th[j][h][k][slot]
                            if (sst2 == null || !sst2.catching) {
                                continue
                            }

                            // we only need to add catch transitions here; holding transitions will be
                            // added in Step 9
                            ev = JMLEvent()

                            // first set the event position
                            if (hands == null) {
                                val cx = catchxsum / numCatches.toDouble()
                                // System.out.println("average catch pos. = "+cx);
                                ev.localCoordinate =
                                    Coordinate((if (h == RIGHT_HAND) cx else -cx), 0.0, 0.0)
                            } else {
                                var pos = sst.handsindex - 2
                                while (pos < 0) {
                                    pos += hands!!.getPeriod(sst.juggler)
                                }
                                val index = hands!!.getCatchIndex(sst.juggler, pos)
                                val c = hands!!.getCoordinate(sst.juggler, pos, index)!!
                                if (h == LEFT_HAND) {
                                    c.x = -c.x
                                }
                                ev.localCoordinate = c
                            }
                            ev.calcpos = false

                            ev.t = sst2.catchtime
                            if (sst2.catchnum == (numCatches - 1)) {
                                lastcatchtime = sst2.catchtime
                            }

                            ev.setHand(
                                j + 1,
                                if (h == RIGHT_HAND) HandLink.RIGHT_HAND else HandLink.LEFT_HAND
                            )
                            ev.addTransition(
                                JMLTransition(
                                    JMLTransition.TRANS_CATCH,
                                    sst2.pathnum,
                                    null,
                                    null
                                )
                            )
                            pat.addEvent(ev)
                        }
                    }

                    // Step 3d: If hand positionss are specified, add any extra hand positioning
                    // events after the catch above, until the next catch for the hand
                    if (hands == null) {
                        continue
                    }

                    // add other events between the previous catch and the current throw
                    var pos = sst.handsindex - 2
                    while (pos < 0) {
                        pos += hands!!.getPeriod(sst.juggler)
                    }
                    val catchindex = hands!!.getCatchIndex(sst.juggler, pos)
                    var numcoords =
                        hands!!.getNumberOfCoordinates(sst.juggler, pos) - catchindex

                    for (di in 1..<numcoords) {
                        val c =
                            hands!!.getCoordinate(sst.juggler, pos, catchindex + di) ?: continue
                        ev = JMLEvent()
                        if (h == LEFT_HAND) {
                            c.x = -c.x
                        }
                        ev.localCoordinate = c
                        ev.calcpos = false
                        ev.t =
                            lastcatchtime + di.toDouble() *
                                (sst.throwtime - lastcatchtime) / numcoords
                        ev.setHand(
                            sst.juggler,
                            (if (h == RIGHT_HAND) HandLink.RIGHT_HAND else HandLink.LEFT_HAND)
                        )
                        pat.addEvent(ev)
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
                            val catcht = tempsst.catchtime + wrap * indexes / bps
                            nextcatchtime =
                                (if (tempslot == 0) catcht else min(nextcatchtime, catcht))
                        }

                        k2++
                    }

                    // add other events between the current throw and the next catch
                    pos = sst.handsindex
                    numcoords = hands!!.getCatchIndex(sst.juggler, pos)

                    for (di in 1..<numcoords) {
                        val c = hands!!.getCoordinate(sst.juggler, pos, di) ?: continue
                        ev = JMLEvent()
                        if (h == LEFT_HAND) {
                            c.x = -c.x
                        }
                        ev.localCoordinate = c
                        ev.calcpos = false
                        ev.t =
                            sst.throwtime + di.toDouble() *
                                (nextcatchtime - sst.throwtime) / numcoords
                        ev.setHand(
                            sst.juggler,
                            (if (h == RIGHT_HAND) HandLink.RIGHT_HAND else HandLink.LEFT_HAND)
                        )
                        pat.addEvent(ev)
                    }
                }
            }
        }
    }

    protected fun addJugglerPositionsToJML(pat: JMLPattern) {
        if (bodies == null) return
        for (k in 0..<period) {
            for (j in 0..<numberOfJugglers) {
                val index = k % bodies!!.getPeriod(j + 1)
                val coords = bodies!!.getNumberOfPositions(j + 1, index)
                for (z in 0..<coords) {
                    val jmlp = bodies!!.getPosition(j + 1, index, z)
                    if (jmlp != null) {
                        jmlp.t = (k.toDouble() + z.toDouble() / coords.toDouble()) / bps
                        pat.addPosition(jmlp)
                    }
                }
            }
        }
    }

    protected fun addEventsForUntouchedHandsToJML(
        pat: JMLPattern,
        handtouched: Array<BooleanArray>
    ) {
        for (j in 0..<numberOfJugglers) {
            for (h in 0..1) {
                if (!handtouched[j][h]) {
                    val ev = JMLEvent()
                    ev.localCoordinate =
                        Coordinate(if (h == RIGHT_HAND) RESTINGX else -RESTINGX, 0.0, 0.0)
                    ev.t = -1.0
                    ev.setHand(j + 1, if (h == 0) HandLink.RIGHT_HAND else HandLink.LEFT_HAND)
                    ev.calcpos = false
                    pat.addEvent(ev)
                }
            }
        }
    }

    protected fun addEventsForUntouchedPathsToJML(pat: JMLPattern, pathtouched: BooleanArray) {
        // first, apply all pattern symmetries to figure out which paths don't get touched
        for (sym in pat.symmetries) {
            val perm = sym.pathPerm
            for (k in 0..<numberOfPaths) {
                if (pathtouched[k]) {
                    for (l in 1..<perm!!.getOrder(k + 1)) {
                        pathtouched[perm.getMapping(k + 1, l) - 1] = true
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
            var hand = HandLink.LEFT_HAND
            var juggler = 0

            top@ for (tempk in 0..<indexes) {
                for (tempj in 0..<numberOfJugglers) {
                    for (temph in 0..1) {
                        for (slot in 0..<maxOccupancy) {
                            val sst = th[tempj][temph][tempk][slot]
                            if (sst != null && sst.pathnum == (k + 1)) {
                                hand = (if (temph == RIGHT_HAND) HandLink.RIGHT_HAND
                                else HandLink.LEFT_HAND)
                                juggler = tempj
                                break@top
                            }
                        }
                    }
                }
            }

            // add <holding> transitions to each of that hand's events
            var ev = pat.eventList
            while (ev != null) {
                if (ev.hand == hand && ev.juggler == (juggler + 1)) {
                    ev.addTransition(
                        JMLTransition(
                            JMLTransition.TRANS_HOLDING,
                            (k + 1),
                            null,
                            null
                        )
                    )
                    // mark related paths as touched
                    pathtouched[k] = true
                    for (sym in pat.symmetries) {
                        val perm = sym.pathPerm
                        for (l in 1..<perm!!.getOrder(k + 1)) {
                            pathtouched[perm.getMapping(k + 1, l) - 1] = true
                        }
                    }
                }
                ev = ev.next
            }
            //  if (ev == null)
            //      throw new JuggleExceptionUser("Could not find event for hand");
        }
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    protected fun addEventsForGapsToJML(pat: JMLPattern) {
        for (h in 0..1) {
            val hand = if (h == 0) HandLink.RIGHT_HAND else HandLink.LEFT_HAND
            if (h == 1) {
                // only need to do this if there's a switch or switchdelay symmetry
                pat.buildEventList()
            }
            for (j in 1..numberOfJugglers) {
                var ev = pat.eventList
                var start: JMLEvent? = null

                while (ev != null) {
                    if (ev.juggler == j && ev.hand == hand) {
                        if (start != null) {
                            val gap = ev.t - start.t

                            if (gap > SECS_EVENT_GAP_MAX) {
                                val add = (gap / SECS_EVENT_GAP_MAX).toInt()
                                val deltat = gap / (add + 1).toDouble()

                                for (i in 1..add) {
                                    val evtime = start.t + i * deltat
                                    if (evtime < pat.loopStartTime || evtime >= pat.loopEndTime) {
                                        continue
                                    }
                                    val ev2 = JMLEvent()
                                    ev2.t = evtime
                                    ev2.setHand(j, hand)
                                    ev2.calcpos = true
                                    pat.addEvent(ev2)
                                }
                            }
                        }
                        start = ev
                    }
                    ev = ev.next
                }
            }
        }
    }

    protected fun addLocationsForIncompleteEventsToJML(pat: JMLPattern) {
        for (j in 1..numberOfJugglers) {
            for (h in 0..1) {
                val hand = if (h == RIGHT_HAND) HandLink.RIGHT_HAND else HandLink.LEFT_HAND
                var ev = pat.eventList
                var start: JMLEvent? = null
                var scanstate = 1 // 1 = starting, 2 = on defined event, 3 = on undefined event
                while (ev != null) {
                    if (ev.juggler == j && ev.hand == hand) {
                        if (ev.calcpos) {
                            scanstate = 3
                        } else {
                            when (scanstate) {
                                1 -> scanstate = 2
                                2 -> {}
                                3 -> {
                                    if (start != null) {
                                        val end: JMLEvent = ev
                                        val tEnd = end.t
                                        val posEnd = end.localCoordinate
                                        val tStart = start.t
                                        val posStart = start.localCoordinate

                                        ev = start.next
                                        while (ev != end) {
                                            if (ev!!.juggler == j && ev.hand == hand) {
                                                val t = ev.t
                                                val x = (posStart.x
                                                    + (t - tStart) * (posEnd.x - posStart.x) /
                                                    (tEnd - tStart))
                                                val y = (posStart.y
                                                    + (t - tStart) * (posEnd.y - posStart.y) /
                                                    (tEnd - tStart))
                                                val z = (posStart.z
                                                    + (t - tStart) * (posEnd.z - posStart.z) /
                                                    (tEnd - tStart))
                                                ev.localCoordinate = Coordinate(x, y, z)
                                                ev.calcpos = false
                                            }
                                            ev = ev.next
                                        }
                                    }
                                    scanstate = 2
                                }
                            }
                            start = ev
                        }
                        // System.out.println("   final state = "+scanstate);
                    }
                    ev = ev.next
                }

                // do a last scan through to define any remaining undefined positions
                ev = pat.eventList
                while (ev != null) {
                    if (ev.juggler == j && ev.hand == hand && ev.calcpos) {
                        ev.localCoordinate =
                            Coordinate(if (h == RIGHT_HAND) RESTINGX else -RESTINGX, 0.0, 0.0)
                        ev.calcpos = false
                    }
                    ev = ev.next
                }
            }
        }
    }

    // Step 9: Scan through the list of events, and look for cases where we need
    // to add additional <holding> transitions.  These are marked by cases where the
    // catch and throw transitions for a given path have intervening events in that
    // hand; we want to add <holding> transitions to these intervening events.

    protected fun addMissingHoldsToJML(pat: JMLPattern) {
        for (k in 0..<numberOfPaths) {
            var addMode = false
            var foundEvent = false
            var addJuggler = 0
            var addHand = 0

            var ev = pat.eventList
            while (ev != null) {
                val tr = ev.getPathTransition((k + 1), JMLTransition.TRANS_ANY)
                if (tr != null) {
                    when (tr.transType) {
                        JMLTransition.TRANS_THROW -> {
                            if (!foundEvent && !addMode) {
                                // first event mentioning path is a throw
                                // rewind to beginning of list and add holds
                                addMode = true
                                addJuggler = ev.juggler
                                addHand = ev.hand
                                ev = pat.eventList
                                continue
                            }
                            addMode = false
                        }

                        JMLTransition.TRANS_CATCH,
                        JMLTransition.TRANS_SOFTCATCH,
                        JMLTransition.TRANS_GRABCATCH -> {
                            addMode = true
                            addJuggler = ev.juggler
                            addHand = ev.hand
                        }

                        JMLTransition.TRANS_HOLDING -> {
                            if (!foundEvent && !addMode) {
                                // first event mentioning path is a hold
                                // rewind to beginning of list and add holds
                                addMode = true
                                addJuggler = ev.juggler
                                addHand = ev.hand
                                ev = pat.eventList
                                continue
                            }
                            addMode = true
                            addJuggler = ev.juggler
                            addHand = ev.hand
                        }
                    }
                    foundEvent = true
                } else if (addMode) {
                    if (ev.juggler == addJuggler && ev.hand == addHand && ev.isMaster) {
                        ev.addTransition(
                            JMLTransition(
                                JMLTransition.TRANS_HOLDING,
                                (k + 1),
                                null,
                                null
                            )
                        )
                    }
                }
                ev = ev.next
            }
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

        // Decide whether the catches immediately prior to the two given throws should
        // be made in the order given, or whether they should be switched.
        //
        // The following implementation isn't ideal; we would like a function that is
        // invariant with respect to the various pattern symmetries we can apply, but
        // I don't think this is possible with respect to the jugglers.

        protected fun isCatchOrderIncorrect(t1: MHNThrow, t2: MHNThrow): Boolean {
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

        //----------------------------------------------------------------------
        // Convert from juggling matrix representation to JML
        //----------------------------------------------------------------------

        // The following are default spatial coordinates to use
        protected val samethrowx: DoubleArray =
            doubleArrayOf(0.0, 20.0, 25.0, 12.0, 10.0, 7.5, 5.0, 5.0, 5.0)
        protected val crossingthrowx: DoubleArray =
            doubleArrayOf(0.0, 17.0, 17.0, 12.0, 10.0, 18.0, 25.0, 25.0, 30.0)
        protected val catchx: DoubleArray =
            doubleArrayOf(0.0, 17.0, 25.0, 30.0, 40.0, 45.0, 45.0, 50.0, 50.0)
        protected const val RESTINGX: Double = 25.0

        // How many beats early to throw a '1' (all other throws are on-beat)
        //
        // This value is calculated; see asJMLPattern()
        protected var beats_one_throw_early: Double = 0.0

        // Minimum airtime for a throw, in beats
        protected const val BEATS_AIRTIME_MIN: Double = 0.3

        // Minimum time from a throw to a subsequent catch for that hand, in beats
        protected const val BEATS_THROW_CATCH_MIN: Double = 0.3

        // Minimum time from a catch to a subsequent throw for that hand, in beats
        protected const val BEATS_CATCH_THROW_MIN: Double = 0.02

        // Maximum allowed time without events for a given hand, in seconds
        protected const val SECS_EVENT_GAP_MAX: Double = 0.5

        //----------------------------------------------------------------------
        // Helpers for converting to JML
        //----------------------------------------------------------------------

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
    }
}
