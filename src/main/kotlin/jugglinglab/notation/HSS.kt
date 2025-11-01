//
// HSS.kt
//
// This class adds Hand Siteswap (HSS) functionality to Juggling Lab's
// siteswap notation component.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.Permutation.Companion.lcm
import java.text.MessageFormat
import kotlin.math.max

object HSS {
    internal var hss_dwell_default: Double = 0.3

    // This function is the external interface for the HSS processor. It takes
    // object and hand siteswaps and produces (among other things) a converted
    // pattern for the Siteswap component to animate.
    //
    // See SiteswapPattern.fromParameters().

    @Throws(JuggleExceptionUser::class)
    fun processHSS(
        p: String,
        h: String,
        hld: Boolean,
        dwlmax: Boolean,
        hndspc: String?,
        dwl: Double
    ): ModParms {
        val ossinfo: OssPatBnc = ossSyntax(p)
        val ossPat = ossinfo.objPat
        ossPermTest(ossPat, ossPat.size)
        
        val hssinfo = hssSyntax(h)
        val numHnd = hssinfo.hands
        val hssPat = hssinfo.pat
        val hssOrb = hssPermTest(hssPat, hssPat.size)

        val handmap = if (hndspc != null) {
            parseHandspec(hndspc, numHnd)
        } else {
            defHandspec(numHnd)
        }
        val numJug = max(1, handmap.maxOfOrNull { it[0] } ?: 1)

        val patinfo: PatParms =
            convertNotation(ossPat, hssPat, hssOrb, handmap, numJug, hld, dwlmax, dwl, ossinfo.bnc)
        if (patinfo.newPat == null) {
            throw JuggleExceptionUser(errorstrings.getString("Error_no_pattern"))
        }
        return ModParms().apply {
            convertedPattern = patinfo.newPat
            dwellBeatsArray = patinfo.dwellBt
        }
    }

    // Ensure object pattern is a vanilla pattern (multiplex allowed), and also
    // perform average test.
    //
    // Convert input pattern string to ArrayList identifying throws made on each beat.

