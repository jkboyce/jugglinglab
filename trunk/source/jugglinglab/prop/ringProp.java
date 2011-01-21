// ringProp.java
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

package jugglinglab.prop;

import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.lang.reflect.*;
import java.text.MessageFormat;

import jugglinglab.core.*;
import jugglinglab.util.*;
import jugglinglab.renderer.*;


public class ringProp extends Prop {
    static String[] colornames = {"black", "blue", "cyan", "gray",
        "green", "magenta", "orange", "pink", "red", "white", "yellow"};
    static Color[] colorvals = {Color.black, Color.blue, Color.cyan,
        Color.gray, Color.green, Color.magenta, Color.orange,
        Color.pink, Color.red, Color.white, Color.yellow};

    protected static final int colornum_def = 8;	// red
    protected static final double outside_diam_def = 25.0;	// in cm
	protected static final double inside_diam_def = 20.0;	// in cm

    protected double outside_diam = outside_diam_def;
	protected double inside_diam = inside_diam_def;
    protected int	colornum = colornum_def;
    protected Color	color;
	
	protected Image image;
	
    protected double lastzoom = 0.0;
	protected double[] lastcamangle = {0.0, 0.0};

    protected Dimension size = null;
    protected Dimension center = null;
    protected Dimension grip = null;
	int polysides = 200;
	int[] px, py;


    public String getName() {
        return "Ring";
    }

    public Color getEditorColor() {
        return color;
    }

    public ParameterDescriptor[] getParameterDescriptors() {
        ParameterDescriptor[] result = new ParameterDescriptor[3];

        Vector range = new Vector();
        for (int i = 0; i < colornames.length; i++)
            range.add(colornames[i]);

        result[0] = new ParameterDescriptor("color", ParameterDescriptor.TYPE_CHOICE,
                                            range, colornames[colornum_def], colornames[colornum]);
        result[1] = new ParameterDescriptor("outside", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(outside_diam_def), new Double(outside_diam));
        result[2] = new ParameterDescriptor("inside", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(inside_diam_def), new Double(inside_diam));

        return result;
    }

