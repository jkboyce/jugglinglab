// MHNBody.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.notation;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jugglinglab.jml.JMLPosition;
import jugglinglab.util.*;


// This class parses the "body" parameter in MHN notation.

public class MHNBody {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected int jugglers = 0;
    protected int[] size;
    protected int[][] coords;
    protected double[][][][] bodypath;

    public MHNBody(String bodies) throws JuggleExceptionUser, JuggleExceptionInternal {
        // delete the '<' and '>' characters
        String pat = "[" + Pattern.quote("<>{}") + "]";
        bodies = bodies.replaceAll(pat, "");

        StringTokenizer st1 = new StringTokenizer(bodies, "|!", false);
        jugglers = st1.countTokens();

        size = new int[jugglers];
        coords = new int[jugglers][];
        bodypath = new double[jugglers][][][];

        for (int j = 0; j < jugglers; j++) {
            String str = st1.nextToken();
            // System.out.println("str["+j+"] = "+str);

            // take three passes through the string for this juggler:
            // pass 0: count the number of jugglers
            //      1: count the number of beats per juggler (i.e. the period)
            //      2: count the number of coordinates in each beat
            //      3: fill in the allocated arrays

            for (int pass = 0; pass < 3; pass++) {
                int beat = processOneJuggler(str, j, pass, 0);

                if (pass == 0) {
                    size[j] = beat;
                    coords[j] = new int[beat];
                    bodypath[j] = new double[beat][][];
                }
            }
        }
    }

    protected int processOneJuggler(String str, int j, int pass, int beat)
                throws JuggleExceptionUser, JuggleExceptionInternal {
        int numcoords = 0;

        for (int pos = 0; pos < str.length(); ) {
            char ch = str.charAt(pos);

            if (ch == ' ') {
                pos++;
                continue;
            }
            if (ch == '.') {
                if (numcoords == 0) {
                    if (pass == 1) {
                        coords[j][beat] = 1;
                        bodypath[j][beat] = new double[1][];
                        bodypath[j][beat][0] = null;
                    }
                } else if (pass == 1) {
                    coords[j][beat] = numcoords;
                    bodypath[j][beat] = new double[numcoords][];
                }
                beat++;
                numcoords = 0;
                pos++;
                continue;
            }
            if (ch == '-') {
                if (pass == 2)
                    bodypath[j][beat][numcoords] = null;
                numcoords++;
                pos++;
                continue;
            }
            if (ch == '(') {
                // A '(' indicates either the start of a coordinate like
                // (0,100,-50), or the start of a repeat section like:
                // ((50,30).(100)..)^3
                //
                // `ch` is the start of a repeat section iff one of these is true:
                // 1. the next '(' occurs before the next ')'
                // 2. condition 1 is false and the first non-whitespace character
                //    after the next ')' is '^', e.g., (.)^10

                int openindex = str.indexOf('(', pos + 1);
                int closeindex = str.indexOf(')', pos + 1);
                if (closeindex < 0)
                    throw new JuggleExceptionUser(errorstrings.getString("Error_body_noparen"));

                boolean is_repeat = (openindex >= 0 && openindex < closeindex);

                if (!is_repeat) {
                    // check second condition
                    String str2 = str.substring(closeindex + 1, str.length());
                    is_repeat = Pattern.matches("^\\s*\\^.*", str2);
                }

                if (is_repeat) {
                    // find some key info about the repeat section:
                    int[] result = parseRepeat(str, pos);
                    int repeat_end = result[0];
                    int repeats = result[1];
                    int resume_start = result[2];

                    // snip out the string to be repeated:
                    String str2 = str.substring(pos + 1, repeat_end);

                    for (int i = 0; i < repeats; i++)
                        beat = processOneJuggler(str2, j, pass, beat);

                    pos = resume_start;
                    continue;
                } else {
                    // regular coordinate, not a repeat
                    if (pass == 2) {
                        bodypath[j][beat][numcoords] = new double[4];
                        bodypath[j][beat][numcoords][3] = 100.0;     // default z

                        String str2 = str.substring(pos + 1, closeindex);

                        try {
                            StringTokenizer st4 = new StringTokenizer(str2, ",", false);
                            bodypath[j][beat][numcoords][0] =
                                Double.valueOf(st4.nextToken()).doubleValue();
                            if (st4.hasMoreTokens())
                                bodypath[j][beat][numcoords][1] =
                                    Double.valueOf(st4.nextToken()).doubleValue();
                            if (st4.hasMoreTokens())
                                bodypath[j][beat][numcoords][2] =
                                    Double.valueOf(st4.nextToken()).doubleValue();
                            if (st4.hasMoreTokens())
                                bodypath[j][beat][numcoords][3] =
                                    Double.valueOf(st4.nextToken()).doubleValue();
                        } catch (NumberFormatException e) {
                            throw new JuggleExceptionUser(errorstrings.getString("Error_body_coordinate"));
                        } catch (NoSuchElementException e) {
                            throw new JuggleExceptionInternal("No such element exception in MHNBody");
                        }
                    }
                    numcoords++;
                    pos = closeindex + 1;
                    continue;
                }
            }

            String template = errorstrings.getString("Error_body_character");
            Object[] arguments = { Character.toString(ch) };
            throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
        }

        if (numcoords != 0)
            throw new JuggleExceptionUser(errorstrings.getString("Error_body_badending"));

        return beat;
    }