    @Suppress("unused", "VariableNeverRead", "AssignedValueIsNeverRead")
    @Throws(JuggleExceptionUser::class)
    private fun ossSyntax(ss: String): OssPatBnc {
        var muxThrow = false
        var muxThrowFound = false
        var minOneThrow = false
        // at most three bounce characters are possible after siteswap number
        // b1, b2, b3 detect which'th bounce character may be expected as current character
        // only possible bounce strings are: B, BL, BF, BHL, BHF
        var b1 = false
        var b2 = false
        var b3 = false
        var throwSum = 0
        var numBeats = 0
        var subBeats = 0 // for multiplex throws
        var numObj = 0
        val oPat = ArrayList<ArrayList<Char?>>()
        val bncinfo = ArrayList<ArrayList<String?>>()

        for (i in 0..<ss.length) {
            val c = ss[i]
            if (muxThrow) {
                if (c.toString().matches("[0-9,a-z]".toRegex())) {
                    minOneThrow = true
                    muxThrowFound = true
                    oPat[numBeats - 1].add(subBeats, c)
                    bncinfo[numBeats - 1].add(subBeats, "null")
                    subBeats++
                    throwSum += Character.getNumericValue(c)
                    b1 = true
                    b2 = false
                    b3 = false
                    continue
                } else if (c == ']') {
                    if (muxThrowFound) {
                        muxThrow = false
                        muxThrowFound = false
                        subBeats = 0
                        b1 = false
                        b2 = false
                        b3 = false
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (Character.isWhitespace(c)) {
                    b1 = false
                    b2 = false
                    b3 = false
                    continue
                } else if (c == 'B') {
                    if (b1) {
                        bncinfo[numBeats - 1][subBeats - 1] = "B"
                        b1 = false
                        b2 = true
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (c == 'F' || c == 'L') {
                    if (b2) {
                        bncinfo[numBeats - 1][subBeats - 1] = "B$c"
                        b2 = false
                        continue
                    } else if (b3) {
                        bncinfo[numBeats - 1][subBeats - 1] = "BH$c"
                        b3 = false
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (c == 'H') {
                    if (b2) {
                        bncinfo[numBeats - 1][subBeats - 1] = "BH"
                        b2 = false
                        b3 = true
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else {
                    val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                    val arguments = arrayOf<Any?>(i + 1)
                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                }
            } else {
                if (c.toString().matches("[0-9,a-z]".toRegex())) {
                    minOneThrow = true
                    oPat.add(numBeats, ArrayList())
                    oPat[numBeats].add(subBeats, c)
                    bncinfo.add(numBeats, ArrayList())
                    bncinfo[numBeats].add(subBeats, "null")
                    numBeats++
                    throwSum += Character.getNumericValue(c)
                    b1 = true
                    b2 = false
                    b3 = false
                    continue
                } else if (c == '[') {
                    muxThrow = true
                    oPat.add(numBeats, ArrayList())
                    bncinfo.add(numBeats, ArrayList())
                    numBeats++
                    b1 = false
                    b2 = false
                    b3 = false
                    continue
                } else if (Character.isWhitespace(c)) {
                    b1 = false
                    b2 = false
                    b3 = false
                    continue
                } else if (c == 'B') {
                    if (b1) {
                        bncinfo[numBeats - 1][subBeats] = "B"
                        b1 = false
                        b2 = true
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (c == 'F' || c == 'L') {
                    if (b2) {
                        bncinfo[numBeats - 1][subBeats] = "B$c"
                        b2 = false
                        continue
                    } else if (b3) {
                        bncinfo[numBeats - 1][subBeats] = "BH$c"
                        b3 = false
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (c == 'H') {
                    if (b2) {
                        bncinfo[numBeats - 1][subBeats] = "BH"
                        b2 = false
                        b3 = true
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else {
                    val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                    val arguments = arrayOf<Any?>(i + 1)
                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                }
            } // if-else muxThrow
        } // for

        if (!muxThrow && minOneThrow) {
            if (throwSum % numBeats == 0) {
                numObj = throwSum / numBeats
            } else {
                throw JuggleExceptionUser(errorstrings.getString("Error_hss_bad_average_object"))
            }
        } else {
            throw JuggleExceptionUser(errorstrings.getString("Error_hss_syntax_error"))
        }
        // append a space after setting the bounceinfo arraylist. Eventually bouncinfo
        // is appended to iph so the space will get transferred there.
        // Reason for space:
        // The primary reason is that if a multiplex throw involving a pass is created
        // then the space will ensure the multiplex throw is written as, for example,
        // [5p3 4] as opposed to [5p34]. In the former, a 5 is to be passed to juggler 3
        // and simultaneously a multiplex 4 is to be thrown. In the latter, it'll get
        // interpreted as a 5 to be passed to juggler 34. In other cases, the space may
        // not matter. However, for coding convenience, the space is appended everywhere.
        for (strings in bncinfo) {
            for (j in strings.indices) {
                if (strings[j] == "null") {
                    strings[j] = " "
                } else {
                    strings[j] = strings[j] + " "
                }
            }
        }
        return OssPatBnc(oPat, bncinfo)
    }

    // Ensure hand pattern is a vanilla pattern (multiplex NOT allowed), and also
    // perform average test.
    //
    // convert input pattern string to ArrayList identifying throws made on each beat
    // also return number of hands, used later to build handmap

    @Throws(JuggleExceptionUser::class)
    private fun hssSyntax(ss: String): HssParms {
        var throwSum = 0
        var numBeats = 0
        val nHnds: Int
        val hPat = ArrayList<Char>()
        for (i in 0..<ss.length) {
            val c = ss[i]
            if (c.toString().matches("[0-9,a-z]".toRegex())) {
                hPat.add(numBeats, c)
                numBeats++
                throwSum += Character.getNumericValue(c)
                continue
            } else if (Character.isWhitespace(c)) {
                continue
            } else {
                val template = errorstrings.getString("Error_hss_hand_syntax_error_at_pos")
                val arguments = arrayOf<Any?>(i + 1)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }
        if (throwSum % numBeats == 0) {
            nHnds = throwSum / numBeats
        } else {
            throw JuggleExceptionUser(errorstrings.getString("Error_hss_bad_average_hand"))
        }

        return HssParms(hPat, nHnds)
    }

    // Do permutation test for object pattern.

    @Throws(JuggleExceptionUser::class)
    private fun ossPermTest(os: ArrayList<ArrayList<Char?>>, op: Int) {
        val mods = ArrayList<ArrayList<Int?>?>()
        var modulo: Int
        val cmp = IntArray(op)
        for (i in 0..<op) {
            mods.add(i, ArrayList())
            for (j in os[i].indices) {
                modulo = (Character.getNumericValue(os[i][j]!!) + i) % op
                mods[i]!!.add(j, modulo)
                cmp[modulo]++
            }
        }

        for (i in 0..<op) {
            if (cmp[i] != os[i].size) {
                throw JuggleExceptionUser(errorstrings.getString("Error_hss_object_pattern_invalid"))
            }
        }
    }

    // Do permutation test for hand pattern.
    //
    // Return overall hand orbit period which is lcm of individual hand orbit periods.

    @Throws(JuggleExceptionUser::class)
    private fun hssPermTest(hs: ArrayList<Char>, hp: Int): Int {
        var modulo: Int
        var ho: Int
        val mods = IntArray(hp)
        val cmp = IntArray(hp)
        val orb = IntArray(hp)
        val touched = BooleanArray(hp)
        for (i in 0..<hp) {
            modulo = (Character.getNumericValue(hs[i]) + i) % hp
            mods[i] = modulo
            cmp[modulo]++
        }

        for (i in 0..<hp) {
            if (cmp[i] != 1) {
                throw JuggleExceptionUser(errorstrings.getString("Error_hss_hand_pattern_invalid"))
            }
        }
        ho = 1
        for (i in 0..<hp) {
            if (!touched[i]) {
                orb[i] = Character.getNumericValue(hs[i])
                touched[i] = true
                var j = mods[i]
                while (j != i) {
                    orb[i] += Character.getNumericValue(hs[j])
                    touched[j] = true
                    j = mods[j]
                }
            }
            if (orb[i] != ho && orb[i] != 0) {
                ho = lcm(orb[i], ho)
            }
        }
        return ho
    }

    // Read and validate user defined handspec. If valid, convert to handmap assigning
    // juggler number and that juggler's left or right hand to each hand.

    @Throws(JuggleExceptionUser::class)
    private fun parseHandspec(hspec: String, nh: Int): Array<IntArray> {
        // handmap: map hand number to juggler number (first index) and
        // left/right hand (second index)
        val hmap = Array(nh) { IntArray(2) }
        var assignLeftHand = false
        var assignRightHand = false
        var jugglerActive = false
        var pass = false
        var handPresent = false
        var numberFormStarted = false
        val matchFound = false
        var jugglerNumber = 0
        var buildHandNumber: String? = null

        for (i in 0..<hspec.length) {
            val c = hspec[i]
            if (!jugglerActive) {  // if not in the middle of processing a () pair
                if (c == '(') {
                    jugglerActive = true  // juggler assignment for the current hand is now active
                    assignLeftHand = true  // at opening "(" assign left hand
                    numberFormStarted = true  // hand number might be a multiple digit number
                    buildHandNumber = null
                    pass = false
                    handPresent = false
                    jugglerNumber++
                    continue
                } else if (Character.isWhitespace(c)) {
                    continue
                } else {
                    val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                    val arguments = arrayOf<Any?>(i + 1)
                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                }
            } else {
                if (assignLeftHand) {
                    if (c.toString().matches("[0-9]".toRegex())) {
                        if (numberFormStarted) {
                            buildHandNumber = if (buildHandNumber == null) {
                                c.toString()
                            } else {
                                buildHandNumber + c
                            }
                            continue
                        } else {
                            val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                            val arguments = arrayOf<Any?>(i + 1)
                            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                        }
                    } else if (Character.isWhitespace(c)) {
                        if (buildHandNumber?.isNotEmpty() ?: false) {
                            numberFormStarted = false
                        }
                        continue
                    } else if (c == ',') {
                        assignLeftHand = false  // at "," left hand assignment complete
                        assignRightHand = true
                        numberFormStarted = true

                        if (buildHandNumber != null) {
                            if ((buildHandNumber.toInt() >= 1) && (buildHandNumber.toInt() <= nh)) {
                                if (hmap[buildHandNumber.toInt() - 1][0] == 0) {
                                    // if juggler not already assigned to this hand
                                    hmap[buildHandNumber.toInt() - 1][0] = jugglerNumber
                                    hmap[buildHandNumber.toInt() - 1][1] = 0
                                    handPresent = true
                                } else {
                                    val template =
                                        errorstrings.getString("Error_hss_hand_assigned_more_than_once")
                                    val arguments = arrayOf<Any?>(buildHandNumber.toInt())
                                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                                }
                            } else {
                                val template = errorstrings.getString("Error_hss_hand_number_out_of_range")
                                val arguments = arrayOf<Any?>(buildHandNumber.toInt())
                                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                            }
                        } else {
                            handPresent = false
                        }
                        buildHandNumber = null  // reset bhn string
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (assignRightHand) {
                    if (c.toString().matches("[0-9]".toRegex())) {
                        if (numberFormStarted) {
                            buildHandNumber = if (buildHandNumber == null) {
                                c.toString()
                            } else {
                                buildHandNumber + c
                            }
                            continue
                        } else {
                            val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                            val arguments = arrayOf<Any?>(i + 1)
                            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                        }
                    } else if (Character.isWhitespace(c)) {
                        if (buildHandNumber?.isNotEmpty()?: false) {
                            numberFormStarted = false
                        }
                        continue
                    } else if (c == ')') {
                        assignRightHand = false
                        jugglerActive = false  // juggler assignment is inactive after ")"

                        if (buildHandNumber != null) {
                            val buildHandNumberInt = buildHandNumber.toInt()
                            if (buildHandNumberInt in 1..nh) {
                                hmap[buildHandNumberInt - 1][0] = jugglerNumber
                                hmap[buildHandNumberInt - 1][1] = 1
                            } else {
                                val template = errorstrings.getString("Error_hss_hand_number_out_of_range")
                                val arguments = arrayOf<Any?>(buildHandNumber.toInt())
                                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                            }
                        } else if (!handPresent) {  // if left hand was also not present
                            throw JuggleExceptionUser(
                                errorstrings.getString("Error_hss_at_least_one_hand_per_juggler")
                            )
                        }
                        buildHandNumber = null  // reset bhn string
                        pass = true
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                }
            }
        }

        // check various error conditions
        if (jugglerNumber > nh) {  // will this ever happen?
            val template = errorstrings.getString("Error_hss_handspec_too_many_jugglers")
            val arguments = arrayOf<Any?>(nh)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        }
        if (pass) {
            for (i in 0..<nh) {  // what is this for?
                if (hmap[i][0] != 0) {
                    break
                }
                if (!matchFound) {
                    val template = errorstrings.getString("Error_hss_handspec_hand_missing")
                    val arguments = arrayOf<Any?>(i + 1)
                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                }
            }
        } else {
            throw JuggleExceptionUser(errorstrings.getString("Error_hss_handspec_syntax_error"))
        }
        for (i in 0..<nh) {
            if (hmap[i][0] == 0) {
                val template = errorstrings.getString("Error_hss_juggler_not_assigned_to_hand")
                val arguments = arrayOf<Any?>(i + 1)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }

        return hmap
    }

    // Build a default handmap in the absence of user defined handspec.
    //
    // assume numHnd/2 jugglers if numHnd even, else (numHnd+1)/2 jugglers
    // assign hand 1 to J1 right hand, hand 2 to J2 right hand and so on
    // once all right hands assigned, come back to J1 and start assigning left hand.

    private fun defHandspec(nh: Int): Array<IntArray> {
        val hmap = Array(nh) { IntArray(2) }
        val nJugs: Int = if (nh % 2 == 0) {
            nh / 2
        } else {
            (nh + 1) / 2
        }

        for (i in 0..<nh) {
            if (i < nJugs) {
                hmap[i][0] = i + 1  // juggler number
                hmap[i][1] = 1  // 0 for left hand, 1 for right
            } else {
                hmap[i][0] = i + 1 - nJugs
                hmap[i][1] = 0
            }
        }
        return hmap
    }

    // Convert oss hss format to Juggling Lab synchronous passing notation with
    // suppressed empty beats so that odd synchronous throws are also allowed.

    @Throws(JuggleExceptionUser::class)
    private fun convertNotation(
        os: ArrayList<ArrayList<Char?>>,
        hs: ArrayList<Char>,
        ho: Int,
        hm: Array<IntArray>,
        nj: Int,
        hldOpt: Boolean,
        dwlMaxOpt: Boolean,
        defDwl: Double,
        bncStr: ArrayList<ArrayList<String?>>
    ): PatParms {
        var throwVal: Int
        var currJug: Int
        var modPat: String? = null // modified pattern
        var flag: Boolean

        // invert, pass and hold for x, p and H
        val iph = ArrayList<ArrayList<String?>>()

        val objPer: Int = os.size
        val hndPer: Int = hs.size
        val patPer = lcm(objPer, ho) // pattern period

        val ah = IntArray(patPer) // assigned hand
        val assignDone = BooleanArray(patPer)
        val ji = Array(patPer) { IntArray(2) }  // jugglerInfo: juggler#, hand#
        val dwlBts = DoubleArray(patPer) // dwell beats

        // extend oss size to pp
        if (patPer > objPer) {
            for (i in objPer..<patPer) {
                os.add(i, os[i - objPer])
            }
        }

        // extend bounce size to pp
        if (patPer > objPer) {
            for (i in objPer..<patPer) {
                bncStr.add(i, bncStr[i - objPer])
            }
        }

        // extend hss size to pp
        if (patPer > hndPer) {
            for (i in hndPer..<patPer) {
                hs.add(i, hs[i - hndPer])
            }
        }

        // check if hss is 0 when oss is not 0; else assign juggler and hand to each beat
        var currHand = 0
        for (i in 0..<patPer) {
            if (hs[i] == '0') {
                for (j in os[i].indices) {
                    if (os[i][j] != '0') {
                        val template = errorstrings.getString("Error_hss_no_hand_to_throw_at_beat")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                }
                ji[i][0] = 0 // assign juggler number 0 for no hand
                ji[i][1] = -1 // assign hand number -1 for no hand
                assignDone[i] = true
            } else {
                if (!assignDone[i]) {
                    currHand++
                    ah[i] = currHand
                    assignDone[i] = true
                    var next = (i + Character.getNumericValue(hs[i])) % patPer
                    while (next != i) {
                        ah[next] = currHand
                        assignDone[next] = true
                        next = (next + Character.getNumericValue(hs[next])) % patPer
                    }
                }
                ji[i][0] = hm[ah[i] - 1][0] // juggler number at beat i based on handmap
                ji[i][1] = hm[ah[i] - 1][1] // throwing hand at beat i based on handmap
            }
        }

        // determine dwellbeats array
        flag = false
        val mincaught = IntArray(patPer)
        var tgtIdx: Int // target index
        var curThrow: Int // current throw

        // find minimum throw being caught at each beat: more than one throw could be
        // getting caught in case of multiplex throw. Dwell time on that beat will be
        // maximized for the minimum throw being caught. Higher throws may thus show
        // more flight time than is strictly required going by hand availability.
        for (i in 0..<patPer) {
            for (j in os[i].indices) {
                curThrow = Character.getNumericValue(os[i][j]!!)
                tgtIdx = (i + curThrow) % patPer
                if (curThrow > 0) {
                    if (mincaught[tgtIdx] == 0) {
                        mincaught[tgtIdx] = curThrow
                    } else if (curThrow < mincaught[tgtIdx]) {
                        mincaught[tgtIdx] = curThrow
                    }
                }
            }
        }
        if (!dwlMaxOpt) {
            for (i in 0..<patPer) {
                if ((ji[i][0] == ji[(i + 1) % patPer][0]) && (ji[i][1] == ji[(i + 1) % patPer][1])) {
                    flag = true // if same hand throws on successive beats
                    break
                }
            }
            for (i in 0..<patPer) {
                dwlBts[i] = if (flag) hss_dwell_default else defDwl
                if (dwlBts[i] >= mincaught[i].toDouble()) {
                    dwlBts[i] = mincaught[i].toDouble() - (1 - hss_dwell_default)
                }
            }
        } else {  // if dwellmax is true
            for (i in 0..<patPer) {
                var j = (i + 1) % patPer
                var diff = 1
                while ((ji[i][0] != ji[j][0]) || (ji[i][1] != ji[j][1])) {
                    j = (j + 1) % patPer
                    diff++
                }
                dwlBts[j] = diff.toDouble() - (1 - hss_dwell_default)
            }
            for (i in 0..<patPer) {
                if (dwlBts[i] >= mincaught[i].toDouble()) {
                    dwlBts[i] = mincaught[i].toDouble() - (1 - hss_dwell_default)
                } else if (dwlBts[i] <= 0) {
                    dwlBts[i] = hss_dwell_default
                }
            }
        }

        // remove clashes in db array in case dwell times from different beats get
        // optimized to same time instant
        val clash = BooleanArray(patPer)
        var clashcnt = 0
        for (i in 0..<patPer) {
            for (j in 1..<patPer) {
                if ((dwlBts[(i + j) % patPer] - dwlBts[i] - j) % patPer == 0.0) {
                    clash[(i + j) % patPer] = true
                    clashcnt++
                }
            }
            while (clashcnt != 0) {
                for (k in 0..<patPer) {
                    if (clash[k]) {
                        dwlBts[k] = dwlBts[k] + hss_dwell_default / clashcnt
                        clashcnt--
                        clash[k] = false
                    }
                }
            }
        }

        // determine x, p and H throws
        for (i in 0..<patPer) {
            iph.add(i, ArrayList())
            for (j in os[i].indices) {
                iph[i].add(j, null)
                throwVal = Character.getNumericValue(os[i][j]!!)

                val sourceJug = ji[i][0]
                val sourceHnd = ji[i][1]
                val targetJug = ji[(i + throwVal) % patPer][0]
                val targetHnd = ji[(i + throwVal) % patPer][1]

                if (throwVal % 2 == 0 && sourceHnd != targetHnd) {
                    iph[i][j] = "x" // put x for even throws to other hand
                } else if (throwVal % 2 != 0 && sourceHnd == targetHnd) {
                    iph[i][j] = "x" // put x for odd throws to same hand
                }
                if (sourceJug != targetJug) {
                    if (iph[i][j] !== "x") {
                        iph[i][j] = "p$targetJug"
                    } else {
                        iph[i][j] = "xp$targetJug"
                    }
                } else if (hldOpt) {
                    if (throwVal == Character.getNumericValue(hs[i])) {
                        if (iph[i][j] !== "x") {
                            iph[i][j] = "H" // enable hold for even throw to same hand
                        } else {
                            iph[i][j] = "xH" // enable hold for odd throw to same hand
                        }
                    }
                }
            }
        }

        for (i in 0..<patPer) {
            for (j in bncStr[i].indices) {
                if (iph[i][j] == null) {
                    iph[i][j] = bncStr[i][j]
                } else {
                    iph[i][j] = iph[i][j] + bncStr[i][j]
                }
            }
        }

        // construct the pattern string
        for (i in 0..<patPer) {
            currJug = 0
            while (currJug < nj) {
                if (modPat == null) {  // at the start of building the converted pattern
                    modPat = "<"
                } else if (currJug == 0) {  // at the start of a new beat
                    modPat += "<"
                }

                if (ji[i][1] == 0) {  // if left hand is throwing at current beat
                    modPat += "("
                    if (ji[i][0] == currJug + 1) {  // if currentjuggler is throwing at current beat
                        if (os[i].size > 1) {  // if it is a multiplex throw
                            modPat += "["
                            for (j in os[i].indices) {
                                modPat += os[i][j]
                                if (iph[i][j] != null) {
                                    modPat += iph[i][j]
                                }
                            }
                            modPat += "]"
                        } else { // if not multiplex throw
                            modPat += os[i].first()
                            if (iph[i].first() != null) {
                                modPat += iph[i].first()
                            }
                        }
                        modPat += ",0)!" // no sync throws allowed, put 0 for right hand
                    } else {  // if current juggler is not throwing at this beat
                        modPat += "0,0)!"
                    }
                } else {  // if right hand is throwing at this beat
                    modPat += "(0," // no sync throws allowed, put 0 for left hand
                    if (ji[i][0] == currJug + 1) {  // if currentjuggler is throwing at current beat
                        if (os[i].size > 1) {  // if it is a multiplex throw
                            modPat += "["
                            for (j in os[i].indices) {
                                modPat += os[i][j]
                                if (iph[i][j] != null) {
                                    modPat += iph[i][j]
                                }
                            }
                            modPat += "]"
                        } else {  // if not multiplex throw
                            modPat += os[i].first()
                            if (iph[i].first() != null) {
                                modPat += iph[i].first()
                            }
                        }
                        modPat += ")!"
                    } else {  // if current juggler is not throwing at this beat
                        modPat += "0)!"
                    }
                } // if-else left-right hand

                modPat = if (currJug == nj - 1) {
                    "$modPat>"
                } else {
                    "$modPat|"
                }
                currJug++
            }  // while currJug < nj
        }  // for all beats

        return PatParms(modPat, dwlBts)
    }

    //--------------------------------------------------------------------------
    // Types related to HSS
    //--------------------------------------------------------------------------

    class OssPatBnc(
        val objPat: ArrayList<ArrayList<Char?>>,
        val bnc: ArrayList<ArrayList<String?>>
    )

    class HssParms(
        var pat: ArrayList<Char>,
        var hands: Int
    )

    class PatParms(
        var newPat: String?,
        var dwellBt: DoubleArray
    )

    class ModParms {
        var convertedPattern: String? = null
        var dwellBeatsArray: DoubleArray? = null
    }
}
