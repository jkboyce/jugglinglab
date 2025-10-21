//
// Renderer2D.java
//
// Class that draws the juggling into the frame.
//
// It is designed so that no object allocation happens in drawFrame().
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.util.ArrayList;
import jugglinglab.core.Constants;
import jugglinglab.jml.JMLPattern;
import jugglinglab.prop.Prop;
import jugglinglab.util.Coordinate;
import jugglinglab.util.JuggleExceptionInternal;

public class Renderer2D extends Renderer {
  public static final int RENDER_POINT_FIELD = 0;
  public static final int RENDER_WIRE_FRAME = 1;
  public static final int RENDER_FLAT_SOLID = 2;
  protected int render_type = RENDER_FLAT_SOLID;  // one of the above

  protected Color background;
  protected Coordinate left;
  protected Coordinate right;
  protected JLVector cameracenter;
  protected double[] cameraangle;
  protected JLMatrix m;

  protected int width;
  protected int height;
  protected Rectangle viewport;
  protected JMLPattern pat;

  protected double zoom;  // pixels/cm
  protected double zoom_orig;  // pixels/cm at zoomfactor=1
  protected double zoomfactor;  // multiplier of `zoom`
  protected int originx;
  protected int originz;
  protected int polysides;  // # sides in polygon for head
  protected double[] headcos;
  protected double[] headsin;
  protected int[] headx;
  protected int[] heady;

  protected DrawObject2D[] obj;
  protected DrawObject2D[] obj2;
  protected JLVector[][] jugglervec;
  protected double propmin; // for drawing floor
  protected Coordinate tempc;
  protected JLVector tempv1;
  protected JLVector tempv2;

  public Renderer2D() {
    background = Color.white;
    cameraangle = new double[2];
    polysides = 40;
    headcos = new double[polysides];
    headsin = new double[polysides];
    headx = new int[polysides];
    heady = new int[polysides];
    for (int i = 0; i < polysides; i++) {
      headcos[i] = Math.cos((double) i * 2.0 * Math.PI / polysides);
      headsin[i] = Math.sin((double) i * 2.0 * Math.PI / polysides);
    }
    tempc = new Coordinate();
    tempv1 = new JLVector();
    tempv2 = new JLVector();
    zoomfactor = 1;
  }

  @Override
  public void setPattern(JMLPattern p) {
    pat = p;
    int maxobjects = 5 * pat.getNumberOfJugglers() + pat.getNumberOfPaths() + 18;
    obj = new DrawObject2D[maxobjects];
    for (int i = 0; i < maxobjects; i++) {
      obj[i] = new DrawObject2D(maxobjects);
    }
    obj2 = new DrawObject2D[maxobjects];
    jugglervec = new JLVector[pat.getNumberOfJugglers()][12];
  }

  @Override
  public Color getBackground() {
    return background;
  }