    protected int[] parseRepeat(String str, int fromPos) throws JuggleExceptionUser {
        /*
        Scan forward in the string to find:
        (1) the end of the repeat (buffer position of ')' where depth returns to 0)
        (2) the number of repeats
            - if the next non-whitespace char after (a) is not '^' -> error
            - if the next non-whitespace char after '^' is not a number -> error
            - parse the numbers after '^' up through the first non-number (or end
              of buffer) into an int = `repeats`
        (3) the buffer position of the first non-numeric character after the
            repeat number (i.e. where to resume) = `resume_start`
            (=str.length() if hit end of buffer)

        We always call this function with `fromPos` sitting on the '(' that starts
        the repeat section.
        */
        int depth = 0;

        for (int pos = fromPos; pos < str.length(); pos++) {
            char ch = str.charAt(pos);

            if (ch == '(')
                depth++;
            else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    Pattern pat = Pattern.compile("^\\s*\\^\\s*(\\d+).*");
                    Matcher m = pat.matcher(str.substring(pos + 1, str.length()));

                    if (!m.matches())
                        throw new JuggleExceptionUser("MHNBody: Repeat section syntax error");

                    int repeat_end = pos;
                    int repeats = Integer.parseInt(m.group(1));
                    int resume_start = m.end(1) + pos + 1;

                    int[] result = new int[3];
                    result[0] = repeat_end;
                    result[1] = repeats;
                    result[2] = resume_start;
                    return result;
                }
            }
        }

        throw new JuggleExceptionUser("MHNBody: Unmatched parentheses in repeat section");
    }

    public int getNumberOfJugglers() {
        return jugglers;
    }

    public int getPeriod(int juggler) {
        int j = (juggler - 1) % jugglers;
        return size[j];
    }

    public int getNumberOfPositions(int juggler, int pos) {
        int j = (juggler - 1) % jugglers;
        return coords[j][pos];
    }

    // pos and index start from 0:
    public JMLPosition getPosition(int juggler, int pos, int index) {
        if (pos >= getPeriod(juggler) || index >= getNumberOfPositions(juggler, pos))
            return null;
        int j = (juggler - 1) % jugglers;
        if (bodypath[j][pos][index] == null)
            return null;
        JMLPosition result = new JMLPosition();
        result.setJuggler(juggler);
        result.setCoordinate(new Coordinate(bodypath[j][pos][index][1], bodypath[j][pos][index][2],
                                            bodypath[j][pos][index][3]));
        result.setAngle(bodypath[j][pos][index][0]);
        return result;
    }
}
