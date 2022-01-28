// MHNHands.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.notation;

import java.util.*;
import java.text.MessageFormat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jugglinglab.util.*;


public class MHNHands {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected int jugglers;
    protected int[] size;
    protected int[][] coords;
    protected int[][] catches;
    protected double[][][][] handpath;


    public MHNHands(String str) throws JuggleExceptionUser, JuggleExceptionInternal {
        str = JLFunc.expandRepeats(str);

        // delete the '<' and '>' characters first
        String pat = "[" + Pattern.quote("<>{}") + "]";
        str = str.replaceAll(pat, "");

        // take four passes through the string:
        // pass 0: count the number of jugglers
        //      1: count the number of beats per juggler (i.e. the period)
        //      2: count the number of coordinates in each beat
        //      3: record coordinates in the allocated arrays
        for (int pass = 0; pass < 4; pass++) {
            int juggler = 0;      // counters during parsing
            int beat = 0;
            int coordnum = 0;
            boolean gotthrow = false, gotcatch = false;

            for (int pos = 0; pos < str.length(); ) {
                char ch = str.charAt(pos);

                if (ch == ' ') {
                    pos++;
                    continue;
                }
                if (ch == '.') {
                    if (pass == 2) {
                        coords[juggler][beat] = coordnum;
                        if (coords[juggler][beat] < 2)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_toofewcoords"));

                        handpath[juggler][beat] = new double[coordnum][];
                    } else if (pass == 3) {
                        if (!gotcatch)
                            catches[juggler][beat] = coords[juggler][beat] - 1;
                        if (handpath[juggler][beat][0] == null)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_nothrow"));
                        if (handpath[juggler][beat][catches[juggler][beat]] == null)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_nocatch"));
                    }
                    gotthrow = gotcatch = false;
                    beat++;
                    coordnum = 0;
                    pos++;
                    continue;
                }
                if (ch == '-') {
                    if (pass == 3)
                        handpath[juggler][beat][coordnum] = null;
                    coordnum++;
                    pos++;
                    continue;
                }
                if (ch == 'T' || ch == 't') {
                    if (coordnum != 0)
                        throw new JuggleExceptionUser(errorstrings.getString("Error_hands_Tnotstart"));
                    if (gotthrow)
                        throw new JuggleExceptionUser(errorstrings.getString("Error_hands_toomanycoords"));
                    gotthrow = true;
                    pos++;
                    continue;
                }
                if (ch == 'C' || ch == 'c') {
                    if (coordnum == 0)
                        throw new JuggleExceptionUser(errorstrings.getString("Error_hands_Catstart"));
                    if (gotcatch)
                        throw new JuggleExceptionUser(errorstrings.getString("Error_hands_toomanycatches"));
                    if (pass == 2)
                        catches[juggler][beat] = coordnum;
                    gotcatch = true;
                    pos++;
                    continue;
                }
                if (ch == '(') {
                    int closeindex = str.indexOf(')', pos + 1);
                    if (closeindex < 0)
                        throw new JuggleExceptionUser(errorstrings.getString("Error_hands_noparen"));
                    if (pass == 3) {
                        handpath[juggler][beat][coordnum] = new double[3];

                        String str2 = str.substring(pos + 1, closeindex);

                        try {
                            StringTokenizer st4 = new StringTokenizer(str2, ",", false);
                            handpath[juggler][beat][coordnum][0] =
                                    JLFunc.parseDouble(st4.nextToken());
                            if (st4.hasMoreTokens())
                                handpath[juggler][beat][coordnum][2] =
                                    JLFunc.parseDouble(st4.nextToken());
                            if (st4.hasMoreTokens())
                                handpath[juggler][beat][coordnum][1] =
                                    JLFunc.parseDouble(st4.nextToken());
                        } catch (NumberFormatException e) {
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_coordinate"));
                        } catch (NoSuchElementException e) {
                            throw new JuggleExceptionInternal("No such element exception in \"hands\"");
                        }
                    }
                    coordnum++;
                    pos = closeindex + 1;
                    continue;
                }
                if (ch == '|' || ch == '!') {
                    if (coordnum != 0)
                        throw new JuggleExceptionUser(errorstrings.getString("Error_hands_badending"));
                    if (pass == 1) {
                        this.size[juggler] = beat;
                        this.catches[juggler] = new int[beat];
                        this.coords[juggler] = new int[beat];
                        this.handpath[juggler] = new double[beat][][];
                    }
                    beat = 0;
                    juggler++;
                    pos++;
                    continue;
                }

                String template = errorstrings.getString("Error_hands_character");
                Object[] arguments = { Character.toString(ch) };
                throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }

            if (coordnum != 0)
                throw new JuggleExceptionUser(errorstrings.getString("Error_hands_badending"));

            if (pass == 0) {
                this.jugglers = juggler + 1;
                this.size = new int[jugglers];
                this.coords = new int[jugglers][];
                this.catches = new int[jugglers][];
                this.handpath = new double[jugglers][][][];
            } else if (pass == 1) {
                this.size[juggler] = beat;
                this.catches[juggler] = new int[beat];
                this.coords[juggler] = new int[beat];
                this.handpath[juggler] = new double[beat][][];
            }
        }

    }

    public int getPeriod(int juggler) {
        int j = (juggler - 1) % jugglers;
        return size[j];
    }

    public int getNumberOfCoordinates(int juggler, int pos) {
        int j = (juggler - 1) % jugglers;
        return coords[j][pos];
    }

    // throw index is always 0:
    public int getCatchIndex(int juggler, int pos) {
        int j = (juggler - 1) % jugglers;
        return catches[j][pos];
    }

    // both pos and index are indexed from 0:
    public Coordinate getCoordinate(int juggler, int pos, int index) {
        if ((pos >= getPeriod(juggler)) || (index >= getNumberOfCoordinates(juggler, pos)))
            return null;
        int j = (juggler - 1) % jugglers;
        if (handpath[j][pos][index] == null)
            return null;
        return new Coordinate(handpath[j][pos][index][0], handpath[j][pos][index][1],
                              handpath[j][pos][index][2]);
    }
}
