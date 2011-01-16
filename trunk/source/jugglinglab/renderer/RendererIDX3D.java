// RendererIDX3D.java
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

import jugglinglab.jml.*;
import jugglinglab.util.*;
import jugglinglab.prop.*;
import idx3d.*;


public class RendererIDX3D extends Renderer {
    protected int		width, height;

    protected idx3d_Vector	cameracenter;
    protected double[]		cameraangle;
    protected double		cameradistance;
    protected JMLPattern	pat = null;

    protected idx3d_Scene	scene = null;
    protected Color		background;
    protected idx3d_Object[]	prop;
    protected int[]		propnum;
    // protected Coordinate[]	proporigin;

    protected idx3d_Vector[][]	jugglervec = null;


    public RendererIDX3D() {
        this.background = Color.black;
        this.cameraangle = new double[2];
        this.cameradistance = 1000.0;
    }

    public void setPattern(JMLPattern pat) {
        this.pat = pat;
        this.jugglervec = new idx3d_Vector[pat.getNumberOfJugglers()][12];
    }

    public Color getBackground() { return this.background; }

    // IDX3D uses a different coordinate system than Juggling Lab.
    // We use the following transformation:
    // x' = x
    // y' = z
    // z' = y

    public void initDisplay(Dimension dim, int border,
                            Coordinate overallmax, Coordinate overallmin) {
        if (scene == null) {
            scene = new idx3d_Scene(dim.width, dim.height);

            for (int juggler = 1; juggler <= pat.getNumberOfJugglers(); juggler++) {
                /*
                 scene.addObject(juggler+"handL", idx3d_ObjectFactory.BOX(8f, 2f, 15f));
                 scene.object(juggler+"handL").setMaterial(new idx3d_Material(0xFF0000));
                 scene.addObject(juggler+"handR", idx3d_ObjectFactory.BOX(8f, 2f, 15f));
                 scene.object(juggler+"handR").setMaterial(new idx3d_Material(0xFF0000));
                 */
                idx3d_Object lowerL = roundedcylinder((float)Juggler.lower_length, (float)Juggler.elbow_radius,
                                                      (float)Juggler.wrist_radius, 5);
                idx3d_Object upperL = roundedcylinder((float)Juggler.upper_length, (float)Juggler.shoulder_radius,
                                                      (float)Juggler.elbow_radius, 5);
                idx3d_Object lowerR = roundedcylinder((float)Juggler.lower_length, (float)Juggler.elbow_radius,
                                                      (float)Juggler.wrist_radius, 5);
                idx3d_Object upperR = roundedcylinder((float)Juggler.upper_length, (float)Juggler.shoulder_radius,
                                                      (float)Juggler.elbow_radius, 5);
                idx3d_Object shoulders = roundedcylinder((float)(2.0*Juggler.shoulder_hw), (float)Juggler.shoulder_radius,
                                                         (float)Juggler.shoulder_radius, 5);
                idx3d_Object head = ellipsoid((float)Juggler.head_hw, (float)(0.5*Juggler.head_h), 10);

                idx3d_Material jugglermat = new idx3d_Material(0x305030);
                jugglermat.setReflectivity(0);

                scene.addObject(juggler+"lowerL", lowerL);
                scene.object(juggler+"lowerL").setMaterial(jugglermat);
                scene.addObject(juggler+"upperL", upperL);
                scene.object(juggler+"upperL").setMaterial(jugglermat);
                scene.addObject(juggler+"lowerR", lowerR);
                scene.object(juggler+"lowerR").setMaterial(jugglermat);
                scene.addObject(juggler+"upperR", upperR);
                scene.object(juggler+"upperR").setMaterial(jugglermat);
                scene.addObject(juggler+"shoulders", shoulders);
                scene.object(juggler+"shoulders").setMaterial(jugglermat);
                scene.addObject(juggler+"head", head);
                scene.object(juggler+"head").setMaterial(jugglermat);
            }

            this.prop = new idx3d_Object[pat.getNumberOfPaths()];
            this.propnum = new int[pat.getNumberOfPaths()];
            // this.proporigin = new Coordinate[pat.getNumberOfPaths()];

            for (int i = 0; i < pat.getNumberOfPaths(); i++) {
                int pnum = pat.getPropAssignment(i+1);
                Prop pr = pat.getProp(pnum);
                prop[i] = (idx3d_Object)(pr.getPropIDX3D());
                // proporigin[i] = pr.getPropIDX3DGrip();
                propnum[i] = pnum;
                scene.addObject("prop"+i, prop[i]);
            }

            scene.environment.bgcolor = background.getRGB() & 0xFFFFFF;

            scene.addLight("light1",new idx3d_Light(new idx3d_Vector(20f,20f,100f),0xFFFFFF,250,80));
            scene.addLight("light2",new idx3d_Light(new idx3d_Vector(-100f,-100f,100f),0xFFFFFF,100,40));

            scene.addObject("floor", idx3d_ObjectFactory.BOX(200f, 2f, 200f));
            scene.object("floor").setMaterial(new idx3d_Material(0x808080
                                                                 //		idx3d_TextureFactory.CHECKERBOARD(160, 120, 2, 0x000000, 0x999999)
                                                                 ));
            scene.object("floor").setPos(0f, -1f, 0f);

            scene.addCamera("camera1", new idx3d_Camera());
        } else {
            if ((dim.width != this.width) || (dim.height != this.height))
                scene.resize(dim.width, dim.height);
        }

        // Rectangle r = new Rectangle(border, border, width-2*border, height-2*border);
        this.width = dim.width;
        this.height = dim.height;

        /*
         scene.camera("camera1").setPos(0f,
                                        (float)(100.0 + shoulder_height + shoulder_radius + neck_h + 0.5*head_h),
                                        (float)(1.0 + head_hw));
         scene.camera("camera1").lookAt(0f,
                                        (float)(100.0 + shoulder_height + shoulder_radius + neck_h + 0.5*head_h),
                                        (float)(100.0 + head_hw));
         scene.camera("camera1").setFov(120f);
         */

        this.cameracenter = new idx3d_Vector((float)(0.5*(overallmax.x+overallmin.x)),
                                             (float)(0.5*(overallmax.z+overallmin.z)),
                                             (float)(0.5*(overallmax.y+overallmin.y)));
        scene.camera("camera1").lookAt(this.cameracenter);
        setCameraAngle(this.cameraangle);

        double fovfact1 = 0.5*(overallmax.z - overallmin.z) / cameradistance;
        double boxwidth0 = 0.5*(overallmax.x - overallmin.x);
        double boxwidth90 = 0.5*(overallmax.y - overallmin.y);
        double boxwidth = Math.sqrt(boxwidth0*boxwidth0 + boxwidth90*boxwidth90);
        /*		double boxwidth = boxwidth0*(float)(Math.abs(Math.cos(cameraangle))) +
            boxwidth90*(float)(Math.abs(Math.sin(cameraangle)));*/
        double fovfact2 = boxwidth / cameradistance;
        if (width < height)
            fovfact1 *= (double)width / (double)height;
        else
            fovfact2 *= (double)height / (double)width;
        double fovfact = (fovfact1 < fovfact2) ? fovfact2 : fovfact1;

        scene.camera("camera1").setFov(idx3d_Math.rad2deg(4.1f*(float)Math.atan(fovfact)));
    }

