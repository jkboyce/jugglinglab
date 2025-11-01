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
import java.util.*

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
        val ossPer: Int
        val hssPer: Int
        val hssOrb: Int
        val numHnd: Int
        var numJug: Int
        val modinf = ModParms()

        val ossPat: ArrayList<ArrayList<Char?>?>
        val hssPat: ArrayList<Char?>
        val bounc: ArrayList<ArrayList<String?>>
        val hssinfo: HssParms?
        val patinfo: PatParms?
        val ossinfo: OssPatBnc?

        ossinfo = ossSyntax(p)
        ossPat = ossinfo.objPat!!
        bounc = ossinfo.bnc!!

        hssinfo = hssSyntax(h)

        hssPat = hssinfo.pat!!
        numHnd = hssinfo.hands
        ossPer = ossPat.size
        hssPer = hssPat.size

        ossPermTest(ossPat, ossPer)

        hssOrb = hssPermTest(hssPat, hssPer)

        val handmap = if (hndspc != null) parseHandspec(hndspc, numHnd) else defHandspec(numHnd)

        numJug = 1
        for (i in 0..<numHnd) {
            if (handmap[i]!![0] > numJug) {
                numJug = handmap[i]!![0]
            }
        }

        patinfo = convNotation(ossPat, hssPat, hssOrb, handmap, numJug, hld, dwlmax, dwl, bounc)
        modinf.convertedPattern = patinfo.newPat
        if (modinf.convertedPattern == null) {
            throw JuggleExceptionUser(errorstrings.getString("Error_no_pattern"))
        }

        modinf.dwellBeatsArray = patinfo.dwellBt
        return modinf
    }

    // Ensure object pattern is a vanilla pattern (multiplex allowed), and also
    // perform average test.
    //
    // convert input pattern string to ArrayList identifying throws made on each beat

    @Suppress("unused")
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
        val oPat = ArrayList<ArrayList<Char?>?>()
        val bncinfo = ArrayList<ArrayList<String?>>()

        for (i in 0..<ss.length) {
            val c = ss.get(i)
            if (muxThrow) {
                if (c.toString().matches("[0-9,a-z]".toRegex())) {
                    minOneThrow = true
                    muxThrowFound = true
                    oPat.get(numBeats - 1)!!.add(subBeats, c)
                    bncinfo.get(numBeats - 1).add(subBeats, "null")
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
                        bncinfo.get(numBeats - 1).set(subBeats - 1, "B")
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
                        bncinfo.get(numBeats - 1).set(subBeats - 1, "B" + c)
                        b2 = false
                        continue
                    } else if (b3) {
                        bncinfo.get(numBeats - 1).set(subBeats - 1, "BH" + c)
                        b3 = false
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (c == 'H') {
                    if (b2) {
                        bncinfo.get(numBeats - 1).set(subBeats - 1, "BH")
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
                    oPat.add(numBeats, ArrayList<Char?>())
                    oPat.get(numBeats)!!.add(subBeats, c)
                    bncinfo.add(numBeats, ArrayList<String?>())
                    bncinfo.get(numBeats).add(subBeats, "null")
                    numBeats++
                    throwSum += Character.getNumericValue(c)
                    b1 = true
                    b2 = false
                    b3 = false
                    continue
                } else if (c == '[') {
                    muxThrow = true
                    oPat.add(numBeats, ArrayList<Char?>())
                    bncinfo.add(numBeats, ArrayList<String?>())
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
                        bncinfo.get(numBeats - 1).set(subBeats, "B")
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
                        bncinfo.get(numBeats - 1).set(subBeats, "B" + c)
                        b2 = false
                        continue
                    } else if (b3) {
                        bncinfo.get(numBeats - 1).set(subBeats, "BH" + c)
                        b3 = false
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_object_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (c == 'H') {
                    if (b2) {
                        bncinfo.get(numBeats - 1).set(subBeats, "BH")
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
                if (strings.get(j) == "null") {
                    strings.set(j, " ")
                } else {
                    strings.set(j, strings.get(j) + " ")
                }
            }
        }
        val ossinf = OssPatBnc()
        ossinf.objPat = oPat
        ossinf.bnc = bncinfo

        return ossinf
    } // OssSyntax

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
        val hPat = ArrayList<Char?>()
        for (i in 0..<ss.length) {
            val c = ss.get(i)
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

        val hssinf = HssParms()
        hssinf.pat = hPat
        hssinf.hands = nHnds

        return hssinf
    }

    // Do permutation test for object pattern.
    @Throws(JuggleExceptionUser::class)
    private fun ossPermTest(os: ArrayList<ArrayList<Char?>?>, op: Int) {
        val mods = ArrayList<ArrayList<Int?>?>()
        var modulo: Int
        val cmp = IntArray(op)
        for (i in 0..<op) {
            mods.add(i, ArrayList<Int?>())
            for (j in os.get(i)!!.indices) {
                modulo = (Character.getNumericValue(os.get(i)!!.get(j)!!) + i) % op
                mods.get(i)!!.add(j, modulo)
                cmp[modulo]++
            }
        }

        for (i in 0..<op) {
            if (cmp[i] != os.get(i)!!.size) {
                throw JuggleExceptionUser(errorstrings.getString("Error_hss_object_pattern_invalid"))
            }
        }
    }

    // Do permutation test for hand pattern.
    //
    // Return overall hand orbit period which is lcm of individual hand orbit periods.
    @Throws(JuggleExceptionUser::class)
    private fun hssPermTest(hs: ArrayList<Char?>, hp: Int): Int {
        var modulo: Int
        var ho: Int
        val mods = IntArray(hp)
        val cmp = IntArray(hp)
        val orb = IntArray(hp)
        val touched = BooleanArray(hp)
        for (i in 0..<hp) {
            modulo = (Character.getNumericValue(hs.get(i)!!) + i) % hp
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
                orb[i] = Character.getNumericValue(hs.get(i)!!)
                touched[i] = true
                var j = mods[i]
                while (j != i) {
                    orb[i] += Character.getNumericValue(hs.get(j)!!)
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
    private fun parseHandspec(hspec: String, nh: Int): Array<IntArray?> {
        // handmap: map hand number to juggler number (first index) and left/right hand
        // (second index)
        val hmap = Array<IntArray?>(nh) { IntArray(2) }
        var assignLH = false // assignLeftHand
        var assignRH = false // assignRightHand
        var jugAct = false // jugglerActive
        var pass = false
        var handPresent = false
        var numFormStart = false // numberformationStarted
        var matchFnd = false // matchFound
        var jugNum = 0 // jugglerNumber
        var buildHndNum: String? = null // buildHandNumber

        for (i in 0..<hspec.length) {
            val c = hspec.get(i)
            if (!jugAct) {  // if not in the middle of processing a () pair
                if (c == '(') {
                    jugAct = true // juggler assignment for the current hand is now active
                    assignLH = true // at opening "(" assign left hand
                    numFormStart = true // hand number might be a multiple digit number
                    buildHndNum = null
                    pass = false
                    handPresent = false
                    jugNum++
                    continue
                } else if (Character.isWhitespace(c)) {
                    continue
                } else {
                    val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                    val arguments = arrayOf<Any?>(i + 1)
                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                }
            } else {
                if (assignLH) {
                    if (c.toString().matches("[0-9]".toRegex())) {
                        if (numFormStart) {
                            if (buildHndNum == null) {
                                buildHndNum = c.toString()
                            } else {
                                buildHndNum = buildHndNum + c
                            }
                            continue
                        } else {
                            val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                            val arguments = arrayOf<Any?>(i + 1)
                            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                        }
                    } else if (Character.isWhitespace(c)) {
                        if (buildHndNum != null && !buildHndNum.isEmpty()) {
                            numFormStart = false
                        }
                        continue
                    } else if (c == ',') {
                        assignLH = false // at "," left hand assignment complete
                        assignRH = true
                        numFormStart = true

                        if (buildHndNum != null) {
                            if ((buildHndNum.toInt() >= 1) && (buildHndNum.toInt() <= nh)) {
                                if (hmap[buildHndNum.toInt() - 1]!![0] == 0) {
                                    // if juggler not already assigned to this hand
                                    hmap[buildHndNum.toInt() - 1]!![0] = jugNum
                                    hmap[buildHndNum.toInt() - 1]!![1] = 0
                                    handPresent = true
                                } else {
                                    val template =
                                        errorstrings.getString("Error_hss_hand_assigned_more_than_once")
                                    val arguments = arrayOf<Any?>(buildHndNum.toInt())
                                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                                }
                            } else {
                                val template = errorstrings.getString("Error_hss_hand_number_out_of_range")
                                val arguments = arrayOf<Any?>(buildHndNum.toInt())
                                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                            }
                        } else {
                            handPresent = false
                        }
                        buildHndNum = null // reset bhn string
                        continue
                    } else {
                        val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                } else if (assignRH) {
                    if (c.toString().matches("[0-9]".toRegex())) {
                        if (numFormStart) {
                            if (buildHndNum == null) {
                                buildHndNum = c.toString()
                            } else {
                                buildHndNum = buildHndNum + c
                            }
                            continue
                        } else {
                            val template = errorstrings.getString("Error_hss_handspec_syntax_error_at_pos")
                            val arguments = arrayOf<Any?>(i + 1)
                            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                        }
                    } else if (Character.isWhitespace(c)) {
                        if (buildHndNum != null && !buildHndNum.isEmpty()) {
                            numFormStart = false
                        }
                        continue
                    } else if (c == ')') {
                        assignRH = false
                        jugAct = false // juggler assignment is inactive after ")"

                        if (buildHndNum != null) {
                            if ((buildHndNum.toInt() >= 1) && (buildHndNum.toInt() <= nh)) {
                                hmap[buildHndNum.toInt() - 1]!![0] = jugNum
                                hmap[buildHndNum.toInt() - 1]!![1] = 1
                            } else {
                                val template = errorstrings.getString("Error_hss_hand_number_out_of_range")
                                val arguments = arrayOf<Any?>(buildHndNum.toInt())
                                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                            }
                        } else if (!handPresent) {  // if left hand was also not present
                            throw JuggleExceptionUser(
                                errorstrings.getString("Error_hss_at_least_one_hand_per_juggler")
                            )
                        }
                        buildHndNum = null // reset bhn string
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
        if (jugNum > nh) {  // will this ever happen?
            val template = errorstrings.getString("Error_hss_handspec_too_many_jugglers")
            val arguments = arrayOf<Any?>(nh)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        }
        if (pass) {
            for (i in 0..<nh) {  // what is this for?
                if (hmap[i]!![0] != 0) {
                    matchFnd = true
                    break
                }
                if (!matchFnd) {
                    val template = errorstrings.getString("Error_hss_handspec_hand_missing")
                    val arguments = arrayOf<Any?>(i + 1)
                    throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                }
            }
        } else {
            throw JuggleExceptionUser(errorstrings.getString("Error_hss_handspec_syntax_error"))
        }

        for (i in 0..<nh) {
            if (hmap[i]!![0] == 0) {
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
    private fun defHandspec(nh: Int): Array<IntArray?> {
        val hmap = Array<IntArray?>(nh) { IntArray(2) }
        val nJugs: Int // numberofJugglers

        if (nh % 2 == 0) {
            nJugs = nh / 2
        } else {
            nJugs = (nh + 1) / 2
        }

        for (i in 0..<nh) {
            if (i < nJugs) {
                hmap[i]!![0] = i + 1 // juggler number
                hmap[i]!![1] = 1 // 0 for left hand, 1 for right
            } else {
                hmap[i]!![0] = i + 1 - nJugs
                hmap[i]!![1] = 0
            }
        }

        return hmap
    }

    // Convert oss hss format to Juggling Lab synchronous passing notation with suppressed
    // empty beats so that odd synchronous throws are also allowed.
    @Throws(JuggleExceptionUser::class)
    private fun convNotation(
        os: ArrayList<ArrayList<Char?>?>,
        hs: ArrayList<Char?>,
        ho: Int,
        hm: Array<IntArray?>,
        nj: Int,
        hldOpt: Boolean,
        dwlMaxOpt: Boolean,
        defDwl: Double,
        bncStr: ArrayList<ArrayList<String?>>
    ): PatParms {
        // pattern period, current hand, throw value, current juggler, ossPeriod, hssPeriod
        val patPer: Int
        var currHand: Int
        var throwVal: Int
        var currJug: Int
        val objPer: Int
        val hndPer: Int
        var modPat: String? = null // modified pattern
        val patinf = PatParms()
        var flag = false

        // invert, pass and hold for x, p and H
        val iph = ArrayList<ArrayList<String?>?>()

        objPer = os.size
        hndPer = hs.size
        patPer = lcm(objPer, ho) // pattern period

        val ah = IntArray(patPer) // assigned hand
        val assignDone = BooleanArray(patPer)
        val ji = Array<IntArray?>(patPer) { IntArray(2) }  // jugglerInfo: juggler#, hand#
        val dwlBts = DoubleArray(patPer) // dwell beats

        // extend oss size to pp
        if (patPer > objPer) {
            for (i in objPer..<patPer) {
                os.add(i, os.get(i - objPer))
            }
        }

        // extend bounce size to pp
        if (patPer > objPer) {
            for (i in objPer..<patPer) {
                bncStr.add(i, bncStr.get(i - objPer))
            }
        }

        // extend hss size to pp
        if (patPer > hndPer) {
            for (i in hndPer..<patPer) {
                hs.add(i, hs.get(i - hndPer))
            }
        }

        // check if hss is 0 when oss is not 0; else assign juggler and hand to each beat
        currHand = 0
        for (i in 0..<patPer) {
            if (hs.get(i) == '0') {
                for (j in os.get(i)!!.indices) {
                    if (os.get(i)!!.get(j) != '0') {
                        val template = errorstrings.getString("Error_hss_no_hand_to_throw_at_beat")
                        val arguments = arrayOf<Any?>(i + 1)
                        throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
                    }
                }
                ji[i]!![0] = 0 // assign juggler number 0 for no hand
                ji[i]!![1] = -1 // assign hand number -1 for no hand
                assignDone[i] = true
            } else {
                if (!assignDone[i]) {
                    currHand++
                    ah[i] = currHand
                    assignDone[i] = true
                    var next = (i + Character.getNumericValue(hs.get(i)!!)) % patPer
                    while (next != i) {
                        ah[next] = currHand
                        assignDone[next] = true
                        next = (next + Character.getNumericValue(hs.get(next)!!)) % patPer
                    }
                }
                ji[i]!![0] = hm[ah[i] - 1]!![0] // juggler number at beat i based on handmap
                ji[i]!![1] = hm[ah[i] - 1]!![1] // throwing hand at beat i based on handmap
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
            for (j in os.get(i)!!.indices) {
                curThrow = Character.getNumericValue(os.get(i)!!.get(j)!!)
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
                if ((ji[i]!![0] == ji[(i + 1) % patPer]!![0]) && (ji[i]!![1] == ji[(i + 1) % patPer]!![1])) {
                    flag = true // if same hand throws on successive beats
                    break
                }
            }
            if (flag) {
                for (i in 0..<patPer) {
                    dwlBts[i] = hss_dwell_default
                }
            } else {
                for (i in 0..<patPer) {
                    dwlBts[i] = defDwl // user defined default dwell in front panel
                }
            }
            for (i in 0..<patPer) {
                if (dwlBts[i] >= mincaught[i].toDouble()) {
                    dwlBts[i] = mincaught[i].toDouble() - (1 - hss_dwell_default)
                }
            }
        } else {  // if dwellmax is true
            for (i in 0..<patPer) {
                var j = (i + 1) % patPer
                var diff = 1
                while ((ji[i]!![0] != ji[j]!![0]) || (ji[i]!![1] != ji[j]!![1])) {
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

        patinf.dwellBt = dwlBts

        // determine x, p and H throws
        for (i in 0..<patPer) {
            iph.add(i, ArrayList<String?>())
            for (j in os.get(i)!!.indices) {
                iph.get(i)!!.add(j, null)
                throwVal = Character.getNumericValue(os.get(i)!!.get(j)!!)

                val sourceJug = ji[i]!![0]
                val sourceHnd = ji[i]!![1]
                val targetJug = ji[(i + throwVal) % patPer]!![0]
                val targetHnd = ji[(i + throwVal) % patPer]!![1]

                if (throwVal % 2 == 0 && sourceHnd != targetHnd) {
                    iph.get(i)!!.set(j, "x") // put x for even throws to other hand
                } else if (throwVal % 2 != 0 && sourceHnd == targetHnd) {
                    iph.get(i)!!.set(j, "x") // put x for odd throws to same hand
                }
                if (sourceJug != targetJug) {
                    if (iph.get(i)!!.get(j) !== "x") {
                        iph.get(i)!!.set(j, "p" + targetJug)
                    } else {
                        iph.get(i)!!.set(j, "xp" + targetJug)
                    }
                } else if (hldOpt) {
                    if (throwVal == Character.getNumericValue(hs.get(i)!!)) {
                        if (iph.get(i)!!.get(j) !== "x") {
                            iph.get(i)!!.set(j, "H") // enable hold for even throw to same hand
                        } else {
                            iph.get(i)!!.set(j, "xH") // enable hold for odd throw to same hand
                        }
                    }
                }
            }
        }

        for (i in 0..<patPer) {
            for (j in bncStr.get(i).indices) {
                if (iph.get(i)!!.get(j) == null) {
                    iph.get(i)!!.set(j, bncStr.get(i).get(j))
                } else {
                    iph.get(i)!!.set(j, iph.get(i)!!.get(j) + bncStr.get(i).get(j))
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
                    modPat = modPat + "<"
                }

                if (ji[i]!![1] == 0) {  // if left hand is throwing at current beat
                    modPat = modPat + "("
                    if (ji[i]!![0] == currJug + 1) {  // if currentjuggler is throwing at current beat
                        if (os.get(i)!!.size > 1) {  // if it is a multiplex throw
                            modPat = modPat + "["
                            for (j in os.get(i)!!.indices) {
                                modPat = modPat + os.get(i)!!.get(j)
                                if (iph.get(i)!!.get(j) != null) {
                                    modPat = modPat + iph.get(i)!!.get(j)
                                }
                            }
                            modPat = modPat + "]"
                        } else { // if not multiplex throw
                            modPat = modPat + os.get(i)!!.getFirst()
                            if (iph.get(i)!!.getFirst() != null) {
                                modPat = modPat + iph.get(i)!!.getFirst()
                            }
                        }
                        modPat = modPat + ",0)!" // no sync throws allowed, put 0 for right hand
                    } else {  // if current juggler is not throwing at this beat
                        modPat = modPat + "0,0)!"
                    }
                } else {  // if right hand is throwing at this beat
                    modPat = modPat + "(0," // no sync throws allowed, put 0 for left hand
                    if (ji[i]!![0] == currJug + 1) {  // if currentjuggler is throwing at current beat
                        if (os.get(i)!!.size > 1) {  // if it is a multiplex throw
                            modPat = modPat + "["
                            for (j in os.get(i)!!.indices) {
                                modPat = modPat + os.get(i)!!.get(j)
                                if (iph.get(i)!!.get(j) != null) {
                                    modPat = modPat + iph.get(i)!!.get(j)
                                }
                            }
                            modPat = modPat + "]"
                        } else {  // if not multiplex throw
                            modPat = modPat + os.get(i)!!.getFirst()
                            if (iph.get(i)!!.getFirst() != null) {
                                modPat = modPat + iph.get(i)!!.getFirst()
                            }
                        }
                        modPat = modPat + ")!"
                    } else {  // if current juggler is not throwing at this beat
                        modPat = modPat + "0)!"
                    }
                } // if-else left-right hand


                if (currJug == nj - 1) {
                    modPat = modPat + ">"
                } else {
                    modPat = modPat + "|"
                }
                currJug++
            } // while currJug < nj
        } // for all beats


        patinf.newPat = modPat
        return patinf
    } // convNotation

    //--------------------------------------------------------------------------
    // Types related to HSS
    //--------------------------------------------------------------------------

    class OssPatBnc {
        var objPat: ArrayList<ArrayList<Char?>?>? = null
        var bnc: ArrayList<ArrayList<String?>>? = null
    }

    class HssParms {
        var pat: ArrayList<Char?>? = null
        var hands: Int = 0
    }

    class PatParms {
        var newPat: String? = null
        var dwellBt: DoubleArray? = null
    }

    class ModParms {
        var convertedPattern: String? = null
        var dwellBeatsArray: DoubleArray? = null
    }
}
