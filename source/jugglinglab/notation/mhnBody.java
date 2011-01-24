// mhnBody.java
//
// Copyright 2003 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

import jugglinglab.jml.*;
import jugglinglab.util.*;


public class mhnBody {
    // static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        // guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

    protected int jugglers = 0;
    protected int[] size = null;
    protected int[][] coords = null;
    protected double[][][][] bodypath = null;

    public mhnBody(String bodies) throws JuggleExceptionUser, JuggleExceptionInternal {
        // delete the '<' and '>' characters first
        int pos;
        while ((pos = bodies.indexOf('<')) >= 0) {
            bodies = bodies.substring(0,pos) + bodies.substring(pos+1,bodies.length());
        }
        while ((pos = bodies.indexOf('>')) >= 0) {
            bodies = bodies.substring(0,pos) + bodies.substring(pos+1,bodies.length());
        }
        while ((pos = bodies.indexOf('{')) >= 0) {
            bodies = bodies.substring(0,pos) + bodies.substring(pos+1,bodies.length());
        }
        while ((pos = bodies.indexOf('}')) >= 0) {
            bodies = bodies.substring(0,pos) + bodies.substring(pos+1,bodies.length());
        }

        StringTokenizer st1 = new StringTokenizer(bodies, "|!", false);
        jugglers = st1.countTokens();

        size = new int[jugglers];
        coords = new int[jugglers][];
        bodypath = new double[jugglers][][][];

        for (int j = 0; j < jugglers; j++) {
            String str = st1.nextToken();
            // System.out.println("str["+j+"] = "+str);

            for (int k = 0; k < 3; k++) {
                pos = 0;
                int numcoords = 0;

                for (int l = 0; l < str.length(); ) {
                    char ch = str.charAt(l);

                    if (ch == ' ') {
                        l++;
                        continue;
                    }
                    if (ch == '.') {
                        if (numcoords == 0) {
                            if (k == 1) {
                                coords[j][pos] = 1;
                                bodypath[j][pos] = new double[1][];
                                bodypath[j][pos][0] = null;
                            }
                        } else if (k == 1) {
                            coords[j][pos] = numcoords;
                            bodypath[j][pos] = new double[numcoords][];
                        }
                        pos++;
                        numcoords = 0;
                        l++;
                        continue;
                    }
                    if (ch == '-') {
                        if (k == 2)
                            bodypath[j][pos][numcoords] = null;
                        numcoords++;
                        l++;
                        continue;
                    }
                    if (ch == '(') {
                        int endindex = str.indexOf(')', l+1);
                        if (endindex < 0)
                            throw new JuggleExceptionUser(errorstrings.getString("Error_body_noparen"));
                        if (k == 2) {
                            bodypath[j][pos][numcoords] = new double[4];
                            bodypath[j][pos][numcoords][3] = 100.0;		// default z

                            String str2 = str.substring(l+1, endindex);

                            try {
                                StringTokenizer st4 = new StringTokenizer(str2, ",", false);
                                bodypath[j][pos][numcoords][0] =
                                    Double.valueOf(st4.nextToken()).doubleValue();
                                if (st4.hasMoreTokens())
                                    bodypath[j][pos][numcoords][1] =
                                        Double.valueOf(st4.nextToken()).doubleValue();
                                if (st4.hasMoreTokens())
                                    bodypath[j][pos][numcoords][2] =
                                        Double.valueOf(st4.nextToken()).doubleValue();
                                if (st4.hasMoreTokens())
                                    bodypath[j][pos][numcoords][3] =
                                        Double.valueOf(st4.nextToken()).doubleValue();
                            } catch (NumberFormatException e) {
                                throw new JuggleExceptionUser(errorstrings.getString("Error_body_coordinate"));
                            } catch (NoSuchElementException e) {
                                throw new JuggleExceptionInternal("No such element exception in \"body\"");
                            }
                        }
                        numcoords++;
                        l = endindex + 1;
                        continue;
                    }

					String template = errorstrings.getString("Error_body_character");
					Object[] arguments = { Character.toString(ch) };					
					throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
                }

                if (k == 0) {
                    size[j] = pos;
                    coords[j] = new int[pos];
                    bodypath[j] = new double[pos][][];
                }

                if (numcoords != 0)
                    throw new JuggleExceptionUser(errorstrings.getString("Error_body_badending"));
            }
        }

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
        if ((pos >= getPeriod(juggler)) || (index >= getNumberOfPositions(juggler, pos)))
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
