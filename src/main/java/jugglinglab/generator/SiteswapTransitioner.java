//
// SiteswapTransitioner.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator;

import java.text.MessageFormat;
import jugglinglab.core.Constants;
import jugglinglab.notation.MHNThrow;
import jugglinglab.notation.SiteswapPattern;
import jugglinglab.util.*;

public class SiteswapTransitioner extends Transitioner {
  protected static final int LOOP_COUNTER_MAX = 20000;

  // configuration variables
  protected int n;
  protected int jugglers;
  protected int indexes;
  protected int l_min;
  protected int l_max;
  protected int target_occupancy;
  protected int max_occupancy;
  protected boolean mp_allow_simulcatches;
  protected boolean mp_allow_clusters;
  protected boolean no_limits;
  protected String pattern_from;
  protected String pattern_to;
  protected SiteswapPattern siteswap_from;
  protected SiteswapPattern siteswap_to;
  protected int[][][] state_from;
  protected int[][][] state_to;
  protected String return_trans;

  // working variables for transition-finding; see recurse() below
  protected int[][][][] state;
  protected int[][][] state_target;
  protected int l_target;
  protected int l_return;
  protected MHNThrow[][][][] th;
  protected int[][][] throws_left;
  protected boolean find_all;
  protected String[][] out;
  protected boolean[] should_print;
  protected boolean[][] async_hand_right;
  protected SiteswapPattern siteswap_prev;
  protected int target_max_filled_index;
  protected int max_num; // maximum number of transitions to find
  protected double max_time; // maximum number of seconds
  protected long max_time_millis; // maximum number of milliseconds
  protected long start_time_millis; // start time of run, in milliseconds
  protected int loop_counter; // gen_loop() counter for checking timeout

  protected SiteswapTransitionerControl control;
  protected GeneratorTarget target;

  @Override
  public String getNotationName() {
    return "Siteswap";
  }

  @Override
  public SiteswapTransitionerControl getTransitionerControl() {
    if (control == null) {
      control = new SiteswapTransitionerControl();
    }
    return control;
  }

  @Override
  public void resetTransitionerControl() {
    if (control != null) {
      control.resetControl();
    }
  }

  @Override
  public void initTransitioner() throws JuggleExceptionUser, JuggleExceptionInternal {
    if (control == null) {
      initTransitioner("5 771");
    } else {
      initTransitioner(control.getParams());
    }
  }

  @Override
  public void initTransitioner(String[] args) throws JuggleExceptionUser, JuggleExceptionInternal {
    configTransitioner(args);
    allocateWorkspace();
  }

