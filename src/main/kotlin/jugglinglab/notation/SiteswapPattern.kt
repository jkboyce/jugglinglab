//
// SiteswapPattern.kt
//
// This class represents a pattern in the generalized form of siteswap notation
// used by Juggling Lab. The real work here is to parse siteswap notation into
// the internal format used by MHNPattern.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.notation

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.core.Constants
import jugglinglab.notation.ssparser.ParseException
import jugglinglab.notation.ssparser.SiteswapParser
import jugglinglab.notation.ssparser.SiteswapTreeItem
import jugglinglab.notation.ssparser.TokenMgrError
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList
import jugglinglab.util.Permutation.Companion.lcm
import java.text.MessageFormat

class SiteswapPattern : MHNPattern() {
    private var oddperiod: Boolean = false
    var hasHandsSpecifier: Boolean = false
        private set
    // async throws on even beat numbers made with right hand?
    private lateinit var rightOnEven: BooleanArray

    override val notationName = "Siteswap"

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun fromString(config: String): SiteswapPattern {
        var conf = config
        if (conf.indexOf('=') == -1) {
            // just the pattern
            conf = "pattern=$conf"
        }

        val pl = ParameterList(conf)
        fromParameters(pl)
        pl.errorIfParametersLeft()
        return this
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    override fun fromParameters(pl: ParameterList): SiteswapPattern {
        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("Starting siteswap parser...")
        }

        super.fromParameters(pl)

        if (hss != null) {
            val modinfo = HSS.processHSS(pattern, hss, hold, dwellmax, handspec, dwell)
            pattern = modinfo.convertedPattern
            dwellarray = modinfo.dwellBeatsArray
        }

        // pattern = JLFunc.expandRepeats(pattern);
        parseSiteswapNotation()

        // see if we need to repeat the pattern to match hand or body periods:
        if (hands != null || bodies != null) {
            val patperiod = norepPeriod

            var handperiod = 1
            if (hands != null) {
                for (i in 1..numberOfJugglers) {
                    handperiod = lcm(handperiod, hands!!.getPeriod(i))
                }
            }

            var bodyperiod = 1
            if (bodies != null) {
                for (i in 1..numberOfJugglers) {
                    bodyperiod = lcm(bodyperiod, bodies!!.getPeriod(i))
                }
            }

            var totalperiod = patperiod
            totalperiod = lcm(totalperiod, handperiod)
            totalperiod = lcm(totalperiod, bodyperiod)

            if (totalperiod != patperiod) {
                val repeats = totalperiod / patperiod
                pattern = "($pattern^$repeats)"
                // pattern = "(" + pattern + ")^" + repeats;
                // pattern = JLFunc.expandRepeats(pattern);
                if (Constants.DEBUG_SITESWAP_PARSING) {
                    println("-----------------------------------------------------")
                    println("Repeating pattern to match hand/body period, restarting\n")
                }
                parseSiteswapNotation()
            }
        }

        super.buildJugglingMatrix()

        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("Siteswap parser finished")
            println("-----------------------------------------------------")
        }