  @Override
  public void initDisplay(Dimension dim, int border, Coordinate overallmax, Coordinate overallmin) {
    width = dim.width;
    height = dim.height;
    viewport = new Rectangle(border, border, width - 2 * border, height - 2 * border);

    // Make some adjustments to the bounding box.
    Coordinate adjusted_max = new Coordinate(overallmax);
    Coordinate adjusted_min = new Coordinate(overallmin);

    final boolean ORIGINAL_ZOOM = true;

    if (ORIGINAL_ZOOM) {
      // This is the zoom algorithm that has been in Juggling Lab for many
      // years. It's a bit too zoomed-in for some patterns.

      // We want to ensure everything stays visible as we rotate the camera
      // viewpoint. The following is simple and seems to work ok.
      if (pat.getNumberOfJugglers() == 1) {
        adjusted_min.z -= 0.3 * Math.max(Math.abs(adjusted_min.y), Math.abs(adjusted_max.y));
        adjusted_max.z += 5.0; // keeps objects from rubbing against top of window
      } else {
        double tempx = Math.max(Math.abs(adjusted_min.x), Math.abs(adjusted_max.x));
        double tempy = Math.max(Math.abs(adjusted_min.y), Math.abs(adjusted_max.y));
        adjusted_min.z -= 0.4 * Math.max(tempx, tempy);
        adjusted_max.z += 0.4 * Math.max(tempx, tempy);
      }

      // make the x-coordinate origin at the center of the view
      double maxabsx = Math.max(Math.abs(adjusted_min.x), Math.abs(adjusted_max.x));
      adjusted_min.x = -maxabsx;
      adjusted_max.x = maxabsx;

      zoom_orig = Math.min(
              (double) viewport.width / (adjusted_max.x - adjusted_min.x),
              (double) viewport.height / (adjusted_max.z - adjusted_min.z));
    } else {
      // NEW ALGORITHM

      // make the x-coordinate origin at the center of the view
      double maxabsx = Math.max(Math.abs(adjusted_min.x), Math.abs(adjusted_max.x));
      adjusted_min.x = -maxabsx;
      adjusted_max.x = maxabsx;

      double dx = adjusted_max.x - adjusted_min.x;
      double dy = adjusted_max.y - adjusted_min.y;
      double dz = adjusted_max.z - adjusted_min.z;
      double dxy = Math.max(dx, dy);

      // Find `zoom` value that keeps the adjusted bounding box visible in
      // the viewport
      zoom_orig = Math.min(
              (double) viewport.width / Math.sqrt(dx * dx + dy * dy),
              (double) viewport.height / Math.sqrt(dxy * dxy + dz * dz));
    }

    // Pattern center vis-a-vis camera rotation
    cameracenter = new JLVector(
            0.5 * (adjusted_max.x + adjusted_min.x),
            0.5 * (adjusted_max.z + adjusted_min.z),
            0.5 * (adjusted_max.y + adjusted_min.y));

    setZoomLevel(getZoomLevel());  // calculate camera matrix etc.

    if (Constants.DEBUG_LAYOUT) {
      System.out.println("overallmax = " + overallmax);
      System.out.println("overallmin = " + overallmin);
      System.out.println("adjusted_max = " + adjusted_max);
      System.out.println("adjusted_min = " + adjusted_min);
      System.out.println("zoom_orig (px/cm) = " + zoom_orig);
    }
  }

  @Override
  public double getZoomLevel() {
    return zoomfactor;
  }

  @Override
  public void setZoomLevel(double z) {
    zoomfactor = z;
    zoom = zoom_orig * zoomfactor;

    originx = viewport.x + (int) Math.round(0.5 * viewport.width - zoom * cameracenter.x);
    originz = viewport.y + (int) Math.round(0.5 * viewport.height + zoom * cameracenter.y);

    calculateCameraMatrix();
  }

  @Override
  public void setCameraAngle(double[] camangle) {
    cameraangle[0] = camangle[0];
    cameraangle[1] = camangle[1];

    if (cameracenter == null) {
      return;
    }

    calculateCameraMatrix();
  }

  protected void calculateCameraMatrix() {
    m = JLMatrix.shiftMatrix(-cameracenter.x, -cameracenter.y, -cameracenter.z);
    m.transform(JLMatrix.rotateMatrix(0, Math.PI - cameraangle[0], 0));
    m.transform(JLMatrix.rotateMatrix(0.5 * Math.PI - cameraangle[1], 0, 0));
    m.transform(JLMatrix.shiftMatrix(cameracenter.x, cameracenter.y, cameracenter.z));

    m.transform(JLMatrix.scaleMatrix(1, -1, 1)); // larger y values -> smaller y pixel coord
    m.transform(JLMatrix.scaleMatrix(zoom));
    m.transform(JLMatrix.shiftMatrix(originx, originz, 0));
  }

  @Override
  public double[] getCameraAngle() {
    double[] ca = new double[2];
    ca[0] = cameraangle[0];
    ca[1] = cameraangle[1];
    return ca;
  }

  @Override
  public int[] getXY(Coordinate c) {
    return getXY(new JLVector(c.x, c.z, c.y));
  }

