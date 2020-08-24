// SiteswapTransitioner.java
//
// Copyright 2020 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.generator;

import java.text.MessageFormat;

import jugglinglab.core.Constants;
import jugglinglab.notation.MHNThrow;
import jugglinglab.notation.SiteswapPattern;
import jugglinglab.util.*;


public class SiteswapTransitioner extends Transitioner {
    protected int n;
    protected int jugglers;
    protected int indexes;
    protected int max_occupancy;
    protected int lmin;
    protected int lmax;
    protected boolean mp_allow_simulcatches;
    protected boolean mp_allow_clusters;
    protected boolean no_limits;
    protected SiteswapTransitionerControl control;

    protected String from_pattern;
    protected String to_pattern;
    protected SiteswapPattern from_siteswap;
    protected SiteswapPattern to_siteswap;
    protected int[][][] from_state;
    protected int[][][] to_state;
    protected String return_trans;

    // working space for transition-finding; see recurse() below
    protected int[][][][] st;
    protected int[][][] st_target;
    protected int l_target;
    protected MHNThrow[][][][] th;
    protected int[][][] throws_left;
    protected boolean find_all;
    protected String[][] out;
    protected boolean[] should_print;
    protected boolean[][] async_hand_right;
    protected SiteswapPattern prev_siteswap;
    protected GeneratorTarget target;

    protected int max_num;              // maximum number of transitions to find
    protected double max_time;          // maximum number of seconds
    protected long max_time_millis;     // maximum number of milliseconds
    protected long start_time_millis;   // start time of run, in milliseconds
    protected int loop_counter;         // gen_loop() counter for checking timeout
    protected final static int loop_counter_max = 20000;


    @Override
    public String getNotationName() {
        return "Siteswap";
    }

    @Override
    public SiteswapTransitionerControl getTransitionerControl() {
        if (control == null)
            control = new SiteswapTransitionerControl();
        return control;
    }

    @Override
    public void resetTransitionerControl() {
        if (control != null)
            control.resetControl();
    }

    @Override
    public void initTransitioner() throws JuggleExceptionUser, JuggleExceptionInternal {
        if (control == null)
            initTransitioner("5 771");
        else
            initTransitioner(control.getParams());
    }