        return this
    }

    // Only works after parseSiteswapNotation() is called
    private val norepPeriod: Int
        get() = if (oddperiod) period / 2 else period

    //--------------------------------------------------------------------------
    // Parse siteswap notation into the MHNPattern data structures
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun parseSiteswapNotation() {
        // first clear out the internal variables
        symmetries = ArrayList()
        val tree: SiteswapTreeItem?

        try {
            if (Constants.DEBUG_SITESWAP_PARSING) {
                println("Parsing pattern \"$pattern\"")
            }
            tree = SiteswapParser.parsePattern(pattern)
            if (Constants.DEBUG_SITESWAP_PARSING) {
                println("Parse tree:\n")
                println(tree)
            }
        } catch (pe: ParseException) {
            if (Constants.DEBUG_SITESWAP_PARSING) {
                println("---------------")
                println("Parse error:")
                println(pe.message)
                println(pe.currentToken)
                println("---------------")
            }

            if (pe.currentToken == null) {
                val template = errorstrings.getString("Error_pattern_parsing")
                val arguments = arrayOf<Any?>(pe.message)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            } else {
                val template = errorstrings.getString("Error_pattern_syntax")
                val problem = ParseException.add_escapes(pe.currentToken.next.image)
                val arguments = arrayOf<Any?>(problem, pe.currentToken.next.beginColumn)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        } catch (tme: TokenMgrError) {
            val template = errorstrings.getString("Error_pattern_syntax")
            val problem = TokenMgrError.addEscapes(tme.curChar.toString())
            val arguments = arrayOf<Any?>(problem, tme.errorColumn - 1)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        }

        // Use tree to fill in MHNPattern internal variables:
        /*
        protected int numjugglers;
        protected int numpaths;
        protected int period;
        protected int max_occupancy;
        protected MHNThrow[][][][] th;
        protected MHNHands hands;
        protected MHNBody bodies;
        protected int max_throw;
        protected int indexes;
        protected ArrayList<MHNSymmetry> symmetry;
        */
        numberOfJugglers = tree.jugglers
        maxOccupancy = 0 // calculated in doFirstPass()
        maxThrow = 0
        rightOnEven = BooleanArray(numberOfJugglers) { true }

        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("Starting first pass...")
        }

        tree.beatnum = 0
        doFirstPass(tree)

        if (!tree.switchrepeat && tree.vanilla_async && tree.beats % 2 == 1) {
            tree.switchrepeat = true
            tree.beats *= 2
            tree.throw_sum *= 2
            oddperiod = true

            if (Constants.DEBUG_SITESWAP_PARSING) {
                println("Vanilla async detected; applying switchdelay symmetry")
            }
        }

        period = tree.beats
        if (tree.throw_sum % tree.beats != 0) {
            throw JuggleExceptionUser(errorstrings.getString("Error_siteswap_bad_average"))
        }
        numberOfPaths = tree.throw_sum / tree.beats
        indexes = maxThrow + period + 1
        th = Array(numberOfJugglers) { Array(2) { Array(indexes) { arrayOfNulls(maxOccupancy) } } }

        if (Constants.DEBUG_SITESWAP_PARSING) {
            println(
                "period = $period, numpaths = $numberOfPaths, " +
                    "max_throw = $maxThrow, max_occupancy = $maxOccupancy"
            )
            println("Starting second pass...")
        }

        doSecondPass(tree, false, 0)

        resolveModifiers()

        // Finally, add pattern symmetries
        addSymmetry(MHNSymmetry(MHNSymmetry.TYPE_DELAY, numberOfJugglers, null, period))
        if (tree.switchrepeat) { // know that tree is of type Pattern
            val sb = StringBuilder()
            for (i in 1..numberOfJugglers) {
                sb.append("(").append(i).append(",").append(i).append("*)")
            }
            addSymmetry(
                MHNSymmetry(MHNSymmetry.TYPE_SWITCHDELAY, numberOfJugglers, sb.toString(), period / 2)
            )
        }

        // Random error check, not sure where this should go
        if (bodies != null && bodies!!.numberOfJugglers < numberOfJugglers) {
            throw JuggleExceptionUser(errorstrings.getString("Error_jugglers_body"))
        }

        if (Constants.DEBUG_SITESWAP_PARSING) {
            println("Done with initial parse.")
        }
    }

    // First pass through the tree:
    // 1)  Assign hands to "Single Throw" types
    // 2)  Determine whether any Pattern items need switchrepeat turned on
    // 3)  Calculate sti.beats for Pattern and GroupedPattern types
    // 4)  Determine absolute beat numbers for each throw
    // 5)  Calculate max_throw, period, numpaths, and max_occupancy
    // 6)  Resolve wildcards (not implemented)

    @Throws(JuggleExceptionInternal::class)
    private fun doFirstPass(sti: SiteswapTreeItem) {
        var child: SiteswapTreeItem

        sti.throw_sum = 0
        sti.vanilla_async = true

        when (sti.type) {
            SiteswapTreeItem.TYPE_PATTERN -> {
                // Can contain Grouped_Pattern, Solo_Sequence, Passing_Sequence, or Wildcard
                sti.beats = 0

                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    child.beatnum = sti.beatnum + sti.beats

                    /*
                      if (child.type == SiteswapTreeItem.TYPE_WILDCARD) {
                          // resolve this wildcard by finding a suitable transition sequence

                          child.transition = null;    // remove any previously-found transition sequence

                          // First find the pattern state immediately prior to the wildcard
                          SiteswapTreeItem[] item = new SiteswapTreeItem[sti.getNumberOfChildren()];
                          int index = sti.getNumberOfChildren() - 1;
                          boolean done = false;
                          for (int j = i-1; j >= 0; j--) {
                              item[index--] = sti.getChild(j);
                              if (sti.getChild(j).type == SiteswapTreeItem.TYPE_GROUPED_PATTERN) {
                                  done = true;
                                  break;
                              }
                              if (sti.getChild(j).type == SiteswapTreeItem.TYPE_WILDCARD)
                                  throw new JuggleExceptionUser("Can only have one wildcard between grouped patterns");
                          }
                          if (!done) {
                              int beatsum = 0;
                              for (int j = sti.getNumberOfChildren()-1; j > i; j--) {
                                  SiteswapTreeItem c = sti.getChild(j);
                                  item[index--] = c;
                                  if (c.type == SiteswapTreeItem.TYPE_GROUPED_PATTERN) {
                                      done = true;
                                      break;
                                  }
                                  if (c.type == SiteswapTreeItem.TYPE_WILDCARD)
                                      throw new JuggleExceptionUser("Can only have one wildcard between grouped patterns");
                              }
                          }
                          if (!done)
                              throw new JuggleExceptionUser("Must have at least one grouped subpattern to use wildcard");
                          SiteswapTreeItem[] item2 = new SiteswapTreeItem[sti.getNumberOfChildren() - 1 - index];
                          index++;
                          for (int j = 0; index < sti.getNumberOfChildren(); j++, index++)
                              item2[j] = item[index];
                          for (int j = item2.length; j >= 0; j--) {
                              //  Need to assign beatnum to items in item2[]

                              //  beatsum += c.beats;
                              //  c.beatnum = sti.beat - beatsum;
                              //  doFirstPass(c);     // make sure child has hands assigned
                          }
                          // int[][] start_state = findExitState(item2);

                          // Next find the pattern state we need to end up at.  Two cases: even number of transition beats, and odd.
                          index = 0;
                          done = false;
                          for (int j = i+1; j < sti.getNumberOfChildren(); j++) {
                              item[index++] = sti.getChild(j);
                              if (sti.getChild(j).type == SiteswapTreeItem.TYPE_GROUPED_PATTERN) {
                                  done = true;
                                  break;
                              }
                              if (sti.getChild(j).type == SiteswapTreeItem.TYPE_WILDCARD)
                                  throw new JuggleExceptionUser("Can only have one wildcard between grouped patterns");
                          }
                          if (!done) {
                              for (int j = 0; j < i; j++) {
                                  SiteswapTreeItem c = sti.getChild(j);
                                  item[index++] = c;
                                  if (c.type == SiteswapTreeItem.TYPE_GROUPED_PATTERN) {
                                      done = true;
                                      break;
                                  }
                                  if (c.type == SiteswapTreeItem.TYPE_WILDCARD)
                                      throw new JuggleExceptionUser("Can only have one wildcard between grouped patterns");
                              }
                          }
                          if (!done)
                              throw new JuggleExceptionUser("Must have at least one grouped subpattern to use wildcard");
                          item2 = new SiteswapTreeItem[index];
                          for (int j = 0; j < index; j++)
                              item2[j] = item[j];

                          for (int transition_beats = 0; transition_beats < 2; transition_beats++) {
                              for (int j = 0; j < item2.length; j++) {
                                  //  Need to assign beatnum to items in item2[]

                                  //    beatsum += c.beats;
                                  //    c.beatnum = sti.beat - beatsum;
                                  //    doFirstPass(c);     // make sure child has hands assigned
                              }
                              int[][] finish_state = findEntranceState(item2);

                              // Rest of stuff goes here (find transition, fill in child.transition)
                          }
                      }
                      */
                    doFirstPass(child)
                    sti.beats += child.beats
                    sti.throw_sum += child.throw_sum
                    sti.vanilla_async = sti.vanilla_async and child.vanilla_async
                    i++
                }
                if (sti.switchrepeat) {
                    sti.beats *= 2
                    sti.throw_sum *= 2
                }
            }

            SiteswapTreeItem.TYPE_GROUPED_PATTERN -> {
                // Contains only a Pattern type (single child)
                /*
                if (sti.repeats > 20)
                    throw new JuggleExceptionUser("Grouped repeats cannot exceed 20");
                */
                child = sti.getChild(0)
                if (sti.numberOfChildren > 1) {
                    sti.removeChildren()
                    sti.addChild(child)
                }
                child.beatnum = sti.beatnum
                doFirstPass(child)
                var i = 1
                while (i < sti.repeats) {
                    val child2 = (child.clone()) as SiteswapTreeItem
                    sti.addChild(child2)
                    child2.beatnum = sti.beatnum + i * child.beats
                    doFirstPass(child2)
                    i++
                }
                sti.beats = child.beats * sti.repeats
                sti.throw_sum = child.throw_sum * sti.repeats
                sti.vanilla_async = sti.vanilla_async and child.vanilla_async
            }

            SiteswapTreeItem.TYPE_SOLO_SEQUENCE -> {
                // Contains Solo Paired Throw, Solo Multi Throw, or Hand Specifier types
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    child.beatnum = sti.beatnum + child.seq_beatnum
                    doFirstPass(child)
                    sti.throw_sum += child.throw_sum
                    sti.vanilla_async = sti.vanilla_async and child.vanilla_async
                    i++
                }
            }

            SiteswapTreeItem.TYPE_SOLO_PAIRED_THROW -> {
                // Contains only Solo Multi Throw type
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    child.beatnum = sti.beatnum
                    doFirstPass(child)
                    child.left = (i == 0)
                    child.sync_throw = true
                    sti.throw_sum += child.throw_sum
                    i++
                }
                sti.vanilla_async = false
            }

            SiteswapTreeItem.TYPE_SOLO_MULTI_THROW -> {
                // Contains only Solo Single Throw type
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    child.beatnum = sti.beatnum
                    doFirstPass(child)
                    sti.throw_sum += child.value
                    sti.vanilla_async = sti.vanilla_async and child.vanilla_async
                    i++
                }
                if (sti.beatnum % 2 == 0) {
                    sti.left = !rightOnEven[sti.source_juggler - 1]
                } else {
                    sti.left = rightOnEven[sti.source_juggler - 1]
                }
                if (sti.numberOfChildren > maxOccupancy) {
                    maxOccupancy = sti.numberOfChildren
                }
            }

            SiteswapTreeItem.TYPE_SOLO_SINGLE_THROW -> {
                // No children
                if (sti.value > maxThrow) {
                    maxThrow = sti.value
                }
                sti.vanilla_async = !sti.x
            }

            SiteswapTreeItem.TYPE_PASSING_SEQUENCE, SiteswapTreeItem.TYPE_PASSING_GROUP -> {
                // Contains only Passing Throws type
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    child.beatnum = sti.beatnum
                    doFirstPass(child)
                    sti.throw_sum += child.throw_sum
                    sti.vanilla_async = sti.vanilla_async and child.vanilla_async
                    i++
                }
            }

            SiteswapTreeItem.TYPE_PASSING_THROWS -> {
                // Contains Passing Paired Throw, Passing Multi Throw, or Hand Specifier types
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    child.beatnum = sti.beatnum + child.seq_beatnum
                    doFirstPass(child)
                    sti.throw_sum += child.throw_sum
                    sti.vanilla_async = sti.vanilla_async and child.vanilla_async
                    i++
                }
            }

            SiteswapTreeItem.TYPE_PASSING_PAIRED_THROW -> {
                // Contains only Passing Multi Throw type
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    child.beatnum = sti.beatnum
                    doFirstPass(child)
                    child.left = (i == 0)
                    child.sync_throw = true
                    sti.throw_sum += child.throw_sum
                    i++
                }
                sti.vanilla_async = false
            }

            SiteswapTreeItem.TYPE_PASSING_MULTI_THROW -> {
                // Contains only Passing Single Throw type
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    child.beatnum = sti.beatnum
                    doFirstPass(child)
                    sti.throw_sum += child.value
                    sti.vanilla_async = sti.vanilla_async and child.vanilla_async
                    i++
                }
                if (sti.beatnum % 2 == 0) {
                    sti.left = !rightOnEven[sti.source_juggler - 1]
                } else {
                    sti.left = rightOnEven[sti.source_juggler - 1]
                }
                if (sti.numberOfChildren > maxOccupancy) {
                    maxOccupancy = sti.numberOfChildren
                }
            }

            SiteswapTreeItem.TYPE_PASSING_SINGLE_THROW -> {
                // No children
                if (sti.value > maxThrow) {
                    maxThrow = sti.value
                }
                sti.vanilla_async = !sti.x
            }

            SiteswapTreeItem.TYPE_WILDCARD -> if (sti.transition != null) {
                sti.transition.beatnum = sti.beatnum
                doFirstPass(sti.transition)
                // copy variables from sti.transition to sti
                sti.throw_sum = sti.transition.throw_sum
                sti.vanilla_async = sti.transition.vanilla_async
                sti.beats = sti.transition.beats
            } else {
                throw JuggleExceptionInternal("Wildcard not resolved")
            }

            SiteswapTreeItem.TYPE_HAND_SPEC -> {
                if (sti.beatnum % 2 == 0) {
                    rightOnEven[sti.source_juggler - 1] = !sti.spec_left
                } else {
                    rightOnEven[sti.source_juggler - 1] = sti.spec_left
                }
                sti.throw_sum = 0
                if (sti.beatnum > 0) {
                    sti.vanilla_async = false
                }
                hasHandsSpecifier = true
            }
        }
    }

    // Second pass through the tree:
    // 1)  Fill in the th[] array with MHNThrow objects

    private fun doSecondPass(sti: SiteswapTreeItem, switchhands: Boolean, beatoffset: Int) {
        var child: SiteswapTreeItem

        when (sti.type) {
            SiteswapTreeItem.TYPE_PATTERN -> {
                // Can contain Grouped_Pattern, Solo_Sequence, or Passing_Sequence
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    doSecondPass(child, switchhands, beatoffset)
                    i++
                }

                if (sti.switchrepeat) {
                    var i = 0
                    while (i < sti.numberOfChildren) {
                        child = sti.getChild(i)
                        doSecondPass(child, !switchhands, beatoffset + sti.beats / 2)
                        i++
                    }
                }
            }

            SiteswapTreeItem.TYPE_GROUPED_PATTERN, SiteswapTreeItem.TYPE_SOLO_SEQUENCE, SiteswapTreeItem.TYPE_SOLO_PAIRED_THROW, SiteswapTreeItem.TYPE_PASSING_SEQUENCE, SiteswapTreeItem.TYPE_PASSING_GROUP, SiteswapTreeItem.TYPE_PASSING_THROWS, SiteswapTreeItem.TYPE_PASSING_PAIRED_THROW -> {
                var i = 0
                while (i < sti.numberOfChildren) {
                    child = sti.getChild(i)
                    doSecondPass(child, switchhands, beatoffset)
                    i++
                }
            }

            SiteswapTreeItem.TYPE_SOLO_MULTI_THROW, SiteswapTreeItem.TYPE_PASSING_MULTI_THROW -> {
                var index = sti.beatnum + beatoffset
                while (index < indexes) {
                    var i = 0
                    while (i < sti.numberOfChildren) {
                        child = sti.getChild(i)

                        val sourceHand: Int = if (switchhands) {
                            if (sti.left) RIGHT_HAND else LEFT_HAND
                        } else {
                            if (sti.left) LEFT_HAND else RIGHT_HAND
                        }

                        var destHand = if (child.value % 2 == 0) sourceHand else (1 - sourceHand)
                        if (child.x) {
                            destHand = 1 - destHand
                        }

                        var mod = child.mod
                        if (mod == null) {
                            mod = "T" // default throw modifier
                            if (child.source_juggler == child.dest_juggler && sourceHand == destHand) {
                                if (child.value <= 1) {
                                    mod = "H"
                                } else if (child.value == 2) {
                                    // resolve hold vs. throw on third pass
                                    mod = "?"
                                }
                            }
                        }

                        var destJuggler = child.dest_juggler
                        if (destJuggler > numberOfJugglers) {
                            destJuggler = 1 + (destJuggler - 1) % numberOfJugglers
                        }

                        // Note we want to add an MHNThrow for 0 throws as well, to
                        // serve as a placeholder in case of patterns like 24[504],
                        val t = MHNThrow(
                            child.source_juggler,
                            sourceHand,
                            index,
                            i,
                            destJuggler,
                            destHand,
                            index + child.value,
                            -1,
                            mod
                        )
                        if (hands != null) {
                            var idx = index
                            if (sti.sync_throw && sourceHand == RIGHT_HAND) {
                                idx++
                            }
                            idx %= hands!!.getPeriod(child.source_juggler)
                            t.handsindex = idx
                        }
                        th[child.source_juggler - 1][sourceHand][index][i] = t

                        i++
                    }

                    index += period
                }
            }
        }
    }

    // Resolve any unresolved '?' modifiers.

    private fun resolveModifiers() {
        for (i in 0..<indexes) {
            for (j in 0..<numberOfJugglers) {
                for (h in 0..1) {
                    for (slot in 0..<maxOccupancy) {
                        val mhnt: MHNThrow = th[j][h][i][slot] ?: continue
                        if (mhnt.mod == "?") {
                            var doHold = true

                            if (i + 1 < indexes) {
                                for (slot2 in 0..<maxOccupancy) {
                                    val mhnt2 = th[j][h][i + 1][slot2]
                                    if (mhnt2 == null || mhnt2.targetindex == i + 1) {
                                        continue
                                    }
                                    doHold = false
                                    break
                                }
                            }

                            mhnt.mod = if (doHold) "H" else "T"
                        }
                    }
                }
            }
        }
    }
}