  protected int[] getXY(JLVector vec) {
    JLVector v = vec.transform(m); // apply camera rotation
    int[] val = new int[2];
    val[0] = (int) Math.round(v.x);
    val[1] = (int) Math.round(v.y);
    return val;
  }

  protected JLVector getXYZ(JLVector vec, JLVector result) {
    result.x = vec.x * m.m00 + vec.y * m.m01 + vec.z * m.m02 + m.m03;
    result.y = vec.x * m.m10 + vec.y * m.m11 + vec.z * m.m12 + m.m13;
    result.z = vec.x * m.m20 + vec.y * m.m21 + vec.z * m.m22 + m.m23;
    return result;
  }

  @Override
  public Coordinate getScreenTranslatedCoordinate(Coordinate c, int dx, int dy) {
    JLVector v = new JLVector(c.x, c.z, c.y);
    JLVector s = v.transform(m);
    JLVector news = JLVector.add(s, new JLVector(dx, dy, 0));
    JLVector newv = news.transform(m.inverse());
    return new Coordinate(newv.x, newv.z, newv.y);
  }

  @Override
  public void drawFrame(double time, int[] pnum, int[] hideJugglers, Graphics g)
      throws JuggleExceptionInternal {
    int numobjects = 5 * pat.getNumberOfJugglers() + pat.getNumberOfPaths() + 18;

    // first reset the objects in the object pool
    for (int i = 0; i < numobjects; i++) {
      obj[i].covering.clear();
    }

    // first create a list of objects in the display
    int index = 0;

    // props
    double propmin = 0;
    for (int i = 1; i <= pat.getNumberOfPaths(); i++) {
      obj[index].type = DrawObject2D.TYPE_PROP;
      obj[index].number = i;
      pat.getPathCoordinate(i, time, tempc);
      if (!tempc.isValid()) {
        tempc.setCoordinate(0, 0, 0);
      }
      getXYZ(JLVector.fromCoordinate(tempc, tempv1), obj[index].coord[0]);
      int x = (int) Math.round(obj[index].coord[0].x);
      int y = (int) Math.round(obj[index].coord[0].y);
      Prop pr = pat.getProp(pnum[i - 1]);
      if (pr.getProp2DImage(zoom, cameraangle) != null) {
        Dimension center = pr.getProp2DCenter(zoom);
        Dimension size = pr.getProp2DSize(zoom);
        obj[index].boundingbox.x = x - center.width;
        obj[index].boundingbox.y = y - center.height;
        obj[index].boundingbox.width = size.width;
        obj[index].boundingbox.height = size.height;
      }
      propmin = Math.min(propmin, pr.getMin().z);
      index++;
    }

    // ground (set of lines)
    if (showground) {
      for (int i = 0; i < 18; i++) {
        obj[index].type = DrawObject2D.TYPE_LINE;
        obj[index].number = 0; // unused

        // first 9 lines for ground:
        // (x, y, z): (-50 + 100 * i / 8, 0, -50) to
        //            (-50 + 100 * i / 8, 0,  50)
        // next 9 lines:
        // (x, y, z): (-50, 0, -50 + 100 * (i - 9) / 8) to
        //            ( 50, 0, -50 + 100 * (i - 9) / 8)
        if (i < 9) {
          tempv1.x = -50.0 + 100.0 * i / 8.0;
          tempv1.z = -50.0;
          tempv2.x = tempv1.x;
          tempv2.z = 50.0;
        } else {
          tempv1.x = -50.0;
          tempv1.z = -50.0 + 100.0 * (i - 9) / 8.0;
          tempv2.x = 50.0;
          tempv2.z = tempv1.z;
        }
        tempv1.y = tempv2.y = propmin;

        getXYZ(tempv1, obj[index].coord[0]);
        getXYZ(tempv2, obj[index].coord[1]);
        int x = Math.min((int) Math.round(obj[index].coord[0].x),
            (int) Math.round(obj[index].coord[1].x));
        int y = Math.min((int) Math.round(obj[index].coord[0].y),
            (int) Math.round(obj[index].coord[1].y));
        int width = Math.abs((int) Math.round(obj[index].coord[0].x)
            - (int) Math.round(obj[index].coord[1].x)) + 1;
        int height = Math.abs((int) Math.round(obj[index].coord[0].y)
            - (int) Math.round(obj[index].coord[1].y)) + 1;
        obj[index].boundingbox.x = x;
        obj[index].boundingbox.y = y;
        obj[index].boundingbox.width = width;
        obj[index].boundingbox.height = height;
        index++;
      }
    }

    // jugglers
    Juggler.findJugglerCoordinates(pat, time, jugglervec);

    for (int i = 1; i <= pat.getNumberOfJugglers(); i++) {
      if (hideJugglers != null) {
        boolean hide = false;
        for (int hideJuggler : hideJugglers) {
          if (hideJuggler == i) {
            hide = true;
            break;
          }
        }
        if (hide) {
          continue;
        }
      }

      obj[index].type = DrawObject2D.TYPE_BODY;
      obj[index].number = i;
      getXYZ(jugglervec[i - 1][2], obj[index].coord[0]); // left shoulder
      getXYZ(jugglervec[i - 1][3], obj[index].coord[1]); // right shoulder
      getXYZ(jugglervec[i - 1][7], obj[index].coord[2]); // right waist
      getXYZ(jugglervec[i - 1][6], obj[index].coord[3]); // left waist
      getXYZ(jugglervec[i - 1][8], obj[index].coord[4]); // left head bottom
      getXYZ(jugglervec[i - 1][9], obj[index].coord[5]); // left head top
      getXYZ(jugglervec[i - 1][10], obj[index].coord[6]); // right head bottom
      getXYZ(jugglervec[i - 1][11], obj[index].coord[7]); // right head top
      int xmin, xmax, ymin, ymax;
      xmin = xmax = (int) Math.round(obj[index].coord[0].x);
      ymin = ymax = (int) Math.round(obj[index].coord[0].y);
      for (int j = 1; j < 8; j++) {
        int x = (int) Math.round(obj[index].coord[j].x);
        int y = (int) Math.round(obj[index].coord[j].y);
        if (x < xmin) {
          xmin = x;
        }
        if (x > xmax) {
          xmax = x;
        }
        if (y < ymin) {
          ymin = y;
        }
        if (y > ymax) {
          ymax = y;
        }
      }
      // inset bb by one pixel to avoid intersection at shoulder:
      obj[index].boundingbox.x = xmin + 1;
      obj[index].boundingbox.y = ymin + 1;
      obj[index].boundingbox.width = xmax - xmin - 1;
      obj[index].boundingbox.height = ymax - ymin - 1;
      index++;

      // the lines for each arm, starting with the left:
      for (int j = 0; j < 2; j++) {
        if (jugglervec[i - 1][4 + j] == null) {
          obj[index].type = DrawObject2D.TYPE_LINE;
          obj[index].number = i;
          getXYZ(jugglervec[i - 1][2 + j], obj[index].coord[0]); // entire arm
          getXYZ(jugglervec[i - 1][j], obj[index].coord[1]);
          int x = Math.min((int) Math.round(obj[index].coord[0].x),
              (int) Math.round(obj[index].coord[1].x));
          int y = Math.min((int) Math.round(obj[index].coord[0].y),
              (int) Math.round(obj[index].coord[1].y));
          int width = Math.abs((int) Math.round(obj[index].coord[0].x)
              - (int) Math.round(obj[index].coord[1].x)) + 1;
          int height = Math.abs((int) Math.round(obj[index].coord[0].y)
              - (int) Math.round(obj[index].coord[1].y)) + 1;
          obj[index].boundingbox.x = x;
          obj[index].boundingbox.y = y;
          obj[index].boundingbox.width = width;
          obj[index].boundingbox.height = height;
          index++;
        } else {
          obj[index].type = DrawObject2D.TYPE_LINE;
          obj[index].number = i;
          getXYZ(jugglervec[i - 1][2 + j], obj[index].coord[0]); // upper arm
          getXYZ(jugglervec[i - 1][4 + j], obj[index].coord[1]);
          int x = Math.min((int) Math.round(obj[index].coord[0].x),
              (int) Math.round(obj[index].coord[1].x));
          int y = Math.min((int) Math.round(obj[index].coord[0].y),
              (int) Math.round(obj[index].coord[1].y));
          int width = Math.abs((int) Math.round(obj[index].coord[0].x)
              - (int) Math.round(obj[index].coord[1].x)) + 1;
          int height = Math.abs((int) Math.round(obj[index].coord[0].y)
              - (int) Math.round(obj[index].coord[1].y)) + 1;
          obj[index].boundingbox.x = x;
          obj[index].boundingbox.y = y;
          obj[index].boundingbox.width = width;
          obj[index].boundingbox.height = height;
          index++;

          obj[index].type = DrawObject2D.TYPE_LINE;
          obj[index].number = i;
          getXYZ(jugglervec[i - 1][4 + j], obj[index].coord[0]); // lower arm
          getXYZ(jugglervec[i - 1][j], obj[index].coord[1]);
          x = Math.min((int) Math.round(obj[index].coord[0].x),
              (int) Math.round(obj[index].coord[1].x));
          y = Math.min((int) Math.round(obj[index].coord[0].y),
              (int) Math.round(obj[index].coord[1].y));
          width = Math.abs((int) Math.round(obj[index].coord[0].x)
              - (int) Math.round(obj[index].coord[1].x)) + 1;
          height = Math.abs((int) Math.round(obj[index].coord[0].y)
              - (int) Math.round(obj[index].coord[1].y)) + 1;
          obj[index].boundingbox.x = x;
          obj[index].boundingbox.y = y;
          obj[index].boundingbox.width = width;
          obj[index].boundingbox.height = height;
          index++;
        }
      }
    }
    numobjects = index;

    // figure out which display elements are covering which other elements
    for (int i = 0; i < numobjects; i++) {
      for (int j = 0; j < numobjects; j++) {
        if (j == i) {
          continue;
        }
        if (obj[i].isCovering(obj[j])) {
          obj[i].covering.add(obj[j]);
        }
      }
      obj[i].drawn = false;
    }

    // figure out a drawing order
    index = 0;
    boolean changed = true;
    while (changed) {
      changed = false;

      for (int i = 0; i < numobjects; i++) {
        if (obj[i].drawn) {
          continue;
        }

        boolean candraw = true;
        for (int j = 0; j < obj[i].covering.size(); j++) {
          DrawObject2D temp = obj[i].covering.get(j);
          if (!temp.drawn) {
            candraw = false;
            break;
          }
        }
        if (candraw) {
          obj2[index] = obj[i];
          obj[i].drawn = true;
          index++;
          changed = true;
        }
      }
    }
    // just in case there were some that couldn't be drawn:
    for (int i = 0; i < numobjects; i++) {
      if (obj[i].drawn) {
        continue;
      }
      obj2[index] = obj[i];
      obj[i].drawn = true;
      index++;
      // System.out.println("got undrawable item, type "+obj[i].type);
    }

    // draw the objects in the sorted order
    for (int i = 0; i < numobjects; i++) {
      DrawObject2D ob = obj2[i];

      switch (ob.type) {
        case DrawObject2D.TYPE_PROP:
          Prop pr = pat.getProp(pnum[ob.number - 1]);
          int x = (int) Math.round(ob.coord[0].x);
          int y = (int) Math.round(ob.coord[0].y);
          Image propimage = pr.getProp2DImage(zoom, cameraangle);
          if (propimage != null) {
            Dimension grip = pr.getProp2DGrip(zoom);
            g.drawImage(propimage, x - grip.width, y - grip.height, null);
          } /* else {
                g.setColor(pr.getEditorColor());
                draw3DProp(ob.object, g);
            } */

          /*
          g.setColor(Color.black);
          g.drawLine(ob.boundingbox.x, ob.boundingbox.y,
                     ob.boundingbox.x + ob.boundingbox.width, ob.boundingbox.y);
          g.drawLine(ob.boundingbox.x + ob.boundingbox.width, ob.boundingbox.y,
                     ob.boundingbox.x + ob.boundingbox.width, ob.boundingbox.y + ob.boundingbox.height);
          g.drawLine(ob.boundingbox.x + ob.boundingbox.width, ob.boundingbox.y + ob.boundingbox.height,
                     ob.boundingbox.x, ob.boundingbox.y + ob.boundingbox.height);
          g.drawLine(ob.boundingbox.x, ob.boundingbox.y + ob.boundingbox.height,
                     ob.boundingbox.x, ob.boundingbox.y);
          */
          break;
        case DrawObject2D.TYPE_BODY:
          int[] bodyx = new int[4];
          int[] bodyy = new int[4];
          for (int j = 0; j < 4; j++) {
            bodyx[j] = (int) Math.round(ob.coord[j].x);
            bodyy[j] = (int) Math.round(ob.coord[j].y);
          }
          g.setColor(background);
          g.fillPolygon(bodyx, bodyy, 4);
          g.setColor(Color.black);
          g.drawPolygon(bodyx, bodyy, 4);

          double LheadBx = ob.coord[4].x;
          double LheadBy = ob.coord[4].y;
          // double LheadTx = ob.coord[5].x;
          double LheadTy = ob.coord[5].y;
          double RheadBx = ob.coord[6].x;
          double RheadBy = ob.coord[6].y;
          // double RheadTx = ob.coord[7].x;
          // double RheadTy = ob.coord[7].y;

          if (Math.abs(RheadBx - LheadBx) > 2.0) {
            // head is at least 2 pixels wide; draw it as a polygon
            for (int j = 0; j < polysides; j++) {
              headx[j] = (int) Math.round(
                  0.5 * (LheadBx + RheadBx + headcos[j] * (RheadBx - LheadBx)));
              heady[j] = (int) Math.round(
                  0.5 * (LheadBy + LheadTy + headsin[j] * (LheadBy - LheadTy))
                  + (headx[j] - LheadBx) * (RheadBy - LheadBy) / (RheadBx - LheadBx));
            }

            g.setColor(background);
            g.fillPolygon(headx, heady, polysides);
            g.setColor(Color.black);
            g.drawPolygon(headx, heady, polysides);
          } else {
            // head is edge-on; draw it as a line
            double h = Math.sqrt(
                (LheadBy - LheadTy) * (LheadBy - LheadTy)
                + (RheadBy - LheadBy) * (RheadBy - LheadBy));
            int headx = (int) Math.round(0.5 * (LheadBx + RheadBx));
            int heady1 = (int) Math.round(0.5 * (LheadTy + RheadBy + h));
            int heady2 = (int) Math.round(0.5 * (LheadTy + RheadBy - h));

            g.setColor(Color.black);
            g.drawLine(headx, heady1, headx, heady2);
          }
          break;
        case DrawObject2D.TYPE_LINE:
          g.setColor(Color.black);
          int x1 = (int) Math.round(ob.coord[0].x);
          int y1 = (int) Math.round(ob.coord[0].y);
          int x2 = (int) Math.round(ob.coord[1].x);
          int y2 = (int) Math.round(ob.coord[1].y);
          g.drawLine(x1, y1, x2, y2);
          break;
      }

      /*
      g.setColor(Color.black);
      g.drawLine(ob.boundingbox.x, ob.boundingbox.y,
                 ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y);
      g.drawLine(ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y,
                 ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y+ob.boundingbox.height-1);
      g.drawLine(ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y+ob.boundingbox.height-1,
                 ob.boundingbox.x, ob.boundingbox.y+ob.boundingbox.height-1);
      g.drawLine(ob.boundingbox.x, ob.boundingbox.y+ob.boundingbox.height-1,
                 ob.boundingbox.x, ob.boundingbox.y);
      */
    }
  }

