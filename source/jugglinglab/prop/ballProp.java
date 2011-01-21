// ballProp.java
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


public class ballProp extends Prop {
    static String[] colornames = {"black", "blue", "cyan", "gray",
        "green", "magenta", "orange", "pink", "red", "white", "yellow"};
    static Color[] colorvals = {Color.black, Color.blue, Color.cyan,
        Color.gray, Color.green, Color.magenta, Color.orange,
        Color.pink, Color.red, Color.white, Color.yellow};

    protected static final int colornum_def = 8;	// red
    protected static final double diam_def = 10.0;	// in cm
    protected static final boolean highlight_def = false;

    protected double 	diam = diam_def;	// diameter, in cm
    protected int		colornum = colornum_def;
    protected Color		color;
    protected boolean	highlight = highlight_def;
    // protected int	ball_pixel_size;

    protected Image 	ballimage;
    protected double 	lastzoom = 0.0;
    // protected int 	offsetx, offsety;
    protected Dimension size = null;
    protected Dimension center = null;
    protected Dimension grip = null;


    public String getName() {
        return "Ball";
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
        result[1] = new ParameterDescriptor("diam", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(diam_def), new Double(diam));
        result[2] = new ParameterDescriptor("highlight", ParameterDescriptor.TYPE_BOOLEAN,
                                            null, new Boolean(highlight_def), new Boolean(highlight));

        return result;
    }

    protected void init(String st) throws JuggleExceptionUser {
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
				StringTokenizer st2 = new StringTokenizer(str, ",", false);
				if (st2.countTokens() == 3) {
					int red = 0, green = 0, blue = 0;
					String token = null;
					try {
						token = st2.nextToken();
						red = Integer.valueOf(token).intValue();
						token = st2.nextToken();
						green = Integer.valueOf(token).intValue();
						token = st2.nextToken();
						blue = Integer.valueOf(token).intValue();
					} catch (NumberFormatException nfe) {
						String template = errorstrings.getString("Error_number_format");
						Object[] arguments = { token };					
						throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
					}
					temp = new Color(red, green, blue);
				} else
					throw new JuggleExceptionUser(errorstrings.getString("Error_token_count"));
            }

            if (temp != null)
                color = temp;
            else {
				String template = errorstrings.getString("Error_prop_color");
				Object[] arguments = { colorstr };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
			}
        }

        String diamstr = pl.getParameter("diam");
        if (diamstr != null) {
            try {
                Double ddiam = Double.valueOf(diamstr);
                double temp = ddiam.doubleValue();
                if (temp > 0.0)
                    diam = temp;
                else
                    throw new JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"));
            } catch (NumberFormatException nfe) {
				String template = errorstrings.getString("Error_number_format");
				Object[] arguments = { "diam" };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }

        String highlightstr = pl.getParameter("highlight");
        if (highlightstr != null) {
            Boolean bhighlight = Boolean.valueOf(highlightstr);
            highlight = bhighlight.booleanValue();
        }
    }

    public Coordinate getMax() {
        return new Coordinate(diam/2,0,diam/2);
    }

    public Coordinate getMin() {
        return new Coordinate(-diam/2,0,-diam/2);
    }

    public Image getProp2DImage(Component comp, double zoom, double[] camangle) {
        if ((ballimage == null) || (zoom != lastzoom))	// first call or display resized?
            recalc2D(comp, zoom);
        return ballimage;
    }

    public Dimension getProp2DSize(Component comp, double zoom) {
        if ((size == null) || (zoom != lastzoom))		// first call or display resized?
            recalc2D(comp, zoom);
        return size;
    }

	public Dimension getProp2DCenter(Component comp, double zoom) {
		if ((center == null) || (zoom != lastzoom))
			recalc2D(comp, zoom);
		return center;
	}
	
    public Dimension getProp2DGrip(Component comp, double zoom) {
        if ((grip == null) || (zoom != lastzoom))		// first call or display resized?
            recalc2D(comp, zoom);
        return grip;
    }

    protected void recalc2D(Component comp, double zoom) {
        int ball_pixel_size = (int)(0.5 + zoom * diam);
        if (ball_pixel_size < 1)
            ball_pixel_size = 1;
        int offsetx = -ball_pixel_size / 2;
        int offsety = -ball_pixel_size;

        // Now we should create a ball image of diameter ball_pixel_size
        // pixels and put it in the variable ballimage.  First try making a
        // ball with a transparent background.
        ballimage = VersionSpecific.getVersionSpecific().makeImage(comp, ball_pixel_size+1, ball_pixel_size+1);
        Graphics ballg = ballimage.getGraphics();
        VersionSpecific.getVersionSpecific().setAntialias(ballg);
        
        if (this.highlight) {
            float highlightOvals = ball_pixel_size / 1.2f;  // Number of concentric circles to draw.
            float[] rgb = new float[3];
            rgb[0] = (float)color.getRed() / 255f;
            rgb[1] = (float)color.getGreen() / 255f;
            rgb[2] = (float)color.getBlue() / 255f;
            // Make the color a little darker so that there is some contrast.
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = rgb[i] / 2.5f;
            }

            ballg.setColor(new Color(rgb[0], rgb[1], rgb[2]));
            ballg.fillOval(0, 0, ball_pixel_size, ball_pixel_size); // Full sized ellipse.

            // Now draw the highlight on the ball.
            for (int i = 0; i < highlightOvals; i++) {
                // Calculate the new color
                for (int j = 0; j < rgb.length; j++) {
                    rgb[j] = Math.min(rgb[j] + (1f / highlightOvals), 1f);
                }
                ballg.setColor(new Color(rgb[0], rgb[1], rgb[2]));
                ballg.fillOval((int)(i/1.1), (int)(i/2.5),  // Literals control how fast highlight
                                                            // moves right and down respectively.
                               ball_pixel_size - (int)(i*1.3),   // These control how fast the
                               ball_pixel_size - (int)(i*1.3));  // highlight converges to a point.
            }
        } else {
            ballg.setColor(color);
            ballg.fillOval(0, 0, ball_pixel_size, ball_pixel_size);
        }

        size = new Dimension(ball_pixel_size, ball_pixel_size);
        center = new Dimension(ball_pixel_size/2, ball_pixel_size/2);
        grip = new Dimension(ball_pixel_size/2, ball_pixel_size/2);

        lastzoom = zoom;
    }

	/*
    public Object getPropIDX3D() {
        Object result = null;
        try {
            Class ob = Class.forName("idx3d.idx3d_Object");
            Class of = Class.forName("idx3d.idx3d_ObjectFactory");
            Class mat = Class.forName("idx3d.idx3d_Material");
            Method sphere = of.getMethod("SPHERE", new Class[] {Float.TYPE, Integer.TYPE});
            result = sphere.invoke(null, new Object[] {new Float((float)diam/2),
                new Integer(10)});
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
        return new Coordinate(0.0, 0.0, -diam/2);		// bottom of ball
    }
	*/
}
