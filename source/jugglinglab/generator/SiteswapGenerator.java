// SiteswapGenerator.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.generator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.JPanel;

import jugglinglab.util.*;
import jugglinglab.core.Constants;


// This is the siteswap pattern generator component of Juggling Lab.
// It is mostly a port of an older program called J2, written in C.

/************************************************************************/
/*   J version 2.3               by Jack Boyce        12/91             */
/*                                  jboyce@tybalt.caltech.edu           */
/*                                                                      */
/*   This program finds all juggling siteswap patterns for a given      */
/*   number of balls, maximum throw value, and pattern length.          */
/*   A state graph approach is used in order to speed up computation.   */
/*                                                                      */
/*   It is a complete rewrite of an earlier program written in 11/90    */
/*   which handled only non-multiplexed asynchronous solo siteswaps.    */
/*   This version can generate multiplexed and nonmultiplexed tricks    */
/*   for an arbitrary number of people, number of hands, and throwing   */
/*   rhythm.  The built-in modes are asynchronous and synchronous solo  */
/*   juggling, and two person asynchronous passing.                     */
/*                                                                      */
/*   Include flag modified and the -simple flag added on 2/92           */
/*   Extra check (for speed) added to gen_loops() on 01/19/98           */
/*   Bug fix to find_start_end() on 02/18/99                            */
/************************************************************************/

public class SiteswapGenerator extends Generator {
    // Modes
    protected final static int ASYNC = 0;
    protected final static int SYNC = 1;
    //protected final static int CUSTOM = 2;

    // Types of multiplexing filter slots
    private final static int MP_EMPTY = 0;
    private final static int MP_THROW = 1;
    private final static int MP_LOWER_BOUND = 2;
    private final static int TYPE = 0;
    private final static int FROM = 1;
    private final static int VALUE = 2;

    // max. # of chars. printed per throw
    private final static int CHARS_PER_THROW = 50;

    protected final static int async_rhythm_repunit[][] = { { 1 } };
    protected final static int sync_rhythm_repunit[][] = { { 1, 0 }, { 1, 0 } };
    private final static int loop_counter_max = 20000;

    // configuration variables
    protected int n;
    protected int jugglers;
    protected int ht;
    protected int l_min;
    protected int l_max;
    protected ArrayList<Pattern> exclude;
    protected ArrayList<Pattern> include;
    protected int numflag;
    protected int groundflag;
    protected int rotflag;
    protected int fullflag;
    protected int mpflag;
    protected int multiplex;
    protected int delaytime;
    protected int hands;
    protected int max_occupancy;
    protected int leader_person;
    protected int[][] rhythm_repunit;
    protected int rhythm_period;
    protected int[] holdthrow;
    protected int[] person_number;
    protected int[][] ground_state;
    protected int ground_state_length;
    protected boolean mp_clustered;
    protected boolean lameflag;
    protected boolean sequenceflag;
    protected boolean connected_patterns;
    protected boolean juggler_permutations;
    protected int mode;
    protected int slot_size;

    // working variables
    protected int[][][] state;
    protected int l_target;
    protected int[][][] rhythm;
    protected int[][] throws_left;
    protected int[][] holes;
    protected int[][][] throw_to;
    protected int[][][] throw_value;
    protected int[][][][] mp_filter;
    protected boolean pattern_printx;
    protected int[] scratch1;
    protected int[] scratch2;
    protected char[] output;
    protected boolean[] connections;
    protected boolean[] perm_scratch1;
    protected boolean[] perm_scratch2;
    protected char[] starting_seq;
    protected char[] ending_seq;
    protected int starting_seq_length;
    protected int ending_seq_length;
    protected int max_num;              // maximum number of patterns to print
    protected double max_time;          // maximum number of seconds
    protected long max_time_millis;     // maximum number of milliseconds
    protected long start_time_millis;   // start time of run, in milliseconds
    protected int loop_counter;         // gen_loop() counter for checking timeout

    protected SiteswapGeneratorControl control;
    protected GeneratorTarget target;


    @Override
    public String getNotationName() {
        return "Siteswap";
    }

    @Override
    public String getStartupMessage() {
        return "Welcome to the J2 Siteswap Generator";
    }

    @Override
    public JPanel getGeneratorControl() {
        if (control == null)
            control = new SiteswapGeneratorControl();
        return control;
    }

    @Override
    public void resetGeneratorControl() {
        if (control != null)
            control.resetControl();
    }

    @Override
    public void initGenerator() throws JuggleExceptionUser {
        if (control == null)
            initGenerator("5 7 5");     // default settings
        else
            initGenerator(control.getParams());
    }

    @Override
    public void initGenerator(String[] args) throws JuggleExceptionUser {
        configGenerator(args);
        allocateWorkspace();
    }

    @Override
    public int runGenerator(GeneratorTarget t) throws JuggleExceptionUser, JuggleExceptionInternal {
        return runGenerator(t, -1, -1.0);  // no limits
    }

    @Override
    public int runGenerator(GeneratorTarget t, int num_limit, double secs_limit) throws JuggleExceptionUser, JuggleExceptionInternal {
        if (groundflag == 1 && ground_state_length > ht)
            return 0;

        max_num = num_limit;
        max_time = secs_limit;
        if (max_time > 0 || Constants.DEBUG_GENERATOR) {
            max_time_millis = (long)(1000.0 * secs_limit);
            start_time_millis = System.currentTimeMillis();
            loop_counter = 0;
        }

        try {
            target = t;

            int num = 0;
            for (l_target = l_min; l_target <= l_max; l_target += rhythm_period)
                num += findPatterns(0, 0, 0);

            if (numflag != 0) {
                if (num == 1)
                    target.setStatus(guistrings.getString("Generator_patterns_1"));
                else {
                    String template = guistrings.getString("Generator_patterns_ne1");
                    Object[] arguments = { Integer.valueOf(num) };
                    target.setStatus(MessageFormat.format(template, arguments));
                }
            }

            return num;
        } finally {
            if (Constants.DEBUG_GENERATOR) {
                long millis = System.currentTimeMillis() - start_time_millis;
                System.out.println(String.format("time elapsed: %d.%03d s", millis/1000, millis%1000));
            }
        }
    }

    //--------------------------------------------------------------------------
    // Non-public methods below
    //--------------------------------------------------------------------------