  @Override
  public Coordinate getHandWindowMax() {
    return new Coordinate(Juggler.HAND_OUT, 0, 1);
  }

  @Override
  public Coordinate getHandWindowMin() {
    return new Coordinate(-Juggler.HAND_IN, 0, -1);
  }

  @Override
  public Coordinate getJugglerWindowMax() {
    Coordinate max = pat.getJugglerMax(1);
    for (int i = 2; i <= pat.getNumberOfJugglers(); i++) {
      max = Coordinate.max(max, pat.getJugglerMax(i));
    }

    max = Coordinate.add(
            max,
            new Coordinate(
                Juggler.SHOULDER_HW,
                Juggler.SHOULDER_HW, // Juggler.HEAD_HW,
                Juggler.SHOULDER_H + Juggler.NECK_H + Juggler.HEAD_H));
    return max;
  }

  @Override
  public Coordinate getJugglerWindowMin() {
    Coordinate min = pat.getJugglerMin(1);
    for (int i = 2; i <= pat.getNumberOfJugglers(); i++) {
      min = Coordinate.min(min, pat.getJugglerMin(i));
    }

    min = Coordinate.add(min, new Coordinate(-Juggler.SHOULDER_HW, -Juggler.SHOULDER_HW, 0));
    return min;
  }

