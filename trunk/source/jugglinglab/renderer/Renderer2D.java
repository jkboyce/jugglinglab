// Renderer2D.java
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

package jugglinglab.renderer;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.lang.reflect.*;
import javax.swing.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;
import jugglinglab.prop.*;


public class Renderer2D extends Renderer {
	public static final int RENDER_POINT_FIELD = 0;
	public static final int RENDER_WIRE_FRAME = 1;
	public static final int RENDER_FLAT_SOLID = 2;
	protected int render_type = RENDER_FLAT_SOLID; // One of the above
	
    protected Color			background = null;
    protected Coordinate	left = null, right = null;
    protected JLVector		cameracenter;
    protected double[]		cameraangle;
    protected double		cameradistance;
    protected JLMatrix	m;

    protected int			width, height;
    protected JMLPattern	pat = null;

    protected double		zoom;
    protected int			originx, originz;
    protected int			polysides;	// # sides in polygon for head
    protected double[]		headcos, headsin;
    protected int[]			headx, heady;

    protected DrawObject2D[]	obj = null, obj2 = null;
    protected JLVector[][]	jugglervec = null;
    protected Coordinate		tempc = null;
    protected JLVector		tempv = null;

    public Renderer2D() {
        this.background = Color.white;
        this.cameraangle = new double[2];
        this.polysides = 40;
        headcos = new double[polysides];
        headsin = new double[polysides];
        headx = new int[polysides];
        heady = new int[polysides];
        for (int i = 0; i < polysides; i++) {
            headcos[i] = Math.cos((double)i * JLMath.toRad(360.0) / polysides);
            headsin[i] = Math.sin((double)i * JLMath.toRad(360.0) / polysides);
        }
        this.tempc = new Coordinate();
        this.tempv = new JLVector();
    }

    public void setPattern(JMLPattern pat) {
        this.pat = pat;
        int numobjects = 5*pat.getNumberOfJugglers() + pat.getNumberOfPaths();
        this.obj = new DrawObject2D[numobjects];
        for (int i = 0; i < numobjects; i++)
            obj[i] = new DrawObject2D(numobjects);
        this.obj2 = new DrawObject2D[numobjects];
        this.jugglervec = new JLVector[pat.getNumberOfJugglers()][12];
    }

    public Color getBackground() { return this.background; }

    public void initDisplay(Dimension dim, int border,
                            Coordinate overallmax, Coordinate overallmin) {
        this.width = dim.width;
        this.height = dim.height;
        Rectangle r = new Rectangle(border, border, width-2*border, height-2*border);
        calcScaling(r, overallmax, overallmin);
        this.cameradistance = 1000.0;
        this.cameracenter = new JLVector(0.5*(overallmax.x+overallmin.x),
                                             0.5*(overallmax.z+overallmin.z),
                                             0.5*(overallmax.y+overallmin.y));
        setCameraAngle(this.cameraangle);	// sets camera position
    }

    public void setCameraAngle(double[] camangle) {
		
        this.cameraangle[0] = camangle[0];
        this.cameraangle[1] = camangle[1];

        if (cameracenter == null)
            return;

        m = JLMatrix.shiftMatrix(-cameracenter.x, -cameracenter.y, -cameracenter.z);
        m.transform(JLMatrix.rotateMatrix(0.0, JLMath.toRad(180.0) - cameraangle[0], 0.0));
        m.transform(JLMatrix.rotateMatrix(JLMath.toRad(90.0) - cameraangle[1], 0.0, 0.0));
        m.transform(JLMatrix.shiftMatrix(cameracenter.x, cameracenter.y, cameracenter.z));

        m.transform(JLMatrix.scaleMatrix(1.0, -1.0, 1.0));	// larger y values -> smaller y pixel coord
        m.transform(JLMatrix.scaleMatrix(this.zoom));
        m.transform(JLMatrix.shiftMatrix(this.originx, this.originz, 0.0));
    }
	
    public double[] getCameraAngle() {
        double[] ca = new double[2];
        ca[0] = cameraangle[0];
        ca[1] = cameraangle[1];
        return ca;
    }