    public void setCameraAngle(double[] camangle) {
        this.cameraangle[0] = camangle[0];
        this.cameraangle[1] = camangle[1];

        if (cameracenter == null)
            return;

        double theta = cameraangle[0];
        double phi = cameraangle[1];

        idx3d_Vector pos = idx3d_Vector.scale((float)cameradistance,
                                              new idx3d_Vector((float)(Math.sin(phi)*Math.sin(theta)),
                                                               (float)(Math.cos(phi)),
                                                               (float)(Math.sin(phi)*Math.cos(theta)))
                                              );
        pos = idx3d_Vector.add(cameracenter, pos);
        scene.camera("camera1").setPos(pos);
    }
    public double[] getCameraAngle() {
        double[] ca = new double[2];
        ca[0] = cameraangle[0];
        ca[1] = cameraangle[1];
        return ca;
    }

    public Coordinate getHandWindowMax() { return new Coordinate(Juggler.wrist_radius, 0, Juggler.wrist_radius); }
    public Coordinate getHandWindowMin() { return new Coordinate(-Juggler.wrist_radius, 0, -Juggler.wrist_radius); }
    public Coordinate getJugglerWindowMax() {
        Coordinate max = pat.getJugglerMax(1);
        for (int i = 2; i <= pat.getNumberOfJugglers(); i++)
            max = Coordinate.max(max, pat.getJugglerMax(i));

        max = Coordinate.add(max, new Coordinate(Juggler.shoulder_hw, Juggler.shoulder_hw, // Juggler.head_hw,
                                                 Juggler.shoulder_h + Juggler.shoulder_radius + Juggler.neck_h + Juggler.head_h));
        return max;
        // return new Coordinate(Math.max(max.x, max.y), Math.max(max.x, max.y), max.z);
    }
    public Coordinate getJugglerWindowMin() {
        Coordinate min = pat.getJugglerMin(1);
        for (int i = 2; i <= pat.getNumberOfJugglers(); i++)
            min = Coordinate.min(min, pat.getJugglerMin(i));

        min = Coordinate.add(min, new Coordinate(-Juggler.shoulder_hw, -Juggler.shoulder_hw, // -Juggler.head_hw,
                                                 Juggler.shoulder_h - Juggler.shoulder_radius));
        return min;
        // return new Coordinate(Math.min(min.x, min.y), Math.min(min.x, min.y), min.z);
    }

