//
// Juggler.java
//
// This class calculates the coordinates of the juggler elbows, shoulders, etc.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.renderer;

import jugglinglab.jml.HandLink;
import jugglinglab.jml.JMLPattern;
import jugglinglab.util.*;

public class Juggler {
  // juggler dimensions, in centimeters
  public static final double SHOULDER_HW = 23; // shoulder half-width (cm)
  public static final double SHOULDER_H = 40; // throw pos. to shoulder
  public static final double WAIST_HW = 17; // waist half-width
  public static final double WAIST_H = -5;
  // public final static double ELBOW_HW = 30;  // elbow "home"
  // public final static double ELBOW_H = 6;
  // public final static double ELBOW_SLOP = 12;
  public static final double HAND_OUT = 5; // outside width of hand
  public static final double HAND_IN = 5;
  public static final double HEAD_HW = 10; // head half-width
  public static final double HEAD_H = 26; // head height
  public static final double NECK_H = 5; // neck height
  public static final double SHOULDER_Y = 0;
  public static final double PATTERN_Y = 30;
  public static final double UPPER_LENGTH = 41;
  public static final double LOWER_LENGTH = 40;

  public static final double LOWER_GAP_WRIST = 1;
  public static final double LOWER_GAP_ELBOW = 0;
  public static final double LOWER_HAND_HEIGHT = 0;
  public static final double UPPER_GAP_ELBOW = 0;
  public static final double UPPER_GAP_SHOULDER = 0;

  protected static final double LOWER_TOTAL = LOWER_LENGTH + LOWER_GAP_WRIST + LOWER_GAP_ELBOW;
  protected static final double UPPER_TOTAL = UPPER_LENGTH + UPPER_GAP_ELBOW + UPPER_GAP_SHOULDER;

  // the remaining are used only for the 3d display
  // public final static double SHOULDER_RADIUS = 6;
  // public final static double ELBOW_RADIUS = 4;
  // public final static double WRIST_RADIUS = 2;

