//
// SiteswapPattern.java
//
// This class represents a pattern in the generalized form of siteswap notation
// used by Juggling Lab. The real work here is to parse siteswap notation into
// the internal format used by MHNPattern.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation;

import java.text.MessageFormat;
import java.util.ArrayList;
import jugglinglab.core.Constants;
import jugglinglab.notation.ssparser.*;
import jugglinglab.util.*;

public class SiteswapPattern extends MHNPattern {
  protected boolean oddperiod = false;
  protected boolean has_hands_specifier = false;

  @Override
  public String getNotationName() {
    return "Siteswap";
  }

  @Override
  public SiteswapPattern fromString(String conf)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    if (conf.indexOf('=') == -1) {
      // just the pattern
      conf = "pattern=" + conf;
    }

    ParameterList pl = new ParameterList(conf);
    fromParameters(pl);
    pl.errorIfParametersLeft();
    return this;
  }

  @Override
  public SiteswapPattern fromParameters(ParameterList pl)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    if (Constants.DEBUG_SITESWAP_PARSING) {
      System.out.println("Starting siteswap parser...");
    }

    super.fromParameters(pl);

    if (hss != null) {
      HSS.ModParms modinfo = HSS.processHSS(pattern, hss, hold, dwellmax, handspec, dwell);
      pattern = modinfo.convertedPattern;
      dwellarray = modinfo.dwellBeatsArray;
    }

    // pattern = JLFunc.expandRepeats(pattern);
    parseSiteswapNotation();

    // see if we need to repeat the pattern to match hand or body periods:
    if (hands != null || bodies != null) {
      int patperiod = getNorepPeriod();

      int handperiod = 1;
      if (hands != null) {
        for (int i = 1; i <= getNumberOfJugglers(); i++) {
          handperiod = Permutation.lcm(handperiod, hands.getPeriod(i));
        }
      }

      int bodyperiod = 1;
      if (bodies != null) {
        for (int i = 1; i <= getNumberOfJugglers(); i++) {
          bodyperiod = Permutation.lcm(bodyperiod, bodies.getPeriod(i));
        }
      }

      int totalperiod = patperiod;
      totalperiod = Permutation.lcm(totalperiod, handperiod);
      totalperiod = Permutation.lcm(totalperiod, bodyperiod);

      if (totalperiod != patperiod) {
        int repeats = totalperiod / patperiod;
        pattern = "(" + pattern + "^" + repeats + ")";
        // pattern = "(" + pattern + ")^" + repeats;
        // pattern = JLFunc.expandRepeats(pattern);

        if (Constants.DEBUG_SITESWAP_PARSING) {
          System.out.println("-----------------------------------------------------");
          System.out.println("Repeating pattern to match hand/body period, restarting\n");
        }
        parseSiteswapNotation();
      }
    }

    super.buildJugglingMatrix();

    if (Constants.DEBUG_SITESWAP_PARSING) {
      System.out.println("Siteswap parser finished");
      System.out.println("-----------------------------------------------------");
    }

    return this;
  }

  public boolean hasHandsSpecifier() {
    return has_hands_specifier;
  }

  // only works after parseSiteswapNotation() is called:
  protected int getNorepPeriod() {
    return (oddperiod ? getPeriod() / 2 : getPeriod());
  }

  //----------------------------------------------------------------------------
  // Parse siteswap notation into the MHNPattern data structures
  //----------------------------------------------------------------------------

  protected void parseSiteswapNotation() throws JuggleExceptionUser, JuggleExceptionInternal {
    // first clear out the internal variables
    th = null;
    symmetry = new ArrayList<>();
    SiteswapTreeItem tree;

    try {
      if (Constants.DEBUG_SITESWAP_PARSING) {
        System.out.println("Parsing pattern \"" + pattern + "\"");
      }
      tree = SiteswapParser.parsePattern(pattern);
      if (Constants.DEBUG_SITESWAP_PARSING) {
        System.out.println("Parse tree:\n");
        System.out.println(tree);
      }
    } catch (ParseException pe) {
      if (Constants.DEBUG_SITESWAP_PARSING) {
        System.out.println("---------------");
        System.out.println("Parse error:");
        System.out.println(pe.getMessage());
        System.out.println(pe.currentToken);
        System.out.println("---------------");
      }

      if (pe.currentToken == null) {
        String template = errorstrings.getString("Error_pattern_parsing");
        Object[] arguments = {pe.getMessage()};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      } else {
        String template = errorstrings.getString("Error_pattern_syntax");
        String problem = ParseException.add_escapes(pe.currentToken.next.image);
        Object[] arguments = {problem, pe.currentToken.next.beginColumn};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    } catch (TokenMgrError tme) {
      String template = errorstrings.getString("Error_pattern_syntax");
      String problem = TokenMgrError.addEscapes(String.valueOf(tme.curChar));
      Object[] arguments = {problem, tme.errorColumn - 1};
      throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
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

    numjugglers = tree.jugglers;
    max_occupancy = 0; // calculated in doFirstPass()
    max_throw = 0;

    right_on_even = new boolean[this.numjugglers];
    for (int i = 0; i < numjugglers; i++) {
      right_on_even[i] = true;
    }

    if (Constants.DEBUG_SITESWAP_PARSING) {
      System.out.println("Starting first pass...");
    }

    tree.beatnum = 0;
    doFirstPass(tree);

    if (!tree.switchrepeat && tree.vanilla_async && tree.beats % 2 == 1) {
      tree.switchrepeat = true;
      tree.beats *= 2;
      tree.throw_sum *= 2;
      oddperiod = true;

      if (Constants.DEBUG_SITESWAP_PARSING) {
        System.out.println("Vanilla async detected; applying switchdelay symmetry");
      }
    }

    period = tree.beats;
    if (tree.throw_sum % tree.beats != 0) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_siteswap_bad_average"));
    }
    numpaths = tree.throw_sum / tree.beats;
    indexes = max_throw + period + 1;
    th = new MHNThrow[numjugglers][2][indexes][max_occupancy];

    if (Constants.DEBUG_SITESWAP_PARSING) {
      System.out.println("period = " + period
              + ", numpaths = " + numpaths
              + ", max_throw = " + max_throw
              + ", max_occupancy = " + max_occupancy);
      System.out.println("Starting second pass...");
    }

    doSecondPass(tree, false, 0);

    resolveModifiers();

    // Finally, add pattern symmetries
    addSymmetry(new MHNSymmetry(MHNSymmetry.TYPE_DELAY, numjugglers, null, period));
    if (tree.switchrepeat) { // know that tree is of type Pattern
      StringBuilder sb = new StringBuilder();
      for (int i = 1; i <= numjugglers; i++) {
        sb.append("(").append(i).append(",").append(i).append("*)");
      }
      addSymmetry(
          new MHNSymmetry(MHNSymmetry.TYPE_SWITCHDELAY, numjugglers, sb.toString(), period / 2));
    }

    // Random error check, not sure where this should go
    if (bodies != null && bodies.getNumberOfJugglers() < this.getNumberOfJugglers()) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_jugglers_body"));
    }

    if (Constants.DEBUG_SITESWAP_PARSING) {
      System.out.println("Done with initial parse.");
    }
  }

  // First pass through the tree:
  // 1)  Assign hands to "Single Throw" types
  // 2)  Determine whether any Pattern items need switchrepeat turned on
  // 3)  Calculate sti.beats for Pattern and GroupedPattern types
  // 4)  Determine absolute beat numbers for each throw
  // 5)  Calculate max_throw, period, numpaths, and max_occupancy
  // 6)  Resolve wildcards (not implemented)

  boolean[] right_on_even; // async throws on even beat numbers made with right hand?

  protected void doFirstPass(SiteswapTreeItem sti) throws JuggleExceptionInternal {
    SiteswapTreeItem child;

    sti.throw_sum = 0;
    sti.vanilla_async = true;

    switch (sti.type) {
      case SiteswapTreeItem.TYPE_PATTERN:
        // Can contain Grouped_Pattern, Solo_Sequence, Passing_Sequence, or Wildcard
        sti.beats = 0;

        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          child.beatnum = sti.beatnum + sti.beats;

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

          doFirstPass(child);
          sti.beats += child.beats;
          sti.throw_sum += child.throw_sum;
          sti.vanilla_async &= child.vanilla_async;
        }
        if (sti.switchrepeat) {
          sti.beats *= 2;
          sti.throw_sum *= 2;
        }
        break;
      case SiteswapTreeItem.TYPE_GROUPED_PATTERN:
        // Contains only a Pattern type (single child)

        /*
        if (sti.repeats > 20)
            throw new JuggleExceptionUser("Grouped repeats cannot exceed 20");
        */

        child = sti.getChild(0);
        if (sti.getNumberOfChildren() > 1) {
          sti.removeChildren();
          sti.addChild(child);
        }
        child.beatnum = sti.beatnum;
        doFirstPass(child);
        for (int i = 1; i < sti.repeats; i++) {
          SiteswapTreeItem child2 = (SiteswapTreeItem) (child.clone());
          sti.addChild(child2);
          child2.beatnum = sti.beatnum + i * child.beats;
          doFirstPass(child2);
        }
        sti.beats = child.beats * sti.repeats;
        sti.throw_sum = child.throw_sum * sti.repeats;
        sti.vanilla_async &= child.vanilla_async;
        break;
      case SiteswapTreeItem.TYPE_SOLO_SEQUENCE:
        // Contains Solo Paired Throw, Solo Multi Throw, or Hand Specifier types
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          child.beatnum = sti.beatnum + child.seq_beatnum;
          doFirstPass(child);
          sti.throw_sum += child.throw_sum;
          sti.vanilla_async &= child.vanilla_async;
        }
        break;
      case SiteswapTreeItem.TYPE_SOLO_PAIRED_THROW:
        // Contains only Solo Multi Throw type
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          child.beatnum = sti.beatnum;
          doFirstPass(child);
          child.left = (i == 0);
          child.sync_throw = true;
          sti.throw_sum += child.throw_sum;
        }
        sti.vanilla_async = false;
        break;
      case SiteswapTreeItem.TYPE_SOLO_MULTI_THROW:
        // Contains only Solo Single Throw type
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          child.beatnum = sti.beatnum;
          doFirstPass(child);
          sti.throw_sum += child.value;
          sti.vanilla_async &= child.vanilla_async;
        }
        if (sti.beatnum % 2 == 0) {
          sti.left = !right_on_even[sti.source_juggler - 1];
        } else {
          sti.left = right_on_even[sti.source_juggler - 1];
        }
        if (sti.getNumberOfChildren() > this.max_occupancy) {
          this.max_occupancy = sti.getNumberOfChildren();
        }
        break;
      case SiteswapTreeItem.TYPE_SOLO_SINGLE_THROW:
        // No children
        if (sti.value > this.max_throw) {
          this.max_throw = sti.value;
        }
        sti.vanilla_async = !sti.x;
        break;
      case SiteswapTreeItem.TYPE_PASSING_SEQUENCE:
        // Contains only Passing Group type
      case SiteswapTreeItem.TYPE_PASSING_GROUP:
        // Contains only Passing Throws type
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          child.beatnum = sti.beatnum;
          doFirstPass(child);
          sti.throw_sum += child.throw_sum;
          sti.vanilla_async &= child.vanilla_async;
        }
        break;
      case SiteswapTreeItem.TYPE_PASSING_THROWS:
        // Contains Passing Paired Throw, Passing Multi Throw, or Hand Specifier types
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          child.beatnum = sti.beatnum + child.seq_beatnum;
          doFirstPass(child);
          sti.throw_sum += child.throw_sum;
          sti.vanilla_async &= child.vanilla_async;
        }
        break;
      case SiteswapTreeItem.TYPE_PASSING_PAIRED_THROW:
        // Contains only Passing Multi Throw type
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          child.beatnum = sti.beatnum;
          doFirstPass(child);
          child.left = (i == 0);
          child.sync_throw = true;
          sti.throw_sum += child.throw_sum;
        }
        sti.vanilla_async = false;
        break;
      case SiteswapTreeItem.TYPE_PASSING_MULTI_THROW:
        // Contains only Passing Single Throw type
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          child.beatnum = sti.beatnum;
          doFirstPass(child);
          sti.throw_sum += child.value;
          sti.vanilla_async &= child.vanilla_async;
        }
        if (sti.beatnum % 2 == 0) {
          sti.left = !right_on_even[sti.source_juggler - 1];
        } else {
          sti.left = right_on_even[sti.source_juggler - 1];
        }
        if (sti.getNumberOfChildren() > this.max_occupancy) {
          this.max_occupancy = sti.getNumberOfChildren();
        }
        break;
      case SiteswapTreeItem.TYPE_PASSING_SINGLE_THROW:
        // No children
        if (sti.value > this.max_throw) {
          this.max_throw = sti.value;
        }
        sti.vanilla_async = !sti.x;
        break;
      case SiteswapTreeItem.TYPE_WILDCARD:
        if (sti.transition != null) {
          sti.transition.beatnum = sti.beatnum;
          doFirstPass(sti.transition);
          // copy variables from sti.transition to sti
          sti.throw_sum = sti.transition.throw_sum;
          sti.vanilla_async = sti.transition.vanilla_async;
          sti.beats = sti.transition.beats;
        } else {
          throw new JuggleExceptionInternal("Wildcard not resolved");
        }
        break;
      case SiteswapTreeItem.TYPE_HAND_SPEC:
        if (sti.beatnum % 2 == 0) {
          right_on_even[sti.source_juggler - 1] = !sti.spec_left;
        } else {
          right_on_even[sti.source_juggler - 1] = sti.spec_left;
        }
        sti.throw_sum = 0;
        if (sti.beatnum > 0) {
          sti.vanilla_async = false;
        }
        has_hands_specifier = true;
        break;
    }
  }

  // Second pass through the tree:
  // 1)  Fill in the th[] array with MHNThrow objects

  protected void doSecondPass(SiteswapTreeItem sti, boolean switchhands, int beatoffset) {
    SiteswapTreeItem child;

    switch (sti.type) {
      case SiteswapTreeItem.TYPE_PATTERN:
        // Can contain Grouped_Pattern, Solo_Sequence, or Passing_Sequence
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          doSecondPass(child, switchhands, beatoffset);
        }

        if (sti.switchrepeat) {
          for (int i = 0; i < sti.getNumberOfChildren(); i++) {
            child = sti.getChild(i);
            doSecondPass(child, !switchhands, beatoffset + sti.beats / 2);
          }
        }
        break;

      case SiteswapTreeItem.TYPE_GROUPED_PATTERN:
      case SiteswapTreeItem.TYPE_SOLO_SEQUENCE:
      case SiteswapTreeItem.TYPE_SOLO_PAIRED_THROW:
      case SiteswapTreeItem.TYPE_PASSING_SEQUENCE:
      case SiteswapTreeItem.TYPE_PASSING_GROUP:
      case SiteswapTreeItem.TYPE_PASSING_THROWS:
      case SiteswapTreeItem.TYPE_PASSING_PAIRED_THROW:
        for (int i = 0; i < sti.getNumberOfChildren(); i++) {
          child = sti.getChild(i);
          doSecondPass(child, switchhands, beatoffset);
        }
        break;

      case SiteswapTreeItem.TYPE_SOLO_MULTI_THROW:
      case SiteswapTreeItem.TYPE_PASSING_MULTI_THROW:
        int index = sti.beatnum + beatoffset;
        while (index < this.indexes) {
          for (int i = 0; i < sti.getNumberOfChildren(); i++) {
            child = sti.getChild(i);

            int source_hand;
            if (switchhands) {
              source_hand = (sti.left ? RIGHT_HAND : LEFT_HAND);
            } else {
              source_hand = (sti.left ? LEFT_HAND : RIGHT_HAND);
            }

            int dest_hand = (child.value % 2 == 0) ? source_hand : (1 - source_hand);
            if (child.x) {
              dest_hand = 1 - dest_hand;
            }

            String mod = child.mod;
            if (mod == null) {
              mod = "T"; // default throw modifier
              if (child.source_juggler == child.dest_juggler && source_hand == dest_hand) {
                if (child.value <= 1) {
                  mod = "H";
                } else if (child.value == 2) {
                  // resolve hold vs. throw on third pass
                  mod = "?";
                }
              }
            }

            int dest_juggler = child.dest_juggler;
            if (dest_juggler > getNumberOfJugglers()) {
              dest_juggler = 1 + (dest_juggler - 1) % getNumberOfJugglers();
            }

            // Note we want to add an MHNThrow for 0 throws as well, to
            // serve as a placeholder in case of patterns like 24[504],

            MHNThrow t = new MHNThrow(
                    child.source_juggler,
                    source_hand,
                    index,
                    i,
                    dest_juggler,
                    dest_hand,
                    index + child.value,
                    -1,
                    mod);
            if (hands != null) {
              int idx = index;
              if (sti.sync_throw && source_hand == RIGHT_HAND) {
                idx++;
              }
              idx %= hands.getPeriod(child.source_juggler);
              t.handsindex = idx;
            }
            th[child.source_juggler - 1][source_hand][index][i] = t;

            // System.out.println("added throw value "+child.value+" at index "+index);
          }

          index += this.period;
        }
        break;
    }
  }

  // Resolve any unresolved '?' modifiers.

  protected void resolveModifiers() {
    for (int i = 0; i < indexes; ++i) {
      for (int j = 0; j < numjugglers; ++j) {
        for (int h = 0; h < 2; ++h) {
          for (int slot = 0; slot < max_occupancy; ++slot) {
            MHNThrow mhnt = th[j][h][i][slot];

            if (mhnt == null) {
              continue;
            }

            if (mhnt.mod.equals("?")) {
              boolean do_hold = true;

              if (i + 1 < indexes) {
                for (int slot2 = 0; slot2 < max_occupancy; ++slot2) {
                  MHNThrow mhnt2 = th[j][h][i + 1][slot2];

                  if (mhnt2 == null || mhnt2.targetindex == i + 1) {
                    continue;
                  }

                  do_hold = false;
                  break;
                }
              }

              mhnt.mod = (do_hold ? "H" : "T");
            }
          }
        }
      }
    }
  }
}