  @Override
  public int runTransitioner(GeneratorTarget t)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    return runTransitioner(t, -1, -1.0);  // negative values --> no limits
  }

  @Override
  public int runTransitioner(GeneratorTarget t, int num_limit, double secs_limit)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    max_num = num_limit;
    max_time = secs_limit;
    if (max_time > 0 || Constants.DEBUG_TRANSITIONS) {
      max_time_millis = (long) (1000.0 * secs_limit);
      start_time_millis = System.currentTimeMillis();
      loop_counter = 0;
    }

    try {
      t.setPrefixSuffix("(" + pattern_from + "^2)", "(" + pattern_to + "^2)" + findReturnTrans());

      int num = 0;
      target = t;

      if (l_min == 0) {
        // no transitions needed
        target.writePattern("", "siteswap", "");
        num = 1;
      } else {
        siteswap_prev = siteswap_from;
        for (int l = l_min; l <= l_max || num == 0; ++l) {
          num += findTrans(state_from, state_to, l, true);
        }
      }

      if (num == 1) {
        target.setStatus(guistrings.getString("Generator_patterns_1"));
      } else {
        String template = guistrings.getString("Generator_patterns_ne1");
        Object[] arguments = {Integer.valueOf(num)};
        target.setStatus(MessageFormat.format(template, arguments));
      }

      return num;
    } finally {
      if (Constants.DEBUG_TRANSITIONS) {
        long millis = System.currentTimeMillis() - start_time_millis;
        System.out.println(String.format("time elapsed: %d.%03d s", millis / 1000, millis % 1000));
      }
    }
  }

  //----------------------------------------------------------------------------
  // Non-public methods below
  //----------------------------------------------------------------------------

  // Set the transitioner configuration variables based on arguments.

  protected void configTransitioner(String[] args)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    if (Constants.DEBUG_TRANSITIONS) {
      System.out.println("-----------------------------------------------------");
      System.out.println("initializing transitioner with args:");
      for (int i = 0; i < args.length; ++i) {
        System.out.print(args[i] + " ");
      }
      System.out.print("\n");
    }

    if (args.length < 2) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_trans_too_few_args"));
    }
    if (args[0].equals("-")) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_trans_from_pattern"));
    }
    if (args[1].equals("-")) {
      throw new JuggleExceptionUser(errorstrings.getString("Error_trans_to_pattern"));
    }

    target_occupancy = 1;
    mp_allow_simulcatches = false;
    mp_allow_clusters = true;
    no_limits = false;
    target = null;

    for (int i = 2; i < args.length; ++i) {
      if (args[i].equals("-mf")) {
        mp_allow_simulcatches = true;
      } else if (args[i].equals("-mc")) {
        mp_allow_clusters = false;
      } else if (args[i].equals("-m")) {
        if (i < (args.length - 1) && args[i + 1].charAt(0) != '-') {
          try {
            target_occupancy = Integer.parseInt(args[i + 1]);
          } catch (NumberFormatException nfe) {
            String template = errorstrings.getString("Error_number_format");
            String str = guistrings.getString("simultaneous_throws");
            Object[] arguments = {str};
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
          }
          i++;
        }
      } else if (args[i].equals("-limits")) {
        no_limits = true;  // for CLI mode only
      } else {
        String template = errorstrings.getString("Error_unrecognized_option");
        Object[] arguments = {args[i]};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }
    }

    pattern_from = args[0];
    pattern_to = args[1];

    // parse patterns, error if either is invalid
    siteswap_from = new SiteswapPattern();
    siteswap_to = new SiteswapPattern();

    try {
      siteswap_from.fromString(pattern_from);
    } catch (JuggleExceptionUser jeu) {
      String template = errorstrings.getString("Error_trans_in_from_pattern");
      Object[] arguments = {jeu.getMessage()};
      throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
    }
    try {
      siteswap_to.fromString(pattern_to);
    } catch (JuggleExceptionUser jeu) {
      String template = errorstrings.getString("Error_trans_in_to_pattern");
      Object[] arguments = {jeu.getMessage()};
      throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
    }

    // work out number of objects and jugglers, and beats (indexes) in states
    int from_n = siteswap_from.getNumberOfPaths();
    int to_n = siteswap_to.getNumberOfPaths();
    if (from_n != to_n) {
      String template = errorstrings.getString("Error_trans_unequal_objects");
      Object[] arguments = {from_n, to_n};
      throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
    }
    n = from_n;

    int from_jugglers = siteswap_from.getNumberOfJugglers();
    int to_jugglers = siteswap_to.getNumberOfJugglers();
    if (from_jugglers != to_jugglers) {
      String template = errorstrings.getString("Error_trans_unequal_jugglers");
      Object[] arguments = {from_jugglers, to_jugglers};
      throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
    }
    jugglers = from_jugglers;

    indexes = Math.max(siteswap_from.getIndexes(), siteswap_to.getIndexes());
    max_occupancy = Math.max(target_occupancy,
        Math.max(siteswap_from.getMaxOccupancy(), siteswap_to.getMaxOccupancy()));

    // find (and store) starting states for each pattern
    state_from = siteswap_from.getStartingState(indexes);
    state_to = siteswap_to.getStartingState(indexes);

    // find length of transitions from A to B, and B to A
    l_min = findMinLength(state_from, state_to);
    l_max = findMaxLength(state_from, state_to);
    l_return = findMinLength(state_to, state_from); // may need to be longer

    if (Constants.DEBUG_TRANSITIONS) {
      System.out.println("from state:");
      printState(state_from);
      System.out.println("to state:");
      printState(state_to);

      System.out.println("objects: " + n);
      System.out.println("jugglers: " + jugglers);
      System.out.println("indexes: " + indexes);
      System.out.println("target_occupancy: " + target_occupancy);
      System.out.println("max_occupancy: " + max_occupancy);
      System.out.println("mp_allow_simulcatches: " + mp_allow_simulcatches);
      System.out.println("mp_allow_clusters: " + mp_allow_clusters);
      System.out.println("l_min: " + l_min);
      System.out.println("l_max: " + l_max);
      System.out.println("l_return (initial): " + l_return);
    }
  }

  // Allocate space for the states and throws in the transition, plus other
  // incidental variables.

  protected void allocateWorkspace() {
    int size = Math.max(l_max, l_return);

    state = new int[size + 1][jugglers][2][indexes];
    state_target = new int[jugglers][2][indexes];
    th = new MHNThrow[jugglers][2][size][max_occupancy];
    throws_left = new int[size + 1][jugglers][2];
    out = new String[jugglers][size];
    should_print = new boolean[size + 1];
    async_hand_right = new boolean[jugglers][size + 1];
  }

  // Find the shortest possible return transition from `to` back to `from`.

  protected String findReturnTrans() throws JuggleExceptionUser, JuggleExceptionInternal {
    if (l_return == 0) {
      return "";
    }

    StringBuffer sb = new StringBuffer();
    target = new GeneratorTarget(sb);
    siteswap_prev = siteswap_to;

    while (true) {
      int num = findTrans(state_to, state_from, l_return, false);

      if (Constants.DEBUG_TRANSITIONS) {
        System.out.println("l_return = " + l_return + " --> num = " + num);
      }

      if (num == 0) {
        ++l_return;
        allocateWorkspace();
        continue;
      }

      if (num == 1) {
        break;
      }

      throw new JuggleExceptionInternal("Too many transitions in findReturnTrans()");
    }

    // if we added a hands modifier at the end, such as 'R' or '<R|R>',
    // then remove it (unneeded at end of overall pattern)
    String return_trans =
        sb.toString().replaceAll("\n", "").replaceAll("R$", "").replaceAll("\\<(R\\|)+R\\>$", "");

    if (Constants.DEBUG_TRANSITIONS) {
      System.out.println("return trans = " + return_trans);
    }
    return return_trans;
  }

  // Find transitions from one state to another, with the number of beats
  // given by `l`.
  //
  // Returns the number of transitions found.

  protected int findTrans(int[][][] from_st, int[][][] to_st, int l, boolean all)
      throws JuggleExceptionUser, JuggleExceptionInternal {
    l_target = l;

    for (int j = 0; j < jugglers; ++j) {
      for (int h = 0; h < 2; ++h) {
        for (int i = 0; i < indexes; ++i) {
          state[0][j][h][i] = from_st[j][h][i];
          state_target[j][h][i] = to_st[j][h][i];
        }
      }
    }

    target_max_filled_index = getMaxFilledIndex(state_target);
    if (Constants.DEBUG_TRANSITIONS) {
      System.out.println("-----------------------------------------------------");
      System.out.println("starting findTrans()...");
      System.out.println("l_target = " + l_target);
      System.out.println("target_max_filled_index = " + target_max_filled_index);
    }

    startBeat(0);
    find_all = all;
    int num = recurse(0, 0, 0);

    if (Constants.DEBUG_TRANSITIONS) {
      System.out.println("" + num + " patterns found");
    }

    return num;
  }

  // Find valid transitions of length `l_target` from a given position in the
  // pattern, to state `state_target`, and outputs them to GeneratorTarget
  // `target`.
  //
  // Returns the number of transitions found.

  protected int recurse(int pos, int j, int h) throws JuggleExceptionUser, JuggleExceptionInternal {
    if (Thread.interrupted()) {
      throw new JuggleExceptionInterrupted();
    }

    // do a time check
    if (max_time > 0) {
      if (++loop_counter > LOOP_COUNTER_MAX) {
        loop_counter = 0;
        if ((System.currentTimeMillis() - start_time_millis) > max_time_millis) {
          String template = guistrings.getString("Generator_timeout");
          Object[] arguments = {Integer.valueOf((int) max_time)};
          throw new JuggleExceptionDone(MessageFormat.format(template, arguments));
        }
      }
    }

    // find the next position with a throw to make
    while (throws_left[pos][j][h] == 0) {
      if (h == 1) {
        h = 0;
        ++j;
      } else {
        h = 1;
      }

      if (j == jugglers) {
        ++pos;  // move to next beat

        if (pos < l_target) {
          startBeat(pos);
          j = h = 0;
          continue;
        }

        // at the target length; does the transition work?
        if (statesEqual(state[pos], state_target)) {
          if (Constants.DEBUG_TRANSITIONS) {
            System.out.println("got a pattern");
          }
          outputPattern();
          return 1;
        } else {
          return 0;
        }
      }
    }

    // iterate over all possible outgoing throws
    MHNThrow mhnt = new MHNThrow();
    mhnt.juggler = j + 1; // source (juggler, hand, index, slot)
    mhnt.hand = h;
    mhnt.index = pos;
    for (mhnt.slot = 0; th[j][h][pos][mhnt.slot] != null; ++mhnt.slot) {
    }

    // loop over target (index, juggler, hand)
    //
    // Iterate over indices in a certain way to get the output ordering we
    // want. The most "natural" transitions are where each throw fills the
    // target state directly, so start with throws that are high enough to
    // do this. Then loop around to small indices.
    int ti_min = pos + 1;
    int ti_max = Math.min(pos + Math.min(indexes, 35), l_target + target_max_filled_index);
    int ti_threshold = Math.min(Math.max(l_target, ti_min), ti_max);

    int ti = ti_threshold;
    int num = 0;

    while (true) {
      for (int tj = 0; tj < jugglers; ++tj) {
        for (int th = 0; th < 2; ++th) {
          int ts = state[pos + 1][tj][th][ti - pos - 1];  // target slot
          int finali = ti - l_target;  // target index in final state

          if (finali >= 0 && finali < indexes) {
            if (ts >= state_target[tj][th][finali]) {
              continue;  // inconsistent with final state
            }
          } else if (ts >= target_occupancy) {
            continue;
          }

          mhnt.targetjuggler = tj + 1;
          mhnt.targethand = th;
          mhnt.targetindex = ti;
          mhnt.targetslot = ts;

          if (Constants.DEBUG_TRANSITIONS) {
            System.out.println("trying throw " + mhnt.toString());
          }

          if (!isThrowValid(pos, mhnt)) {
            continue;
          }

          if (Constants.DEBUG_TRANSITIONS) {
            StringBuffer sb = new StringBuffer();
            for (int t = 0; t < pos; ++t) sb.append(".  ");
            sb.append(mhnt.toString());
            System.out.println(sb.toString());
          }

          addThrow(pos, mhnt);
          num += recurse(pos, j, h);
          removeThrow(pos, mhnt);

          if (!find_all && num > 0) {
            return num;
          }

          if (max_num > 0 && num >= max_num) {
            String template = guistrings.getString("Generator_spacelimit");
            Object[] arguments = {Integer.valueOf(max_num)};
            throw new JuggleExceptionDone(MessageFormat.format(template, arguments));
          }
        }
      }

      ++ti;
      if (ti > ti_max) {
        ti = ti_min;
      }
      if (ti == ti_threshold) {
        break;
      }
    }

    return num;
  }

  // Do additional validation that a throw is allowed at a given position in the
  // pattern.
  //
  // Note this is called prior to adding the throw to the pattern, so future
  // states do not reflect the impact of this throw, nor does th[].

  protected boolean isThrowValid(int pos, MHNThrow mhnt) {
    int j = mhnt.juggler - 1;
    int h = mhnt.hand;
    int i = mhnt.index;
    int targetj = mhnt.targetjuggler - 1;
    int targeth = mhnt.targethand;
    int targeti = mhnt.targetindex;

    // check #1: throw can't be more than 35 beats long
    if (targeti - i > 35) {
      return false;
    }

    // check #2: if we're going to throw on the next beat from the same
    // hand, throw can only be a 1x (i.e. a short hold)
    int[][][] next_st = (pos + 1 == l_target ? state_target : state[pos + 1]);
    if (next_st[j][h][0] > 0) {
      if (targetj != j || targeth != h || targeti != i + 1) {
        if (Constants.DEBUG_TRANSITIONS) {
          System.out.println("  failed check 2");
        }
        return false;
      }
    }

    // check #3: if we threw from the same hand on the previous beat,
    // cannot throw a 1x (would have successive 1x throws, which are
    // equivalent to a long hold (2))
    if (pos > 0 && state[pos - 1][j][h][0] > 0) {
      if (targetj == j && targeth == h && targeti == i + 1) {
        if (Constants.DEBUG_TRANSITIONS) {
          System.out.println("  failed check 3");
        }
        return false;
      }
    }

    // check #4: if multiplexing, throw cannot be greater than any
    // preceding throw from the same hand
    for (int s = 0; s < max_occupancy; ++s) {
      MHNThrow prev = th[j][h][pos][s];
      if (prev == null) {
        break;
      }

      if (MHNThrow.compareThrows(mhnt, prev) == 1) {
        if (Constants.DEBUG_TRANSITIONS) {
          System.out.println("  failed check 4");
        }
        return false;
      }
    }

    // check #5: if multiplexing, check for cluster throws if that setting
    // is enabled
    if (max_occupancy > 1 && !mp_allow_clusters) {
      for (int s = 0; s < max_occupancy; ++s) {
        MHNThrow prev = th[j][h][pos][s];
        if (prev == null) {
          break;
        }

        if (MHNThrow.compareThrows(mhnt, prev) == 0) {
          if (Constants.DEBUG_TRANSITIONS) {
            System.out.println("  failed check 5");
          }
          return false;
        }
      }
    }

    // check #6: if multiplexing, check for simultaneous catches if that
    // setting is enabled
    if (target_occupancy > 1 && !mp_allow_simulcatches && th[j][h][pos][0] != null) {
      // count how many incoming throws are not holds
      int num_not_holds = 0;

      // case 1: incoming throws from within the transition itself
      for (int j2 = 0; j2 < jugglers; ++j2) {
        for (int h2 = 0; h2 < 2; ++h2) {
          for (int i2 = 0; i2 < pos; ++i2) {
            for (int s2 = 0; s2 < max_occupancy; ++s2) {
              MHNThrow mhnt2 = th[j2][h2][i2][s2];
              if (mhnt2 == null) {
                break;
              }

              if (mhnt2.targetjuggler == mhnt.juggler && mhnt2.targethand == mhnt.hand &&
                  mhnt2.targetindex == pos && !mhnt2.isHold()) {
                ++num_not_holds;
              }
            }
          }
        }
      }

      // case 2: incoming throws from the previous pattern
      MHNThrow[][][][] th2 = siteswap_prev.getThrows();
      int period = siteswap_prev.getPeriod();
      int slots = siteswap_prev.getMaxOccupancy();

      for (int j2 = 0; j2 < jugglers; ++j2) {
        for (int h2 = 0; h2 < 2; ++h2) {
          for (int i2 = 0; i2 < period; ++i2) {
            for (int s2 = 0; s2 < slots; ++s2) {
              MHNThrow mhnt2 = th2[j2][h2][i2][s2];
              if (mhnt2 == null) {
                break;
              }

              // Figure out if the throw is landing at the desired time.
              // The time index for the previous pattern runs from 0 to
              // `period`. Our transition tacks on to the end, so we
              // need to add `period` to our transition index to get the
              // index in the reference frame of the previous pattern.
              int index_overshoot = mhnt2.targetindex - (pos + period);

              // If the overshoot is not negative, and is some even
              // multiple of the previous pattern's period, then on
              // some earlier repetition of the pattern it will land at
              // the target index.
              boolean correct_index = ((index_overshoot >= 0) && (index_overshoot % period == 0));

              if (correct_index && mhnt2.targetjuggler == mhnt.juggler &&
                  mhnt2.targethand == mhnt.hand && !mhnt2.isHold()) {
                // System.out.println("got a fill from previous pattern");
                ++num_not_holds;
              }
            }
          }
        }
      }

      if (num_not_holds > 1) {
        // System.out.println("filtered out a pattern");
        if (Constants.DEBUG_TRANSITIONS) {
          System.out.println("  failed check 6");
        }
        return false;
      }
    }

    // check #7: if throw is not a short hold (1x) and there is a nonzero
    // state element S on the beat immediately preceding the target index,
    // then must reserve S slots in the target for the 1x's.
    if (targeti - i != 1 || targetj != j || targeth != h) {
      if (targeti - pos - 2 >= 0) {
        // # of filled slots one beat before
        int reserved = state[pos + 1][targetj][targeth][targeti - pos - 2];

        // maximum allowed slot number
        int max_slot = target_occupancy - 1;

        int finali = targeti - l_target;  // target index in final state
        if (finali >= 0 && finali < indexes) {
          max_slot = state_target[targetj][targeth][finali] - 1;
        }

        if (mhnt.targetslot > max_slot - reserved) {
          if (Constants.DEBUG_TRANSITIONS) {
            System.out.println("  failed check 7");
          }
          return false;
        }
      }
    }

    return true;
  }

  // Add a throw to the pattern, updating all data structures.

  protected void addThrow(int pos, MHNThrow mhnt) {
    int j = mhnt.juggler - 1;
    int h = mhnt.hand;
    int i = mhnt.index;
    int s = mhnt.slot;
    int dj = mhnt.targetjuggler - 1;
    int dh = mhnt.targethand;
    int di = mhnt.targetindex;

    th[j][h][i][s] = mhnt;
    --throws_left[pos][j][h];

    // update future states
    for (int pos2 = pos + 1; pos2 <= l_target && pos2 <= di; ++pos2) {
      ++state[pos2][dj][dh][di - pos2];
    }
  }

  // Undo the actions of addThrow().

  protected void removeThrow(int pos, MHNThrow mhnt) {
    int j = mhnt.juggler - 1;
    int h = mhnt.hand;
    int i = mhnt.index;
    int s = mhnt.slot;
    int dj = mhnt.targetjuggler - 1;
    int dh = mhnt.targethand;
    int di = mhnt.targetindex;

    th[j][h][i][s] = null;
    ++throws_left[pos][j][h];

    // update future states
    for (int pos2 = pos + 1; pos2 <= l_target && pos2 <= di; ++pos2) {
      --state[pos2][dj][dh][di - pos2];
    }
  }

  // Output a completed pattern.

  protected void outputPattern() throws JuggleExceptionInternal {
    if (target == null) {
      return;
    }

    for (int pos = 0; pos < l_target; ++pos) {
      outputBeat(pos);
    }

    StringBuffer sb = new StringBuffer();

    if (jugglers > 1) {
      sb.append('<');
    }
    for (int j = 0; j < jugglers; ++j) {
      for (int i = 0; i < l_target; ++i) {
        sb.append(out[j][i]);
      }

      // if we ended with an unneeded separator, remove it
      if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '/') {
        sb.deleteCharAt(sb.length() - 1);
      }

      if (j < jugglers - 1) {
        sb.append('|');
      }
    }
    if (jugglers > 1) {
      sb.append('>');
    }

    // If, for any of the jugglers, the parser would assign an async throw
    // on the next beat to the left hand, then add on a hands modifier to
    // force it to reset.
    //
    // We laid out the "from" and "to" patterns starting with the right
    // hands, so we want to preserve this or the patterns and transitions
    // won't glue together into a working whole.
    boolean needs_hands_modifier = false;
    for (int j = 0; j < jugglers; ++j) {
      if (!async_hand_right[j][l_target]) {
        needs_hands_modifier = true;
        break;
      }
    }

    if (needs_hands_modifier) {
      if (jugglers > 1) {
        sb.append('<');
      }
      for (int j = 0; j < jugglers; ++j) {
        sb.append('R');
        if (j < jugglers - 1) {
          sb.append('|');
        }
      }
      if (jugglers > 1) {
        sb.append('>');
      }
    }

    try {
      target.writePattern(sb.toString(), "siteswap", sb.toString().trim());
    } catch (JuggleExceptionInternal jei) {
      if (Constants.VALIDATE_GENERATED_PATTERNS) {
        System.out.println("#################");
        printThrowSet();
        System.out.println("#################");
      }
      throw jei;
    }
  }

  // Creates the string form of the assigned throws at this position in the
  // pattern, and saves into the out[][] array. This also determines the
  // values of async_hand_right[][] for the next position in the pattern.
  //
  // This output must be accurately parsable by SiteswapPattern.
  protected void outputBeat(int pos) {
    /*
    Rules:

    - Any juggler with throws for left and right makes a sync throw;
      otherwise an async throw.
    - If any juggler has a sync throw, check if there are throws (by
      any juggler) on the following beat. If there aren't, then output
      two beats together using two-beat sync throws; otherwise output one
      beat and any sync throws are single-beat (with ! after).
    */

    if (!should_print[pos]) {
      // skipping this beat because we already printed on the previous one
      should_print[pos + 1] = true;

      for (int j = 0; j < jugglers; ++j) {
        async_hand_right[j][pos + 1] = !async_hand_right[j][pos];
        out[j][pos] = "";
      }
      return;
    }

    // logic for deciding whether to print next beat along with this one
    boolean have_sync_throw = false;
    boolean have_throw_next_beat = false;

    for (int j = 0; j < jugglers; ++j) {
      if (th[j][0][pos][0] != null && th[j][1][pos][0] != null) {
        have_sync_throw = true;
      }
      if (state[pos + 1][j][0][0] > 0 || state[pos + 1][j][1][0] > 0) {
        have_throw_next_beat = true;
      }
    }

    boolean print_double_beat = have_sync_throw && !have_throw_next_beat;
    should_print[pos + 1] = !print_double_beat;

    for (int j = 0; j < jugglers; ++j) {
      StringBuffer sb = new StringBuffer();

      boolean async_hand_right_next = !async_hand_right[j][pos];

      int hands_throwing = 0;
      if (th[j][0][pos][0] != null) {
        ++hands_throwing;
      }
      if (th[j][1][pos][0] != null) {
        ++hands_throwing;
      }

      switch (hands_throwing) {
        case 0:
          if (pos == 0 && siteswap_prev.hasHandsSpecifier()) {
            sb.append('R');
          }
          sb.append('0');
          if (print_double_beat) {
            sb.append('0');
          }
          break;
        case 1:
          boolean needs_slash;

          if (th[j][0][pos][0] != null) {
            if (!async_hand_right[j][pos]) {
              sb.append('R');
              async_hand_right_next = false;
            } else if (pos == 0 && siteswap_prev.hasHandsSpecifier()) {
              sb.append('R');
            }

            needs_slash = outputMultiThrow(pos, j, 0, sb);
          } else {
            if (async_hand_right[j][pos]) {
              sb.append('L');
              async_hand_right_next = true;
            } else if (pos == 0 && siteswap_prev.hasHandsSpecifier()) {
              sb.append('R');
            }

            needs_slash = outputMultiThrow(pos, j, 1, sb);
          }
          if (needs_slash) {
            sb.append('/');
          }
          if (print_double_beat) {
            sb.append('0');
          }
          break;
        case 2:
          if (pos == 0 && siteswap_prev.hasHandsSpecifier()) {
            sb.append('R');
          }

          sb.append('(');
          outputMultiThrow(pos, j, 1, sb);
          sb.append(',');
          outputMultiThrow(pos, j, 0, sb);
          sb.append(')');
          if (!print_double_beat || pos == l_target - 1) {
            sb.append('!');
          }
          break;
      }

      async_hand_right[j][pos + 1] = async_hand_right_next;

      out[j][pos] = sb.toString();
    }
  }

  // Print the set of throws (assumed non-empty) for a given juggler+hand
  // combination.
  //
  // Returns true if a following throw will need a '/' separator, false if not.

  protected boolean outputMultiThrow(int pos, int j, int h, StringBuffer sb) {
    boolean needs_slash = false;

    int num_throws = 0;
    for (int s = 0; s < max_occupancy; ++s) {
      if (th[j][h][pos][s] != null) {
        ++num_throws;
      }
    }

    if (num_throws == 0) {
      return false;  // should never happen
    }

    if (num_throws > 1) {
      sb.append('[');
    }

    for (int s = 0; s < max_occupancy; ++s) {
      MHNThrow mhnt = th[j][h][pos][s];
      if (mhnt == null) {
        break;
      }

      int beats = mhnt.targetindex - mhnt.index;
      boolean is_crossed = (mhnt.hand == mhnt.targethand) ^ (beats % 2 == 0);
      boolean is_pass = (mhnt.targetjuggler != mhnt.juggler);

      if (beats < 36) {
        sb.append(Character.toLowerCase(Character.forDigit(beats, 36)));
      } else {
        sb.append('?');  // wildcard will parse but not animate
      }

      if (is_crossed) {
        sb.append('x');
      }
      if (is_pass) {
        sb.append('p');
        if (jugglers > 2) {
          sb.append(mhnt.targetjuggler);
        }

        boolean another_throw = ((s + 1) < max_occupancy) && (th[j][h][pos][s + 1] != null);
        if (another_throw) {
          sb.append('/');
        }

        needs_slash = true;
      } else {
        needs_slash = false;
      }
    }

    if (num_throws > 1) {
      sb.append(']');
      needs_slash = false;
    }

    return needs_slash;
  }

  // Initialize data structures to start filling in pattern at position `pos`.

  protected void startBeat(int pos) {
    if (pos == 0) {
      should_print[0] = true;

      for (int j = 0; j < jugglers; ++j) {
        async_hand_right[j][0] = true;
      }
    }

    for (int j = 0; j < jugglers; ++j) {
      for (int h = 0; h < 2; ++h) {
        for (int i = 0; i < (indexes - 1); ++i) {
          state[pos + 1][j][h][i] = state[pos][j][h][i + 1];
        }
        state[pos + 1][j][h][indexes - 1] = 0;

        throws_left[pos][j][h] = state[pos][j][h][0];
      }
    }
  }

  // Test if two states are equal.

  protected boolean statesEqual(int[][][] s1, int[][][] s2) {
    for (int j = 0; j < jugglers; ++j) {
      for (int h = 0; h < 2; ++h) {
        for (int i = 0; i < indexes; ++i) {
          if (s1[j][h][i] != s2[j][h][i]) {
            return false;
          }
        }
      }
    }
    return true;
  }

  // Output the state to the command line (useful for debugging).

  protected void printState(int[][][] state) {
    int last_index = 0;
    for (int i = 0; i < indexes; ++i) {
      for (int j = 0; j < jugglers; ++j) {
        for (int h = 0; h < 2; ++h) {
          if (state[j][h][i] != 0) last_index = i;
        }
      }
    }
    for (int i = 0; i <= last_index; ++i) {
      for (int j = 0; j < jugglers; ++j) {
        for (int h = 0; h < 2; ++h) {
          System.out.println("  s[" + j + "][" + h + "][" + i + "] = " + state[j][h][i]);
        }
      }
    }
  }

  // Find the minimum length of transition, in beats.

  protected int findMinLength(int[][][] from_st, int[][][] to_st) {
    int length = 0;

    while (true) {
      boolean done = true;

      for (int j = 0; j < jugglers; ++j) {
        for (int h = 0; h < 2; ++h) {
          for (int i = 0; i < indexes - length; ++i) {
            if (from_st[j][h][i + length] > to_st[j][h][i]) {
              done = false;
            }
          }
        }
      }

      if (done) {
        return length;
      }
      ++length;
    }
  }

  // Find the maximum length of transition, in beats.

  protected int findMaxLength(int[][][] from_st, int[][][] to_st) {
    int length = 0;

    for (int i = 0; i < indexes; ++i) {
      for (int j = 0; j < jugglers; ++j) {
        for (int h = 0; h < 2; ++h) {
          if (from_st[j][h][i] > 0) {
            length = i + 1;
          }
        }
      }
    }

    return length;
  }

  // Find the maximum index of a nonzero element in the target state.

  protected int getMaxFilledIndex(int[][][] to_st) {
    for (int i = indexes - 1; i >= 0; --i) {
      for (int j = 0; j < jugglers; ++j) {
        for (int h = 0; h < 2; ++h) {
          if (to_st[j][h][i] > 0) {
            return i;
          }
        }
      }
    }

    return 0;
  }

  // Print the throw matrix to standard output (useful for debugging).

  protected void printThrowSet() {
    StringBuffer sb = new StringBuffer();

    for (int pos = 0; pos < l_target; ++pos) {
      for (int j = 0; j < jugglers; ++j) {
        for (int h = 0; h < 2; ++h) {
          for (int s = 0; s < max_occupancy; ++s) {
            MHNThrow mhnt = th[j][h][pos][s];
            if (mhnt == null) {
              continue;
            }

            for (int t = 0; t < pos; ++t) {
              sb.append(".  ");
            }
            sb.append(mhnt.toString() + '\n');
          }
        }
      }
    }
    System.out.println(sb.toString());
  }

  //----------------------------------------------------------------------------
  // Static methods to run transitioner with command line input
  //----------------------------------------------------------------------------

  // Execution limits
  protected static final int TRANS_MAX_PATTERNS = 1000;
  protected static final double TRANS_MAX_TIME = 15;

  public static void runTransitionerCLI(String[] args, GeneratorTarget target) {
    if (args.length < 2) {
      String template = guistrings.getString("Version");
      Object[] arg1 = {Constants.version};
      String output = "Juggling Lab " + MessageFormat.format(template, arg1).toLowerCase() + "\n";

      template = guistrings.getString("Copyright_message");
      Object[] arg2 = {Constants.year};
      output += MessageFormat.format(template, arg2) + "\n";

      output += guistrings.getString("GPL_message") + "\n\n";

      String intro = guistrings.getString("Transitioner_intro");
      if (jugglinglab.JugglingLab.isWindows) {
        // replace single quotes with double quotes in Windows examples
        intro = intro.replaceAll("\'", "\"");
      }
      output += intro;

      System.out.println(output);
      return;
    }

    if (target == null) {
      return;
    }

    try {
      SiteswapTransitioner sst = new SiteswapTransitioner();
      sst.initTransitioner(args);

      if (sst.no_limits) {
        sst.runTransitioner(target);
      } else {
        sst.runTransitioner(target, TRANS_MAX_PATTERNS, TRANS_MAX_TIME);
      }
    } catch (JuggleExceptionDone e) {
      System.out.println(e.getMessage());
    } catch (Exception e) {
      System.out.println(errorstrings.getString("Error") + ": " + e.getMessage());
    }
  }

  public static void main(String[] args) {
    SiteswapTransitioner.runTransitionerCLI(args, new GeneratorTarget(System.out));
  }
}