  //----------------------------------------------------------------------------
  // Class for defining the objects to draw
  //----------------------------------------------------------------------------

  public static class DrawObject2D {
    public static final int TYPE_PROP = 1;
    public static final int TYPE_BODY = 2;
    public static final int TYPE_LINE = 3;

    protected static final double SLOP = 3;

    public int type;
    public int number; // either path number or juggler number
    public JLVector[] coord;
    public Rectangle boundingbox;
    public ArrayList<DrawObject2D> covering;
    public boolean drawn;
    public JLVector tempv;

    public DrawObject2D(int numobjects) {
      coord = new JLVector[8];
      for (int i = 0; i < 8; i++) {
        coord[i] = new JLVector();
      }
      boundingbox = new Rectangle();
      covering = new ArrayList<>(numobjects);
      tempv = new JLVector();
    }

    public boolean isCovering(DrawObject2D obj) {
      if (!boundingbox.intersects(obj.boundingbox)) {
        return false;
      }

      switch (type) {
        case TYPE_PROP:
          switch (obj.type) {
            case TYPE_PROP:
              return (coord[0].z < obj.coord[0].z);
            case TYPE_BODY:
              {
                vectorProduct(obj.coord[0], obj.coord[1], obj.coord[2], tempv);
                if (tempv.z == 0.0) {
                  return false;
                }
                double z = obj.coord[0].z - (tempv.x * (coord[0].x - obj.coord[0].x)
                      + tempv.y * (coord[0].y - obj.coord[0].y)) / tempv.z;
                return (coord[0].z < z);
              }
            case TYPE_LINE:
              return (isBoxCoveringLine(this, obj) == 1);
          }
          break;
        case TYPE_BODY:
          switch (obj.type) {
            case TYPE_PROP:
              {
                vectorProduct(coord[0], coord[1], coord[2], tempv);
                if (tempv.z == 0.0) {
                  return false;
                }
                double z = coord[0].z - (tempv.x * (obj.coord[0].x - coord[0].x)
                      + tempv.y * (obj.coord[0].y - coord[0].y)) / tempv.z;
                return (z < obj.coord[0].z);
              }
            case TYPE_BODY:
              {
                double d = 0.0;
                for (int i = 0; i < 4; i++) {
                  d += (coord[i].z - obj.coord[i].z);
                }
                return (d < 0.0);
              }
            case TYPE_LINE:
              return (isBoxCoveringLine(this, obj) == 1);
          }
          break;
        case TYPE_LINE:
          switch (obj.type) {
            case TYPE_PROP:
            case TYPE_BODY:
              return (isBoxCoveringLine(obj, this) == -1);
            case TYPE_LINE:
              return false;
          }
          break;
      }

      return false;
    }