    protected void init(String st) throws JuggleExceptionUser {
		px = new int[polysides];
		py = new int[polysides];
		
        color = Color.red;
		
        if (st == null) return;
        ParameterList pl = new ParameterList(st);

        String colorstr = pl.getParameter("color");
        if (colorstr != null) {
            Color temp = null;
            if (colorstr.indexOf((int)',') == -1) { // color name
                for (int i = 0; i < colornames.length; i++) {
                    if (colornames[i].equalsIgnoreCase(colorstr)) {
                        temp = colorvals[i];
                        colornum = i;
                        break;
                    }
                }
            } else {	// RGB triplet
                     // delete the '{' and '}' characters first
                String str = colorstr;
                int pos;
                while ((pos = str.indexOf('{')) >= 0) {
                    str = str.substring(0,pos) + str.substring(pos+1,str.length());
                }
                while ((pos = str.indexOf('}')) >= 0) {
                    str = str.substring(0,pos) + str.substring(pos+1,str.length());
                }
                int red = 0, green = 0, blue = 0;
                StringTokenizer st2 = new StringTokenizer(str, ",", false);
                if (st2.hasMoreTokens())
                    red = Integer.valueOf(st2.nextToken()).intValue();
                if (st2.hasMoreTokens())
                    green = Integer.valueOf(st2.nextToken()).intValue();
                if (st2.hasMoreTokens())
                    blue = Integer.valueOf(st2.nextToken()).intValue();
                temp = new Color(red, green, blue);
            }

            if (temp != null)
                color = temp;
            else {
				String template = errorstrings.getString("Error_prop_color");
				Object[] arguments = { colorstr };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
			}
        }

        String outsidestr = pl.getParameter("outside");
        if (outsidestr != null) {
            try {
                Double ddiam = Double.valueOf(outsidestr);
                double temp = ddiam.doubleValue();
                if (temp > 0.0)
                    outside_diam = temp;
                else
                    throw new JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"));
            } catch (NumberFormatException nfe) {
				String template = errorstrings.getString("Error_number_format");
				Object[] arguments = { "diam" };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
		
		String insidestr = pl.getParameter("inside");
        if (insidestr != null) {
            try {
                Double ddiam = Double.valueOf(insidestr);
                double temp = ddiam.doubleValue();
                if (temp > 0.0)
                    inside_diam = temp;
                else
                    throw new JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"));
            } catch (NumberFormatException nfe) {
				String template = errorstrings.getString("Error_number_format");
				Object[] arguments = { "diam" };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
    }

    public Coordinate getMax() {
        return new Coordinate(outside_diam/2, 0, outside_diam/2);
    }

    public Coordinate getMin() {
        return new Coordinate(-outside_diam/2, 0, -outside_diam/2);
    }

    public Image getProp2DImage(Component comp, double zoom, double[] camangle) {
        if ((image == null) || (zoom != lastzoom) ||
			(camangle[0] != lastcamangle[0]) || (camangle[1] != lastcamangle[1]))	// first call or display resized?
            redrawImage(comp, zoom, camangle);
        return image;
    }

    public Dimension getProp2DSize(Component comp, double zoom) {
        if ((size == null) || (zoom != lastzoom))		// first call or display resized?
            redrawImage(comp, zoom, lastcamangle);
        return size;
    }
	
    public Dimension getProp2DCenter(Component comp, double zoom) {
        if ((center == null) || (zoom != lastzoom))		// first call or display resized?
            redrawImage(comp, zoom, lastcamangle);
        return center;
    }

    public Dimension getProp2DGrip(Component comp, double zoom) {
        if ((grip == null) || (zoom != lastzoom))		// first call or display resized?
            redrawImage(comp, zoom, lastcamangle);
        return grip;
    }

	private void redrawImage(Component comp, double zoom, double[] camangle) {
		int outside_pixel_diam = (int)(0.5 + zoom * outside_diam);
		int inside_pixel_diam = (int)(0.5 + zoom * inside_diam);
			
		double c0 = Math.cos(camangle[0]);
		double s0 = Math.sin(camangle[0]);
		double s1 = Math.sin(camangle[1]);
		
		int width = (int)(outside_pixel_diam * Math.abs(s0*s1));
		if (width < 2)
			width = 2;
		int height = outside_pixel_diam;
		if (height < 2)
			height = 2;
			
		int inside_width = (int)(inside_pixel_diam * Math.abs(s0*s1));
		if (inside_width == width)
			inside_width -= 2;

		int inside_height = inside_pixel_diam;
		if (inside_height == height)
			inside_height -= 2;
			
		// The angle of rotation of the ring.
		double term1 = Math.sqrt(c0*c0 / (1.0 - s0*s0*s1*s1));
		double angle = (term1 < 1.0) ? Math.acos(term1) : 0.0;
		if (c0*s0 > 0.0)
			angle = -angle;		
		double sa = Math.sin(angle);
		double ca = Math.cos(angle);
		
		int pxmin=0, pxmax=0, pymin=0, pymax=0;
		for (int i = 0; i < polysides; i++) {
			double theta = (double)i * 2.0 * JLMath.pi / (double)polysides;
			double x = (double)width * Math.cos(theta) * 0.5;
			double y = (double)height * Math.sin(theta) * 0.5;
			px[i] = (int)(ca*x - sa*y + 0.5);
			py[i] = (int)(ca*y + sa*x + 0.5);
			if ((i == 0) || (px[i] < pxmin))
				pxmin = px[i];
			if ((i == 0) || (px[i] > pxmax))
				pxmax = px[i];
			if ((i == 0) || (py[i] < pymin))
				pymin = py[i];
			if ((i == 0) || (py[i] > pymax))
				pymax = py[i];
		}

		int bbwidth = pxmax - pxmin + 1;
		int bbheight = pymax - pymin + 1;
		size = new Dimension(bbwidth, bbheight);

		image = VersionSpecific.getVersionSpecific().makeImage(comp, bbwidth, bbheight);
		Graphics g = image.getGraphics();
		VersionSpecific.getVersionSpecific().setAntialias(g);

		g.setColor(color);

		for (int i = 0; i < polysides; i++) {
			px[i] -= pxmin;
			py[i] -= pymin;
		}
		g.fillPolygon(px, py, polysides);
		
		VersionSpecific.getVersionSpecific().setColorTransparent(g);
		
		for (int i = 0; i < polysides; i++) {
			double theta = (double)i * 2.0 * JLMath.pi / (double)polysides;
			double x = (double)inside_width * Math.cos(theta) * 0.5;
			double y = (double)inside_height * Math.sin(theta) * 0.5;
			px[i] = (int)(ca*x - sa*y + 0.5) - pxmin;
			py[i] = (int)(ca*y + sa*x + 0.5) - pymin;
		}
		g.fillPolygon(px, py, polysides);
		
		center = new Dimension(bbwidth/2, bbheight/2);
		
		int gripx = (s0 < 0) ? (bbwidth - 1) : 0;
		double bbw = sa*sa + ca*ca*Math.abs(s0*s1);
		double dsq = s0*s0*s1*s1*ca*ca + sa*sa - bbw*bbw;
		double d = (dsq > 0.0) ? Math.sqrt(dsq) : 0.0;
		if (c0 > 0)
			d = -d;
		int gripy = (int)((double)outside_pixel_diam * d) + bbheight/2;
		grip = new Dimension(gripx, gripy);
		
		lastzoom = zoom;
		lastcamangle = new double[] {camangle[0], camangle[1]};
	}

	/*
    public Object getPropIDX3D() {
        Object result = null;
        try {
            Class ob = Class.forName("idx3d.idx3d_Object");
            Class of = Class.forName("idx3d.idx3d_ObjectFactory");
            Class mat = Class.forName("idx3d.idx3d_Material");
            Method spiral = of.getMethod("SPIRAL", new Class[] {Float.TYPE, Float.TYPE, Float.TYPE, Float.TYPE,
				Float.TYPE, Float.TYPE, Integer.TYPE, Integer.TYPE});
            result = spiral.invoke(null, new Object[] {new Float(0f), new Float((float)outside_diam/2),
				new Float(0f), new Float((float)(outside_diam-inside_diam)/2), new Float(1.02f), new Float(0f),
				new Integer(10), new Integer(10)});
			Method rotate = ob.getMethod("rotate", new Class[] {Float.TYPE, Float.TYPE, Float.TYPE});
			Method scale = ob.getMethod("scale", new Class[] {Float.TYPE, Float.TYPE, Float.TYPE});
			rotate.invoke(result, new Object[] {new Float(0f), new Float(JLMath.pi/2), new Float(0f)});
			scale.invoke(result, new Object[] {new Float(0.1f), new Float(1f), new Float(1f)});
            Method setcolor = mat.getMethod("setColor", new Class[] {Integer.TYPE});
            Object surf = mat.newInstance();
            setcolor.invoke(surf, new Object[] {new Integer(color.getRGB())});
            Method setmaterial = ob.getMethod("setMaterial", new Class[] {mat});
            setmaterial.invoke(result, new Object[] {surf});
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InstantiationException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
        return result;
    }

    public Coordinate getPropIDX3DGrip() {
        return new Coordinate(0.0, -outside_diam/2, 0.0);
    }
	*/
}
