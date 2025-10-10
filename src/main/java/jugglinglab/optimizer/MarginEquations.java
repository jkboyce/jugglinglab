//
// MarginEquations.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.optimizer;

import java.util.*;
import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;

public class MarginEquations {
  static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
  static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;
  protected static final double EPSILON = 0.000001;

  public int varsNum; // number of variables in margin equations
  public JMLEvent[] varsEvents; // corresponding JMLEvents, one per variable
  public double[] varsValues; // current values of variables
  public double[] varsMin; // minimum values of variables
  public double[] varsMax; // maximum values of variables
  public int marginsNum; // number of distinct margin equations
  public LinearEquation[] marginsEqs; // array of linear equations

  public MarginEquations() {
    varsNum = 0;
    varsEvents = null;
    varsValues = null;
    varsMin = null;
    varsMax = null;
    marginsNum = 0;
    marginsEqs = null;
  }

  public MarginEquations(JMLPattern pat) throws JuggleExceptionInternal, JuggleExceptionUser {
    this();
    findeqs(pat);
  }

  public double getNumberOfEquations() {
    return marginsNum;
  }

  // returns current value of a given margin equation

  public double getMargin(int eqn) {
    double m = 0;
    for (int i = 0; i < varsNum; i++) m += marginsEqs[eqn].coef(i) * varsValues[i];

    return Math.abs(m) + marginsEqs[eqn].constant();
  }

  // returns minimum value of all margins together

  public double getMargin() {
    if (marginsNum == 0) return -100;

    double minmargin = getMargin(0);
    for (int i = 1; i < marginsNum; i++) {
      double m = getMargin(i);
      if (m < minmargin) minmargin = m;
    }
    return minmargin;
  }