    // Returns 1 if box covers line, -1 if line covers box, 0 otherwise.

    protected int isBoxCoveringLine(DrawObject2D box, DrawObject2D line) {
      // If at least one end of the line is inside the box's boundingbox, then return
      // 1 if all such ends are behind the box, and -1 otherwise.
      // If neither end is inside the boundingbox, then find intersections between the
      // line and the boundingbox.  If no points of intersection, return 0.  If the
      // line is behind the bb at all points of intersection, return 1.  Otherwise
      // return -1;

      if (box.type == TYPE_BODY) {
        vectorProduct(box.coord[0], box.coord[1], box.coord[2], tempv);
      } else {
        tempv.x = 0;
        tempv.y = 0;
        tempv.z = 1;
      }

      if (tempv.z == 0) {
        return 0;  // box is exactly sideways
      }

      boolean endinbb = false;
      for (int i = 0; i < 2; i++) {
        double x = line.coord[i].x;
        double y = line.coord[i].y;

        if (box.boundingbox.contains((int) (x + 0.5), (int) (y + 0.5))) {
          double zb = box.coord[0].z
                  - (tempv.x * (x - box.coord[0].x) + tempv.y * (y - box.coord[0].y)) / tempv.z;
          if (line.coord[i].z < (zb - SLOP)) {
            return -1;
          }
          endinbb = true;
        }
      }
      if (endinbb) {
        return 1;  // know that end wasn't in front of body
      }

      boolean intersection = false;
      for (int i = 0; i < 2; i++) {
        int x = ((i == 0) ? box.boundingbox.x : (box.boundingbox.x + box.boundingbox.width - 1));
        if (x < Math.min(line.coord[0].x, line.coord[1].x)
            || x > Math.max(line.coord[0].x, line.coord[1].x)) {
          continue;
        }
        if (line.coord[1].x == line.coord[0].x) {
          continue;
        }
        double y = line.coord[0].y + (line.coord[1].y - line.coord[0].y)
              * ((double) x - line.coord[0].x) / (line.coord[1].x - line.coord[0].x);
        if (y < box.boundingbox.y || y > (box.boundingbox.y + box.boundingbox.height - 1)) {
          continue;
        }
        intersection = true;
        double zb = box.coord[0].z
                - (tempv.x * (x - box.coord[0].x) + tempv.y * (y - box.coord[0].y)) / tempv.z;
        double zl = line.coord[0].z
                + (line.coord[1].z - line.coord[0].z)
                    * ((double) x - line.coord[0].x)
                    / (line.coord[1].x - line.coord[0].x);
        if (zl < (zb - SLOP)) {
          return -1;
        }
      }
      for (int i = 0; i < 2; i++) {
        int y = ((i == 0) ? box.boundingbox.y : (box.boundingbox.y + box.boundingbox.height - 1));
        if (y < Math.min(line.coord[0].y, line.coord[1].y)
            || y > Math.max(line.coord[0].y, line.coord[1].y)) {
          continue;
        }
        if (line.coord[1].y == line.coord[0].y) {
          continue;
        }
        double x = line.coord[0].x + (line.coord[1].x - line.coord[0].x)
                    * ((double) y - line.coord[0].y) / (line.coord[1].y - line.coord[0].y);
        if (x < box.boundingbox.x || x > (box.boundingbox.x + box.boundingbox.width - 1)) {
          continue;
        }
        intersection = true;
        double zb = box.coord[0].z
                - (tempv.x * (x - box.coord[0].x) + tempv.y * ((double) y - box.coord[0].y))
                / tempv.z;
        double zl = line.coord[0].z
                + (line.coord[1].z - line.coord[0].z) * (x - line.coord[0].x)
                / (line.coord[1].x - line.coord[0].x);
        if (zl < (zb - SLOP)) {
          return -1;
        }
      }

      return (intersection ? 1 : 0);
    }

    public JLVector vectorProduct(JLVector v1, JLVector v2, JLVector v3, JLVector result) {
      double ax = v2.x - v1.x;
      double ay = v2.y - v1.y;
      double az = v2.z - v1.z;
      double bx = v3.x - v1.x;
      double by = v3.y - v1.y;
      double bz = v3.z - v1.z;
      result.x = ay * bz - by * az;
      result.y = az * bx - bz * ax;
      result.z = ax * by - bx * ay;
      return result;
    }
  }
}