    // returns screen coordinates:  x in val[0], y in val[1]
    public int[] getXY(Coordinate coord) {
        return getXY(new idx3d_Vector((float)coord.x, (float)coord.z, (float)coord.y));
    }
    protected int[] getXY(idx3d_Vector pos) {
        idx3d_Camera cam = scene.camera("camera1");
        idx3d_Matrix m = cam.getMatrix();
        // idx3d_Matrix nm = cam.getNormalMatrix();

        idx3d_Vector pos2 = pos.transform(m);

        float fact = cam.screenscale/cam.fovfact/((pos2.z>0.1)?pos2.z:0.1f);
        int[] val = new int[2];
        val[0] = (int)(pos2.x*fact+(cam.screenwidth>>1));
        val[1] = (int)(-pos2.y*fact+(cam.screenheight>>1));
        return val;
    }
    public Coordinate getScreenTranslatedCoordinate(Coordinate c, int dx, int dy) {
        idx3d_Vector v = new idx3d_Vector((float)c.x, (float)c.z, (float)c.y);
        idx3d_Camera cam = scene.camera("camera1");
        idx3d_Matrix m = cam.getMatrix();
        // idx3d_Matrix nm = cam.getNormalMatrix();

        idx3d_Vector v2 = v.transform(m);

        float fact = cam.screenscale/cam.fovfact/((v2.z>0.1)?v2.z:0.1f);
        idx3d_Vector v3 = idx3d_Vector.add(v2, new idx3d_Vector((float)dx/fact, (float)(-dy)/fact, 0f));
        idx3d_Vector newv = v3.transform(m.inverse());
        return new Coordinate((double)newv.x, (double)newv.z, (double)newv.y);

        /*
         return Coordinate.add(coord, new Coordinate((double)(-dx) / this.scal.getZoom(), 0.0,
                                                     (double)(-dy) / this.scal.getZoom()));
         */
    }


    double lasttime = -1.0;
    double[] lastangle = new double[2];
    Image im = null;

    public void drawFrame(double time, int[] pnum, Graphics g, Component comp) throws JuggleExceptionInternal {
        if ((time != lasttime) || (cameraangle[0] != lastangle[0]) || (cameraangle[1] != lastangle[1])) {
            // first place all of the props
            boolean[] placed = new boolean[pat.getNumberOfPaths()];
            for (int i = 0; i < pat.getNumberOfPaths(); i++) {
                for (int j = 0; j < pat.getNumberOfPaths(); j++) {
                    if (!placed[j] && (propnum[j] == pnum[i])) {
                        placed[j] = true;
                        Coordinate coord1 = new Coordinate();
                        // Coordinate origin = proporigin[j];
                        pat.getPathCoordinate(i+1, time, coord1);
                        prop[j].setPos((float)(coord1.x),
                                       (float)(coord1.z), (float)(coord1.y));
                        //prop[j].setPos((float)(coord1.x-origin.x),
                        //               (float)(coord1.z-origin.z), (float)(coord1.y-origin.y));
						
						// Rotate the object to its correct orientation!!!
						double angle = pat.getPathOrientation(i+1, time, coord1);
						// Do something here!!!
						
                        break;
                    }
                }
            }
            // then place the juggler-related objects
            Juggler.findJugglerCoordinates(pat, time, jugglervec);

            for (int juggler = 1; juggler <= pat.getNumberOfJugglers(); juggler++)
                placeJugglerObjects(juggler, jugglervec[juggler-1]);

            scene.render(scene.camera("camera1"));
            im = scene.getImage();
            lasttime = time;
            lastangle[0] = cameraangle[0];
            lastangle[1] = cameraangle[1];
        }

        g.drawImage(im, 0, 0, comp);
        //        System.out.println("drawing time "+time);
    }

