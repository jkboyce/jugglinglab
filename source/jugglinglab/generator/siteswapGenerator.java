// siteswapGenerator.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.generator;


import javax.swing.*;
import java.util.*;
import java.util.regex.*;
import java.text.MessageFormat;

import jugglinglab.util.*;
import jugglinglab.core.*;


// This class is a port of the J2 siteswap pattern generator to Java.
// For now the "custom" mode has been commented out, although at some
// point it might be interesting to have a graphical rhythm editor.

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

public class siteswapGenerator extends Generator {
    // Different types of siteswaps
    protected final static int ASYNCH = 0;	/* different types of modes */
    protected final static int SYNCH = 1;
    //	protected final static int CUSTOM = 2;

    // Types of multiplexing filter slots
    private final static int MP_EMPTY = 0;
    private final static int MP_THROW = 1;
    private final static int MP_LOWER_BOUND = 2;
    private final static int TYPE = 0;
    private final static int FROM = 1;
    private final static int VALUE = 2;

    // max. # of chars. printed per throw
    private final static int CHARS_PER_THROW = 50;

    protected final static int asynch_rhythm_repunit[][] = { { 1 } };
    protected final static int synch_rhythm_repunit[][] = { { 1, 0 }, { 1, 0 } };

    // Instance variables
    protected int pattern_rhythm[][][], pattern_state[][][];
    protected int pattern_throwcount[][], pattern_holes[][][];
    protected int pattern_throw_to[][][], pattern_throw_value[][][];
    protected int pattern_filter[][][][];
    protected boolean pattern_printx;

    protected int hands, max_occupancy, leader_person;
    protected int rhythm_repunit[][], rhythm_period;
    protected int holdthrow[], person_number[], scratch1[], scratch2[];
    protected int ground_state[][];
    protected int ground_state_length;
    protected int n, ht, l, llow, lhigh;
	protected Vector exclude, include;
	protected char[] output;
	protected int outputpos;
    protected int numflag, groundflag;
    protected int rotflag;	// added 12/15/02
    protected int fullflag, mp_filter, delaytime;
    protected boolean mp_clustered;
    protected boolean lameflag, sequenceflag;
	protected boolean connected_patterns;
	protected boolean[] connections;
	protected boolean juggler_permutations;
	protected boolean[] perm_scratch1, perm_scratch2;
    protected int mode, jugglers, slot_size;
    protected char[] starting_seq, ending_seq;
	protected int starting_seq_length, ending_seq_length;

    protected GeneratorTarget target;
    protected int max_num;				// maximum number of patterns to print
    protected double max_time;			// maximum number of seconds
    protected long max_time_millis;		// maximum number of milliseconds
    protected long start_time_millis;   // start time of run, in milliseconds
    protected int loop_counter;			// gen_loop() counter for checking timeout
    protected final static int loop_counter_max = 20000;

    protected siteswapGeneratorControl control; // contains controls for generator


    // The public methods

    public String getStartupMessage() {
        return "Welcome to the J2 Siteswap Generator";
    }

    public JPanel getGeneratorControls() {
        if (control == null)
            control = new siteswapGeneratorControl();
        return control;
    }

    public void resetGeneratorControls() {
        if (control != null)
            control.resetControl();
    }

    public void initGenerator() throws JuggleExceptionUser {
        if (control == null)
            initGenerator("5 7 5");		// default settings
        else
            initGenerator(control.getParams());
    }

