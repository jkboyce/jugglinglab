//
// MHNBody.java
//
// This class parses the "body" parameter in MHN notation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation;

import java.text.MessageFormat;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import jugglinglab.jml.JMLPosition;
import jugglinglab.util.*;

public class MHNBody {
  static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
  static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

  protected int jugglers = 0;
  protected int[] size;
  protected int[][] coords;
  protected double[][][][] bodypath;

  public MHNBody(String str) throws JuggleExceptionUser, JuggleExceptionInternal {
    str = JLFunc.expandRepeats(str);

    // delete the '<' and '>' characters
    String pat = "[" + Pattern.quote("<>{}") + "]";
    str = str.replaceAll(pat, "");

    // take four passes through the string:
    // pass 0: count the number of jugglers
    //      1: count the number of beats per juggler (i.e. the period)
    //      2: count the number of coordinates in each beat
    //      3: record coordinates in the allocated arrays
    for (int pass = 0; pass < 4; pass++) {
      int juggler = 0; // counters during parsing
      int beat = 0;
      int coordnum = 0;

      for (int pos = 0; pos < str.length(); ) {
        char ch = str.charAt(pos);

        if (ch == ' ') {
          pos++;
          continue;
        }
        if (ch == '.') {
          if (pass == 2) {
            if (coordnum == 0) {
              coords[juggler][beat] = 1;
              bodypath[juggler][beat] = new double[1][];
              bodypath[juggler][beat][0] = null;
            } else {
              coords[juggler][beat] = coordnum;
              bodypath[juggler][beat] = new double[coordnum][];
            }
          }
          beat++;
          coordnum = 0;
          pos++;
          continue;
        }
        if (ch == '-') {
          if (pass == 3) {
            bodypath[juggler][beat][coordnum] = null;
          }
          coordnum++;
          pos++;
          continue;
        }
        if (ch == '(') {
          int closeindex = str.indexOf(')', pos + 1);
          if (closeindex < 0) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_body_noparen"));
          }
          if (pass == 3) {
            bodypath[juggler][beat][coordnum] = new double[4];
            bodypath[juggler][beat][coordnum][3] = 100.0; // default z

            String str2 = str.substring(pos + 1, closeindex);

            try {
              StringTokenizer st4 = new StringTokenizer(str2, ",", false);
              bodypath[juggler][beat][coordnum][0] = JLFunc.parseDouble(st4.nextToken());
              if (st4.hasMoreTokens()) {
                bodypath[juggler][beat][coordnum][1] = JLFunc.parseDouble(st4.nextToken());
              }
              if (st4.hasMoreTokens()) {
                bodypath[juggler][beat][coordnum][2] = JLFunc.parseDouble(st4.nextToken());
              }
              if (st4.hasMoreTokens()) {
                bodypath[juggler][beat][coordnum][3] = JLFunc.parseDouble(st4.nextToken());
              }
            } catch (NumberFormatException e) {
              throw new JuggleExceptionUser(errorstrings.getString("Error_body_coordinate"));
            } catch (NoSuchElementException e) {
              throw new JuggleExceptionInternal("No such element exception in MHNBody");
            }
          }
          coordnum++;
          pos = closeindex + 1;
          continue;
        }
        if (ch == '|' || ch == '!') {
          if (coordnum != 0) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_body_badending"));
          }
          if (pass == 1) {
            this.size[juggler] = beat;
            this.coords[juggler] = new int[beat];
            this.bodypath[juggler] = new double[beat][][];
          }
          beat = 0;
          juggler++;
          pos++;
          continue;
        }

        String template = errorstrings.getString("Error_body_character");
        Object[] arguments = {Character.toString(ch)};
        throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
      }

      if (coordnum != 0) {
        throw new JuggleExceptionUser(errorstrings.getString("Error_body_badending"));
      }

      if (pass == 0) {
        this.jugglers = juggler + 1;
        this.size = new int[jugglers];
        this.coords = new int[jugglers][];
        this.bodypath = new double[jugglers][][][];
      } else if (pass == 1) {
        this.size[juggler] = beat;
        this.coords[juggler] = new int[beat];
        this.bodypath[juggler] = new double[beat][][];
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

  // Position and index start from 0

  public JMLPosition getPosition(int juggler, int pos, int index) {
    if (pos >= getPeriod(juggler) || index >= getNumberOfPositions(juggler, pos)) {
      return null;
    }
    int j = (juggler - 1) % jugglers;
    if (bodypath[j][pos][index] == null) {
      return null;
    }
    JMLPosition result = new JMLPosition();
    result.setJuggler(juggler);
    result.setCoordinate(new Coordinate(
        bodypath[j][pos][index][1], bodypath[j][pos][index][2], bodypath[j][pos][index][3]));
    result.setAngle(bodypath[j][pos][index][0]);
    return result;
  }
}