    public int[] getXY(Coordinate c) {
        return getXY(new JLVector(c.x, c.z, c.y));
    }
    protected int[] getXY(JLVector vec) {
        JLVector v = vec.transform(m);	// apply camera rotation
        int[] val = new int[2];
        val[0] = (int)(v.x + 0.5f);
        val[1] = (int)(v.y + 0.5f);
        return val;
    }
    protected JLVector getXYZ(JLVector vec, JLVector result) {
        result.x = vec.x*m.m00 + vec.y*m.m01 + vec.z*m.m02 + m.m03;
        result.y = vec.x*m.m10 + vec.y*m.m11 + vec.z*m.m12 + m.m13;
        result.z = vec.x*m.m20 + vec.y*m.m21 + vec.z*m.m22 + m.m23;
        return result;
    }

    public Coordinate getScreenTranslatedCoordinate(Coordinate c, int dx, int dy) {
        JLVector v = new JLVector(c.x, c.z, c.y);
        JLVector s = v.transform(m);
        JLVector news = JLVector.add(s, new JLVector(dx, dy, 0.0));
        JLVector newv = news.transform(m.inverse());
        return new Coordinate(newv.x, newv.z, newv.y);
    }


    public void drawFrame(double time, int[] pnum, Graphics g, JPanel pan) throws JuggleExceptionInternal {
        // try to turn on antialiased rendering
        VersionSpecific.getVersionSpecific().setAntialias(g);

        int numobjects = 5*pat.getNumberOfJugglers() + pat.getNumberOfPaths();
        // first reset the objects in the object pool
        for (int i = 0; i < numobjects; i++)
            obj[i].covering.removeAllElements();

        // first create a list of objects in the display
        int index = 0;

        for (int i = 1; i <= pat.getNumberOfPaths(); i++) {
            obj[index].type = DrawObject2D.TYPE_PROP;
            obj[index].number = i;
            pat.getPathCoordinate(i, time, tempc);
            if (!tempc.isValid())
                tempc.setCoordinate(0.0,0.0,0.0);
            getXYZ(Renderer.toVector(tempc, tempv), obj[index].coord[0]);
            int x = (int)(0.5f + obj[index].coord[0].x);
            int y = (int)(0.5f + obj[index].coord[0].y);
            Prop pr = pat.getProp(pnum[i-1]);
			if (pr.getProp2DImage(pan, this.zoom, this.cameraangle) != null) {
				Dimension center = pr.getProp2DCenter(pan, this.zoom);
				Dimension size = pr.getProp2DSize(pan, this.zoom);
				obj[index].boundingbox.x = x - center.width;
				obj[index].boundingbox.y = y - center.height;
				obj[index].boundingbox.width = size.width;
				obj[index].boundingbox.height = size.height;
			}
            index++;
        }

        Juggler.findJugglerCoordinates(pat, time, jugglervec);

        for (int i = 1; i <= pat.getNumberOfJugglers(); i++) {
            obj[index].type = DrawObject2D.TYPE_BODY;
            obj[index].number = i;
            getXYZ(jugglervec[i-1][2], obj[index].coord[0]);	// left shoulder
            getXYZ(jugglervec[i-1][3], obj[index].coord[1]);	// right shoulder
            getXYZ(jugglervec[i-1][7], obj[index].coord[2]);	// right waist
            getXYZ(jugglervec[i-1][6], obj[index].coord[3]);	// left waist
            getXYZ(jugglervec[i-1][8], obj[index].coord[4]);	// left head bottom
            getXYZ(jugglervec[i-1][9], obj[index].coord[5]);	// left head top
            getXYZ(jugglervec[i-1][10], obj[index].coord[6]);	// right head bottom
            getXYZ(jugglervec[i-1][11], obj[index].coord[7]);	// right head top
            int xmin, xmax, ymin, ymax;
            xmin = xmax = (int)(0.5f + obj[index].coord[0].x);
            ymin = ymax = (int)(0.5f + obj[index].coord[0].y);
            for (int j = 1; j < 8; j++) {
                int x = (int)(0.5f + obj[index].coord[j].x);
                int y = (int)(0.5f + obj[index].coord[j].y);
                if (x < xmin) xmin =  x;
                if (x > xmax) xmax =  x;
                if (y < ymin) ymin =  y;
                if (y > ymax) ymax =  y;
            }
            // inset bb by one pixel to avoid intersection at shoulder:
            obj[index].boundingbox.x = xmin+1;
            obj[index].boundingbox.y = ymin+1;
            obj[index].boundingbox.width = xmax-xmin-1;
            obj[index].boundingbox.height = ymax-ymin-1;
            index++;

            // the lines for each arm, starting with the left:
            for (int j = 0; j < 2; j++) {
                if (jugglervec[i-1][4+j] == null) {
                    obj[index].type = DrawObject2D.TYPE_LINE;
                    obj[index].number = i;
                    getXYZ(jugglervec[i-1][2+j], obj[index].coord[0]);	// entire arm
                    getXYZ(jugglervec[i-1][0+j], obj[index].coord[1]);
                    int x = Math.min((int)(0.5f + obj[index].coord[0].x), (int)(0.5f + obj[index].coord[1].x));
                    int y = Math.min((int)(0.5f + obj[index].coord[0].y), (int)(0.5f + obj[index].coord[1].y));
                    int width = Math.abs((int)(0.5f + obj[index].coord[0].x) - (int)(0.5f + obj[index].coord[1].x)) + 1;
                    int height = Math.abs((int)(0.5f + obj[index].coord[0].y) - (int)(0.5f + obj[index].coord[1].y)) + 1;
                    obj[index].boundingbox.x = x;
                    obj[index].boundingbox.y = y;
                    obj[index].boundingbox.width = width;
                    obj[index].boundingbox.height = height;
                    index++;
                } else {
                    obj[index].type = DrawObject2D.TYPE_LINE;
                    obj[index].number = i;
                    getXYZ(jugglervec[i-1][2+j], obj[index].coord[0]);	// upper arm
                    getXYZ(jugglervec[i-1][4+j], obj[index].coord[1]);
                    int x = Math.min((int)(0.5f + obj[index].coord[0].x), (int)(0.5f + obj[index].coord[1].x));
                    int y = Math.min((int)(0.5f + obj[index].coord[0].y), (int)(0.5f + obj[index].coord[1].y));
                    int width = Math.abs((int)(0.5f + obj[index].coord[0].x) - (int)(0.5f + obj[index].coord[1].x)) + 1;
                    int height = Math.abs((int)(0.5f + obj[index].coord[0].y) - (int)(0.5f + obj[index].coord[1].y)) + 1;
                    obj[index].boundingbox.x = x;
                    obj[index].boundingbox.y = y;
                    obj[index].boundingbox.width = width;
                    obj[index].boundingbox.height = height;
                    index++;

                    obj[index].type = DrawObject2D.TYPE_LINE;
                    obj[index].number = i;
                    getXYZ(jugglervec[i-1][4+j], obj[index].coord[0]);	// lower arm
                    getXYZ(jugglervec[i-1][0+j], obj[index].coord[1]);
                    x = Math.min((int)(0.5f + obj[index].coord[0].x), (int)(0.5f + obj[index].coord[1].x));
                    y = Math.min((int)(0.5f + obj[index].coord[0].y), (int)(0.5f + obj[index].coord[1].y));
                    width = Math.abs((int)(0.5f + obj[index].coord[0].x) - (int)(0.5f + obj[index].coord[1].x)) + 1;
                    height = Math.abs((int)(0.5f + obj[index].coord[0].y) - (int)(0.5f + obj[index].coord[1].y)) + 1;
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
                if (j == i)
                    continue;
                if (obj[i].isCovering(obj[j]))
                    obj[i].covering.addElement(obj[j]);
            }
            obj[i].drawn = false;
        }

        // now figure out a drawing order
        index = 0;
        boolean changed = true;
        while (changed) {
            changed = false;

            for (int i = 0; i < numobjects; i++) {
                if (obj[i].drawn)
                    continue;

                boolean candraw = true;
                for (int j = 0; j < obj[i].covering.size(); j++) {
                    DrawObject2D temp = (DrawObject2D)(obj[i].covering.elementAt(j));
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
            if (obj[i].drawn)
                continue;
            obj2[index] = obj[i];
            obj[i].drawn = true;
            index++;
            // System.out.println("got undrawable item, type "+obj[i].type);
        }

        // draw the objects in the sorted order
        g.setColor(this.background);
        g.fillRect(0, 0, width, height);

        for (int i = 0; i < numobjects; i++) {
            DrawObject2D ob = obj2[i];

            switch (ob.type) {
                case DrawObject2D.TYPE_PROP:
                    Prop pr = pat.getProp(pnum[ob.number-1]);
					int x = (int)(0.5f + ob.coord[0].x);
                    int y = (int)(0.5f + ob.coord[0].y);
                    Image propimage = pr.getProp2DImage(pan, this.zoom, this.cameraangle);
					if (propimage != null) {
						Dimension grip = pr.getProp2DGrip(pan, this.zoom);
						g.drawImage(propimage, x-grip.width, y-grip.height, pan);
					} /* else {
						g.setColor(pr.getEditorColor());
						draw3DProp(ob.object, g);
					} */
					
					// g.setColor(Color.black);
					// g.drawLine(ob.boundingbox.x, ob.boundingbox.y, ob.boundingbox.x + ob.boundingbox.width, ob.boundingbox.y);
					// g.drawLine(ob.boundingbox.x + ob.boundingbox.width, ob.boundingbox.y, ob.boundingbox.x + ob.boundingbox.width, ob.boundingbox.y + ob.boundingbox.height);
					// g.drawLine(ob.boundingbox.x + ob.boundingbox.width, ob.boundingbox.y + ob.boundingbox.height, ob.boundingbox.x, ob.boundingbox.y + ob.boundingbox.height);
					// g.drawLine(ob.boundingbox.x, ob.boundingbox.y + ob.boundingbox.height, ob.boundingbox.x, ob.boundingbox.y);

                    break;
                case DrawObject2D.TYPE_BODY:
                    int[] bodyx = new int[4];
                    int[] bodyy = new int[4];
                    for (int j = 0; j < 4; j++) {
                        bodyx[j] = (int)(0.5f + ob.coord[j].x);
                        bodyy[j] = (int)(0.5f + ob.coord[j].y);
                    }
					g.setColor(this.background);
                    g.fillPolygon(bodyx, bodyy, 4);
                    g.setColor(Color.black);
                    g.drawPolygon(bodyx, bodyy, 4);

                    double LheadBx = ob.coord[4].x;
                    double LheadBy = ob.coord[4].y;
                    double LheadTx = ob.coord[5].x;
                    double LheadTy = ob.coord[5].y;
                    double RheadBx = ob.coord[6].x;
                    double RheadBy = ob.coord[6].y;
                    double RheadTx = ob.coord[7].x;
                    double RheadTy = ob.coord[7].y;
                    for (int j = 0; j < polysides; j++) {
                        headx[j] = (int)(0.5f + 0.5f*(LheadBx + RheadBx + headcos[j]*(RheadBx-LheadBx)));
                        heady[j] = (int)(0.5f + 0.5f*(LheadBy + LheadTy + headsin[j]*(LheadBy-LheadTy)) +
                                         (headx[j]-LheadBx)*(RheadBy-LheadBy) / (RheadBx-LheadBx));
                    }

					g.setColor(this.background);
                    g.fillPolygon(headx, heady, polysides);
                    g.setColor(Color.black);
                    g.drawPolygon(headx, heady, polysides);
                    break;
                case DrawObject2D.TYPE_LINE:
					 g.setColor(Color.black);
                    int x1 = (int)(0.5f + ob.coord[0].x);
                    int y1 = (int)(0.5f + ob.coord[0].y);
                    int x2 = (int)(0.5f + ob.coord[1].x);
                    int y2 = (int)(0.5f + ob.coord[1].y);
                    g.drawLine(x1, y1, x2, y2);
                    break;
            }
            
			
            // g.setColor(Color.black);
            // g.drawLine(ob.boundingbox.x, ob.boundingbox.y, ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y);
            // g.drawLine(ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y, ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y+ob.boundingbox.height-1);
            // g.drawLine(ob.boundingbox.x+ob.boundingbox.width-1, ob.boundingbox.y+ob.boundingbox.height-1, ob.boundingbox.x, ob.boundingbox.y+ob.boundingbox.height-1);
            // g.drawLine(ob.boundingbox.x, ob.boundingbox.y+ob.boundingbox.height-1, ob.boundingbox.x, ob.boundingbox.y);
             
        }
    }

    public Coordinate getHandWindowMax() {
        return new Coordinate(Juggler.hand_out, 0, 1);
    }

    public Coordinate getHandWindowMin() {
        return new Coordinate(-Juggler.hand_in, 0, -1);
    }

    public Coordinate getJugglerWindowMax() {
        Coordinate max = pat.getJugglerMax(1);
        for (int i = 2; i <= pat.getNumberOfJugglers(); i++)
            max = Coordinate.max(max, pat.getJugglerMax(i));

        max = Coordinate.add(max, new Coordinate(Juggler.shoulder_hw, Juggler.shoulder_hw,  // Juggler.head_hw,
                                                 Juggler.shoulder_h + Juggler.neck_h + Juggler.head_h));
        return max;
        // return new Coordinate(Math.max(max.x, max.y), Math.max(max.x, max.y), max.z);
    }
    public Coordinate getJugglerWindowMin() {
        Coordinate min = pat.getJugglerMin(1);
        for (int i = 2; i <= pat.getNumberOfJugglers(); i++)
            min = Coordinate.min(min, pat.getJugglerMin(i));

        min = Coordinate.add(min, new Coordinate(-Juggler.shoulder_hw, -Juggler.shoulder_hw, // -Juggler.head_hw,
                                                 Juggler.shoulder_h));
        return min;
        // return new Coordinate(Math.min(min.x, min.y), Math.min(min.x, min.y), min.z);
    }


    protected void calcScaling(Rectangle r, Coordinate coordmax, Coordinate coordmin) {
        //		double frame_width = 2.0 * Math.max(Math.abs(coordmax.x), Math.abs(coordmin.x));
        double frame_width = coordmax.x - coordmin.x;
        double frame_height = coordmax.z - coordmin.z;
        zoom = Math.min((double)(r.width) / frame_width,
                        (double)(r.height) / frame_height);
        originx = r.x + (int)(0.5 + 0.5 * (r.width - zoom*(coordmax.x+coordmin.x)));   // r.x + r.width / 2;
        originz = r.y + (int)(0.5 + 0.5 * (r.height + zoom*(coordmax.z+coordmin.z)));
    }


    class DrawObject2D {
        public static final int TYPE_PROP = 1;
        public static final int TYPE_BODY = 2;
        public static final int TYPE_LINE = 3;

        protected static final double slop = 3.0;

        public int type;
        public int number;		// either path or juggler number
        public JLVector[] coord = null;
        public Rectangle boundingbox = null;
        public Vector covering = null;
        public boolean drawn = false;
        public JLVector tempv = null;
		
        public DrawObject2D(int numobjects) {
            this.coord = new JLVector[8];
            for (int i = 0; i < 8; i++)
                this.coord[i] = new JLVector();
            this.boundingbox = new Rectangle();
            this.covering = new Vector(numobjects);
            this.tempv = new JLVector();
        }


        public boolean isCovering(DrawObject2D obj) {
            if (!boundingbox.intersects(obj.boundingbox))
                return false;

            switch (this.type) {
                case TYPE_PROP:
                    switch (obj.type) {
                        case TYPE_PROP:
                            return (this.coord[0].z < obj.coord[0].z);
                        case TYPE_BODY:
                        {
                            vectorProduct(obj.coord[0], obj.coord[1], obj.coord[2], tempv);
                            if (tempv.z == 0f)
                                return false;
                            double z = obj.coord[0].z - (tempv.x * (this.coord[0].x - obj.coord[0].x) +
                                                        tempv.y * (this.coord[0].y - obj.coord[0].y)) / tempv.z;
                            return (this.coord[0].z < z);
                        }
                        case TYPE_LINE:
                            return (isBoxCoveringLine(this, obj) == 1);
                    }
                    break;
                case TYPE_BODY:
                    switch (obj.type) {
                        case TYPE_PROP:
                        {
                            vectorProduct(this.coord[0], this.coord[1], this.coord[2], tempv);
                            if (tempv.z == 0f)
                                return false;
                            double z = this.coord[0].z - (tempv.x * (obj.coord[0].x - this.coord[0].x) +
                                                         tempv.y * (obj.coord[0].y - this.coord[0].y)) / tempv.z;
                            return (z < obj.coord[0].z);
                        }
                        case TYPE_BODY:
                        {
                            double d = 0.0;
                            for (int i = 0; i < 4; i++)
                                d += (this.coord[i].z - obj.coord[i].z);
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


        // returns 1 if box covers line, -1 if line covers box, 0 otherwise
        protected int isBoxCoveringLine(DrawObject2D box, DrawObject2D line) {
            // If at least one end of the line is inside the box's boundingbox, then return
            // 1 if all such ends are behind the box, and -1 otherwise.
            // If neither end is inside the boundingbox, then find intersections between the
            // line and the boundingbox.  If no points of intersection, return 0.  If the
            // line is behind the bb at all points of intersection, return 1.  Otherwise
            // return -1;

            // System.out.println("starting...");
            if (box.type == TYPE_BODY)
                vectorProduct(box.coord[0], box.coord[1], box.coord[2], tempv);
            else {
                tempv.x = 0f;
                tempv.y = 0f;
                tempv.z = 1f;
            }

            if (tempv.z == 0f)
                return 0;		// box is exactly sideways

            boolean endinbb = false;
            for (int i = 0; i < 2; i++) {
                double x = line.coord[i].x;
                double y = line.coord[i].y;

                if (box.boundingbox.contains((int)(x+0.5f), (int)(y+0.5f))) {
                    double zb = box.coord[0].z - (tempv.x * (x - box.coord[0].x) +
                                                 tempv.y * (y - box.coord[0].y)) / tempv.z;
                    if (line.coord[i].z < (zb-slop)) {
                        // System.out.println("   exit 1");
                        return -1;
                    }
                    endinbb = true;
                }
            }
            if (endinbb) {
                // System.out.println("   exit 2");
                return 1;	// know that end wasn't in front of body
            }

            boolean intersection = false;
            for (int i = 0; i < 2; i++) {
                int x = ((i == 0) ? box.boundingbox.x : (box.boundingbox.x+box.boundingbox.width-1));
                if ((x < Math.min(line.coord[0].x, line.coord[1].x)) ||
                    (x > Math.max(line.coord[0].x, line.coord[1].x)))
                    continue;
                if (line.coord[1].x == line.coord[0].x)
                    continue;
                double y = line.coord[0].y + (line.coord[1].y-line.coord[0].y)*((double)x - line.coord[0].x) /
                    (line.coord[1].x - line.coord[0].x);
                if ((y < box.boundingbox.y) || (y > (box.boundingbox.y + box.boundingbox.height - 1)))
                    continue;
                intersection = true;
                double zb = box.coord[0].z - (tempv.x * (x - box.coord[0].x) +
                                             tempv.y * (y - box.coord[0].y)) / tempv.z;
                double zl = line.coord[0].z + (line.coord[1].z-line.coord[0].z)*((double)x-line.coord[0].x) /
                    (line.coord[1].x - line.coord[0].x);
                if (zl < (zb-slop)) {
                    // System.out.println("   exit 3, i = "+i);
                    return -1;
                }
            }
            for (int i = 0; i < 2; i++) {
                int y = ((i == 0) ? box.boundingbox.y : (box.boundingbox.y+box.boundingbox.height-1));
                if ((y < Math.min(line.coord[0].y, line.coord[1].y)) ||
                    (y > Math.max(line.coord[0].y, line.coord[1].y)))
                    continue;
                if (line.coord[1].y == line.coord[0].y)
                    continue;
                double x = line.coord[0].x + (line.coord[1].x-line.coord[0].x)*((double)y - line.coord[0].y) /
                    (line.coord[1].y - line.coord[0].y);
                if ((x < box.boundingbox.x) || (x > (box.boundingbox.x + box.boundingbox.width - 1)))
                    continue;
                intersection = true;
                double zb = box.coord[0].z - (tempv.x * (x - box.coord[0].x) +
                                             tempv.y * ((double)y - box.coord[0].y)) / tempv.z;
                double zl = line.coord[0].z + (line.coord[1].z-line.coord[0].z)*(x-line.coord[0].x) /
                    (line.coord[1].x - line.coord[0].x);
                if (zl < (zb-slop)) {
                    // System.out.println("   exit 4, i = "+i);
                    return -1;
                }
            }

            // System.out.println("   exit 5");
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