    public void initGenerator(String[] args) throws JuggleExceptionUser {
        int i, j, k, multiplex = 1;
		boolean true_multiplex = false;
		
        if (args.length < 3)
            throw new JuggleExceptionUser(errorstrings.getString("Error_generator_insufficient_input"));

        max_occupancy = 0;
        leader_person = 1;
        numflag = 0;
        groundflag = 0;
        rotflag = 0;
        fullflag = mp_filter = 1;
        mp_clustered = true;
        delaytime = 0;
        lameflag = false;
		connected_patterns = false;
		juggler_permutations = false;
        sequenceflag = true;
        mode = ASYNCH;          /* default mode */
        jugglers = 1;
        target = null;

		exclude = new Vector();
		include = new Vector();

        for (i = 3; i < args.length; i++) {
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
                mode = SYNCH;
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
                mp_filter = 0;
            else if (args[i].equals("-mc"))
                mp_clustered = false;
			else if (args[i].equals("-mt"))
				true_multiplex = true;
            else if (args[i].equals("-m")) {
                if ((i < (args.length - 1)) && (args[i + 1].charAt(0) != '-')) {
                    try {
                        multiplex = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
						String template = errorstrings.getString("Error_number_format");
						String str = guistrings.getString("simultaneous_throws");
						Object[] arguments = { str };					
						throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    i++;
                }
            }
            else if (args[i].equals("-j")) {
                if ((i < (args.length - 1)) && (args[i + 1].charAt(0) != '-')) {
                    try {
                        jugglers = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
 						String template = errorstrings.getString("Error_number_format");
						String str = guistrings.getString("Jugglers");
						Object[] arguments = { str };					
						throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    i++;
                }
            }
            else if (args[i].equals("-d")) {
                if ((i < (args.length - 1)) && (args[i + 1].charAt(0) != '-')) {
                    try {
                        delaytime = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
  						String template = errorstrings.getString("Error_number_format");
						String str = guistrings.getString("Passing_communication_delay");
						Object[] arguments = { str };					
						throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    groundflag = 1;        // find only ground state tricks
                    i++;
                }
            }
            else if (args[i].equals("-l")) {
                if ((i < (args.length - 1)) && (args[i + 1].charAt(0) != '-')) {
                    try {
                        leader_person = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException nfe) {
   						String template = errorstrings.getString("Error_number_format");
						String str = guistrings.getString("Error_passing_leader_number");
						Object[] arguments = { str };					
						throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                    }
                    i++;
                }
            }
            else if (args[i].equals("-x")) {
                i++;
                while ((i < args.length) && (args[i].charAt(0) != '-')) {
                    try {
						String re = make_standard_RE(args[i]);
						if (re.indexOf("^") < 0)
							re = ".*" + re + ".*";
						exclude.add(Pattern.compile(re));
                    } catch (PatternSyntaxException pse) {
                        throw new JuggleExceptionUser(errorstrings.getString("Error_excluded_throws"));
                    }
                    i++;
                }
                i--;
            }
            else if (args[i].equals("-i")) {
                i++;
                while ((i < args.length) && (args[i].charAt(0) != '-')) {
                    try {
						String re = make_standard_RE(args[i]);
						if (re.indexOf("^") < 0)
							re = ".*" + re;
						if (re.indexOf("$") < 0)
							re = re + ".*";
						include.add(Pattern.compile(re));
                    } catch (PatternSyntaxException ps) {
                        throw new JuggleExceptionUser(errorstrings.getString("Error_included_throws"));
                    }
                    i++;
                }
                i--;
            } else {
				String template = errorstrings.getString("Error_unrecognized_option");
				Object[] arguments = { args[i] };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
			}
        }

        //	if (mode != CUSTOM)
        initialize();

        try {
            n = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
			String template = errorstrings.getString("Error_number_format");
			String str = guistrings.getString("balls");
			Object[] arguments = { str };					
			throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
        try {
			if (args[1].equals("-"))	// signal to not specify a maximum throw
				ht = -1;
			else
				ht = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
 			String template = errorstrings.getString("Error_number_format");
			String str = guistrings.getString("max._throw");
			Object[] arguments = { str };					
			throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }
        try {
			if (args[2].equals("-")) {
				llow = rhythm_period;
				lhigh = -1;
			} else {
				int divider = args[2].indexOf('-');
				if (divider == 0) {
					llow = rhythm_period;
					lhigh = Integer.parseInt(args[2].substring(1));
				} else if (divider == (args[2].length() - 1)) {
					llow = Integer.parseInt(args[2].substring(0, divider));
					lhigh = -1;
				} else if (divider > 0) {
					llow = Integer.parseInt(args[2].substring(0, divider));
					lhigh = Integer.parseInt(args[2].substring(divider + 1));
				} else {
					llow = lhigh = Integer.parseInt(args[2]);
				}
			}
        } catch (NumberFormatException nfe) {
 			String template = errorstrings.getString("Error_number_format");
			String str = guistrings.getString("period");
			Object[] arguments = { str };					
			throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }

        if (n < 1)
            throw new JuggleExceptionUser(errorstrings.getString("Error_generator_too_few_balls"));
		if (lhigh == -1) {
			if (fullflag != 2)
				throw new JuggleExceptionUser(errorstrings.getString("Error_generator_must_be_prime_mode"));
			if (ht == -1)
				throw new JuggleExceptionUser(errorstrings.getString("Error_generator_underspecified"));
			lhigh = JLMath.choose(ht * hands, n);
			lhigh -= (lhigh % rhythm_period);
		}
		if (ht == -1)
			ht = n * lhigh;
        if (ht < 1)
            throw new JuggleExceptionUser(errorstrings.getString("Error_generator_height_too_small"));
        if ((llow < 1) || (lhigh < 1) || (llow > lhigh))
            throw new JuggleExceptionUser(errorstrings.getString("Error_generator_period_problem"));

		output = new char[lhigh * CHARS_PER_THROW];
		outputpos = 0;
		
        
		if ((jugglers > 1) && !juggler_permutations && (groundflag != 0))
			throw new JuggleExceptionUser(errorstrings.getString("Error_juggler_permutations"));
		
        if (((llow % rhythm_period) != 0) || (lhigh % rhythm_period) != 0) {
 			String template = errorstrings.getString("Error_period_multiple");
			Object[] arguments = { new Integer(rhythm_period) };					
			throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
		}

        find_ground();      // find ground state
        
        /*  The following variable slot_size serves two functions.  It  */
        /*  is the size of a slot used in the multiplexing filter, and  */
        /*  it is the number of throws allocated in memory.  The number */
        /*  of throws needs to be larger than L sometimes since these   */
        /*  same structures are used to find starting and ending        */
        /*  sequences (containing as many as HT elements).              */

        slot_size = ((ht > lhigh) ? ht : lhigh);
        slot_size += rhythm_period - (slot_size % rhythm_period);

        for (i = 0; i < hands; i++)
            for (j = 0; j < rhythm_period; j++)
                if ((k = rhythm_repunit[i][j]) > max_occupancy)
                    max_occupancy = k;
        max_occupancy *= multiplex;
        if (max_occupancy == 1)       /* no multiplexing, turn off filter */
            mp_filter = 0;

        /*  Now allocate the memory space for the states, rhythms, and  */
        /*  throws in the pattern, plus other incidental variables.     */

        pattern_state = new int[lhigh+1][hands][ground_state_length];   // last index not ht because of find_start_end()

        pattern_holes = new int[lhigh][hands][ht];

        pattern_throw_to = new int[slot_size][hands][max_occupancy];// first index not l because of find_start_end()
        pattern_throw_value = new int[slot_size][hands][max_occupancy];
		
        pattern_rhythm = new int[slot_size+1][hands][ht];
        for (i = 0; i < (slot_size + 1); i++)
            for (j = 0; j < hands; j++)
                for (k = 0; k < ht; k++)
                    pattern_rhythm[i][j][k] =
                        multiplex * rhythm_repunit[j][(k + i) % rhythm_period];


        if (mp_filter != 0)         /* allocate space for filter variables */
            pattern_filter = new int[lhigh+1][hands][slot_size][3];

        pattern_throwcount = new int[lhigh][hands];

        if (jugglers > 1) {       /* passing communication delay variables */
            scratch1 = new int[hands];
            scratch2 = new int[hands];
        }
		
		// Include the regular expressions that define "true multiplexing"
		if (true_multiplex) {
			String include_RE = null;

			if (jugglers == 1) {
				if (mode == ASYNCH)
					include_RE = ".*\\[[^2]*\\].*";
				else if (mode == SYNCH)
					include_RE = ".*\\[([^2\\]]*2x)*[^2\\]]*\\].*";
			} else {
				if (mode == ASYNCH)
					include_RE = ".*\\[([^2\\]]*(2p|.p2|2p.))*[^2\\]]*\\].*";
				else if (mode == SYNCH)
					include_RE = ".*\\[([^2\\]]*(2p|.p2|2p.|2x|2xp|.xp2|2xp.))*[^2\\]]*\\].*";
			}
			
			if (include_RE != null)
				include.add(Pattern.compile(include_RE));
		}
		
		if (connected_patterns)
			connections = new boolean[jugglers];
		
		if ((jugglers > 1) && !juggler_permutations) {
			perm_scratch1 = new boolean[lhigh];
			perm_scratch2 = new boolean[lhigh];
		}
    }

	/*
	The following reformats the exclude/include terms into standard regular expressions.
	It exchanges "\x" for "x", where x is one of the RE metacharacters that conflicts
	with siteswap notation: []()|
	*/
	protected String make_standard_RE(String term) {
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

    public int runGenerator(GeneratorTarget t) {
        try {
            return runGenerator(t, -1, -1.0);			// no limits
        } catch (JuggleException je) {
        }
        return 0;		// can't happen
    }
    
    public int runGenerator(GeneratorTarget t, int max_num, double secs) throws JuggleExceptionUser {
        if ((groundflag == 1) && (ground_state_length > ht)) {
            return 0;
        }
        
        this.target = t;
        this.max_num = max_num;
        this.max_time = secs;
        this.max_time_millis = (long)(1000.0 * secs);
        this.start_time_millis = System.currentTimeMillis();
    
        this.loop_counter = 0;
    
		int num = 0;
		for (l = llow; l <= lhigh; l += rhythm_period)
			num += gen_patterns(0, 0, 0, 0);
    
        if (numflag != 0) {
            if (num == 1)
                target.setStatus(guistrings.getString("Generator_patterns_1"));
            else {
				String template = guistrings.getString("Generator_patterns_ne1");
				Object[] arguments = { new Integer(num) };
                target.setStatus(MessageFormat.format(template, arguments));
			}
        }
    
        return num;
    }

    // Now the protected methods
    
    /* The following routine initializes stuff for the built-in rhythms. */
    /* This involves allocating and initializing arrays.                 */
    
    protected void initialize() {
        switch (mode) {
            case ASYNCH:
                rhythm_repunit = new int[jugglers][1];
                holdthrow = new int[jugglers];
                person_number = new int[jugglers];
                hands = jugglers;
                rhythm_period = 1;
                pattern_printx = false;
                for (int i = 0; i < hands; i++) {
                    rhythm_repunit[i][0] = asynch_rhythm_repunit[0][0];
                    holdthrow[i] = 2;
                    person_number[i] = i+1;
                }
                break;
            case SYNCH:
                rhythm_repunit = new int[2*jugglers][2];
                holdthrow = new int[2*jugglers];
                person_number = new int[2*jugglers];
                hands = 2*jugglers;
                rhythm_period = 2;
                pattern_printx = true;
                for (int i = 0; i < hands; i++) {
                    for (int j = 0; j < rhythm_period; j++)
                        rhythm_repunit[i][j] = synch_rhythm_repunit[i%2][j];
                    holdthrow[i] = 2;
                    person_number[i] = (i/2) + 1;
                }
                break;
        }
    }


/* This routine reads a custom rhythm file and parses it to get the */
/* relevant information.  If there is an error it prints a message  */
/* and then exits.                                                  */
/*
	void custom_initialize(char *custom_file)   // name of file
	{
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

                        for (j = 0, k = 0; (ch = file_buffer[j]) && (ch != ';'); j++)
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
                                for (j = 0; j < rhythm_period; j++) {
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


    /*  valid_throw -- checks if a given throw is valid.  Check for        */
    /*                 excluded throws and a passing communication delay,  */
    /*                 as well a custom filter (if in CUSTOM mode).        */
    
    protected boolean valid_throw(int pos) {
        int i, j, k, balls_left, balls_thrown;
    
		// test pattern in progress against all exclusions
		for (i = 0; i < exclude.size(); i++) {
			Pattern regex = (Pattern)exclude.elementAt(i);
			/*System.out.println("test for string " + (new String(output, 0, outputpos)) + " = " +
							   regex.matcher(new String(output, 0, outputpos)).matches());*/
			if (regex.matcher(new String(output, 0, outputpos)).matches())
				return false;
		}
		
        for (i = 0; i < hands; i++) {
            if (pattern_rhythm[pos][i][0] != 0) {	// can we make a throw here?
                for (j = 0; (j < max_occupancy) && ((k = pattern_throw_value[pos][i][j]) != 0); j++) {
                    if (mp_clustered)					// need to check for clustered throws?
                        continue;
    
                    for (int l = 0; l < j; l++) {
                        if ((pattern_throw_value[pos][i][j] == pattern_throw_value[pos][i][l])
                            && (pattern_throw_to[pos][i][j] == pattern_throw_to[pos][i][l]))
                            return false;
                    }
                }
            }
        }
    
        /*  Now check if we are allowing for a sufficient  */
        /*  communication delay, if we are passing.        */
    
        if ((jugglers > 1) && (pos < delaytime)) {      /* need to check? */
            /*  First count the number of balls being thrown,       */
            /*  assuming no multiplexing.  Also check if leader is  */
            /*  forcing others to multiplex or make no throw.       */
            for (balls_thrown = 0, i = 0; i < hands; i++) {
                if (pattern_rhythm[pos][i][0] != 0) {
                    balls_thrown++;
                    if ((pattern_state[pos][i][0] != 1) && (person_number[i] != leader_person))
                        return false;
                }
            }
    
            balls_left = n;
            for (i = 0; (i < ht) && (balls_left != 0); i++) {
                for (j = 0; (j < hands) && (balls_left != 0); j++) {
                    if (pattern_rhythm[pos + 1][j][i] != 0) {
                        if (--balls_left < balls_thrown) {
                            scratch1[balls_left] = j;       /* dest hand # */
                            scratch2[balls_left] = i + 1;   /* dest value */
                        }
                    }
                }
            }
    
            if (balls_left != 0)
                return false;       /* this shouldn't happen, but die anyway */

            for (i = 0; i < hands; i++) {
                if ((pattern_state[pos][i][0] != 0) &&
                            (person_number[i] != leader_person)) {
                    for (j = 0, k = 1; (j < balls_thrown) && (k != 0); j++)
                        if ((scratch1[j] == pattern_throw_to[pos][i][0]) &&
                                    (scratch2[j] == pattern_throw_value[pos][i][0]))
                            scratch2[j] = k = 0;    // can't throw to spot again
                    if (k != 0)
                        return false;   /* wasn't throwing to empty position */
                }
            }
        }
    
        return true;
    }


    protected boolean valid_pattern() {
        int i, j, k, m, p;

		// test pattern in progress against all inclusions
		for (i = 0; i < include.size(); i++) {
			Pattern regex = (Pattern)include.elementAt(i);
			if (!regex.matcher(new String(output, 0, outputpos)).matches())
				return false;
		}
		
        if ((mode == ASYNCH) && lameflag && (max_occupancy == 1)) {
            for (i = 0; i < (l - 1); i++)       /* check for '11' sequence */
                for (j = 0; j < hands; j++)
                    if ((pattern_throw_value[i][j][0] == 1) &&
								(person_number[pattern_throw_to[i][j][0]] == person_number[j]) &&
                                (pattern_throw_value[i+1][j][0] == 1) &&
								(person_number[pattern_throw_to[i+1][j][0]] == person_number[j]))
                        return false;
        }
        
        // Added 12/4/2002
        // Is the pattern composite?  If so, ensure we only print one rotation of it
        if ((fullflag == 0) && (rotflag == 0)) {
            for (i = 1; i < l; i++) {
                if ((i % rhythm_period) == 0) {		// can we compare states?
                    if (compare_states(pattern_state[0], pattern_state[i]) == 0) {
                        if (compare_rotations(0, i) < 0)
                            return false;
                    }
                }
            }
        }
        
		// test whether passing pattern is connected
		if (connected_patterns) {
			for (i = 0; i < jugglers; i++)
				connections[i] = false;
			connections[0] = true;
			
			boolean changed = true;
			while (changed) {
				changed = false;

				for (i = 0; i < l; i++) {
					for (j = 0; j < hands; j++) {
						if (connections[person_number[j]-1])
							continue;
						for (k = 0; (k < max_occupancy) && (pattern_throw_value[i][j][k] > 0); k++) {
							m = person_number[pattern_throw_to[i][j][k]];
							
							if (connections[m - 1]) {
								connections[person_number[j]-1] = true;
								changed = true;
							}
						}
					}
				}
			}
			
			for (i = 0; i < jugglers; i++)
				if (!connections[i])
					return false;
		}
		
		// Test to see whether there is a better permutation of jugglers
		// This algorithm is not guaranteed to eliminate all permuted duplicates, but will do so in the
		// vast majority of cases.
		if ((jugglers > 1) && !juggler_permutations) {
			for (m = 1; m <= (jugglers-1); m++) {
				// compare juggler m against juggler (m+1)
				for (i = 0; i < l; i++)
					perm_scratch1[i] = perm_scratch2[i] = false;
				
				for (p = 0; p < l; p++) {
					int scorem = -1, scoremp1 = -1, maxm = 0, maxmp1 = 0;
				
					for (i = 0; i < l; i++) {
						if (!perm_scratch1[i]) {
							int scoretemp = 0;
							
							for (j = 0; j < hands; j++) {
								if (person_number[j] != m)
									continue;
								for (k = 0; (k < max_occupancy) && (pattern_throw_value[i][j][k] > 0); k++) {
									scoretemp += 4 * pattern_throw_value[i][j][k] * (2*max_occupancy) * (2*max_occupancy);
									if (pattern_throw_to[i][j][k] != j) {
										scoretemp += 2 * (2*max_occupancy);
										if (person_number[pattern_throw_to[i][j][k]] != m)
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
							
							for (j = 0; j < hands; j++) {
								if (person_number[j] != (m+1))
									continue;
								for (k = 0; (k < max_occupancy) && (pattern_throw_value[i][j][k] > 0); k++) {
									scoretemp += 4 * pattern_throw_value[i][j][k] * (2*max_occupancy) * (2*max_occupancy);
									if (pattern_throw_to[i][j][k] != j) {
										scoretemp += 2 * (2*max_occupancy);
										if (person_number[pattern_throw_to[i][j][k]] != (m+1))
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
					
					if (scoremp1 > scorem)
						return false;
					if (scoremp1 < scorem)
						break;		// go to the next pair of jugglers
						
					perm_scratch1[maxm] = perm_scratch2[maxmp1] = true;
				}
			}
		}
		
        return true;
    }


    // Added 12/9/2002
    // This method assumes the throws are comparable, i.e., that pos1 is congruent to pos2
    // mod rhythm_period
    protected int compare_rotations(int pos1, int pos2) {
        for (int i = 0; i < l; ) {
            int res = compare_loops((pos1+i)%l, (pos2+i)%l);
            if (res > 0)
                return 1;
            else if (res < 0)
                return -1;
    
            i++;
            for (; i < l; i++) {
                if (compare_states(pattern_state[pos1], pattern_state[(pos1+i)%l]) == 0)
                    break;
            }
        }
        return 0;
    }

    // Added 12/4/2002
    protected int compare_loops(int pos1, int pos2) {
        int[][] startstate = pattern_state[pos1];
        int result = 0;
        int i = 0;
    
        // Rule 1:  The longer loop is always greater
        // Rule 2:  For loops of equal length, use throw-by-throw comparison
        // Rule 3:  Loops are equal only if the respective throws are identical
    
        while (true) {
            i++;
    
            if (result == 0)
                result = compare_throws(pos1, pos2);
    
            if ((i % rhythm_period) == 0) {
                int cs1 = compare_states(pattern_state[pos1+1], startstate);
                int cs2 = compare_states(pattern_state[pos2+1], startstate);
    
                if (cs1 == 0) {
                    if (cs2 == 0)
                        return result;
                    return -1;
                }
                if (cs2 == 0)
                    return 1;
            }
    
            pos1++;
            pos2++;
        }
    }

    // Added 12/7/2002
    // This method assumes the throws are comparable, i.e., that pos1 is congruent
    // to pos2 mod rhythm_period
    protected int compare_throws(int pos1, int pos2) {
        int[][] value1 = pattern_throw_value[pos1];
        int[][] to1 = pattern_throw_to[pos1];
        int[][] value2 = pattern_throw_value[pos2];
        int[][] to2 = pattern_throw_to[pos2];
        int[][] rhythm = pattern_rhythm[pos1];	// same as pos2 since throws comparable
    
        // Rule 1:  Throws are equal only if identical
    
        for (int i = 0; i < hands; i++) {
            for (int j = 0; j < rhythm[i][0]; j++) {
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


    /*  compare_states -- equality (0), lesser (-1), or greater (1)     */
    protected int compare_states(int state1[][], int state2[][]) {
        int i, j, mo1 = 0, mo2 = 0;
    
        for (i = 0; i < hands; i++) {
            for (j = 0; j < ht; j++) {
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
    
        for (j = (ht - 1); j >= 0; j--) {
            for (i = (hands - 1); i >= 0; i--) {
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



    /* returns number as single character */
    protected char convert_number(int value) {
		return Character.toLowerCase(Character.forDigit(value, 36));
    }


    protected int print_throw(char[] out, int outpos, int[][] throw_value, int[][] throw_to, int[][] rhythm) {
        int i, j, k, m, q, lo_hand, hi_hand;
        boolean multiplex, parens;
        char ch;

        for (i = 0; i < rhythm.length; i++)
            if (rhythm[i][0] != 0)
                break;
        if (i == rhythm.length)		// no throw on this beat
            return outpos;
    
        if (jugglers > 1)
            out[outpos++] = '<';
    
        for (i = 1; i <= jugglers; i++) {
    
            // first find the hand numbers corresponding to person
            for (lo_hand = 0; person_number[lo_hand] != i; lo_hand++)
                ;
            for (hi_hand = lo_hand; (hi_hand < hands) &&
                (person_number[hi_hand] == i); hi_hand++)
                ;
    
            // check rhythm to see if person is throwing this time
            for (j = lo_hand, k = 0; j < hi_hand; j++)
                if (rhythm[j][0] != 0)
                    k++;
    
            if (k > 0) {        // person should throw
                if (k > 1) {     // more than one hand throwing?
                    out[outpos++] = '(';
                    parens = true;
                } else
                    parens = false;
    
                for (j = lo_hand; j < hi_hand; j++) {
                    if (rhythm[j][0] == 0)	// this hand supposed to throw?
                        continue;
    
                    if ((max_occupancy > 1) && (throw_value[j][1] > 0)) {
                        out[outpos++] = '[';        // multiplexing?
                        multiplex = true;
                    } else
                        multiplex = false;
    
                    // Now loop thru the throws coming out of this hand
    
                    for (k = 0; (k < max_occupancy) && (throw_value[j][k] > 0); k++) {
                        m = throw_value[j][k];
                        out[outpos++] = convert_number(m);    // print throw value
    
                        if (hands > 1) {    // potential ambiguity about destination?
                            m = person_number[throw_to[j][k]];
    
                            // print destination hand, if needed
                            if (pattern_printx) {
                                // find hand # of destination person
                                q = throw_to[j][k] - 1;
                                int dest_hand = 0;
                                while ((q >= 0) && (person_number[q] == m)) {
                                    q--;
                                    dest_hand++;
                                }
    
                                if (dest_hand != (j - lo_hand))
                                    out[outpos++] = 'x';
                            }
    
                            // print pass modifier and person number, if needed
                            if (m != i) {
                                out[outpos++] = 'p';
                                if (jugglers > 2)
                                    out[outpos++] = convert_number(m);
                            }
                            /*
                            // destination person has 1 hand, don't print
                            if ((ch != 'a') || ((q < (hands - 2)) &&
                                                (person_number[q + 2] == m)))
                            out[outpos++] = ch;             // print it
                            */
                        }
    
                        if (multiplex && (jugglers > 1) &&
                            (k != (max_occupancy - 1)) && (throw_value[j][k + 1] > 0))
                            out[outpos++] = '/';
                        // another multiplexed throw?
                    }
    
                    if (k == 0)
                        out[outpos++] = '0';
    
                    if (multiplex)
                        out[outpos++] = ']';
    
                    if ((j < (hi_hand - 1)) && parens)  // put comma between hands
                        out[outpos++] = ',';
                }
                if (parens)
                    out[outpos++] = ')';
            }
            if (i < jugglers)           // another person throwing next?
                out[outpos++] = '|';
        }
    
        if (jugglers > 1)
            out[outpos++] = '>';
			
		return outpos;
    }


    protected void print_pattern() {
        int i, j, excited = 0, skip;
        StringBuffer outputline = new StringBuffer(hands*(2*ground_state_length+l)*CHARS_PER_THROW + 10);
        StringBuffer outputline2 = new StringBuffer(hands*(2*ground_state_length+l)*CHARS_PER_THROW + 10);
    
        if (groundflag != 1) {
            if (sequenceflag) {
                if (mode == ASYNCH) {
                    for (i = n - starting_seq_length; i > 0; i--)
                        outputline.append(" ");
				}
                outputline.append(starting_seq, 0, starting_seq_length);
				outputline.append("  ");
            } else {
                excited = compare_states(ground_state, pattern_state[0]);
                
                if (excited != 0)
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
				// add proper number of trailing spaces too, so formatting is aligned
				// in RTL languages
                if (mode == ASYNCH) {
                    for (i = n - ending_seq_length; i > 0; i--)
                        outputline.append(" ");
				}
			} else {
				if (excited != 0)
					outputline.append(" *");
				else
					outputline.append("  ");
			}
        }
    
        target.writePattern(outputline.toString(), "siteswap", outputline2.toString().trim());
    }


    /*  The next function is part of the implementation of the   */
    /*  multiplexing filter.  It adds a throw to a filter slot,  */
    /*  returning 1 if there is a collision, 0 otherwise.        */
    
    protected int mp_addthrow(int dest_slot[], int slot_hand, int type, int value, int from) {
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
                if ((from == slot_hand) && (value == holdthrow[slot_hand]))
                    return 0;           /* throw is a hold, so ignore it */
    
                switch (dest_slot[TYPE]) {
                    case MP_EMPTY:
                        dest_slot[TYPE] = MP_THROW;
                        dest_slot[VALUE] = value;
                        dest_slot[FROM] = from;
                        return 0;
                    case MP_LOWER_BOUND:
                        if ((dest_slot[VALUE] <= value) || (dest_slot[VALUE] <=
                                                            holdthrow[slot_hand])) {
                            dest_slot[TYPE] = MP_THROW;
                            dest_slot[VALUE] = value;
                            dest_slot[FROM] = from;
                            return 0;
                        }
                        break;       /* this kills recursion */
                    case MP_THROW:
                        if ((dest_slot[FROM] == from) &&
                            (dest_slot[VALUE] == value))
                            return 0;        /* throws from same place (cluster) */
                        break;       /* kills recursion */
                }
                    break;
        }
    
        return 1;
    }


    /*  gen_loops -- Recursively generates loops, given a particular starting state. */
    
    protected int gen_loops(int pos, int throws_made, int min_throw,
                            int min_hand, int num) throws JuggleExceptionUser {
        /* int pos;              /* slot number in pattern that we're constructing */
        /* int throws_made;      /* number of throws made out of current slot      */
        /* int min_throw;        /* lowest we can throw this time                  */
        /* int min_hand;         /* lowest hand we can throw to this time          */
        /* unsigned int num;     /* number of valid patterns counted               */
        int i, j, k, m, o;
		int outputpos_temp = outputpos;
    
        if (max_time > 0.0) {
            if (loop_counter++ > loop_counter_max) {
                loop_counter = 0;
                if ((System.currentTimeMillis() - start_time_millis) > max_time_millis) {
					String template = guistrings.getString("Generator_timeout");
					Object[] arguments = { new Integer((int)max_time) };					
					throw new JuggleExceptionDone(MessageFormat.format(template, arguments));
				}
            }
        }
    
        if (pos == l) {
            if ((compare_states(pattern_state[0], pattern_state[l]) == 0) && valid_pattern()) {
                if (numflag != 2)
                    print_pattern();
                if (num++ == max_num) {
					String template = guistrings.getString("Generator_spacelimit");
					Object[] arguments = { new Integer(max_num) };					
                    throw new JuggleExceptionDone(MessageFormat.format(template, arguments));
				}
            }
			outputpos = outputpos_temp;
            return num;
        }
    
        if (throws_made == 0)
            for (i = 0; i < hands; i++) {
                pattern_throwcount[pos][i] = pattern_state[pos][i][0];
                for (j = 0; j < ht; j++) {
                    pattern_holes[pos][i][j] = pattern_rhythm[pos + 1][i][j];
                    if (j != (ht - 1))
                        pattern_holes[pos][i][j] -= pattern_state[pos][i][j + 1];
                }
                for (j = 0; j < max_occupancy; j++) {
                    pattern_throw_to[pos][i][j] = i;      /* clear throw matrix */
                    pattern_throw_value[pos][i][j] = 0;
                }
            }
    
        for (i = 0; (i < hands) && (pattern_throwcount[pos][i] == 0); i++)
            ;
    
        if (i == hands) {  /* done with current slot, move to next */
            outputpos = print_throw(output, outputpos, pattern_throw_value[pos],
							pattern_throw_to[pos], pattern_rhythm[pos]);
			
            if (!valid_throw(pos)) {              /* is the throw ok? */
				outputpos = outputpos_temp;
                return num;
			}
    
            /* first calculate the next state in ptrn, given last throw */
            for (j = 0; j < hands; j++)      /* shift state to the left */
                for (k = 0; k < ht; k++)
                    pattern_state[pos + 1][j][k] =
                        ( (k == (ht-1)) ? 0 : pattern_state[pos][j][k+1] );
    
            /* add on the last throw */
            for (j = 0; j < hands; j++)
                for (k = 0; (k < max_occupancy) && ((m = pattern_throw_value[pos][j][k]) != 0); k++)
                    pattern_state[pos + 1][pattern_throw_to[pos][j][k]][m - 1]++;
    
            /* Check if this is a valid state for a period-L pattern */
            /* This check added 01/19/98.                            */
            for (j = 0; j < hands; j++) {
                for (k = 0; k < ht; k++) {
                    o = k;
                    while (o < (ht-l)) {
                        if (pattern_state[pos + 1][j][o + l] > pattern_state[pos + 1][j][o]) {
							outputpos = outputpos_temp;
                            return num;     /* die (invalid state for this L) */
						}
						o += l;
					}
                }
            }
            /* end of new section */
    
            if (((pos + 1) % rhythm_period) == 0) {
                /* can we compare states? (rhythms must be same) */
                j = compare_states(pattern_state[0], pattern_state[pos + 1]);
                if ((fullflag != 0) && (pos != (l - 1)) && (j == 0))  /* intersection */ {
					outputpos = outputpos_temp;
                    return num;
				}
                if ((rotflag == 0) && (j == 1)) { /* prevents rotations from being printed */
					outputpos = outputpos_temp;
                    return num;
				}
            }
    
            if (fullflag == 2) {            /* list only simple loops? */
                for (j = 1; j <= pos; j++)
                    if (((pos + 1 - j) % rhythm_period) == 0) {
                        if (compare_states(pattern_state[j], pattern_state[pos + 1]) == 0) {
							outputpos = outputpos_temp;
                            return num;
						}
                    }
            }
    
            /*  Now do the multiplexing filter.  This ensures that,  */
            /*  other than holds, objects from only one source are   */
            /*  landing in any given hand (for example, a cluster of */
            /*  3's).  The implementation is a little complicated,   */
            /*  since I want to cut off the recursion as quickly as  */
            /*  possible to get speed on big searches.  This         */
            /*  precludes simply generating all patterns and then    */
            /*  throwing out the unwanted ones.                      */
    
            if (mp_filter != 0) {
                for (j = 0; j < hands; j++) {    /* shift filter frame to left */
                    for (k = 0; k < (slot_size - 1); k++) {
                        pattern_filter[pos + 1][j][k][TYPE] =
                        pattern_filter[pos][j][k + 1][TYPE];
                        pattern_filter[pos + 1][j][k][FROM] =
                            pattern_filter[pos][j][k + 1][FROM];
                        pattern_filter[pos + 1][j][k][VALUE] =
                            pattern_filter[pos][j][k + 1][VALUE];
                    }
                    pattern_filter[pos + 1][j][slot_size - 1][TYPE] = MP_EMPTY;
                    /* empty slots shift in */
    
                    if (mp_addthrow( pattern_filter[pos + 1][j][l - 1],
                                        j, pattern_filter[pos][j][0][TYPE],
                                        pattern_filter[pos][j][0][VALUE],
                                        pattern_filter[pos][j][0][FROM]) != 0) {
						outputpos = outputpos_temp;
                        return num;
					}
                }
    
                for (j = 0; j < hands; j++)           /* add on last throw */
                    for (k = 0; (k < max_occupancy) &&
                                ((m = pattern_throw_value[pos][j][k]) != 0); k++)
                        if (mp_addthrow(
                                        pattern_filter[pos + 1][pattern_throw_to[pos][j][k]][m - 1],
                                        pattern_throw_to[pos][j][k], MP_THROW, m, j) != 0) {
							outputpos = outputpos_temp;
                            return num;        /* problem, so end recursion */
						}
            }
    
            num = gen_loops(pos + 1, 0, 1, 0, num);       /* go to next slot */
        } else {
            m = --pattern_throwcount[pos][i];     /* record throw */
            k = min_hand;
    
            for (j = min_throw; j <= ht; j++) {
                for ( ; k < hands; k++) {
                    if (pattern_holes[pos][k][j - 1] != 0) {/*can we throw to position?*/
                        pattern_holes[pos][k][j - 1]--;
                        pattern_throw_to[pos][i][m] = k;
                        pattern_throw_value[pos][i][m] = j;
                        if (m != 0)
                            num = gen_loops(pos, throws_made + 1, j, k, num);
                        else
                            num = gen_loops(pos, throws_made + 1, 1, 0, num);
                        pattern_holes[pos][k][j - 1]++;
                    }
                }
                k = 0;
            }
            pattern_throwcount[pos][i]++;
        }
    
		outputpos = outputpos_temp;
        return num;
    }


    /*  The next routine finds valid starting and ending sequences for      */
    /*  excited state patterns.  Note that these sequences are not unique.  */
    //  Rewritten on 12/31/03
    protected void find_start_end() {
        int i, j, k, m, q;
    
        /* first find the starting sequence */
        int start_beats = 0;
findstarting1:
        do {
            for (j = 0; j < hands; j++) {
                for (k = 0; k < ht; k++) {
                    // use p_s[1] as scratch
                    if ((k + start_beats) < ground_state_length)
                        pattern_state[1][j][k] = ground_state[j][k+start_beats];
                    else
                        pattern_state[1][j][k] = 0;

                    if (pattern_state[1][j][k] > pattern_state[0][j][k]) {
                        start_beats += rhythm_period;
                        continue findstarting1;
                    }

                    pattern_state[1][j][k] = pattern_state[0][j][k] - pattern_state[1][j][k];
                }
            }

            break;
        } while (true);

        for (i = 0; i < start_beats; i++) {
            for (j = 0; j < hands; j++) {
                for (k = 0; k < max_occupancy; k++) {
                    pattern_throw_value[i][j][k] = 0;
                    pattern_throw_to[i][j][k] = j;
                }
                
                if ((i >= ground_state_length) || (ground_state[j][i] == 0))
                    continue;
findstarting2:
                for (k = 0; k < ht; k++) {
                    for (m = 0; m < hands; m++) {
                        if (pattern_state[1][m][k] > 0) {
                            pattern_state[1][m][k]--;

                            pattern_throw_value[i][j][0] = k + start_beats - i;
                            pattern_throw_to[i][j][0] = m;
                            break findstarting2;
                        }
                    }
                }
            }
        }
        
	// write starting sequence to buffer
        starting_seq = new char[hands * start_beats * CHARS_PER_THROW];
		starting_seq_length = 0;
		
        for (i = 0; i < start_beats; i++) {
            starting_seq_length = print_throw(starting_seq, starting_seq_length, pattern_throw_value[i],
                            pattern_throw_to[i], pattern_rhythm[i]);
        }
    
        /*  Now construct an ending sequence.  Unlike the starting  */
        /*  sequence above, this time work forward to ground state. */

        int end_beats = 0;
findending1:
        do {
            for (j = 0; j < hands; j++) {
                for (k = 0; k < ground_state_length; k++) {
                    // use pattern_state[1] as scratch
                    if ((k + end_beats) < ht)
                        pattern_state[1][j][k] = pattern_state[0][j][k+end_beats];
                    else
                        pattern_state[1][j][k] = 0;

                    if (pattern_state[1][j][k] > ground_state[j][k]) {
                        end_beats += rhythm_period;
                        continue findending1;
                    }

                    pattern_state[1][j][k] = ground_state[j][k] - pattern_state[1][j][k];
                }
            }

            break;
        } while (true);

        for (i = 0; i < end_beats; i++) {
            for (j = 0; j < hands; j++) {
                for (k = 0; k < max_occupancy; k++) {
                    pattern_throw_value[i][j][k] = 0;
                    pattern_throw_to[i][j][k] = j;
                }

				if (i >= ht)
					continue;
					
                for (q = 0; q < pattern_state[0][j][i]; q++) {
findending2:
                    for (k = 0; k < ground_state_length; k++) {
                        for (m = 0; m < hands; m++) {
                            if (pattern_state[1][m][k] > 0) {
                                pattern_state[1][m][k]--;
    
                                pattern_throw_value[i][j][q] = k + end_beats - i;
                                pattern_throw_to[i][j][q] = m;
                                break findending2;
                            }
                        }
                    }
                }
            }
        }

        ending_seq = new char[hands * end_beats * CHARS_PER_THROW];
		ending_seq_length = 0;
		
        for (j = 0; j < end_beats; j++) {
            ending_seq_length = print_throw(ending_seq, ending_seq_length, pattern_throw_value[j],
                            pattern_throw_to[j], pattern_rhythm[j]);
        }
    }


    /*  gen_patterns -- Recursively generates all possible starting        */
    /*                  states, calling gen_loops above to find the loops  */
    /*                  for each one.                                      */
    
    protected int gen_patterns(int balls_placed, int min_value, int min_to, int num) throws JuggleExceptionUser {
        int i, j, k, m, q;
    
        if ((balls_placed == n) || (groundflag == 1)) {
            if (groundflag == 1) {    /* find only ground state patterns? */
                for (i = 0; i < hands; i++)
                    for (j = 0; j < ht; j++)
                        pattern_state[0][i][j] = ground_state[i][j];
            } else if ((groundflag == 2) &&
                    (compare_states(pattern_state[0], ground_state) == 0))
                return num;         /* don't find ground state patterns */
    
            /*  At this point our state is completed.  Check to see      */
            /*  if it's valid.  (Position X must be at least as large    */
            /*  as position X+L, where L = pattern length.)  Also set    */
            /*  up the initial multiplexing filter frame, if we need it. */
    
            for (i = 0; i < hands; i++) {
                for (j = 0; j < ht; j++) {
    
                    k = pattern_state[0][i][j];
                    if ((mp_filter != 0) && (k == 0))
                        pattern_filter[0][i][j][TYPE] = MP_EMPTY;
                    else {
                        if (mp_filter != 0) {
                            pattern_filter[0][i][j][VALUE] = j + 1;
                            pattern_filter[0][i][j][FROM] = i;
                            pattern_filter[0][i][j][TYPE] = MP_LOWER_BOUND;
                        }
    
                        m = j;
                        while ((m += l) < ht) {
                            if ((q = pattern_state[0][i][m]) > k)
                                return num;     /* die (invalid state for this L) */
                            if ((mp_filter != 0) && (q != 0)) {
                                if ((q < k) && (j > holdthrow[i]))
                                    return num;  /* different throws into same hand */
                                pattern_filter[0][i][j][VALUE] = m + 1;  /* new bound */
                            }
                        }
                    }
                }
    
                if (mp_filter != 0)
                    for ( ; j < slot_size; j++)
                        pattern_filter[0][i][j][TYPE] = MP_EMPTY; /* clear rest of slot */
            }
    
            if ((numflag != 2) && sequenceflag)
                find_start_end();/* find starting and ending sequences for state */
    
                return gen_loops(0, 0, 1, 0, num);   /* find patterns thru state */
        }
    
        if (balls_placed == 0) {        /* startup, clear state */
            for (i = 0; i < hands; i++)
                for (j = 0; j < ht; j++)
                    pattern_state[0][i][j] = 0;
        }
        
        j = min_to;       /* ensures each state is generated only once */
        for (i = min_value; i < ht; i++) {
            for ( ; j < hands; j++) {
                if (pattern_state[0][j][i] < pattern_rhythm[0][j][i]) {
                    pattern_state[0][j][i]++;
                    num = gen_patterns(balls_placed + 1, i, j, num);   /* recursion */
                    pattern_state[0][j][i]--;
                }
            }
            j = 0;
        }
    
        return num;
    }
    
    
    
    
    /*  find_ground -- Find the ground state for our rhythm.  Just put  */
    /*                 the balls in the lowest possible slots, with no  */
    /*                 multiplexing.                                    */

    protected void find_ground() {
        int i, j, balls_left;

        balls_left = n;
        for (i = 0; balls_left != 0; i++) {
            for (j = 0; (j < hands) && (balls_left != 0); j++) {
                if (rhythm_repunit[j][i % rhythm_period] != 0) {
                    balls_left--;
                    if (balls_left == 0)
                        ground_state_length = i + 1;
                }
            }
        }

        if (ground_state_length < ht)
            ground_state_length = ht;

        ground_state = new int[hands][ground_state_length];
        
        for (i = 0; i < hands; i++)       /* clear ground state array */
            for (j = 0; j < ground_state_length; j++)
                ground_state[i][j] = 0;

        balls_left = n;
        for (i = 0; balls_left != 0; i++) {
            for (j = 0; (j < hands) && (balls_left != 0); j++) {
                if (rhythm_repunit[j][i % rhythm_period] != 0) {      /* available slots */
                    ground_state[j][i] = 1;
                    balls_left--;
                }
            }
        }
    }
    
    
    public static void main(String[] args) {
        if (args.length < 3) {
            String template = guistrings.getString("Version");
            Object[] arg1 = { Constants.version };
            String output = "Juggling Lab " + MessageFormat.format(template, arg1).toLowerCase()+"\n";

            template = guistrings.getString("Copyright_message");
            Object[] arg2 = { Constants.year };
            output += MessageFormat.format(template, arg2)+"\n\n";

            output += guistrings.getString("GPL_message")+"\n\n";
            output += guistrings.getString("Generator_intro");
            
            System.out.println(output);
        } else {
            siteswapGenerator ssg = new siteswapGenerator();
    
            try {
                ssg.initGenerator(args);
                ssg.runGenerator(new GeneratorTarget(System.out));// no pattern number limit
            } catch (Exception e) {
                System.out.println(errorstrings.getString("Error")+": "+e.getMessage());
                // jugglinglab.util.ErrorDialog.handleException(e);
            }
        }
    }

}
