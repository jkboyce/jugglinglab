// mhnHands.java
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

package jugglinglab.notation;

import java.util.*;
import java.text.MessageFormat;

import jugglinglab.util.*;


public class mhnHands {
    // static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        // guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    protected int jugglers = 0;
    protected int[] size = null;
    protected int[][] coords = null;
    protected int[][] catches = null;
    protected double[][][][] handpath = null;
    

    public mhnHands(String hands) throws JuggleExceptionUser, JuggleExceptionInternal {
        // delete the '<' and '>' characters first
        int pos;
        while ((pos = hands.indexOf('<')) >= 0) {
            hands = hands.substring(0,pos) + hands.substring(pos+1,hands.length());
        }
        while ((pos = hands.indexOf('>')) >= 0) {
            hands = hands.substring(0,pos) + hands.substring(pos+1,hands.length());
        }
        while ((pos = hands.indexOf('{')) >= 0) {
            hands = hands.substring(0,pos) + hands.substring(pos+1,hands.length());
        }
        while ((pos = hands.indexOf('}')) >= 0) {
            hands = hands.substring(0,pos) + hands.substring(pos+1,hands.length());
        }

        StringTokenizer st1 = new StringTokenizer(hands, "|!", false);
        jugglers = st1.countTokens();

        size = new int[jugglers];
        coords = new int[jugglers][];
        catches = new int[jugglers][];
        handpath = new double[jugglers][][][];

        for (int j = 0; j < jugglers; j++) {
            String str = st1.nextToken();
            // System.out.println("str["+j+"] = "+str);

            for (int k = 0; k < 3; k++) {
                pos = 0;
                int numcoords = 0;
                boolean gotthrow = false, gotcatch = false;

                for (int l = 0; l < str.length(); ) {
                    char ch = str.charAt(l);

                    if (ch == ' ') {
                        l++;
                        continue;
                    }
                    if (ch == '.') {
                        if (k == 1) {
                            coords[j][pos] = numcoords;
                            if (coords[j][pos] < 2)
                                throw new JuggleExceptionUser(errorstrings.getString("Error_hands_toofewcoords"));

                            handpath[j][pos] = new double[numcoords][];
                        } else if (k == 2) {
                            if (!gotcatch)
                                catches[j][pos] = coords[j][pos] - 1;
                            if (handpath[j][pos][0] == null)
                                throw new JuggleExceptionUser(errorstrings.getString("Error_hands_nothrow"));
                            if (handpath[j][pos][catches[j][pos]] == null)
                                throw new JuggleExceptionUser(errorstrings.getString("Error_hands_nocatch"));
                        }
                        gotthrow = gotcatch = false;
                        pos++;
                        numcoords = 0;
                        l++;
                        continue;
                    }
                    if (ch == '-') {
                        if (k == 2)
                            handpath[j][pos][numcoords] = null;
                        numcoords++;
                        l++;
                        continue;
                    }
                    if ((ch == 'T') || (ch == 't')) {
                        if (numcoords != 0)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_Tnotstart"));
                        if (gotthrow)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_toomanycoords"));
                        gotthrow = true;
                        l++;
                        continue;
                    }
                    if ((ch == 'C') || (ch == 'c')) {
                        if (numcoords == 0)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_Catstart"));
                        if (gotcatch)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_toomanycatches"));
                        if (k == 1)
                            catches[j][pos] = numcoords;
                        gotcatch = true;
                        l++;
                        continue;
                    }
                    if (ch == '(') {
                        int endindex = str.indexOf(')', l+1);
                        if (endindex < 0)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_hands_noparen"));
                        if (k == 2) {
                            handpath[j][pos][numcoords] = new double[3];
                            String str2 = str.substring(l+1, endindex);

                            try {
                                StringTokenizer st4 = new StringTokenizer(str2, ",", false);
                                handpath[j][pos][numcoords][0] =
                                    Double.valueOf(st4.nextToken()).doubleValue();
                                if (st4.hasMoreTokens())
                                    handpath[j][pos][numcoords][2] =
                                        Double.valueOf(st4.nextToken()).doubleValue();
                                if (st4.hasMoreTokens())
                                    handpath[j][pos][numcoords][1] =
                                        Double.valueOf(st4.nextToken()).doubleValue();
                            } catch (NumberFormatException e) {
                                throw new JuggleExceptionUser(errorstrings.getString("Error_hands_coordinate"));
                            } catch (NoSuchElementException e) {
                                throw new JuggleExceptionInternal("No such element exception in \"hands\"");
                            }
                        }
                        numcoords++;
                        l = endindex + 1;
                        continue;
                    }

					String template = errorstrings.getString("Error_hands_character");
					Object[] arguments = { Character.toString(ch) };					
					throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }

                if (k == 0) {
                    size[j] = pos;
                    catches[j] = new int[pos];
                    coords[j] = new int[pos];
                    handpath[j] = new double[pos][][];
                }

                if (numcoords != 0)
                    throw new JuggleExceptionUser(errorstrings.getString("Error_hands_badending"));
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