  public static void findJugglerCoordinates(JMLPattern pat, double time, JLVector[][] result)
      throws JuggleExceptionInternal {
    for (int juggler = 1; juggler <= pat.getNumberOfJugglers(); juggler++) {
      JLVector lefthand, righthand;
      JLVector leftshoulder, rightshoulder;
      JLVector leftelbow, rightelbow;
      JLVector leftwaist, rightwaist;
      JLVector leftheadbottom, leftheadtop;
      JLVector rightheadbottom, rightheadtop;

      Coordinate coord0 = new Coordinate();
      Coordinate coord1 = new Coordinate();
      Coordinate coord2 = new Coordinate();
      pat.getHandCoordinate(juggler, HandLink.LEFT_HAND, time, coord0);
      pat.getHandCoordinate(juggler, HandLink.RIGHT_HAND, time, coord1);
      lefthand = new JLVector(coord0.x, coord0.z + LOWER_HAND_HEIGHT, coord0.y);
      righthand = new JLVector(coord1.x, coord1.z + LOWER_HAND_HEIGHT, coord1.y);

      pat.getJugglerPosition(juggler, time, coord2);
      double angle = Math.toRadians(pat.getJugglerAngle(juggler, time));
      double s = Math.sin(angle);
      double c = Math.cos(angle);

      leftshoulder =
          new JLVector(
              coord2.x - SHOULDER_HW * c - SHOULDER_Y * s,
              coord2.z + SHOULDER_H,
              coord2.y - SHOULDER_HW * s + SHOULDER_Y * c);
      rightshoulder =
          new JLVector(
              coord2.x + SHOULDER_HW * c - SHOULDER_Y * s,
              coord2.z + SHOULDER_H,
              coord2.y + SHOULDER_HW * s + SHOULDER_Y * c);
      leftwaist =
          new JLVector(
              coord2.x - WAIST_HW * c - SHOULDER_Y * s,
              coord2.z + WAIST_H,
              coord2.y - WAIST_HW * s + SHOULDER_Y * c);
      rightwaist =
          new JLVector(
              coord2.x + WAIST_HW * c - SHOULDER_Y * s,
              coord2.z + WAIST_H,
              coord2.y + WAIST_HW * s + SHOULDER_Y * c);
      leftheadbottom =
          new JLVector(
              coord2.x - HEAD_HW * c - SHOULDER_Y * s,
              coord2.z + SHOULDER_H + NECK_H,
              coord2.y - HEAD_HW * s + SHOULDER_Y * c);
      leftheadtop =
          new JLVector(
              coord2.x - HEAD_HW * c - SHOULDER_Y * s,
              coord2.z + SHOULDER_H + NECK_H + HEAD_H,
              coord2.y - HEAD_HW * s + SHOULDER_Y * c);
      rightheadbottom =
          new JLVector(
              coord2.x + HEAD_HW * c - SHOULDER_Y * s,
              coord2.z + SHOULDER_H + NECK_H,
              coord2.y + HEAD_HW * s + SHOULDER_Y * c);
      rightheadtop =
          new JLVector(
              coord2.x + HEAD_HW * c - SHOULDER_Y * s,
              coord2.z + SHOULDER_H + NECK_H + HEAD_H,
              coord2.y + HEAD_HW * s + SHOULDER_Y * c);

      double L = LOWER_TOTAL;
      double U = UPPER_TOTAL;
      JLVector deltaL = JLVector.sub(lefthand, leftshoulder);
      double D = deltaL.length();
      if (D <= (L + U)) {
        // Calculate the coordinates of the elbows
        double Lr =
            Math.sqrt(
                (4.0 * U * U * L * L - (U * U + L * L - D * D) * (U * U + L * L - D * D))
                    / (4.0 * D * D));
        if (Double.isNaN(Lr)) throw new JuggleExceptionInternal("NaN in renderer 1");

        double factor = Math.sqrt(U * U - Lr * Lr) / D;
        if (Double.isNaN(factor)) throw new JuggleExceptionInternal("NaN in renderer 2");
        JLVector Lxsc = JLVector.scale(factor, deltaL);
        double Lalpha = Math.asin(deltaL.y / D);
        if (Double.isNaN(Lalpha)) throw new JuggleExceptionInternal("NaN in renderer 3");
        factor = 1.0 + Lr * Math.tan(Lalpha) / (factor * D);
        leftelbow =
            new JLVector(
                leftshoulder.x + Lxsc.x * factor,
                leftshoulder.y + Lxsc.y - Lr * Math.cos(Lalpha),
                leftshoulder.z + Lxsc.z * factor);
      } else {
        leftelbow = null;
      }

      JLVector deltaR = JLVector.sub(righthand, rightshoulder);
      D = deltaR.length();
      if (D <= (L + U)) {
        // Calculate the coordinates of the elbows
        double Rr =
            Math.sqrt(
                (4.0 * U * U * L * L - (U * U + L * L - D * D) * (U * U + L * L - D * D))
                    / (4.0 * D * D));
        if (Double.isNaN(Rr)) throw new JuggleExceptionInternal("NaN in renderer 4");

        double factor = Math.sqrt(U * U - Rr * Rr) / D;
        if (Double.isNaN(factor)) throw new JuggleExceptionInternal("NaN in renderer 5");
        JLVector Rxsc = JLVector.scale(factor, deltaR);
        double Ralpha = Math.asin(deltaR.y / D);
        if (Double.isNaN(Ralpha)) throw new JuggleExceptionInternal("NaN in renderer 6");
        factor = 1.0 + Rr * Math.tan(Ralpha) / (factor * D);
        rightelbow =
            new JLVector(
                rightshoulder.x + Rxsc.x * factor,
                rightshoulder.y + Rxsc.y - Rr * Math.cos(Ralpha),
                rightshoulder.z + Rxsc.z * factor);
      } else {
        rightelbow = null;
      }

      result[juggler - 1][0] = lefthand;
      result[juggler - 1][1] = righthand;
      result[juggler - 1][2] = leftshoulder;
      result[juggler - 1][3] = rightshoulder;
      result[juggler - 1][4] = leftelbow;
      result[juggler - 1][5] = rightelbow;
      result[juggler - 1][6] = leftwaist;
      result[juggler - 1][7] = rightwaist;
      result[juggler - 1][8] = leftheadbottom;
      result[juggler - 1][9] = leftheadtop;
      result[juggler - 1][10] = rightheadbottom;
      result[juggler - 1][11] = rightheadtop;
    }
  }
}