    // Sets the generator configuration variables based on arguments
    protected void configGenerator(String[] args) throws JuggleExceptionUser {
        if (Constants.DEBUG_GENERATOR) {
            System.out.println("-----------------------------------------------------");
            System.out.println("initializing generator with args:");
            for (int i = 0; i < args.length; ++i)
                System.out.print(args[i] + " ");
            System.out.print("\n");
        }

        if (args.length < 3)
            throw new JuggleExceptionUser(errorstrings.getString("Error_generator_insufficient_input"));

        max_occupancy = 0;
        leader_person = 1;
        numflag = 0;
        groundflag = 0;
        rotflag = 0;
        fullflag = mpflag = 1;
        mp_clustered = true;
        multiplex = 1;
        delaytime = 0;
        lameflag = false;
        connected_patterns = false;
        juggler_permutations = false;
        sequenceflag = true;
        mode = ASYNC;  // default mode
        jugglers = 1;
        target = null;
        exclude = new ArrayList<Pattern>();
        include = new ArrayList<Pattern>();

        boolean true_multiplex = false;

        for (int i = 3; i < args.length; ++i) {
            if (args[i].equals("-n"))
                numflag = 1;
            else if (args[i].equals("-no"))
                numflag = 2;
            else if (args[i].equals("-g"))
                groundflag = 1;
            else if (args[i].equals("-ng"))
                groundflag = 2;
            else if (args[i].equals("-f"))
                fullflag = 0;
            else if (args[i].equals("-prime"))
                fullflag = 2;
            else if (args[i].equals("-rot"))
                rotflag = 1;
            else if (args[i].equals("-jp"))
                juggler_permutations = true;
            else if (args[i].equals("-lame"))
                lameflag = true;
            else if (args[i].equals("-se"))
                sequenceflag = false;
            else if (args[i].equals("-s"))
                mode = SYNC;
            /*  else if (!strcmp(argv[i], "-c")) {
                mode = CUSTOM;
            if (i != (argc - 1))
                custom_initialize(argv[++i]);
            else {
                printf("No custom rhythm file given\n");
                exit(0);
            }
            }*/
            else if (args[i].equals("-cp"))
                connected_patterns = true;
            else if (args[i].equals("-mf"))
                mpflag = 0;
            else if (args[i].equals("-mc"))
                mp_clustered = false;
            else if (args[i].equals("-mt"))
                true_multiplex = true;
            else if (args[i].equals("-m")) {
                if (i < (args.length - 1) && args[i + 1].charAt(0) != '-') {
                    try {
                        multiplex = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
                        String template = errorstrings.getString("Error_number_format");
                        String str = guistrings.getString("simultaneous_throws");
                        Object[] arguments = { str };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    ++i;
                }
            }
            else if (args[i].equals("-j")) {
                if (i < (args.length - 1) && args[i + 1].charAt(0) != '-') {
                    try {
                        jugglers = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
                        String template = errorstrings.getString("Error_number_format");
                        String str = guistrings.getString("Jugglers");
                        Object[] arguments = { str };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    ++i;
                }
            }
            else if (args[i].equals("-d")) {
                if (i < (args.length - 1) && args[i + 1].charAt(0) != '-') {
                    try {
                        delaytime = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
                        String template = errorstrings.getString("Error_number_format");
                        String str = guistrings.getString("Passing_communication_delay");
                        Object[] arguments = { str };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    groundflag = 1;  // find only ground state tricks
                    ++i;
                }
            }
            else if (args[i].equals("-l")) {
                if (i < (args.length - 1) && args[i + 1].charAt(0) != '-') {
                    try {
                        leader_person = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
                        String template = errorstrings.getString("Error_number_format");
                        String str = guistrings.getString("Error_passing_leader_number");
                        Object[] arguments = { str };
                        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    ++i;
                }
            }
            else if (args[i].equals("-x")) {
                ++i;
                while (i < args.length && args[i].charAt(0) != '-') {
                    try {
                        String re = makeStandardRegex(args[i]);
                        if (re.indexOf("^") < 0)
                            re = ".*" + re + ".*";
                        if (Constants.DEBUG_GENERATOR)
                            System.out.println("adding exclusion " + re);
                        exclude.add(Pattern.compile(re));
                    } catch (PatternSyntaxException pse) {
                        throw new JuggleExceptionUser(errorstrings.getString("Error_excluded_throws"));
                    }
                    ++i;
                }
                --i;
            }
            else if (args[i].equals("-i")) {
                ++i;
                while (i < args.length && args[i].charAt(0) != '-') {
                    try {
                        String re = makeStandardRegex(args[i]);
                        if (re.indexOf("^") < 0)
                            re = ".*" + re;
                        if (re.indexOf("$") < 0)
                            re = re + ".*";
                        include.add(Pattern.compile(re));
                    } catch (PatternSyntaxException ps) {
                        throw new JuggleExceptionUser(errorstrings.getString("Error_included_throws"));
                    }
                    ++i;
                }
                --i;
            } else {
                String template = errorstrings.getString("Error_unrecognized_option");
                Object[] arguments = { args[i] };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }

        configMode();

        try {
            n = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            String template = errorstrings.getString("Error_number_format");
            String str = guistrings.getString("balls");
            Object[] arguments = { str };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
        try {
            if (args[1].equals("-"))  // signal to not specify a maximum throw
                ht = -1;
            else if (args[1].matches("^[0-9]+$"))
                ht = Integer.parseInt(args[1]);  // numbers only
            else
                ht = Integer.parseInt(args[1], 36);  // 'a' = 10, 'b' = 11, ...
        } catch (NumberFormatException nfe) {
            String template = errorstrings.getString("Error_number_format");
            String str = guistrings.getString("max._throw");
            Object[] arguments = { str };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
        try {
            if (args[2].equals("-")) {
                l_min = rhythm_period;
                l_max = -1;
            } else {
                int divider = args[2].indexOf('-');
                if (divider == 0) {
                    l_min = rhythm_period;
                    l_max = Integer.parseInt(args[2].substring(1));
                } else if (divider == (args[2].length() - 1)) {
                    l_min = Integer.parseInt(args[2].substring(0, divider));
                    l_max = -1;
                } else if (divider > 0) {
                    l_min = Integer.parseInt(args[2].substring(0, divider));
                    l_max = Integer.parseInt(args[2].substring(divider + 1));
                } else {
                    l_min = l_max = Integer.parseInt(args[2]);
                }
            }
        } catch (NumberFormatException nfe) {
            String template = errorstrings.getString("Error_number_format");
            String str = guistrings.getString("period");
            Object[] arguments = { str };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }

        if (Constants.DEBUG_GENERATOR) {
            System.out.println("objects: " + n);
            System.out.println("height: " + ht);
            System.out.println("period_min: " + l_min);
            System.out.println("period_max: " + l_max);
            System.out.println("hands: " + hands);
            System.out.println("rhythm_period: " + rhythm_period);
        }

        if (n < 1)
            throw new JuggleExceptionUser(errorstrings.getString("Error_generator_too_few_balls"));
        if (l_max == -1) {
            if (fullflag != 2)
                throw new JuggleExceptionUser(errorstrings.getString("Error_generator_must_be_prime_mode"));
            if (ht == -1)
                throw new JuggleExceptionUser(errorstrings.getString("Error_generator_underspecified"));
            l_max = JLFunc.binomial(ht * hands, n);
            l_max -= (l_max % rhythm_period);
        }
        if (ht == -1)
            ht = n * l_max;
        if (ht < 1)
            throw new JuggleExceptionUser(errorstrings.getString("Error_generator_height_too_small"));
        if (l_min < 1 || l_max < 1 || l_min > l_max)
            throw new JuggleExceptionUser(errorstrings.getString("Error_generator_period_problem"));

        output = new char[l_max * CHARS_PER_THROW];

        if (jugglers > 1 && !juggler_permutations && groundflag != 0)
            throw new JuggleExceptionUser(errorstrings.getString("Error_juggler_permutations"));

        if ((l_min % rhythm_period) != 0 || (l_max % rhythm_period) != 0) {
            String template = errorstrings.getString("Error_period_multiple");
            Object[] arguments = { Integer.valueOf(rhythm_period) };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }

        findGround();

        if (Constants.DEBUG_GENERATOR) {
            System.out.println("ground state length: " + ground_state_length);
            System.out.println("ground state:");
            printState(ground_state);
        }

        // The following variable slot_size serves two functions. It is the size
        // of a slot used in the multiplexing filter, and it is the number of
        // throws allocated in memory. The number of throws needs to be larger
        // than L sometimes, since these same structures are used to find
        // starting and ending sequences (containing as many as HT elements).

        slot_size = Math.max(ht, l_max);
        slot_size += rhythm_period - (slot_size % rhythm_period);

        for (int i = 0; i < hands; ++i)
            for (int j = 0; j < rhythm_period; ++j)
                max_occupancy = Math.max(max_occupancy, rhythm_repunit[i][j]);

        max_occupancy *= multiplex;
        if (max_occupancy == 1)  // no multiplexing, turn off filter
            mpflag = 0;

        // Include the regular expressions that define "true multiplexing"
        if (true_multiplex) {
            String include_RE = null;

            if (jugglers == 1) {
                if (mode == ASYNC)
                    include_RE = ".*\\[[^2]*\\].*";
                else if (mode == SYNC)
                    include_RE = ".*\\[([^2\\]]*2x)*[^2\\]]*\\].*";
            } else {
                if (mode == ASYNC)
                    include_RE = ".*\\[([^2\\]]*(2p|.p2|2p.))*[^2\\]]*\\].*";
                else if (mode == SYNC)
                    include_RE = ".*\\[([^2\\]]*(2p|.p2|2p.|2x|2xp|.xp2|2xp.))*[^2\\]]*\\].*";
            }

            if (include_RE != null)
                include.add(Pattern.compile(include_RE));
        }
    }

    // Initializes configuration data structures to reflect operating mode.
    protected void configMode() {
        switch (mode) {
            case ASYNC:
                rhythm_repunit = new int[jugglers][1];
                holdthrow = new int[jugglers];
                person_number = new int[jugglers];
                hands = jugglers;
                rhythm_period = 1;
                pattern_printx = false;
                for (int i = 0; i < hands; ++i) {
                    rhythm_repunit[i][0] = async_rhythm_repunit[0][0];
                    holdthrow[i] = 2;
                    person_number[i] = i + 1;
                }
                break;
            case SYNC:
                rhythm_repunit = new int[2 * jugglers][2];
                holdthrow = new int[2 * jugglers];
                person_number = new int[2 * jugglers];
                hands = 2 * jugglers;
                rhythm_period = 2;
                pattern_printx = true;
                for (int i = 0; i < hands; ++i) {
                    for (int j = 0; j < rhythm_period; ++j)
                        rhythm_repunit[i][j] = sync_rhythm_repunit[i % 2][j];
                    holdthrow[i] = 2;
                    person_number[i] = (i / 2) + 1;
                }
                break;
        }
    }

    // Allocates space for the states, rhythms, and throws in the pattern,
    // plus other incidental variables.
    protected void allocateWorkspace() {
        state = new int[l_max + 1][hands][ground_state_length];  // last index not ht because of findStartEnd()
        holes = new int[hands][l_max + ht];
        throw_to = new int[slot_size][hands][max_occupancy];  // first index not l because of findStartEnd()
        throw_value = new int[slot_size][hands][max_occupancy];

        rhythm = new int[slot_size + 1][hands][ht];
        for (int i = 0; i < (slot_size + 1); ++i)
            for (int j = 0; j < hands; ++j)
                for (int k = 0; k < ht; ++k)
                    rhythm[i][j][k] =
                        multiplex * rhythm_repunit[j][(k + i) % rhythm_period];


        if (mpflag != 0)  // allocate space for filter variables
            mp_filter = new int[l_max + 1][hands][slot_size][3];

        throws_left = new int[l_max][hands];

        if (jugglers > 1) {  // passing communication delay variables
            scratch1 = new int[hands];
            scratch2 = new int[hands];
        }

        if (connected_patterns)
            connections = new boolean[jugglers];

        if (jugglers > 1 && !juggler_permutations) {
            perm_scratch1 = new boolean[l_max];
            perm_scratch2 = new boolean[l_max];
        }
    }

    // Generates all patterns.
    //
    // It does this by generating all possible starting states recursively,
    // then calling findCycles() to find the loops for each one.
    protected int findPatterns(int balls_placed, int min_value, int min_to) throws JuggleExceptionUser, JuggleExceptionInternal {
        if (Thread.interrupted())
            throw new JuggleExceptionInterrupted();

        // check if we're done making the state
        if (balls_placed == n || groundflag == 1) {
            if (groundflag == 1) {  // find only ground state patterns?
                for (int i = 0; i < hands; ++i)
                    for (int j = 0; j < ht; ++j)
                        state[0][i][j] = ground_state[i][j];
            } else if (groundflag == 2 &&
                    compareStates(state[0], ground_state) == 0)
                return 0;  // don't find ground state patterns

            // At this point our state is completed.  Check to see if it's
            // valid. (Position X must be at least as large as position X+L,
            // where L = pattern length.) Also set up the initial multiplexing
            // filter frame, if needed.

            for (int i = 0; i < hands; ++i) {
                int j = 0;

                for ( ; j < ht; ++j) {
                    int k = state[0][i][j];

                    if (mpflag != 0 && k == 0)
                        mp_filter[0][i][j][TYPE] = MP_EMPTY;
                    else {
                        if (mpflag != 0) {
                            mp_filter[0][i][j][VALUE] = j + 1;
                            mp_filter[0][i][j][FROM] = i;
                            mp_filter[0][i][j][TYPE] = MP_LOWER_BOUND;
                        }

                        int m = j;
                        int q = 0;

                        while ((m += l_target) < ht) {
                            if ((q = state[0][i][m]) > k)
                                return 0;  // die (invalid state for this L)
                            if (mpflag != 0 && q != 0) {
                                if (q < k && j > holdthrow[i])
                                    return 0;  // different throws into same hand
                                mp_filter[0][i][j][VALUE] = m + 1;  // new bound
                            }
                        }
                    }
                }

                if (mpflag != 0)
                    for ( ; j < slot_size; ++j)
                        mp_filter[0][i][j][TYPE] = MP_EMPTY;  // clear rest of slot
            }

            if (numflag != 2 && sequenceflag)
                findStartEnd();

            if (Constants.DEBUG_GENERATOR) {
                System.out.println("Starting findCycles() from state:");
                printState(state[0]);
            }

            for (int h = 0; h < hands; ++h) {
                for (int ti = 0; ti < l_target + ht; ++ti) {
                    // calculate the number of throws we can make into a
                    // particular (hand, target index) combo
                    int num_holes;

                    // maximum number of holes we have to fill...
                    if (ti < l_target)
                        num_holes = multiplex * rhythm_repunit[h][ti % rhythm_period];
                    else
                        num_holes = state[0][h][ti - l_target];

                    // ...less those filled by throws before beat 0
                    if (ti < ht)
                        num_holes -= state[0][h][ti];

                    holes[h][ti] = num_holes;
                }
            }

            startBeat(0);
            return findCycles(0, 1, 0, 0);  // find patterns thru state
        }

        if (balls_placed == 0) {  // startup, clear state
            for (int i = 0; i < hands; ++i)
                for (int j = 0; j < ht; ++j)
                    state[0][i][j] = 0;
        }

        int num = 0;

        int j = min_to;  // ensures each state is generated only once
        for (int i = min_value; i < ht; ++i) {
            for ( ; j < hands; ++j) {
                if (state[0][j][i] < rhythm[0][j][i]) {
                    ++state[0][j][i];
                    if (i < l_target || state[0][j][i] <= state[0][j][i - l_target])
                        num += findPatterns(balls_placed + 1, i, j);  // next ball
                    --state[0][j][i];
                }
            }
            j = 0;
        }

        return num;
    }

    // Generates cycles in the state graph, starting from some given vertex.
    //
    // Arguments:
    // int pos;              // beat number in pattern that we're constructing
    // int min_throw;        // lowest we can throw this time
    // int min_hand;         // lowest hand we can throw to this time
    // int outputpos;        // current position in the char[] output buffer
    //
    // Returns the number of cycles found.
    protected int findCycles(int pos, int min_throw, int min_hand, int outputpos)
                    throws JuggleExceptionUser, JuggleExceptionInternal {
        if (Thread.interrupted())
            throw new JuggleExceptionInterrupted();

        //System.out.println("starting findCycles with pos=" + pos + ", min_throw="
        //        + min_throw + ", min_hand=" + min_hand);

        // do a time check
        if (max_time > 0) {
            if (loop_counter++ > loop_counter_max) {
                loop_counter = 0;
                if ((System.currentTimeMillis() - start_time_millis) > max_time_millis) {
                    String template = guistrings.getString("Generator_timeout");
                    Object[] arguments = { Integer.valueOf((int)max_time) };
                    throw new JuggleExceptionDone(MessageFormat.format(template, arguments));
                }
            }
        }

        // find the next hand with something to throw
        int h = 0;

        while (throws_left[pos][h] == 0) {
            ++h;

            if (h < hands)
                continue;

            // Done with this beat. Do some checks to see if things are valid
            // so far.

            // output the throw as a string so we can test for exclusions
            int outputpos_new = outputBeat(pos, output, outputpos);

            if (!areThrowsValid(pos, outputpos_new))
                return 0;
            if (mpflag != 0 && !isMultiplexingValid(pos))
                return 0;

            calculateState(pos + 1);
            if (!isStateValid(pos + 1))
                return 0;

            if (Constants.DEBUG_GENERATOR) {
                StringBuffer sb = new StringBuffer();
                for (int t = 0; t < pos; ++t)
                    sb.append(".  ");
                for (int t = outputpos; t < outputpos_new; ++t)
                    sb.append(output[t]);
                System.out.println(sb.toString());
            }

            // move to next beat
            ++pos;

            if (pos < l_target) {
                startBeat(pos);
                return findCycles(pos, 1, 0, outputpos_new);
            }

            // at the target length; does the pattern work?
            if (compareStates(state[0], state[l_target]) == 0
                        && isPatternValid(outputpos_new)) {
                if (Constants.DEBUG_GENERATOR) {
                    StringBuffer sb = new StringBuffer();
                    for (int t = 0; t < outputpos_new; ++t)
                        sb.append(output[t]);
                    System.out.println("got a pattern: " + sb.toString());
                }
                if (numflag != 2)
                    outputPattern(outputpos_new);
                return 1;
            } else
                return 0;
        }

        // Have a throw to assign. Iterate over all possibilities.

        --throws_left[pos][h];

        int slot = throws_left[pos][h];
        int k = min_hand;
        int num = 0;
        //System.out.println("check 1: k=" + k + ", min_throw=" + min_throw + ", slot=" + slot);

        for (int j = min_throw; j <= ht; ++j) {
            int ti = pos + j;  // target index

            for (; k < hands; ++k) {
                if (holes[k][ti] == 0)  // can we throw to position?
                    continue;

                --holes[k][ti];

                throw_to[pos][h][slot] = k;
                throw_value[pos][h][slot] = j;

                if (slot != 0)
                    num += findCycles(pos, j, k, outputpos);  // enforces ordering on multiplexed throws
                else
                    num += findCycles(pos, 1, 0, outputpos);

                ++holes[k][ti];

                if (max_num >= 0 && num >= max_num) {
                    String template = guistrings.getString("Generator_spacelimit");
                    Object[] arguments = { Integer.valueOf(max_num) };
                    throw new JuggleExceptionDone(MessageFormat.format(template, arguments));
                }
            }

            k = 0;
        }
        //System.out.println("check 2");

        ++throws_left[pos][h];
        return num;
    }

    // Calculates the state based on previous beat's state and throws.
    protected void calculateState(int pos) {
        if (pos == 0)
            return;

        for (int j = 0; j < hands; ++j) {  // shift state to the left
            for (int k = 0; k < ht - 1; ++k)
                state[pos][j][k] = state[pos - 1][j][k + 1];
            state[pos][j][ht - 1] = 0;
        }

        for (int j = 0; j < hands; ++j) {  // add on the last throw(s)
            for (int k = 0; k < max_occupancy; ++k) {
                int v = throw_value[pos - 1][j][k];
                if (v == 0)
                    break;

                ++state[pos][throw_to[pos - 1][j][k]][v - 1];
            }
        }
    }

    // Checks if the state is valid at a given position in the pattern.
    protected boolean isStateValid(int pos) {
        // Check if this is a valid state for a period-L pattern.
        // This check added 01/19/98.
        if (ht > l_target) {
            for (int j = 0; j < hands; ++j) {
                for (int k = 0; k < l_target; ++k) {
                    for (int o = k; o < ht - l_target; o += l_target) {
                        if (state[pos][j][o + l_target] > state[pos][j][o])
                            return false;
                    }
                }
            }
        }

        if (pos % rhythm_period == 0) {
            int cs = compareStates(state[0], state[pos]);

            if (fullflag != 0 && pos != l_target && cs == 0)  // intersection
                return false;

            if (rotflag == 0 && cs == 1)  // bad rotation
                return false;
        }

        if (fullflag == 2) {  // list only simple loops?
            for (int j = 1; j < pos; ++j) {
                if ((pos - j) % rhythm_period == 0) {
                    if (compareStates(state[j], state[pos]) == 0)
                        return false;
                }
            }
        }

        return true;
    }

    // Updates the multiplexing filter with the throws at position `pos`, and
    // checks whether the combination of throws is valid.
    //
    // The filter ensures that, other than holds, objects from only one source
    // are landing in any given hand (for example, a cluster of 3's).
    protected boolean isMultiplexingValid(int pos) {
        for (int j = 0; j < hands; ++j) {  // shift filter frame to left
            for (int k = 0; k < (slot_size - 1); ++k) {
                mp_filter[pos + 1][j][k][TYPE] =
                mp_filter[pos][j][k + 1][TYPE];
                mp_filter[pos + 1][j][k][FROM] =
                    mp_filter[pos][j][k + 1][FROM];
                mp_filter[pos + 1][j][k][VALUE] =
                    mp_filter[pos][j][k + 1][VALUE];
            }
            mp_filter[pos + 1][j][slot_size - 1][TYPE] = MP_EMPTY;
            // empty slots shift in

            if (addThrowMPFilter(mp_filter[pos + 1][j][l_target - 1],
                            j, mp_filter[pos][j][0][TYPE],
                            mp_filter[pos][j][0][VALUE],
                            mp_filter[pos][j][0][FROM]) != 0) {
                return false;
            }
        }

        for (int j = 0; j < hands; ++j) {  // add on last throw
            for (int k = 0; k < max_occupancy; ++k) {
                int m = throw_value[pos][j][k];
                if (m == 0)
                    break;

                if (addThrowMPFilter(mp_filter[pos + 1][throw_to[pos][j][k]][m - 1],
                                throw_to[pos][j][k], MP_THROW, m, j) != 0) {
                    return false;
                }
            }
        }

        return true;
    }

    // Initializes data structures to start filling in pattern at position `pos`.
    protected void startBeat(int pos) {
        for (int i = 0; i < hands; ++i) {
            throws_left[pos][i] = state[pos][i][0];

            for (int j = 0; j < max_occupancy; ++j) {
                throw_to[pos][i][j] = i;  // clear throw matrix
                throw_value[pos][i][j] = 0;
            }
        }
    }

    // Checks if the throws made on a given beat are valid.
    //
    // Test for excluded throws and a passing communication delay, as well as
    // a custom filter (if in CUSTOM mode).
    protected boolean areThrowsValid(int pos, int outputpos) {
        // check #1: test against exclusions
        for (Pattern regex : exclude) {
            if (Constants.DEBUG_GENERATOR)
                System.out.println("test exclusions for string " + (new String(output, 0, outputpos)) + " = " +
                               regex.matcher(new String(output, 0, outputpos)).matches());
            if (regex.matcher(new String(output, 0, outputpos)).matches())
                return false;
        }

        // check #2: if multiplexing, look for clustered throws if disallowed
        if (!mp_clustered) {
            for (int i = 0; i < hands; ++i) {
                if (rhythm[pos][i][0] != 0) {
                    for (int j = 0; j < max_occupancy && throw_value[pos][i][j] != 0; ++j) {
                        for (int l = 0; l < j; ++l) {
                            if (throw_value[pos][i][j] == throw_value[pos][i][l]
                                    && throw_to[pos][i][j] == throw_to[pos][i][l])
                                return false;
                        }
                    }
                }
            }
        }

        // check #3: if passing, look for an adequate communication delay
        if (jugglers > 1 && pos < delaytime) {
            // Count the number of balls being thrown, assuming no
            // multiplexing. Also check if leader is forcing others to
            // multiplex or make no throw.
            int balls_thrown = 0;
            for (int i = 0; i < hands; ++i) {
                if (rhythm[pos][i][0] != 0) {
                    ++balls_thrown;
                    if (state[pos][i][0] != 1 && person_number[i] != leader_person)
                        return false;
                }
            }

            int balls_left = n;
            for (int i = 0; i < ht && balls_left != 0; ++i) {
                for (int j = 0; j < hands && balls_left != 0; ++j) {
                    if (rhythm[pos + 1][j][i] != 0) {
                        if (--balls_left < balls_thrown) {
                            scratch1[balls_left] = j;  // dest hand #
                            scratch2[balls_left] = i + 1;  // dest value
                        }
                    }
                }
            }

            if (balls_left != 0)
                return false;  // shouldn't happen, but die anyway

            for (int i = 0; i < hands; ++i) {
                if (state[pos][i][0] != 0 && person_number[i] != leader_person) {
                    boolean found_spot = false;

                    for (int j = 0; j < balls_thrown; ++j)
                        if (scratch1[j] == throw_to[pos][i][0] &&
                                    scratch2[j] == throw_value[pos][i][0]) {
                            scratch2[j] = 0;  // don't throw to spot again
                            found_spot = true;
                            break;
                        }
                    if (!found_spot)
                        return false;
                }
            }
        }

        return true;
    }

    // Tests if a completed pattern is valid.
    protected boolean isPatternValid(int outputpos) {
        // check #1: verify against inclusions
        for (Pattern regex : include) {
            if (!regex.matcher(new String(output, 0, outputpos)).matches()) {
                if (Constants.DEBUG_GENERATOR)
                    System.out.println("   pattern invalid: missing inclusion");
                return false;
            }
        }

        // check #2: look for '11' sequence.
        if (mode == ASYNC && lameflag && max_occupancy == 1) {
            for (int i = 0; i < (l_target - 1); ++i) {
                for (int j = 0; j < hands; ++j) {
                    if (throw_value[i][j][0] == 1 &&
                                person_number[throw_to[i][j][0]] == person_number[j] &&
                                throw_value[i+1][j][0] == 1 &&
                                person_number[throw_to[i+1][j][0]] == person_number[j]) {
                        if (Constants.DEBUG_GENERATOR)
                            System.out.println("  pattern invalid: 11 sequence");
                        return false;
                    }
                }
            }
        }

        // check #3: if pattern is composite, ensure we only print one rotation of it.
        // (Added 12/4/2002)
        if (fullflag == 0 && rotflag == 0) {
            for (int i = 1; i < l_target; ++i) {
                if (i % rhythm_period == 0) {  // can we compare states?
                    if (compareStates(state[0], state[i]) == 0) {
                        if (compareRotations(0, i) < 0) {
                            if (Constants.DEBUG_GENERATOR)
                                System.out.println("   pattern invalid: bad rotation");
                            return false;
                        }
                    }
                }
            }
        }

        // check #4: if passing, test whether pattern is connected if enabled.
        if (connected_patterns) {
            for (int i = 0; i < jugglers; ++i)
                connections[i] = false;
            connections[0] = true;

            boolean changed = true;
            while (changed) {
                changed = false;

                for (int i = 0; i < l_target; ++i) {
                    for (int j = 0; j < hands; ++j) {
                        if (connections[person_number[j] - 1])
                            continue;
                        for (int k = 0; k < max_occupancy && throw_value[i][j][k] > 0; ++k) {
                            int p = person_number[throw_to[i][j][k]];

                            if (connections[p - 1]) {
                                connections[person_number[j] - 1] = true;
                                changed = true;
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < jugglers; ++i) {
                if (!connections[i]) {
                    if (Constants.DEBUG_GENERATOR)
                        System.out.println("   pattern invalid: not connected");
                    return false;
                }
            }
        }

        // check #5: See if there is a better permutation of jugglers.
        //
        // This algorithm is not guaranteed to eliminate all permuted duplicates,
        // but will do so in the vast majority of cases.
        if (jugglers > 1 && !juggler_permutations) {
            for (int m = 1; m <= (jugglers - 1); ++m) {
                // compare juggler m against juggler (m+1)
                for (int i = 0; i < l_target; ++i)
                    perm_scratch1[i] = perm_scratch2[i] = false;

                for (int p = 0; p < l_target; ++p) {
                    int scorem = -1, scoremp1 = -1, maxm = 0, maxmp1 = 0;

                    for (int i = 0; i < l_target; ++i) {
                        if (!perm_scratch1[i]) {
                            int scoretemp = 0;

                            for (int j = 0; j < hands; ++j) {
                                if (person_number[j] != m)
                                    continue;
                                for (int k = 0; k < max_occupancy && throw_value[i][j][k] > 0; ++k) {
                                    scoretemp += 4 * throw_value[i][j][k] * (2*max_occupancy) * (2*max_occupancy);
                                    if (throw_to[i][j][k] != j) {
                                        scoretemp += 2 * (2*max_occupancy);
                                        if (person_number[throw_to[i][j][k]] != m)
                                            scoretemp += 1;
                                    }
                                }
                            }

                            if (scoretemp > scorem) {
                                scorem = scoretemp;
                                maxm = i;
                            }
                        }
                        if (!perm_scratch2[i]) {
                            int scoretemp = 0;

                            for (int j = 0; j < hands; ++j) {
                                if (person_number[j] != (m+1))
                                    continue;
                                for (int k = 0; k < max_occupancy && throw_value[i][j][k] > 0; ++k) {
                                    scoretemp += 4 * throw_value[i][j][k] * (2*max_occupancy) * (2*max_occupancy);
                                    if (throw_to[i][j][k] != j) {
                                        scoretemp += 2 * (2*max_occupancy);
                                        if (person_number[throw_to[i][j][k]] != (m+1))
                                            scoretemp += 1;
                                    }
                                }
                            }

                            if (scoretemp > scoremp1) {
                                scoremp1 = scoretemp;
                                maxmp1 = i;
                            }
                        }
                    }

                    if (scoremp1 > scorem) {
                        if (Constants.DEBUG_GENERATOR)
                            System.out.println("   pattern invalid: bad juggler permutation");
                        return false;
                    }
                    if (scoremp1 < scorem)
                        break;      // go to the next pair of jugglers

                    perm_scratch1[maxm] = perm_scratch2[maxmp1] = true;
                }
            }
        }

        return true;
    }

    // Compares two rotations of the same pattern.
    //
    // This method assumes the throws are comparable, i.e., that pos1 is
    // congruent to pos2 mod rhythm_period
    protected int compareRotations(int pos1, int pos2) {
        for (int i = 0; i < l_target; ) {
            int res = compareLoops((pos1 + i) % l_target, (pos2 + i) % l_target);
            if (res > 0)
                return 1;
            else if (res < 0)
                return -1;

            ++i;
            for (; i < l_target; ++i) {
                if (compareStates(state[pos1], state[(pos1 + i) % l_target]) == 0)
                    break;
            }
        }
        return 0;
    }

    // Compares two generated loops.
    protected int compareLoops(int pos1, int pos2) {
        int[][] state_start = state[pos1];
        int result = 0;
        int i = 0;

        // Rule 1:  The longer loop is always greater
        // Rule 2:  For loops of equal length, use throw-by-throw comparison
        // Rule 3:  Loops are equal only if the respective throws are identical

        while (true) {
            ++i;

            if (result == 0)
                result = compareThrows(pos1, pos2);

            if (i % rhythm_period == 0) {
                int cs1 = compareStates(state[pos1 + 1], state_start);
                int cs2 = compareStates(state[pos2 + 1], state_start);

                if (cs1 == 0) {
                    if (cs2 == 0)
                        return result;
                    return -1;
                }
                if (cs2 == 0)
                    return 1;
            }

            ++pos1;
            ++pos2;
        }
    }

    // Compares two throws.
    //
    // Returns 1 if the throw at pos1 is greater than the throw at pos2,
    // -1 if lesser, and 0 iff the throws are identical.
    //
    // This method assumes the throws are comparable, i.e., that pos1 is congruent
    // to pos2 mod rhythm_period.
    protected int compareThrows(int pos1, int pos2) {
        int[][] value1 = throw_value[pos1];
        int[][] to1 = throw_to[pos1];
        int[][] value2 = throw_value[pos2];
        int[][] to2 = throw_to[pos2];
        int[][] rhy = rhythm[pos1];  // same as pos2 since throws comparable

        for (int i = 0; i < hands; ++i) {
            for (int j = 0; j < rhy[i][0]; ++j) {
                if (value1[i][j] > value2[i][j])
                    return 1;
                else if (value1[i][j] < value2[i][j])
                    return -1;
                else if (to1[i][j] > to2[i][j])
                    return 1;
                else if (to1[i][j] < to2[i][j])
                    return -1;
            }
        }

        return 0;
    }

    // Compares two states.
    //
    // Returns 1 if state1 > state2, -1 if state1 < state2, and 0 iff state1
    // and state2 are identical.
    protected int compareStates(int state1[][], int state2[][]) {
        int mo1 = 0;
        int mo2 = 0;

        for (int i = 0; i < hands; ++i) {
            for (int j = 0; j < ht; ++j) {
                if (state1[i][j] > mo1)
                    mo1 = state1[i][j];
                if (state2[i][j] > mo2)
                    mo2 = state2[i][j];
            }
        }

        if (mo1 > mo2)
            return 1;
        if (mo1 < mo2)
            return -1;

        for (int j = (ht - 1); j >= 0; --j) {
            for (int i = (hands - 1); i >= 0; --i) {
                mo1 = state1[i][j];
                mo2 = state2[i][j];
                if (mo1 > mo2)
                    return 1;
                if (mo1 < mo2)
                    return -1;
            }
        }

        return 0;
    }

    // Returns number as single character
    protected static char convertNumber(int value) {
        return Character.toLowerCase(Character.forDigit(value, 36));
    }

    // Prints the throws for a given beat
    protected int outputBeat(int pos, char[] out, int outpos) {
        boolean no_throw = true;
        for (int i = 0; i < rhythm[pos].length; ++i)
            if (rhythm[pos][i][0] != 0) {
                no_throw = false;
                break;
            }
        if (no_throw)  // no throw on this beat
            return outpos;

        boolean x_space = (outpos > 0);  // for printing 'x'-valued throws

        if (jugglers > 1) {
            out[outpos++] = '<';
            x_space = false;
        }

        for (int i = 1; i <= jugglers; ++i) {
            // first find the hand numbers corresponding to the person
            int lo_hand = 0;
            while (person_number[lo_hand] != i)
                ++lo_hand;

            int hi_hand = lo_hand;
            while (hi_hand < hands && person_number[hi_hand] == i)
                ++hi_hand;

            // check rhythm to see how many hands are throwing
            int num_hands_throwing = 0;
            for (int j = lo_hand; j < hi_hand; ++j)
                if (rhythm[pos][j][0] != 0)
                    ++num_hands_throwing;

            if (num_hands_throwing > 0) {
                boolean parens = false;

                if (num_hands_throwing > 1) {
                    out[outpos++] = '(';
                    x_space = false;
                    parens = true;
                }

                for (int j = lo_hand; j < hi_hand; ++j) {
                    if (rhythm[pos][j][0] == 0)  // this hand supposed to throw?
                        continue;

                    boolean is_multiplex = false;

                    if (max_occupancy > 1 && throw_value[pos][j][1] > 0) {
                        out[outpos++] = '[';  // multiplexing?
                        x_space = false;
                        is_multiplex = true;
                    }

                    // loop over the throws coming out of this hand

                    boolean got_throw = false;

                    for (int k = 0; k < max_occupancy && throw_value[pos][j][k] > 0; ++k) {
                        got_throw = true;

                        if (throw_value[pos][j][k] == 33 && x_space)
                            out[outpos++] = ' ';

                        out[outpos++] = convertNumber(throw_value[pos][j][k]);  // print throw value
                        x_space = true;

                        if (hands > 1) {  // potential ambiguity about destination?
                            int target_juggler = person_number[throw_to[pos][j][k]];

                            // print destination hand, if needed
                            if (pattern_printx) {
                                // find hand # of destination person
                                int q = throw_to[pos][j][k] - 1;
                                int dest_hand = 0;
                                while (q >= 0 && person_number[q] == target_juggler) {
                                    --q;
                                    ++dest_hand;
                                }

                                if (dest_hand != (j - lo_hand))
                                    out[outpos++] = 'x';
                            }

                            // print pass modifier and person number, if needed
                            if (target_juggler != i) {
                                out[outpos++] = 'p';
                                if (jugglers > 2)
                                    out[outpos++] = convertNumber(target_juggler);
                            }
                            /*
                            // destination person has 1 hand, don't print
                            if ((ch != 'a') || ((q < (hands - 2)) &&
                                                (person_number[q + 2] == m)))
                            out[outpos++] = ch;             // print it
                            */
                        }

                        // another multiplexed throw?
                        if (is_multiplex && jugglers > 1 &&
                                k != (max_occupancy - 1) && throw_value[pos][j][k + 1] > 0) {
                            out[outpos++] = '/';
                            x_space = false;
                        }
                    }

                    if (!got_throw) {
                        out[outpos++] = '0';
                        x_space = true;
                    }

                    if (is_multiplex) {
                        out[outpos++] = ']';
                        x_space = false;
                    }

                    if (j < (hi_hand - 1) && parens) {  // put comma between hands
                        out[outpos++] = ',';
                        x_space = false;
                    }
                }
                if (parens) {
                    out[outpos++] = ')';
                    x_space = false;
                }
            }
            if (i < jugglers) {           // another person throwing next?
                out[outpos++] = '|';
                x_space = false;
            }
        }

        if (jugglers > 1)
            out[outpos++] = '>';

        return outpos;
    }

    protected void outputPattern(int outputpos) throws JuggleExceptionInternal {
        boolean is_excited = false;
        StringBuffer outputline = new StringBuffer(hands
                * (2 * ground_state_length + l_target) * CHARS_PER_THROW + 10);
        StringBuffer outputline2 = new StringBuffer(hands
                * (2 * ground_state_length + l_target) * CHARS_PER_THROW + 10);

        if (groundflag != 1) {
            if (sequenceflag) {
                if (mode == ASYNC) {
                    for (int i = n - starting_seq_length; i > 0; --i)
                        outputline.append(" ");
                }
                outputline.append(starting_seq, 0, starting_seq_length);
                outputline.append("  ");
            } else {
                is_excited = (compareStates(ground_state, state[0]) != 0);

                if (is_excited)
                    outputline.append("* ");
                else
                    outputline.append("  ");
            }
        }

        outputline.append(output, 0, outputpos);
        outputline2.append(output, 0, outputpos);

        if (groundflag != 1) {
            if (sequenceflag) {
                outputline.append("  ");
                outputline.append(ending_seq, 0, ending_seq_length);
                // add proper number of trailing spaces too, so formatting is
                // aligned in RTL languages
                if (mode == ASYNC) {
                    for (int i = n - ending_seq_length; i > 0; --i)
                        outputline.append(" ");
                }
            } else {
                if (is_excited)
                    outputline.append(" *");
                else
                    outputline.append("  ");
            }
        }

        target.writePattern(outputline.toString(), "siteswap", outputline2.toString().trim());
    }

    // Adds a throw to a multiplexing filter slot (part of the multiplexing
    // filter).
    //
    // Returns 1 if there is a collision, 0 otherwise.
    protected int addThrowMPFilter(int dest_slot[], int slot_hand, int type, int value, int from) {
        switch (type) {
            case MP_EMPTY:
                return 0;
            case MP_LOWER_BOUND:
                if (dest_slot[TYPE] == MP_EMPTY) {
                    dest_slot[TYPE] = MP_LOWER_BOUND;
                    dest_slot[VALUE] = value;
                    dest_slot[FROM] = from;
                }
                return 0;
            case MP_THROW:
                if (from == slot_hand && value == holdthrow[slot_hand])
                    return 0;  // throw is a hold, so ignore it

                switch (dest_slot[TYPE]) {
                    case MP_EMPTY:
                        dest_slot[TYPE] = MP_THROW;
                        dest_slot[VALUE] = value;
                        dest_slot[FROM] = from;
                        return 0;
                    case MP_LOWER_BOUND:
                        if (dest_slot[VALUE] <= value
                                || dest_slot[VALUE] <= holdthrow[slot_hand]) {
                            dest_slot[TYPE] = MP_THROW;
                            dest_slot[VALUE] = value;
                            dest_slot[FROM] = from;
                            return 0;
                        }
                        break;  // kill recursion
                    case MP_THROW:
                        if (dest_slot[FROM] == from && dest_slot[VALUE] == value)
                            return 0;  // throws from same place (cluster)
                        break;
                }
                    break;
        }

        return 1;
    }

    // Finds valid starting and ending sequences for excited state patterns.
    // Note that these sequences are not unique.
    //
    // Rewritten on 12/31/03
    protected void findStartEnd() {
        // find the number of beats in starting sequence
        int start_beats = 0;

        findstarting1:
        do {
            for (int j = 0; j < hands; ++j) {
                for (int k = 0; k < ht; ++k) {
                    // use p_s[1] as scratch
                    if ((k + start_beats) < ground_state_length)
                        state[1][j][k] = ground_state[j][k + start_beats];
                    else
                        state[1][j][k] = 0;

                    if (state[1][j][k] > state[0][j][k]) {
                        start_beats += rhythm_period;
                        continue findstarting1;
                    }

                    state[1][j][k] = state[0][j][k] - state[1][j][k];
                }
            }

            break;
        } while (true);

        for (int i = 0; i < start_beats; ++i) {
            for (int j = 0; j < hands; ++j) {
                for (int k = 0; k < max_occupancy; ++k) {
                    throw_value[i][j][k] = 0;
                    throw_to[i][j][k] = j;
                }

                if (i >= ground_state_length || ground_state[j][i] == 0)
                    continue;

                findstarting2:
                for (int k = 0; k < ht; ++k) {
                    for (int m = 0; m < hands; ++m) {
                        if (state[1][m][k] > 0) {
                            --state[1][m][k];

                            throw_value[i][j][0] = k + start_beats - i;
                            throw_to[i][j][0] = m;
                            break findstarting2;
                        }
                    }
                }
            }
        }

        // write starting sequence to buffer
        starting_seq = new char[hands * start_beats * CHARS_PER_THROW];
        starting_seq_length = 0;

        for (int i = 0; i < start_beats; ++i)
            starting_seq_length = outputBeat(i, starting_seq, starting_seq_length);

        // Construct an ending sequence. Unlike the starting sequence above,
        // this time work forward to ground state.

        int end_beats = 0;

        findending1:
        do {
            for (int j = 0; j < hands; ++j) {
                for (int k = 0; k < ground_state_length; ++k) {
                    // use state[1] as scratch
                    if ((k + end_beats) < ht)
                        state[1][j][k] = state[0][j][k+end_beats];
                    else
                        state[1][j][k] = 0;

                    if (state[1][j][k] > ground_state[j][k]) {
                        end_beats += rhythm_period;
                        continue findending1;
                    }

                    state[1][j][k] = ground_state[j][k] - state[1][j][k];
                }
            }

            break;
        } while (true);

        for (int i = 0; i < end_beats; ++i) {
            for (int j = 0; j < hands; ++j) {
                for (int k = 0; k < max_occupancy; ++k) {
                    throw_value[i][j][k] = 0;
                    throw_to[i][j][k] = j;
                }

                if (i >= ht)
                    continue;

                for (int q = 0; q < state[0][j][i]; ++q) {
                    findending2:
                    for (int k = 0; k < ground_state_length; ++k) {
                        for (int m = 0; m < hands; ++m) {
                            if (state[1][m][k] > 0) {
                                --state[1][m][k];

                                throw_value[i][j][q] = k + end_beats - i;
                                throw_to[i][j][q] = m;
                                break findending2;
                            }
                        }
                    }
                }
            }
        }

        ending_seq = new char[hands * end_beats * CHARS_PER_THROW];
        ending_seq_length = 0;

        for (int i = 0; i < end_beats; ++i)
            ending_seq_length = outputBeat(i, ending_seq, ending_seq_length);
    }

    // Finds the ground state for our rhythm. It does so by putting the balls
    // into the lowest possible slots, with no multiplexing.
    protected void findGround() {
        int balls_left = n;

        for (int i = 0; balls_left != 0; ++i) {
            for (int j = 0; j < hands && balls_left != 0; ++j) {
                if (rhythm_repunit[j][i % rhythm_period] != 0) {
                    --balls_left;
                    if (balls_left == 0)
                        ground_state_length = i + 1;
                }
            }
        }

        if (ground_state_length < ht)
            ground_state_length = ht;

        ground_state = new int[hands][ground_state_length];

        for (int i = 0; i < hands; ++i)  // clear ground state array
            for (int j = 0; j < ground_state_length; ++j)
                ground_state[i][j] = 0;

        balls_left = n;
        for (int i = 0; balls_left != 0; ++i) {
            for (int j = 0; j < hands && balls_left != 0; ++j) {
                if (rhythm_repunit[j][i % rhythm_period] != 0) {  // available slots
                    ground_state[j][i] = 1;
                    --balls_left;
                }
            }
        }
    }

    // Outputs the state to the command line (useful for debugging).
    protected void printState(int[][] st) {
        int last_index = 0;
        for (int i = 0; i < ground_state_length; ++i) {
            for (int j = 0; j < hands; ++j) {
                if (st[j][i] != 0)
                    last_index = i;
            }
        }
        for (int i = 0; i <= last_index; ++i)
            for (int j = 0; j < hands; ++j)
                System.out.println("  s[" + j + "][" + i + "] = " + st[j][i]);
    }


    /*
    // Reads a custom rhythm file and parses it. If there is an error it
    // prints a message and exits.
    void custom_initialize(char *custom_file) {
        int i, j, k, left_delim, right_delim;
        int last_period, last_person, person, hold, second_pass;
        char ch, *file_buffer;
        FILE *fp;

        if ((fp = fopen(custom_file, "r")) == NULL) {
            printf("File error: cannot open '%s'\n", custom_file);
            exit(0);
        }
        if ((file_buffer = (char *)malloc(BUFFER_SIZE * sizeof(char))) == 0)
            die();

        for (second_pass = 0; second_pass < 2; second_pass++) {
            hands = j = 0;
            jugglers = last_person = 1;

            do {
                ch = (char)(i = fgetc(fp));

                if ((ch == (char)10) || (i == EOF)) {
                    file_buffer[j] = (char)0;

                    for (j = 0, k = 0; (ch = file_buffer[j]) && (ch != ';'); ++j)
                        if (ch == '|') {
                            if (++k == 1)
                                left_delim = j;
                            else if (k == 2)
                                right_delim = j;
                        }
                            if (ch == ';')
                                file_buffer[j] = (char)0;        // terminate at comment

                    if (k) {
                        if (k != 2) {
                            printf("File error: need two rhythm delimiters per hand\n");
                            exit(0);
                        }
                        // At this point the line checks out.  See if
                        // period is what we got last time.
                        if (hands && ((right_delim-left_delim-1) != last_period)) {
                            printf("File error: rhythm period not constant\n");
                            exit(0);
                        }
                        last_period = right_delim - left_delim - 1;

                        // Now parse the line we've read in

                        file_buffer[left_delim] = (char)0;
                        person = atoi(file_buffer);

                        if (hands) {
                            if (person == (last_person + 1)) {
                                jugglers++;
                                last_person = person;
                            } else if (person != last_person) {
                                printf("File error: person numbers goofed up\n");
                                exit(0);
                            }
                        } else if (person != 1) {
                            printf("File error: must start with person number 1\n");
                            exit(0);
                        }

                        // Now put stuff in the allocated arrays

                        if (second_pass) {
                            person_number[hands] = person;
                            hold = atoi(file_buffer + right_delim + 1);
                            holdthrow[hands] = (hold ? hold : 2);

                            // Fill the rhythm matrix
                            for (j = 0; j < rhythm_period; ++j) {
                                ch = file_buffer[j + left_delim + 1];
                                if (((ch < '0') || (ch > '9')) && (ch != ' ')) {
                                    printf("File error: bad character in rhythm\n");
                                    exit(0);
                                }
                                if (ch == ' ')
                                    ch = '0';
                                rhythm_repunit[hands][j] = (int)(ch - '0');
                            }
                        }

                        hands++;   // got valid line, increment counter
                    }
                    j = 0;    // reset buffer pointer for next read
                } else {
                    file_buffer[j] = ch;
                    if (++j >= BUFFER_SIZE) {
                        printf("File error: input buffer overflow\n");
                        exit(0);
                    }
                }
            } while (i != EOF);

            if (!hands) {
                printf("File error: must have at least one hand\n");
                exit(0);
            }

            if (!second_pass) {        // allocate space after first pass
                rhythm_period = last_period;
                rhythm_repunit = alloc_array(hands, rhythm_period);
                if ((holdthrow = (int *)malloc(hands * sizeof(int))) == 0)
                    die();
                if ((person_number = (int *)malloc(hands * sizeof(int))) == 0)
                    die();
                rewind(fp);          // go back to start of file
            }

        }

        (void)fclose(fp);        // close file and free memory
        free(file_buffer);
    }
    */

    // Reformats the exclude/include terms into standard regular expressions.
    // Exchange "\x" for "x", where x is one of the RE metacharacters that conflicts
    // with siteswap notation: []()|
    protected static String makeStandardRegex(String term) {
        String res;

        res = Pattern.compile("\\\\\\[").matcher(term).replaceAll("@");
        res = Pattern.compile("\\[").matcher(res).replaceAll("\\\\[");
        res = Pattern.compile("@").matcher(res).replaceAll("[");
        res = Pattern.compile("\\\\\\]").matcher(res).replaceAll("@");
        res = Pattern.compile("\\]").matcher(res).replaceAll("\\\\]");
        res = Pattern.compile("@").matcher(res).replaceAll("]");

        res = Pattern.compile("\\\\\\(").matcher(res).replaceAll("@");
        res = Pattern.compile("\\(").matcher(res).replaceAll("\\\\(");
        res = Pattern.compile("@").matcher(res).replaceAll("(");
        res = Pattern.compile("\\\\\\)").matcher(res).replaceAll("@");
        res = Pattern.compile("\\)").matcher(res).replaceAll("\\\\)");
        res = Pattern.compile("@").matcher(res).replaceAll(")");

        res = Pattern.compile("\\\\\\|").matcher(res).replaceAll("@");
        res = Pattern.compile("\\|").matcher(res).replaceAll("\\\\|");
        res = Pattern.compile("@").matcher(res).replaceAll("|");

        return res;
    }

    //--------------------------------------------------------------------------
    // Static methods to run the generator from the command line
    //--------------------------------------------------------------------------

    public static void runGeneratorCLI(String[] args, GeneratorTarget target) {
        if (args.length < 3) {
            String template = guistrings.getString("Version");
            Object[] arg1 = { Constants.version };
            String output = "Juggling Lab " +
                            MessageFormat.format(template, arg1).toLowerCase() + "\n";

            template = guistrings.getString("Copyright_message");
            Object[] arg2 = { Constants.year };
            output += MessageFormat.format(template, arg2) + "\n\n";

            output += guistrings.getString("GPL_message") + "\n\n";
            output += guistrings.getString("Generator_intro");

            System.out.println(output);
            return;
        }

        if (target == null)
            return;

        try {
            SiteswapGenerator ssg = new SiteswapGenerator();
            ssg.initGenerator(args);
            ssg.runGenerator(target);
        } catch (Exception e) {
            System.out.println(errorstrings.getString("Error")+": "+e.getMessage());
        }
    }

    public static void main(String[] args) {
        SiteswapGenerator.runGeneratorCLI(args, new GeneratorTarget(System.out));
    }

}