    @Override
    public void initTransitioner(String[] args) throws JuggleExceptionUser, JuggleExceptionInternal {
        if (Constants.DEBUG_TRANSITIONS) {
            System.out.println("initializing transitioner with args:");
            for (int i = 0; i < args.length; ++i)
                System.out.print(args[i] + " ");
            System.out.print("\n");
        }

        if (args.length < 2)
            throw new JuggleExceptionUser("Too few arguments");
        if (args[0].equals("-"))
            throw new JuggleExceptionUser("'From pattern' not specified");
        if (args[1].equals("-"))
            throw new JuggleExceptionUser("'To pattern' not specified");

        max_occupancy = 1;
        mp_allow_simulcatches = false;
        mp_allow_clusters = true;
        no_limits = false;
        target = null;

        for (int i = 2; i < args.length; ++i) {
            if (args[i].equals("-mf"))
                mp_allow_simulcatches = true;
            else if (args[i].equals("-mc"))
                mp_allow_clusters = false;
            else if (args[i].equals("-m")) {
                if (i < (args.length - 1) && args[i + 1].charAt(0) != '-') {
                    try {
                        max_occupancy = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
                        String template = errorstrings.getString("Error_number_format");
                        String str = guistrings.getString("simultaneous_throws");
                        Object[] arguments = { str };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    i++;
                }
            } else if (args[i].equals("-limits"))
                no_limits = true;  // for CLI mode only
            else {
                String template = errorstrings.getString("Error_unrecognized_option");
                Object[] arguments = { args[i] };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }

        from_pattern = args[0];
        to_pattern = args[1];

        // parse patterns, error if either is invalid
        from_siteswap = new SiteswapPattern();
        to_siteswap = new SiteswapPattern();

        try {
            from_siteswap.fromString(from_pattern);
        } catch (JuggleExceptionUser jeu) {
            throw new JuggleExceptionUser("From pattern: " + jeu.getMessage());
        }
        try {
            to_siteswap.fromString(to_pattern);
        } catch (JuggleExceptionUser jeu) {
            throw new JuggleExceptionUser("To pattern: " + jeu.getMessage());
        }

        // work out number of objects and jugglers, and beats (indexes) in states
        int from_n = from_siteswap.getNumberOfPaths();
        int to_n = to_siteswap.getNumberOfPaths();
        if (from_n != to_n) {
            throw new JuggleExceptionUser("Patterns have unequal number of objects ("
                    + from_n + " != " + to_n + ")");
        }
        n = from_n;

        int from_jugglers = from_siteswap.getNumberOfJugglers();
        int to_jugglers = to_siteswap.getNumberOfJugglers();
        if (from_jugglers != to_jugglers) {
            throw new JuggleExceptionUser("Patterns have unequal number of jugglers ("
                    + from_jugglers + " != " + to_jugglers + ")");
        }
        jugglers = from_jugglers;

        indexes = Math.max(from_siteswap.getIndexes(), to_siteswap.getIndexes());

        // find (and store) starting states for each pattern
        from_state = from_siteswap.getStartingState(indexes);
        to_state = to_siteswap.getStartingState(indexes);

        if (Constants.DEBUG_TRANSITIONS) {
            System.out.println("from state:");
            printState(from_state);
            System.out.println("to state:");
            printState(to_state);

            System.out.println("objects: " + n);
            System.out.println("jugglers: " + jugglers);
            System.out.println("indexes: " + indexes);
            System.out.println("max_occupancy: " + max_occupancy);
            System.out.println("mp_allow_simulcatches: " + mp_allow_simulcatches);
            System.out.println("mp_allow_clusters: " + mp_allow_clusters);
        }

        // find length of transitions from A to B, and B to A
        lmin = findMinLength(from_state, to_state);
        lmax = findMaxLength(from_state, to_state);
        int lreturn = findMinLength(to_state, from_state);

        // initialize working space
        int size = Math.max(lmax, lreturn);
        st = new int[size + 1][jugglers][2][indexes];
        st_target = new int[jugglers][2][indexes];
        th = new MHNThrow[jugglers][2][size][max_occupancy];
        throws_left = new int[size + 1][jugglers][2];
        out = new String[jugglers][size];
        should_print = new boolean[size + 1];
        async_hand_right = new boolean[jugglers][size + 1];

        if (Constants.DEBUG_TRANSITIONS) {
            System.out.println("lmin = " + lmin);
            System.out.println("lmax = " + lmax);
            System.out.println("lreturn = " + lreturn);
        }
    }

    @Override
    public int runTransitioner(GeneratorTarget t) throws JuggleExceptionInternal {
        try {
            return runTransitioner(t, -1, -1.0);  // negative values --> no limits
        } catch (JuggleExceptionUser je) {
            throw new JuggleExceptionInternal("Got a user exception in runTransitioner()");
        }
    }

    @Override
    public int runTransitioner(GeneratorTarget t, int num_limit, double secs_limit)
                    throws JuggleExceptionUser, JuggleExceptionInternal {
        max_num = num_limit;
        max_time = secs_limit;
        if (max_time > 0) {
            max_time_millis = (long)(1000.0 * secs_limit);
            start_time_millis = System.currentTimeMillis();
            loop_counter = 0;
        }

        // find (and store) the shortest transition from B back to A
        //
        // if we added a hands modifier at the end, such as 'R' or '<R|R>',
        // then remove it (unneeded at end of overall pattern)
        prev_siteswap = to_siteswap;
        return_trans = findShortestTrans(to_state, from_state)
                       .replaceAll("R$", "")
                       .replaceAll("\\<(R\\|)+R\\>$", "");

        if (Constants.DEBUG_TRANSITIONS) {
            System.out.println("return trans = " + return_trans);
        }

        target = t;
        target.setPrefixSuffix("(" + from_pattern + "^2)",
                "(" + to_pattern + "^2)" + return_trans);

        int num = 0;

        if (lmin == 0) {
            // no transitions needed
            target.writePattern("", "siteswap", "");
            num = 1;
        } else {
            prev_siteswap = from_siteswap;
            for (int l = lmin; l <= lmax; ++l)
                num += findAllTrans(from_state, to_state, l);
        }

        if (num == 0)
            throw new JuggleExceptionInternal("No transitions found in runTransitioner()");

        if (num == 1)
            target.setStatus(guistrings.getString("Generator_patterns_1"));
        else {
            String template = guistrings.getString("Generator_patterns_ne1");
            Object[] arguments = { new Integer(num) };
            target.setStatus(MessageFormat.format(template, arguments));
        }

        return num;
    }

    //--------------------------------------------------------------------------
    // Utility methods to generate transitions
    //--------------------------------------------------------------------------

    // Finds a single example of a shortest transition from one state to another.
    protected String findShortestTrans(int[][][] from_st, int[][][] to_st) throws JuggleExceptionDone, JuggleExceptionInternal {
        l_target = findMinLength(from_st, to_st);
        if (l_target == 0)
            return "";

        for (int j = 0; j < jugglers; ++j) {
            for (int h = 0; h < 2; ++h) {
                for (int i = 0; i < indexes; ++i) {
                    st[0][j][h][i] = from_st[j][h][i];
                    st_target[j][h][i] = to_st[j][h][i];
                }
            }
        }

        StringBuffer sb = new StringBuffer();
        target = new GeneratorTarget(sb);

        startBeat(0);
        find_all = false;
        int num = recurse(0, 0, 0);

        if (num == 0)
            throw new JuggleExceptionInternal("No transitions found in findShortestTrans()");
        else if (num > 1)
            throw new JuggleExceptionInternal("Too many transitions found in findShortestTrans()");

        return sb.toString().replaceAll("\n", "");
    }

    // Finds all transitions from one state to another, with the number of beats
    // given by `l`.
    //
    // Returns the number of transitions found.
    protected int findAllTrans(int[][][] from_st, int[][][] to_st, int l) throws JuggleExceptionDone {
        l_target = l;

        for (int j = 0; j < jugglers; ++j) {
            for (int h = 0; h < 2; ++h) {
                for (int i = 0; i < indexes; ++i) {
                    st[0][j][h][i] = from_st[j][h][i];
                    st_target[j][h][i] = to_st[j][h][i];
                }
            }
        }

        startBeat(0);
        find_all = true;

        return recurse(0, 0, 0);
    }

    // Finds valid transitions of length `l_target` from a given position in
    // the pattern, to state `st_target`, and outputs them to GeneratorTarget
    // `target`.
    //
    // returns the number of transitions found.
    protected int recurse(int pos, int j, int h) throws JuggleExceptionDone {
        // do a time check
        if (max_time > 0) {
            if (++loop_counter > loop_counter_max) {
                loop_counter = 0;
                if ((System.currentTimeMillis() - start_time_millis) > max_time_millis) {
                    String template = guistrings.getString("Generator_timeout");
                    Object[] arguments = { new Integer((int)max_time) };
                    throw new JuggleExceptionDone(MessageFormat.format(template, arguments));
                }
            }
        }

        // anything left to do in this position?
        if (throws_left[pos][j][h] == 0) {
            // move to next juggler/hand combo
            if (h == 1) {
                h = 0;
                ++j;
            } else
                h = 1;

            if (j == jugglers) {
                ++pos;  // move to next beat

                if (pos < l_target) {
                    startBeat(pos);
                    return recurse(pos, 0, 0);
                }

                // at the target length; does the transition work?
                if (statesEqual(st[pos], st_target)) {
                    outputPattern();
                    return 1;
                } else
                    return 0;
            }

            return recurse(pos, j, h);
        }

        // iterate over all possible outgoing throws
        MHNThrow mhnt = new MHNThrow();
        mhnt.juggler = j + 1;  // source (juggler, hand, index, slot)
        mhnt.hand = h;
        mhnt.index = pos;
        for (mhnt.slot = 0; th[j][h][pos][mhnt.slot] != null; ++mhnt.slot)
            ;

        // loop over target (index, juggler, hand)
        //
        // Iterate over indices in a certain way to get the output ordering we
        // want. The most "natural" transitions are where each throw fills the
        // target state directly, so start with throws that are high enough to
        // do this. Then loop around to small indices.
        int ti_min = pos + 1;
        int ti_max = pos + Math.min(indexes, 35);
        int ti_threshold = Math.min(Math.max(l_target, ti_min), ti_max);

        int ti = ti_threshold;
        int num = 0;

        while (true) {
            for (int tj = 0; tj < jugglers; ++tj) {
                for (int th = 0; th < 2; ++th) {
                    int ts = st[pos + 1][tj][th][ti - pos - 1];  // target slot
                    int finali = ti - l_target;  // target index in final state

                    if (finali >= 0 && finali < indexes) {
                        if (ts >= st_target[tj][th][finali])
                            continue;  // inconsistent with final state
                    } else if (ts >= max_occupancy)
                        continue;

                    mhnt.targetjuggler = tj + 1;
                    mhnt.targethand = th;
                    mhnt.targetindex = ti;
                    mhnt.targetslot = ts;

                    if (isThrowValid(pos, mhnt)) {
                        addThrow(pos, mhnt);
                        num += recurse(pos, j, h);
                        removeThrow(pos, mhnt);

                        if (!find_all && num > 0)
                            return num;

                        if (max_num > 0 && num >= max_num) {
                            String template = guistrings.getString("Generator_spacelimit");
                            Object[] arguments = { new Integer(max_num) };
                            throw new JuggleExceptionDone(MessageFormat.format(template, arguments));
                        }
                    }
                }
            }

            ++ti;
            if (ti > ti_max)
                ti = ti_min;
            if (ti == ti_threshold)
                break;
        }

        return num;
    }

    // Does additional validation that a throw is allowed at a given position
    // in the pattern.
    //
    // Note this is called prior to adding the throw to the pattern, so future
    // states do not reflect the impact of this throw, nor does th[].
    protected boolean isThrowValid(int pos, MHNThrow mhnt) {
        // check #1: throw can't be more than 35 beats long
        if (mhnt.targetindex - mhnt.index > 35)
            return false;

        // check #2: if we're going to throw on the next beat from the same
        // hand, throw can only be a 1x (i.e. a short hold)
        int[][][] next_st = (pos + 1 == l_target ? st_target : st[pos + 1]);
        if (next_st[mhnt.juggler - 1][mhnt.hand][0] > 0) {
            if ((mhnt.targetjuggler != mhnt.juggler)
                    || (mhnt.targethand != mhnt.hand)
                    || (mhnt.targetindex != mhnt.index + 1))
                return false;
        }

        // check #3: if we threw from the same hand on the previous beat,
        // cannot throw a 1x (would have successive 1x throws, which are
        // equivalent to a long hold (2))
        if (pos > 0 && st[pos - 1][mhnt.juggler - 1][mhnt.hand][0] > 0) {
            if ((mhnt.targetjuggler == mhnt.juggler)
                    && (mhnt.targethand == mhnt.hand)
                    && (mhnt.targetindex == mhnt.index + 1))
                return false;
        }

        // check #4: if multiplexing, throw cannot be greater than any
        // preceding throw from the same hand
        for (int s = 0; s < max_occupancy; ++s) {
            MHNThrow prev = th[mhnt.juggler - 1][mhnt.hand][pos][s];
            if (prev == null)
                break;

            if (MHNThrow.compareThrows(mhnt, prev) == 1)
                return false;
        }

        // check #5: if multiplexing, check for cluster throws if that setting
        // is enabled
        if (max_occupancy > 1 && !mp_allow_clusters) {
            for (int s = 0; s < max_occupancy; ++s) {
                MHNThrow prev = th[mhnt.juggler - 1][mhnt.hand][pos][s];
                if (prev == null)
                    break;

                if (MHNThrow.compareThrows(mhnt, prev) == 0)
                    return false;
            }
        }

        // check #6: if multiplexing, check for simultaneous catches if that
        // setting is enabled
        if (max_occupancy > 1 && !mp_allow_simulcatches
                && th[mhnt.juggler - 1][mhnt.hand][pos][0] != null) {
            // count how many incoming throws are not holds
            int num_not_holds = 0;

            // case 1: incoming throws from within the transition itself
            for (int j = 0; j < jugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int i = 0; i < pos; ++i) {
                        for (int s = 0; s < max_occupancy; ++s) {
                            MHNThrow mhnt2 = th[j][h][i][s];
                            if (mhnt2 == null)
                                break;

                            if (mhnt2.targetjuggler == mhnt.juggler
                                    && mhnt2.targethand == mhnt.hand
                                    && mhnt2.targetindex == pos
                                    && !mhnt2.isHold())
                                ++num_not_holds;
                        }
                    }
                }
            }

            // case 2: incoming throws from the previous pattern
            MHNThrow[][][][] th2 = prev_siteswap.getThrows();
            int period = prev_siteswap.getPeriod();
            int slots = prev_siteswap.getMaxOccupancy();

            for (int j = 0; j < jugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int i = 0; i < period; ++i) {
                        for (int s = 0; s < slots; ++s) {
                            MHNThrow mhnt2 = th2[j][h][i][s];
                            if (mhnt2 == null)
                                break;

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
                            boolean correct_index = ((index_overshoot >= 0)
                                                     && (index_overshoot % period == 0));

                            if (correct_index && mhnt2.targetjuggler == mhnt.juggler
                                    && mhnt2.targethand == mhnt.hand && !mhnt2.isHold()) {
                                //System.out.println("got a fill from previous pattern");
                                ++num_not_holds;
                            }
                        }
                    }
                }
            }