  protected void findeqs(JMLPattern pat) throws JuggleExceptionInternal, JuggleExceptionUser {
    if (Constants.DEBUG_OPTIMIZE) System.out.println("finding margin equations");

    if (pat.getNumberOfJugglers() > 1)
      throw new JuggleExceptionUser(errorstrings.getString("Error_optimizer_no_passing"));
    if (pat.isBouncePattern())
      throw new JuggleExceptionUser(errorstrings.getString("Error_optimizer_no_bouncing"));

    // Step 1: Lay out the pattern. This generates two things we need, the pattern event
    // list and the pattern pathlink list.

    pat.layoutPattern();
    JMLEvent events = pat.getEventList();
    ArrayList<ArrayList<PathLink>> pathlinks = pat.getPathLinks();

    // Step 2: Figure out the variables in the margin equations. Find the master events
    // in the pattern, in particular the ones that are throws or catches. The x-coordinate
    // of each will be a free variable in our equations.

    ArrayList<JMLEvent> variableEvents = new ArrayList<JMLEvent>();

    double maxValue = 0;
    double g = 980; // cm per second^2

    JMLEvent ev = events;
    while (ev != null) {
      if (ev.isMaster()) {
        for (JMLTransition tr : ev.transitions()) {
          int type = tr.getType();
          if (type == JMLTransition.TRANS_THROW
              || type == JMLTransition.TRANS_CATCH
              || type == JMLTransition.TRANS_SOFTCATCH
              || type == JMLTransition.TRANS_GRABCATCH) {
            ++varsNum;
            variableEvents.add(ev);
            Coordinate coord = ev.getLocalCoordinate();
            if (Math.abs(coord.x) > maxValue) maxValue = Math.abs(coord.x);

            if (type == JMLTransition.TRANS_THROW) {
              ParameterList pl = new ParameterList(tr.getMod());
              String gparam = pl.getParameter("g");
              if (gparam != null) {
                try {
                  g = JLFunc.parseDouble(gparam);
                } catch (NumberFormatException nfe) {
                }
              }
            }
            break;
          }
        }
      }
      ev = ev.getNext();
    }
    if (Constants.DEBUG_OPTIMIZE) {
      System.out.println("   number of variables = " + varsNum);
      System.out.println("   maxValue = " + maxValue);
      System.out.println("   g = " + g);
    }

    // Step 3: Set up the arrays containing the current values of our variables, their
    // minimum and maximum allowed values, and corresponding JMLEvents

    varsEvents = new JMLEvent[varsNum];
    varsValues = new double[varsNum];
    varsMin = new double[varsNum];
    varsMax = new double[varsNum];

    for (int i = 0; i < varsNum; i++) {
      ev = variableEvents.get(i);
      Coordinate coord = ev.getLocalCoordinate();
      int type = ev.getTransition(0).getType();

      varsEvents[i] = ev;
      varsValues[i] = coord.x;
      // optimization won't move events to the other side of the body
      if (varsValues[i] > 0) {
        varsMin[i] = 0.1 * maxValue;
        varsMax[i] = maxValue;

        if (type == JMLTransition.TRANS_THROW) varsMax[i] *= 0.9;
      } else {
        varsMin[i] = -maxValue;
        varsMax[i] = -0.1 * maxValue;

        if (type == JMLTransition.TRANS_THROW) varsMin[i] *= 0.9;
      }
      if (Constants.DEBUG_OPTIMIZE)
        System.out.println("   variable " + i + " min = " + varsMin[i] + ", max = " + varsMax[i]);
    }

    // Step 4: Find the maximum radius of props in the pattern, used in the margin
    // calculation below

    double propradius = 0;
    for (int i = 0; i < pat.getNumberOfProps(); i++) {
      double thisprop = 0.5 * pat.getProp(i + 1).getWidth();
      if (thisprop > propradius) propradius = thisprop;
    }
    if (Constants.DEBUG_OPTIMIZE) System.out.println("   propradius = " + propradius);

    // Step 5: Identify the "master pathlinks", the non-hand pathlinks starting on
    // master events. Put them into a linear array for convenience

    int masterplNum = 0;
    PathLink[] masterpl = null;
    for (int pass = 1; pass < 3; pass++) {
      int k = 0;
      for (int i = 0; i < pathlinks.size(); i++) {
        for (int j = 0; j < pathlinks.get(i).size(); j++) {
          PathLink pl = pathlinks.get(i).get(j);
          if (!pl.isInHand() && pl.getStartEvent().isMaster()) {
            if (pass == 1) masterplNum++;
            else masterpl[k++] = pl;
          }
        }
      }
      if (pass == 1) masterpl = new PathLink[masterplNum];
    }
    if (Constants.DEBUG_OPTIMIZE)
      System.out.println("   number of master pathlinks = " + masterplNum);

    // Step 6: Figure out all distinct potential collisions in the pattern, and the
    // equation determining throw error margin for each one.
    //
    // Find all pathlink pairs (P1, P2) such that:
    // * P1 and P2 both represent paths through the air (not in the hands)
    // * P1 and P2 are either both air paths, or both bounced paths
    // * P1 starts on a master event
    // * P2 does not start on the same event as P1
    // * P2 starts no earlier than P1
    // * if P1 and P2 start at the same time, then P2 ends no earlier than P1
    // * if P1 and P2 start and end at the same time, then P2 is not from a smaller juggler number
    // than P1
    // * if P1 and P2 start and end at the same time, and are from the same juggler, then P1 is from
    // the right hand
    // * P1 and P2 can collide (t_same is defined and occurs when both are in the air)

    double sym_delay = -1;
    boolean sym_switchdelay = false;
    for (JMLSymmetry sym : pat.symmetries()) {
      switch (sym.getType()) {
        case JMLSymmetry.TYPE_DELAY:
          sym_delay = sym.getDelay();
          break;
        case JMLSymmetry.TYPE_SWITCHDELAY:
          sym_switchdelay = true;
          break;
        case JMLSymmetry.TYPE_SWITCH:
          throw new JuggleExceptionUser(errorstrings.getString("Error_no_optimize_switch"));
      }
    }

    ArrayList<double[]> eqns = new ArrayList<double[]>();

    if (Constants.DEBUG_OPTIMIZE) System.out.println("potential collisions:");
    for (int i = 0; i < masterplNum; i++) {
      for (int j = 0; j < masterplNum; j++) {
        PathLink mpl1 = masterpl[i];
        PathLink mpl2 = masterpl[j];

        // enumerate all of the ways that mpl2 could collide with mpl1.
        double mpl1_start = mpl1.getStartEvent().getT();
        double mpl1_end = mpl1.getEndEvent().getT();
        double mpl2_start = mpl2.getStartEvent().getT();
        double mpl2_end = mpl2.getEndEvent().getT();
        double delay = 0;
        boolean invert_mpl2 = false;

        do {
          boolean can_collide = true;

          // implement the criteria described above
          if (delay == 0.0 && mpl1.getStartEvent() == mpl2.getStartEvent()) can_collide = false;
          if (mpl1_start > (mpl2_start + delay)) can_collide = false;
          else if (mpl1_start == (mpl2_start + delay)) {
            if (mpl1_end > (mpl2_end + delay)) can_collide = false;
            else if (mpl1_end == (mpl2_end + delay)) {
              if (mpl1.getStartEvent().getJuggler() > mpl2.getStartEvent().getJuggler())
                can_collide = false;
              else if (mpl1.getStartEvent().getJuggler() == mpl2.getStartEvent().getJuggler()) {
                if (mpl1.getStartEvent().getHand() == HandLink.LEFT_HAND) can_collide = false;
              }
            }
          }

          double tsame = -1;
          double tsame_denom = (mpl2_start + mpl2_end + 2 * delay) - (mpl1_start + mpl1_end);
          if (tsame_denom == 0) can_collide = false;

          if (can_collide) {
            tsame =
                ((mpl2_start + delay) * (mpl2_end + delay) - mpl1_start * mpl1_end) / tsame_denom;

            if (tsame < mpl1_start
                || tsame > mpl1_end
                || tsame < (mpl2_start + delay)
                || tsame > (mpl2_end + delay)) can_collide = false;
          }

          if (can_collide) {
            // We have another potential collision in the pattern, and a new margin equation.
            //
            // The error margin associated with a potential collision is a linear function
            // of the x-coordinates of throw and catch points, for each of the two arcs
            // (4 coordinates in all):
            //
            // margin = sum_i {coef_i * x_i} + coef_varsNum

            double[] coefs = new double[varsNum + 1];

            // Calculate the angular margin of error (in radians) with the relations:
            //
            // margin * v_y1 * (tsame - t_t1) + margin * v_y2 * (tsame - t_t2)
            // = (horizontal distance btwn arcs at time t_same) - 2 * propradius
            // = abs(
            //     (x_t1 * (t_c1 - tsame) + x_c1 * (tsame - t_t1)) / (t_c1 - t_t1)
            //    - (x_t2 * (t_c2 - tsame) + x_c2 * (tsame - t_t2)) / (t_c2 - t_t2)
            //   ) - 2 * propradius
            //
            // where the vertical throwing velocities are:
            // v_y1 = 0.5 * g * (t_c1 - t_t1)
            // v_y2 = 0.5 * g * (t_c2 - t_t2)
            //
            // and t_t1, t_c1 are the throw and catch time of arc 1, etc.

            double t_t1 = mpl1_start;
            double t_c1 = mpl1_end;
            double t_t2 = mpl2_start + delay;
            double t_c2 = mpl2_end + delay;

            double v_y1 = 0.5 * g * (t_c1 - t_t1);
            double v_y2 = 0.5 * g * (t_c2 - t_t2);
            double denom = v_y1 * (tsame - t_t1) + v_y2 * (tsame - t_t2);

            if (denom > EPSILON) {
              denom *= Math.PI / 180; // so margin will be in degrees

              double coef_t1 = (t_c1 - tsame) / ((t_c1 - t_t1) * denom);
              double coef_c1 = (tsame - t_t1) / ((t_c1 - t_t1) * denom);
              double coef_t2 = -(t_c2 - tsame) / ((t_c2 - t_t2) * denom);
              double coef_c2 = -(tsame - t_t2) / ((t_c2 - t_t2) * denom);
              double coef_0 = -2 * propradius / denom;

              int t1_varnum, c1_varnum, t2_varnum, c2_varnum;

              JMLEvent mplev = mpl1.getStartEvent();
              if (!mplev.isMaster()) {
                if (mplev.getHand() != mplev.getMaster().getHand()) coef_t1 = -coef_t1;
                mplev = mplev.getMaster();
              }
              t1_varnum = variableEvents.indexOf(mplev);
              mplev = mpl1.getEndEvent();
              if (!mplev.isMaster()) {
                if (mplev.getHand() != mplev.getMaster().getHand()) coef_c1 = -coef_c1;
                mplev = mplev.getMaster();
              }
              c1_varnum = variableEvents.indexOf(mplev);
              mplev = mpl2.getStartEvent();
              if (!mplev.isMaster()) {
                if (mplev.getHand() != mplev.getMaster().getHand()) coef_t2 = -coef_t2;
                mplev = mplev.getMaster();
              }
              t2_varnum = variableEvents.indexOf(mplev);
              mplev = mpl2.getEndEvent();
              if (!mplev.isMaster()) {
                if (mplev.getHand() != mplev.getMaster().getHand()) coef_c2 = -coef_c2;
                mplev = mplev.getMaster();
              }
              c2_varnum = variableEvents.indexOf(mplev);

              if (t1_varnum < 0 || c1_varnum < 0 || t2_varnum < 0 || c2_varnum < 0)
                throw new JuggleExceptionInternal("Could not find master event in variableEvents");

              if (invert_mpl2) {
                coef_t2 = -coef_t2;
                coef_c2 = -coef_c2;
              }

              coefs[t1_varnum] += coef_t1;
              coefs[c1_varnum] += coef_c1;
              coefs[t2_varnum] += coef_t2;
              coefs[c2_varnum] += coef_c2;
              coefs[varsNum] = coef_0;

              // define coefficients so distance (ignoring prop dimension) is nonnegative
              double dist = 0;
              for (int k = 0; k < varsNum; k++) dist += coefs[k] * varsValues[k];
              if (dist < 0) {
                for (int k = 0; k < varsNum; k++) {
                  if (coefs[k] != 0) coefs[k] = -coefs[k];
                }
              }

              eqns.add(coefs);
              ++marginsNum;

              if (Constants.DEBUG_OPTIMIZE)
                System.out.println("   mpl[" + i + "] and mpl[" + j + "] at tsame = " + tsame);
            }
          }

          if (sym_switchdelay) {
            delay += 0.5 * sym_delay;
            invert_mpl2 = !invert_mpl2;
          } else {
            delay += sym_delay;
          }

        } while (mpl1_end > (mpl2_start + delay));
      }
    }

    // Step 7: De-duplicate the list of equations; for various reasons the same equation
    // can appear multiple times.

    if (Constants.DEBUG_OPTIMIZE) {
      System.out.println("total margin equations = " + marginsNum);
      for (int i = 0; i < marginsNum; i++) {
        StringBuffer sb = new StringBuffer();
        sb.append("{ ");
        double[] temp = eqns.get(i);
        for (int j = 0; j <= varsNum; j++) {
          sb.append(JLFunc.toStringRounded(temp[j], 4));
          if (j == (varsNum - 1)) sb.append(" : ");
          else if (j != varsNum) sb.append(", ");
        }
        double dtemp = temp[varsNum];
        for (int j = 0; j < varsNum; j++) dtemp += temp[j] * varsValues[j];
        sb.append(" } --> " + JLFunc.toStringRounded(dtemp, 4));

        System.out.println("   eq[" + i + "] = " + sb.toString());
      }
      System.out.println("de-duplicating equations...");
    }

    int orig_row = 1;

    for (int i = 1; i < marginsNum; i++) {
      boolean dupoverall = false;
      double[] rowi = eqns.get(i);

      for (int j = 0; !dupoverall && j < i; j++) {
        double[] rowj = eqns.get(j);
        boolean duprow = true;
        for (int k = 0; duprow && k <= varsNum; k++) {
          if (rowi[k] < (rowj[k] - EPSILON) || rowi[k] > (rowj[k] + EPSILON)) duprow = false;
        }
        dupoverall |= duprow;
      }

      if (dupoverall) {
        if (Constants.DEBUG_OPTIMIZE)
          System.out.println("   removed duplicate equation " + orig_row);
        eqns.remove(i);
        --i;
        --marginsNum;
      }
      if (Constants.DEBUG_OPTIMIZE) ++orig_row;
    }

    // Step 8: Move the equations into an array, and sort it based on margins at the
    // current values of the variables.

    marginsEqs = new LinearEquation[marginsNum];
    for (int i = 0; i < marginsNum; i++) {
      marginsEqs[i] = new LinearEquation(varsNum);
      marginsEqs[i].setCoefficients(eqns.get(i));
    }

    if (Constants.DEBUG_OPTIMIZE) {
      System.out.println("total margin equations = " + marginsNum);
      for (int i = 0; i < marginsNum; i++) {
        StringBuffer sb = new StringBuffer();
        sb.append("{ ");
        for (int j = 0; j <= varsNum; j++) {
          sb.append(JLFunc.toStringRounded(marginsEqs[i].coef(j), 4));
          if (j == (varsNum - 1)) sb.append(" : ");
          else if (j != varsNum) sb.append(", ");
        }
        double dtemp = marginsEqs[i].constant();
        for (int j = 0; j < varsNum; j++) dtemp += marginsEqs[i].coef(j) * varsValues[j];
        sb.append(" } --> " + JLFunc.toStringRounded(dtemp, 4));

        System.out.println("   eq[" + i + "] = " + sb.toString());
      }
    }

    sort();

    if (Constants.DEBUG_OPTIMIZE) {
      System.out.println("sorted:");
      for (int i = 0; i < marginsNum; i++) {
        StringBuffer sb = new StringBuffer();
        sb.append("{ ");
        for (int j = 0; j <= varsNum; j++) {
          sb.append(JLFunc.toStringRounded(marginsEqs[i].coef(j), 4));
          if (j == (varsNum - 1)) sb.append(" : ");
          else if (j != varsNum) sb.append(", ");
        }
        double dtemp = marginsEqs[i].constant();
        for (int j = 0; j < varsNum; j++) dtemp += marginsEqs[i].coef(j) * varsValues[j];
        sb.append(" } --> " + JLFunc.toStringRounded(dtemp, 4));

        System.out.println("   eq[" + i + "] = " + sb.toString());
      }
    }
  }

  public void sort() {
    Comparator<LinearEquation> comp =
        new Comparator<LinearEquation>() {
          @Override
          public int compare(LinearEquation eq1, LinearEquation eq2) {
            if (eq1.done() && !eq2.done()) return -1;
            if (!eq1.done() && eq2.done()) return 1;

            double m1 = eq1.constant();
            double m2 = eq2.constant();
            for (int i = 0; i < varsNum; i++) {
              m1 += eq1.coef(i) * varsValues[i];
              m2 += eq2.coef(i) * varsValues[i];
            }
            if (m1 < m2) return -1;
            else if (m1 > m2) return 1;
            return 0;
          }

          @Override
          public boolean equals(Object eq) {
            return false;
          }
        };

    Arrays.sort(marginsEqs, comp);
  }
}
