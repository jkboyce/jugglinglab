// imageProp.java
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

import java.awt.*;
import java.util.*;
import javax.swing.*;
import java.awt.image.*;
import java.awt.image.ImageObserver.*;
import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.text.MessageFormat;

import jugglinglab.util.*;
import jugglinglab.renderer.*;
import jugglinglab.core.*;


public class imageProp extends Prop {
	private URL url_def;
	private URL url;
	private Image image;
	private Image scaled_image;
	private final float width_def = 10f;  // in cm 
	private float height_def; // The default height is whatever preserves the proper aspect
							// We have to load the image to determine it
	private float width;
	private float height;
	private Dimension size = null;
	private Dimension center = null;
	private Dimension grip = null;
	
	private double last_zoom = 0;
	
	public imageProp() throws JuggleExceptionUser {
		VersionSpecific vs = VersionSpecific.getVersionSpecific();
		url_def = vs.getDefaultPropImage();
		url = url_def;
		loadImage();
		rescaleImage(1.0);
	}
	
	private void loadImage() throws JuggleExceptionUser {
		// We should probably get the real component that this image wants to be
		// drawn on, but it doesn't really seem to matter much.
		try {
			MediaTracker mt = new MediaTracker(new JPanel());
			image = Toolkit.getDefaultToolkit().getImage(url);
			mt.addImage(image, 0);
			// Try to laod the image
			try {
				mt.waitForAll();
			} catch (InterruptedException ex) {}
			
			if (mt.isErrorAny()) {
				image = null;
				// This could also be bad image data, but it is usually a nonexistent file.
				throw new JuggleExceptionUser(errorstrings.getString("Error_bad_file"));
			}
			
			float aspectRatio = ((float)image.getHeight(null))/((float)image.getWidth(null));
			this.height_def = this.width_def * aspectRatio;
			this.width = width_def;
			this.height = height_def;
		} catch (SecurityException se) {
			throw new JuggleExceptionUser(errorstrings.getString("Error_security_restriction"));
		}
	}
	
	private void rescaleImage(double zoom) {
		int image_pixel_width = (int)(0.5 + zoom * width);
        if (image_pixel_width < 1)
            image_pixel_width = 1;
		int image_pixel_height = (int)(0.5 + zoom * height);
        if (image_pixel_height < 1)
            image_pixel_height = 1;
		size = new Dimension(image_pixel_width, image_pixel_height);
		center = new Dimension(image_pixel_width/2, image_pixel_height/2);
		
        int offsetx = image_pixel_width / 2;
        int offsety = image_pixel_height;
		grip = new Dimension(offsetx, offsety);
		
		last_zoom = zoom;
		
		scaled_image = image.getScaledInstance(image_pixel_width, image_pixel_height, Image.SCALE_SMOOTH);
	}
		
	public String getName() {
		return "Image";
	}
	
    public Color getEditorColor() {
		// The color that shows up in the visual editor
		// We could try to get an average color for the image
		return Color.white;
	}
	
	public ParameterDescriptor[] getParameterDescriptors() {
		ParameterDescriptor[] result = new ParameterDescriptor[2];

		result[0] = new ParameterDescriptor("image", ParameterDescriptor.TYPE_ICON,
                                            null, url_def, url);
		result[1] = new ParameterDescriptor("width", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(width_def), new Double(width));
        //result[2] = new ParameterDescriptor("height", ParameterDescriptor.TYPE_INT,
        //                                    null, new Integer(height_def), new Integer(height));
		
        return result;
	}

	protected void init(String st) throws JuggleExceptionUser {
		if (st == null) return;
		
        ParameterList pl = new ParameterList(st);
		
		String sourcestr = pl.getParameter("image");
		if (sourcestr != null) {
			try {
				this.url = new URL(sourcestr);
				loadImage();
			} catch (MalformedURLException ex) {
				throw new JuggleExceptionUser(errorstrings.getString("Error_malformed_URL"));
			}
		}
		
		String widthstr = pl.getParameter("width");
        if (widthstr != null) {
            try {
                Float width = Float.valueOf(widthstr);
                float temp = width.floatValue();
                if (temp > 0) {
                    this.width = temp;
					float aspectRatio = ((float)image.getHeight(null))/((float)image.getWidth(null));
					this.height = this.width * aspectRatio;
				}				
                else
                    throw new NumberFormatException();
            } catch (NumberFormatException nfe) {
				String template = errorstrings.getString("Error_number_format");
				Object[] arguments = { "width" };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }
		
		/*String heightstr = pl.getParameter("height");
        if (heightstr != null) {
            try {
                Integer height = Integer.valueOf(heightstr);
                int temp = height.intValue();
                if (temp > 0)
                    this.height = temp;
                else
                    throw new NumberFormatException();
            } catch (NumberFormatException nfe) {
				String template = errorstrings.getString("Error_number_format");
				Object[] arguments = { "height" };					
				throw new JuggleExceptionUser(MessageFormat.format(template, arguments));
            }
        }*/
	}
	
	public Image getProp2DImage(Component comp, double zoom, double[] camangle) {
		if (zoom != last_zoom)
			rescaleImage(zoom);
		return scaled_image;
	}

	// Copied from ball
    public Coordinate getMax() {
		return new Coordinate(width/2, 0.0, width);
	}
    public Coordinate getMin() {
		return new Coordinate(-width/2, 0.0, 0.0);
	}
	
    public Dimension getProp2DSize(Component comp, double zoom) {
		if ((size == null) || (zoom != last_zoom))
			rescaleImage(zoom);
		return size;
	}
	
	public Dimension getProp2DCenter(Component comp, double zoom) {
		if ((center == null) || (zoom != last_zoom))
			rescaleImage(zoom);
		return center;
	}
	
    public Dimension getProp2DGrip(Component comp, double zoom) {
		if ((grip == null) || (zoom != last_zoom))
			rescaleImage(zoom);
		return grip;
	}
	
	/*
	public Object getPropIDX3D() {
        Object result = null;
        try {
            Class ob = Class.forName("idx3d.idx3d_Object");
            Class of = Class.forName("idx3d.idx3d_ObjectFactory");
            Class mat = Class.forName("idx3d.idx3d_Material");
			Class tex = Class.forName("idx3d.idx3d_Texture");
			
            Method sphere = of.getMethod("CUBE", new Class[] {Float.TYPE});
            result = sphere.invoke(null, new Object[] {new Float((float)Math.max(width, height))});
			
			Constructor texcons = tex.getConstructor(new Class[] {Class.forName("java.awt.Image")});
			Object texture = texcons.newInstance(new Object[] {image});
            Object surf = mat.newInstance();
			Method settexture = mat.getMethod("setTexture", new Class[] {tex});
            settexture.invoke(surf, new Object[] {texture});
            Method setmaterial = ob.getMethod("setMaterial", new Class[] {mat});
            setmaterial.invoke(result, new Object[] {surf});
        } catch (ClassNotFoundException e) {
			System.err.println("ClassNotFound");
            return null;
        } catch (NoSuchMethodException e) {
			System.err.println("NoSuchMethod");
            return null;
        } catch (SecurityException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InstantiationException e) {
			System.err.println("Instantiation");
            return null;
        } catch (InvocationTargetException e) {
			System.err.println("InvocationTarget");
            return null;
        }
        return result;
    }
	
    public Coordinate getPropIDX3DGrip() {
        return new Coordinate(0.0, 0.0, -height/2);		// bottom of cube
    }
	*/
}