            if (num_not_holds > 1) {
                //System.out.println("filtered out a pattern");
                return false;
            }
        }

        return true;
    }

    // Adds a throw to the pattern, updating all data structures.
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
            ++st[pos2][dj][dh][di - pos2];
        }
    }

    // Undoes the actions of addThrow().
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
            --st[pos2][dj][dh][di - pos2];
        }
    }

    // Outputs a completed pattern
    protected void outputPattern() {
        if (target == null)
            return;

        for (int pos = 0; pos < l_target; ++pos)
            outputBeat(pos);

        StringBuffer sb = new StringBuffer();

        if (jugglers > 1)
            sb.append('<');
        for (int j = 0; j < jugglers; ++j) {
            for (int i = 0; i < l_target; ++i)
                sb.append(out[j][i]);

            // if we ended with an unneeded separator, remove it
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '/')
                sb.deleteCharAt(sb.length() - 1);

            if (j < jugglers - 1)
                sb.append('|');
        }
        if (jugglers > 1)
            sb.append('>');

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
            if (jugglers > 1)
                sb.append('<');
            for (int j = 0; j < jugglers; ++j) {
                sb.append('R');
                if (j < jugglers - 1)
                    sb.append('|');
            }
            if (jugglers > 1)
                sb.append('>');
        }

        target.writePattern(sb.toString(), "siteswap", sb.toString().trim());
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
            if (th[j][0][pos][0] != null && th[j][1][pos][0] != null)
                have_sync_throw = true;
            if (st[pos + 1][j][0][0] > 0 || st[pos + 1][j][1][0] > 0)
                have_throw_next_beat = true;
        }

        boolean print_double_beat = have_sync_throw && !have_throw_next_beat;
        should_print[pos + 1] = !print_double_beat;

        for (int j = 0; j < jugglers; ++j) {
            StringBuffer sb = new StringBuffer();

            boolean async_hand_right_next = !async_hand_right[j][pos];

            int hands_throwing = 0;
            if (th[j][0][pos][0] != null)
                ++hands_throwing;
            if (th[j][1][pos][0] != null)
                ++hands_throwing;

            switch (hands_throwing) {
                case 0:
                    sb.append('0');
                    if (print_double_beat)
                        sb.append('0');
                    break;
                case 1:
                    boolean needs_slash;

                    if (th[j][0][pos][0] != null) {
                        if (!async_hand_right[j][pos]) {
                            sb.append('R');
                            async_hand_right_next = false;
                        }
                        needs_slash = printMultiThrow(pos, j, 0, sb);
                    } else {
                        if (async_hand_right[j][pos]) {
                            sb.append('L');
                            async_hand_right_next = true;
                        }
                        needs_slash = printMultiThrow(pos, j, 1, sb);
                    }
                    if (needs_slash)
                        sb.append('/');
                    if (print_double_beat)
                        sb.append('0');
                    break;
                case 2:
                    sb.append('(');
                    printMultiThrow(pos, j, 1, sb);
                    sb.append(',');
                    printMultiThrow(pos, j, 0, sb);
                    sb.append(')');
                    if (!print_double_beat)
                        sb.append('!');
                    break;
            }

            async_hand_right[j][pos + 1] = async_hand_right_next;

            out[j][pos] = sb.toString();
        }
    }

    // Prints the set of throws (assumed non-empty) for a given juggler+hand
    // combination.
    //
    // Returns true if a following throw will need a '/' separator, false if
    // not.
    protected boolean printMultiThrow(int pos, int j, int h, StringBuffer sb) {
        boolean needs_slash = false;

        int num_throws = 0;
        for (int s = 0; s < max_occupancy; ++s) {
            if (th[j][h][pos][s] != null)
                ++num_throws;
        }

        if (num_throws == 0)
            return false;  // should never happen

        if (num_throws > 1)
            sb.append('[');

        for (int s = 0; s < max_occupancy; ++s) {
            MHNThrow mhnt = th[j][h][pos][s];
            if (mhnt == null)
                break;

            int beats = mhnt.targetindex - mhnt.index;
            boolean is_crossed = (mhnt.hand == mhnt.targethand) ^ (beats % 2 == 0);
            boolean is_pass = (mhnt.targetjuggler != mhnt.juggler);

            if (beats < 36)
                sb.append(Character.toLowerCase(Character.forDigit(beats, 36)));
            else
                sb.append('?');  // wildcard will parse but not animate

            if (is_crossed)
                sb.append('x');
            if (is_pass) {
                sb.append('p');
                if (jugglers > 2)
                    sb.append(mhnt.targetjuggler);

                boolean another_throw = ((s + 1) < max_occupancy)
                                         && (th[j][h][pos][s + 1] != null);
                if (another_throw)
                    sb.append('/');

                needs_slash = true;
            } else
                needs_slash = false;
        }

        if (num_throws > 1) {
            sb.append(']');
            needs_slash = false;
        }

        return needs_slash;
    }

    // Initializes data structures to start filling in pattern at position `pos`.
    protected void startBeat(int pos) {
        if (pos == 0) {
            should_print[0] = true;

            for (int j = 0; j < jugglers; ++j)
                async_hand_right[j][0] = true;
        }

        for (int j = 0; j < jugglers; ++j) {
            for (int h = 0; h < 2; ++h) {
                for (int i = 0; i < (indexes - 1); ++i)
                    st[pos + 1][j][h][i] = st[pos][j][h][i + 1];
                st[pos + 1][j][h][indexes - 1] = 0;

                throws_left[pos][j][h] = st[pos][j][h][0];
            }
        }
    }

    // Tests if two states are equal.
    protected boolean statesEqual(int[][][] s1, int[][][] s2) {
        for (int j = 0; j < jugglers; ++j) {
            for (int h = 0; h < 2; ++h) {
                for (int i = 0; i < indexes; ++i) {
                    if (s1[j][h][i] != s2[j][h][i])
                        return false;
                }
            }
        }
        return true;
    }

    // Outputs the state to the command line (useful for debugging).
    protected void printState(int[][][] state) {
        int last_index = 0;
        for (int i = 0; i < indexes; ++i) {
            for (int j = 0; j < jugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    if (state[j][h][i] != 0)
                        last_index = i;
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

    // Finds the minimum length of transition, in beats.
    protected int findMinLength(int[][][] from_st, int[][][] to_st) {
        int length = 0;

        while (true) {
            boolean done = true;

            for (int j = 0; j < jugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    for (int i = 0; i < indexes - length; ++i) {
                        if (from_st[j][h][i + length] > to_st[j][h][i])
                            done = false;
                    }
                }
            }

            if (done)
                return length;
            ++length;
        }
    }

    // Finds the maximum length of transition, in beats.
    protected int findMaxLength(int[][][] from_st, int[][][] to_st) {
        int length = 0;

        for (int i = 0; i < indexes; ++i) {
            for (int j = 0; j < jugglers; ++j) {
                for (int h = 0; h < 2; ++h) {
                    if (from_st[j][h][i] > 0)
                        length = i + 1;
                }
            }
        }

        return length;
    }

    //--------------------------------------------------------------------------
    // Static methods to run transitioner with command line input
    //--------------------------------------------------------------------------

    // Execution limits
    protected static final int trans_max_patterns = 1000;
    protected static final double trans_max_time = 15.0;

    public static void runTransitionerCLI(String[] args, GeneratorTarget target) {
        if (args.length < 2) {
            String template = guistrings.getString("Version");
            Object[] arg1 = { Constants.version };
            String output = "Juggling Lab " +
                            MessageFormat.format(template, arg1).toLowerCase() + "\n";

            template = guistrings.getString("Copyright_message");
            Object[] arg2 = { Constants.year };
            output += MessageFormat.format(template, arg2) + "\n\n";

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

        if (target == null)
            return;

        try {
            SiteswapTransitioner sst = new SiteswapTransitioner();
            sst.initTransitioner(args);

            if (sst.no_limits)
                sst.runTransitioner(target);
            else
                sst.runTransitioner(target, trans_max_patterns, trans_max_time);
        } catch (JuggleExceptionDone e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            System.out.println(errorstrings.getString("Error")+": "+e.getMessage());
        }
    }

    public static void main(String[] args) {
        SiteswapTransitioner.runTransitionerCLI(args, new GeneratorTarget(System.out));
    }

}