    // all coordinates here are in global frame
    protected void placeJugglerObjects(int juggler, idx3d_Vector[] vecs) throws JuggleExceptionInternal {
        idx3d_Vector lefthand = vecs[0];
        idx3d_Vector righthand = vecs[1];
        idx3d_Vector leftshoulder = vecs[2];
        idx3d_Vector rightshoulder = vecs[3];
        idx3d_Vector leftelbow = vecs[4];
        idx3d_Vector rightelbow = vecs[5];

        /*
         scene.object(juggler+"handL").setPos((float)lefthand.x, (float)(lefthand.z-1.0), (float)lefthand.y);
         scene.object(juggler+"handR").setPos((float)righthand.x, (float)(righthand.z-1.0), (float)righthand.y);
         */
        idx3d_Vector head = new idx3d_Vector(0.5f*(leftshoulder.x+rightshoulder.x),
                                             0.5f*(leftshoulder.y+rightshoulder.y) + (float)(Juggler.neck_h+0.5*Juggler.head_h+Juggler.shoulder_radius),
                                             0.5f*(leftshoulder.z+rightshoulder.z));
        scene.object(juggler+"head").setPos(head);

        placeObject(scene.object(juggler+"shoulders"), leftshoulder, rightshoulder);

        if (leftelbow == null) {
            double L = Juggler.lower_total;
            double U = Juggler.upper_total;
            idx3d_Vector deltaL = idx3d_Vector.sub(lefthand, leftshoulder);
            double D = (double)(deltaL.length());
            idx3d_Vector loc1 = idx3d_Vector.add(leftshoulder, idx3d_Vector.scale((float)(U/D), deltaL));
            idx3d_Vector loc2 = idx3d_Vector.add(lefthand, idx3d_Vector.scale((float)(-L/D), deltaL));
            placeObject(scene.object(juggler+"upperL"), loc1, leftshoulder);
            placeObject(scene.object(juggler+"lowerL"), lefthand, loc2);
        } else {
            placeObject(scene.object(juggler+"upperL"), leftelbow, leftshoulder);
            placeObject(scene.object(juggler+"lowerL"), lefthand, leftelbow);
        }

        if (rightelbow == null) {
            double L = Juggler.lower_total;
            double U = Juggler.upper_total;
            idx3d_Vector deltaR = idx3d_Vector.sub(righthand, rightshoulder);
            double D = (double)(deltaR.length());
            idx3d_Vector loc1 = idx3d_Vector.add(rightshoulder, idx3d_Vector.scale((float)(U/D), deltaR));
            idx3d_Vector loc2 = idx3d_Vector.add(righthand, idx3d_Vector.scale((float)(-L/D), deltaR));
            placeObject(scene.object(juggler+"upperR"), loc1, rightshoulder);
            placeObject(scene.object(juggler+"lowerR"), righthand, loc2);
        } else {
            placeObject(scene.object(juggler+"upperR"), rightelbow, rightshoulder);
            placeObject(scene.object(juggler+"lowerR"), righthand, rightelbow);
        }
    }

    protected void placeObject(idx3d_Object obj, idx3d_Vector bottom, idx3d_Vector top) {
        idx3d_Vector up = idx3d_Vector.sub(top, bottom).normalize();
        if ((up.x == 0f) && (up.y == 0f))
            up.x = 0.0001f;
        idx3d_Vector right = (new idx3d_Vector(up.y, -up.x, 0)).normalize();
        idx3d_Vector forward = idx3d_Vector.getNormal(up, right);

        obj.matrix = new idx3d_Matrix(right, up, forward);
        obj.normalmatrix = obj.matrix.getClone();

        // idx3d_Vector center = idx3d_Vector.scale(0.5f, idx3d_Vector.add(top, bottom));
        // obj.setPos(center);
        obj.setPos(top);
    }

    protected static idx3d_Object roundedcylinder(float length, float radius1, float radius2, int segments) {
        idx3d_Vector[] path=new idx3d_Vector[2*segments];

        float angle_limit = -(float)Math.asin((radius1 - radius2) / length);

        path[0]=new idx3d_Vector(0,radius1,0);
        for (int i = 1; i < segments; i++) {
            float angle = 0.5f*3.14159265f - (float)i*(0.5f*3.14159265f - angle_limit)/(float)(segments-1);
            float x=(float)Math.cos(angle)*radius1;
            float y=(float)Math.sin(angle)*radius1;
            path[i]=new idx3d_Vector(x, y, 0);
        }
        for (int i = 0; i < (segments-1); i++) {
            float angle = angle_limit - (float)i*(angle_limit + 0.5f*3.14159265f)/(float)(segments-1);
            float x=(float)Math.cos(angle)*radius2;
            float y=(float)Math.sin(angle)*radius2;
            path[i+segments]=new idx3d_Vector(x, y-length, 0);
        }
        path[2*segments-1] = new idx3d_Vector(0, -length-radius2, 0);

        return idx3d_ObjectFactory.ROTATIONOBJECT(path, 2*segments);
    }

    protected static idx3d_Object ellipsoid(float radius, float semimajoraxis, int segments) {
        idx3d_Vector[] path = new idx3d_Vector[segments];

        float x, y, angle;

        path[0] = new idx3d_Vector(0, semimajoraxis, 0);
        path[segments-1] = new idx3d_Vector(0, -radius, 0);

        for (int i=1; i<segments-1; i++) {
            angle = -(((float)i/(float)(segments-2))-0.5f)*3.14159265f;
            x = (float)Math.cos(angle)*radius;
            y = (float)Math.sin(angle)*semimajoraxis;
            path[i] = new idx3d_Vector(x,y,0);
        }

        return idx3d_ObjectFactory.ROTATIONOBJECT(path, segments);
    }
}
