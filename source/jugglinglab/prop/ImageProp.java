// ImageProp.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.prop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.text.MessageFormat;
import javax.imageio.ImageIO;

import jugglinglab.util.*;


public class ImageProp extends Prop {
    static URL image_url_default;
    static {
        image_url_default = ImageProp.class.getResource("/ball.png");
    }

    protected URL           url;
    protected BufferedImage image;
    protected BufferedImage scaled_image;
    protected final float   width_default = 10.0f;  // in centimeters
    protected float         width;
    protected float         height;
    protected Dimension     size;
    protected Dimension     center;
    protected Dimension     grip;
    protected Coordinate    propmax;
    protected Coordinate    propmin;

    private double          last_zoom = 0.0;

    public ImageProp() throws JuggleExceptionUser {
        if (image_url_default == null)
            throw new JuggleExceptionUser("ImageProp error: Default image not set");
        this.url = image_url_default;
        loadImage();
        rescaleImage(1.0);
    }

    private void loadImage() throws JuggleExceptionUser {
        try {
            MediaTracker mt = new MediaTracker(new Component() {});
            image = ImageIO.read(this.url);
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

            float aspectRatio = ((float)image.getHeight())/((float)image.getWidth());
            this.width = width_default;
            this.height = width_default * aspectRatio;;
        } catch (IOException e) {
            throw new JuggleExceptionUser(errorstrings.getString("Error_bad_file"));
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

        scaled_image = new BufferedImage(image_pixel_width, image_pixel_height, image.getType());
        Graphics2D g = scaled_image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, image_pixel_width, image_pixel_height,
                    0, 0, image.getWidth(), image.getHeight(), null);
        g.dispose();
    }

    @Override
    public String getType() {
        return "Image";
    }

    @Override
    public Color getEditorColor() {
        // The color that shows up in the visual editor
        // We could try to get an average color for the image
        return Color.white;
    }

    @Override
    public ParameterDescriptor[] getParameterDescriptors() {
        ParameterDescriptor[] result = new ParameterDescriptor[2];

        result[0] = new ParameterDescriptor("image", ParameterDescriptor.TYPE_ICON,
                                            null, image_url_default, url);
        result[1] = new ParameterDescriptor("width", ParameterDescriptor.TYPE_FLOAT,
                                            null, new Double(width_default), new Double(width));

        return result;
    }

    @Override
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
    }

    @Override
    public Image getProp2DImage(double zoom, double[] camangle) {
        if (zoom != last_zoom)
            rescaleImage(zoom);
        return scaled_image;
    }

    @Override
    public Coordinate getMax() {
        if (this.propmax == null)
            this.propmax = new Coordinate(width / 2.0, 0.0, width);
        return this.propmax;
    }
    @Override
    public Coordinate getMin() {
        if (this.propmin == null)
            this.propmin = new Coordinate(-width / 2.0, 0.0, 0.0);
        return this.propmin;
    }

    @Override
    public double getWidth() { return width; }

    @Override
    public Dimension getProp2DSize(double zoom) {
        if (size == null || zoom != last_zoom)
            rescaleImage(zoom);
        return size;
    }

    @Override
    public Dimension getProp2DCenter(double zoom) {
        if (center == null || zoom != last_zoom)
            rescaleImage(zoom);
        return center;
    }

    @Override
    public Dimension getProp2DGrip(double zoom) {
        if (grip == null || zoom != last_zoom)
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
        return new Coordinate(0.0, 0.0, -height/2);     // bottom of cube
    }
    */
}
